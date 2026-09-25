# Rapport de couverture et de tests — MDD (backend)

Projet MDD (« Monde de Dev »), OpenClassrooms P5 option B. Rapport établi le 19 septembre 2026 sur le code de `main` au commit `d2cdd31` ; les renvois à `TESTS_REVIEW.md` visent son état après le correctif de décomptes du même jour (commit `d754d38`). Mis à jour le même jour après la correction de l'anomalie p de la revue technique (branche `fix/validation-mot-de-passe-obligatoire`, intégrée dans `main` par le commit `9581c70` ; référence mise à jour le 24 septembre 2026) : un test ajouté, total 127.

## 1. Périmètre et état du projet

Ce rapport couvre **uniquement le backend** (`back/`, Spring Boot 4.1.0, Java 21). Le front-end Angular 21 (`front/`) n'est pas commencé : `front/src/app/app.routes.ts` contient un tableau vide et aucun composant, service, garde ou intercepteur n'existe. Il n'y a donc **ni test front, ni test end-to-end** : les indicateurs de la grille « tests d'intégration et end-to-end pour valider le parcours utilisateur complet » et « communication front/back fluide et sécurisée » ne sont couverts que pour leur moitié côté API ; « tests unitaires couvrant les composants critiques du front-end et du back-end » ne l'est que pour le back.

Le texte de mission impose un seuil de couverture de 70 % **[texte de mission, source hors dépôt]** — seuil issu du texte de mission sur la plateforme, non repris dans la grille d'auto-évaluation. Aucun template de rapport de tests n'est fourni par la mission ; la structure ci-dessous est libre.

Origine des chiffres, conformément à la règle de preuve : **[mesuré]** = exécuté dans cette session (`./mvnw clean verify`, Docker démarré, rapports `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/jacoco.csv`) ; **[code]** = lu dans un fichier, cité ; **[test]** = comportement verrouillé par un test nommé ; **[TESTS_REVIEW]** = repris du journal `back/docs/TESTS_REVIEW.md`, section citée.

Reproduire : `cd back && ./mvnw clean verify`. Prérequis : Docker démarré, et la variable `JWT_SECRET` exportée dans le shell (le fichier gitignoré `application-local.properties` la référence ; sans elle, `MddApiApplicationIT` échoue sur `Could not resolve placeholder 'JWT_SECRET'` — vérifié dans cette session). `./mvnw test` seul (strate unitaire) n'a besoin de rien. Rapport HTML dans `back/target/site/jacoco/index.html`.

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

