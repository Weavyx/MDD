# Revue complète des tests backend — 18 septembre 2026

Branche `test/revue-tests-backend`. Aucune ligne de code de production modifiée : ce
chantier n'ajoute que des tests et ne corrige aucun comportement.

## Pourquoi ce chantier

Relecture tardive du texte de mission OpenClassrooms : une **couverture de test
minimale de 70 %** est exigée, avec pour consigne « pour chaque fonctionnalité
développée, rédiger les tests associés ». Une dette était déjà consignée (`register`
sans test d'intégration, `login` sans aucun test). L'audit de couverture a révélé que
la dette réelle était plus large.

## Diagnostic initial

Mesure JaCoCo sur `main` avant intervention — **95 tests verts** (32 Surefire +
63 Failsafe), couverture globale **87 % instructions / 86,9 % lignes / 100 % branches**.

| Package | Instructions | Constat |
|---|---|---|
| `security.services` | **11 %** | `UserDetailsImpl` : 0 méthode couverte sur 9 |
| `security.jwt` | **57 %** | `JwtService` quasi entièrement non exécuté |
| racine | 37 % | `MddApiApplication` (méthode `main`) |
| `controller` | 88 % | écart concentré sur `AuthController` (25 %) |
| `service` | 95 % | écart concentré sur `AuthService` (`login` non testé) |
| `exception` | 96 % | un handler jamais atteint |
| `security` | 100 % | — |

Le seuil de 70 % était donc déjà franchi globalement, mais la moyenne masquait deux
trous réels dans la chaîne d'authentification.

### Audit de pertinence des tests existants

Les classes ayant absorbé des scénarios migrés lors des refactorings précédents
(`UserServiceTest` et `UserControllerIT`, qui ont reçu 14 scénarios d'abonnement et
5 scénarios de fil) ont été relues : chaque test conserve un nom
`méthode_condition_résultat` distinct correspondant à un seul cas, sans redondance
d'assertion ni nommage devenu trompeur après déplacement. **Aucune correction
nécessaire.**

## Ce qui a été ajouté

24 tests, dans l'ordre de rédaction service → controller conforme à la pyramide de
tests du projet.

| Classe | Nouveaux tests | Ce qui est vérifié |
|---|---|---|
| `JwtServiceTest` (nouvelle) | 4 | Valeur du token retournée ; claims `iss`/`sub`/`iat` construits ; expiration conforme à la durée configurée ; algorithme HS256 |
| `UserDetailsImplTest` (nouvelle) | 4 | Délégation des getters au `User` enveloppé ; `getUsername()` renvoie le username et non l'email ; authorities vides ; flags de compte tous actifs |
| `UserDetailsServiceImplTest` (nouvelle) | 3 | Résolution par email ; résolution par username ; `UsernameNotFoundException` sur identifiant inconnu |
| `AuthServiceTest` (complétée) | 2 | `login` : token généré avec l'id du principal ; `BadCredentialsException` propagée sans génération de token |
| `AuthControllerIT` (remplie) | 10 | `register` : 201 + token, 409 email, 409 username, 400 × 3 (email invalide, mot de passe non conforme, username trop court) ; `login` : 200 + token, identifiant transmis tel quel au service, 401 mauvais identifiants, 400 identifiant absent |
| `UserControllerIT` (complétée) | 1 | `DataIntegrityViolationException` → 409 avec message générique, sans fuite de détail technique de la base |

## Résultat

**119 tests verts** (45 Surefire + 74 Failsafe), 0 échec — à l'issue de cette première
phase. Une seconde phase d'audit qualité a suivi, décrite plus bas : total final **126**.

| Métrique | Avant | Après |
|---|---|---|
| Instructions | 87 % | **99,5 %** (5 manquées / 1 059) |
| Lignes | 86,9 % | **99,2 %** (2 manquées / 252) |
| Branches | 100 % | **100 %** (0 / 22) |

Par package, après : `controller`, `exception`, `security`, `security.jwt`,
`security.services`, `service` tous à **100 %**. Seule la racine
`com.openclassrooms.mddapi` reste à 33,3 % — voir ci-dessous.

Progressions les plus fortes : `security.services` 11 % → 100 % (+89 points),
`security.jwt` 57 % → 100 % (+43 points).

## Ce qui reste non couvert, et pourquoi

Une seule classe sur 22, deux lignes :

MddApiApplication.java
L10: SpringApplication.run(MddApiApplication.class, args);
L11: }


