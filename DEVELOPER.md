# Developer Guide

## Table of contents

1. [Architecture](#1-architecture)
2. [Docker layout](#2-docker-layout)
3. [Backend structure](#3-backend-structure)
4. [Query flow](#4-query-flow)
5. [Authentication](#5-authentication)
6. [Database](#6-database)
7. [Sync pipeline](#7-sync-pipeline)
8. [RabbitMQ retry loop](#8-rabbitmq-retry-loop)
9. [Cross-cutting concerns](#9-cross-cutting-concerns)
10. [Frontend](#10-frontend)
11. [Tests and CI](#11-tests-and-ci)
12. [Recipes](#12-recipes)
13. [Debugging](#13-debugging)
14. [Pitfalls](#14-pitfalls)

---

## 1. Architecture

The application consists of five services, each running in its own Docker container:

```
                         browser
                            |
                            v
 +---------------------- frontend ----------------------+
 |  Angular app served by nginx (port 4201)             |
 |  nginx forwards /query, /auth, /internal,            |
 |  /transliterate to the backend                       |
 +-----------------------------|------------------------+
                               v
 +---------------------- backend ------------------------+
 |  Spring Boot / Java 21 (port 8081 on the host)        |
 |  - verifies logins, issues JWTs                       |
 |  - sends questions to the LLM, runs the SQL it gets   |
 |  - syncs movie data from TMDB on a schedule           |
 +----|-----------------|------------------|-------------+
      v                 v                  v
 +--------+      +-----------+      +------------+        +-----------+
 | MySQL  |      | llama.cpp |      | RabbitMQ   |        | TMDB API  |
 | 3307   |      | LLM 11436 |      | 5673/15673 |        | (internet)|
 +--------+      +-----------+      +------------+        +-----------+
```

- **MySQL** stores a local copy of TMDB's movie catalog (1990 onward) and the application's users, i.e., all persistent data resides here.
- **LLM**: a fine-tuned llama-3-sqlcoder running under llama.cpp. It converts a natural-language question into a MySQL query and holds no data or state.
- **Backend**: handles security, orchestration, data sync, and execution of the generated SQL. All business logic resides here.
- **RabbitMQ** holds failed TMDB sync batches for delayed retry.
- **Frontend**: a single chat screen responsible for rendering and input handling only.

The system has two primary flows. The query flow is synchronous, i.e., the request travels browser -> backend -> LLM -> MySQL -> browser within a single HTTP request. The sync flow runs in the background, i.e., a scheduler pulls movies from TMDB in parallel monthly batches and writes them to MySQL, while RabbitMQ handles failed batches.

On startup, [StartupDependencyChecker](backend/src/main/java/com/chatbot/startup/StartupDependencyChecker.java) verifies that MySQL, RabbitMQ, and the LLM server are reachable before the application begins serving requests. It is a `@PostConstruct` method and can be disabled via `startup.dependency-check.enabled`. MySQL is checked via `DataSource.isValid`, RabbitMQ via `createConnection`, and the LLM via `GET {endpoint}/health`. If any of them is unreachable, the application fails to start. For the LLM, any HTTP response is treated as reachable, since the server may return a 503 while the model is still loading; only a connection failure is treated as fatal. In Docker this is redundant, since compose already gates the backend container on the healthchecks of the three services.

---

## 2. Docker layout

Docker runs each service as a container, i.e., an isolated Linux environment built from an image. [docker-compose.yml](docker-compose.yml) declares all five services, and `docker compose up` runs them. The file defines:

**Ports.** A mapping such as `"3307:3306"` forwards host port 3307 to container port 3306. The host-side ports are offset from the standard ones (MySQL 3307 instead of 3306, RabbitMQ 5673 instead of 5672) to avoid colliding with natively installed services. Containers reach each other by service name on the inside port, i.e., the backend container connects to `mysql:3306` and `rabbitmq:5672`, not `localhost:3307`.

| Service | Inside Docker network | From the host (Docker run) | Native dev run |
|---|---|---|---|
| backend | `backend:8080` | `localhost:8081` | `localhost:8080` |
| frontend | `frontend:80` | `localhost:4201` | `localhost:4200` |
| MySQL | `mysql:3306` | `localhost:3307` | Docker MySQL at 3307 |
| RabbitMQ | `rabbitmq:5672` | `localhost:5673` (UI 15673) | Docker at 5673 |
| LLM | `llm:8080` | `localhost:11436` | Docker at 11436 |

**Environment overrides.** The backend service's `environment:` block sets variables such as `SPRING_DATASOURCE_URL`. Spring Boot matches environment variables to properties by normalizing the names, i.e., uppercase-with-underscores is converted to lowercase-with-dots, so `SPRING_DATASOURCE_URL` becomes `spring.datasource.url`, and an environment variable always takes precedence over the properties file. The properties file is written for native runs (localhost ports, third column), while the environment variables replace those values with container addresses (first column). The same jar therefore runs in both environments without code changes.

**Volumes.** `mysql-data:/var/lib/mysql` keeps the database on a named volume that survives container deletion and rebuilds.

**Healthchecks and ordering.** The backend declares `depends_on: mysql: condition: service_healthy`, so compose waits for MySQL's `mysqladmin ping` healthcheck before starting it.

**Profiles.** Every service declares `profiles: ["app"]`, i.e., services are started by name, or all five with `--profile app`.

**Secrets.** Compose reads `.env` at the project root and substitutes `${TMDB_API_KEY}`-style references. Secrets reside there (gitignored), not in the compose file.

Common commands:

```bash
docker compose up -d mysql rabbitmq llm          # infrastructure only
docker compose up -d --build --no-deps backend   # rebuild + restart backend alone
docker compose logs -f backend                   # follow logs
docker exec -it movie-mysql mysql -umoviebot -pmoviebot repo   # SQL shell
```

The standard development setup runs the infrastructure (mysql, rabbitmq, llm) in Docker, the backend natively in the IDE, and the frontend natively via `npm start`. Native processes restart faster and support breakpoints, while the containers run the parts not being edited.

---

## 3. Backend structure

```
backend/src/main/java/com/chatbot/
  MovieChatBot.java      entry point
  controller/            HTTP endpoints - thin, no logic
  service/               interfaces  +  service/impl/  business logic
  repository/            database access interfaces (Spring Data JPA)
  beans/                 JPA entities - classes mapped to tables
  dto/                   request/response shapes (auth, llm, tmdb, sync)
  client/                outbound HTTP callers (TMDB, Google transliterate)
  config/                bean factories (LLM, TMDB, RabbitMQ, Swagger...)
  security/              SecurityConfig, GoogleTokenVerifier
  filter/                servlet filters (JWT, correlation id)
  messaging/             RabbitMQ producer + consumer
  scheduler/             timed/startup sync triggers
  errorhandling/         global exception handler, retry policy
  validation/            custom bean-validation rules
  logging/               AOP logging aspect
  constant/              prompts, message keys, constants
  utils/                 JWT and date helpers
```

The layering is one-directional, i.e., controllers call services, and services call repositories and clients, never the reverse.

**Bean creation and injection.** Spring scans these packages at startup and creates an instance of every class annotated `@Service`, `@RestController`, `@Component`, `@Repository`, or `@Configuration`. These instances are called beans. By default each class gets exactly one instance, i.e., a singleton, shared by all requests and threads. One instance is sufficient since these classes are stateless, i.e., their fields hold only dependencies and configuration values set once at startup, while per-request data lives in method parameters and local variables, of which every thread gets its own copy. Sharing a single instance is therefore thread-safe, and the object graph is wired once rather than per request. (Spring supports other scopes, such as one instance per request; this codebase uses only the default.) Spring injects each bean's dependencies through its constructor by matching parameter types to existing beans. Nothing in the codebase calls `new` on a service or controller. Services are injected by interface (`MovieIntelService`), with the single implementation (`MovieIntelServiceImpl`) resolved automatically. This is also what allows unit tests to run without Spring, i.e., the tests call the constructor directly and pass mocks. `@Autowired` marks the injection constructor, and classes with Lombok's `@RequiredArgsConstructor` get that constructor generated for their `final` fields.

**Annotations used in this codebase**, beyond the stereotypes above:

| Annotation | Role here |
|---|---|
| `@Configuration` + `@Bean` | Factory methods whose return values become beans - the configured `RestTemplate`s in [LlmConfig](backend/src/main/java/com/chatbot/config/LlmConfig.java) and [TMDBConfig](backend/src/main/java/com/chatbot/config/TMDBConfig.java) |
| `@PostMapping`, `@GetMapping` | Route an HTTP verb + path to a controller method |
| `@Value("${query.timeout-ms:5000}")` | Inject a property (default after the colon) |
| `@ConfigurationProperties(prefix = "llm")` | Bind a property group onto a class |
| `@Transactional` | Wrap the method in a DB transaction |
| `@Scheduled`, `@Async`, `@EventListener` | Background execution |
| `@RabbitListener` | Consume messages from a queue |

Embedded Tomcat on port 8080, the MySQL connection pool, JSON serialization, and the RabbitMQ connection are provided by the `spring-boot-starter-*` dependencies in [backend/pom.xml](backend/pom.xml), i.e., each starter activates and configures its feature from `spring.*` properties.

**Configuration.** Every tunable value resides in [application.properties](backend/src/main/resources/application.properties): datasource, LLM endpoint and sampling settings, JWT secret and lifetime, sync thread count, retry delays, and timeouts. `spring.profiles.active=local` additionally loads `application-local.properties` (gitignored), used locally for real TMDB keys. The override precedence is environment variables > profile file > base file. Property values can reference environment variables directly, e.g., `${GOOGLE_CLIENT_ID:fallback}`.

**Lombok.** `@Data`, `@Slf4j`, `@Builder`, and `@RequiredArgsConstructor` generate boilerplate (getters/setters, the `log` field, builders, constructors) at compile time. IntelliJ requires the Lombok plugin and annotation processing enabled (Settings -> Build -> Compiler -> Annotation Processors); otherwise the project does not compile in the IDE.

---

## 4. Query flow

This section traces the processing of a single request, e.g., "Top 20 Telugu movies by rating", from browser to response.

### 4.1 Browser to backend

The chat component calls `MovieService.query()` ([movie.service.ts](frontend/src/app/chat/movie.service.ts)), which POSTs the raw question (plain text, not JSON) to `/query`. Two frontend interceptors modify the outgoing request, i.e., one adds `Accept-Language` (the UI language) and the other adds the `Authorization: Bearer <jwt>` header. In Docker, nginx proxies `/query|/internal|/auth|/transliterate` to the backend container ([nginx.conf](frontend/nginx.conf)); in native development, the Angular dev server does the same via [proxy.conf.json](frontend/proxy.conf.json).

### 4.2 Filters

Servlet filters process every request before any controller:

1. [CorrelationIdFilter](backend/src/main/java/com/chatbot/filter/CorrelationIdFilter.java) puts an 8-character random id into the MDC, i.e., a thread-local map that the logging layout prints in every line. All log lines of one request share the id, so a single grep collects the full request. This is the `[a1b2c3d4]` column in the backend output.
2. [JwtTokenFilter](backend/src/main/java/com/chatbot/filter/JwtTokenFilter.java) validates the JWT's signature, extracts the email claim, and populates Spring Security's context for this request.
3. The security rules ([SecurityConfig](backend/src/main/java/com/chatbot/security/SecurityConfig.java)) reject the request with 401 unless step 2 authenticated it, since `/query` is not on the public list.

### 4.3 Controller

[MovieChatBotController](backend/src/main/java/com/chatbot/controller/MovieChatBotController.java), `@PostMapping("/query")`:

```java
public ResponseEntity<?> query(
    @NotBlank(message = "{query.empty}") @RequestBody(required = false) String userInput) {
  if (syncService.isSyncInProgress())                 // 1. refuse during sync -> 503
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)...;
  String sql = llmService.generateSql(userInput.trim());   // 2. question -> SQL
  if (sql == null || sql.isBlank())                   // 3. nothing usable -> message
    return ResponseEntity.ok(Map.of("message", msg(ApiMessages.NO_RESULTS)));
  return ResponseEntity.ok(movieIntelService.searchByCriteria(sql));  // 4. run it
}
```

- `@RequestBody String` binds the raw body. `@NotBlank(message = "{query.empty}")` is bean validation, i.e., an empty body is rejected before the method runs, with the message key resolved from [messages.properties](backend/src/main/resources/messages.properties) in the caller's language and returned as a 400 by the global exception handler.
- The 503-during-sync check is required since a query issued mid-sync would read partially written months, i.e., return inconsistent results.
- There are three response shapes: `{message}` (no SQL), `{error}` (sync running), and `{data: [...]}` (rows). The frontend branches on which key is present.

### 4.4 Calling the model

[LlmServiceImpl](backend/src/main/java/com/chatbot/service/impl/LlmServiceImpl.java) sends two messages, i.e., a fixed `system` message and the user's question. The system message ([LlmPrompts.SYSTEM_PROMPT](backend/src/main/java/com/chatbot/constant/LlmPrompts.java)) contains the MySQL dialect rules ("LIKE not ILIKE...", "always JOIN through `repo`.`movie_genres`...") and the full `CREATE TABLE` DDL of the queryable tables. The model receives this schema on every call and was fine-tuned with this exact prompt ([backend/training/README.md](backend/training/README.md)), so the prompt and the training data must stay in sync.

The call goes through `llmRestTemplate` (3s connect / 60s read timeout, [LlmConfig](backend/src/main/java/com/chatbot/config/LlmConfig.java)). llama.cpp implements the OpenAI chat-completions protocol, i.e., the request is `{model, messages, max_tokens, temperature}` ([dto/llm/](backend/src/main/java/com/chatbot/dto/llm/)) and the answer is in `choices[0].message.content`. The temperature is 0.05 for near-deterministic output, and `max_tokens` 300 caps the response length.

`sanitizeSql()` strips two known model artifacts, i.e., a trailing `[/SQL]` marker and PostgreSQL's `NULLS LAST|FIRST`, which MySQL rejects.

An unreachable LLM endpoint raises `LlmUnavailableException`, which the global exception handler maps to a 503 with a translated message, since the model being down is a service condition rather than a server bug.

### 4.5 Executing the generated SQL

[MovieIntelServiceImpl.searchByCriteria()](backend/src/main/java/com/chatbot/service/impl/MovieIntelServiceImpl.java) treats the model output as untrusted input. The safeguards, in order:

1. Trailing semicolons are stripped, and any remaining mid-string semicolon rejects the statement as a multi-statement attempt.
2. An allowlist rejects anything not starting with `SELECT` or `WITH`, so UPDATE, DELETE, and DDL never reach MySQL.
3. A MySQL optimizer hint `/*+ MAX_EXECUTION_TIME(5000) */` is inserted after `SELECT` (with a regex variant for `WITH ... SELECT`), so MySQL terminates anything running past 5 seconds (`query.timeout-ms`).
4. Execution runs on a dedicated read-only connection pool ([QueryDataSourceConfig](backend/src/main/java/com/chatbot/config/QueryDataSourceConfig.java)) with its own JDBC-level timeout. The pool can point at a SELECT-only MySQL user (`moviebot_ro`, created automatically on fresh Docker installs via [mysql-init/](mysql-init/)) through the `query.datasource.*` properties, and defaults to the main credentials.
5. `JdbcTemplate.queryForList(sql)` returns each row as a `Map<String, Object>`. This is the codebase's second database-access style, i.e., JPA repositories handle known, typed entities, while `JdbcTemplate` handles dynamic SQL whose shape is unknown at compile time.
6. Every `DataAccessException` is caught, classified (timeout, invalid SQL from the model, MySQL error 3024 = hint timeout), logged at WARN, and converted to an empty result rather than a 500. The user sees "No results found", and the cause is in the log.

Rows are returned wrapped in `JsonObjectWrapper` (`{data: [...]}`), Jackson serializes them, and the frontend picks a layout based on the shape.

---

## 5. Authentication

Regular users carry no password, i.e., identity comes from Google. There is one exception, a single non-SSO **admin** account that logs in with a username/password, covered in 5.3. After either handshake the backend issues its own credential, a JWT, used for all further requests.

A JWT is three base64url segments, `header.payload.signature`. The payload carries claims, i.e., `sub` (Google's stable user id, or the admin username), `email`, `name`, a `roles` array, and expiry. The signature is HMAC-SHA256 over the rest, keyed by `security.jwt.token.secret-key`. [JwtTokenUtil](backend/src/main/java/com/chatbot/utils/JwtTokenUtil.java) derives the HMAC key with `Keys.hmacShaKeyFor(...)` and logs a warning if the baked-in dev-default secret is still in use, since anyone reading the repo could then forge tokens. A valid signature proves the claims are authentic, so the backend verifies requests without storing sessions, hence `SessionCreationPolicy.STATELESS` in [SecurityConfig](backend/src/main/java/com/chatbot/security/SecurityConfig.java). The tradeoff is that a JWT cannot be revoked, i.e., it is valid until expiry (24h, `security.jwt.token.expiration`). A Google ID token is itself a JWT, signed by Google.

### 5.1 Login flow

```
browser                      backend                         Google
   |  user clicks Google button  |                              |
   |---------------------------------------------------------->|
   |        <- ID token (a JWT signed by Google)                |
   |  POST /auth/google {idToken}|                              |
   |---------------------------->|  verify signature, audience, |
   |                             |  expiry (GoogleTokenVerifier)|
   |                             |  upsert app_user row         |
   |                             |  mint own 24h HS256 JWT      |
   |   <- {token, email, name, pictureUrl}                      |
   |  stored in localStorage; sent as Bearer header from then on|
```

- [AuthController](backend/src/main/java/com/chatbot/controller/AuthController.java), `POST /auth/google`, is the only unauthenticated API endpoint; a verification failure returns 401.
- [GoogleTokenVerifier](backend/src/main/java/com/chatbot/security/GoogleTokenVerifier.java) wraps Google's verifier library. `.setAudience(List.of(clientId))` restricts acceptance to tokens issued for this application's OAuth client id, since without it a token issued to any other website would also pass.
- [AuthServiceImpl](backend/src/main/java/com/chatbot/service/impl/AuthServiceImpl.java) looks the user up by `googleSub` (not by email, which can change), updates or creates the [User](backend/src/main/java/com/chatbot/beans/User.java) row (`app_user` table), and mints the JWT via [JwtTokenUtil](backend/src/main/java/com/chatbot/utils/JwtTokenUtil.java) (jjwt library).

### 5.2 Subsequent requests

[JwtTokenFilter](backend/src/main/java/com/chatbot/filter/JwtTokenFilter.java) (`OncePerRequestFilter`, registered via `addFilterBefore`) parses the Bearer token, and a valid signature populates `SecurityContextHolder` (Spring Security's per-request record of the caller) with the email as principal and the authorities read from the token's `roles` claim (`authoritiesFromToken`). Tokens minted before roles existed, or without the claim, default to `ROLE_USER` for backward compatibility. Invalid or expired tokens are swallowed (`catch (JwtException | IllegalArgumentException)` clears the context), and the request proceeds unauthenticated, to be rejected by the authorization rules with the JSON 401 from `SecurityConfig`'s `authenticationEntryPoint`.

The rules are ordered most-specific first, since order matters, i.e., the open `health` rule precedes the `actuator/**` admin rule. CSRF is off (no cookies, so no CSRF surface), CORS is restricted to a configured allow-list (`security.cors.allowed-origins`, defaulting to the local frontend origins), and sessions are stateless. The public paths are `OPTIONS` preflights, `/auth/**`, root and swagger paths, `/error`, and `/actuator/health[/**]` (kept open for container probes). `ROLE_ADMIN` is required for `/actuator/**` and `/internal/**`. `anyRequest().authenticated()` covers the rest, including `/query`.

### 5.3 Admin login (username/password)

The `/internal/**` sync and `/actuator/**` operational endpoints are admin-only, so the application needs an identity that carries `ROLE_ADMIN` without going through Google. [AdminAccount](backend/src/main/java/com/chatbot/security/AdminAccount.java) is that single account, i.e., it reads `security.admin.username`/`password` (defaults `admin`/`admin`, overridable via `ADMIN_USERNAME`/`ADMIN_PASSWORD`) and BCrypt-hashes the password once at construction, so the plaintext is never held in a field. `matches(user, pass)` performs a constant-time `passwordEncoder.matches` against that hash.

`POST /auth/login` ([AuthController](backend/src/main/java/com/chatbot/controller/AuthController.java)) takes a `LoginRequest` (whose `toString` masks the password), and [AuthServiceImpl.loginWithCredentials](backend/src/main/java/com/chatbot/service/impl/AuthServiceImpl.java) validates it against `AdminAccount`, throwing `BadCredentialsException` (mapped to 401) on mismatch. On success it mints a JWT with roles `["ROLE_ADMIN", "ROLE_USER"]`, and that extra role is what unlocks the `hasRole("ADMIN")` matchers. The Google path, by contrast, issues a plain `ROLE_USER` token.

On the frontend, [auth.service.ts](frontend/src/app/auth/auth.service.ts) decodes the JWT payload locally to expose an `isAdmin` signal (true when the `roles` array contains `ROLE_ADMIN`), and the chat header gates the sync/backfill button on it, so non-admins never see an action that would 403.

---

## 6. Database

### 6.1 Schema

There are five tables in the `repo` schema, created by Liquibase (not by hand, and not by Hibernate):

```
movie         id PK, title, original_title, original_language -> language,
              overview (MEDIUMTEXT), release_date, popularity,
              vote_average, vote_count, adult, video, poster/backdrop paths
genre         id PK, name
language      iso_639_1 PK, english_name, name
movie_genres  movie_id -> movie, genre_ids -> genre   (join table, many-to-many)
app_user      id PK, google_sub (unique), email, name, picture_url,
              created_at, last_login_at
```

`movie.original_language` holds ISO codes (`te`), and the `language` table maps codes to names (Telugu), which is why the system prompt requires joining through `language` instead of exposing codes. `movie_genres` models the many-to-many relationship between movies and genres.

### 6.2 Entities and repositories

JPA maps classes onto tables, and Hibernate implements the mapping. [Movie.java](backend/src/main/java/com/chatbot/beans/Movie.java) uses:

- `@Entity`, maps to the `movie` table (default: the class name).
- `@Id`, the primary key, not auto-generated, i.e., the TMDB id is used directly, making re-syncs idempotent (saving the same movie twice updates instead of duplicating).
- `@Lob` / `@Column(columnDefinition = "MEDIUMTEXT")`, since overviews exceed VARCHAR limits.
- `@ElementCollection @CollectionTable(name = "movie_genres", ...)`, maps `List<Integer> genreIds` onto the join table without a separate entity.
- `@JsonProperty("vote_average")`, since the same class is also the JSON shape TMDB returns, so the Jackson annotations sit beside the JPA ones.

[MovieRepository](backend/src/main/java/com/chatbot/repository/MovieRepository.java) is an interface extending `JpaRepository<Movie, Long>`, and Spring Data generates the implementation, i.e., `save`, `findById`, and `count` are inherited. Custom queries use `@Query` with JPQL, which queries entities and fields rather than tables and columns (`SELECT MAX(m.releaseDate) FROM Movie m`). For dynamic or performance-critical SQL the code uses `JdbcTemplate`, i.e., raw SQL with bound `?` parameters (injection-safe), as in `saveMovieGenres()`'s `INSERT IGNORE` batch and the execution of model-generated queries.

`@Transactional` on a method makes its writes commit or roll back as one unit, e.g., `addMoviesToDatabase()` is transactional so that a half-saved month cannot exist.

### 6.3 Liquibase

`spring.jpa.hibernate.ddl-auto=validate` means Hibernate only checks that the entities and tables agree, and refuses to start on a mismatch. The schema itself is built by Liquibase at startup, before that validation, i.e., [db.changelog-master.yaml](backend/src/main/resources/db/changelog/db.changelog-master.yaml) includes numbered changesets from [changes/](backend/src/main/resources/db/changelog/changes/). Each changeset runs exactly once per database, tracked in the `DATABASECHANGELOG` table, so fresh installs replay the full history while existing installs apply only what is new.

There are two conventions in this repo, i.e., every `CREATE TABLE` carries a `preConditions: not tableExists / onFail: MARK_RAN` guard (so Liquibase adopts databases that predate it), and merged changesets are never edited, since schema changes are always new files.

---

## 7. Sync pipeline

Three triggers call the same method:

- **Startup**: [SyncScheduler.syncOnStartup()](backend/src/main/java/com/chatbot/scheduler/SyncScheduler.java), `@EventListener(ApplicationReadyEvent.class)`.
- **Daily**: `@Scheduled(cron = "0 30 3 * * *")`, format `second minute hour day month weekday`, i.e., 03:30 nightly. Both are `@Async` (a background thread, so startup is not blocked), enabled by `@EnableScheduling`/`@EnableAsync` on [MovieChatBot.java](backend/src/main/java/com/chatbot/MovieChatBot.java).
- **Manual**: `POST /internal/sync` from the UI's sync panel ([MovieBackFillController](backend/src/main/java/com/chatbot/controller/MovieBackFillController.java)). It returns 202 immediately and runs in the background, since a full sync outlasts any HTTP timeout, or 409 if one is already running. The UI polls `GET /internal/sync-status` (which includes a `syncInProgress` flag) until completion.

### 7.1 SyncServiceImpl.syncNow()

[SyncServiceImpl](backend/src/main/java/com/chatbot/service/impl/SyncServiceImpl.java), step by step:

1. **Single-flight guard.** The method is `synchronized` and sets an `AtomicBoolean syncInProgress`, i.e., the flag `/query` checks to return 503. It is reset in a `finally` block, so a crashed sync cannot block queries permanently.
2. **Resume point.** The `sync_log` table holds one row (id=1) with `lastSyncDate`. The start is that date + 1 day; if the row is missing, it falls back to `MAX(release_date)` + 1, then to `sync.default-start-date` (01-01-1990). The end is always yesterday, since same-day TMDB data is still changing.
3. **Batching.** `buildMonthlyBatches()` slices the range into calendar months, and a month is the retry unit.
4. **Reference data first.** Languages and genres are refreshed before movies, so that foreign keys resolve.
5. **Parallel fetch.** A fixed pool of `sync.thread-count` (12) threads runs one batch each via `CompletableFuture.runAsync`. The MDC context is explicitly copied into each worker thread, since MDC is thread-local and, without the copy, the worker log lines would lose the correlation id.
6. **Failure routing.** Failed batches pass through [RetryPolicy.isRetryable()](backend/src/main/java/com/chatbot/errorhandling/RetryPolicy.java), which walks the exception cause chain. Network errors (`ResourceAccessException`), 5xx, 429, and transient DB errors are retryable, i.e., published to RabbitMQ for delayed retry. Everything else (a 401 from a bad API key, or the overlap guard) would fail identically on retry, i.e., logged and skipped.
7. **Genre pairs, sequentially.** Each batch returns its movie-genre pairs, and all are saved at the end in one sequential `INSERT IGNORE` batch. Parallel inserts into the same join table caused deadlocks, so serializing the writes avoids them, and `IGNORE` makes re-runs harmless.
8. **Watermark.** `lastSyncDate` advances to the end of the last contiguous successful batch. If June failed and July succeeded, the watermark stops at May, June is retried on the next run, and July's re-sync is skipped by the overlap guard.

### 7.2 One batch

[MovieBackFillServiceImpl.addMoviesToDatabase()](backend/src/main/java/com/chatbot/service/impl/MovieBackFillServiceImpl.java) first runs the overlap guard, i.e., it expands the range into a per-day list, queries `MovieRepository.findExistingDates(...)`, and if any day already has movies it throws `NonRetryableException`, preventing double-inserts from overlapping manual syncs. [TMDBServiceApi.getMoviesInDateRange()](backend/src/main/java/com/chatbot/client/TMDBServiceApi.java) then pages through TMDB's `/discover/movie` (20 movies per page, capped at TMDB's limit of 500 pages, with a warning to narrow the range). Its shared `fetch` helper treats an empty body as a transient `ResourceAccessException` and retries HTTP 429 up to three times, honoring the `Retry-After` header (falling back to linear `1000 * attempt` ms backoff), so the parallel batches do not exceed TMDB's rate limit. Auth headers are added to every request by an interceptor on the dedicated `tmdbRestTemplate` ([TMDBConfig](backend/src/main/java/com/chatbot/config/TMDBConfig.java)), so callers never handle credentials.

Movies are saved with `genreIds` emptied and the `(movieId, genreId)` pairs returned to the caller, so the join-table `INSERT IGNORE` happens once, sequentially, at the end of the sync rather than inside the parallel phase, since parallel inserts into `movie_genres` deadlocked.

---

## 8. RabbitMQ retry loop

RabbitMQ is a message broker, i.e., producers publish messages, queues hold them, and consumers receive them asynchronously. Routing goes through an **exchange**, i.e., producers publish to an exchange, which forwards to the queues matching the message's **routing key** (a `DirectExchange` matches exactly). A queue can declare a **TTL** (message lifetime) and a **dead-letter exchange** (DLX), i.e., the destination for its expired messages. Combining the two produces delayed delivery, for which RabbitMQ has no native primitive.

All pieces are declared as beans in [RabbitMQConfig](backend/src/main/java/com/chatbot/config/RabbitMQConfig.java) and created on the broker at startup:

```
FailedBatchProducer.publish(msg)
        |
        v
movie.sync.retry.delay.exchange ---> movie.sync.retry.delay.queue
                                       TTL = 5 min, no consumer
                                       x-dead-letter-exchange ----+
                                                                  v
                                     movie.sync.failed.exchange ---> movie.sync.failed.queue
                                                                          |
                                                                          v
                                                              FailedBatchConsumer (re-runs the batch)
```

The delay queue has no consumer, i.e., messages sit until the 5-minute TTL (`sync.retry-delay-ms`) expires, then dead-letter into the failed exchange, whose queue does have a consumer. The result is delivery delayed by 5 minutes.

The message ([FailedBatchMessage](backend/src/main/java/com/chatbot/messaging/FailedBatchMessage.java)) carries the batch dates, an `attemptCount`, and the error, serialized as JSON by the `JacksonJsonMessageConverter` bean.

[FailedBatchConsumer](backend/src/main/java/com/chatbot/messaging/FailedBatchConsumer.java) is one `@RabbitListener(queues = FAILED_QUEUE)` method, called per message. It re-runs the batch, including saving its own genre pairs, since the main sync has already finished. On another failure: a non-retryable error is dropped with a log; a retryable error with `attemptCount < sync.max-retry-attempts` (3) calls [FailedBatchProducer.republish()](backend/src/main/java/com/chatbot/messaging/FailedBatchProducer.java), which increments the count and re-enqueues; and on exhausted attempts the message moves to the parking-lot queue (`movie.sync.failed.parking.queue`), which has no consumer, i.e., parked batches stay visible in the management UI for inspection and manual replay instead of being lost.

A month retried successfully by the consumer does not update the sync watermark itself, i.e., the next scheduled sync encounters its data, the overlap guard reports "already present", and the run counts that month as synced, which is how the watermark catches up.

The management UI at http://localhost:15673 (moviebot/moviebot), Queues tab, shows the loop at runtime, i.e., messages accumulate in the delay queue, move to the failed queue at TTL expiry, and drain as the consumer processes them.

---

## 9. Cross-cutting concerns

### 9.1 Logging: MDC + AOP

[LoggingAspect](backend/src/main/java/com/chatbot/logging/LoggingAspect.java) complements the correlation id filter, i.e., instead of entry/exit logging pasted into every method, an aspect declares pointcuts (package patterns such as `execution(* com.chatbot.service..*(..))`) and advice that runs around every matching call. The aspect tags the MDC with the current layer (CONTROLLER, SERVICE, REPOSITORY, EXTERNAL, SCHEDULER), times the call, and logs the method, parameter names, arguments, and duration uniformly via [LoggingBean](backend/src/main/java/com/chatbot/logging/LoggingBean.java). If the call started outside an HTTP request (a scheduler thread, where `CorrelationIdFilter` never ran), the aspect generates and owns a correlation id itself, removing it in a `finally`. Arguments are masked per parameter rather than per layer, i.e., `maskSensitive` replaces any argument whose parameter name contains a sensitive keyword (`token`, `secret`, `password`, `jwt`, `authorization`, `clientid`, ...) with `***`, so `/query`'s `userInput` is logged in full while a Google ID token or admin password is not. `@AfterThrowing` on controllers logs a `NonRetryableException` at WARN (an expected rejection) and everything else at ERROR with a stack trace.

`logging.pattern.console` in [application.properties](backend/src/main/resources/application.properties) prints `[correlationId] [layer] logger : message` on every line. A grep on a correlation id returns one request across layers and threads, and the layer column shows how far it got.

### 9.2 Error handling

[GlobalExceptionHandler](backend/src/main/java/com/chatbot/errorhandling/GlobalExceptionHandler.java) is a `@RestControllerAdvice`, i.e., exception handlers applying to all controllers. Validation failures (`BindException`, `ConstraintViolationException`) become structured 400s carrying the first human-readable message; `LlmUnavailableException` becomes a 503; and anything unhandled becomes a structured 500 carrying a generic translated message, never the raw exception text, which can leak connection strings or internals. The shape is always [GlobalError](backend/src/main/java/com/chatbot/errorhandling/GlobalError.java): `{statusCode, status, reason, timestamp}`. Controllers contain no try/catch boilerplate, and the details stay in the server log.

### 9.3 Validation and i18n

Bean validation appears in three forms, i.e., field annotations (`@NotBlank` on the query body), parameter rules (`@Pattern(regexp = "te")` on the transliterate `lang` param, since Telugu is the only transliteration language), and the custom class-level [@ValidDateRange](backend/src/main/java/com/chatbot/validation/ValidDateRange.java) / [DateRangeValidator](backend/src/main/java/com/chatbot/validation/DateRangeValidator.java), which checks the manual-sync range for format (`dd-MM-yyyy`, strictly parsed), ordering, and not-after-yesterday.

Every validation message is a key, e.g., `{validation.dateRange.order}`, resolved against [messages.properties](backend/src/main/resources/messages.properties) / `messages_fr` / `messages_te`, selected by the `Accept-Language` header (set by the frontend's language interceptor) and accessed in code via `LocaleContextHolder`. A message missing from the French or Telugu file falls back to English.

### 9.4 Observability

Actuator exposes `/actuator/health` (public, for container probes) and `/actuator/info`. `/actuator/health` stays open, and everything else under `/actuator` is `ROLE_ADMIN`-only ([SecurityConfig](backend/src/main/java/com/chatbot/security/SecurityConfig.java)).

springdoc generates the interactive API explorer at `/swagger-ui.html`. [SwaggerController](backend/src/main/java/com/chatbot/controller/SwaggerController.java) redirects `/` there, and [SwaggerDocumentationConfig](backend/src/main/java/com/chatbot/config/SwaggerDocumentationConfig.java) registers a global `bearerAuth` JWT scheme so the UI shows an **Authorize** button (paste a token from `/auth/login` or `/auth/google`, then "Execute" sends it). The admin/`/internal` controller is annotated `@Hidden` and actuator is left out of the spec, so the explorer lists only the application's own API (`/auth`, `/query`, `/transliterate`). The OpenAPI document is static and served to everyone identically, i.e., Swagger cannot show a different endpoint list per logged-in role, so hiding the sensitive operations from the spec, together with the role checks on the endpoints themselves, is what keeps them out of reach.

---

## 10. Frontend

Angular 22, i.e., standalone components (no NgModules), signals for state, and functional interceptors. The UI is about ten files under [frontend/src/app/](frontend/src/app/).

### 10.1 Bootstrap

Execution starts at [main.ts](frontend/src/main.ts): `bootstrapApplication(AppComponent, appConfig)`. [app.config.ts](frontend/src/app/app.config.ts) declares the app-wide providers (Angular's dependency injection, the same concept as Spring's), i.e., the HTTP client with two interceptors, and the translation module.

[AppComponent](frontend/src/app/app.component.ts) is a router-less switch:

```ts
@if (auth.authenticated()) { <app-chat /> } @else { <app-login /> }
```

`auth.authenticated()` reads a **signal**, i.e., a reactive value holder: `signal(false)` creates one, `.set(true)` writes, calling it reads, and any template that read it re-renders on change. There are no manual DOM updates. Components obtain dependencies via `inject(AuthService)`, i.e., Angular's equivalent of Spring's constructor injection.

### 10.2 Auth files

There are four files in [auth/](frontend/src/app/auth/):

- [login.component.ts](frontend/src/app/auth/login.component.ts) renders the Google button. Google's GIS script (loaded in [index.html](frontend/src/index.html)) draws it and passes an ID token to a callback. The callback arrives outside Angular's change-detection zone, hence the `zone.run(...)` wrapper, since without it the UI does not refresh after login. The component polls up to 50 x 100ms for the GIS script before rendering, to handle slow networks.
- [auth.service.ts](frontend/src/app/auth/auth.service.ts) exchanges the ID token at `POST /auth/google`, stores the JWT and profile in `localStorage` (which survives reloads), and exposes the `user`/`authenticated` signals. On startup it restores state by decoding the stored JWT locally and checking `exp`, i.e., an expired token means starting logged out.
- [auth.interceptor.ts](frontend/src/app/auth/auth.interceptor.ts), i.e., an interceptor is middleware on outgoing HTTP, the frontend counterpart of a servlet filter. It clones each request (requests are immutable) to add the Bearer header, skipping `/auth` and assets, and watches responses, i.e., any 401 triggers `logout()`, returning an expired session to the login screen.
- [auth.config.ts](frontend/src/app/auth/auth.config.ts), i.e., the Google OAuth client id, which must match the backend's `security.google.client-id`.

The second interceptor, inline in app.config.ts, adds `Accept-Language` to every request, and this header selects the language of the backend messages.

### 10.3 Chat screen

[chat.component.ts](frontend/src/app/chat/chat.component.ts) is the largest frontend file, i.e., signals and small methods for the current result, loading flag, theme, language, sync panel state, and transliteration toggle. The details:

- **Theme and language** persist in `localStorage`, and switching goes through ngx-translate (`translate.use('te')`), which loads [assets/i18n/te.json](frontend/src/assets/i18n/te.json). The UI strings live in those JSON files, i.e., the frontend counterpart of the backend's `messages_*.properties`.
- **Transliteration**: with Telugu active and the toggle on, the space is typed natively (so the caret and any mid-text edit behave normally), and `onInput` then transliterates the romanized word that now precedes the caret via `transliterateWord(caret - 1)`; Enter does the same for the word at the caret before submitting. `transliterateWord` finds the trailing `[a-zA-Z]+` run, calls `GET /transliterate` (1.5s timeout), and substitutes the first suggestion, e.g., "evaru" becomes "ఎవరు". It is caret- and race-safe, i.e., before writing it re-checks `el.value.slice(start, wordEnd) === word`, so anything typed during the network round-trip is never clobbered, and `setInputAndCaret` restores the caret (shifted by the length delta) after Angular writes the value back. The backend proxies Google Input Tools ([GoogleTransliterateClient](backend/src/main/java/com/chatbot/client/GoogleTransliterateClient.java)), so the browser never calls Google directly.
- **The sync panel** drives `/internal/*`, including the manual date-range sync.

### 10.4 Rendering results

[chat.component.html](frontend/src/app/chat/chat.component.html) picks a layout from the data shape, i.e., one row x one column renders as a single stat, one row x many columns as a detail card, 2-9 rows as cards, and more as a table. Column headers are formatted from the SQL names (`vote_average` -> "Vote Average") by helpers at the bottom of the component class.

### 10.5 Reaching the backend

Service classes call relative URLs, e.g., `/query`, never `http://localhost:8081/query`. A proxy in front makes them same-origin, which avoids CORS configuration:

- **Native dev** (`npm start`): the dev server applies [proxy.conf.json](frontend/proxy.conf.json), forwarding `/auth`, `/query`, `/transliterate`, and `/internal` to `localhost:8080`.
- **Docker**: nginx serves the compiled app and forwards the same prefixes to `backend:8080` ([nginx.conf](frontend/nginx.conf)).

A new top-level API prefix must be added to both proxy files.

HTTP calls return RxJS `Observable`s, i.e., asynchronous values; `.subscribe(...)` registers the handler, and `firstValueFrom(...)` converts to a Promise for `async/await` code.

---

## 11. Tests and CI

```bash
cd backend && mvn test                 # everything
mvn test -Dtest=SyncServiceImplTest    # one class
mvn verify                             # tests + JaCoCo coverage
open target/site/jacoco/index.html     # coverage report
```

[backend/src/test/java](backend/src/test/java/com/chatbot/) mirrors the main tree one-to-one. The stack is JUnit 5 + Mockito, i.e., the class under test is constructed directly with mocks for its dependencies, behaviors are stubbed (`when(repo.findById(1L)).thenReturn(...)`), and outcomes and interactions are asserted (`verify(producer).publish(...)`). Constructor injection is what makes this possible without a Spring container, which keeps the tests fast.

Controller tests use MockMvc, which drives the web layer with simulated HTTP (`mockMvc.perform(post("/query"))` plus status/JSON asserts) without opening a port. [MovieChatBotControllerValidationTest](backend/src/test/java/com/chatbot/controller/MovieChatBotControllerValidationTest.java) additionally wires bean validation and the exception handler into the MockMvc setup, covering the 400/503 paths. A controller's test documents its expected request/response behavior.

The frontend has no test suite at present, i.e., verification is manual through the browser.

CI ([.github/workflows/build.yml](.github/workflows/build.yml)) runs on every push and PR, i.e., JDK 21, `mvn verify`, then SonarQube analysis. A red build is usually a failing test, and `mvn verify` reproduces it locally.

---

## 12. Recipes

Each recipe lists every file involved, and a typical feature combines several recipes.

### 12.1 Add an API endpoint

Example: `GET /internal/movie-count` returning `{"count": 1234567}`.

1. Service interface: add `long countMovies();` to [MovieIntelService](backend/src/main/java/com/chatbot/service/MovieIntelService.java) (or whichever service fits the domain).
2. Implementation: inject `MovieRepository`, return `movieRepository.count()` (inherited from `JpaRepository`).
3. Controller - [MovieBackFillController](backend/src/main/java/com/chatbot/controller/MovieBackFillController.java) owns `/internal`:
   ```java
   @GetMapping("/movie-count")
   public Map<String, Long> movieCount() {
     return Map.of("count", movieIntelService.countMovies());
   }
   ```
4. Security: nothing is required, since `/internal/**` is already gated to `hasRole("ADMIN")`, so the endpoint requires an admin token (from `/auth/login`), and it is hidden from Swagger by the controller's `@Hidden`. [SecurityConfig](backend/src/main/java/com/chatbot/security/SecurityConfig.java) only changes if the endpoint needs different access (public, or any authenticated user).
5. Proxies: nothing for `/internal/...`. A new top-level prefix (e.g., `/stats`) additionally needs entries in [proxy.conf.json](frontend/proxy.conf.json) and [nginx.conf](frontend/nginx.conf)'s location regex.
6. Test: a method in the controller's test class; mock the service, assert the JSON.
7. Frontend (optional): a method in [movie.service.ts](frontend/src/app/chat/movie.service.ts), a signal + call in the component.

### 12.2 Add a database column

Example: store each movie's `runtime`.

1. Migration first, i.e., a new file `backend/src/main/resources/db/changelog/changes/003-add-movie-runtime.yaml`:
   ```yaml
   databaseChangeLog:
     - changeSet:
         id: 003-add-movie-runtime
         author: <name>
         changes:
           - sql:
               sql: ALTER TABLE `repo`.`movie` ADD COLUMN `runtime` INT NULL;
   ```
   Register it in [db.changelog-master.yaml](backend/src/main/resources/db/changelog/db.changelog-master.yaml) with an `- include:` line. An existing changeset is never edited, i.e., always a new file.
2. Entity: add the field to [Movie.java](backend/src/main/java/com/chatbot/beans/Movie.java), with `@JsonProperty` matching TMDB's field name. (Check TMDB's docs, since `/discover/movie` omits fields that only `/movie/{id}` returns, i.e., a field TMDB does not send stays null.)
3. Restart: Liquibase applies 003, then Hibernate validates entity == schema. Skipping step 1 fails startup with a validation error naming the missing column.
4. Teach the model (optional): to make the column queryable in chat, add it to the DDL in [LlmPrompts.SYSTEM_PROMPT](backend/src/main/java/com/chatbot/constant/LlmPrompts.java). The model generalizes somewhat to schema edits; for reliable use, add question/SQL examples to [backend/training/data/](backend/training/data/) and re-train, since the system prompt inside the training data must match the updated prompt ([backend/training/README.md](backend/training/README.md)).
5. Re-sync: existing rows hold null, and new syncs fill the column going forward.

### 12.3 Add or change a configuration property

1. Define it in [application.properties](backend/src/main/resources/application.properties) with a default.
2. Read it via `@Value("${my.prop:default}")`, or add it to a `@ConfigurationProperties` class such as [LlmConfig](backend/src/main/java/com/chatbot/config/LlmConfig.java) for a group.
3. If Docker must override it: add the env-var form (`my.prop` -> `MY_PROP`) to [docker-compose.yml](docker-compose.yml)'s backend `environment:` block, and to `.env` if secret.

### 12.4 Add a UI language

Example: Hindi.

1. Frontend strings: `frontend/src/assets/i18n/hi.json` (copy `en.json`, translate values).
2. Switcher: add `{ code: 'hi', label: '...' }` to the `languages` array in [chat.component.ts](frontend/src/app/chat/chat.component.ts).
3. Backend messages: `backend/src/main/resources/messages_hi.properties` mirroring [messages.properties](backend/src/main/resources/messages.properties).
4. Hindi questions already reach the model unchanged (LLaMA 3 is multilingual), and quality depends on the training examples, i.e., consider Hindi rows in the training data.
5. Romanized-to-Hindi-script input additionally requires relaxing `@Pattern(regexp = "te")` in [TransliterateController](backend/src/main/java/com/chatbot/controller/TransliterateController.java) and the transliterate client's language handling.

### 12.5 Tune sync and retry behavior

All settings are in [application.properties](backend/src/main/resources/application.properties), with no code changes: `sync.thread-count` (parallelism vs. TMDB rate limits), `sync.retry-delay-ms` (the queue TTL; the delay queue is created with this value, so changing it after the queue exists requires deleting the queue in the management UI first, a RabbitMQ constraint), `sync.max-retry-attempts`, `sync.default-start-date`, `query.timeout-ms`, and `llm.read-timeout-ms`.

---

## 13. Debugging

**Backend, recommended setup.** Run the infrastructure in Docker and the application in the IDE, i.e., start `mysql rabbitmq llm` via compose and run `MovieChatBot` in IntelliJ with Debug. The properties file already points at the Docker ports. This gives breakpoints, devtools hot reload, and fast restarts. (On the first run, enable Lombok annotation processing.)

**Backend inside Docker.** For container-only bugs, uncomment `BACKEND_DEBUG` in `.env`, run `docker compose up -d --no-deps backend`, then attach via IntelliJ -> Run -> Edit Configurations -> Remote JVM Debug -> localhost:5005. Code changes require an image rebuild, so native is faster for iteration.

**Frontend.** Use the dev server (`npm start`, http://localhost:4200), since the containerized build is minified. In the browser DevTools, breakpoints map to TypeScript via source maps, and the Network tab shows every API call, its Authorization header, and the response shape.

**Logs.** Use `docker compose logs -f backend`, or the IDE console natively. Grepping the 8-character correlation id from any line of a failing request returns that request across all layers and threads, and the layer column shows how far it got.

**"No results found" on a reasonable question.** Three causes produce the same message, i.e., the model returned no SQL ("Empty SQL for input"), invalid SQL ("Model produced invalid SQL"), or valid SQL with zero rows. The backend log distinguishes them, and the logged SQL can be run directly in a MySQL shell.

**Direct access to individual services:**

```bash
# MySQL shell
docker exec -it movie-mysql mysql -umoviebot -pmoviebot repo
#   SHOW TABLES; SELECT COUNT(*) FROM movie; SELECT * FROM sync_log;

# Call the LLM directly, bypassing the app
curl -s http://localhost:11436/v1/chat/completions -H 'Content-Type: application/json' \
  -d '{"model":"movie-sql-q4_k_m","messages":[{"role":"system","content":"<SYSTEM_PROMPT>"},{"role":"user","content":"top 5 telugu movies"}],"max_tokens":300,"temperature":0.05}'

# Health
curl -s http://localhost:8081/actuator/health
```

The RabbitMQ management UI is at http://localhost:15673 (moviebot/moviebot), i.e., queue depths, message payloads (Get Messages), and consumer status. Swagger is at http://localhost:8081/swagger-ui.html, and a JWT for protected endpoints is in the browser at DevTools -> Application -> Local Storage -> `auth_token`.

**Slow or timed-out query.** The 5s `MAX_EXECUTION_TIME` hint plus the 7s transaction timeout bound every query. `EXPLAIN <the logged SQL>` in the MySQL shell shows the execution plan, which is typically a full scan on `movie` from an unindexed predicate.

---

## 14. Pitfalls

**Wrong port for the run mode.** Most connection failures come from mixing run modes, i.e., [application.properties](backend/src/main/resources/application.properties) carries native-run addresses (`localhost:3307`, `localhost:5673`, `localhost:11436`), while inside containers the compose env vars replace them with service-name addresses (`mysql:3306`, `rabbitmq:5672`, `llm:8080`). A failing component is usually configured with the other mode's address.

**503 from /query.** A sync is running (startup, 3:30 AM, or manual). This is expected behavior, so wait or check the logs/sync panel.

**401 on everything after it worked yesterday.** The JWT lives 24 hours, and the interceptor logs out on a 401, so sign in again. If it persists, check whether `JWT_SECRET` changed between token issue and verification.

**Sign-in button dead or "origin not allowed".** The application's URL must be an Authorized JavaScript origin on the Google OAuth client (`http://localhost:4201` for Docker, `http://localhost:4200` for dev), and the client id must match in both [auth.config.ts](frontend/src/app/auth/auth.config.ts) and `security.google.client-id`.

**New endpoint 404s through the frontend but works via curl to 8081.** The new top-level prefix is missing from [nginx.conf](frontend/nginx.conf) / [proxy.conf.json](frontend/proxy.conf.json), i.e., only `/query`, `/internal`, `/auth`, and `/transliterate` are forwarded.

**Startup fails: "Schema-validation: missing column".** An entity changed without a Liquibase changeset. The fix is a migration, not `ddl-auto=update`.

**Sync finished but months are missing.** Batches may still be cycling through the RabbitMQ delay queue (5-minute intervals, 3 attempts). Exhausted batches are ERROR-logged, and the advanced sync panel re-runs them. A TMDB 401 (a bad or missing token in `.env`) is non-retryable and skips batches immediately.

**Date formats.** The sync APIs and properties use `dd-MM-yyyy` (`01-01-1990`), strictly parsed, while TMDB and MySQL use ISO `yyyy-MM-dd` internally. The conversion is in [DateConversionUtils](backend/src/main/java/com/chatbot/utils/DateConversionUtils.java).

**Secrets.** Use `.env` (gitignored) for Docker, `application-local.properties` (gitignored) for native runs, and GitHub Actions secrets in CI, never in code. Compose passes `JWT_SECRET` through as optional (`${JWT_SECRET:-}`), so nothing refuses to start without it, but if it is unset (or left at the baked-in dev default) the backend falls back to a publicly known signing key and logs a warning, and anyone could then forge tokens. Set a real `JWT_SECRET` before exposing the backend beyond localhost.

**CORS allow-list.** CORS is restricted to `security.cors.allowed-origins` (default `http://localhost:4201,http://localhost:4200`). The browser only talks same-origin to nginx/dev-proxy, so CORS rarely applies in practice, but a browser calling the backend from any other origin is rejected. Add the origin to `CORS_ALLOWED_ORIGINS` if the backend is exposed elsewhere; `curl` and Swagger send no `Origin` and are unaffected.

**SQL execution safety.** The design uses multiple safeguards rather than trust, i.e., a read-only transaction, statement-splitting stripped, two timeout layers, and errors converted to empty results. Restricting the MySQL user to SELECT-only on the data tables would be a further hardening step.
