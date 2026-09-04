# Checklist de tests manuels — Topics / Abonnements

## GET /api/topics

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 1 | Utilisateur authentifié, aucun abonnement | `GET /api/topics` avec JWT valide ; utilisateur sans aucune ligne dans `subscriptions` | 200 | Tableau de tous les topics, chacun avec `subscribed: false` | | |
| 2 | Utilisateur authentifié, abonné à certains topics | `GET /api/topics` avec JWT valide ; utilisateur abonné à un sous-ensemble des topics | 200 | Tableau de tous les topics, `subscribed: true` uniquement pour les topics abonnés | | |
| 3 | Aucun JWT fourni | `GET /api/topics` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 4 | JWT invalide/expiré | `GET /api/topics` avec `Authorization: Bearer <token invalide ou expiré>` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

## POST /api/topics/{id}/subscribe

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 5 | Topic existant, pas encore abonné | `POST /api/topics/{id}/subscribe` avec JWT valide ; `id` existant en base ; aucun abonnement préalable pour ce couple utilisateur/topic | 200 | Corps vide | | |
| 6 | Topic existant, déjà abonné | `POST /api/topics/{id}/subscribe` avec JWT valide ; abonnement déjà existant pour ce couple utilisateur/topic | 409 | Message d'erreur "Vous êtes déjà abonné à ce topic" | | |
| 7 | Topic inexistant (id absent en base) | `POST /api/topics/{id}/subscribe` avec JWT valide ; `id` ne correspondant à aucun topic | 404 | Message d'erreur "Ce topic n'existe pas" | | |
| 8 | Aucun JWT | `POST /api/topics/{id}/subscribe` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |
| 9 | id non numérique dans l'URL (ex: `/api/topics/abc/subscribe`) | `POST /api/topics/abc/subscribe` avec JWT valide | à vérifier - non garanti par le code actuel | à déterminer lors du test | | |

## DELETE /api/topics/{id}/subscribe

| # | Scénario | Requête (méthode + état préalable nécessaire) | Code attendu | Corps attendu (résumé) | Résultat observé | Statut |
|---|----------|--------------------------------------------------|--------------|--------------------------|-------------------|--------|
| 10 | Abonnement existant | `DELETE /api/topics/{id}/subscribe` avec JWT valide ; abonnement existant pour ce couple utilisateur/topic | 204 | Corps vide | | |
| 11 | Aucun abonnement existant, topic existant (idempotence) | `DELETE /api/topics/{id}/subscribe` avec JWT valide ; `id` existant en base mais aucun abonnement pour ce couple utilisateur/topic | 204 | Corps vide | | |
| 12 | Topic inexistant | `DELETE /api/topics/{id}/subscribe` avec JWT valide ; `id` ne correspondant à aucun topic | 404 | Message d'erreur "Ce topic n'existe pas" | | |
| 13 | Aucun JWT | `DELETE /api/topics/{id}/subscribe` sans en-tête `Authorization` | 401 | Corps d'erreur d'authentification (selon config Spring Security) | | |

## Notes de justification

La distinction entre le scénario 11 et le scénario 12 repose sur la nature de ce qui est évalué : au scénario 11, la ressource ciblée par l'URL (le topic) existe bel et bien, et l'action demandée — « ne plus être abonné à ce topic » — est déjà satisfaite avant comme après la requête, d'où un 204 conforme à la RFC 9110, qui définit l'idempotence comme le fait que l'état du serveur résultant de N requêtes identiques soit le même qu'après une seule, indépendamment du code retour renvoyé à chaque exécution. Au scénario 12, c'est la ressource elle-même référencée dans l'URL qui est absente : il ne s'agit plus de l'état d'un abonnement mais de l'existence du topic, ce qui justifie un 404 distinct. Autrement dit, l'idempotence de DELETE garantit la stabilité de l'état serveur en cas de répétition, mais ne dispense pas de vérifier au préalable que la ressource référencée existe.
