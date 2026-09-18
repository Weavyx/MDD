# Checklist de tests manuels — Profil utilisateur

Préalable commun : deux comptes existants en base, désignés ci-dessous **A** (compte testé, JWT obtenu via `POST /api/auth/login`) et **B** (compte tiers, dont on connaît l'`email` et le `username`). Sauf mention contraire, les requêtes sont envoyées avec le JWT de **A**.

## GET /api/users/me

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 1 | Profil nominal, avec abonnements | `GET /api/users/me` avec JWT de A ; A abonné à au moins deux topics | 200 | `UserProfileResponse` : `id` = id de A, `email`, `username`, `subscriptions` = tableau de `TopicResponse` (`id`, `name`, `description`, `subscribed: true`) contenant exactement les topics suivis par A ; aucun champ `passwordHash`/`password` | | |
| 2 | Profil nominal, sans abonnement | `GET /api/users/me` avec JWT de A ; A n'a aucun abonnement (ou après désabonnement de tous ses topics) | 200 | Même structure, `subscriptions: []` | | |
| 3 | Abonnements reflétés après modification | `POST /api/users/me/subscriptions/{topicId}` puis `GET /api/users/me` | 200 (sur le `GET`) | Le topic nouvellement suivi apparaît dans `subscriptions` avec `subscribed: true` | | |
| 4 | Aucun JWT fourni | `GET /api/users/me` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 5 | JWT invalide/expiré | `GET /api/users/me` avec `Authorization: Bearer <token invalide ou expiré>` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

## PUT /api/users/me — cas nominaux

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 6 | Modification de l'email seul | `PUT /api/users/me` avec JWT de A, `{ "email": "<nouvel email libre>", "username": "<username actuel de A>" }` (pas de champ `password`) | 200 | `UserResponse` : `id` = id de A, `email` = nouvel email, `username` inchangé ; aucun champ mot de passe. Vérification complémentaire : `POST /api/auth/login` avec le **nouvel** email et le mot de passe **inchangé** → 200 | | |
| 7 | Modification du username seul | `PUT /api/users/me` avec JWT de A, `{ "email": "<email actuel de A>", "username": "<nouveau username libre>" }` (pas de champ `password`) | 200 | `UserResponse` avec `username` = nouveau, `email` inchangé. Vérification complémentaire : login avec le **nouveau** username et le mot de passe **inchangé** → 200 | | |
| 8 | Modification du mot de passe seul | `PUT /api/users/me` avec JWT de A, `{ "email": "<email actuel>", "username": "<username actuel>", "password": "NouveauMdp1!" }` | 200 | `UserResponse` avec `email` et `username` inchangés ; le mot de passe n'apparaît **pas** dans le corps. Vérifications complémentaires : login avec l'**ancien** mot de passe → 401 ; login avec `NouveauMdp1!` → 200 ; en base, `users.password_hash` commence par `$2a$`/`$2b$` (BCrypt), jamais la valeur en clair | | |
| 9 | Sans aucun changement (valeurs actuelles renvoyées telles quelles) | `PUT /api/users/me` avec JWT de A, `{ "email": "<email actuel>", "username": "<username actuel>" }` | 200 | `UserResponse` identique au profil actuel ; **pas de 409** ; login avec le mot de passe inchangé → 200 | | |
| 10 | Sa propre valeur avec une casse différente | `PUT /api/users/me` avec JWT de A, `{ "email": "<email actuel en MAJUSCULES>", "username": "<username actuel>" }` | 200 | **Pas de faux 409** (la clause `AndIdNot` exclut A) ; `email` renvoyé = valeur envoyée | | |
| 11 | Modification simultanée email + username + mot de passe | `PUT /api/users/me` avec JWT de A, les trois champs modifiés avec des valeurs libres et un mot de passe conforme | 200 | `UserResponse` avec les nouveaux `email`/`username` ; login avec le nouvel email + nouveau mot de passe → 200 | | |
| 12 | Profil relu après modification | Enchaîner scénario 6 ou 7 puis `GET /api/users/me` | 200 (sur le `GET`) | `email`/`username` reflètent la modification ; `subscriptions` inchangés | | |
| 13 | JWT toujours valide après changement d'email/username | Enchaîner scénario 6 ou 7 puis n'importe quel appel authentifié (`GET /api/topics`) avec le **même** JWT | 200 | Le token reste accepté (le `sub` porte l'id, pas l'email/username) | | |

## PUT /api/users/me — conflits (409)

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 14 | Email déjà pris par un autre utilisateur | `PUT /api/users/me` avec JWT de A, `{ "email": "<email de B>", "username": "<username actuel de A>" }` | 409 | `ErrorResponse` avec `message` = "Cet email est déjà utilisé" ; vérifier via `GET /api/users/me` que l'email de A est **inchangé** | | |
| 15 | Username déjà pris par un autre utilisateur | `PUT /api/users/me` avec JWT de A, `{ "email": "<email actuel de A>", "username": "<username de B>" }` | 409 | `ErrorResponse` avec `message` = "Ce nom d'utilisateur est déjà utilisé" ; username de A inchangé | | |
| 16 | Email pris **et** username pris | `PUT /api/users/me` avec JWT de A, `{ "email": "<email de B>", "username": "<username de B>" }` | 409 | `ErrorResponse` avec `message` = "Cet email est déjà utilisé" (l'email est vérifié en premier) | | |
| 17 | Conflit avec mot de passe fourni : rien n'est modifié | `PUT /api/users/me` avec JWT de A, `{ "email": "<email de B>", "username": "<username actuel>", "password": "AutreMdp1!" }` | 409 | `ErrorResponse` ; vérification complémentaire : login de A avec l'**ancien** mot de passe → 200 (le hash n'a pas été touché malgré la présence de `password` dans la requête) | | |

## PUT /api/users/me — validation (400)

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 18 | Mot de passe non conforme (sans majuscule) | `PUT /api/users/me` avec JWT de A, `"password": "motdepasse1!"` (email/username actuels) | 400 | Corps d'erreur de validation ; login avec l'ancien mot de passe → toujours 200 | | |
| 19 | Mot de passe non conforme (sans chiffre) | `"password": "MotDePasse!"` | 400 | Corps d'erreur de validation | | |
| 20 | Mot de passe non conforme (sans caractère spécial) | `"password": "MotDePasse1"` | 400 | Corps d'erreur de validation | | |
| 21 | Mot de passe trop court (< 8 caractères) | `"password": "Ab1!"` | 400 | Corps d'erreur de validation | | |
| 22 | Mot de passe trop long (> 72 caractères) | `"password"` = 73 caractères ou plus, respectant par ailleurs le pattern | 400 | Corps d'erreur de validation | | |
| 23 | Mot de passe chaîne vide | `"password": ""` | 400 | Corps d'erreur de validation (`@Size(min = 8)`) — la chaîne vide n'est **pas** traitée comme « pas de changement » côté HTTP | | |
| 24 | Email invalide | `"email": "pas-un-email"` (username actuel) | 400 | Corps d'erreur de validation | | |
| 25 | Email absent ou vide | Corps sans `email`, ou `"email": ""` | 400 | Corps d'erreur de validation | | |
| 26 | Username absent ou vide | Corps sans `username`, ou `"username": ""` | 400 | Corps d'erreur de validation | | |
| 27 | Username trop court (< 3 caractères) | `"username": "ab"` | 400 | Corps d'erreur de validation | | |
| 28 | Username trop long (> 50 caractères) | `"username"` de 51 caractères ou plus | 400 | Corps d'erreur de validation | | |
| 29 | Corps JSON malformé | `PUT /api/users/me` avec JWT de A, corps `{ "email": ` (JSON invalide) | 400 | Corps d'erreur de désérialisation (selon config Spring) | | |

## PUT /api/users/me — authentification

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 30 | Aucun JWT fourni | `PUT /api/users/me` sans en-tête `Authorization`, corps valide | 401 | Corps d'erreur d'authentification (selon config Spring Security) ; aucune modification en base | | |
| 31 | JWT invalide/expiré | `PUT /api/users/me` avec `Authorization: Bearer <token invalide ou expiré>`, corps valide | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 32 | Impossible de cibler un autre utilisateur | `PUT /api/users/me` avec JWT de A, corps contenant un champ supplémentaire `"id": <id de B>` avec des valeurs valides | 200 | Le champ `id` est ignoré ; `UserResponse.id` = id de **A** ; le profil de B est inchangé (`GET /api/users/me` avec le JWT de B) | | |

## POST /api/users/me/subscriptions/{topicId}

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 36 | Topic existant, pas encore abonné | `POST /api/users/me/subscriptions/{topicId}` avec JWT valide ; `topicId` existant en base ; aucun abonnement préalable pour ce couple utilisateur/topic | 200 | Corps vide | | |
| 37 | Topic existant, déjà abonné | `POST /api/users/me/subscriptions/{topicId}` avec JWT valide ; abonnement déjà existant pour ce couple utilisateur/topic | 409 | Message d'erreur "Vous êtes déjà abonné à ce topic" | | |
| 38 | Topic inexistant (topicId absent en base) | `POST /api/users/me/subscriptions/{topicId}` avec JWT valide ; `topicId` ne correspondant à aucun topic | 404 | Message d'erreur "Ce topic n'existe pas" | | |
| 39 | Aucun JWT | `POST /api/users/me/subscriptions/{topicId}` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 40 | topicId non numérique dans l'URL (ex: `/api/users/me/subscriptions/abc`) | `POST /api/users/me/subscriptions/abc` avec JWT valide | à vérifier - non garanti par le code actuel | à déterminer lors du test | | |

## DELETE /api/users/me/subscriptions/{topicId}

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 41 | Abonnement existant | `DELETE /api/users/me/subscriptions/{topicId}` avec JWT valide ; abonnement existant pour ce couple utilisateur/topic | 204 | Corps vide | | |
| 42 | Aucun abonnement existant, topic existant (idempotence) | `DELETE /api/users/me/subscriptions/{topicId}` avec JWT valide ; `topicId` existant en base mais aucun abonnement pour ce couple utilisateur/topic | 204 | Corps vide | | |
| 43 | Topic inexistant | `DELETE /api/users/me/subscriptions/{topicId}` avec JWT valide ; `topicId` ne correspondant à aucun topic | 404 | Message d'erreur "Ce topic n'existe pas" | | |
| 44 | Aucun JWT | `DELETE /api/users/me/subscriptions/{topicId}` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

### Notes de justification

La distinction entre le scénario 42 et le scénario 43 repose sur la nature de ce qui est évalué : au scénario 42, la ressource ciblée par l'URL (le topic) existe bel et bien, et l'action demandée — « ne plus être abonné à ce topic » — est déjà satisfaite avant comme après la requête, d'où un 204 conforme à la RFC 9110, qui définit l'idempotence comme le fait que l'état du serveur résultant de N requêtes identiques soit le même qu'après une seule, indépendamment du code retour renvoyé à chaque exécution. Au scénario 43, c'est la ressource elle-même référencée dans l'URL qui est absente : il ne s'agit plus de l'état d'un abonnement mais de l'existence du topic, ce qui justifie un 404 distinct. Autrement dit, l'idempotence de DELETE garantit la stabilité de l'état serveur en cas de répétition, mais ne dispense pas de vérifier au préalable que la ressource référencée existe.

## Effet de bord sur l'inscription (AuthService)

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 33 | Inscription avec un email déjà pris | `POST /api/auth/register` (sans JWT) avec `email` = email de B, `username` libre, mot de passe conforme | 409 | `ErrorResponse` avec `message` = "Cet email est déjà utilisé" (et non plus 500) | | |
| 34 | Inscription avec un username déjà pris | `POST /api/auth/register` (sans JWT) avec `username` = username de B, `email` libre, mot de passe conforme | 409 | `ErrorResponse` avec `message` = "Ce nom d'utilisateur est déjà utilisé" (et non plus 500) | | |
| 35 | Connexion non régressée | `POST /api/auth/login` avec identifiant (email ou username) et mot de passe corrects de A | 200 | `AuthResponse` avec `token` | | |
