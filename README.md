# MovieChatBot

MovieChatBot is a conversational movie-search application that runs on a local machine. It supports multi-user login on the same machine, with authentication through Google Single Sign-On.

The user enters a plain-language question (e.g., "Best Tamil thrillers from the last five years", or "oka manchi telugu movie cheppandi"), and receives back a matching list of movies, or information related to movies, from the database.

The database is a local mirror of TMDB (The Movie Database), covering films from 1990 onwards, kept up to date automatically. The interpretation is performed by a locally running language model, i.e., it translates the plain-language question into a SQL query against that database.

The whole stack runs in Docker by default, while individual services can run natively for development. Apple Silicon is required only for re-training the model.

The deployment model is one installation per machine, i.e., a single database and a single set of services, shared by all users of that machine. Multiple users can each sign in with their own Google account against the same installation. There is no central server, and separate machines run separate, independent installations.

---

## The Application

The UI is a single chat-style screen behind a Google sign-in. A dedicated sync panel shows the last sync date and triggers a manual refresh.

Sample queries:

- "Top 20 Telugu movies by rating"
- "Best Korean thrillers from 2018 to 2022"
- "Most popular Hindi movies released in January"
- "Top 5 Japanese Animation movies with at least 1000 votes"
- "Highest rated French movies with the word love in the title"
- "Most common genre in English films"
- "Compare Telugu vs Tamil average ratings"

### Authentication (Google SSO)

All endpoints except login, Swagger, and actuator require authentication. The flow is stateless:

1. The Angular login screen renders a Google Sign-In button using Google Identity Services.
2. On sign-in, Google returns an ID token, and the frontend exchanges it at `POST /auth/google`.
3. The backend verifies the token against Google's public keys and this application's OAuth client ID, upserts the user into the `app_user` table, and returns a 24-hour HS256 JWT.
4. The frontend stores the JWT and attaches it as an `Authorization: Bearer` header on every request; a 401 logs the user out.

Configuration: the OAuth Web client ID resides in `frontend/src/app/auth/auth.config.ts` and `security.google.client-id`, overridable via `GOOGLE_CLIENT_ID`. The JWT signing key is overridden in production via `JWT_SECRET`. The application's URL must be an Authorized JavaScript origin on the OAuth client, i.e., `http://localhost:4201` for Docker and `http://localhost:4200` for local dev.

### Multilingual input

The UI is localized in English, French, and Telugu. Questions in any of these languages go directly to the SQL model, since the LLaMA 3 base is multilingual.

For Telugu, the application supports romanized typing through a transliteration service, which is automatically converted to Telugu script.

### How a query works

1. The user types a question in the search box and presses Enter.
2. The Angular frontend POSTs the raw text to `POST /query` with the JWT header.
3. The backend forwards it to the llama.cpp server alongside a fixed system prompt containing the database schema and MySQL dialect rules.
4. The LLM returns a SQL query. The backend sanitizes it and executes it against MySQL with a query timeout.
5. The result rows come back as JSON, and the frontend renders them.

If the model produces no valid SQL, or a malformed one, the user sees "No results found. Try rephrasing your question." A 503 is returned if a background sync is in progress.

### API endpoints

**Auth**

`POST /auth/google`, body `{"idToken": "..."}` carrying the Google ID token. Returns `{token, email, name, pictureUrl}`. This is the only unauthenticated API endpoint.

**Query**

`POST /query`, body is a plain-text question. Returns movie results as JSON. Requires a JWT.

**Transliteration**

`GET /transliterate?text=evaru&lang=te`, returns `{"suggestions": ["ఎవరు", ...]}`. Requires a JWT.

