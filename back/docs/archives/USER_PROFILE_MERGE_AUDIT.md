# Audit de la branche `feat/user-profile` avant merge dans `main`

Audit en lecture seule — aucun fichier de code n'a été modifié. Branche auditée au commit `be36bec` (`feat: add user profile read and update endpoints`). Périmètre analysé :

- `controller/UserController.java`
- `service/UserService.java`
- `dto/UserResponse.java`
- `dto/UserProfileResponse.java`
- `dto/UpdateProfileRequest.java`
- `exception/UserNotFoundException.java`
- `exception/EmailAlreadyUsedException.java`
- `exception/UsernameAlreadyUsedException.java`
- `exception/GlobalExceptionHandler.java` (les 3 handlers ajoutés)
- `repository/UserRepository.java` (nouvelles méthodes `existsByEmailAndIdNot` / `existsByUsernameAndIdNot`)
- `repository/SubscriptionRepository.java` (`@EntityGraph` ajouté sur `findByUserId`)
- `service/AuthService.java` (modifications)

(complété ponctuellement par la lecture de `model/User.java` et `model/Subscription.java` pour les associations JPA, de `dto/ErrorResponse.java` pour le point (g), de `security/SecurityConfig.java` pour le point (i), de `dto/RegisterRequest.java` pour la cohérence des règles de mot de passe, et des notes du vault `prepa/Justifications/` — en particulier *Entités et données/DTOs et gestion des erreurs.md* (section 3, « DTOs — Profil utilisateur »), *authentification/Aucun identifiant utilisateur dans les URLs pour MDD.md* et *prepa/Etapes d'implémentations.md* — comme référence des décisions d'architecture documentées.)

Contrairement à la procédure `feat/posts-comments`, **aucun merge local n'est effectué** : le merge se fera exclusivement par Pull Request GitHub squashée, ouverte manuellement.

---

## a) `GET /api/users/me` — utilisateur courant via JWT uniquement — ✅ Respecté

`controller/UserController.java:23-27` :
```java
@GetMapping("/me")
public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
    Long userId = Long.valueOf(jwt.getSubject());
    return ResponseEntity.ok(userService.getProfile(userId));
}
```

- Code retour : `ResponseEntity.ok(...)` → **200** (`controller/UserController.java:26`).
- Identité : `Long.valueOf(jwt.getSubject())` (`controller/UserController.java:25`), conforme à la décision *Sub du JWT égal à l'id utilisateur pour MDD*.
- La route est `/api/users/me` : aucun `@PathVariable`, aucun `@RequestParam`. La classe ne déclare que `@RequestMapping("/api/users")` (`controller/UserController.java:14`) et deux mappings sur `/me` (`:23`, `:29`). Aucun identifiant utilisateur n'est lu depuis l'URL ou un paramètre, conformément à *Aucun identifiant utilisateur dans les URLs pour MDD* (« pas de `/api/users/{id}/...` »).

---

## b) `PUT /api/users/me` — mêmes garanties JWT, corps validé — ✅ Respecté

`controller/UserController.java:29-33` :
```java
@PutMapping("/me")
public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    Long userId = Long.valueOf(jwt.getSubject());
    return ResponseEntity.ok(userService.updateProfile(userId, request));
}
```

- Code retour : `ResponseEntity.ok(...)` → **200** (`controller/UserController.java:32`).
- Identité : `Long.valueOf(jwt.getSubject())` (`controller/UserController.java:31`), même mécanisme que le `GET`. Le corps `UpdateProfileRequest` ne porte aucun champ `id` (`dto/UpdateProfileRequest.java:10-31` : `username`, `email`, `password` uniquement) — impossible de cibler un autre utilisateur via le corps.
- Validation : `@Valid @RequestBody` (`controller/UserController.java:30`). Contraintes de `dto/UpdateProfileRequest.java` :

