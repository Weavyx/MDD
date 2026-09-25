import { newUser, registerByApi, visitLoggedIn } from '../support/users';

/** `authGuard` and `guestGuard`, with a real token issued by the API. */
describe('Gardes de navigation', () => {
  it('redirige un visiteur non connecté de /feed vers /login', () => {
    cy.visit('/feed');
    cy.location('pathname').should('eq', '/login');
    cy.contains('h1', 'Se connecter');
  });

  it('redirige un utilisateur connecté de /login vers /feed', () => {
    registerByApi(newUser()).then((token) => visitLoggedIn('/login', token));
    cy.location('pathname').should('eq', '/feed');
    cy.contains('h1', 'Articles');
  });
});
