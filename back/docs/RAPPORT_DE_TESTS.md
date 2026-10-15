# Rapport de couverture et de tests — MDD

Projet MDD (« Monde de Dev »), OpenClassrooms P5 option B. Rapport réécrit le 14 octobre 2026 après la refonte de la stratégie de tests (PR #35 à #39), sur `main` après la PR #39. Il remplace la version du 25 septembre 2026, qui décrivait une stratégie en quatre strates (unitaire, `@WebMvcTest`, `@DataJpaTest`, contexte complet) ; le journal de cette ancienne stratégie reste dans `TESTS_REVIEW.md`, daté du 18 septembre 2026.

## 1. Périmètre et origine des chiffres

Ce rapport couvre le **backend** (`back/`, Spring Boot 4.1.0, Java 21), le **front-end** Angular 21.2 (`front/`, tests unitaires et de composants) et les **tests end-to-end** Cypress qui traversent les deux.

Le texte de mission impose un seuil de couverture de 70 % **[texte de mission, source hors dépôt]**. Aucun template de rapport de tests n'est fourni ; la structure ci-dessous est libre.

Origine des chiffres : **[mesuré]** = exécuté, commande au §11 ; **[code]** = lu dans un fichier, cité ; **[test]** = comportement verrouillé par un test nommé ; **[PR]** = repris de la description d'une pull request fusionnée.

## 2. Stratégie : un observable, un seul niveau de test

La stratégie a été simplifiée le 8 octobre 2026, avec une règle : **chaque comportement observable est vérifié à un seul niveau**, celui qui peut l'observer le plus directement. Un test qui vérifie la même chose qu'un autre test, à un autre niveau, a été supprimé.

| Niveau | Outil | Vérifie | Ne vérifie pas | Tests [mesuré] |
|---|---|---|---|---|
| **Intégration back** | `@SpringBootTest` + MockMvc + MySQL 8.4 (Testcontainers) | Chaque action de la spécification de bout en bout côté API : contrat HTTP, règles métier, sécurité, état réel en base | Le conteneur de servlets (pages d'erreur) ; les commits réels (transaction annulée après chaque test) ; le rendu | 83 |
| **Unitaire back** | JUnit 5, Mockito, AssertJ | Les règles fines que l'intégration ne peut pas épingler précisément : bornes, normalisation, claims du jeton, longueur minimale de la clé, découpage CORS | Le câblage avec le reste de l'application | 15 |
| **Persistance** | `@DataJpaTest` + MySQL 8.4 | Le chargement anticipé (`@EntityGraph`), invisible depuis l'API | Services et contrôleurs | 3 |
| **Unitaire front** | Vitest + jsdom | Composants, services, gardes, intercepteur, validateurs, avec l'API simulée (`HttpTestingController`) | Le vrai serveur | 269 |
| **End-to-end** | Cypress (Firefox) | Le parcours utilisateur complet dans un vrai navigateur, contre le vrai back et sa base : rendu, routage, gardes, intercepteur, commits réels | Les cas d'erreur fins (laissés aux niveaux inférieurs) | 7 |

Back : **101 tests** = 15 Surefire + 86 Failsafe, 0 échec [mesuré le 15 octobre 2026, après le passage à jjwt]. Avant la refonte : 158 tests (57 + 101), dont 67 `@WebMvcTest` et 23 `@DataJpaTest` [mesuré le 8 octobre 2026] ; un même observable y était souvent vérifié deux ou trois fois (service mocké, contrôleur mocké, contexte complet).

## 3. Tests d'intégration back : un fichier par action de la spécification

Package `back/src/test/java/com/openclassrooms/mddapi/parcours/` [code].

| Action de la spécification | Fichier | Cas [mesuré] |
|---|---|---|
| S'inscrire | `InscriptionIT` | 18 |
| Se connecter (e-mail ou nom d'utilisateur) | `ConnexionIT` | 7 |
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
| Transversal : toute route protégée exige un jeton valide | `AuthentificationIT` | 11 |

La déconnexion n'a pas de route côté API (jeton sans état) : elle est couverte côté front et en end-to-end.

Conventions communes [code] :

- Chaque classe hérite de `ParcoursIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`), elle-même adossée au conteneur MySQL partagé d'`AbstractContainerIT`. Le contexte Spring est complet : vraie chaîne de filtres de sécurité, vrais services, vraie base créée par les migrations Flyway.
- **Arrange par les vraies routes** (`inscrire`, `connecter`, `abonner`, `creerArticle`, `commenter`) : un test prépare ses données comme un utilisateur le ferait, sans écrire directement en base. Seuls les thèmes de référence viennent de la migration `V2__insert_reference_topics.sql`.
- **Un seul Act par test**, structure Arrange–Act–Assert commentée, nommage `action_condition_resultat`.
- **Relecture en base** : quand un test vérifie l'état persisté (hash du mot de passe, abonnement enregistré ou supprimé, auteur et date d'un article), il appelle `viderLeCache()` (`flush` puis `clear` de l'`EntityManager`) **après** les assertions sur la réponse HTTP et **juste avant** la relecture, pour que celle-ci interroge MySQL et non le cache d'Hibernate. Placé plus tôt, une erreur de contrainte au moment du `flush` cassait la transaction partagée et masquait l'assertion visée : observé sous mutation le 8 octobre 2026.

## 4. Tests unitaires back gardés, et pourquoi

| Classe | Tests | Pourquoi au niveau unitaire |
|---|---|---|
| `PostServiceTest` | 2 | Borne exacte de l'extrait du fil : 200 caractères conservés, 201 et plus tronqués à 200 suivis de « … ». Un cas limite précis, coûteux à reproduire par l'API |
| `MaxUtf8BytesValidatorTest` | 5 | Borne de 72 octets UTF-8 sur la forme NFC (BCrypt refuse au-delà) : 72 octets ASCII acceptés, 73 refusés, 36 « é » précomposés (72 octets) acceptés, 37 refusés, 36 « é » en NFD comptés sous leur forme NFC. L'intégration vérifie seulement que la règle est branchée (74 octets → 400) |
| `NfcPasswordEncoderTest` | 2 | Un mot de passe encodé en NFD correspond au même en NFC, et inversement : la normalisation avant BCrypt |
| `JwtServiceTest` | 3 | Aller-retour émission puis lecture (`sub` relu comme id), en-tête `HS256`, claims `iss` et `iat`, expiration égale à la durée configurée, et refus au démarrage d'une clé de moins de 256 bits. Sans Spring ni mock : une clé aléatoire par test |
| `SecurityConfigTest` | 3 | Découpage de la liste des origines CORS (espaces, entrées vides) et méthodes autorisées |

## 5. Tests de persistance : la preuve du chargement anticipé

`PostRepositoryIT`, `CommentRepositoryIT` et `SubscriptionRepositoryIT` gardent une méthode chacun. Ils vérifient, par `Hibernate.isInitialized(...)` **avant** tout accès, que les relations sont chargées par la requête elle-même (`@EntityGraph`). Les tests d'intégration ne peuvent pas l'observer : dans leur transaction ouverte, un chargement paresseux réussit aussi. Ce sont les trois seuls tests `@DataJpaTest` conservés, par application de la même règle : un niveau de test seulement là où les autres ne voient pas.

## 6. Front-end et end-to-end

### 6.1 Tests unitaires et de composants

`npx ng test --watch=false` : **25 fichiers de spec, 269 tests**, tous verts [mesuré le 8 octobre 2026 ; le front n'a pas changé depuis]. Chaque fichier testé a son `*.spec.ts` à côté de lui. Les appels HTTP sont interceptés par `HttpTestingController`, qui vérifie l'URL, la méthode et le corps de chaque requête. Le code de sécurité (service d'authentification, intercepteur, gardes, validateur de mot de passe) est prouvé par mutation manuelle, et chaque spec de feature compte au moins une mutation [PR #30].

### 6.2 End-to-end (Cypress)

Contre le back réel sur `http://localhost:8080` et sa base MySQL : **5 specs, 7 tests, 7 verts** [mesuré le 9 octobre 2026, Firefox] :

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
| Identité tirée du jeton, jamais du corps ni de l'URL | `InscriptionIT` et `ConnexionIT` (`sub` du jeton = id du compte) ; `CreationArticleIT` (auteur et date imposés dans le corps ignorés) |
| Accès refusé sans jeton valide | `AuthentificationIT` : 401 avec `WWW-Authenticate: Bearer` sans jeton sur les 9 routes protégées, avec un jeton signé avec une autre clé, avec un jeton expiré ; le refus du jeton est prouvé par mutation de `JwtAuthenticationFilter` et de `JwtService` (§9) |
| Unicité de l'e-mail et du nom d'utilisateur | `InscriptionIT` et `ModificationProfilIT` (409, message exact ; à l'inscription, aucun second compte créé) |
| Borne de 72 octets et normalisation NFC | `MaxUtf8BytesValidatorTest`, `NfcPasswordEncoderTest` ; branchement vérifié par `InscriptionIT`, `ModificationProfilIT` et `ConnexionIT` |
| Double abonnement refusé | `AbonnementIT.abonnement_dejaAbonne_retourne409` (message applicatif, distinct du 409 générique de la contrainte `UNIQUE`) |
| Désabonnement limité au compte courant, idempotent | `DesabonnementIT` (`nAffectePasLesAutresComptes`, 204 sans abonnement) |
| Fil limité aux thèmes suivis, tri dans les deux sens | `ConsultationFilIT`, `TriFilIT` ; `parcours-complet.cy.ts` |
| Auteur et date définis par le serveur | `CreationArticleIT`, `CommentaireIT` |
| Extrait du fil | `PostServiceTest` |
| Chargement anticipé (N+1 évité) | `PostRepositoryIT`, `CommentRepositoryIT`, `SubscriptionRepositoryIT` |
| Contrat d'erreur `ErrorResponse` | Message exact asserté dans chaque fichier d'action (400, 404, 409) ; JSON malformé dans `InscriptionIT` |
| CORS | `SecurityConfigTest` |
| Gardes, intercepteur, stockage du jeton (front) | Specs `auth.guard`, `guest.guard`, `auth.interceptor`, `auth.service` ; `gardes.cy.ts` |
| Parcours utilisateur complet | `parcours-complet.cy.ts`, `mobile.cy.ts` |

## 8. Couverture

### 8.1 Back-end (mesure du 15 octobre 2026)

`./mvnw clean verify`, JaCoCo, 24 classes analysées [mesuré] :

| Métrique | Couvert / total |
|---|---|
| Lignes | 277 / 282 = **98,2 %** |
| Branches | 30 / 32 = **93,8 %** |

Avant le passage à jjwt, sur `main` après la PR #40 : 264 / 269 lignes, 27 / 28 branches, 24 classes [mesuré le 15 octobre 2026, même commande]. Avant les suppressions de la PR #39, sur `main` à la PR #38 : 266 / 269 lignes, 27 / 28 branches [mesuré le 14 octobre 2026, même commande]. Le seuil de 70 % du texte de mission est dépassé.

Lignes non couvertes, et pourquoi :

- **`MddApiApplication.main`** (2 lignes) : le contexte est démarré par les tests d'intégration via `@SpringBootTest`, jamais par un appel à `main`. Ni exclusion du périmètre, ni `MockedStatic` : un chiffre expliqué vaut mieux qu'un chiffre obtenu en réduisant le périmètre mesuré.
- **`NfcPasswordEncoder`** : `upgradeEncoding` (simple délégation, jamais appelée) et la branche `rawPassword == null` de la normalisation (seule branche manquante du back).
- **`GlobalExceptionHandler`, gestionnaire `DataIntegrityViolationException`** : le 409 générique levé par une contrainte de base. Il n'est atteignable que par deux écritures concurrentes, puisque chaque contrainte est d'abord vérifiée par le service. Il n'était couvert que par un `@WebMvcTest` qui simulait l'exception ; sa réalité a été constatée sous mutation (vérification applicative retirée → 409 générique de la contrainte `UNIQUE`).
- **`UserDetailsImpl.getUsername()`** : imposé par l'interface `UserDetails` de Spring Security, il n'est exécuté par aucun parcours de l'application. Le seul test qui l'appelait vérifiait ce getter isolément ; il a été supprimé plutôt que gardé pour le chiffre.
- **`dto` et `model`** : absents du rapport, sans exclusion configurée : leur bytecode est généré par Lombok (`@lombok.Generated`), que JaCoCo filtre par défaut. **`repository`** : interfaces Spring Data sans bytecode propre.

La mesure doit être faite avec `clean` : sans lui, `target/jacoco.exec` cumule les exécutions précédentes et affiche comme couvertes des lignes qu'aucun test courant n'exécute (constaté le 9 octobre 2026).

### 8.2 Front-end (mesure du 8 octobre 2026)

`npx ng test --watch=false --coverage` (fournisseur v8), 25 fichiers, 269 tests [mesuré] :

| Métrique | Couverture |
|---|---|
| Instructions (*statements*) | **99,15 %** |
| Branches | **98,48 %** |
| Fonctions | **99,31 %** |
| Lignes | **99,45 %** |

Le fichier utilitaire de test `features/auth/testing.ts` est compté dans ce périmètre. Rapport HTML dans `front/coverage/front/index.html`.

## 9. Qualité des tests : preuve par mutation

Une couverture élevée dit qu'un test exécute une ligne ; elle ne dit pas qu'il échouerait si la ligne était fausse. Chaque test ajouté ou modifié pendant la refonte a donc été confronté à une **mutation** : un bug plausible introduit à la main dans le code de production, qui doit faire échouer le test, puis le code est restauré (sauvegarde du fichier, restauration, `git diff` vide contrôlé après chaque mutation). Aucun outil de mutation testing n'est utilisé.

**Critère de détection.** Une mutation n'est comptée comme détectée que si un test attendu échoue **dans son Act ou son Assert**. Un échec dans l'Arrange, sur `viderLeCache()`, ou par une erreur interne d'Hibernate (`AssertionFailure`) ne prouve pas que l'assertion visée fonctionne : la mutation est alors classée « non prouvée » et remplacée.

| Lot | Tests concernés | Mutations détectées |
|---|---|---|
| PR #35 | `InscriptionIT` | 14 |
| PR #37 | `ConnexionIT`, `ConsultationProfilIT`, `ModificationProfilIT` | 25 |
| PR #38 | Les neuf autres fichiers d'action et `AuthentificationIT` | 37 |
| PR #39 | `PostServiceTest`, les trois preuves `@EntityGraph`, deux étapes end-to-end (tri côté front, connexion par e-mail côté back) | 6 |
| Passage à jjwt | `AuthentificationIT`, `ConnexionIT`, `InscriptionIT`, `JwtServiceTest` | 10 |

Exemple de mutation non prouvée puis remplacée : dans `ModificationProfilIT`, forcer la branche « nouveau mot de passe » enregistrait un hash nul. Le test échouait, mais au `flush` de `viderLeCache()` (contrainte `NOT NULL`), alors que l'assertion HTTP avait vu un 200 et que l'assertion sur le hash n'était jamais atteinte. La mutation a été remplacée par une autre qui change le hash sans enfreindre la contrainte, et qui fait échouer l'assertion visée. Ce cas illustre aussi la limite de `@Transactional` (§12).

Passage à jjwt (15 octobre 2026) : un jeton refusé reçoit la même réponse qu'un jeton absent, sans `invalid_token`. Le refus est prouvé par deux mutations : un jeton refusé authentifié malgré tout dans `JwtAuthenticationFilter`, qui fait échouer les tests du jeton signé avec une autre clé et du jeton expiré ; une tolérance d'horloge de deux heures dans `JwtService`, qui ne fait échouer que le test du jeton expiré. La vérification de signature elle-même n'a pas de mutation propre : jjwt l'effectue toujours.

## 10. Anomalie détectée et corrigée : le JSON malformé

Le test de JSON malformé passait sous MockMvc (400). Vérifié avec `curl` sur le serveur réel, le même appel renvoyait **401** sur une route publique : laissée au mécanisme par défaut de Spring, l'erreur était rendue par la page d'erreur du conteneur, et cette nouvelle passe dans la chaîne de filtres était refusée par Spring Security. MockMvc ne simule pas cette page d'erreur. Correction : un gestionnaire dédié à `HttpMessageNotReadableException`, qui renvoie un 400 `ErrorResponse` « Le corps de la requête est illisible ». Le test, écrit avant la correction, échouait avant et passe après ; `curl` renvoie désormais 400 [PR #36, test `InscriptionIT.inscription_corpsJsonMalforme_retourne400`].

## 11. Commandes de mesure

| Mesure | Commande | Résultat |
|---|---|---|
| Back | `cd back && mise exec -- ./mvnw clean verify` ; couverture dans `target/site/jacoco/jacoco.csv` | 15 + 86 = 101 tests, 0 échec ; lignes 98,2 %, branches 93,8 % (15 octobre 2026) |
| Front | `cd front && npx ng test --watch=false --coverage` | 25 fichiers, 269 tests ; 99,15 % / 98,48 % / 99,31 % / 99,45 % (8 octobre 2026) |
| End-to-end | `npx ng serve --port 4201` ; `npm run e2e:firefox -- --config baseUrl=http://localhost:4201` (back démarré sur 8080) | 5 specs, 7 tests, 7 verts (9 octobre 2026) |

Prérequis du back : Docker démarré et `JWT_SECRET` dans l'environnement, en Base64 et d'au moins 32 octets une fois décodé, sinon le contexte ne démarre pas (le fichier non versionné `application-local.properties` la référence).

## 12. Limites assumées et axes d'amélioration

| Limite | Conséquence | Traitement |
|---|---|---|
| **Commits réels non exercés par les tests d'intégration** (`@Transactional`, annulation après chaque test) | Une erreur qui n'apparaît qu'au commit n'est pas vue par les assertions HTTP (§9) | Compensé par les tests end-to-end, qui passent par de vrais commits |
| **MockMvc ne simule pas le conteneur de servlets** | Les pages d'erreur du conteneur ne sont pas exercées (cas du JSON malformé, §10) | Vérification au `curl` et tests end-to-end sur le vrai serveur |
| **Écriture concurrente non testée** | Le gestionnaire `DataIntegrityViolationException` n'est pas couvert (§8.1) | Filet assuré par les contraintes `UNIQUE` et `NOT NULL` de la base |
| **Base end-to-end non remise à zéro** | Les données s'accumulent d'un lancement à l'autre ; l'isolement repose sur un utilisateur unique par test | Suffisant tant qu'aucun test ne dépend d'une base vide |
| **Tests end-to-end hors du build Maven** | Ils se lancent à la main, back et front démarrés | — |
| **Doublons restants côté front** : specs des services HTTP déjà vérifiés par les composants, `app.spec.ts`, deux paires de cas proches (`topic-list`, `profile`) | Maintenance un peu plus lourde | Non supprimés avant la soutenance : à fusionner |
| **Specs front qui simulent des messages que l'API n'envoie pas** (« 72 caractères » dans `login.spec.ts`, `register.spec.ts`, `profile.spec.ts` ; « Topic introuvable » dans `post-create.spec.ts`) | Ces specs vérifient l'affichage d'un message, pas son texte réel | À aligner sur les messages du back |
| **Pas de mutation testing outillé** (PIT) ni de seuil de couverture bloquant | La preuve par mutation n'est pas rejouable automatiquement ; une baisse de couverture ne casse pas le build | Choix assumé ; à reconsidérer si la suite grossit |

Mesure du 14 octobre 2026 : `mise exec -- ./mvnw clean verify` depuis `back/` sur la branche de la PR #39, comparée ligne à ligne à la même mesure sur `main` à la PR #38 ; front et end-to-end : mesures des 8 et 9 octobre 2026, le front n'ayant pas changé depuis.

Mesure du 15 octobre 2026 (back seulement) : `mise exec -- ./mvnw clean verify` depuis `back/` sur la branche `refactor/securite-jjwt`, après le passage à jjwt ; chiffres de couverture tirés de `target/site/jacoco/jacoco.csv` et comparés à la même mesure sur `main` après la PR #40.
