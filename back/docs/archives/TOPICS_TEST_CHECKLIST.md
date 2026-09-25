# Checklist de tests manuels — Topics / Abonnements

## GET /api/topics

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 1 | Utilisateur authentifié, aucun abonnement | `GET /api/topics` avec JWT valide ; utilisateur sans aucune ligne dans `subscriptions` | 200 | Tableau de tous les topics, chacun avec `subscribed: false` | | |
| 2 | Utilisateur authentifié, abonné à certains topics | `GET /api/topics` avec JWT valide ; utilisateur abonné à un sous-ensemble des topics | 200 | Tableau de tous les topics, `subscribed: true` uniquement pour les topics abonnés | | |
| 3 | Aucun JWT fourni | `GET /api/topics` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 4 | JWT invalide/expiré | `GET /api/topics` avec `Authorization: Bearer <token invalide ou expiré>` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

Les scénarios d'abonnement/désabonnement (`POST`/`DELETE /api/users/me/subscriptions/{topicId}`) ont été déplacés vers `USER_PROFILE_TEST_CHECKLIST.md` suite à la migration de ces endpoints de `TopicController` vers `UserController`.
