/** A user created for one test; never reused between runs. */
export interface TestUser {
  username: string;
  email: string;
  password: string;
}

/** Satisfies the password rule shared by the front and the API. */
export const VALID_PASSWORD = 'Motdepasse1!';

/** `localStorage` key where the front keeps the JWT. */
export const TOKEN_KEY = 'mdd.token';

/**
 * A fresh user with a unique suffix (timestamp plus a random part), so that every run works
 * on an empty account and never depends on data left by a previous run.
 */
export function newUser(): TestUser {
  const suffix = `${Date.now()}${Cypress._.random(100, 999)}`;
  return {
    username: `e2e_${suffix}`,
    email: `e2e_${suffix}@example.com`,
    password: VALID_PASSWORD,
  };
}

/** Creates the user through the API (via the dev server proxy) and yields its token. */
export function registerByApi(user: TestUser): Cypress.Chainable<string> {
  return cy
    .request<{ token: string }>('POST', '/api/auth/register', user)
    .then((response) => response.body.token);
}

/** Opens `path` as a logged-in user, the token being stored before the app starts. */
export function visitLoggedIn(path: string, token: string): void {
  cy.visit(path, {
    onBeforeLoad: (win) => win.localStorage.setItem(TOKEN_KEY, token),
  });
}

/** Fills and submits the login form; the current page must be `/login`. */
export function submitLogin(identifier: string, password: string): void {
  // The long floating label covers the empty input's centre: clicking the label focuses it.
  cy.contains('mat-label', "E-mail ou nom d'utilisateur").click();
  cy.focused().should('have.attr', 'formcontrolname', 'identifier').type(identifier);
  cy.get('input[formcontrolname="password"]').type(password, { log: false });
  cy.contains('button', 'Se connecter').click();
}
