# Training the Model

This directory is intended for developers who wish to produce the model themselves, either from scratch with their own training data, or by extending the existing data with additional examples (e.g., question styles the model handles incorrectly, or questions in further languages). Nothing here is required to run the app, since the published LLM image is pulled instead. The model and how it works are described under "The Model" in the root README.

## Files

| Path | Purpose |
|------|---------|
| `data/` | Training examples: `train.jsonl` and `valid.jsonl` |
| `scripts/build_model.sh` | The end-to-end pipeline |
| `docker/Dockerfile` | Bakes the GGUF into the LLM image (used by the publish stage) |
| `venv/`, `models/`, `adapters/` | Generated during a run; not committed |

## Prerequisites

- An Apple Silicon Mac, since training runs on MLX
- Python 3
- llama.cpp (`brew install llama.cpp`)
- Docker Desktop, for publishing the result
- A free HuggingFace account and an access token from [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens)

## 1. Prepare the training data

Each record in `data/train.jsonl` and `data/valid.jsonl` is a JSON object with three roles, i.e., `system`, `user`, and `assistant`:

```json
{
  "messages": [
    {
      "role": "system",
      "content": "You are a MySQL 8 SQL generator for a movie database. Always use MySQL syntax: LIKE not ILIKE, GROUP_CONCAT not STRING_AGG, CAST() not ::, LIMIT n not FETCH FIRST. Every table must be prefixed with the repo schema and every identifier wrapped in backticks. For genre lookups always JOIN through `repo`.`movie_genres` — never use correlated subqueries or ANY/ALL. Use MySQL date functions: YEAR(), MONTH(), CURDATE(), DATE_SUB(), DATE_FORMAT().\n\nCREATE TABLE `repo`.`genre` (...);\nCREATE TABLE `repo`.`language` (...);\nCREATE TABLE `repo`.`movie` (...);\nCREATE TABLE `repo`.`movie_genres` (...);"
    },
    {
      "role": "user",
      "content": "Top 5 action movies"
    },
    {
      "role": "assistant",
      "content": "SELECT m.`title`, m.`vote_average` FROM `repo`.`movie` m JOIN `repo`.`movie_genres` mg ON m.`id`=mg.`movie_id` JOIN `repo`.`genre` g ON mg.`genre_ids`=g.`id` WHERE g.`name`='Action' ORDER BY m.`vote_average` DESC LIMIT 5;"
    }
  ]
}
```

The `system` message is identical in every training record and at inference time, i.e., it contains the MySQL dialect rules and the full schema DDL.

## 2. Run the pipeline

From the repository root:

```bash
cd backend/training
export HF_TOKEN=your_huggingface_token
docker login
./scripts/build_model.sh
```

The script itself works from any directory, since it resolves its own location, but all paths in this document are relative to `backend/training/`. The finished serving model lands at `backend/training/models/gguf/movie-sql-q4_k_m.gguf`, which is exactly where the publish stage and the root README's local llama-server command expect it.

A single script runs the whole pipeline, i.e., it sets up the Python environment and downloads the base model `defog/llama-3-sqlcoder-8b`, fine-tunes with LoRA (minutes on an M-series Mac), converts and quantizes to GGUF, smoke-tests the result by generating SQL from a sample question, and builds and pushes the LLM image to Docker Hub.

Every stage skips work that already exists, so the script is safe to re-run after a failure. A single stage can be run on its own:

```bash
./scripts/build_model.sh train
```

The stages are `setup`, `train`, `convert`, `test`, and `publish`. The run needs about 35 GB of free disk at peak, and the convert stage cleans up its intermediates automatically. During training, watch the validation loss printed every 50 steps, i.e., it should fall and plateau. If it rises toward the end, copy the checkpoint with the lowest validation loss over `adapters/movie-sql/adapters.safetensors` and re-run from `convert`.

After publishing, the MLX base and the adapter can be deleted to reclaim disk, since only the GGUF is needed to serve locally and consumers pull the image.
