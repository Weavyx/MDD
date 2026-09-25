# Rapport de couverture et de tests — MDD

Projet MDD (« Monde de Dev »), OpenClassrooms P5 option B. Rapport établi le 19 septembre 2026 sur le code de `main` au commit `d2cdd31` ; les renvois à `TESTS_REVIEW.md` visent son état après le correctif de décomptes du même jour (commit `d754d38`). Mis à jour le même jour après la correction de l'anomalie p de la revue technique (branche `fix/validation-mot-de-passe-obligatoire`, intégrée dans `main` par le commit `9581c70` ; référence mise à jour le 24 septembre 2026) : un test ajouté, total 127. **Re-mesuré le 25 septembre 2026** sur `main` au commit `2a01fc9` (PR #20 à #30 comprises) : tous les chiffres des sections 3, 4, 6, 7 et 9 sont ceux de cette mesure ; les chiffres du 19 septembre ne subsistent que comme historique, signalés comme tels.

## 1. Périmètre et état du projet

Ce rapport couvre le **backend** (`back/`, Spring Boot 4.1.0, Java 21), le **front-end** Angular 21.2 (`front/`, tests unitaires et de composants) et les **tests end-to-end** Cypress qui traversent les deux. Le front a été développé par les PR #21 à #26, les tests end-to-end par la PR #27 ; les PR #29 (back) et #30 (front) ont corrigé les anomalies d'une revue adverse et ajouté les tests correspondants (§8).

Le texte de mission impose un seuil de couverture de 70 % **[texte de mission, source hors dépôt]** — seuil issu du texte de mission sur la plateforme, non repris dans la grille d'auto-évaluation. Aucun template de rapport de tests n'est fourni par la mission ; la structure ci-dessous est libre.

Origine des chiffres, conformément à la règle de preuve : **[mesuré]** = exécuté dans la session de mesure (commandes au §9) ; **[code]** = lu dans un fichier, cité ; **[test]** = comportement verrouillé par un test nommé ; **[TESTS_REVIEW]** = repris du journal `back/docs/TESTS_REVIEW.md`, section citée ; **[PR]** = repris de la description d'une pull request fusionnée, citée.

Reproduire : `cd back && ./mvnw clean verify`. Prérequis : Docker démarré, et la variable `JWT_SECRET` exportée dans le shell (le fichier gitignoré `application-local.properties` la référence ; sans elle, `MddApiApplicationIT` échoue sur `Could not resolve placeholder 'JWT_SECRET'` — vérifié le 19 septembre). `./mvnw test` seul (strate unitaire) n'a besoin de rien. Rapport HTML dans `back/target/site/jacoco/index.html`. Front et end-to-end : §7.

## 2. Outils et rôle de chacun

