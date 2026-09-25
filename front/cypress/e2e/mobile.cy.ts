import { newUser, registerByApi, submitLogin } from '../support/users';

const WIDTH = 375;
const HEIGHT = 812;

/** Opens the burger menu and waits until it has slid entirely into the screen. */
function openMenu(): void {
  cy.get('button[aria-label="Ouvrir le menu"]').click();
  cy.get('[aria-label="Menu principal"]').should(($menu) => {
    expect($menu[0].getBoundingClientRect().right).to.be.at.most(WIDTH);
  });
}

/** Handset layout: the navigation moves into a side menu opened by the burger button. */
describe(`Navigation mobile (${WIDTH} × ${HEIGHT})`, () => {
  beforeEach(() => cy.viewport(WIDTH, HEIGHT));

  it('connexion, menu burger, « Thèmes » puis déconnexion depuis le menu', () => {
    const user = newUser();
    registerByApi(user);

    cy.visit('/login');
    submitLogin(user.username, user.password);
    cy.location('pathname').should('eq', '/feed');
    cy.get('nav[aria-label="Navigation principale"]').should('not.exist');

    openMenu();
    cy.get('[aria-label="Menu principal"]').contains('Thèmes').click();
    cy.location('pathname').should('eq', '/topics');
    cy.contains('h1', 'Thèmes');
    cy.get('[aria-label="Menu principal"]').should('not.be.visible');

    openMenu();
    cy.get('[aria-label="Menu principal"]').contains('Se déconnecter').click();
    cy.location('pathname').should('eq', '/');
    cy.contains('h1', 'Accueil');
  });
});