| Champ | Contraintes | Lignes | Cohérent avec `RegisterRequest` ? |
|---|---|---|---|
| `username` | `@NotBlank`, `@Size(min = 3, max = 50)` | `:11-13` | ✅ identique (`dto/RegisterRequest.java:11-13`) |
| `email` | `@NotBlank`, `@Size(max = 255)`, `@Email` | `:15-18` | ✅ identique (`dto/RegisterRequest.java:15-18`) |
| `password` | `@Size(min = 8, max = 72)`, `@Pattern(\p{Punct}…)`, **sans** `@NotBlank` | `:25-30` | ✅ mêmes règles que `dto/RegisterRequest.java:35-40`, moins l'obligation de présence (voulu, cf. point d) |

Les tests `UserControllerTest` (`updateProfile_motDePasseNonConformeAuPattern_retourne400`, `updateProfile_motDePasseTropCourt_retourne400`, `updateProfile_emailInvalide_retourne400`, `updateProfile_usernameTropCourt_retourne400`, `updateProfile_emailAbsent_retourne400`) confirment que le `@Valid` déclenche bien un 400 sur chaque contrainte.

---

## c) Unicité email/username à la mise à jour — clause `AndIdNot` — ✅ Respecté

`repository/UserRepository.java:18-19` :
```java
boolean existsByEmailAndIdNot(String email, Long id);
boolean existsByUsernameAndIdNot(String username, Long id);
```
Dérivation par nom Spring Data : le suffixe `IdNot` se traduit en `... AND u.id <> :id`. L'utilisateur courant est donc exclu par construction du test d'existence dans les deux méthodes.

`service/UserService.java:53-58` :
```java
if (!requestEmail.equals(user.getEmail()) && userRepository.existsByEmailAndIdNot(requestEmail, userId)) {
    throw new EmailAlreadyUsedException("Cet email est déjà utilisé");
}
if (!requestUsername.equals(user.getUsername()) && userRepository.existsByUsernameAndIdNot(requestUsername, userId)) {
    throw new UsernameAlreadyUsedException("Ce nom d'utilisateur est déjà utilisé");
}
```

Deux barrières indépendantes contre le faux 409 sur sa propre valeur :

1. **Court-circuit applicatif** : si la valeur envoyée est strictement égale à la valeur actuelle de l'utilisateur, le repository n'est même pas interrogé (`!requestEmail.equals(user.getEmail())` est `false`). Test `UserServiceTest.updateProfile_emailEtUsernameInchanges_aucuneVerificationUniciteEtSaveAppele` (vérifie `verify(userRepository, never()).existsByEmailAndIdNot(...)`).
2. **Exclusion SQL** : si la valeur diffère seulement par la casse (ex. `Alice@Mail.com` vs `alice@mail.com` stocké, `equals` retourne `false`), la requête `existsByEmailAndIdNot` est bien exécutée mais exclut la ligne de l'utilisateur courant via `id <> :id` — avec la collation MySQL par défaut (insensible à la casse), la seule ligne qui matcherait est celle de l'utilisateur lui-même, donc exclue, donc `false`, donc pas de 409. Test `UserServiceTest.updateProfile_emailEtUsernamePrisParUtilisateurLuiMeme_pasDeConflitGraceAIdNot`.

Aucun chemin de code ne permet à un utilisateur d'être rejeté en conservant sa propre valeur. L'`userId` passé aux deux méthodes est celui issu du JWT (`controller/UserController.java:31`), pas du corps.

Note : l'unicité reste par ailleurs garantie en base (`model/User.java:19-23`, `unique = true` sur `username` et `email`) ; en cas de course entre deux requêtes concurrentes, la `DataIntegrityViolationException` résultante est déjà mappée en 409 par `exception/GlobalExceptionHandler.java:47-50`.

---

## d) Mot de passe — `null` → inchangé ; non-null → hashé, jamais en clair, jamais renvoyé — ✅ Respecté

