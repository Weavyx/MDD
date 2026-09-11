# Checklist de tests manuels — Articles / Commentaires

## GET /api/posts

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 1 | Utilisateur authentifié, tri par défaut (aucun paramètre `sort`) | `GET /api/posts` avec JWT valide ; utilisateur abonné à au moins un topic portant des articles | 200 | Tableau de `PostSummaryResponse` (id, title, excerpt, createdAt, topicName, author), trié du plus récent au plus ancien (`desc`) | | |
| 2 | Tri ascendant explicite | `GET /api/posts?sort=asc` avec JWT valide | 200 | Tableau trié du plus ancien au plus récent | | |
| 3 | Tri descendant explicite | `GET /api/posts?sort=desc` avec JWT valide | 200 | Tableau trié du plus récent au plus ancien | | |
| 4 | Valeur de `sort` invalide | `GET /api/posts?sort=recent` (ou toute valeur hors `asc`/`desc`) avec JWT valide | 400 | Corps d'erreur (`ErrorResponse`) | | |
| 5 | Extrait tronqué sur un contenu long | `GET /api/posts` ; au moins un article existant avec `content` > 200 caractères | 200 | `excerpt` = 200 premiers caractères du `content` + `…`, `content` complet absent du corps | | |
| 6 | Extrait non tronqué sur un contenu court | `GET /api/posts` ; au moins un article existant avec `content` ≤ 200 caractères | 200 | `excerpt` = `content` intégral, sans `…` ajouté | | |
| 7 | Aucun article dans les topics abonnés | `GET /api/posts` avec JWT valide ; utilisateur sans abonnement ou abonné à des topics sans article | 200 | Tableau vide `[]` | | |
| 8 | Aucun JWT fourni | `GET /api/posts` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 9 | JWT invalide/expiré | `GET /api/posts` avec `Authorization: Bearer <token invalide ou expiré>` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

## POST /api/posts

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 10 | Création réussie | `POST /api/posts` avec JWT valide, `{ "topicId": <id existant>, "title": "...", "content": "..." }` | 201 | Corps vide ; header `Location` pointant vers `/api/posts/{id}` du nouvel article | | |
| 11 | Topic inexistant | `POST /api/posts` avec JWT valide, `topicId` ne correspondant à aucun topic en base | 404 | Message d'erreur "Ce topic n'existe pas" | | |
| 12 | `title` manquant/vide | `POST /api/posts` avec JWT valide, `title` absent ou chaîne vide | 400 | Corps d'erreur de validation | | |
| 13 | `title` trop long (> 255 caractères) | `POST /api/posts` avec JWT valide, `title` de 256 caractères ou plus | 400 | Corps d'erreur de validation | | |
| 14 | `content` manquant/vide | `POST /api/posts` avec JWT valide, `content` absent ou chaîne vide | 400 | Corps d'erreur de validation | | |
| 15 | `topicId` manquant | `POST /api/posts` avec JWT valide, `topicId` absent du corps | 400 | Corps d'erreur de validation | | |
| 16 | Aucun JWT | `POST /api/posts` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

## GET /api/posts/{id}

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 17 | Article existant, sans commentaire | `GET /api/posts/{id}` avec JWT valide ; `id` existant, aucun commentaire associé | 200 | `PostDetailResponse` avec `content` complet (non tronqué), `comments: []` | | |
| 18 | Article existant, avec commentaires | `GET /api/posts/{id}` avec JWT valide ; `id` existant, plusieurs commentaires associés | 200 | `PostDetailResponse` avec `content` complet et `comments` triés par `createdAt` croissant (du plus ancien au plus récent) | | |
| 19 | Article inexistant | `GET /api/posts/{id}` avec JWT valide ; `id` ne correspondant à aucun article | 404 | Message d'erreur "Cet article n'existe pas" | | |
| 20 | Aucun JWT | `GET /api/posts/{id}` sans en-tête `Authorization`, `id` existant | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

## POST /api/posts/{id}/comments

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 21 | Ajout de commentaire réussi | `POST /api/posts/{id}/comments` avec JWT valide, `{ "content": "..." }` ; `id` existant | 201 | Corps vide | | |
| 22 | Article inexistant | `POST /api/posts/{id}/comments` avec JWT valide, `id` ne correspondant à aucun article | 404 | Message d'erreur "Cet article n'existe pas" | | |
| 23 | `content` manquant/vide | `POST /api/posts/{id}/comments` avec JWT valide, `content` absent ou chaîne vide ; `id` existant | 400 | Corps d'erreur de validation | | |
| 24 | `content` trop long (> 1000 caractères) | `POST /api/posts/{id}/comments` avec JWT valide, `content` de 1001 caractères ou plus ; `id` existant | 400 | Corps d'erreur de validation | | |
| 25 | Commentaire bien positionné après ajout | Enchaîner scénario 21 puis `GET /api/posts/{id}` | 200 (sur le `GET` suivant) | Le nouveau commentaire apparaît en dernière position de `comments` (tri chronologique croissant) | | |
| 26 | Aucun JWT | `POST /api/posts/{id}/comments` sans en-tête `Authorization`, `id` existant | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
