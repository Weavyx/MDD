# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is **P6-Full-Stack-reseau-dev** (a MDD — "Monde de Dev" — social network project from the OpenClassrooms Java/Angular full-stack path).

- `back/` — Spring Boot skeleton (`MddApiApplication` only). The package layout under `com.openclassrooms.mddapi` (`controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`, `security/`, `exception/`) is scaffolded (each dir held by a `.gitkeep`) but empty — no entities, controllers, or services written yet.
- `front/` — Angular 21 app (standalone-component style: `app.config.ts` / `app.routes.ts`, no `NgModule`). `@angular/material` and `@angular/cdk` are already added as dependencies, but no routes or feature components exist yet (`app.routes.ts` is an empty array).
- `front-legacy/` — the previous Angular 14 scaffold (NgModule-based, `@angular/material` wired in with a sample home page), kept for reference during the front-end migration. Do not build new features here; check it for patterns (Material setup, routing, sample page) worth porting into `front/` before writing from scratch.

## Commands

### Backend (`back/`, Java 21, Spring Boot 4.1.0, Maven)

```bash
./mvnw spring-boot:run       # run the API
./mvnw test                  # run all tests
./mvnw test -Dtest=ClassName # run a single test class
./mvnw package                # build jar
```

### Frontend (`front/`, Angular 21, standalone components, Vitest)

```bash
npm install
npm start        # ng serve, http://localhost:4200/
npm run build    # ng build -> dist/
npm test         # ng test (runs via Vitest)
```

No e2e test runner is configured for `front/`.

### Database (Docker)

```bash
docker compose up -d    # starts a MySQL 8.4 container (mdd-mysql) on port ${MYSQL_PORT}
```

`docker-compose.yml` reads `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_PORT` from a root-level `.env` (gitignored; see `.env.example` for the expected keys).

## Architecture notes

- Backend follows the standard Maven layout under `com.openclassrooms.mddapi`. Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `mysql-connector-j` (runtime), `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (JWT auth via Spring's native `JwtEncoder`/`JwtDecoder`, no external JJWT-style library), `spring-boot-starter-validation`. Persistence is MySQL/JPA-based once entities are added.
- Backend config is split across two profile-scoped files:
  - `application.properties` — checked into git, holds non-secret config (`spring.datasource.url`/`username` with `MYSQL_*` env var placeholders, JPA/Hibernate settings, `mdd.jwt.*` settings). Activates the `local` profile by default.
  - `application-local.properties` — gitignored, holds only `spring.datasource.password` so no secret (even a dev one) is committed. **This file is currently still tracked by git from an earlier commit** — if you touch it, remember to `git rm --cached` it so `.gitignore` can actually take effect, otherwise the password will get committed on the next `git add` of that path.
- Frontend uses Angular's newer standalone bootstrapping (`bootstrapApplication` in `main.ts` via `app.config.ts`), not the `NgModule` pattern used in `front-legacy/`. Add new routes to `app.routes.ts`, not an `AppRoutingModule`.