| Outil | Déclaration | Rôle dans ce projet [code] |
|---|---|---|
| JUnit 5 (Jupiter), AssertJ | via `spring-boot-starter-test` (`pom.xml`) | Exécution et assertions de tous les tests. Uniquement des `@Test` simples : aucun `@ParameterizedTest`, `@Nested`, `@RepeatedTest`. |
| Mockito | idem ; `@MockitoBean` (Spring Framework 7) | Isolation des services (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`) ; remplacement des services et du `JwtDecoder` dans les tests de contrôleur. |
| `spring-boot-starter-webmvc-test` | `pom.xml` | `@WebMvcTest` + `MockMvc` : couche web sans serveur ni base. |
| `spring-boot-starter-data-jpa-test` | `pom.xml` | `@DataJpaTest` + `TestEntityManager` : couche persistance, transaction annulée après chaque test. |
| `spring-security-test` | `pom.xml` | `jwt().jwt(j -> j.subject("42"))` : injecte un principal `Jwt` directement dans le contexte de sécurité, sans passer par le décodeur. |
| Testcontainers 1.21.4 (`mysql`) | `pom.xml`, `AbstractContainerIT` | Un conteneur `mysql:8.4` — la même image que `docker-compose.yml` — démarré une fois pour toute la JVM de test et injecté par `@DynamicPropertySource`. |
| Maven Surefire / Failsafe | Failsafe déclaré dans `pom.xml` (goals `integration-test`, `verify`) | Convention de nommage : `*Test` → Surefire (`mvn test`, sans Docker) ; `*IT` → Failsafe (`mvn verify`, Docker requis). |
| JaCoCo 0.8.15 | `pom.xml` : `prepare-agent` + `report` en phase `verify` | Un seul agent, donc un seul `jacoco.exec` agrégeant Surefire et Failsafe. **Aucun seuil bloquant (`check`), aucun `<excludes>`** : la mesure est informative et le périmètre n'est pas réduit. |
| Vitest 4.1.10 + jsdom | `front/package.json`, builder `@angular/build:unit-test` (`front/angular.json`, cible `test`) | Tests unitaires et de composants du front (§7.1). `HttpTestingController` remplace l'API. |
| `@vitest/coverage-v8` | `front/package.json` | Couverture du front (`npx ng test --watch=false --coverage`). |
| Cypress 16.1.0 | `front/package.json`, `front/cypress.config.ts` | Tests end-to-end dans un vrai navigateur, contre le vrai back et sa base (§7.2). |

Volontairement absents [TESTS_REVIEW « Axes d'amélioration », « Outils »] : PIT (mutation testing outillé — dépendance supplémentaire, compatibilité Boot 4 / Java 21 non vérifiée, remplacé par une mutation manuelle, §5) ; H2 (dialecte différent de MySQL : `LONGTEXT`, sous-requêtes `IN (SELECT …)` ; remplacé par Testcontainers) ; seuil JaCoCo bloquant.

Aucun analyseur statique n'est configuré (pas de SpotBugs, Checkstyle, PMD ni Sonar dans `pom.xml`, pas d'ESLint dans `front/package.json` [code]). Les deux outils d'analyse du projet sont la couverture (JaCoCo, v8), qui a localisé les zones non exécutées (§4), et la preuve par mutation manuelle, qui a localisé les assertions trop faibles (§5) ; c'est à ce titre qu'ils tiennent lieu d'« outils d'analyse pour identifier et corriger les points faibles ».

## 3. Stratégie back : quatre strates, ce que chacune garantit

Tests nommés `méthode_condition_résultat`, un cas par test, structure Arrange–Act–Assert, aucune dépendance d'ordre. Deux classes abstraites factorisent l'infrastructure : `AbstractContainerIT` (conteneur MySQL singleton) et `AbstractRepositoryIT` (helpers `persistUser`/`persistTopic`/`persistPost` avec `persistAndFlush`).

| Strate | Classes [mesuré : nombre de tests] | Garantit | Ne garantit pas |
|---|---|---|---|
| **A. Unitaire pur** (Surefire, Mockito, aucun contexte Spring) | `UserServiceTest` 16, `PostServiceTest` 10, `MaxUtf8BytesValidatorTest` 7, `AuthServiceTest` 5, `JwtServiceTest` 4, `UserDetailsImplTest` 4, `NfcPasswordEncoderTest` 3, `UserDetailsServiceImplTest` 3, `SecurityConfigTest` 2, `TopicServiceTest` 2 — **56** | Logique métier et conditions de garde (exception levée, `save` jamais appelé via `never()`), mapping entité → DTO champ par champ, hachage du mot de passe (`ArgumentCaptor<User>` : `passwordHash` ≠ clair), forme des claims JWT (`ArgumentCaptor<JwtEncoderParameters>`), borne de 72 octets UTF-8 sur la forme NFC, normalisation NFC avant BCrypt, découpage des origines CORS. | Requêtes SQL réelles, sérialisation JSON, chaîne de filtres de sécurité, encodage HMAC réel (le `JwtEncoder` est mocké). |
| **B. Contrôleur** (`@WebMvcTest` + `@Import({SecurityConfig, GlobalExceptionHandler})`, services et `JwtDecoder` en `@MockitoBean`) | `UserControllerIT` 35, `AuthControllerIT` 16, `PostControllerIT` 14, `TopicControllerIT` 2 — **67** | Routage et codes HTTP, validation `@Valid` et contenu de `fieldErrors`, mapping exception → statut par `GlobalExceptionHandler`, extraction de l'identité depuis `sub`, 401 sans token, 400 sur paramètre non numérique, absence de `password`/`passwordHash` dans les réponses. | Le service réel (mocké), le décodage réel d'un JWT (couvert par la strate D), le rendu réel de la page `/error`. |
| **C. Persistance** (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers) | `UserRepositoryIT` 11, `SubscriptionRepositoryIT` 7, `PostRepositoryIT` 3, `CommentRepositoryIT` 1, `TopicRepositoryIT` 1 — **23** | JPQL et requêtes dérivées exécutées sur MySQL 8.4 réel, schéma créé par les migrations Flyway : filtres, tri, contraintes, thèmes de référence insérés par `V2__insert_reference_topics.sql` ; chargement anticipé prouvé par `Hibernate.isInitialized(...)` avant tout accès pour trois des quatre `@EntityGraph` du code. | Services et contrôleurs ; comportement transactionnel réel (chaque test est annulé) ; le chargement anticipé de `findPostsByUserId` (le fil) n'est pas prouvé (§10). |
| **D. Contexte complet** (`@SpringBootTest` sur le conteneur) | `SecurityIT` 6, `MddApiApplicationIT` 1 — **7** | Le contexte Spring démarre avec la vraie configuration ; `SecurityIT` (`@AutoConfigureMockMvc`, transaction annulée après chaque test) traverse la vraie chaîne — `PasswordEncoder`, `AuthenticationManager`, `JwtDecoder`, base MySQL : connexion, mot de passe de 74 octets à la connexion, profil sans mot de passe, jeton valide, jeton signé avec une autre clé, jeton expiré. | Aucun serveur HTTP réel (MockMvc) ; pas de requête CORS `OPTIONS`. |

Total : **153 tests** = 56 Surefire + 97 Failsafe, 0 échec, 0 erreur, 0 ignoré [mesuré le 25 septembre 2026]. Historique : 126 = 45 + 81 au commit `d2cdd31` ; 127 = 45 + 82 après la correction de l'axe p ; 128 = 45 + 83 après la PR #20 (`TopicRepositoryIT`) ; 153 après la PR #29 [PR #29 : « Surefire 56 tests (45 avant), Failsafe 97 (83 avant) »].

Isolation : la strate A ne touche ni Spring ni la base ; B n'instancie que le contrôleur visé ; C et `SecurityIT` annulent leur transaction après chaque test ; le conteneur est partagé mais jamais l'état.

## 4. Chiffres de couverture

### 4.1 Back-end (mesure du 25 septembre 2026)

Mesure JaCoCo sur `back/target/site/jacoco/jacoco.csv`, 24 classes analysées [mesuré] :

| Périmètre | Instructions | Lignes | Branches |
|---|---|---|---|
| **Total** | **1 116 / 1 128 = 98,94 %** | **265 / 268 = 98,88 %** | **27 / 28 = 96,43 %** |
| `controller` | 158 / 158 = 100 % | 39 / 39 | — (aucune branche) |
| `service` | 534 / 534 = 100 % | 122 / 122 | 18 / 18 = 100 % |
| `exception` | 130 / 130 = 100 % | 30 / 30 | 2 / 2 = 100 % |
| `security` | 146 / 153 = 95,4 % | 31 / 32 | 3 / 4 = 75 % |
| `security.jwt` | 66 / 66 = 100 % | 19 / 19 | — |
| `security.services` | 54 / 54 = 100 % | 17 / 17 | — |
| `validation` | 25 / 25 = 100 % | 6 / 6 | 4 / 4 = 100 % |
| racine (`MddApiApplication`) | 3 / 8 = 37,5 % | 1 / 3 = 33,3 % | — |

Le seuil de 70 % du texte de mission est dépassé sur les trois métriques au total et dans chaque package hors racine ; la racine ne contient que `MddApiApplication` (voir ci-dessous).

Ce qui n'apparaît pas dans le tableau, et pourquoi :

- **`MddApiApplication.main`** : 2 lignes non couvertes. Décision documentée de ne pas les exclure du périmètre ni de les couvrir par un `MockedStatic` : « un 99,2 % expliqué vaut mieux qu'un 100 % obtenu en réduisant le périmètre mesuré » [TESTS_REVIEW « Ce qui reste non couvert »].
- **`NfcPasswordEncoder`** (ajouté par la PR #29) : `upgradeEncoding`, simple délégation, n'est jamais appelé (ligne 32), et la branche `rawPassword == null` de `normalize` n'est pas exercée (ligne 36) [mesuré, `jacoco.xml`]. C'est la seule branche manquante du back.
- **`dto` et `model`** : absents du CSV. Ce n'est pas une exclusion configurée (aucun `<excludes>` dans `pom.xml`) : le bytecode de ces classes porte `@lombok.Generated` (vérifié par `javap` sur `model.Topic`) et JaCoCo filtre ce code par défaut ; une classe entièrement générée disparaît du rapport [mesuré le 19 septembre].
- **`repository`** : interfaces Spring Data sans bytecode propre ; leurs requêtes sont exercées par la strate C.

Évolution [TESTS_REVIEW « Diagnostic initial », « Résultat »] : avant la revue des tests du 18 septembre, 95 tests et 87 % d'instructions, avec deux trous réels masqués par la moyenne — `security.services` à 11 % et `security.jwt` à 57 %, c'est-à-dire la chaîne d'authentification jamais exécutée par un test. 24 tests ont comblé ces trous (99,5 %), puis une seconde phase a ajouté 7 tests et renforcé 23 autres **sans faire bouger la couverture d'un dixième de point** — c'est l'objet de la section suivante. Ligne de base du 19 septembre (commit `d2cdd31`) : 1 054 / 1 059 instructions = 99,5 %, 250 / 252 lignes = 99,2 %, 22 / 22 branches = 100 %. La baisse au 25 septembre (98,94 % / 98,88 % / 96,43 %) vient uniquement des deux points de `NfcPasswordEncoder` ci-dessus.

### 4.2 Front-end (mesure du 25 septembre 2026)

`npx ng test --watch=false --coverage` (fournisseur v8), 25 fichiers de spec, 269 tests [mesuré] :

| Métrique | Couvert / total |
|---|---|
| Instructions (*statements*) | 1 052 / 1 061 = **99,15 %** |
| Branches | 390 / 396 = **98,48 %** |
| Fonctions | 146 / 147 = **99,31 %** |
| Lignes | 725 / 729 = **99,45 %** |

Fichiers non couverts à 100 %, tels que listés par le rapport : `core/http/api-error.ts` (une branche, ligne 25), `features/auth/testing.ts` (utilitaire de test, ligne 5), `post-create.html` (ligne 19), `post-detail.html` (ligne 27), `post-detail.ts` (ligne 119), `profile.ts` (une branche, ligne 155) [mesuré]. Rapport HTML dans `front/coverage/front/index.html`.

## 5. Qualité des tests : preuve par mutation manuelle

Une couverture de 99 % dit qu'un test exécute chaque ligne ; elle ne dit pas qu'il échouerait si la ligne était fausse. Pour le vérifier, chacun des tests existants a été soumis à la question « quel bug plausible ce test laisserait-il passer ? », puis le bug a été réellement introduit. Protocole en cinq temps [TESTS_REVIEW « Seconde phase »] :

1. introduire une mutation plausible dans `src/main/java` ;
2. relancer le test : s'il reste vert, la faiblesse est prouvée (s'il passe au rouge, l'audit se trompait) ;
3. restaurer le code (`git diff src/main/java` vide, contrôlé après chaque mutation) ;
4. renforcer ou ajouter le test ;
5. réappliquer la mutation, vérifier que le test échoue, restaurer.

Quinze situations où la suite restait verte ont été identifiées. Les plus graves, avec la correction apportée (tests retrouvés dans le code [test]) :

| Mutation introduite | Suite avant correction | Correction |
|---|---|---|
| `deleteByUserIdAndTopicId` : condition `user_id` retirée du `DELETE` → un désabonnement supprimait l'abonnement de **tous** les utilisateurs au topic | 6/6 verts | Ajout de `deleteByUserIdAndTopicId_deuxUtilisateursAbonnesAuMemeTopic_neSupprimeQueCeluiVise` (`SubscriptionRepositoryIT`) |
| `AuthService.register` : `PasswordEncoder` appelé mais résultat ignoré → mot de passe stocké **en clair** | 5/5 verts | `register_emailEtUsernameLibres_sauvegardeEtRetourneUnToken` renforcé par `ArgumentCaptor<User>` : `passwordHash` = hash et ≠ mot de passe fourni (`AuthServiceTest`) |
| `AuthService.login` : identifiant et mot de passe inversés dans le jeton d'authentification | 5/5 verts | Captor sur `Authentication` : principal et credentials vérifiés séparément |
| `UserController.subscribe`/`unsubscribe` : `(userId, topicId)` inversés | verts | Sujet JWT `"42"` et topic `7` distincts, `verify(userService).subscribe(42L, 7L)` (`UserControllerIT`) |
| `findPostsByUserId` : filtre par auteur ajouté → le fil ne montrait que ses propres articles | 2/2 verts | Ajout de `findPostsByUserId_postEcritParUnAutreAuteurDansUnTopicSouscrit_estInclusDansLeFil` (`PostRepositoryIT`) |
| `@EntityGraph` retirés sur trois requêtes (`findWithUserAndTopicById`, `findByUserId`, `findByPostIdOrderByCreatedAtAsc`) → N+1 silencieux | 10/10 verts | `Hibernate.isInitialized(post.getUser())` asserté **avant** tout accès (`PostRepositoryIT`, `SubscriptionRepositoryIT`, `CommentRepositoryIT`) |
| `GlobalExceptionHandler` : clé de `fieldErrors` = nom de l'objet au lieu du champ | 10/10, 13/13 et 28/28 verts (trois contrôleurs) | Six assertions `jsonPath("$.fieldErrors.<champ>")`, deux par contrôleur concerné |
| `UserService.subscribe` : `Subscription` créée avec `user` nul | 17/17 verts | `ArgumentCaptor<Subscription>` : `isSameAs(user)` / `isSameAs(topic)` |

Ce que la méthode a révélé : les faiblesses n'étaient pas dans les branches non couvertes mais dans les **assertions trop faibles** — vérifier qu'une méthode est appelée sans vérifier avec quoi, vérifier qu'une liste n'est pas vide sans vérifier son contenu. Sept tests ajoutés, vingt-trois renforcés, couverture strictement inchangée à 99,5 % / 99,2 % / 100 % [TESTS_REVIEW « Résultat de la seconde phase »]. Le journal complet des mutations est dans `TESTS_REVIEW.md` ; ce rapport n'en reprend que les cas décisifs.

**Tests ajoutés par les PR #29 et #30.** Les tests ajoutés ont été confrontés à une mutation du code qu'ils protègent, selon le même protocole. Back [PR #29, section « Mutations manuelles »] : tuées — `matches` sans `normalize` ; `<=` → `<` dans le validateur d'octets ; `@MaxUtf8Bytes` retiré de l'inscription ; `@Pattern` retiré du `username` ; validateur JWT remplacé par `success()` (tué par le test du jeton expiré) ; CORS sans `trim`. Une mutation survit, comme prévu : remettre la condition du service à « non blanc » ne change pas le comportement HTTP, puisque la validation rejette déjà le blanc (400) avant le service. Le rejet d'un jeton **signé avec une autre clé** n'a pas de mutation propre : le décodeur Nimbus vérifie toujours la signature, et la seule façon de le faire échouer serait de réécrire le décodeur. Front [PR #30, section « Vérifications »] : au moins une mutation par fichier de spec modifié ou créé, toutes tuées. Après chaque mutation, le fichier est restauré et `git diff` est vide [PR #29].

Anomalies corrigées : aucune mutation n'a mis au jour un bug **réel** du code de production — les quinze cas de la seconde phase sont des trous de filet, pas des défauts. Les anomalies réelles ont été trouvées **hors** mutation, par relecture adverse : l'axe p de la revue technique (mot de passe non obligatoire à l'inscription, commit `9581c70`, test écrit avant la correction), puis les anomalies corrigées par les PR #29 et #30 (§8). Les autres axes de `REVUE_TECHNIQUE.md` §2 sont suivis dans la revue, avec leur état au 25 septembre.

## 6. Sécurité et contrat d'erreur verrouillés par les tests

- **Accès sans jeton** : 9 tests `*_sansJwt_retourne401` (un par endpoint protégé exercé : `TopicControllerIT` 1, `PostControllerIT` 3, `UserControllerIT` 5) [mesuré par grep `void .*sansJwt_retourne401`].
- **Jeton présent mais invalide ou expiré** : `SecurityIT.getProfile_jetonSigneAvecUneAutreCle_retourne401` et `getProfile_jetonExpire_retourne401` appellent `GET /api/users/me` avec le vrai `JwtDecoder` : 401 avec un en-tête `WWW-Authenticate` portant `invalid_token` ; le témoin `getProfile_jetonValide_retourne200` montre que le 401 vient bien du jeton [test].
- **Identifiants invalides** : `login_identifiantsInvalides_retourne401` fige le comportement natif de Spring Security — corps vide, en-tête `WWW-Authenticate: Bearer` — puisqu'aucun handler applicatif ne le redéfinit [test]. Un mot de passe de 74 octets à la connexion donne aussi 401, pas 500 : `LoginRequest` n'a pas de borne, et `BCryptPasswordEncoder.matches` renvoie `false` [test `SecurityIT.login_utilisateurExistantMotDePasseDe74Octets_retourne401`].
- **Non-divulgation par les exceptions** : `AuthControllerIT` et `UserControllerIT` assertent `jsonPath("$.password").doesNotExist()` et `$.passwordHash` absent des réponses ; `DataIntegrityViolationException` → 409 avec message générique, aucun détail SQL [test `UserControllerIT`, handler `GlobalExceptionHandler.handleDataIntegrityViolation`].
- **Logs** : aucun logger applicatif dans `src/main/java` (grep `Logger`, `@Slf4j`, `log.` : 0) [mesuré le 19 septembre], donc aucune trace applicative ne peut contenir un secret. En revanche `spring.jpa.show-sql=true` est dans le profil commun : les requêtes SQL (pas leurs paramètres, liés en `?`) sont journalisées — limite consignée dans la revue technique, axe c.
- **Validation d'entrée** : 27 tests attendent un 400 dans les tests de contrôleur (`AuthControllerIT` 9, `UserControllerIT` 13, `PostControllerIT` 5) : 22 corps invalides, 4 paramètres d'URL invalides (trois id non numériques, un `sort` hors `asc|desc`) et 1 JSON malformé ; 13 d'entre eux assertent la clé de `fieldErrors` [mesuré par grep `isBadRequest()` et `fieldErrors.` par méthode de test].
- **Identité issue du jeton** : les contrôleurs reçoivent `userId` = `jwt.getSubject()` ; sur les chemins nominaux, les tests utilisent des identifiants différents pour l'utilisateur et la ressource (sujet `42`, topic `7`), ce qui rend une inversion d'arguments détectable [test `UserControllerIT.subscribe_avecJwtValideEtSucces_retourne200`, `unsubscribe_…`].
- **JSON malformé** : `register_corpsJsonMalforme_retourne400` fige un 400 à corps vide sous MockMvc — un comportement hors format `ErrorResponse`, connu et documenté (voir revue technique, axe e).

## 7. Front-end et end-to-end

### 7.1 Tests unitaires et de composants

`npm test -- --watch=false` : **25 fichiers de spec, 269 tests**, tous verts [mesuré le 25 septembre 2026]. Chaque fichier testé a son `*.spec.ts` à côté de lui : `app`, routes, `core/auth` (service, `authGuard`, `guestGuard`), `core/http` (intercepteur, `toApiError`), `core/layout/shell`, `core/notification`, les services HTTP et les pages des features `auth`, `posts`, `topics`, `profile`, `legal`, et les deux validateurs de `shared/validators` [code, `find front/src -name "*.spec.ts"`]. Les appels HTTP sont interceptés par `HttpTestingController`, qui vérifie l'URL, la méthode et le corps de chaque requête.

Règle du projet (`CLAUDE.md`, section « Frontend ») : le code de sécurité (service d'authentification, intercepteur, gardes, validateur de mot de passe) est prouvé par mutation manuelle, et chaque spec de feature compte au moins une mutation. Pour les specs modifiées ou créées par la PR #30, voir §5.

### 7.2 End-to-end (Cypress)

`npm run e2e:firefox -- --config baseUrl=http://localhost:4201`, contre le back réel sur `http://localhost:8080` et sa base MySQL : **5 specs, 7 tests, 7 verts** [mesuré le 25 septembre 2026] :

| Spec | Tests | Parcours |
|---|---|---|
| `accueil.cy.ts` | 2 | L'accueil tient dans l'écran sans défilement, à 1280 × 800 et à 375 × 667 : ni le document ni `mat-sidenav-content` ne défilent, et les liens légaux sont visibles |
| `gardes.cy.ts` | 2 | Visiteur non connecté redirigé de `/feed` vers `/login` ; utilisateur connecté redirigé de `/login` vers `/feed` |
| `mobile.cy.ts` | 1 | Connexion, menu burger, « Thèmes » puis déconnexion depuis le menu |
| `parcours-complet.cy.ts` | 1 | Inscription, abonnement, article, commentaire, profil, déconnexion et reconnexion (1280 px) |
| `validation.cy.ts` | 1 | Mot de passe faible refusé à l'inscription : message d'erreur, aucune requête, aucune redirection |

Firefox plutôt qu'Electron : sous Wayland, Electron en mode headless ne termine pas les transitions CSS [commit `90f1620`, PR #27]. `--config baseUrl` sert à viser un autre port que 4200, l'URL par défaut de `front/cypress.config.ts`.

## 8. Tests ajoutés par les PR #29 et #30, par thème

| Thème | Tests [test] |
|---|---|
| **Borne de 72 octets UTF-8 et NFC** (back) | `MaxUtf8BytesValidatorTest` (7) : chaîne ASCII de 72 octets acceptée, de 73 refusée, 36 « é » précomposés (72 octets) acceptés, 37 (74 octets) refusés, 36 « é » en NFD comptés sous leur forme NFC, `null` valide, message indiquant la borne en octets ; `NfcPasswordEncoderTest` (3) : un mot de passe encodé en NFD correspond au même en NFC et inversement, un autre mot de passe ne correspond pas ; `AuthControllerIT.register_motDePasseDepassant72Octets_…` (renommé depuis `…72Caracteres…`) et `register_motDePasseAccentueDe42CaracteresEt74Octets_retourne400EtServiceJamaisAppele` ; `UserControllerIT.updateProfile_motDePasseAccentueDe42CaracteresEt74Octets_retourne400` ; `SecurityIT.login_utilisateurExistantMotDePasseDe74Octets_retourne401` (connexion sans borne : 401, pas 500) |
| **Borne en octets** (front) | `max-utf8-bytes.validator.spec.ts` : chaîne ASCII de 72 octets acceptée, 36 « é » acceptés, 37 « é » refusés avec la borne et la taille réelle, « é » décomposé compté en NFC ; `password.validator.spec.ts` : 73 caractères passent ce validateur, la borne haute relève de `maxUtf8Bytes` ; `register.spec.ts` et `profile.spec.ts` : message complet de la borne en octets, mot de passe accentué de 72 octets accepté |
| **Contrat du mot de passe au profil** | `UserControllerIT.updateProfile_motDePasseVide_retourne400EtServiceJamaisAppele`, `updateProfile_motDePasseBlanc_retourne400EtServiceJamaisAppele` ; `SecurityIT.updateProfile_motDePasseAbsent_retourne200EtHashInchange` (chaîne complète) ; front : `profile.spec.ts` « omits the password key when the field is left empty » (remplace « sends a null password… »). Le test unitaire `UserServiceTest.updateProfile_motDePasseBlanc_hashInchange` est supprimé : la branche qu'il testait n'était pas atteignable par l'API |
| **Liste blanche du `username`** | `AuthControllerIT.register_usernameAuFormatEmail_retourne400EtServiceJamaisAppele`, `register_usernameAvecPointTiretEtTiretBas_retourne201` ; `UserControllerIT.updateProfile_usernameAuFormatEmail_…`, `updateProfile_usernameAvecPointTiretEtTiretBas_retourne200` ; front : `register.spec.ts` et `profile.spec.ts` refusent un nom avec espace ou lettre accentuée (message exact) et acceptent `Al.ice_9-B` |
| **JWT invalide ou expiré** | `SecurityIT.getProfile_jetonSigneAvecUneAutreCle_retourne401`, `getProfile_jetonExpire_retourne401`, témoins `getProfile_jetonValide_retourne200` et `login_utilisateurExistantBonMotDePasse_retourne200` |
| **CORS** | `SecurityConfigTest.corsConfigurationSource_originesSepareesParVirguleEtEspace_toutesAutorisees`, `corsConfigurationSource_entreesVides_ignorees` |
| **Double envoi** | « disables … and sends one request only … » dans `login.spec.ts`, `register.spec.ts`, `post-create.spec.ts` (« Créer »), `post-detail.spec.ts` (envoi de commentaire), `profile.spec.ts` (« Sauvegarder » et « Se désabonner »), `topic-list.spec.ts` (« S'abonner ») |
| **409 à l'abonnement** | `topic-list.spec.ts` « treats a 409 as "already subscribed": "Déjà abonné" and no notification » |
| **401 sans notification** | « leaves a 401 … to the interceptor and notifies nothing » dans `feed.spec.ts` (chargement du fil), `post-create.spec.ts` (liste des thèmes, publication), `post-detail.spec.ts` (chargement, commentaire) |
| **Accueil sans défilement** (e2e) | `accueil.cy.ts`, 2 tests (§7.2) |

## 9. Commandes de mesure

| Mesure | Commande | Résultat du 25 septembre 2026 |
|---|---|---|
| Back | `cd back && mise exec -- ./mvnw -q verify` ; comptes lus dans `target/surefire-reports` et `target/failsafe-reports`, couverture dans `target/site/jacoco/jacoco.csv` | 56 + 97 = 153 tests, 0 échec ; 98,94 % / 96,43 % / 98,88 % (instructions / branches / lignes) |
| Front | `cd front && npm test -- --watch=false` puis `npx ng test --watch=false --coverage` | 25 fichiers, 269 tests ; 99,15 % / 98,48 % / 99,31 % / 99,45 % (instructions / branches / fonctions / lignes) |
| End-to-end | `npx cypress verify` ; `npx ng serve --port 4201` ; `npm run e2e:firefox -- --config baseUrl=http://localhost:4201` | 5 specs, 7 tests, 7 verts |
| Checklists manuelles | `grep -cE '^\| *[A-Z]*-?[0-9]+'` sur les trois `back/docs/archives/*_TEST_CHECKLIST.md` | 26 + 4 + 44 = 74 lignes de scénario ; aucune collection Postman dans le dépôt (`find . -name "*postman*" -not -path "*/node_modules/*"` : aucun résultat) |

## 10. Limites assumées et ce qui n'est pas couvert

| Limite | Preuve | Conséquence | Traitement |
|---|---|---|---|
| **Jeton invalide ou expiré : couvert depuis la PR #29.** Les `*ControllerIT` gardent un `JwtDecoder` en `@MockitoBean` jamais stubbé ; `SecurityIT` utilise le vrai. | `SecurityIT` [test] | Signature invalide et expiration renvoient 401 ; le rejet de signature n'a pas de mutation propre (§5). | Réalisé (revue technique, axe a) |
| **Pas de test sur un serveur HTTP réel côté back.** `SecurityIT` traverse la vraie chaîne avec MockMvc, sans port ; les tests end-to-end Cypress appellent le vrai back, mais ne tournent pas dans `./mvnw verify`. | `SecurityIT` (`@AutoConfigureMockMvc`) [code] | Les e2e ne font pas partie du build Maven : ils se lancent à la main, back démarré. | Revue technique, axe b |
| **Chargement anticipé du fil non prouvé.** `findPostsByUserId` porte un `@EntityGraph(user, topic)` mais ses deux tests (`PostRepositoryIT.findPostsByUserId_*`) n'assertent pas `Hibernate.isInitialized` ; seuls les trois autres `@EntityGraph` le sont. | `PostRepositoryIT` [code] | Retirer ce graphe passerait inaperçu : le fil, requête la plus exposée au N+1, n'est protégé que par la lecture du code. | Une assertion à ajouter (10 min [estimation]) |
| **CORS testé sur la configuration, pas sur une requête.** `SecurityConfigTest` vérifie la liste d'origines produite (espaces retirés, entrées vides ignorées) ; aucun test n'envoie de requête `OPTIONS`. | `SecurityConfigTest` [test] | Une régression de méthodes ou d'en-têtes autorisés ne serait pas vue. En dev et derrière un reverse proxy même origine, le front n'a pas besoin de CORS (`DOCUMENTATION_TECHNIQUE.md` §1). | Revue technique, axe b |
| **Checklists manuelles jamais exécutées.** 74 scénarios de vérification manuelle, décrits dans les checklists archivées (`back/docs/archives/*_TEST_CHECKLIST.md`) ; aucune collection Postman ou Bruno n'est versionnée à ce jour. Leurs colonnes « Résultat observé » et « Statut » sont vides. | lecture des trois fichiers [code], §9 | Elles documentent des scénarios attendus, pas des résultats. Le JWT expiré a désormais un équivalent automatisé (`SecurityIT`) ; le hash `$2a$` en base n'en a pas. | Archivées comme trace (PR #28) |
| **Inscription sans mot de passe : couverte depuis la correction de l'axe p.** Au commit `d2cdd31`, `RegisterRequest.password` n'avait pas de `@NotBlank` et aucun des huit `register_*` n'envoyait un corps sans ce champ. Comportement mesuré avant correction : 201 à corps vide sous `@WebMvcTest` ; 409 trompeur en contexte complet (`encode(null)` renvoie `null`, `password_hash NOT NULL` violé). | `register_motDePasseAbsent_retourne400EtServiceJamaisAppele` [test, commit `9581c70`], branche `fix/validation-mot-de-passe-obligatoire` avant intégration [mesuré] | Trou de spécification de test, invisible à la preuve par mutation qui ne renforce que des tests existants. | Corrigé : `@NotBlank` + test écrit avant la correction (échec 201 ≠ 400). |
| **Pas de mutation testing outillé.** | `pom.xml` sans PIT [code] | La preuve par mutation (§5) n'est pas rejouable automatiquement ; une régression de la qualité des tests ne serait pas détectée. | À reconsidérer si la suite grossit |
| **Pas de seuil de couverture bloquant.** | `pom.xml` sans goal `check` ; aucun seuil dans `front/angular.json` [code] | Une baisse de couverture ne casse pas le build. | Choix assumé : mesure informative |

Note sur le journal : trois décomptes de `TESTS_REVIEW.md` (tests de validation, assertions `fieldErrors`, tests 401) ont été re-mesurés le 19 septembre et corrigés sur place ; la note datée en fin de `TESTS_REVIEW.md` décrit la méthode de mesure. Les chiffres du présent rapport sont ceux mesurés.

Mesure du 25 septembre 2026, sur `main` au commit `2a01fc9`, branche `docs/passe-finale` : `mise exec -- ./mvnw -q verify` depuis `back/` (Docker démarré), comptes relus dans les XML de `target/surefire-reports` et `target/failsafe-reports` (attributs `tests`, `failures`, `errors`, `skipped`), couverture sommée sur `target/site/jacoco/jacoco.csv` ; `npm test -- --watch=false` et `npx ng test --watch=false --coverage` depuis `front/` ; `npm run e2e:firefox -- --config baseUrl=http://localhost:4201` contre `ng serve --port 4201` et le back sur 8080 ; décomptes du §6 par grep sur `back/src/test/java`.
