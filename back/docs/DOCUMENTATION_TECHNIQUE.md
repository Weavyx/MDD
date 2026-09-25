# Documentation technique — MDD (backend)

Projet MDD (« Monde de Dev »), OpenClassrooms P5 option B. Document établi le 19 septembre 2026 sur le code de `back/` (Spring Boot 4.1.0, Java 21, MySQL 8.4), mis à jour le 24 septembre 2026 sur `main`, commit `9581c70` compris (`@NotBlank` sur le mot de passe d'inscription) : le contrat décrit est celui de `main`.

**Périmètre.** Le back-end est complet pour le MVP. Le front-end Angular (`front/`) n'est pas commencé (`app.routes.ts` vide, aucun composant) : ce document est donc une documentation d'API et d'environnement, destinée au développeur qui écrira le front. Aucun template n'a été fourni par la mission pour ce livrable ; la structure suit les indicateurs de la grille (« endpoints, schémas de données, dépendances techniques », « configuration de l'environnement »).

**Reste à produire, bloqué par l'absence de front :** captures d'écran de l'interface, analyse des besoins front-end (composants, services, gardes, intercepteurs), FAQ utilisateur (connexion, publication, abonnement, profil), mentions légales et politique de confidentialité.

Tout ce qui suit a été vérifié dans le code de `back/src/main/java/com/openclassrooms/mddapi/` (contrôleurs, DTO, entités, `SecurityConfig`, `GlobalExceptionHandler`) et, pour le schéma, dans le DDL généré par Hibernate dans le conteneur MySQL de développement (`SHOW CREATE TABLE`).

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

Cinq entités JPA, cinq tables (`ddl-auto=update`, nommage snake_case par Hibernate). Toutes les clés primaires sont `bigint AUTO_INCREMENT`. Colonnes `varchar(255)` sauf indication.

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
| `lombok` (provided) | Getters/setters/constructeurs des DTO et entités |
| Test : `spring-boot-starter-test`, `-webmvc-test`, `-data-jpa-test`, `-security-test`, `spring-security-test`, Testcontainers 1.21.4 (`mysql`, `junit-jupiter`) | Voir `RAPPORT_DE_TESTS.md` |
| Plugins : `spring-boot-maven-plugin`, `maven-failsafe-plugin`, `jacoco-maven-plugin` 0.8.15 | Exécutable, tests `*IT`, couverture |

Absents, par choix ou par périmètre : Flyway/Liquibase, springdoc-openapi, MapStruct, limiteur de débit, analyseur statique (voir `REVUE_TECHNIQUE.md` §3).

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

Le profil `local` est activé en dur par `application.properties` (`spring.profiles.active=local`). Il n'existe pas de profil de production ; `spring.jpa.hibernate.ddl-auto=update` et `spring.jpa.show-sql=true` s'appliquent partout (dette consignée dans `REVUE_TECHNIQUE.md`, axe c).

**Maven ne lit pas `.env`** : les variables doivent être exportées dans le shell avant `spring-boot:run` et avant `verify`, à la main, par mise (le `mise.toml` à la racine charge `.env` dans le shell après un `mise trust`) ou par le plugin EnvFile d'IntelliJ.

### 5.3 Lancer l'application

```bash
docker compose up -d          # à la racine : MySQL 8.4 dans mdd-mysql, port ${MYSQL_PORT}
cd back
./mvnw spring-boot:run        # avec les variables .env exportées ; API sur http://localhost:8080
```

Le schéma est créé ou mis à jour par Hibernate au démarrage. Les topics n'ayant pas d'endpoint de création, insérer au moins une ligne dans `topics` (`name`, `description`) pour pouvoir publier.

### 5.4 Lancer les tests

```bash
./mvnw test      # 45 tests unitaires (*Test), aucun prérequis
./mvnw verify    # + 82 tests d'intégration (*IT) : Docker démarré (Testcontainers mysql:8.4) et JWT_SECRET exporté
```

`verify` produit le rapport JaCoCo dans `back/target/site/jacoco/index.html`. Sans `JWT_SECRET`, `MddApiApplicationIT` échoue au chargement du contexte (`Could not resolve placeholder 'JWT_SECRET'`) ; sans Docker, tous les `*IT` adossés à la base échouent au démarrage du conteneur. Détail de la stratégie et des chiffres dans `RAPPORT_DE_TESTS.md`.