`service/UserService.java:62-64` :
```java
if (requestPassword != null && !requestPassword.isBlank()) {
    user.setPasswordHash(passwordEncoder.encode(requestPassword));
}
```

- **`null` → aucune modification** : la branche est sautée, `user.passwordHash` (`model/User.java:25-26`) n'est jamais touché, et `userRepository.save(user)` (`:66`) persiste l'entité avec son hash d'origine. Test `UserServiceTest.updateProfile_emailEtUsernameSeuls_motDePasseInchangeEtSaveAppele`.
- **Chaîne blanche → également ignorée** (`!requestPassword.isBlank()`). C'est une garde défensive : en pratique une chaîne vide ou blanche est déjà rejetée en 400 par `@Size(min = 8)` (`dto/UpdateProfileRequest.java:25`) avant d'atteindre le service. Conforme à la décision documentée (*DTOs et gestion des erreurs.md*, section 3 : « Le service applicatif ignore ce champ s'il est vide ou nul plutôt que d'écraser le hash existant »). Test `UserServiceTest.updateProfile_motDePasseBlanc_hashInchange`.
- **Valeur non-null → hashée** : `passwordEncoder.encode(requestPassword)` (`:63`) — même `PasswordEncoder` (BCrypt) que l'inscription (`service/AuthService.java:47`). La valeur brute n'est jamais affectée à l'entité. Test `UserServiceTest.updateProfile_avecNouveauMotDePasse_hashRemplace`.
- **Jamais renvoyé** :
  - `dto/UserResponse.java:9-11` : `id`, `email`, `username` uniquement.
  - `dto/UserProfileResponse.java:11-14` : `id`, `email`, `username`, `subscriptions` uniquement.
  - `grep -rn "passwordHash" dto/ controller/` : aucune occurrence.
- Les contraintes Jakarta `@Size` et `@Pattern` ignorent une valeur `null` par spécification (Bean Validation), donc l'absence du champ dans le JSON ne déclenche pas de 400 — c'est bien ce qui rend le champ optionnel. Test `UserControllerTest.updateProfile_emailEtUsernameSansMotDePasse_retourne200EtAppelleLeServiceAvecPasswordNull`.

---

## e) Abonnements dans le `GET` — `@EntityGraph` sur `SubscriptionRepository.findByUserId` — ✅ Respecté

`repository/SubscriptionRepository.java:18-19` :
```java
@EntityGraph(attributePaths = "topic")
List<Subscription> findByUserId(Long userId);
```

- `Subscription.topic` est une association `@ManyToOne(fetch = FetchType.LAZY)` (`model/Subscription.java:30-32`) ; sans `@EntityGraph`, chaque `subscription.getTopic()` déclencherait une requête supplémentaire.
- `service/UserService.java:36-41` itère sur le résultat et appelle `subscription.getTopic()` puis `topic.getId()/getName()/getDescription()` pour chaque abonnement : avec l'`@EntityGraph`, ces accès lisent des entités déjà chargées par jointure dans la requête initiale.
- `getProfile` est `@Transactional(readOnly = true)` (`service/UserService.java:32`), ce qui garantit une session ouverte pendant tout le mapping.
- Bilan SQL par appel : 1 requête `findById` (`:71`) + 1 requête `findByUserId` avec `JOIN` sur `topics` = **2 requêtes fixes**, indépendamment du nombre d'abonnements. Pas de N+1.

Cohérent avec la décision *Lecture des abonnements sans N+1 pour MDD* déjà appliquée sur `feat/topics-subscriptions`.

---

## f) `AuthService.register` — `RuntimeException` remplacées, `login` non affecté — ✅ Respecté

Diff `main...feat/user-profile` sur `service/AuthService.java` (6 lignes : 2 imports + 2 `throw`) :