**Internal / data management** — all require JWT

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/internal/sync-status` | Last sync date, timestamp, and whether a sync is currently running |
| `POST` | `/internal/sync` | Start an async sync from last sync date to yesterday — returns 202 immediately (409 if already running); progress via `/internal/sync-status` |
| `POST` | `/internal/sync/range?startDateStr=dd-MM-yyyy&endDateStr=dd-MM-yyyy` | Sync a specific date range |
| `POST` | `/internal/backfill_language_data` | Refresh the language table from TMDB |
| `POST` | `/internal/backfill_genre_data` | Refresh the genre table from TMDB |

### Tech stack

Angular 22 frontend, Spring Boot 4 / Java 21 backend with Spring Security for Google ID token verification and JWT, MySQL 8 database, RabbitMQ for retry queuing, llama.cpp for LLM inference, TMDB API as the data source.

### Database schema

There are five tables: `movie` (title, language, popularity, rating, vote count, release date, and overview), `genre`, `language`, `movie_genres` (the join table), and `app_user` (for Google SSO users). The schema is owned by Liquibase.

### Database sync

The sync splits the requested period into monthly chunks processed in parallel. Failed batches go to a RabbitMQ dead-letter queue and are retried up to `sync.max-retry-attempts` times. On startup, and daily at 3:30 AM, the application syncs any missing months automatically. While a sync is in progress, `POST /query` returns 503.

```properties
sync.default-start-date=01-01-1990
sync.thread-count=12
sync.retry-delay-ms=300000
sync.max-retry-attempts=3
```

---

## Running the App

### 1. Install Docker Desktop

```bash
brew install --cask docker-desktop
open -a "Docker Desktop"
```

Skip this if Docker is already installed, i.e., check with `docker --version`.

### 2. Install MySQL and RabbitMQ

```bash
docker compose up -d mysql rabbitmq
```

This pulls and starts both. It creates the `repo` database and `moviebot` user automatically, with MySQL on `localhost:3307` and the RabbitMQ management UI on `http://localhost:15673` (login `moviebot`/`moviebot`). Data persists in a Docker volume.

### 3. Install the LLM image

```bash
docker pull srujanakalluru/movie-sql-llm:latest
```

This is a self-contained llama.cpp server with the fine-tuned model baked in, about 5 GB.

### 4. Generate a TMDB API key