Le corps de `main(String[])`. Le contexte Spring est bien chargé et vérifié par
`MddApiApplicationIT`, mais via `@SpringBootTest`, jamais par un appel à `main`.

**Décision : ne pas couvrir, ne pas exclure.** L'alternative envisagée était d'ajouter
un bloc `<excludes>` JaCoCo dans le `pom.xml` pour retirer cette classe du périmètre
et afficher 100 %. Rejetée : une exclusion maquille le chiffre au lieu d'assumer une
limite, et déplace la question du « pourquoi ce n'est pas couvert » vers un
« pourquoi c'est exclu » plus difficile à défendre. Un 99,2 % expliqué vaut mieux
qu'un 100 % obtenu en réduisant le périmètre mesuré.

L'autre alternative — couvrir `main` via un `MockedStatic` sur `SpringApplication` —
a été écartée comme complexité non demandée, la mission demandant explicitement de ne
pas en ajouter.

## Absence des DTOs et entités du rapport

Les DTOs et entités JPA n'apparaissent pas dans le rapport JaCoCo, et **ce n'est pas
une exclusion configurée** : le `pom.xml` ne contient aucun bloc `<excludes>`. Ces
classes sont intégralement générées par Lombok (`@Data`, `@Getter`/`@Setter`,
`@NoArgsConstructor`), et JaCoCo ignore par défaut tout code annoté
`@lombok.Generated` — une classe dont toutes les méthodes sont générées disparaît donc
du rapport.

Ajouter des `<excludes>` explicites pour ces packages a été envisagé puis rejeté :
aucun chiffre n'en serait changé, et cela laisserait croire à une décision de
configuration là où le comportement vient de l'outillage.

## Deux points découverts pendant le chantier

**`JwtService` n'avait jamais été exécuté par un test.** Partout où il intervenait, il
était mocké (`AuthServiceTest`) ou son `JwtDecoder` l'était (`@WebMvcTest`). La pièce
la plus sensible de l'authentification — construction des claims, durée de validité,
algorithme de signature — fonctionnait sans aucun filet. C'est le trou le plus
significatif comblé par ce chantier, et il n'avait pas été identifié par la dette
consignée jusque-là.

**`handleDataIntegrityViolation` n'avait jamais été atteint.** Ce handler est le filet
de sécurité de la contrainte UNIQUE composite sur `subscriptions` (cas du double-clic
rapide). Un handler jamais exécuté peut très bien ne pas produire ce qu'on croit ; le
test ajouté verrouille le 409 et surtout le message générique, garantissant qu'aucun
détail technique de la base ne remonte au client.

## Deux choix techniques de test à signaler

**Vérification des claims JWT par `ArgumentCaptor` plutôt que par encodage réel.**
`JwtEncoder` est mocké et l'argument passé à `encode()` est capturé pour inspecter le
`JwtClaimsSet` construit. L'alternative — encoder réellement avec une clé HMAC puis
décoder le token produit — a été rejetée : elle aurait transformé un test unitaire en
test d'intégration dépendant de la clé secrète et de l'environnement, sans vérifier
davantage la logique propre à `JwtService`.

