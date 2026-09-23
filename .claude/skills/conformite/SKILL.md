---
name: conformite
description: Check the repo against the ORION specs and the OpenClassrooms rubric; DONE / PARTIAL / MISSING with evidence. Manual only.
disable-model-invocation: true
context: fork
background: false
disallowed-tools: Edit, Write, NotebookEdit
---
Branch: !`git branch --show-current`
Changes vs main: !`git diff --stat main...HEAD`

Read-only. For each item give: status (DONE / PARTIAL / MISSING / UNKNOWN), backend evidence and frontend evidence separately (`file:line`, or command + output line). Never mark DONE on backend evidence alone when the item needs a UI. MISSING = blocking defect.

Functional specs:
E1 logged-out home gives access to login and register · E2 register with email, password, username · E3 login with email OR username + password · E4 login persists across browser sessions · E5 profile shows email, username, subscriptions · E6 edit email, username, password · E7 logout · E8 page listing all topics, subscribed or not · E9 subscribe from topics page; button disabled, label "Déjà abonné" · E10 unsubscribe from profile page · E11 logged-in home shows the feed, newest first · E12 feed sort newest↔oldest · E13 create post (topic, title, content); author and date set server-side · E14 post page: topic, title, author, date, content, comments · E15 add comment; author and date automatic; no nesting · E16 password policy ≥ 8 chars, digit, lower, upper, special — front and back · E17 every screen responsive · E18 front/back interaction secured.

Rubric:
R1 clear, modular front architecture; Angular conventions · R2 screens match `docs/maquettes/` (compare visually if browser tools are available, else "not verified") · R3 front/back bindings (services, endpoints, observables/signals) · R4 responsive desktop + mobile · R5 key components and UI architecture documented with screenshots · R6 REST API structure; endpoints, schemas, formats documented · R7 authentication and error handling front and back · R8 logs and exceptions leak no sensitive data · R9 Java conventions and Javadoc on public API · R10 unit tests on critical front and back parts · R11 integration and end-to-end tests of the full user journey · R12 readable coverage reports; coverage ≥ 70 % (mission text) · R13 tests: naming, isolation, Arrange-Act-Assert · R14 quality/analysis tools integrated · R15 legal notice and privacy policy · R16 README: structure, technologies, install, environment, deployment · R17 user FAQ (login, posting, subscription, profile) · R18 improvement areas documented.

Output: one table (id, status, evidence), then MISSING items listed first with what would close each, then the commands run.
