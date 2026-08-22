# preppanel-backend

> Fastest way to run the whole project (this + frontend + Postgres) is one
> command from the repo root - see the top-level `README.md`. Everything
> below is for running/developing this backend on its own.

Spring Boot API for PrepPanel. v1 scope right now: self-built JWT auth, Postgres schema via
Flyway, and a generic behavioral-question interview loop (no LLM, no company research yet —
those come later per the build plan).

## Stack
- Java 21, Spring Boot 3.3
- Spring Security + JJWT (self-built auth, BCrypt password hashing)
- PostgreSQL + Flyway migrations
- Spring Data JPA

## Running locally

### 1. Start Postgres
```
docker compose up -d
```
This starts a local Postgres 16 container on `localhost:5432` with db `preppanel` /
user `preppanel` / password `preppanel` (matches the defaults in `application.yml` —
override with `DB_USERNAME` / `DB_PASSWORD` env vars if you change them).

If Docker gives you trouble, installing Postgres 16 natively on Windows and creating a
`preppanel` database/user with those same credentials works just as well for local dev.

### 2. Run the app
You need a local JDK 21+ and Maven (or just use IntelliJ's built-in Maven support / run
`PrepPanelApplication.java` directly once the project is imported).

```
mvn spring-boot:run
```

Flyway runs automatically on startup and creates all tables + seeds ~20 behavioral
questions into `question_bank`.

> Note: this project doesn't ship a `mvnw` wrapper yet since it was scaffolded without
> network access to Maven Central. Generate one locally with `mvn -N wrapper:wrapper`
> once you have Maven installed, or just use your local `mvn` / IntelliJ's Maven integration.

### 3. Try it
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123"}'

# -> returns accessToken + refreshToken. Use accessToken as a Bearer token below.

# Start an interview session (returns question 1 of 5)
curl -X POST http://localhost:8080/api/interviews \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"role":"Backend Engineer","mode":"text"}'

# Answer it (returns question 2, or completed:true after question 5)
curl -X POST http://localhost:8080/api/interviews/<sessionId>/answers \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"answerText":"Here is my answer..."}'

# View full session transcript
curl http://localhost:8080/api/interviews/<sessionId> \
  -H "Authorization: Bearer <accessToken>"
```

## What's implemented
- `POST /api/auth/register|login|refresh|logout|logout-all`
- `POST /api/interviews` — creates a session, serves question 1 from the generic behavioral bank
- `POST /api/interviews/{id}/answers` — records the answer, serves the next question (fixed at 5 questions/session for v1), auto-completes on the last one
- `POST /api/interviews/{id}/complete` — force-complete a session early
- `GET /api/interviews/{id}` — full transcript (questions + answers)

## What's deliberately NOT here yet
- LLM question generation / scoring (`LlmClientService`, `ScoringController`)
- Company research pipeline (`ResearchController`, Jsoup scraper)
- Voice mode / delivery metrics
- Frontend (separate `preppanel-frontend` repo, not started)

## Auth design notes
- Access tokens are short-lived JWTs (15 min, HMAC-signed).
- Refresh tokens are opaque random strings, not JWTs — only their SHA-256 hash is stored
  in `refresh_tokens`, so a leaked DB row alone can't be replayed.
- Refresh rotates on every use: presenting a token revokes it and issues a new pair.
  A revoked/reused token is rejected outright (basic reuse-detection).
- Passwords hashed with BCrypt (strength 12).

## Docker (for later)
A `Dockerfile` is included for when you're ready to containerize the app itself —
not needed for local dev, where `mvn spring-boot:run` against Postgres is simpler.
