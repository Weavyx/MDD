# Documentation technique — MDD

Projet MDD (« Monde de Dev »), OpenClassrooms P5 option B. Document établi le 19 septembre 2026 sur le code de `back/` (Spring Boot 4.1.0, Java 21, MySQL 8.4), mis à jour le 24 septembre 2026 sur `main`, commit `9581c70` compris (`@NotBlank` sur le mot de passe d'inscription) : le contrat décrit est celui de `main`. Section 6 (front-end) ajoutée le 25 septembre 2026 sur `main`, commit `860544c` (PR #25) compris.

**Périmètre.** Le back-end et le front-end Angular (`front/`) couvrent le MVP : les huit pages (accueil, inscription, connexion, fil, article, création d'article, thèmes, profil) sont routées dans `front/src/app/app.routes.ts` et consomment les onze endpoints décrits ici (tableau en §6.4). Les sections 1 à 5 documentent l'API et l'environnement, la section 6 l'architecture du front. Aucun template n'a été fourni par la mission pour ce livrable ; la structure suit les indicateurs de la grille (« endpoints, schémas de données, dépendances techniques », « configuration de l'environnement »).

**Reste à produire :** captures d'écran de l'interface, mentions légales et politique de confidentialité. La FAQ utilisateur est dans [`docs/FAQ.md`](../../docs/FAQ.md).

Tout ce qui suit a été vérifié dans le code de `back/src/main/java/com/openclassrooms/mddapi/` (contrôleurs, DTO, entités, `SecurityConfig`, `GlobalExceptionHandler`) et, pour le schéma, dans le DDL du conteneur MySQL de développement (`SHOW CREATE TABLE`, 19 septembre), puis dans la migration Flyway `V1__create_schema.sql` (25 septembre).

## 1. Conventions communes

- Base des chemins : `/api`. Corps et réponses en JSON (`Content-Type: application/json`).
- Authentification : en-tête `Authorization: Bearer <jwt>` sur toute route sauf `POST /api/auth/register` et `POST /api/auth/login`. Le jeton est signé HS256, valable **24 h** (`mdd.jwt.expiration-minutes=1440`), claims `iss`, `iat`, `exp`, `sub` (= id numérique de l'utilisateur). Pas de refresh, pas de logout serveur : la déconnexion consiste à oublier le jeton côté client.
- L'identité de l'appelant vient toujours du jeton ; aucun endpoint ne prend d'id d'utilisateur en paramètre. Les ressources propres à l'utilisateur sont sous `/api/users/me/…`.
- Il n'y a ni rôles ni autorisations différenciées : un jeton valide donne accès à toutes les routes protégées ; **aucun 403 n'est émis**.
- Dates : `createdAt` en ISO-8601 sans fuseau (`LocalDateTime`, ex. `2026-09-19T09:53:09.847`) ; `timestamp` des erreurs en ISO-8601 UTC (`Instant`, suffixe `Z`).
- CORS : origines autorisées lues dans `MDD_CORS_ALLOWED_ORIGINS` (défaut `http://localhost:4200`), méthodes `GET, POST, PUT, DELETE, OPTIONS`, en-têtes `Authorization` et `Content-Type`.

## 2. Endpoints de l'API

### 2.1 Authentification — `AuthController`

| | |
|---|---|
| **`POST /api/auth/register`** | Public |
| Corps | `RegisterRequest` : `username` (obligatoire, 3 à 50 caractères), `email` (obligatoire, format e-mail, ≤ 255), `password` (obligatoire — `@NotBlank` ajouté par le commit `9581c70`, absent au commit `d2cdd31` —, 8 à 72 caractères, au moins une minuscule, une majuscule, un chiffre et un caractère de `\p{Punct}`) |
| Succès | **201** `AuthResponse` `{ "token": "<jwt>" }` — l'inscription connecte directement |
| Erreurs | **400** `ErrorResponse` + `fieldErrors` (validation) · **409** `ErrorResponse` « Cet email est déjà utilisé » ou « Ce nom d'utilisateur est déjà utilisé » (l'email est vérifié en premier ; si les deux sont pris, seule l'erreur d'email est renvoyée) |

| | |
|---|---|
| **`POST /api/auth/login`** | Public |
| Corps | `LoginRequest` : `identifier` (obligatoire, ≤ 255 : **email ou nom d'utilisateur**, un seul champ), `password` (obligatoire, ≤ 255) |
| Succès | **200** `AuthResponse` `{ "token": "<jwt>" }` |
| Erreurs | **400** `ErrorResponse` + `fieldErrors` (champ manquant) · **401 à corps vide** avec en-tête `WWW-Authenticate: Bearer …` si l'identifiant est inconnu **ou** le mot de passe faux — les deux cas sont indistinguables, par choix ; c'est au front de produire le message « identifiants incorrects » |

### 2.2 Topics — `TopicController`

| | |
|---|---|
| **`GET /api/topics`** | JWT |
| Corps | — |
| Succès | **200** `List<TopicResponse>` : `{ "id", "name", "description", "subscribed": true\|false }` — la liste est la même pour tous, seul `subscribed` dépend de l'appelant |
| Erreurs | **401** corps vide (jeton absent, invalide ou expiré). Aucune erreur métier. Aucun endpoint de création ou de modification de topic : ils sont insérés directement en base |

### 2.3 Utilisateur courant — `UserController`

| | |
|---|---|
| **`GET /api/users/me`** | JWT |
| Corps | — |
| Succès | **200** `UserProfileResponse` : `{ "id", "email", "username", "subscriptions": [TopicResponse…] }` — chaque abonnement a `subscribed: true` |
| Erreurs | **401** · **404** « Cet utilisateur n'existe pas » si le compte porté par le jeton a été supprimé |

| | |
|---|---|
| **`PUT /api/users/me`** | JWT |
| Corps | `UpdateProfileRequest` : `username` (obligatoire, 3 à 50), `email` (obligatoire, format e-mail, ≤ 255), `password` (**optionnel** : absent, `null` ou blanc = inchangé ; sinon mêmes règles qu'à l'inscription). Les trois champs sont envoyés à chaque fois : c'est un remplacement, pas un `PATCH` |
| Succès | **200** `UserResponse` `{ "id", "email", "username" }`. Le jeton reste valide après changement d'email ou de nom (il ne porte que l'id) |
| Erreurs | **400** + `fieldErrors` · **401** · **404** compte disparu · **409** « Cet email est déjà utilisé » / « Ce nom d'utilisateur est déjà utilisé » si la valeur appartient à un **autre** compte (renvoyer sa propre valeur ne produit pas de conflit) |

| | |
|---|---|
| **`POST /api/users/me/subscriptions/{topicId}`** | JWT |
| Corps | — (`topicId` numérique dans l'URL) |
| Succès | **200** sans corps |
| Erreurs | **400** « Le paramètre fourni est invalide » si `topicId` n'est pas numérique · **401** · **404** « Ce topic n'existe pas » · **409** « Vous êtes déjà abonné à ce topic » (ou 409 générique « La ressource entre en conflit avec une contrainte existante » si deux abonnements simultanés se croisent) |

| | |
|---|---|
| **`DELETE /api/users/me/subscriptions/{topicId}`** | JWT |
| Corps | — |
| Succès | **204** sans corps, **idempotent** : même réponse si l'abonnement n'existait pas |
| Erreurs | **400** `topicId` non numérique · **401** · **404** « Ce topic n'existe pas » (seul cas d'erreur) |

| | |
|---|---|
| **`GET /api/users/me/feed?sort=desc`** | JWT |
| Paramètre | `sort` : `asc` ou `desc` **exactement** (minuscules), défaut `desc` |
| Succès | **200** `List<PostSummaryResponse>` : `{ "id", "title", "excerpt", "createdAt", "topicName", "author" }` — articles de tous les topics suivis, tous auteurs confondus, triés par `createdAt` ; `excerpt` = contenu tronqué à 200 caractères suivis de « … » seulement s'il dépasse. **Non paginé.** Liste vide sans abonnement |
| Erreurs | **400** « Le paramètre fourni est invalide » (sans `fieldErrors`) pour toute autre valeur de `sort` · **401** |

### 2.4 Articles et commentaires — `PostController`

| | |
|---|---|
| **`POST /api/posts`** | JWT |
| Corps | `CreatePostRequest` : `topicId` (obligatoire, numérique), `title` (obligatoire, ≤ 255), `content` (obligatoire, sans limite de longueur — `LONGTEXT`). Auteur et date ne sont pas envoyés : ils viennent du jeton et de la base |
| Succès | **201** sans corps, en-tête `Location` = URL absolue de l'article, terminée par `/api/posts/{id}` |
| Erreurs | **400** + `fieldErrors` · **401** · **404** « Ce topic n'existe pas » |

| | |
|---|---|
| **`GET /api/posts/{id}`** | JWT |
| Corps | — |
| Succès | **200** `PostDetailResponse` : `{ "id", "title", "content", "createdAt", "topicName", "author", "comments": [ { "id", "content", "createdAt", "author" } … ] }` — commentaires du plus ancien au plus récent ; accessible sans condition d'abonnement au topic |
| Erreurs | **400** `id` non numérique · **401** · **404** « Cet article n'existe pas » |

| | |
|---|---|
| **`POST /api/posts/{id}/comments`** | JWT |
| Corps | `CreateCommentRequest` : `content` (obligatoire, ≤ 1000) |
| Succès | **201** sans corps ni `Location` : les commentaires n'ont pas de route propre, ils se relisent via `GET /api/posts/{id}` |
| Erreurs | **400** + `fieldErrors` · **401** · **404** « Cet article n'existe pas » |

Pas de modification ni de suppression d'article ou de commentaire, pas de `GET /api/posts` global, pas de `GET /api/auth/me`, pas de `/logout` : hors périmètre du MVP.

## 3. Formats d'erreur

Trois formes coexistent ; le front doit gérer les trois.

**1. Validation d'un corps de requête** (`MethodArgumentNotValidException`, handler `GlobalExceptionHandler.handleValidation`) — **400** :

```json
{
  "timestamp": "2026-09-19T07:53:09.847Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Requête invalide",
  "fieldErrors": { "email": "L'adresse e-mail doit être valide", "password": "Le mot de passe doit contenir entre 8 et 72 caractères" }
}
```

`fieldErrors` est indexé par nom de champ du DTO ; la valeur est le message de l'annotation violée, jamais la valeur saisie. **Une seule entrée par champ** : si deux contraintes échouent sur le même champ, une seule est renvoyée.

**2. Erreur métier ou paramètre d'URL** — `ErrorResponse` avec `fieldErrors: null` (le champ est présent, à `null`) :

| Statut | `message` | Origine |
|---|---|---|
| 400 | « Le paramètre fourni est invalide » | segment d'URL non numérique, ou `sort` hors `asc\|desc` |
| 404 | « Cet utilisateur n'existe pas » / « Ce topic n'existe pas » / « Cet article n'existe pas » | `UserNotFoundException`, `TopicNotFoundException`, `PostNotFoundException` |
| 409 | « Cet email est déjà utilisé » / « Ce nom d'utilisateur est déjà utilisé » / « Vous êtes déjà abonné à ce topic » | `EmailAlreadyUsedException`, `UsernameAlreadyUsedException`, `AlreadySubscribedException` |
| 409 | « La ressource entre en conflit avec une contrainte existante » | violation d'une contrainte en base (`DataIntegrityViolationException`), message fixe sans détail SQL |

**3. Hors format `ErrorResponse`** (comportement Spring par défaut, volontairement non redéfini) :

- **401** — jeton absent, invalide ou expiré sur une route protégée, et identifiants faux au login : **corps vide**, en-tête `WWW-Authenticate` commençant par `Bearer` (figé par `AuthControllerIT.login_identifiantsInvalides_retourne401`). Le front doit dériver son message du seul statut.
- **400 sur JSON malformé** (`HttpMessageNotReadableException`) : pas de handler applicatif ; corps vide sous MockMvc, vraisemblablement le JSON standard de `BasicErrorController` en conteneur réel — forme non figée par un test.
- **500** — aucun handler générique ; réponse Spring Boot par défaut, sans trace ni message applicatif. Aucun cas connu ne le produit.

## 4. Schémas de données

Cinq entités JPA, cinq tables, créées par la migration Flyway `V1__create_schema.sql` (`back/src/main/resources/db/migration/`) ; Hibernate ne fait que valider le schéma au démarrage (`ddl-auto=validate`). Toutes les clés primaires sont `bigint AUTO_INCREMENT`. Colonnes `varchar(255)` sauf indication.

| Table / entité | Colonnes | Contraintes |
|---|---|---|
| `users` / `User` | `id`, `username`, `email`, `password_hash` | `username` UNIQUE, `email` UNIQUE, tout NOT NULL. Seule entité mutable (mise à jour du profil) |
| `topics` / `Topic` | `id`, `name`, `description` (`varchar(500)`) | `name` UNIQUE, tout NOT NULL. Aucune colonne de date. Pas d'association vers les abonnements ni les articles |
| `subscriptions` / `Subscription` | `id`, `user_id`, `topic_id` | FK vers `users` et `topics`, NOT NULL ; **UNIQUE (`user_id`, `topic_id`)** nommé `UniqueUserAndTopic` |
| `posts` / `Post` | `id`, `title`, `content` (`longtext`), `created_at` (`datetime(6)`), `user_id`, `topic_id` | FK vers `users` et `topics`, NOT NULL ; `created_at` posé par Hibernate (`@CreationTimestamp`), non modifiable |
| `comments` / `Comment` | `id`, `content` (`varchar(1000)`), `created_at`, `user_id`, `post_id` | FK vers `users` et `posts`, NOT NULL ; `created_at` idem |

Relations : toutes **unidirectionnelles `@ManyToOne` LAZY** depuis l'entité fille (`Subscription → User, Topic` ; `Post → User, Topic` ; `Comment → User, Post`). Aucun `@OneToMany`, aucun `cascade` JPA, aucun `orphanRemoval`, aucune colonne `updated_at`, aucun `@Version`.

**Suppression** : les six clés étrangères sont générées **sans clause `ON DELETE`** (vérifié par `SHOW CREATE TABLE`), donc en `RESTRICT` (défaut InnoDB) : supprimer un utilisateur, un topic ou un article référencé est refusé par MySQL. Aucun endpoint de suppression n'existe ; une suppression manuelle en base doit retirer d'abord les lignes dépendantes. Côté application, une insertion qui référence un utilisateur disparu (`getReferenceById`) remonte en 409 générique.

Chargement : les lectures composites utilisent `@EntityGraph` (`Post` avec `user` et `topic`, `Comment` avec `user`, `Subscription` avec `topic`) pour éviter le N+1 ; `open-in-view=false`, donc aucun chargement paresseux hors transaction.

## 5. Dépendances techniques et configuration

### 5.1 Dépendances (`back/pom.xml`)

| Dépendance | Rôle |
|---|---|
| `spring-boot-starter-web` (Boot 4.1.0, Java 21) | API REST, Tomcat embarqué |
| `spring-boot-starter-data-jpa` + `mysql-connector-j` (runtime) | Persistance JPA/Hibernate sur MySQL |
| `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | Chaîne de filtres, validation du JWT (`NimbusJwtDecoder`), émission (`NimbusJwtEncoder`) — aucune bibliothèque JWT tierce |
| `spring-boot-starter-validation` | Bean Validation sur les DTO d'entrée |
| `spring-boot-starter-flyway` + `flyway-mysql` | Migrations du schéma et des thèmes de référence au démarrage |
| `lombok` (provided) | Getters/setters/constructeurs des DTO et entités |
| Test : `spring-boot-starter-test`, `-webmvc-test`, `-data-jpa-test`, `-security-test`, `spring-security-test`, Testcontainers 1.21.4 (`mysql`, `junit-jupiter`) | Voir `RAPPORT_DE_TESTS.md` |
| Plugins : `spring-boot-maven-plugin`, `maven-failsafe-plugin`, `jacoco-maven-plugin` 0.8.15 | Exécutable, tests `*IT`, couverture |

Absents, par choix ou par périmètre : springdoc-openapi, MapStruct, limiteur de débit, analyseur statique (voir `REVUE_TECHNIQUE.md` §3).

### 5.2 Variables d'environnement

Aucun secret n'est dans le dépôt. Les valeurs vivent dans un fichier `.env` à la racine (gitignoré ; clés attendues dans `.env.example`) et sont consommées par deux mécanismes distincts :

| Variable | Lue par | Usage |
|---|---|---|
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_PORT` | `docker-compose.yml` (nativement) et `application.properties` / `application-local.properties` (comme variables d'environnement du processus) | Conteneur `mdd-mysql` et datasource de l'application (`jdbc:mysql://localhost:${MYSQL_PORT}/${MYSQL_DATABASE}`) |
| `JWT_SECRET` | `application-local.properties` (`mdd.jwt.secret=${JWT_SECRET}`) | Clé HMAC-SHA256, **en Base64, ≥ 32 octets décodés** (ex. `openssl rand -base64 32`). Non contrôlée au démarrage |
| `MDD_CORS_ALLOWED_ORIGINS` (optionnelle) | `application.properties` | Origines CORS séparées par des virgules ; défaut `http://localhost:4200` |

`back/src/main/resources/application-local.properties` est gitignoré et **doit être créé à la main** sur un clone frais, avec deux lignes :

```properties
spring.datasource.password=${MYSQL_PASSWORD}
mdd.jwt.secret=${JWT_SECRET}
```

Le profil `local` est activé en dur par `application.properties` (`spring.profiles.active=local`). Il n'existe pas de profil de production ; `spring.jpa.show-sql=true` s'applique partout (dette consignée dans `REVUE_TECHNIQUE.md`, axe c). Depuis le 25 septembre 2026, le schéma est géré par Flyway et seulement validé par Hibernate (`ddl-auto=validate`).

**Maven ne lit pas `.env`** : les variables doivent être exportées dans le shell avant `spring-boot:run` et avant `verify`, à la main, par mise (le `mise.toml` à la racine charge `.env` dans le shell après un `mise trust`) ou par le plugin EnvFile d'IntelliJ.

### 5.3 Lancer l'application

```bash
docker compose up -d          # à la racine : MySQL 8.4 dans mdd-mysql, port ${MYSQL_PORT}
cd back
./mvnw spring-boot:run        # avec les variables .env exportées ; API sur http://localhost:8080
```

Le schéma est créé au démarrage par les migrations Flyway de `back/src/main/resources/db/migration/`, qui insèrent aussi les huit thèmes de référence (`V2__insert_reference_topics.sql`) ; Hibernate se contente de le valider.

### 5.4 Lancer les tests

```bash
./mvnw test      # 45 tests unitaires (*Test), aucun prérequis
./mvnw verify    # + 82 tests d'intégration (*IT) : Docker démarré (Testcontainers mysql:8.4) et JWT_SECRET exporté
```

`verify` produit le rapport JaCoCo dans `back/target/site/jacoco/index.html`. Sans `JWT_SECRET`, `MddApiApplicationIT` échoue au chargement du contexte (`Could not resolve placeholder 'JWT_SECRET'`) ; sans Docker, tous les `*IT` adossés à la base échouent au démarrage du conteneur. Détail de la stratégie et des chiffres dans `RAPPORT_DE_TESTS.md`.

## 6. Front-end

Application Angular 21.2 (`front/`), composants standalone, sans zone.js (aucune dépendance `zone.js` dans `front/package.json`), Angular Material et CDK 21.2 sur le thème de `front/src/styles.scss` (`mat.$violet-palette`). Formulaires en Reactive Forms typés ; état porté par des services et des signaux, sans NgRx. Tout ce qui suit a été vérifié dans `front/src/`.

### 6.1 Arborescence

```text
front/src/app/
├── app.ts, app.config.ts, app.routes.ts   racine (<app-shell />), providers, routes
├── core/
│   ├── auth/          AuthService, authGuard, guestGuard
│   ├── http/          authInterceptor, toApiError, ErrorResponse (miroir du DTO back)
│   ├── layout/shell/  Shell : barre du haut, navigation, menu latéral, <router-outlet>
│   └── notification/  NotificationService (MatSnackBar)
├── shared/
│   └── validators/    passwordValidator (même règle que le back)
└── features/
    ├── auth/          home, login, register, AuthApiService
    ├── posts/         feed, post-create, post-detail, PostService
    ├── topics/        topic-list, topic-card, TopicService
    └── profile/       profile, UserService
```

`core/` contient ce qui sert à toute l'application, `shared/` le code réutilisable sans état, `features/` un dossier par domaine fonctionnel (composants de page, service HTTP et interfaces miroirs des DTO du back).

### 6.2 Routes et gardes

Toutes les pages sont chargées à la demande (`loadComponent`) ; le titre de l'onglet est porté par la route (`front/src/app/app.routes.ts`).

| Chemin | Page | Garde |
|---|---|---|
| `/` | Accueil (`Home`) | `guestGuard` |
| `/login` | Connexion (`Login`) | `guestGuard` |
| `/register` | Inscription (`Register`) | `guestGuard` |
| `/feed` | Fil d'actualité (`Feed`) | `authGuard` |
| `/posts/new` | Création d'article (`PostCreate`) | `authGuard` |
| `/posts/:id` | Article et commentaires (`PostDetail`) | `authGuard` |
| `/topics` | Thèmes (`TopicList`) | `authGuard` |
| `/profile` | Profil et abonnements (`Profile`) | `authGuard` |
| `**` | redirection vers `/` | — |

`/posts/new` est déclarée avant `/posts/:id` pour que `new` ne soit pas lu comme un identifiant. Les deux gardes sont fonctionnelles (`CanActivateFn`) : `authGuard` renvoie vers `/login` un visiteur sans jeton valide ; `guestGuard` renvoie vers `/feed` un utilisateur déjà connecté.

### 6.3 Authentification

- **Stockage.** Le jeton renvoyé par `POST /api/auth/login` ou `POST /api/auth/register` est rangé dans `localStorage` sous la clé `mdd.token` et dans un signal de `AuthService` (`core/auth/auth.service.ts`). Si `localStorage` est indisponible (navigation privée, quota), la session fonctionne en mémoire seulement.
- **Validité.** Le front ne vérifie pas la signature (c'est le rôle de l'API) : il décode la charge utile pour lire `exp`. Un jeton illisible, sans `exp` numérique ou expiré compte comme absent. `isAuthenticated` est un signal calculé ; il suit les changements de jeton, pas l'écoulement du temps.
- **Expiration.** Les gardes appellent `checkSession()` à chaque navigation : l'horloge est relue et un jeton expiré depuis le chargement de la page est effacé. Un jeton qui expire pendant qu'une page est ouverte est détecté au premier appel d'API, par le 401 de l'intercepteur.
- **Intercepteur** (`core/http/auth.interceptor.ts`, fonctionnel, enregistré par `provideHttpClient(withInterceptors(...))`). Il ajoute `Authorization: Bearer <jeton>` aux appels `/api/**` sauf `/api/auth/**`, uniquement si le jeton est encore valide. Un 401 sur un de ces appels déconnecte l'utilisateur et le renvoie vers `/login` ; l'erreur est quand même transmise à l'appelant. Un 401 sur `/api/auth/**` (identifiants faux) passe sans traitement.
- **Déconnexion.** `logout()` efface le jeton et revient à `/`. Aucun appel serveur : l'API n'a pas de logout (§1).

### 6.4 Endpoints et services Angular

Tous les services appellent des URL relatives et renvoient des `Observable` ; les composants s'y abonnent ou les convertissent avec `toSignal`.

| Endpoint | Service et méthode | Utilisé par |
|---|---|---|
| `POST /api/auth/register` | `AuthApiService.register` | `Register` |
| `POST /api/auth/login` | `AuthApiService.login` | `Login` |
| `GET /api/topics` | `TopicService.getTopics` | `TopicList`, `PostCreate` (liste des thèmes) |
| `POST /api/users/me/subscriptions/{topicId}` | `TopicService.subscribe` | `TopicList` |
| `DELETE /api/users/me/subscriptions/{topicId}` | `TopicService.unsubscribe` | `Profile` |
| `GET /api/users/me` | `UserService.getProfile` | `Profile` |
| `PUT /api/users/me` | `UserService.updateProfile` | `Profile` (mot de passe vide envoyé à `null`) |
| `GET /api/users/me/feed?sort=` | `PostService.getFeed` | `Feed` |
| `POST /api/posts` | `PostService.createPost` | `PostCreate` |
| `GET /api/posts/{id}` | `PostService.getPost` | `PostDetail` |
| `POST /api/posts/{id}/comments` | `PostService.addComment` | `PostDetail` (relit l'article après envoi) |

### 6.5 Gestion des erreurs

- `toApiError` (`core/http/api-error.ts`) extrait `message` et `fieldErrors` d'un corps `ErrorResponse` ; tout autre corps (401 vide, erreur réseau, JSON par défaut de Spring) donne `message: null`, et l'écran choisit alors un message de repli.
- **Erreurs de champ.** Chaque entrée de `fieldErrors` est posée comme erreur `server` sur le contrôle du même nom et s'affiche sous le champ (`mat-error`) jusqu'à la prochaine saisie.
- **Cas particuliers.** Le 401 du login (corps vide) devient « Identifiants incorrects » sous le formulaire. Un 409 à l'inscription (« Cet email est déjà utilisé », « Ce nom d'utilisateur est déjà utilisé ») s'affiche au-dessus du bouton ; sur le profil, il passe par une notification. Un 404 sur un article affiche la page « Article introuvable ».
- **Autres erreurs.** `NotificationService.show` ouvre un `MatSnackBar` (bouton « Fermer », 5 s) avec le message de l'API ou un message de repli propre à l'écran.
- **Rendu.** Les contenus d'articles et de commentaires sont affichés par interpolation, donc échappés : aucun `innerHTML` ni `bypassSecurityTrust*` dans `front/src/`.

### 6.6 Proxy de développement

`ng serve` (`npm start`) utilise `front/src/proxy.conf.json` (déclaré dans `front/angular.json`, cible `serve`) : les appels `/api/**` sont relayés vers `http://localhost:8080`. Comme les services appellent des URL relatives, le navigateur ne voit qu'une seule origine et aucun réglage CORS n'est nécessaire en local. En production, le front et l'API doivent de même être servis sous la même origine (voir le `README.md` racine, « Déploiement »).

### 6.7 Responsive

`Shell` observe le point de rupture `Breakpoints.Handset` du CDK (`BreakpointObserver`). Sur téléphone, les liens (« Se déconnecter », « Articles », « Thèmes », profil) passent dans un `MatSidenav` ouvert par un bouton menu ; sur écran large, ils sont dans la barre du haut. La page d'accueil n'a pas de barre. Les feuilles de style des pages ajustent leur mise en page sous 600 px de large (`@media (max-width: 599.98px)`, même seuil que `Handset` en portrait).

### 6.8 Tests

Tests unitaires et de composants avec Vitest et jsdom, lancés par le builder `@angular/build:unit-test` (`front/angular.json`, cible `test`). Chaque fichier testé a son `*.spec.ts` à côté de lui.

```bash
cd front
npm test                                 # mode surveillance
npx ng test --watch=false                # une seule exécution
npx ng test --watch=false --coverage     # + rapport dans front/coverage/front/index.html
```

Les tests end-to-end (Cypress) font l'objet d'une autre pull request.
