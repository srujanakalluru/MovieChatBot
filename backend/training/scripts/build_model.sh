#!/usr/bin/env bash
# TRAINING-ONLY: not needed to run the app. The app pulls the published LLM image;
# this script exists for re-training and publishing a new model.
#
# End-to-end model pipeline: setup -> train -> convert -> test -> publish.
#
#   ./scripts/build_model.sh            # run all stages
#   ./scripts/build_model.sh train      # run a single stage: setup|train|convert|test|publish
#
# Requirements: Apple Silicon Mac, Python 3, brew install llama.cpp, Docker Desktop,
# HF_TOKEN exported (first run only), docker login done (publish stage only).
# Every stage skips work that already exists, so the script is safe to re-run after a failure.
set -euo pipefail

TRAINING_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$TRAINING_DIR"

BASE_MODEL="models/llama3-sqlcoder-mlx"
ADAPTER="adapters/movie-sql"
FUSED_DIR="models/fused-fp16"
GGUF_DIR="models/gguf"
F16_GGUF="$GGUF_DIR/movie-sql-f16.gguf"
Q4_GGUF="$GGUF_DIR/movie-sql-q4_k_m.gguf"
LLAMA_CPP_DIR="llama.cpp"
IMAGE="srujanakalluru/movie-sql-llm:latest"
TEST_PORT=11437

activate_venv() {
  # shellcheck disable=SC1091
  [ -z "${VIRTUAL_ENV:-}" ] && [ -f venv/bin/activate ] && source venv/bin/activate || true
}

stage_setup() {
  echo "=== Stage: setup ==="
  [ -d venv ] || python3 -m venv venv
  activate_venv
  pip install -q -U mlx-lm

  if [ -d "$BASE_MODEL" ]; then
    echo "    base model exists - skipping download"
  else
    if [ -z "${HF_TOKEN:-}" ]; then
      echo "ERROR: export HF_TOKEN=... first (token from huggingface.co/settings/tokens)"
      exit 1
    fi
    python3 -m mlx_lm convert \
      --hf-path defog/llama-3-sqlcoder-8b \
      --mlx-path "$BASE_MODEL" \
      --quantize --q-bits 4
  fi
}

stage_train() {
  echo "=== Stage: train ==="
  activate_venv
  if [ ! -f data/train.jsonl ] || [ ! -f data/valid.jsonl ]; then
    echo "ERROR: data/train.jsonl and data/valid.jsonl are required"
    exit 1
  fi
  if [ -f "$ADAPTER/adapters.safetensors" ]; then
    echo "    adapter exists - skipping (delete $ADAPTER to retrain)"
    return
  fi
  python3 -m mlx_lm lora \
    --model "$BASE_MODEL" \
    --train \
    --data data \
    --adapter-path "$ADAPTER" \
    --iters 800 \
    --batch-size 2 \
    --steps-per-report 10 \
    --steps-per-eval 50 \
    --save-every 100
  # Watch val loss above: if it rose toward the end, copy the best checkpoint over
  # adapters.safetensors and re-run from the convert stage.
}

stage_convert() {
  echo "=== Stage: convert ==="
  activate_venv
  python3 -c "import mlx_lm" 2>/dev/null || { echo "ERROR: run the setup stage first"; exit 1; }

  if [ -f "$F16_GGUF" ]; then
    echo "    f16 GGUF exists - skipping fuse"
  else
    if [ ! -d "$FUSED_DIR" ]; then
      python3 -m mlx_lm fuse \
        --model "$BASE_MODEL" \
        --adapter-path "$ADAPTER" \
        --save-path "$FUSED_DIR" \
        --dequantize
    fi
    if [ ! -d "$LLAMA_CPP_DIR" ]; then
      git clone --depth 1 https://github.com/ggml-org/llama.cpp "$LLAMA_CPP_DIR"
    fi
    python3 -m pip install -q -r "$LLAMA_CPP_DIR/requirements/requirements-convert_hf_to_gguf.txt"
    # llama.cpp pins transformers 4.x which breaks mlx_lm - restore 5.x for consistency
    python3 -m pip install -q -U 'transformers>=5'
    mkdir -p "$GGUF_DIR"
    python3 "$LLAMA_CPP_DIR/convert_hf_to_gguf.py" "$FUSED_DIR" --outfile "$F16_GGUF.tmp" --outtype f16
    mv "$F16_GGUF.tmp" "$F16_GGUF"
  fi

  if [ -f "$Q4_GGUF" ]; then
    echo "    q4 GGUF exists - skipping quantize (delete $GGUF_DIR to rebuild)"
  else
    llama-quantize "$F16_GGUF" "$Q4_GGUF.tmp" Q4_K_M
    mv "$Q4_GGUF.tmp" "$Q4_GGUF"
  fi

  rm -rf "$FUSED_DIR"
  rm -f "$F16_GGUF"
  echo "    intermediates cleaned; serving model: $Q4_GGUF"
}

stage_test() {
  echo "=== Stage: test ==="
  command -v llama-server >/dev/null || { echo "ERROR: brew install llama.cpp"; exit 1; }
  llama-server -m "$Q4_GGUF" --port "$TEST_PORT" >/dev/null 2>&1 &
  SERVER_PID=$!
  trap 'kill $SERVER_PID 2>/dev/null || true' EXIT

  echo "    waiting for model to load..."
  for _ in $(seq 1 60); do
    curl -s "http://localhost:$TEST_PORT/health" | grep -q '"ok"' && break
    sleep 2
  done

  QUESTION="Telugu movies with rating above 7" PORT="$TEST_PORT" python3 - <<'PYEOF'
import json, os, urllib.request
system = open("data/train.jsonl").readline()
system = json.loads(system)["messages"][0]["content"]
payload = json.dumps({
    "messages": [
        {"role": "system", "content": system},
        {"role": "user", "content": os.environ["QUESTION"]},
    ],
    "temperature": 0.05,
    "max_tokens": 300,
}).encode()
req = urllib.request.Request(
    f"http://localhost:{os.environ['PORT']}/v1/chat/completions",
    data=payload, headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=120) as r:
    sql = json.load(r)["choices"][0]["message"]["content"]
print("    generated SQL:", sql)
assert "SELECT" in sql.upper(), "model did not produce SQL"
PYEOF

  kill $SERVER_PID 2>/dev/null || true
  trap - EXIT
  echo "    test passed"
}

stage_publish() {
  echo "=== Stage: publish ==="
  [ -f "$Q4_GGUF" ] || { echo "ERROR: $Q4_GGUF not found - run the convert stage first"; exit 1; }
  docker build -t "$IMAGE" -f docker/Dockerfile "$GGUF_DIR"
  echo "    pushing $IMAGE (~5GB, takes a while)"
  docker push "$IMAGE"
  echo "    consumers get the new model with: docker pull $IMAGE"
}

STAGE="${1:-all}"
case "$STAGE" in
  setup)   stage_setup ;;
  train)   stage_train ;;
  convert) stage_convert ;;
  test)    stage_test ;;
  publish) stage_publish ;;
  all)     stage_setup; stage_train; stage_convert; stage_test; stage_publish ;;
  *)       echo "usage: $0 [setup|train|convert|test|publish]"; exit 1 ;;
esac
echo "Done."