**Lecture du claim `iss` par `getClaim("iss")` et non `getIssuer()`.** Première
rédaction du test utilisant `claims.getIssuer()` : échec avec
`IllegalArgumentException: Unable to convert claim 'iss' of type 'class
java.lang.String' to URL`. `JwtClaimAccessor.getIssuer()` force une conversion en
`URL`, incompatible avec la valeur configurée `mdd.jwt.issuer=mdd-api`, qui est une
chaîne simple. Le code de production est correct — un `iss` non-URL est valide au sens
de la RFC 7519 (`StringOrURI`) — et rien dans l'application ne relit ce claim :
`NimbusJwtDecoder.withSecretKey()` n'installe pas de `JwtIssuerValidator`, seule
l'expiration est validée. C'est l'assertion du test qui a été corrigée.

## `login` avec mauvais identifiants : 401 sans handler dédié

`BadCredentialsException` n'a aucun `@ExceptionHandler` dans
`GlobalExceptionHandler`, et le test `login_identifiantsInvalides_retourne401`
confirme pourtant un **401**. Mécanisme : `BadCredentialsException` est une
`AuthenticationException` ; levée depuis le contrôleur, elle remonte la chaîne de
filtres où `ExceptionTranslationFilter` l'intercepte et délègue au
`BearerTokenAuthenticationEntryPoint` du resource server, qui répond 401.

Ajouter un handler explicite a été envisagé puis rejeté : il dupliquerait un
comportement déjà fourni nativement par Spring Security, avec le risque de diverger du
format d'erreur du reste de la chaîne d'authentification.

## Correction d'un décompte erroné

La documentation du projet consignait « suite complète 63/63 » comme total après le
chantier précédent. Ce chiffre était **le total Failsafe uniquement** : le log dont il
provenait s'arrêtait sur le bloc `Results:` de Failsafe, sans les tests unitaires
exécutés plus tôt par Surefire. Le total réel avant ce chantier était de **95 tests**
(32 Surefire + 63 Failsafe).

Vérification par le décompte : Surefire 32 + 13 nouveaux = 45 ; Failsafe 63 + 11
nouveaux = 74 ; total 119.

Ce chiffre étant destiné à alimenter le rapport de tests final, il est à corriger dans
les notes de suivi du projet.

## Seconde phase — audit de la qualité des tests

La première phase a porté la couverture de 87 % à 99,5 %. Elle ne dit rien de la
**qualité** des tests : la couverture mesure les lignes *exécutées*, jamais les
comportements *vérifiés*. Un test qui appelle une méthode sans rien asserter de sérieux
la couvre à 100 % tout en ne protégeant de rien.

Une seconde phase a donc audité les 119 tests existants, un par un, avec une question
unique : **si on cassait la logique de production que ce test prétend vérifier,
échouerait-il ?**

### Méthode : la preuve par mutation

Chaque correction a suivi le même protocole en cinq temps :

1. Modifier temporairement le code de production pour introduire un bug plausible
2. Relancer le test — s'il reste **vert**, la faiblesse est prouvée ; s'il échoue,
   l'audit s'était trompé et rien n'est corrigé
3. Restaurer le code de production
4. Corriger le test
5. Ré-appliquer la mutation et vérifier que le test **échoue** désormais, puis restaurer

`git diff src/main/java` a été vérifié vide après chaque mutation et à la fin de chaque
lot : aucune ligne de production n'a été modifiée de façon permanente.

Ce protocole a un coût, mais il produit une garantie qu'aucune métrique ne donne : on
sait, test par test, qu'il détecte effectivement l'erreur qu'il est censé détecter.

### Ce que l'audit a révélé

**15 situations où le code de production cassé laissait la suite entièrement verte.**

