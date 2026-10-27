# CLAUDE.md

MDD ("Monde de Dév"), OpenClassrooms P5 option B. Mono-repo: `back/` Spring Boot API, `front/` Angular app.

## Commands

### Backend (`back/`)

```bash
./mvnw test            # Surefire only: *Test classes (unit: JUnit, Mockito), no Docker needed
./mvnw verify          # + Failsafe: *IT classes — Docker REQUIRED (Testcontainers mysql:8.4)
./mvnw test -Dtest=ClassName
./mvnw spring-boot:run # needs MYSQL_* and JWT_SECRET in the environment (see below)
```

- JaCoCo report is produced by `verify` in `target/site/jacoco/` (one agent for both Surefire and Failsafe; no threshold, no excludes on purpose). Measure coverage with `./mvnw clean verify`: without `clean`, `target/jacoco.exec` accumulates previous runs and reports lines that no current test executes.
- Maven does **not** read the root `.env`: `mise.toml` loads it into the shell (`mise trust` once), otherwise export its variables before `verify` or `spring-boot:run` (`mise exec -- ./mvnw verify` outside an activated shell). `application-local.properties` (gitignored) holds only `spring.datasource.password=${MYSQL_PASSWORD}` and `mdd.jwt.secret=${JWT_SECRET}`; every `*IT` fails at context load with `Could not resolve placeholder 'JWT_SECRET'` if the variable is missing. `./mvnw test` alone needs nothing.
- `docker compose up -d` at the root starts `mdd-mysql` from the same `.env`.

### Frontend (`front/`)

`npm install`, `npm start` (4200), `npm run build`, `npm test` (Vitest; headless: `npx ng test --watch=false`, coverage: add `--coverage`), `npm run format`.

E2e: Cypress (`cypress/e2e/`), against the real back on 8080 and the front on 4200 (`cypress.config.ts`). Scripts `npm run e2e` (headless Electron), `npm run e2e:open`, `npm run e2e:firefox` (use it under Wayland: headless Electron never finishes CSS transitions). On a new machine run `npx cypress install` once. Other front port: `npm run e2e:firefox -- --config baseUrl=http://localhost:4201`.

## Backend conventions that differ from defaults

