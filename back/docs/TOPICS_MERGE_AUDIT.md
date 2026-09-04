# Audit de la branche `feat/topics-subscriptions` avant merge dans `main`

Audit en lecture seule — aucun fichier de code n'a été modifié. Périmètre analysé :

- `repository/SubscriptionRepository.java`
- `exception/TopicNotFoundException.java`
- `exception/AlreadySubscribedException.java`
- `exception/GlobalExceptionHandler.java`
- `dto/ErrorResponse.java`
- `dto/TopicResponse.java`
- `service/TopicService.java`
- `controller/TopicController.java`

(complété ponctuellement par la lecture de `model/Topic.java` et `model/Subscription.java` pour vérifier l'absence d'association JPA, et de `docs/TOPICS_TEST_CHECKLIST.md`.)

---

## 1. Cohérence avec les décisions d'architecture

### 1.1 Stratégie anti-N+1 — ✅ Respectée

`TopicService.findAllWithSubscriptionStatus` (`service/TopicService.java:31-43`) exécute :
1. `topicRepository.findAll()` — une requête pour tous les topics.
2. `subscriptionRepository.findSubscribedTopicIdsByUserId(userId)` (`repository/SubscriptionRepository.java:18-19`) — une requête JPQL ciblée qui ne sélectionne que les `topic.id`.

La jointure se fait ensuite en mémoire via `new HashSet<>(...)` + `Set.contains()` (`service/TopicService.java:33,40`). Aucune jointure JPQL directe n'est utilisée.

Confirmation complémentaire : `Topic` (`model/Topic.java`) ne porte aucune association vers `Subscription` — la relation n'existe que dans l'autre sens (`Subscription` → `Topic` via `@ManyToOne`, `model/Subscription.java:30-32`). La stratégie en deux requêtes séparées est donc la seule option disponible et est bien celle implémentée.

### 1.2 Codes retour — ✅ Respectés

| Endpoint | Cas | Code observé | Emplacement |
|---|---|---|---|
| POST subscribe réussi | `ResponseEntity.ok().build()` | 200 | `controller/TopicController.java:32` |
| DELETE réussi (y compris idempotent) | `ResponseEntity.noContent().build()`, appelé inconditionnellement après le contrôle d'existence du topic, sans lire le nombre de lignes affectées par `deleteByUserIdAndTopicId` | 204 | `controller/TopicController.java:39`, `service/TopicService.java:64` |
| Double abonnement | `AlreadySubscribedException` → `handleAlreadySubscribed` | 409 | `service/TopicService.java:50-52`, `exception/GlobalExceptionHandler.java:20-23` |
| Topic inexistant (subscribe) | `TopicNotFoundException` → `handleTopicNotFound` | 404 | `service/TopicService.java:47-48`, `exception/GlobalExceptionHandler.java:15-18` |
| Topic inexistant (unsubscribe) | `TopicNotFoundException` → `handleTopicNotFound` | 404 | `service/TopicService.java:61-62`, `exception/GlobalExceptionHandler.java:15-18` |

Tous les codes attendus sont conformes.

### 1.3 Utilisateur courant via JWT uniquement — ✅ Respecté

Les trois endpoints (`findAll`, `subscribe`, `unsubscribe`) récupèrent l'utilisateur exclusivement via `@AuthenticationPrincipal Jwt jwt` puis `Long.valueOf(jwt.getSubject())` (`controller/TopicController.java:24,30,37`). Aucun identifiant utilisateur n'est lu depuis un paramètre d'URL ou de requête. Seul `id` (l'identifiant du **topic**) provient de l'URL, ce qui est attendu.

### 1.4 Création de `Subscription` — ✅ Respectée

Dans `TopicService.subscribe` (`service/TopicService.java:46-57`) :
- `Topic` est vérifié via `topicRepository.findById(topicId)` (SELECT réel, nécessaire pour lever `TopicNotFoundException`).
- `User` est récupéré via `userRepository.getReferenceById(userId)` (proxy Hibernate, sans SELECT), puisque l'utilisateur est garanti exister (issu du JWT validé) et n'a pas besoin d'être chargé pleinement.

### 1.5 Désabonnement en une requête + SELECT assumé — ✅ Respecté

`TopicService.unsubscribe` (`service/TopicService.java:59-65`) effectue :
1. `topicRepository.findById(topicId)` — SELECT explicite et assumé pour la vérification d'existence du topic (nécessaire pour distinguer 404 topic-inexistant de 204 idempotent).
2. `subscriptionRepository.deleteByUserIdAndTopicId(userId, topicId)` (`repository/SubscriptionRepository.java:21-23`) — un unique DELETE JPQL via `@Modifying @Query`, sans SELECT préalable de la `Subscription` elle-même.

Conforme à la décision documentée.

---

## 2. Code mort, TODO, imports inutilisés, méthodes non appelées

