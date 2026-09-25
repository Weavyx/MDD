# MDD — front-end

Application Angular 21 de MDD. Installation, configuration et lancement complet (base, API, front) : voir le [README racine](../README.md).

Commandes essentielles, depuis `front/` :

- `npm install` : installe les dépendances ;
- `npm start` : serveur de développement sur `http://localhost:4200`, qui relaie `/api/**` vers l'API sur `http://localhost:8080` (`src/proxy.conf.json`) ;
- `npm test` : tests Vitest en mode surveillance ; `npx ng test --watch=false` pour une seule exécution, `--coverage` en plus pour la couverture ;
- `npm run e2e` : tests end-to-end Cypress, API sur 8080 et front sur 4200 lancés (`npm run e2e:open` pour l'interface, `npm run e2e:firefox` sous Wayland, `-- --config baseUrl=http://localhost:4201` pour un autre port, `npx cypress install` une fois sur une machine neuve) ;
- `npm run build` : build de production dans `dist/front/` ;
- `npm run format` : formate le code avec Prettier (`npm run format:check` pour vérifier sans modifier).

Architecture du front (dossiers, routes, authentification, services) : [`back/docs/DOCUMENTATION_TECHNIQUE.md`](../back/docs/DOCUMENTATION_TECHNIQUE.md), section « Front-end ».
