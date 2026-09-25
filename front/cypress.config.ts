import { defineConfig } from 'cypress';

/**
 * End-to-end tests against the real stack: the Angular dev server (which proxies `/api/**`
 * to the Spring Boot API on port 8080) and the MySQL database behind it. Nothing is stubbed.
 *
 * Another port: `npx cypress run --config baseUrl=http://localhost:4304`.
 */
export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:4200',
    viewportWidth: 1280,
    viewportHeight: 800,
  },
});