- Aucun `TODO`/`FIXME` trouvé dans les 8 fichiers audités.
- Aucun import inutilisé détecté (tous les imports de chaque fichier sont utilisés).
- Aucune méthode morte : chaque méthode publique des repositories/service/contrôleur est appelée au moins une fois dans le périmètre (`existsByUserIdAndTopicId`, `findByUserId`, `findSubscribedTopicIdsByUserId`, `deleteByUserIdAndTopicId` sont toutes utilisées, à l'exception de `findByUserId` — voir remarque ci-dessous).

**Remarque (à confirmer par l'équipe, pas un bloquant en soi)** : `SubscriptionRepository.findByUserId(Long userId)` (`repository/SubscriptionRepository.java:16`) n'est appelée nulle part dans le périmètre audité (`TopicService` utilise `findSubscribedTopicIdsByUserId`, pas `findByUserId`). Ce n'est pas strictement du code mort au sens where il pourrait être utilisé ailleurs dans le projet (hors périmètre de cet audit) — à vérifier avant merge si elle est réellement utilisée quelque part, sinon la retirer ou justifier sa présence.

---

## 3. Séparation DTO/Entité

- ✅ Aucun endpoint de `TopicController` ne retourne une entité JPA : `findAll` retourne `List<TopicResponse>`, `subscribe`/`unsubscribe` retournent `Void` (`controller/TopicController.java:23,29,35`).
- ✅ `TopicService` ne renvoie jamais `Topic`, `Subscription` ou `User` au contrôleur — seul `findAllWithSubscriptionStatus` retourne un type public (`List<TopicResponse>`), les deux autres méthodes sont `void`.
- ✅ `TopicResponse` (`dto/TopicResponse.java:8-13`) n'expose que `id`, `name`, `description`, `subscribed` — aucun champ interne (pas de référence à `User`, pas de timestamps techniques, pas d'identifiants de `Subscription`) ne fuite vers l'API publique.

---

## 4. Documentation (Javadoc) des méthodes publiques

Aucune des méthodes publiques suivantes ne porte de Javadoc ni de commentaire explicatif :

- `TopicService.findAllWithSubscriptionStatus(Long userId)` — `service/TopicService.java:31`
- `TopicService.subscribe(Long userId, Long topicId)` — `service/TopicService.java:46`
- `TopicService.unsubscribe(Long userId, Long topicId)` — `service/TopicService.java:60`
- `TopicController.findAll(Jwt jwt)` — `controller/TopicController.java:23`
- `TopicController.subscribe(Jwt jwt, Long id)` — `controller/TopicController.java:29`
- `TopicController.unsubscribe(Jwt jwt, Long id)` — `controller/TopicController.java:36`

Aucune méthode publique de ces deux classes n'est documentée. (Conformément à la consigne, aucune documentation n'a été ajoutée par cet audit.)

---

## Points ouverts non résolus

Les éléments suivants doivent être traités avant un merge définitif — ils ne sont **pas corrigés** par cet audit, seulement listés.

1. **`MethodArgumentTypeMismatchException` non gérée dans `GlobalExceptionHandler`**
   `exception/GlobalExceptionHandler.java` (fichier entier, lignes 15-28) ne déclare que trois `@ExceptionHandler` : `TopicNotFoundException`, `AlreadySubscribedException`, `DataIntegrityViolationException`. Il n'existe **aucun** handler pour `MethodArgumentTypeMismatchException`.
   Conséquence : un appel `POST /api/topics/abc/subscribe` ou `DELETE /api/topics/abc/subscribe` (où `{id}` n'est pas convertible en `Long` — voir `@PathVariable Long id` dans `controller/TopicController.java:29,36`) ne sera intercepté par aucun handler dédié et remontera probablement en **500** via le comportement par défaut de Spring, avec un risque de fuite de stack trace / détails d'implémentation dans la réponse HTTP selon la configuration de `spring.mvc.problemdetails` / gestion d'erreurs par défaut. Ce cas est d'ailleurs déjà identifié comme non garanti dans `back/docs/TOPICS_TEST_CHECKLIST.md:20` (scénario 9 : « à vérifier - non garanti par le code actuel »).

2. **Checklist de tests manuels non exécutée**
   `back/docs/TOPICS_TEST_CHECKLIST.md` liste 13 scénarios de test manuel (`GET /api/topics` : scénarios 1-4 ; `POST /api/topics/{id}/subscribe` : scénarios 5-9 ; `DELETE /api/topics/{id}/subscribe` : scénarios 10-13). Toutes les colonnes « Résultat observé » et « Statut » sont vides à ce jour — aucun des 13 scénarios n'a encore été vérifié manuellement. Ceci doit être exécuté avant un merge définitif dans `main`.

3. **`SubscriptionRepository.findByUserId` potentiellement inutilisée** (voir section 2) — à confirmer avant merge si elle sert ailleurs dans le projet, sinon à retirer.
