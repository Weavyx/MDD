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

**119 tests verts** (45 Surefire + 74 Failsafe), 0 échec.

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
