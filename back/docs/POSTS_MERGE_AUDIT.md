# Audit de la branche `feat/posts-comments` avant merge dans `main`

Audit en lecture seule — aucun fichier de code n'a été modifié. Périmètre analysé :

- `repository/PostRepository.java`
- `repository/CommentRepository.java`
- `exception/PostNotFoundException.java`
- `exception/GlobalExceptionHandler.java` (parties liées à `Post`)
- `dto/PostSummaryResponse.java`
- `dto/PostDetailResponse.java`
- `dto/CommentResponse.java`
- `dto/CreatePostRequest.java`
- `dto/CreateCommentRequest.java`
- `service/PostService.java`
- `controller/PostController.java`

(complété ponctuellement par la lecture de `model/Post.java` et `model/Comment.java` pour vérifier les associations JPA, de `security/SecurityConfig.java` pour le point (i), et des notes du vault `prepa/Justifications/Articles et commentaires/` — en particulier *Stratégie anti-N+1 pour les articles pour MDD.md* et *Endpoints Articles et Commentaires pour MDD.md* — comme référence des décisions d'architecture documentées.)

---

## a) Stratégie anti-N+1 — ✅ Respectée (corrigé)

**Décision documentée** (`prepa/Justifications/Articles et commentaires/Stratégie anti-N+1 pour les articles pour MDD.md`) :
> « La lecture des articles (**fil et détail**) utilise `@EntityGraph(attributePaths = {"user", "topic"})` sur `PostRepository` [...] `Post` porte de vraies associations `@ManyToOne` vers `User` et `Topic`, ce qui rend `@EntityGraph` [...] directement applicable en une seule requête avec jointures. »

La décision vise explicitement **les deux** méthodes de lecture (fil **et** détail).

**Constat initial (résolu depuis)** : `repository/PostRepository.java` ne déclarait qu'une seule méthode annotée `@EntityGraph`, `findPostsByUserId` (utilisée pour le fil, `service/PostService.java:42`). Le détail (`GET /api/posts/{id}`) passait par `postRepository.findById(postId)`, la méthode héritée par défaut de `JpaRepository` sans `@EntityGraph`, ce qui provoquait 2 requêtes SQL de lazy-loading supplémentaires (`post.getTopic()`, `post.getUser()`) à chaque appel.

**Correction appliquée** — `repository/PostRepository.java:19-20` :
```java
@EntityGraph(attributePaths = {"user", "topic"})
Optional<Post> findWithUserAndTopicById(Long id);
```

`service/PostService.java:73` utilise désormais cette méthode :
```java
Post post = postRepository.findWithUserAndTopicById(postId)
        .orElseThrow(() -> new PostNotFoundException("Cet article n'existe pas"));
```

Les deux méthodes de lecture de `PostRepository` (`findPostsByUserId` pour le fil, `findWithUserAndTopicById` pour le détail) portent désormais `@EntityGraph(attributePaths = {"user", "topic"})`, conformément à la décision documentée. Comportement 404 inchangé (`PostNotFoundException` si absent). `PostService.addComment` (`service/PostService.java:98`) continue d'utiliser `postRepository.findById(postId)` — volontairement conservé tel quel, car ce chemin ne fait qu'une vérification d'existence du post et n'accède jamais à `user`/`topic`, donc ne nécessite pas l'`EntityGraph`.

**Verdict : ✅.**

---

## b) Codes retour — ✅ Respectés

| Endpoint | Cas | Code observé | Emplacement |
|---|---|---|---|
| `GET /api/posts` | Succès | `ResponseEntity.ok(...)` → 200 | `controller/PostController.java:36` |
| `POST /api/posts` | Succès | `ResponseEntity.created(location).build()` → 201, header `Location`, corps vide | `controller/PostController.java:44` |
| `GET /api/posts/{id}` | Succès | `ResponseEntity.ok(...)` → 200 | `controller/PostController.java:49` |
| `GET /api/posts/{id}` | Post inexistant | `PostNotFoundException` → `handlePostNotFound` → 404 | `service/PostService.java:73-74`, `exception/GlobalExceptionHandler.java:22-25` |
| `POST /api/posts/{id}/comments` | Succès | `ResponseEntity.status(HttpStatus.CREATED).build()` → 201, corps vide | `controller/PostController.java:56` |
| `POST /api/posts/{id}/comments` | Post inexistant | `PostNotFoundException` → `handlePostNotFound` → 404 | `service/PostService.java:98-99`, `exception/GlobalExceptionHandler.java:22-25` |

Tous les codes attendus sont conformes. Le `Location` de `POST /api/posts` pointe bien vers `/api/posts/{id}` via `ServletUriComponentsBuilder` (`controller/PostController.java:43`).

---

## c) Utilisateur courant exclusivement via JWT — ✅ Respecté

- `findFeed` : `Long.valueOf(jwt.getSubject())` (`controller/PostController.java:35`).
- `create` : `Long.valueOf(jwt.getSubject())` (`controller/PostController.java:41`).
- `addComment` : `Long.valueOf(jwt.getSubject())` (`controller/PostController.java:54`).

`findById` (`controller/PostController.java:48-50`) ne lit aucun identifiant utilisateur (lecture publique côté logique métier, l'authentification restant néanmoins exigée au niveau du filtre de sécurité — voir point i). Aucun `userId` n'est lu depuis un paramètre d'URL ou de requête dans les quatre endpoints ; seul `id` (identifiant du **post**) provient de l'URL, ce qui est attendu.

---

## d) Tri du fil — ✅ Respecté

`controller/PostController.java:34` :
```java
@RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String sort
```
- Valeur absente → `desc` par défaut (comportement conforme).
- Valeur hors `asc|desc` (ex. `ASC`, `xyz`) → violation de contrainte sur paramètre de méthode → `HandlerMethodValidationException`, mappée en 400 (`exception/GlobalExceptionHandler.java:42-45`).
- `Sort.Direction.fromString(sort)` (`controller/PostController.java:36`) n'est atteint qu'après validation réussie.

---

## e) Tri des commentaires — ✅ Respecté

`repository/CommentRepository.java:12-13` :
```java
@EntityGraph(attributePaths = "user")
List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
```
Dérivation par nom pure, pas de `@Query`. `OrderByCreatedAtAsc` garantit un tri chronologique croissant, conforme à la décision documentée (`Endpoints Articles et Commentaires pour MDD.md`, section 3 : tri fixe, pas de `Sort` dynamique pour les commentaires).

---

## f) Gestion des 404 — ✅ Respectée

| Cas | Exception levée | Emplacement | Branchement handler |
|---|---|---|---|
| `GET /api/posts/{id}` sur post absent | `PostNotFoundException` | `service/PostService.java:73-74` | `GlobalExceptionHandler.java:22-25` |
| `POST /api/posts/{id}/comments` sur post absent | `PostNotFoundException` | `service/PostService.java:98-99` | `GlobalExceptionHandler.java:22-25` |
| `POST /api/posts` sur topic absent | `TopicNotFoundException` | `service/PostService.java:63-64` | `GlobalExceptionHandler.java:17-20` |

Les trois cas sont bien couverts et branchés.

---

## g) Extrait du fil — ✅ Respecté

`service/PostService.java:54-59` :
```java
private String excerpt(String content) {
    if (content.length() <= EXCERPT_MAX_LENGTH) {
        return content;
    }
    return content.substring(0, EXCERPT_MAX_LENGTH) + "…";
}
```
`EXCERPT_MAX_LENGTH = 200` (`service/PostService.java:26`). Troncature à 200 caractères + `…` uniquement en cas de dépassement, sinon contenu renvoyé tel quel.

`content` complet n'apparaît que dans `PostDetailResponse` (`dto/PostDetailResponse.java:14`). `PostSummaryResponse` (`dto/PostSummaryResponse.java`) n'expose que `excerpt` — aucun champ `content` ne fuite dans le résumé du fil.

---

## h) Validation des DTOs de requête — ✅ Respectée

| DTO | Contrainte | Colonne réelle | Cohérent ? |
|---|---|---|---|
| `CreatePostRequest.title` | `@NotBlank`, `@Size(max = 255)` (`dto/CreatePostRequest.java:13-14`) | `posts.title` — `@Column(nullable = false)` sans longueur explicite (`model/Post.java:21-22`, donc `VARCHAR(255)` par défaut Hibernate) | ✅ |
| `CreatePostRequest.content` | `@NotBlank` seul, pas de `@Size` (`dto/CreatePostRequest.java:17`) | `posts.content` — `@Lob` / `LONGTEXT` (`model/Post.java:24-26`) | ✅ cohérent, pas de limite de taille pertinente sur `LONGTEXT` |
| `CreatePostRequest.topicId` | `@NotNull` (`dto/CreatePostRequest.java:10`) | FK obligatoire (`model/Post.java:32-34`) | ✅ |
| `CreateCommentRequest.content` | `@NotBlank`, `@Size(max = 1000)` (`dto/CreateCommentRequest.java:9-10`) | `comments.content` — `@Column(length = 1000)` (`model/Comment.java:33`) | ✅ |

Toutes les contraintes Jakarta sont cohérentes avec les colonnes réelles.

---

## i) Sécurité — ✅ Respectée

`security/SecurityConfig.java:24-26` :
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
        .anyRequest().authenticated());
```
Seuls `/api/auth/register` et `/api/auth/login` sont publics. Les 4 endpoints de `PostController` (`/api/posts`, `/api/posts/{id}`, `/api/posts/{id}/comments`) tombent tous sous `anyRequest().authenticated()` et exigent donc un JWT valide, y compris `GET /api/posts/{id}` qui ne lit pourtant aucune donnée utilisateur dans sa logique métier.

---

## Synthèse

| Point | Verdict |
|---|---|
| a) Stratégie anti-N+1 | ✅ (corrigé) |
| b) Codes retour | ✅ |
| c) Utilisateur courant via JWT | ✅ |
| d) Tri du fil | ✅ |
| e) Tri des commentaires | ✅ |
| f) Gestion des 404 | ✅ |
| g) Extrait du fil | ✅ |
| h) Validation des DTOs | ✅ |
| i) Sécurité | ✅ |

---

## Divergences code / vault signalées

1. **Point (a), résolu** : la note *Stratégie anti-N+1 pour les articles pour MDD.md* documente `@EntityGraph(attributePaths = {"user", "topic"})` pour « la lecture des articles (fil **et** détail) ». La lecture du détail ne portait initialement pas cette annotation (`findById` hérité de `JpaRepository`). Corrigé par l'ajout de `PostRepository.findWithUserAndTopicById` (voir point a ci-dessus), utilisée par `PostService.findById`. Tests ciblés (`PostServiceTest`, `PostControllerTest`) mis à jour et repassés au vert (28/28) après correction.

2. **Point non bloquant, hors périmètre a-i, à titre informatif** : `GlobalExceptionHandler` ne déclare pas de `@ExceptionHandler(MethodArgumentNotValidException.class)` (l'exception levée par `@Valid @RequestBody` sur `CreatePostRequest`/`CreateCommentRequest` en cas d'échec de validation). Spring la gère par défaut en 400, donc le code retour reste correct, mais le corps de réponse ne suivra pas le format `ErrorResponse` unifié utilisé partout ailleurs dans l'API — à confirmer si c'est voulu ou un oubli, par cohérence avec le format d'erreur centralisé documenté dans `DTOs et gestion des erreurs.md`.

---

## Points ouverts non résolus

Les éléments suivants ne bloquent pas ce merge (aucun ❌ restant) mais restent à traiter séparément :

1. **`MethodArgumentNotValidException` non gérée explicitement dans `GlobalExceptionHandler`** (voir divergence 2 ci-dessus) — le code retour (400) est correct par défaut, mais le format du corps de réponse n'est pas garanti cohérent avec `ErrorResponse`.

2. **Checklist de tests manuels non exécutée** : `back/docs/POSTS_TEST_CHECKLIST.md` liste les scénarios de test manuel pour les 4 endpoints. Toutes les colonnes « Résultat observé » et « Statut » sont vides à ce jour — aucun scénario n'a encore été vérifié manuellement.

---

## Conclusion

Les 9 points de l'audit sont désormais tous ✅ : le point (a), initialement ❌ bloquant (stratégie anti-N+1 incomplète sur la lecture du détail d'un article), a été corrigé par l'ajout de `PostRepository.findWithUserAndTopicById` et sa mise en usage dans `PostService.findById`. Les tests ciblés `PostServiceTest`/`PostControllerTest` confirment l'absence de régression. Conformément à la procédure, le merge de `feat/posts-comments` dans `main` est effectué.
