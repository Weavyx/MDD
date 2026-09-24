# CLAUDE.md

MDD ("Monde de Dév"), OpenClassrooms P5 option B. Mono-repo: `back/` Spring Boot API, `front/` Angular app. `front-legacy/` is a gitignored Angular 14 reference: never build or edit there.

## Commands

### Backend (`back/`)

```bash
./mvnw test            # Surefire only: *Test classes (pure Mockito), no Docker needed
./mvnw verify          # + Failsafe: *IT classes — Docker REQUIRED (Testcontainers mysql:8.4)
./mvnw test -Dtest=ClassName
./mvnw spring-boot:run # needs MYSQL_* and JWT_SECRET in the environment (see below)
```

- JaCoCo report is produced by `verify` in `target/site/jacoco/` (one agent for both Surefire and Failsafe; no threshold, no excludes on purpose).
- Maven does **not** read the root `.env`: `mise.toml` loads it into the shell (`mise trust` once), otherwise export its variables before `verify` or `spring-boot:run` (`mise exec -- ./mvnw verify` outside an activated shell). `application-local.properties` (gitignored) holds only `spring.datasource.password=${MYSQL_PASSWORD}` and `mdd.jwt.secret=${JWT_SECRET}`; `MddApiApplicationIT` fails with `Could not resolve placeholder 'JWT_SECRET'` if the variable is missing. `./mvnw test` alone needs nothing.
- `docker compose up -d` at the root starts `mdd-mysql` from the same `.env`.

### Frontend (`front/`)

`npm install`, `npm start` (4200), `npm run build`, `npm test` (Vitest; headless: `npx ng test --watch=false`), `npm run format`. No e2e runner yet.

## Backend conventions that differ from defaults

- **Test naming decides the runner**: `*Test` → Surefire (unit, `@ExtendWith(MockitoExtension)`); `*IT` → Failsafe (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`). Spring Boot 4 / Framework 7: use `@MockitoBean`, not `@MockBean`.
- DB-backed ITs extend `AbstractContainerIT`, which starts one shared MySQL container in a `static {}` block. Do **not** add `@Testcontainers`/`@Container`: the JUnit extension would stop the inherited static container after the first test class.
- Repository ITs assert eager loading with `Hibernate.isInitialized(...)` before any access (project pattern); `entityManager.detach` does not work for this.
- Controller ITs authenticate with `jwt().jwt(j -> j.subject("<userId>"))`; the `JwtDecoder` is a `@MockitoBean` that is never stubbed, so no test exercises an invalid/expired token — do not claim otherwise.
- Test quality was checked by **manual mutation** (break `src/main`, expect red, restore); `git diff src/main/java` must be empty after any such check. No PIT.

## Backend architecture decisions

- Identity comes only from the JWT `sub` (= numeric user id) via `@AuthenticationPrincipal Jwt`. No user id ever appears in a URL or body; user-relative resources live under `/api/users/me/...` (profile, subscriptions, feed). `GET /api/auth/me` was removed deliberately (#13).
- JWT: Spring Security's native `JwtEncoder`/`JwtDecoder` (Nimbus), HS256 set explicitly, claims `iss`/`iat`/`exp`/`sub` only, 24 h, no refresh, no logout endpoint, no roles. The decoder validates signature and `exp` only (no issuer validator).
- Errors: one `@RestControllerAdvice` (`GlobalExceptionHandler`) → `ErrorResponse`; validation errors carry `fieldErrors`. Two cases are **intentionally** outside that format and pinned by tests: 401 (empty body + `WWW-Authenticate`, from `BearerTokenAuthenticationEntryPoint`) and malformed JSON (Spring default). Don't add a catch-all `Exception` handler (it would turn 401s into 500s).
- Layered monolith, packages by layer, concrete services without interfaces, one DTO per action (`*Request`/`*Response`, Lombok `@Data`), manual inline mapping, no MapStruct. Entities are immutable except `User`; associations are unidirectional `@ManyToOne LAZY` with `@EntityGraph` where needed; no `cascade`, no `@OneToMany`.
- `Topic` has no creation endpoint; there is no seed in the repo yet.
- `spring.profiles.active=local` is hard-coded in `application.properties`; there is no prod profile, `ddl-auto=update` and `show-sql=true` are known debts (see `REVUE_TECHNIQUE.md`), not things to "fix" in passing.
- The rationale for each decision lives in the Obsidian vault (`Projets/MDD/`); this file records only the constraints.

## Frontend

- Angular 21.2, standalone, **zoneless** (no zone.js: never add it or rely on `NgZone`), Vitest + jsdom (never Karma/Jasmine APIs).
- Before writing Angular code, use the `angular-cli` MCP server: `get_best_practices` and `find_examples` with `workspacePath` = the absolute path to `front/angular.json`, `includeExperimental` left false; `search_documentation` with `version: 21`, then check `searchedVersion` in the result (it may fall back to 20).
- No API marked `@experimental` in the installed 21.2 typings (e.g. Signal Forms `form()`, `resource()`, `httpResource`) unless recorded as a project decision below.
- UI: Angular Material components on the existing SCSS theme (`src/styles.scss`). No Tailwind, no `@angular/aria`.
- Screens follow `docs/maquettes/` and must work on mobile and desktop. User-facing text is French and copies the spec wording exactly (e.g. "Déjà abonné").

<!-- Front architecture decisions: to be added after the framing session. -->

## Evidence and reports

- Every claim in a report cites its source: `file:line`, or the command run and the relevant output line, marked [lu] / [grep] / [exec] / [déduit]. Write "not verified" rather than guess.
- Anomalies noticed in passing: `⚠ file:line — what`; do not fix without being asked.

## Deliverable docs

- `back/docs/`: `RAPPORT_DE_TESTS.md` and `REVUE_TECHNIQUE.md` are deliverables; `TESTS_REVIEW.md` is the dated journal of the test review; `*_MERGE_AUDIT.md` are historical PR audits partly superseded (#12, #13); `*_TEST_CHECKLIST.md` were never executed.
- A wrong figure in a repo doc is corrected in place (measure, fix, dated line at the end saying how it was measured), in a dedicated commit. Divergences in the Obsidian vault are reported, never edited.
- A requirement present in the vault but absent from the PDFs comes from the OpenClassrooms mission text: a missing source, not a false one. Never write "not found" or invent a requirement.
- Temporary progress/tracking files are never committed: keep them untracked and delete them at the end.

## Git etiquette

- One branch per piece of work (`feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`), Conventional Commits, PR squash-merged into `main`. Never commit on `main` directly; never push unless asked.
- Never commit `.env` or `application-local.properties` (both gitignored).
