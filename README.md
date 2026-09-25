# MDD — Monde de Dév

MDD est un réseau social de développeurs, réalisé pour le projet 5 (option B) du parcours OpenClassrooms.
Un utilisateur inscrit s'abonne à des thèmes de programmation, lit dans son fil les articles publiés sur ces thèmes, publie ses propres articles et les commente.
Ce dépôt contient le MVP : une API REST Spring Boot et une application Angular.

## Structure du dépôt

| Dossier | Contenu |
|---|---|
| `back/` | API REST Spring Boot (Java 21, MySQL, JWT). Documentation dans `back/docs/` |
| `front/` | Application Angular (Angular Material) |
| `docs/` | Maquettes (`docs/maquettes/`) et FAQ utilisateur (`docs/FAQ.md`) |

## Prérequis

| Outil | Version | Source |
|---|---|---|
| JDK | 21 | `back/pom.xml` (`java.version`) |
| Maven | aucun à installer : wrapper `./mvnw` (Maven 3.9.16) | `back/.mvn/wrapper/maven-wrapper.properties` |
| Node.js | 24 | `mise.toml` |
| npm | 11 (`npm@11.6.3`) | `front/package.json` (`packageManager`) |
| Docker et Docker Compose | image `mysql:8.4` | `docker-compose.yml` |
| mise (facultatif) | — | charge `.env` dans le shell, voir plus bas |

Versions des frameworks : Spring Boot 4.1.0 (`back/pom.xml`), Angular 21.2 (`front/package.json`).

## Configuration

1. **Variables d'environnement.** Copier le modèle puis renseigner les valeurs :

   ```bash
   cp .env.example .env
   ```

   `.env` est gitignoré. Il fournit `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_PORT` (lus par `docker-compose.yml` et par l'API) et `JWT_SECRET` (clé de signature des jetons, en Base64, au moins 32 octets décodés, par exemple `openssl rand -base64 32`). `MDD_CORS_ALLOWED_ORIGINS` est facultative (défaut `http://localhost:4200`).

2. **`back/src/main/resources/application-local.properties`.** Ce fichier est gitignoré et doit être créé à la main sur un clone neuf, avec ces deux lignes :

   ```properties
   spring.datasource.password=${MYSQL_PASSWORD}
   mdd.jwt.secret=${JWT_SECRET}
   ```

3. **Charger `.env` pour Maven.** Maven ne lit pas `.env` : ses variables doivent être présentes dans le shell avant `./mvnw spring-boot:run` et `./mvnw verify`. Deux façons :
   - avec [mise](https://mise.jdx.dev) : exécuter `mise trust` une fois à la racine ; ensuite, dans un shell où mise est activé, `.env` est chargé automatiquement. Sans shell activé, préfixer la commande : `mise exec -- ./mvnw verify` ;
   - sans mise : exporter les variables à la main (`set -a; . ./.env; set +a` depuis la racine, en bash).

Le détail de chaque variable est dans [`back/docs/DOCUMENTATION_TECHNIQUE.md`](back/docs/DOCUMENTATION_TECHNIQUE.md), §5.2.

## Lancement en local

```bash
docker compose up -d
```

Démarre MySQL 8.4 dans le conteneur `mdd-mysql`, sur le port `MYSQL_PORT` de la machine.

```bash
cd back && ./mvnw spring-boot:run
```

Démarre l'API sur `http://localhost:8080`, avec les variables de `.env` chargées. Au premier démarrage, Flyway crée le schéma et insère les huit thèmes de référence.

```bash
cd front && npm install && npm start
```

Démarre l'application sur `http://localhost:4200`. Le serveur de développement relaie tous les appels `/api/**` vers `http://localhost:8080` (`front/src/proxy.conf.json`) : pas de configuration CORS à faire en local.

## Tests

**Back-end** (depuis `back/`) :

```bash
./mvnw test
```

Tests unitaires (`*Test`), sans prérequis.

```bash
./mvnw verify
```

Ajoute les tests d'intégration (`*IT`). Il faut Docker démarré (Testcontainers lance son propre `mysql:8.4`) et `JWT_SECRET` dans l'environnement. Le rapport de couverture JaCoCo est écrit dans `back/target/site/jacoco/index.html`.

**Front-end** (depuis `front/`) :

```bash
npm test
```

Lance Vitest en mode surveillance.

```bash
npx ng test --watch=false
```

Exécute les tests une seule fois (intégration continue).

```bash
npx ng test --watch=false --coverage
```

Mesure la couverture ; le rapport HTML est écrit dans `front/coverage/front/index.html`.

**End-to-end** : les tests Cypress arrivent dans une autre pull request.

## Documentation

- [`back/docs/DOCUMENTATION_TECHNIQUE.md`](back/docs/DOCUMENTATION_TECHNIQUE.md) : endpoints, formats d'erreur, schéma de données, configuration, architecture du front-end.
- [`back/docs/RAPPORT_DE_TESTS.md`](back/docs/RAPPORT_DE_TESTS.md) : stratégie de test et couverture.
- [`back/docs/REVUE_TECHNIQUE.md`](back/docs/REVUE_TECHNIQUE.md) : revue technique et dette identifiée.
- [`docs/FAQ.md`](docs/FAQ.md) : questions fréquentes des utilisateurs.

## Déploiement

L'application n'est pas déployée : il n'existe ni profil de production ni configuration d'hébergement. `npm run build` produit le front dans `front/dist/front/`.

Les services Angular appellent l'API par des URL relatives (`/api/...`). En production, le front et l'API doivent donc être servis sous la même origine, par exemple derrière un reverse proxy qui route `/api/**` vers l'API. Pour servir le front depuis une autre origine, il faudrait rendre l'URL de l'API configurable côté front (ce n'est pas prévu aujourd'hui) et autoriser cette origine dans `MDD_CORS_ALLOWED_ORIGINS`.