- **Test naming decides the runner**: `*Test` → Surefire (unit: JUnit, Mockito); `*IT` → Failsafe (`@SpringBootTest`). Spring Boot 4 / Framework 7: use `@MockitoBean`, not `@MockBean`.
- DB-backed ITs extend `AbstractContainerIT`, which starts one shared MySQL container in a `static {}` block. Do **not** add `@Testcontainers`/`@Container`: the JUnit extension would stop the inherited static container after the first test class.
- **Test strategy (decided 08/10/2026, PR #35 to #39; duplicate rule revised 22/10/2026; Arrange and authentication revised 26/10/2026)**: a higher-level test is a duplicate only if it checks a condition already checked at a lower level without adding anything (Ham Vocke, *The Practical Test Pyramid*, martinfowler.com).
  - Integration: package `parcours`, one file per action of the functional spec, plus `AuthentificationIT` (JWT filter and security chain) and `NombreDeRequetesIT` (number of SQL queries of the three main reads: feed, post detail, profile; Hibernate statistics enabled for this class only). Real MySQL everywhere; only the reference topics come from Flyway V2. One Act per test, `// Arrange` / `// Act` / `// Assert` comments.
  - Action classes, `NombreDeRequetesIT` included, extend `ActionIT` (`@SpringBootTest` + `@AutoConfigureMockMvc(addFilters = false)` + `@Transactional`): the Arrange creates data through the repositories and sets the authenticated user itself (principal = the numeric user id, exactly as `JwtAuthenticationFilter` sets it); action tests never go through registration, login or the security filter chain, and only the Act is an HTTP request. An action test that forgets to set the user fails with an exception, not with a 401 (on `GET /api/user`: `The given id must not be null`).
  - `InscriptionIT`, `ConnexionIT` and `AuthentificationIT` extend `ChaineDeSecuriteIT` and keep the filter chain: only the action under test goes through its route; accounts are created through the repositories, with the application's `PasswordEncoder` when login needs a real hash. `AuthentificationIT` covers every protected route without a token (401; the route list is maintained by hand), a token signed with another key, an expired token, a valid token sent without the `Bearer ` prefix, and a valid token accepted for the right user.
  - No `@WithMockUser`: its principal is Spring Security's `User`, while controllers expect `@AuthenticationPrincipal Long`.
  - `viderLeCache()` (`flush` + `clear`) goes after the HTTP assertions and right before any database re-read: placed earlier, a constraint error at flush breaks the shared transaction and masks the targeted assertion. Exception: `NombreDeRequetesIT` calls it at the end of the Arrange, then clears the Hibernate statistics, so that only the Act's queries are counted and none is served by the cache.
  - A value a test checks as absent from the log (`doesNotContain` on `CapturedOutput`) belongs to that test alone (account name, e-mail): `getAll()` returns the output of the whole test class, including what the other tests printed (on 22/10/2026, a failing test's MockMvc printout made `ConnexionIT`'s log test fail on an account name the two tests shared).
  - Unit tests only for rules the integration tests cannot pin precisely (excerpt bound, JWT claims and minimum key length, NFC, 72-byte bound, CORS parsing). No `@WebMvcTest`.
  - No `@DataJpaTest` (the three eager-loading slices were removed on 22/10/2026): each `@EntityGraph` of the feed, the post detail (post and comments) and the profile is proven by `NombreDeRequetesIT`, which counts the SQL queries of a real HTTP read; removing one adds a query per author, topic or comment author.
  - E2e (Cypress) for what needs a browser and the real server: rendering, routing, guards, interceptor, real commits.
- Invalid and expired tokens are covered by `AuthentificationIT` (token signed with another key → 401, expired token → 401). A refused token gets the same 401 as a missing one, so the refusal is proven by mutation: a refused token authenticated anyway in `JwtAuthenticationFilter` (fails both tests), and a two-hour clock skew in `JwtService` (fails the expired-token test only). The signature check itself has no mutation of its own: jjwt always performs it. The valid-token test proves that an accepted token reaches the controller as the right user id; since action tests bypass the filter, it is the only back-end test of that path.
- Every added or modified test is proven by **manual mutation** (break `src/main`, expect red, restore); `git diff src/main/java` must be empty after any such check. No PIT. A mutation counts as detected only if an expected test fails in its Act or Assert block — not in the Arrange, not on `viderLeCache()`, not with Hibernate's `AssertionFailure`.
- Mutation procedure (back and front): back up the file with `cp` to a session-specific path (e.g. `/tmp/mutation-<branch>.bak`), mutate, run the test, restore from that backup. Never `git checkout --`, `git restore` nor `git show … >` on a mutated file. `git diff` must be empty after the restore.

## Backend architecture decisions

- Identity comes only from the JWT `sub` (= numeric user id): `JwtAuthenticationFilter` makes it the principal and controllers receive `@AuthenticationPrincipal Long userId`. No user id ever appears in a URL or body; user-relative resources live under `/api/user` (profile `GET`/`PATCH /api/user`, `/api/user/subscriptions/{topicId}`, `/api/user/feed`); no `/me` segment anywhere (decision of 02/10/2026). `GET /api/auth/me` was removed deliberately (#13).
- JWT: jjwt 0.13.0 (`jjwt-api`; `jjwt-impl` and `jjwt-jackson` at runtime only) and a custom `OncePerRequestFilter`, `JwtAuthenticationFilter`, created with `new` in `SecurityConfig` (not a bean, so Spring Boot does not also register it as a servlet filter); no OAuth2 resource server, no Nimbus (decision of 15/10/2026, to keep the security simple to explain). `JwtService` signs and validates: HS256 set explicitly, claims `iss`/`iat`/`exp`/`sub` only, 24 h, no refresh, no logout endpoint, no roles; validation checks signature and `exp` only (no issuer check). The Base64 key must decode to at least 256 bits, otherwise the context does not start (`Keys.hmacShaKeyFor`). The filter also saves the authentication in a request attribute so that the container's `/error` re-dispatch stays authenticated.
- Errors: one `@RestControllerAdvice` (`GlobalExceptionHandler`) → `ErrorResponse`; validation errors carry `fieldErrors`. One case is **intentionally** outside that format: 401 (empty body + `WWW-Authenticate: Bearer`), produced for every 401 (missing, invalid or expired token, wrong credentials) by the single entry point declared in `SecurityConfig`. The empty body is asserted on the login 401s only (`ConnexionIT`); `AuthentificationIT` does not assert the body, the shared entry point is what makes the token 401s identical. Malformed JSON has its own handler (`HttpMessageNotReadableException` → 400 `ErrorResponse`): left to Spring's default, the container error page was re-authorized by Spring Security and returned 401 on public routes (observed with curl, 09/10/2026). Don't add a catch-all `Exception` handler (it would turn 401s into 500s).
- Layered monolith, packages by layer, concrete services without interfaces, one DTO per action (`*Request`/`*Response`, Lombok `@Data`), manual inline mapping, no MapStruct. Entities are immutable except `User`; associations are unidirectional `@ManyToOne LAZY` with `@EntityGraph` where needed; no `cascade`, no `@OneToMany`.
- `Topic` has no creation endpoint: reference topics are inserted by the Flyway migration `back/src/main/resources/db/migration/V2__insert_reference_topics.sql`; test data must not reuse their names (`topics.name` is unique).
- `spring.profiles.active=local` is hard-coded in `application.properties`; there is no prod profile. The schema is owned by Flyway (`back/src/main/resources/db/migration/`) and Hibernate only validates it (`ddl-auto=validate`): any entity change needs a new migration. `show-sql=true` is a known debt (see `REVUE_TECHNIQUE.md`), not a thing to "fix" in passing.
- The rationale for each decision lives in the Obsidian vault (`OpenClassrooms/Formation Java Angular/Projets/Projet_05 - MDD (full-stack)/prepa/`, index `prepa/Etapes des choix logiques.md`); this file records only the constraints.

## Frontend

- Angular 21.2, standalone, **zoneless** (no zone.js: never add it or rely on `NgZone`), Vitest + jsdom (never Karma/Jasmine APIs).
- Before writing Angular code, use the `angular-cli` MCP server: `get_best_practices` and `find_examples` with `workspacePath` = the absolute path to `front/angular.json`, `includeExperimental` left false; `search_documentation` with `version: 21`, then check `searchedVersion` in the result (it may fall back to 20).
- No API marked `@experimental` in the installed 21.2 typings (e.g. Signal Forms `form()`, `resource()`, `httpResource`) unless recorded as a project decision below.
- UI: Angular Material components on the existing SCSS theme (`src/styles.scss`). No Tailwind, no `@angular/aria`.
- Screens follow `docs/maquettes/` and must work on mobile and desktop. User-facing text is French and copies the spec wording exactly (e.g. "Déjà abonné").

### Front architecture decisions (framing of 25/09/2026)
- Structure by feature: `src/app/core/` (auth, http, layout, notification), `src/app/shared/` (validators), `src/app/features/{auth,posts,topics,profile,legal}/` (`legal`: legal notice `/mentions-legales` and privacy policy `/confidentialite`, public, no guard, linked from the shell footer). A feature session only edits its own feature folder; changes needed in `core/` or `shared/` are reported, not made.
- State: services + signals. HTTP services return Observables; components convert them with `toSignal` or subscribe; the auth state is a signal in `AuthService`. No NgRx; no `httpResource` nor Signal Forms (experimental in Angular 21).
- Forms: typed Reactive Forms. Password rule shared with the backend: at least 8 characters with a digit, a lowercase, an uppercase and a `\p{Punct}` character (`shared/validators/password.validator.ts` ↔ `@Size(min = 8)` + `@Pattern`), and at most 72 UTF-8 bytes after NFC normalisation (`shared/validators/max-utf8-bytes.validator.ts` ↔ `@MaxUtf8Bytes(72)`; BCrypt refuses more than 72 bytes, it does not truncate). Login has no byte bound (too long → 401). Username: 3 to 50 characters, `^[A-Za-z0-9._-]+$`, front and back. Profile: an empty password field omits the `password` key (absent = unchanged; empty or blank = 400 on the API).
- Auth: JWT in `localStorage` (key `mdd.token`); expiry read from the `exp` claim (no signature check on the front, the API checks it). Functional `authInterceptor` adds `Authorization: Bearer <token>` to `/api/**` except `/api/auth/**`; a 401 on a non-auth call logs out and redirects to `/login`. Functional guards: `authGuard` (protected pages → `/login`), `guestGuard` (home, login, register → `/feed` when logged in).
- Errors: `fieldErrors` shown under the matching form field; other errors through `NotificationService` (`core/notification`, MatSnackBar); the login 401 (empty body) becomes "Identifiants incorrects" on the front. A 401 on a non-auth call belongs to the interceptor alone: components never notify it. Every write button is disabled by a signal while its request runs (no second request on a second click). A 409 on subscribe means "already subscribed" (card shows "Déjà abonné", no notification); unsubscribe is idempotent on the API (204).
- Dev: `ng serve` proxies `/api/**` to `http://localhost:8080` (`src/proxy.conf.json`); services call relative `/api/...` URLs, so any dev port works without CORS.
- UI: Angular Material, theme `mat.$violet-palette`; responsive nav with CDK `BreakpointObserver` (Handset → burger menu + `MatSidenav`). Mockups are inspiration: every screen and feature is mandatory, pixel fidelity is not.
- Security: never `innerHTML` nor `bypassSecurityTrust*`; post and comment content are rendered as text.
- Tests: Vitest + jsdom. Security code (auth service, interceptor, guards, password validator) is proven by manual mutation; feature specs need at least one mutation per spec file.

## Code quality (SonarQube Cloud)

- Analysis runs in CI only (`.github/workflows/ci.yml`); its analysis parameters live only in `sonar-project.properties` at the repo root.
- Front test helpers outside `*.spec.ts` live in a `testing/` folder or a `testing.ts` file under `front/src/` and are imported only by specs: Sonar classifies them as test code (`sonar.test.inclusions`).
- Never add `NOSONAR`, `eslint-disable` or a Sonar exclusion without Maxime's explicit decision. Issue statuses (Accepted, False positive) and hotspot reviews are changed by Maxime only, in the Sonar UI.

## Evidence and reports

- Every claim in a report cites its source: `file:line`, or the command run and the relevant output line, marked [lu] / [grep] / [exec] / [déduit]. Write "not verified" rather than guess.
- Anomalies noticed in passing: `⚠ file:line — what`; do not fix without being asked.

## Deliverable docs

- `back/docs/`: `RAPPORT_DE_TESTS.md` and `REVUE_TECHNIQUE.md` are deliverables; `TESTS_REVIEW.md` is the dated journal of the test review; `archives/` holds the `*_MERGE_AUDIT.md` (historical PR audits partly superseded by #12, #13) and the `*_TEST_CHECKLIST.md` (never executed).
- A wrong figure in a repo doc is corrected in place (measure, fix, dated line at the end saying how it was measured), in a dedicated commit. Divergences in the Obsidian vault are reported, never edited.
- A requirement present in the vault but absent from the PDFs comes from the OpenClassrooms mission text: a missing source, not a false one. Never write "not found" or invent a requirement.
- Temporary progress/tracking files are never committed: keep them untracked and delete them at the end.

## Git etiquette

- One branch per piece of work (`feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`), Conventional Commits, PR squash-merged into `main`. Never commit on `main` directly; never push unless asked.
- Never commit `.env` or `application-local.properties` (both gitignored).