Sign up at [themoviedb.org](https://www.themoviedb.org) → Settings → API → request a key (free).

All secrets reside in the `.env` file at the project root, which Docker Compose reads automatically. Open it and fill in `TMDB_API_KEY` and `TMDB_API_TOKEN`. Do not commit the file with real values, since in CI the same names are configured as GitHub Actions secrets.

> Local dev alternative: put them in `backend/src/main/resources/application-local.properties` as `tmdb.api.key=` and `tmdb.api.token=`.

`JWT_SECRET` in `.env` is optional, i.e., the Docker backend starts without it, but it then falls back to a publicly known dev-default signing key and logs a warning, so tokens become forgeable. Set a long random string before any non-local use.

### 5. Create a Google OAuth client (for sign-in)

[console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials → Create Credentials → OAuth client ID → type "Web application" → add `http://localhost:4201` under Authorized JavaScript origins → Create. Copy the client ID (ends in `.apps.googleusercontent.com`) into:

- `GOOGLE_CLIENT_ID` in `.env`
- `GOOGLE_CLIENT_ID` in `frontend/src/app/auth/auth.config.ts`

> Local dev alternative: also add `http://localhost:4200` to the Authorized JavaScript origins.

### 6. Start the app

One command per service, in dependency order:

```bash
docker compose up -d mysql
docker compose up -d rabbitmq
docker compose up -d llm
docker compose up -d backend
docker compose up -d frontend
```

Services already running are left untouched, so re-running any line is harmless. A bare `docker compose up -d` without a service name deliberately starts nothing, i.e., every service must be named explicitly. To start all five at once, use `docker compose --profile app up -d`.

Starting `backend` or `frontend` also starts their stopped dependencies, since the compose file declares startup order. To start or restart exactly one service and nothing else, add `--no-deps`:

```bash
docker compose up -d --no-deps backend
```

If a dependency is down, the backend container still starts and retries on its own until the dependency returns.

Open `http://localhost:4201`, sign in with Google, and ask a question. The first startup syncs movie data from TMDB and takes a few minutes. After changing code, rebuild with `docker compose up -d --build --no-deps backend frontend`.

> Local dev alternative with hot reload and native speed:
> ```bash
> cd backend && mvn spring-boot:run      # http://localhost:8080
> cd frontend && npm install && npm start  # http://localhost:4200 — use this URL
> ```

### Services

| Service              | URL                       |
|----------------------|---------------------------|
| Frontend             | http://localhost:4201     |
| Backend              | http://localhost:8081     |
| LLM                  | http://localhost:11436    |
| MySQL                | localhost:3307            |
| RabbitMQ             | localhost:5673            |
| RabbitMQ management  | http://localhost:15673    |

---

## Developer Notes

[DEVELOPER.md](DEVELOPER.md) is a guided tour of the codebase with hands-on exercises, intended for developers new to Spring Boot, MySQL, RabbitMQ, JWT, or LLMs.

### Debugging the backend

The recommended approach is to run the backend natively in the IDE while the infrastructure stays in Docker. Start `mysql`, `rabbitmq`, and `llm` as in the steps above, then run the application in IntelliJ with its normal Run or Debug configuration. `application.properties` already points at the Docker ports, so no configuration changes are needed. This provides full breakpoints, instant restarts, and devtools hot reload. The dev frontend at `http://localhost:4200` (via `npm start`) proxies to this backend on port 8080.

To debug the backend running inside its container instead, which is useful when a bug appears in Docker but not natively, enable remote JVM debugging. The wiring is already in place, i.e., uncomment the `BACKEND_DEBUG` line in `.env`, then:

```bash
docker compose up -d --no-deps backend
```

Compose detects the changed environment and recreates only the backend container, while everything else keeps running untouched.

In IntelliJ: Run → Edit Configurations → + → Remote JVM Debug → host `localhost`, port `5005` → Debug. Breakpoints, stepping, and expression evaluation work normally. The limitation is that code changes require an image rebuild, so the native setup is preferable for iterative work. Re-comment the line and restart to turn the debug port off.

### Debugging the frontend

The containerized frontend is a minified production build served as static files by nginx, i.e., there is nothing to attach a debugger to. Frontend debugging always uses the dev server:

```bash
cd frontend && npm start
```

This serves `http://localhost:4200` with source maps and hot reload, proxying API calls to the backend. Debug in the browser DevTools, where breakpoints map to the original TypeScript, or in IntelliJ with a JavaScript Debug configuration pointing at `http://localhost:4200`. Note that the Google OAuth client must list `http://localhost:4200` as an authorized origin for sign-in to work here.

---

## The Model

### What it is

`movie-sql` is a fine-tuned version of `defog/llama-3-sqlcoder-8b`, further trained using LoRA on 199 question-to-SQL examples written for this application's exact schema.

The base model is an 8B-parameter model built by Defog AI on top of Meta's LLaMA 3, specifically pre-trained for text-to-SQL tasks, i.e., it already understands SQL syntax, JOIN patterns, filtering, and aggregation. Fine-tuning therefore only needs to teach it this application's schema and output format, not SQL fundamentals, which keeps the training data small (199 examples) and the training time short (minutes on an M-series Mac).

### LoRA

LoRA (Low-Rank Adaptation) makes fine-tuning practical on a single consumer machine. Instead of modifying all 8B parameters, it freezes them and trains a small set of delta weights on top. These delta weights (the adapter) are ~50 MB against a ~16 GB base model.

Concretely, LoRA decomposes the weight delta for each attention layer into two small matrices A and B (rank 8 in this project). At inference time the model computes `base_weight + A × B × scale`, and during training only A and B are updated.

### From adapter to GGUF

Training happens in Apple's MLX framework, while serving happens in llama.cpp. The bridge is the `convert` stage of `backend/training/scripts/build_model.sh`, which fuses the LoRA adapter into the base model as de-quantized fp16, converts to GGUF, and quantizes to Q4_K_M. The resulting single file runs anywhere llama.cpp runs (Metal on the Mac, CPU in Docker), with no adapter loading at inference time.

Quantization formats are not interchangeable, i.e., MLX 4-bit and GGUF Q4_K_M are different layouts, which is why the conversion goes through fp16.

---

## Training Your Own Model

Everything needed to re-train, rebuild, and publish the model (the training data, the pipeline script, and its documentation) lives in `backend/training/`. See [backend/training/README.md](backend/training/README.md). Running the app never requires that directory, since the model arrives as a pulled Docker image.