| Avant | Après | Ligne |
|---|---|---|
| `throw new RuntimeException("Cet email est déjà utilisé")` | `throw new EmailAlreadyUsedException("Cet email est déjà utilisé")` | `service/AuthService.java:38` |
| `throw new RuntimeException("Ce nom d'utilisateur est déjà utilisé")` | `throw new UsernameAlreadyUsedException("Ce nom d'utilisateur est déjà utilisé")` | `service/AuthService.java:41` |

Imports ajoutés `service/AuthService.java:7-8`. Les deux `RuntimeException` brutes (qui tombaient en 500 faute de handler) sont désormais typées et mappées en 409 (voir point g) — c'est une correction de comportement bienvenue pour `POST /api/auth/register`.

`AuthService.login` (`service/AuthService.java:55-63`) : aucune ligne modifiée dans le diff. Le reste de `register` (`:44-52`) est inchangé. Tests `AuthServiceTest.register_emailDejaUtilise_emailAlreadyUsedExceptionEtSaveJamaisAppele` et `register_usernameDejaUtilise_usernameAlreadyUsedExceptionEtSaveJamaisAppele`.

---

## g) Codes retour des 3 nouveaux handlers — même format `ErrorResponse` — ✅ Respecté

`exception/GlobalExceptionHandler.java` :

| Exception | Handler | Code | Lignes |
|---|---|---|---|
| `UserNotFoundException` | `handleUserNotFound` | `HttpStatus.NOT_FOUND` → 404 | `:27-30` |
| `EmailAlreadyUsedException` | `handleEmailAlreadyUsed` | `HttpStatus.CONFLICT` → 409 | `:37-40` |
| `UsernameAlreadyUsedException` | `handleUsernameAlreadyUsed` | `HttpStatus.CONFLICT` → 409 | `:42-45` |

Les trois passent par le même `buildResponse(HttpStatus, String)` privé (`exception/GlobalExceptionHandler.java:62-65`) que les handlers préexistants (`handleTopicNotFound :17-20`, `handlePostNotFound :22-25`, `handleAlreadySubscribed :32-35`, etc.), qui construit un `ErrorResponse(Instant timestamp, int status, String error, String message)` (`dto/ErrorResponse.java:10-15`). Format strictement identique. Le message métier (`ex.getMessage()`) est propagé au client comme pour les autres exceptions métier.

Les trois classes d'exception (`exception/UserNotFoundException.java`, `EmailAlreadyUsedException.java`, `UsernameAlreadyUsedException.java`, 7 lignes chacune) suivent le même patron `extends RuntimeException` + constructeur `(String message)` que `TopicNotFoundException`/`PostNotFoundException`/`AlreadySubscribedException`. Le choix d'exceptions spécifiques au domaine plutôt qu'une `DuplicateResourceException` générique est explicitement validé dans le vault (*DTOs et gestion des erreurs.md*, section 9, note de cohérence).

Tests `UserControllerTest.getProfile_userNotFoundException_retourne404`, `updateProfile_emailAlreadyUsedException_retourne409`, `updateProfile_usernameAlreadyUsedException_retourne409`.

---

## h) Séparation DTO/entité — ✅ Respectée

- `GET /api/users/me` renvoie `ResponseEntity<UserProfileResponse>` (`controller/UserController.java:24`), construit dans `service/UserService.java:43` à partir de champs individuels de `User` (`getId()`, `getEmail()`, `getUsername()`) et d'une liste de `TopicResponse`.
- `PUT /api/users/me` renvoie `ResponseEntity<UserResponse>` (`controller/UserController.java:30`), construit dans `service/UserService.java:67` à partir de `savedUser.getId()/getEmail()/getUsername()`.
- `grep -rn "model.User" controller/ dto/` : aucune occurrence — `UserController` n'importe même pas l'entité (`controller/UserController.java:3-11`). `User` reste confiné à `service/` et `repository/`.
- Aucun DTO de sortie ne porte `passwordHash` (voir point d).