Volontairement absents [TESTS_REVIEW « Axes d'amélioration », « Outils »] : PIT (mutation testing outillé — dépendance supplémentaire, compatibilité Boot 4 / Java 21 non vérifiée, remplacé par une mutation manuelle, §5) ; H2 (dialecte différent de MySQL : `LONGTEXT`, sous-requêtes `IN (SELECT …)` ; remplacé par Testcontainers) ; seuil JaCoCo bloquant.

Aucun analyseur statique n'est configuré (pas de SpotBugs, Checkstyle, PMD ni Sonar dans `pom.xml` [mesuré]). Les deux outils d'analyse du projet sont JaCoCo, qui a localisé les zones non exécutées (§4), et la preuve par mutation manuelle, qui a localisé les assertions trop faibles (§5) ; c'est à ce titre qu'ils tiennent lieu d'« outils d'analyse pour identifier et corriger les points faibles ».

## 3. Stratégie : quatre strates, ce que chacune garantit

Tests nommés `méthode_condition_résultat`, un cas par test, structure Arrange–Act–Assert, aucune dépendance d'ordre. Deux classes abstraites factorisent l'infrastructure : `AbstractContainerIT` (conteneur MySQL singleton) et `AbstractRepositoryIT` (helpers `persistUser`/`persistTopic`/`persistPost` avec `persistAndFlush`).

| Strate | Classes [mesuré : nombre de tests] | Garantit | Ne garantit pas |
|---|---|---|---|
| **A. Unitaire pur** (Surefire, Mockito, aucun contexte Spring) | `UserServiceTest` 17, `PostServiceTest` 10, `AuthServiceTest` 5, `TopicServiceTest` 2, `JwtServiceTest` 4, `UserDetailsImplTest` 4, `UserDetailsServiceImplTest` 3 — **45** | Logique métier et conditions de garde (exception levée, `save` jamais appelé via `never()`), mapping entité → DTO champ par champ, hachage du mot de passe (`ArgumentCaptor<User>` : `passwordHash` ≠ clair), forme des claims JWT (`ArgumentCaptor<JwtEncoderParameters>`). | Requêtes SQL réelles, sérialisation JSON, Bean Validation, chaîne de filtres de sécurité, encodage HMAC réel (le `JwtEncoder` est mocké). |
| **B. Contrôleur** (`@WebMvcTest` + `@Import({SecurityConfig, GlobalExceptionHandler})`, services et `JwtDecoder` en `@MockitoBean`) | `UserControllerIT` 30, `PostControllerIT` 14, `AuthControllerIT` 13, `TopicControllerIT` 2 — **59** | Routage et codes HTTP, validation `@Valid` et contenu de `fieldErrors`, mapping exception → statut par `GlobalExceptionHandler`, extraction de l'identité depuis `sub`, 401 sans token, 400 sur paramètre non numérique, absence de `password`/`passwordHash` dans les réponses. | Le service réel (mocké), le décodage réel d'un JWT (§7), le rendu réel de la page `/error`. |
| **C. Persistance** (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers) | `UserRepositoryIT` 11, `SubscriptionRepositoryIT` 7, `PostRepositoryIT` 3, `CommentRepositoryIT` 1 — **22** | JPQL et requêtes dérivées exécutées sur MySQL 8.4 réel : filtres, tri, contraintes ; chargement anticipé prouvé par `Hibernate.isInitialized(...)` avant tout accès pour trois des quatre `@EntityGraph` du code. | Services et contrôleurs ; comportement transactionnel réel (chaque test est annulé) ; le chargement anticipé de `findPostsByUserId` (le fil) n'est pas prouvé (§7). |
| **D. Contexte complet** (`@SpringBootTest` sur le conteneur) | `MddApiApplicationIT` 1 — **1** | Le contexte Spring démarre avec la vraie configuration de sécurité et une base MySQL. | Aucun appel HTTP, aucune assertion métier. |

Total : **127 tests** = 45 Surefire + 82 Failsafe, 0 échec, 0 erreur, 0 ignoré [mesuré après la correction de l'axe p ; 126 = 45 + 81 au commit `d2cdd31`].

Isolation : la strate A ne touche ni Spring ni la base ; B n'instancie que le contrôleur visé ; C annule sa transaction après chaque test ; le conteneur est partagé mais jamais l'état.

## 4. Chiffres de couverture (ligne de base du 19 septembre 2026)

Mesurés au commit `d2cdd31` et re-mesurés à l'identique après la correction de l'axe p (le `@NotBlank` ajouté vit dans une classe Lombok hors périmètre JaCoCo, et le test ajouté n'exécute aucune ligne nouvelle) [mesuré].

Mesure JaCoCo sur `target/site/jacoco/jacoco.csv`, 22 classes analysées [mesuré] :

| Périmètre | Instructions | Lignes | Branches |
|---|---|---|---|
| **Total** | **1 054 / 1 059 = 99,5 %** | **250 / 252 = 99,2 %** | **22 / 22 = 100 %** |
| `controller` | 158 / 158 = 100 % | 39 / 39 | — (aucune branche) |
| `service` | 537 / 537 = 100 % | 122 / 122 | 20 / 20 = 100 % |
| `exception` | 130 / 130 = 100 % | 30 / 30 | 2 / 2 = 100 % |
| `security` | 106 / 106 = 100 % | 22 / 22 | — |
| `security.jwt` | 66 / 66 = 100 % | 19 / 19 | — |
| `security.services` | 54 / 54 = 100 % | 17 / 17 | — |
| racine (`MddApiApplication`) | 3 / 8 = 37,5 % | 1 / 3 = 33,3 % | — |

Le seuil de 70 % du texte de mission est dépassé sur les trois métriques au total et dans chaque package hors racine ; la racine ne contient que `MddApiApplication` (voir ci-dessous).

Ce qui n'apparaît pas dans le tableau, et pourquoi :

- **`MddApiApplication.main`** : 2 lignes non couvertes, seul manque du projet. Décision documentée de ne pas les exclure du périmètre ni de les couvrir par un `MockedStatic` : « un 99,2 % expliqué vaut mieux qu'un 100 % obtenu en réduisant le périmètre mesuré » [TESTS_REVIEW « Ce qui reste non couvert »].
- **`dto` et `model`** : absents du CSV. Ce n'est pas une exclusion configurée (aucun `<excludes>` dans `pom.xml`) : le bytecode de ces classes porte `@lombok.Generated` (vérifié par `javap` sur `model.Topic`) et JaCoCo filtre ce code par défaut ; une classe entièrement générée disparaît du rapport [mesuré].
- **`repository`** : interfaces Spring Data sans bytecode propre ; leurs requêtes sont exercées par la strate C.

Évolution [TESTS_REVIEW « Diagnostic initial », « Résultat »] : avant la revue des tests du 18 septembre, 95 tests et 87 % d'instructions, avec deux trous réels masqués par la moyenne — `security.services` à 11 % et `security.jwt` à 57 %, c'est-à-dire la chaîne d'authentification jamais exécutée par un test. 24 tests ont comblé ces trous (99,5 %), puis une seconde phase a ajouté 7 tests et renforcé 23 autres **sans faire bouger la couverture d'un dixième de point** — c'est l'objet de la section suivante.

## 5. Qualité des tests : preuve par mutation manuelle

Une couverture de 99,5 % dit qu'un test exécute chaque ligne ; elle ne dit pas qu'il échouerait si la ligne était fausse. Pour le vérifier, chacun des tests existants a été soumis à la question « quel bug plausible ce test laisserait-il passer ? », puis le bug a été réellement introduit. Protocole en cinq temps [TESTS_REVIEW « Seconde phase »] :

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

Anomalies corrigées : aucune mutation n'a mis au jour un bug **réel** du code de production — les quinze cas sont des trous de filet, pas des défauts. Les corrections portent donc exclusivement sur `src/test/java`, ce que garantit le contrôle `git diff src/main/java` vide en fin de chantier. Une anomalie réelle a en revanche été trouvée **hors** mutation, par la relecture adverse de la revue technique (axe p : mot de passe non obligatoire à l'inscription) et corrigée par le commit `9581c70` avec un test écrit avant la correction — voir §6 et la revue technique. Les autres axes de `REVUE_TECHNIQUE.md` §2 restent ouverts, chacun avec une recommandation chiffrée.

## 6. Sécurité et contrat d'erreur verrouillés par les tests

- **Accès sans jeton** : 9 tests `*_sansJwt_retourne401` (un par endpoint protégé exercé : `TopicControllerIT` 1, `PostControllerIT` 3, `UserControllerIT` 5) [mesuré par grep `isUnauthorized()`].
- **Identifiants invalides** : `login_identifiantsInvalides_retourne401` fige le comportement natif de Spring Security — corps vide, en-tête `WWW-Authenticate: Bearer` — puisqu'aucun handler applicatif ne le redéfinit [test].
- **Non-divulgation par les exceptions** : `AuthControllerIT` et `UserControllerIT` assertent `jsonPath("$.password").doesNotExist()` et `$.passwordHash` absent des réponses ; `DataIntegrityViolationException` → 409 avec message générique, aucun détail SQL [test `UserControllerIT`, handler `GlobalExceptionHandler.handleDataIntegrityViolation`].
- **Logs** : aucun logger applicatif dans `src/main/java` (grep `Logger`, `@Slf4j`, `log.` : 0) [mesuré], donc aucune trace applicative ne peut contenir un secret. En revanche `spring.jpa.show-sql=true` est dans le profil commun : les requêtes SQL (pas leurs paramètres, liés en `?`) sont journalisées — limite consignée dans la revue technique, axe c.
- **Validation d'entrée** : 16 tests de corps invalide (400) avec, pour sept d'entre eux, la clé et le message exacts de `fieldErrors` ; 4 tests de paramètre d'URL invalide (trois id non numériques, un `sort` hors `asc|desc`) [mesuré].
- **Identité issue du jeton** : les contrôleurs reçoivent `userId` = `jwt.getSubject()` ; sur les chemins nominaux, les tests utilisent des identifiants différents pour l'utilisateur et la ressource (sujet `42`, topic `7`), ce qui rend une inversion d'arguments détectable [test `UserControllerIT.subscribe_avecJwtValideEtSucces_retourne200`, `unsubscribe_…`].
- **JSON malformé** : `register_corpsJsonMalforme_retourne400` fige un 400 à corps vide sous MockMvc — un comportement hors format `ErrorResponse`, connu et documenté (voir revue technique, axe e).

## 7. Limites assumées et ce qui n'est pas couvert

| Limite | Preuve | Conséquence | Traitement |
|---|---|---|---|
| **Aucun test de JWT présent mais invalide ou expiré.** Le `JwtDecoder` des `*ControllerIT` est un `@MockitoBean` **jamais stubbé** (aucun `when(jwtDecoder…)`, aucune `JwtException` dans `src/test/java`) ; l'authentification des tests passe par `jwt()` qui court-circuite le filtre Bearer. | grep sur `src/test/java` [mesuré] | Le chemin « en-tête `Authorization` → décodage → 401 sur signature ou expiration » n'est exécuté par aucun test. Le cas le plus fréquent en production (utilisateur revenant après 24 h) n'est pas couvert. | Revue technique, recommandation des axes a–b |
| **Aucun test de câblage bout-en-bout** contrôleur → service → repository → base. Le seul `@SpringBootTest` (`MddApiApplicationIT`) ne fait aucun appel. | `MddApiApplicationIT` [code] | Une inversion d'arguments entre deux couches resterait invisible ; l'encodage HMAC réel (`JwtConfig`) n'est exécuté que par le démarrage du contexte. | Revue technique, recommandation des axes a–b |
| **Chargement anticipé du fil non prouvé.** `findPostsByUserId` porte un `@EntityGraph(user, topic)` mais ses deux tests (`PostRepositoryIT.findPostsByUserId_*`) n'assertent pas `Hibernate.isInitialized` ; seuls les trois autres `@EntityGraph` le sont. | `PostRepositoryIT` [code] | Retirer ce graphe passerait inaperçu : le fil, requête la plus exposée au N+1, n'est protégé que par la lecture du code. | Une assertion à ajouter (10 min [estimation]) |
| **Aucun test end-to-end, aucun test front.** | `front/src/app/app.routes.ts` vide [code] | Le « parcours utilisateur complet » de la grille ne peut pas être validé. | Bloqué par le front |
| **CORS non testé** (`mdd.cors.allowed-origins`). | grep `cors` sur `src/test` : 0 [mesuré] | Une régression de la configuration CORS ne serait vue qu'au premier appel du front. | Revue technique, axes a–b (à inclure dans le test bout-en-bout) |
| **Checklists manuelles jamais exécutées.** `back/docs/*_TEST_CHECKLIST.md` (74 scénarios Postman au total) ont leurs colonnes « Résultat observé » et « Statut » vides. | lecture des trois fichiers [code] | Elles documentent des scénarios attendus, pas des résultats. Plusieurs cas qu'elles listent (JWT expiré, hash `$2a$` en base) n'ont pas d'équivalent automatisé. | Conservées comme spécification ; à exécuter ou supprimer à la livraison du front |
| **Inscription sans mot de passe : couverte depuis la correction de l'axe p.** Au commit `d2cdd31`, `RegisterRequest.password` n'avait pas de `@NotBlank` et aucun des huit `register_*` n'envoyait un corps sans ce champ. Comportement mesuré avant correction : 201 à corps vide sous `@WebMvcTest` ; 409 trompeur en contexte complet (`encode(null)` renvoie `null`, `password_hash NOT NULL` violé). | `register_motDePasseAbsent_retourne400EtServiceJamaisAppele` [test, commit `9581c70`], branche `fix/validation-mot-de-passe-obligatoire` avant intégration [mesuré] | Trou de spécification de test, invisible à la preuve par mutation qui ne renforce que des tests existants. | Corrigé : `@NotBlank` + test écrit avant la correction (échec 201 ≠ 400), 127 tests verts. |
| **Pas de mutation testing outillé.** | `pom.xml` sans PIT [code] | La preuve par mutation (§5) n'est pas rejouable automatiquement ; une régression de la qualité des tests ne serait pas détectée. | À reconsidérer si la suite grossit |
| **Pas de seuil de couverture bloquant.** | `pom.xml` sans goal `check` [code] | Une baisse de couverture ne casse pas le build. | Choix assumé : mesure informative |

Note sur le journal : trois décomptes de `TESTS_REVIEW.md` (tests de validation, assertions `fieldErrors`, tests 401) ont été re-mesurés dans cette session et corrigés sur place ; la note datée en fin de `TESTS_REVIEW.md` décrit la méthode de mesure. Les chiffres du présent rapport sont ceux mesurés.
