# Rapport de couverture et de tests — MDD

Projet MDD (« Monde de Dev »), OpenClassrooms P5 option B. Rapport réécrit le 14 octobre 2026 après la refonte de la stratégie de tests (PR #35 à #39), puis mis à jour le 27 octobre 2026 après la migration des tests d'intégration (PR #56). Il remplace la version du 25 septembre 2026, qui décrivait une stratégie en quatre strates (unitaire, `@WebMvcTest`, `@DataJpaTest`, contexte complet) ; le journal de cette ancienne stratégie reste dans `TESTS_REVIEW.md`, daté du 18 septembre 2026.

## 1. Périmètre et origine des chiffres

Ce rapport couvre le **backend** (`back/`, Spring Boot 4.1.0, Java 21), le **front-end** Angular 21.2 (`front/`, tests unitaires et de composants) et les **tests end-to-end** Cypress qui traversent les deux.

La page de présentation du projet recommande une couverture de test d'au moins 70 % (étape 7, parmi les recommandations) **[page de présentation, source hors dépôt]**. Aucun template de rapport de tests n'est fourni ; la structure ci-dessous est libre.

Origine des chiffres : **[mesuré]** = exécuté, commande au §11 ; **[code]** = lu dans un fichier, cité ; **[test]** = comportement verrouillé par un test nommé ; **[PR]** = repris de la description d'une pull request fusionnée.

## 2. Stratégie : chaque niveau vérifie ce que les autres ne voient pas

La stratégie a été simplifiée le 8 octobre 2026, avec une règle : chaque comportement observable est vérifié au niveau qui l'observe le plus directement. Elle a été précisée le 22 octobre : un test de niveau supérieur n'est un doublon que s'il vérifie une condition déjà vérifiée plus bas **sans rien ajouter** (Ham Vocke, *The Practical Test Pyramid*, martinfowler.com).

| Niveau | Outil | Vérifie | Ne vérifie pas | Tests [mesuré] |
|---|---|---|---|---|
| **Intégration back** | `@SpringBootTest` + MockMvc + MySQL 8.4 (Testcontainers) | Chaque action de la spécification côté API : contrat HTTP, règles métier, état réel en base ; la chaîne de sécurité (filtre JWT, refus d'accès), exercée par l'inscription, la connexion et `AuthentificationIT` ; le nombre de requêtes SQL des lectures principales | Le conteneur de servlets (pages d'erreur) ; les commits réels (transaction annulée après chaque test) ; le rendu | 93 |
| **Unitaire back** | JUnit Jupiter 6, Mockito 5, AssertJ | Les règles fines que l'intégration ne peut pas épingler précisément : bornes, normalisation, claims du jeton, longueur minimale de la clé, découpage CORS, 409 d'une contrainte de la base | Le câblage avec le reste de l'application | 17 |
| **Unitaire front** | Vitest + jsdom | Composants, services, gardes, intercepteur, validateurs, avec l'API simulée (`HttpTestingController`) | Le vrai serveur | 271 |
| **End-to-end** | Cypress (Firefox) | Le parcours utilisateur complet dans un vrai navigateur, contre le vrai back et sa base : rendu, routage, gardes, intercepteur, commits réels | Les cas d'erreur fins (laissés aux niveaux inférieurs) | 7 |

Back : **110 tests** = 17 Surefire + 93 Failsafe, 0 échec [mesuré le 27 octobre 2026, commit `90016b8`]. Avant la refonte : 158 tests (57 + 101), dont 67 `@WebMvcTest` et 23 `@DataJpaTest` [mesuré le 8 octobre 2026] ; un même observable y était souvent vérifié deux ou trois fois (service mocké, contrôleur mocké, contexte complet). Les trois derniers `@DataJpaTest` ont été remplacés par la PR #54 par une classe d'intégration qui compte les requêtes SQL (§5).

## 3. Tests d'intégration back : un fichier par action de la spécification

Package `back/src/test/java/com/openclassrooms/mddapi/parcours/` [code].

| Action de la spécification | Fichier | Cas [mesuré] |
|---|---|---|
| S'inscrire | `InscriptionIT` | 18 |
| Se connecter (e-mail ou nom d'utilisateur) | `ConnexionIT` | 10 |
| Consulter son profil | `ConsultationProfilIT` | 3 |
| Modifier son profil | `ModificationProfilIT` | 14 |
| Consulter la liste des thèmes | `ConsultationThemesIT` | 3 |
| S'abonner à un thème | `AbonnementIT` | 4 |
| Se désabonner | `DesabonnementIT` | 4 |
| Consulter son fil d'actualité | `ConsultationFilIT` | 3 |
| Trier le fil | `TriFilIT` | 3 |
| Créer un article (auteur et date automatiques) | `CreationArticleIT` | 6 |
| Consulter un article et ses commentaires | `ConsultationArticleIT` | 3 |
| Commenter (auteur et date automatiques) | `CommentaireIT` | 4 |
| Transversal : toute route protégée exige un jeton valide, et un jeton valide est accepté pour le bon utilisateur | `AuthentificationIT` | 15 |
| Transversal : nombre de requêtes SQL des trois lectures principales | `NombreDeRequetesIT` | 3 |

La déconnexion n'a pas de route côté API (jeton sans état) : elle est couverte côté front et en end-to-end.

Conventions communes [code] :

- Deux classes de base, adossées au conteneur MySQL partagé d'`AbstractContainerIT`. Le contexte Spring est complet (vrais services, vraie base créée par les migrations Flyway) :
  - `ActionIT` (`@SpringBootTest`, `@AutoConfigureMockMvc(addFilters = false)`, `@Transactional`), pour les classes d'action et `NombreDeRequetesIT` : les filtres ne sont pas exécutés ; l'Arrange crée les données par les repositories et pose lui-même l'utilisateur connecté, avec le même objet que `JwtAuthenticationFilter` (principal = id numérique) ; seul l'Act est une requête HTTP ;
  - `ChaineDeSecuriteIT` (mêmes annotations, filtres compris), pour `InscriptionIT`, `ConnexionIT` et `AuthentificationIT` : seule l'action testée passe par sa route ; les comptes sont créés par le repository, avec l'encodeur de mots de passe de l'application.
- **Pourquoi deux bases** (révision des 26 et 27 octobre 2026, à la suite d'une revue) : quand chaque test préparait ses données par les vraies routes et traversait la chaîne de sécurité, une erreur dans l'inscription ou dans le filtre JWT faisait échouer des dizaines de tests sans rapport avec elle. Au début de la migration, une mutation du filtre (jeton accepté pour un autre utilisateur) faisait encore échouer 27 tests ; elle n'en fait plus échouer qu'un, celui qui vérifie ce chemin (§9).
- Seuls les thèmes de référence viennent de la migration `V2__insert_reference_topics.sql`.
- **Un seul Act par test**, structure Arrange–Act–Assert commentée, nommage `action_condition_resultat`.
- **Relecture en base** : quand un test vérifie l'état persisté (hash du mot de passe, abonnement enregistré ou supprimé, auteur et date d'un article), il appelle `viderLeCache()` (`flush` puis `clear` de l'`EntityManager`) **après** les assertions sur la réponse HTTP et **juste avant** la relecture, pour que celle-ci interroge MySQL et non le cache d'Hibernate. Placé plus tôt, une erreur de contrainte au moment du `flush` cassait la transaction partagée et masquait l'assertion visée : observé sous mutation le 8 octobre 2026. Exception : `NombreDeRequetesIT` l'appelle en fin d'Arrange (§5).

## 4. Tests unitaires back gardés, et pourquoi

| Classe | Tests | Pourquoi au niveau unitaire |
|---|---|---|
| `PostServiceTest` | 2 | Borne exacte de l'extrait du fil : 200 caractères conservés, 201 et plus tronqués à 200 suivis de « … ». Un cas limite précis, coûteux à reproduire par l'API |
| `MaxUtf8BytesValidatorTest` | 5 | Borne de 72 octets UTF-8 sur la forme NFC (BCrypt refuse au-delà) : 72 octets ASCII acceptés, 73 refusés, 36 « é » précomposés (72 octets) acceptés, 37 refusés, 36 « é » en NFD comptés sous leur forme NFC. L'intégration vérifie seulement que la règle est branchée (74 octets → 400) |
| `NfcPasswordEncoderTest` | 2 | Un mot de passe encodé en NFD correspond au même en NFC, et inversement : la normalisation avant BCrypt. Que cet encodeur soit bien celui de l'application est vérifié par `ConnexionIT` (§9) |
| `JwtServiceTest` | 3 | Aller-retour émission puis lecture (`sub` relu comme id), en-tête `HS256`, claims `iss` et `iat`, expiration égale à la durée configurée, et refus au démarrage d'une clé de moins de 256 bits. Sans Spring ni mock : une clé aléatoire par test |
| `SecurityConfigTest` | 3 | Découpage de la liste des origines CORS (espaces, entrées vides) et méthodes autorisées |
| `GlobalExceptionHandlerTest` | 2 | Le 409 générique renvoyé quand la base refuse une écriture (`DataIntegrityViolationException`). En usage normal, l'API ne l'atteint pas : l'e-mail, le nom d'utilisateur et l'abonnement en double sont refusés avant par les services ; il reste le cas de deux écritures concurrentes |

## 5. Nombre de requêtes : la preuve du chargement anticipé

`NombreDeRequetesIT` compte les requêtes SQL des trois lectures principales, lors de l'appel de la route par MockMvc : fil d'actualité 1, détail d'un article 2 (l'article avec son auteur et son thème, puis les commentaires avec leur auteur), profil 2 (le compte, puis les abonnements avec leur thème) [test]. Les statistiques d'Hibernate ne sont activées que pour cette classe (`@TestPropertySource`). `viderLeCache()` est appelé en fin d'Arrange et les statistiques sont remises à zéro, pour que seules les requêtes de l'Act soient comptées. Le nombre attendu est exact : toute requête ajoutée à ces lectures fait échouer le test, volontairement.

Les autres tests d'intégration ne peuvent pas voir ce chargement : dans leur transaction ouverte, un chargement paresseux réussit aussi, avec une requête de plus par auteur, thème ou auteur de commentaire. Retirer un `@EntityGraph` fait passer le fil de 1 à 5 requêtes, le détail de 2 à 4 ou à 5, le profil de 2 à 5 [mesuré sous mutation le 27 octobre 2026].

Cette classe a remplacé, dans la PR #54, les trois tests `@DataJpaTest` qui vérifiaient le chargement par `Hibernate.isInitialized(...)` : ils ne couvraient pas le fil, la lecture la plus exposée, et imposaient un quatrième type de test. Leurs dépendances ont été retirées le 27 octobre, sans changement du résultat de la suite.

## 6. Front-end et end-to-end

### 6.1 Tests unitaires et de composants

`npx ng test --watch=false` : **25 fichiers de spec, 271 tests**, tous verts [mesuré le 23 octobre 2026, commit `ddc4d80` ; aucun commit ne touche `front/` entre cette mesure et le commit `b02daf2`]. Chaque fichier testé a son `*.spec.ts` à côté de lui. Les appels HTTP sont interceptés par `HttpTestingController`, qui vérifie l'URL, la méthode et le corps de chaque requête. Le code de sécurité (service d'authentification, intercepteur, gardes, validateur de mot de passe) est prouvé par mutation manuelle, et chaque spec de feature compte au moins une mutation [PR #30] ; pour les specs modifiées depuis par les PR #46, #51 et #52, la preuve n'est pas retrouvée dans le dépôt ni dans les PR.

### 6.2 End-to-end (Cypress)

Contre le back réel sur `http://localhost:8080` et sa base MySQL : **5 specs, 7 tests, 7 verts** [mesuré le 27 octobre 2026, Firefox 158, Cypress 16.1.0, commit `b02daf2`] :

| Spec | Tests | Parcours |
|---|---|---|
| `parcours-complet.cy.ts` | 1 | Inscription, abonnement à un thème (« Déjà abonné » inactif), création de deux articles, tri du fil dans les deux sens, commentaire, profil et désabonnement, modification du nom d'utilisateur, déconnexion, reconnexion par nom d'utilisateur puis par e-mail, refus d'un mauvais mot de passe |
| `accueil.cy.ts` | 2 | L'accueil tient dans l'écran sans défilement, à 1280 × 800 et à 375 × 667 |
| `gardes.cy.ts` | 2 | Visiteur non connecté redirigé de `/feed` vers `/login` ; utilisateur connecté redirigé de `/login` vers `/feed` (persistance de la session) |
| `mobile.cy.ts` | 1 | Connexion, menu burger, « Thèmes » puis déconnexion depuis le menu |
| `validation.cy.ts` | 1 | Mot de passe faible refusé à l'inscription : message d'erreur, aucune requête, aucune redirection |

Isolement : chaque test crée un utilisateur au nom unique (horodatage et partie aléatoire, `front/cypress/support/users.ts`) ; la base n'est pas remise à zéro entre deux lancements. Firefox plutôt qu'Electron : sous Wayland, Electron sans fenêtre ne termine pas les transitions CSS [PR #27].

## 7. Composants critiques et tests qui les protègent

| Composant ou règle | Test(s) |
|---|---|
| Mot de passe haché, jamais stocké ni renvoyé en clair | `InscriptionIT` (relecture en base : hash différent du mot de passe, `matches` vrai ; aucun mot de passe dans la réponse) |
| Identité tirée du jeton, jamais du corps ni de l'URL | `InscriptionIT` et `ConnexionIT` (`sub` du jeton = id du compte) ; `AuthentificationIT` (un jeton valide atteint le contrôleur avec le bon id) ; `CreationArticleIT` (auteur et date imposés dans le corps ignorés) |
| Accès refusé sans jeton valide | `AuthentificationIT` : 401 avec `WWW-Authenticate: Bearer` sans jeton sur les 9 routes protégées, avec un jeton signé avec une autre clé, avec un jeton expiré, avec un jeton valide envoyé sans le préfixe `Bearer` ; le refus du jeton est prouvé par mutation de `JwtAuthenticationFilter` et de `JwtService` (§9) |
| Unicité de l'e-mail et du nom d'utilisateur | `InscriptionIT` et `ModificationProfilIT` (409, message exact) ; en dernier recours, contrainte `UNIQUE` de la base (409 générique, `GlobalExceptionHandlerTest`) |
| Borne de 72 octets et normalisation NFC | `MaxUtf8BytesValidatorTest`, `NfcPasswordEncoderTest` ; borne branchée : `InscriptionIT` et `ModificationProfilIT` (74 octets → 400), `ConnexionIT` (plus de 72 octets → 401) ; normalisation branchée : `ConnexionIT`, seul test en échec si l'encodeur de l'application perd la normalisation (§9) |
| Double abonnement refusé | `AbonnementIT.abonnement_dejaAbonne_retourne409` (message applicatif, distinct du 409 générique de la contrainte `UNIQUE`) |
| Désabonnement limité au compte courant, idempotent | `DesabonnementIT` (`nAffectePasLesAutresComptes`, 204 sans abonnement) |
| Fil limité aux thèmes suivis, tri dans les deux sens | `ConsultationFilIT`, `TriFilIT` ; `parcours-complet.cy.ts` |
| Auteur et date définis par le serveur | `CreationArticleIT`, `CommentaireIT` |
| Extrait du fil | `PostServiceTest` |
| Chargement anticipé (N+1 évité) | `NombreDeRequetesIT` |
| Contrat d'erreur `ErrorResponse` | Message exact asserté dans chaque fichier d'action (400, 404, 409) ; JSON malformé dans `InscriptionIT` ; 409 générique de la base dans `GlobalExceptionHandlerTest` |
| CORS | `SecurityConfigTest` |
| Gardes, intercepteur, stockage du jeton (front) | Specs `auth.guard`, `guest.guard`, `auth.interceptor`, `auth.service` ; `gardes.cy.ts` |
| Parcours utilisateur complet | `parcours-complet.cy.ts`, `mobile.cy.ts` |

## 8. Couverture

### 8.1 Back-end (mesure du 27 octobre 2026)

`./mvnw clean verify`, JaCoCo, 25 classes analysées [mesuré, commit `90016b8`] :

| Métrique | Couvert / total |
|---|---|
| Lignes | 289 / 293 = **98,6 %** |
| Branches | 33 / 34 = **97,1 %** |

Mesures précédentes, même commande : 289 / 293 lignes et 32 / 34 branches sur `main` après la PR #54 [mesuré le 23 octobre 2026] ; 277 / 282 lignes et 30 / 32 branches après le passage à jjwt [mesuré le 15 octobre 2026]. Depuis le 23 octobre, la ligne qui vérifie le préfixe `Bearer` dans `JwtAuthenticationFilter` n'a plus de branche manquée. La recommandation de 70 % de la page de présentation est dépassée.

Lignes non couvertes, et pourquoi :

- **`MddApiApplication.main`** (2 lignes) : le contexte est démarré par les tests d'intégration via `@SpringBootTest`, jamais par un appel à `main`. Ni exclusion du périmètre, ni `MockedStatic` : un chiffre expliqué vaut mieux qu'un chiffre obtenu en réduisant le périmètre mesuré.
- **`NfcPasswordEncoder`** : `upgradeEncoding` (simple délégation, jamais appelée) et la branche `rawPassword == null` de la normalisation.
- **`UserDetailsImpl.getUsername()`** : imposé par l'interface `UserDetails` de Spring Security, il n'est exécuté par aucun parcours de l'application. Le seul test qui l'appelait vérifiait ce getter isolément ; il a été supprimé plutôt que gardé pour le chiffre.
- **`dto` et `model`** : absents du rapport, sans exclusion configurée : leur bytecode est généré par Lombok (`@lombok.Generated`), que JaCoCo filtre par défaut. **`repository`** : interfaces Spring Data sans bytecode propre.

Ne figurent plus dans cette liste : le gestionnaire `DataIntegrityViolationException`, couvert par `GlobalExceptionHandlerTest` (§4), et la branche « en-tête sans préfixe `Bearer` » de `JwtAuthenticationFilter`, testée par `AuthentificationIT` depuis le 27 octobre.

La mesure doit être faite avec `clean` : sans lui, `target/jacoco.exec` cumule les exécutions précédentes et affiche comme couvertes des lignes qu'aucun test courant n'exécute (constaté le 9 octobre 2026).

### 8.2 Front-end (mesure du 23 octobre 2026)

`npx ng test --watch=false --coverage` (fournisseur v8), 25 fichiers, 271 tests [mesuré, commit `ddc4d80`] :

| Métrique | Couverture |
|---|---|
| Instructions (*statements*) | **99,15 %** |
| Branches | **98,49 %** |
| Fonctions | **99,31 %** |
| Lignes | **99,45 %** |

Les fichiers utilitaires de test hors `*.spec.ts`, dont `features/auth/testing.ts`, sont comptés dans ce périmètre. Rapport HTML dans `front/coverage/front/index.html`.

## 9. Qualité des tests : preuve par mutation

Une couverture élevée dit qu'un test exécute une ligne ; elle ne dit pas qu'il échouerait si la ligne était fausse. Chaque test ajouté ou modifié pendant la refonte a donc été confronté à une **mutation** : un bug plausible introduit à la main dans le code de production, qui doit faire échouer le test, puis le code est restauré (sauvegarde du fichier, restauration, `git diff` vide contrôlé après chaque mutation). Aucun outil de mutation testing n'est utilisé.

**Critère de détection.** Une mutation n'est comptée comme détectée que si un test attendu échoue **dans son Act ou son Assert**. Un échec dans l'Arrange, sur `viderLeCache()`, ou par une erreur interne d'Hibernate (`AssertionFailure`) ne prouve pas que l'assertion visée fonctionne : la mutation est alors classée « non prouvée » et remplacée.

| Lot | Tests concernés | Mutations détectées |
|---|---|---|
| PR #35 et #36 | `InscriptionIT` | 14 (13 à la PR #35 ; 1 à la PR #36, sur le gestionnaire du JSON malformé) |
| PR #37 | `ConnexionIT`, `ConsultationProfilIT`, `ModificationProfilIT` | 25 |
| PR #38 | Les neuf autres fichiers d'action et `AuthentificationIT` | 37 |
| PR #39 | `PostServiceTest`, les trois preuves `@EntityGraph`, deux étapes end-to-end (tri côté front, connexion par e-mail côté back) | 6 |
| Passage à jjwt | `AuthentificationIT`, `ConnexionIT`, `InscriptionIT`, `JwtServiceTest` | 10 |
| PR #53 | Tests du journal (`OutputCaptureExtension`) | 9 |
| PR #54 | Corrections de la revue du 22 octobre : normalisation branchée, dates des lectures, corps du 409, `NombreDeRequetesIT`, isolement des tests du journal | 22 conformes, dont une dont le résultat attendu était qu'un test reste vert |
| Chantier des 26 et 27 octobre | Les quatorze classes d'intégration (migration vers `ActionIT` et `ChaineDeSecuriteIT`) ; branchement de l'encodeur | 60 (71 exécutions avec les mutations rejouées) |

Les lignes des PR #53 et #54 reprennent les descriptions et commentaires de ces PR [PR] ; celle du chantier des 26 et 27 octobre est mesurée [mesuré].

Les quatorze mutations d'`InscriptionIT`, faites le 9 octobre 2026 avant que ce critère soit formalisé, ont été rejouées le 15 octobre sous ce critère : toutes sont détectées, treize par l'assertion visée et une (`@MaxUtf8Bytes` retiré) par l'exception de BCrypt levée dans l'Act, avant l'assertion sur le message.

Exemple de mutation non prouvée puis remplacée : dans `ModificationProfilIT`, forcer la branche « nouveau mot de passe » enregistrait un hash nul. Le test échouait, mais au `flush` de `viderLeCache()` (contrainte `NOT NULL`), alors que l'assertion HTTP avait vu un 200 et que l'assertion sur le hash n'était jamais atteinte. La mutation a été remplacée par une autre qui change le hash sans enfreindre la contrainte, et qui fait échouer l'assertion visée. Ce cas illustre aussi la limite de `@Transactional` (§12).

Passage à jjwt (15 octobre 2026) : un jeton refusé reçoit la même réponse qu'un jeton absent, sans `invalid_token`. Le refus est prouvé par deux mutations : un jeton refusé authentifié malgré tout dans `JwtAuthenticationFilter`, qui fait échouer les tests du jeton signé avec une autre clé et du jeton expiré ; une tolérance d'horloge de deux heures dans `JwtService`, qui ne fait échouer que le test du jeton expiré. La vérification de signature elle-même n'a pas de mutation propre : jjwt l'effectue toujours.

Chantier des 26 et 27 octobre 2026 : chaque test dont le code a changé avec la migration vers `ActionIT` et `ChaineDeSecuriteIT` (§3) a été confronté à ses mutations, la suite complète étant rejouée sous chacune. Deux résultats montrent l'effet de la migration : un jeton accepté pour un autre utilisateur dans `JwtAuthenticationFilter` faisait échouer 27 tests au début de la migration et n'en fait plus échouer qu'un (`AuthentificationIT`, jeton valide) ; une requête de connexion limitée à l'e-mail faisait échouer 5 tests et n'en fait plus échouer qu'un (`ConnexionIT`, connexion par nom). Le chantier a aussi comblé les deux derniers trous relevés le 22 octobre : la date des commentaires dans la réponse du détail d'un article, et l'en-tête `Authorization` sans préfixe `Bearer`. Enfin, remplacer l'encodeur de l'application par BCrypt seul ne fait échouer que le test de connexion NFC de `ConnexionIT` : c'est ce test, et non `NfcPasswordEncoderTest`, qui prouve que la normalisation est branchée.

## 10. Anomalie détectée et corrigée : le JSON malformé

Le test de JSON malformé passait sous MockMvc (400). Vérifié avec `curl` sur le serveur réel, le même appel renvoyait **401** sur une route publique : laissée au mécanisme par défaut de Spring, l'erreur était rendue par la page d'erreur du conteneur, et cette nouvelle passe dans la chaîne de filtres était refusée par Spring Security. MockMvc ne simule pas cette page d'erreur. Correction : un gestionnaire dédié à `HttpMessageNotReadableException`, qui renvoie un 400 `ErrorResponse` « Le corps de la requête est illisible ». Le test, écrit avant la correction, échouait avant et passe après ; `curl` renvoie désormais 400 [PR #36, test `InscriptionIT.inscription_corpsJsonMalforme_retourne400`].

## 11. Commandes de mesure

| Mesure | Commande | Résultat |
|---|---|---|
| Back | `cd back && mise exec -- ./mvnw clean verify` ; couverture dans `target/site/jacoco/jacoco.csv` | 17 + 93 = 110 tests, 0 échec ; lignes 98,6 %, branches 97,1 % (27 octobre 2026) |
| Front | `cd front && npx ng test --watch=false --coverage` | 25 fichiers, 271 tests ; 99,15 % / 98,49 % / 99,31 % / 99,45 % (23 octobre 2026) |
| End-to-end | `docker compose up -d` ; back : `cd back && mise exec -- ./mvnw spring-boot:run` (8080) ; front : `cd front && mise exec -- npm start` (4200) ; puis `cd front && mise exec -- npm run e2e:firefox -- --config baseUrl=http://localhost:4200` | 5 specs, 7 tests, 7 verts (27 octobre 2026) |

Prérequis du back pour les tests : Docker démarré (Testcontainers). Les tests d'intégration fournissent leur propre clé de signature (`AbstractContainerIT`) ; `JWT_SECRET`, en Base64 et d'au moins 32 octets une fois décodé, n'est nécessaire que pour lancer l'application, qui ne démarre pas sans lui (le fichier non versionné `application-local.properties` la référence).

## 12. Limites assumées et axes d'amélioration

| Limite | Conséquence | Traitement |
|---|---|---|
| **Commits réels non exercés par les tests d'intégration** (`@Transactional`, annulation après chaque test) | Une erreur qui n'apparaît qu'au commit n'est pas vue par les assertions HTTP (§9) ; par exemple, sans la vérification d'e-mail déjà pris du service, la modification du profil renvoie 200 dans le test (mutation du 27 octobre) | Compensé par les tests end-to-end, qui passent par de vrais commits |
| **MockMvc ne simule pas le conteneur de servlets** | Les pages d'erreur du conteneur ne sont pas exercées (cas du JSON malformé, §10) | Vérification au `curl` et tests end-to-end sur le vrai serveur |
| **Écriture concurrente non testée** | Aucun test d'intégration ne déclenche le 409 de la base ; le gestionnaire est vérifié en unitaire (`GlobalExceptionHandlerTest`, §4) | Filet assuré par les contraintes `UNIQUE` et `NOT NULL` de la base |
| **Tests d'action sans chaîne de sécurité** | Le filtre JWT n'est exercé que par `InscriptionIT`, `ConnexionIT` et `AuthentificationIT` ; un test d'action qui oublie de poser l'utilisateur échoue sur une exception, pas sur un 401 | Le test du jeton valide d'`AuthentificationIT` prouve que le filtre pose le même objet que les tests (mutation, §9) ; Cypress parcourt le chemin réel |
| **Liste des routes protégées tenue à la main** (`AuthentificationIT`, 9 routes) | Une route ajoutée sans être ajoutée à la liste n'aurait pas de test de refus | À compléter à chaque nouvelle route |
| **Base end-to-end non remise à zéro** | Les données s'accumulent d'un lancement à l'autre ; l'isolement repose sur un utilisateur unique par test | Suffisant tant qu'aucun test ne dépend d'une base vide |
| **Tests end-to-end hors du build Maven et de la CI** | Ils se lancent à la main, back et front démarrés | — |
| **Doublons restants côté front** : specs des services HTTP déjà vérifiés par les composants, `app.spec.ts`, deux paires de cas proches (`topic-list`, `profile`) | Maintenance un peu plus lourde | Non supprimés avant la soutenance : à fusionner |
| **Specs front qui simulent des messages que l'API n'envoie pas** (« 72 caractères » dans `login.spec.ts`, `register.spec.ts`, `profile.spec.ts` ; « Topic introuvable » dans `post-create.spec.ts`) | Ces specs vérifient l'affichage d'un message, pas son texte réel | À aligner sur les messages du back |
| **Pas de mutation testing outillé** (PIT) ni de seuil de couverture bloquant | La preuve par mutation n'est pas rejouable automatiquement ; une baisse de couverture ne casse pas le build | Choix assumé ; à reconsidérer si la suite grossit |

Mesures du 27 octobre 2026 : back, `mise exec -- ./mvnw clean verify` depuis `back/` sur le commit `90016b8`, chiffres de couverture tirés de `target/site/jacoco/jacoco.csv` ; mêmes résultats sur `b02daf2`, après le retrait de trois dépendances de test inutilisées ; end-to-end sur `b02daf2`. Front : mesure du 23 octobre 2026 sur `ddc4d80` ; aucun commit ne touche `front/` entre cette mesure et `b02daf2`.
