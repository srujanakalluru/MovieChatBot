# TRAINING-ONLY: not needed to run the app, which pulls the published image instead.
# Self-contained LLM image: llama.cpp server with the fine-tuned movie-sql GGUF baked in.
# Built by the publish stage of scripts/build_model.sh, after the convert stage has
# produced models/gguf/movie-sql-q4_k_m.gguf. The build context is models/gguf, so the
# COPY below resolves the .gguf relative to that directory:
#   docker build -t srujanakalluru/movie-sql-llm:latest -f docker/Dockerfile models/gguf
#   docker push srujanakalluru/movie-sql-llm:latest
FROM ghcr.io/ggml-org/llama.cpp:server
COPY movie-sql-q4_k_m.gguf /models/movie-sql-q4_k_m.gguf