| Mutation appliquée | Résultat avant correction |
|---|---|
| `deleteByUserIdAndTopicId` : clause `WHERE` privée de sa condition sur `user_id` | 6/6 verts |
| `findPostsByUserId` : filtre sur l'auteur ajouté au filtre d'abonnement | 2/2 verts |
| `findWithUserAndTopicById` : `@EntityGraph` retirée | 2/2 verts |
| `findByUserId` (abonnements) : `@EntityGraph` retirée | 7/7 verts |
| `findByPostIdOrderByCreatedAtAsc` : `@EntityGraph` retirée | 1/1 vert |
| `UserController.subscribe` : arguments `(userId, topicId)` inversés | test visé vert |
| `UserController.unsubscribe` : arguments inversés | test visé vert |
| `UserService.subscribe` : `Subscription` construite avec un `user` nul | 17/17 verts |
| `AuthService.login` : `identifier` et `password` inversés dans le token d'authentification | 5/5 verts |
| `AuthService.register` : mot de passe stocké en clair (encoder appelé, résultat ignoré) | 5/5 verts |
| `GlobalExceptionHandler` : clé de `fieldErrors` = nom de l'objet au lieu du nom du champ | 10/10, 13/13 et 28/28 verts |
| `TopicService` : `name` et `description` intervertis dans le mapping | verts |
| `PostService` : `content` remplacé par `title`, `createdAt` du commentaire par celui du post | verts |

### Les deux cas les plus graves

**Suppression d'abonnement sans filtre utilisateur.** `deleteByUserIdAndTopicId` est un
`@Modifying @Query` écrit à la main. Aucun test ne comportait deux utilisateurs abonnés
au même thème : la clause `WHERE` pouvait perdre sa condition sur `user_id` — et donc
supprimer les abonnements de *tous* les utilisateurs au thème visé — sans qu'un seul
test ne bronche. Perte de données silencieuse sur du SQL manuscrit. Corrigé par
`deleteByUserIdAndTopicId_deuxUtilisateursAbonnesAuMemeTopic_neSupprimeQueCeluiVise`.

**Mot de passe potentiellement stocké en clair.** `register_emailEtUsernameLibres_…` se
contentait de `verify(userRepository).save(any(User.class))` : l'objet sauvegardé n'était
jamais inspecté. Un `User` portant le mot de passe en clair passait le test. C'est la
seule garantie de sécurité de toute la suite, et elle n'existait pas. Corrigée par un
`ArgumentCaptor<User>` assertant que le `passwordHash` vaut le hash produit par l'encoder
et **diffère** du mot de passe brut.

### Le piège des tests d'`@EntityGraph`

Trois tests portaient dans leur nom la promesse de vérifier un chargement anticipé
(« …ChargesSansLazyInitializationException », « …AvecTopicCharge »). Aucun ne le
vérifiait : `@DataJpaTest` est transactionnel, la session Hibernate reste ouverte
pendant toute la durée du test, et un accès paresseux réussit donc même sans
`@EntityGraph`. Les trois tests passaient l'annotation retirée.

`entityManager.detach(...)` a d'abord été essayé puis écarté : détacher l'entité racine
ne détache pas les proxies de ses relations, qui restent rattachés à la session ouverte.
La technique retenue est `Hibernate.isInitialized(relation)`, assertée **avant** tout
accès : une relation déjà initialisée au retour de la requête ne peut l'être que par le
fetch anticipé. C'est désormais le patron du projet pour prouver un chargement anticipé.

L'alternative — compter les requêtes SQL via `SessionFactory.getStatistics()` — aurait
aussi fonctionné, mais au prix d'une configuration supplémentaire et d'un message
d'échec moins lisible.

### Le contrat d'erreur de l'API, établi et figé

L'audit a établi factuellement la forme des réponses d'erreur. L'API en produit **trois
différentes**, ce qui n'était documenté nulle part :

| Situation | Forme de la réponse | Ce que le front devra faire |
|---|---|---|
| Validation de corps (`@Valid`) | `ErrorResponse` avec `fieldErrors` peuplé | afficher le message sous chaque champ |
| Erreur métier (404, 409) | `ErrorResponse`, `fieldErrors` à `null` | afficher `message` |
| Identifiants invalides à la connexion | **corps vide**, en-tête `WWW-Authenticate: Bearer …` | fabriquer son propre message |
| Corps JSON malformé | **corps vide** sous MockMvc | cas de repli générique |

`fieldErrors` est une `Map` indexée par **nom de champ du DTO** (`email`, `username`,
`title`…), dont les valeurs sont les `message` déclarés dans les annotations de
validation. Aucun des 16 tests de validation ne l'assertait ; trois d'entre eux le font
désormais, un par contrôleur concerné.