Conforme à *Séparation stricte (DTO - Entité JPA) pour MDD* et à la section 1 de *DTOs et gestion des erreurs.md*.

---

## i) Sécurité — `/api/users/**` authentifié — ✅ Respectée

`security/SecurityConfig.java:24-26` (fichier non modifié par la branche) :
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
        .anyRequest().authenticated());
```
Seuls `/api/auth/register` et `/api/auth/login` sont publics. `GET /api/users/me` et `PUT /api/users/me` tombent sous `anyRequest().authenticated()` et exigent un JWT valide via `oauth2ResourceServer().jwt()` (`:22-23`). Session `STATELESS` (`:20-21`).

Tests `UserControllerTest.getProfile_sansJwt_retourne401` et `updateProfile_sansJwt_retourne401`.

---

## Synthèse

| Point | Verdict |
|---|---|
| a) `GET /api/users/me` — 200, JWT uniquement | ✅ |
| b) `PUT /api/users/me` — 200, JWT, `@Valid` | ✅ |
| c) Unicité `AndIdNot`, pas de faux 409 | ✅ |
| d) Mot de passe : null → inchangé, sinon hashé, jamais exposé | ✅ |
| e) `@EntityGraph` sur `findByUserId`, pas de N+1 | ✅ |
| f) `AuthService.register` typé, `login` intact | ✅ |
| g) 3 handlers, 409/409/404, format `ErrorResponse` | ✅ |
| h) Séparation DTO/entité | ✅ |
| i) `/api/users/**` authentifié | ✅ |

**Aucun ❌.** Tests de la branche exécutés le 2026-09-11 : `UserControllerTest` 14/14, `UserServiceTest` 11/11, `AuthServiceTest` 3/3 — **28/28 verts**.

---

## Divergences code / vault signalées (non corrigées, à traiter séparément)

1. **`prepa/Etapes d'implémentations.md` — section « Points ouverts avant soutenance » obsolète.** La note indique encore « Profil utilisateur […] : fonctionnalité backend non commencée, choix d'endpoint (extension de `GET /api/auth/me` vs nouvel endpoint `GET`/`PUT /api/me` dédié) à trancher avant de coder ». Le code a tranché pour une **troisième** option, `GET`/`PUT /api/users/me`, non listée dans la note. Aucune note de justification `prepa/Justifications/` ne documente ce choix d'endpoint (ni pourquoi `/api/users/me` plutôt que `/api/me` ou l'extension de `/api/auth/me`). La section « Points ouverts » et l'historique des PR de cette note sont à mettre à jour, et une note de décision est à écrire — hors périmètre de cet audit (lecture seule).

2. **Coexistence de `GET /api/auth/me` et `GET /api/users/me`** (`controller/AuthController.java:37-39` vs `controller/UserController.java:23-27`). Les deux endpoints renvoient l'identité de l'utilisateur courant, avec des DTOs différents (`AuthenticatedUserResponse` : `id`, `username` ; `UserProfileResponse` : `id`, `email`, `username`, `subscriptions`). Le second est un sur-ensemble fonctionnel du premier. Tranché (décision produit, voir « Correctif post-audit ») : les deux sont conservés avec des rôles distincts — `/api/auth/me` vérification légère du token au chargement du front, `/api/users/me` profil complet.

3. **`MethodArgumentNotValidException` toujours non gérée dans `GlobalExceptionHandler`** — point déjà signalé dans `POSTS_MERGE_AUDIT.md` (divergence 2) et listé dans les « Points ouverts avant soutenance » du vault. Il s'applique désormais aussi à `PUT /api/users/me` : les 400 de validation (`UpdateProfileRequest`) sont bien produits (code correct, confirmé par les 5 tests 400 de `UserControllerTest` et par les traces `DefaultHandlerExceptionResolver : Resolved [MethodArgumentNotValidException…]` observées à l'exécution), mais le corps de réponse ne suit pas le format `ErrorResponse` unifié documenté dans *DTOs et gestion des erreurs.md* section 8.3 (`handleValidation` → 400 avec `fieldErrors`). Non bloquant (code retour correct), inchangé par rapport aux branches précédentes.

4. **`GET /api/auth/me` et type du principal — bug confirmé, corrigé (voir section « Correctif post-audit » ci-dessous).** `controller/AuthController.java:38` déclarait `@AuthenticationPrincipal UserDetailsImpl userDetails`, alors que `SecurityConfig` utilise `oauth2ResourceServer().jwt()` sans `JwtAuthenticationConverter` personnalisé, ce qui place un `Jwt` (et non un `UserDetailsImpl`) comme principal. Le point préexistait à la branche (présent sur `main`) et n'était pas dans le périmètre initial des 9 points ; il a été traité dans un commit dédié sur cette même branche.

---

## Points ouverts non résolus

Les éléments suivants ne bloquent pas ce merge (aucun ❌) mais restent à traiter séparément :

1. **Vault à mettre à jour** (divergence 1) : section « Points ouverts » de *Etapes d'implémentations.md*, ajout d'une entrée « PR `feat/user-profile` », et note de justification du choix d'endpoint `/api/users/me`.
2. **`MethodArgumentNotValidException` non gérée explicitement** (divergence 3) — hérité des branches précédentes.
3. **Coexistence `/api/auth/me` / `/api/users/me`** (divergence 2) — tranchée : les deux sont conservés avec des rôles distincts (voir « Correctif post-audit », décision produit).
4. **Checklist de tests manuels non exécutée** : `back/docs/archives/USER_PROFILE_TEST_CHECKLIST.md` liste les scénarios de test manuel pour les 2 endpoints. Toutes les colonnes « Résultat observé » et « Statut » sont vides à ce jour.

---

## Correctif post-audit — `GET /api/auth/me` répondait 500 (NPE)

### Bug

`controller/AuthController.java:38` (état au commit `a8c3ac9`, identique à `main`) :
```java
public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal UserDetailsImpl userDetails) {
    return ResponseEntity.ok(new AuthenticatedUserResponse(userDetails.getId(), userDetails.getUsername()));
}
```

`security/SecurityConfig.java:22-23` configure `oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))` sans `JwtAuthenticationConverter` personnalisé : l'`Authentication` placée dans le `SecurityContext` est une `JwtAuthenticationToken` dont le principal est un `org.springframework.security.oauth2.jwt.Jwt`. `@AuthenticationPrincipal` avec un type de paramètre incompatible (`UserDetailsImpl`) injecte `null` (comportement par défaut, `errorOnInvalidType = false`), et `userDetails.getId()` lève une `NullPointerException` → **500 sur toute requête**, quel que soit le JWT. `UserDetailsImpl` n'est produit que par `UserDetailsServiceImpl` au moment du `login` via l'`AuthenticationManager` (`service/AuthService.java:57-61`), jamais lors de la validation d'un JWT.

**Preuve par test, avant correction** — un `AuthControllerTest` minimal (`@WebMvcTest(AuthController.class)`, `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`, `GET /api/auth/me` avec `jwt().jwt(jwt -> jwt.subject("1"))`, attendu 200) exécuté contre le code non corrigé :
```
Tests run: 1, Failures: 0, Errors: 1
jakarta.servlet.ServletException: Request processing failed: java.lang.NullPointerException:
  Cannot invoke "com.openclassrooms.mddapi.security.services.UserDetailsImpl.getId()" because "userDetails" is null
```
Le bug était réel, pas une fausse lecture d'audit.

### Décision produit

`/api/auth/me` et `/api/users/me` sont **tous deux conservés**, avec des rôles distincts :
- `GET /api/auth/me` — endpoint léger « suis-je toujours authentifié ? », utilisé par le front au chargement de l'application pour valider le token. Renvoie `AuthenticatedUserResponse` (`id`, `username`).
- `GET /api/users/me` — endpoint complet du profil (`email`, `username`, `subscriptions`). Renvoie `UserProfileResponse`.

Aucun des deux n'est fusionné ni supprimé.

### Correction

`controller/AuthController.java:37-41` — alignement sur le pattern déjà en place dans `UserController`/`TopicController`/`PostController` :
```java
@GetMapping("/me")
public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
    Long userId = Long.valueOf(jwt.getSubject());
    return ResponseEntity.ok(authService.getCurrentUser(userId));
}
```
L'import de `UserDetailsImpl` est retiré du contrôleur. `UserDetailsImpl` reste utilisé par `UserDetailsServiceImpl` et `AuthService.login` (cast du principal de l'`AuthenticationManager`) — il n'est pas supprimé.

`service/AuthService.java:66-70` — le JWT ne porte que l'id (`sub`), le `username` est rechargé depuis la base :
```java
public AuthenticatedUserResponse getCurrentUser(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Cet utilisateur n'existe pas"));
    return new AuthenticatedUserResponse(user.getId(), user.getUsername());
}
```
`UserNotFoundException` (créée dans ce chantier) est réutilisée : un JWT valide dont le `sub` ne correspond plus à aucun utilisateur (compte supprimé après émission du token) répond désormais 404 via `handleUserNotFound` (`exception/GlobalExceptionHandler.java:27-30`), au lieu de 500.

### Tests ajoutés — `controller/AuthControllerTest.java` (nouveau, 4 tests)

| Test | Vérifie |
|---|---|
| `me_avecJwtValide_retourne200EtIdPlusUsername` | 200, `id` et `username` corrects, absence de `email`/`passwordHash` dans le corps |
| `me_utiliseLeSubDuJwtCommeIdUtilisateur` | `authService.getCurrentUser` est appelé avec `Long.valueOf(sub)` (sub `"42"` → `42L`) |
| `me_userNotFoundException_retourne404` | 404 au format `ErrorResponse` (`status`, `message`) |
| `me_sansJwt_retourne401` | 401 sans `Authorization`, service jamais appelé |

Périmètre volontairement limité à `/api/auth/me` (`/register` et `/login` hors périmètre de ce fix).

### Résultat après correction

Suite complète (`./mvnw test`, variables `.env` exportées) : **79/79 verts** — `AuthControllerTest` 4/4, `UserControllerTest` 14/14, `PostControllerTest` 18/18, `TopicControllerTest` 10/10, `AuthServiceTest` 3/3, `UserServiceTest` 11/11, `PostServiceTest` 10/10, `TopicServiceTest` 8/8, `MddApiApplicationTests` 1/1. Aucune régression.

Les 9 verdicts de l'audit initial (a–i) sont inchangés : ce correctif ne touche ni `UserController`, ni `UserService`, ni les DTOs du profil.

---

## Conclusion

Les 9 points de l'audit sont ✅ dès la première lecture, sans correction nécessaire sur le périmètre du profil. Le bug préexistant sur `GET /api/auth/me` (500 par NPE, hors périmètre initial) a été corrigé dans un commit dédié sur la même branche, avec 4 tests d'intégration ; la suite complète passe à 79/79. Les divergences relevées concernent exclusivement la documentation du vault (obsolète sur le sujet du profil) et des points préexistants hors périmètre ; aucune ne remet en cause le code de la branche.

Conformément à la procédure spécifique à cette branche : **aucun merge local n'est effectué**. Les deux fichiers de documentation (`USER_PROFILE_MERGE_AUDIT.md`, `USER_PROFILE_TEST_CHECKLIST.md`) sont commités sur `feat/user-profile`, la branche est poussée sur `origin`, et le merge dans `main` se fera par Pull Request GitHub squashée, ouverte manuellement.