Les deux formes divergentes sont désormais **figées par un test**, ce qui ne les corrige
pas mais rend toute régression visible et le comportement explicite pour le front.

Réserve sur le JSON malformé : sous MockMvc, `sendError(400)` ne déclenche pas le rendu
de `/error`, d'où le corps vide observé. Sur un conteneur réel, Spring Boot renverrait le
JSON de `BasicErrorController`. Le test fige donc le statut et le non-appel du service,
pas la forme que verra réellement le front.

### Résultat de la seconde phase

**126 tests verts** (45 Surefire + 81 Failsafe). 7 tests ajoutés, 23 renforcés.

| Métrique | Avant la seconde phase | Après |
|---|---|---|
| Instructions | 99,5 % | **99,5 %** |
| Lignes | 99,2 % | **99,2 %** |
| Branches | 100 % | **100 %** |

**La couverture n'a pas bougé d'un point.** C'est le principal enseignement du chantier :
quatre lots de travail, quinze faiblesses réelles corrigées dont une perte de données
potentielle et une fuite de mot de passe, pour exactement zéro variation de la métrique.
La couverture est un détecteur de zones non testées, pas une mesure de la qualité des
tests. Elle avait correctement signalé les trous de la première phase ; elle était
aveugle à tout ce qu'a trouvé la seconde.

### Axes d'amélioration identifiés, non traités

Relevés pendant l'audit, hors du périmètre de ce chantier :

- **Unifier les erreurs sur `ErrorResponse`** : un `@ExceptionHandler(HttpMessageNotReadableException.class)`
  et un `AuthenticationEntryPoint` personnalisé donneraient au front une forme d'erreur
  unique. À arbitrer contre le risque de diverger du comportement natif de Spring Security.
- **Aucun test ne vérifie le câblage complet** contrôleur → service → repository → base.
  Les trois strates s'isolent mutuellement par construction : une inversion d'arguments
  entre deux couches resterait invisible. Quelques `@SpringBootTest` sur Testcontainers
  (l'infrastructure `AbstractContainerIT` existe déjà) couvriraient ce trou — c'est une
  limite assumée de l'architecture en strates, pas un oubli.
- **Aucun test de JWT présent mais invalide ou expiré.** Les onze tests de sécurité
  vérifient tous l'*absence* de token ; le `JwtDecoder` mocké dans les contrôleurs n'est
  jamais configuré pour lever une `JwtException`. Le scénario le plus fréquent en
  production — un utilisateur revenant après expiration — n'est pas couvert.
- **Mutation testing outillé** : PIT automatiserait ce que ce chantier a fait à la main.
  Écarté ici (dépendance supplémentaire, compatibilité Spring Boot 4 / Java 21 non
  vérifiée, temps d'exécution), mais c'est l'outil qui répond directement à la question
  « mes tests sont-ils bons ».

## Outillage

- **JUnit 5** + **Mockito** + **AssertJ** — tests unitaires de service et de classes
  de sécurité
- **MockMvc** + `@WebMvcTest` — tests d'intégration controller, service mocké
- **`@DataJpaTest` + Testcontainers MySQL 8.4** — tests d'intégration repository
- **Surefire** (`*Test.java`) / **Failsafe** (`*IT.java`) — séparation unitaires /
  intégration
- **JaCoCo 0.8.15** — mesure de couverture, agrégation des deux runners dans un
  `target/jacoco.exec` unique, rapport généré en phase `verify` après
  `failsafe:verify`

Aucun seuil de couverture bloquant n'est configuré dans le `pom.xml` : la mesure reste
informative.

**Mutation manuelle** — la qualité des tests a été vérifiée en cassant temporairement le
code de production et en contrôlant que le test concerné échoue bien. Aucun outil de
mutation testing n'a été ajouté au projet ; le code de production est resté intact à
chaque étape (`git diff src/main/java` vide).
