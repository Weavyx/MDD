import { newUser } from '../support/users';

/** The password rule is enforced on the front before any call to the API. */
describe("Validation du formulaire d'inscription", () => {
  it('refuse un mot de passe faible : message d’erreur, aucune requête, aucune redirection', () => {
    const user = newUser();
    cy.intercept('POST', '/api/auth/register', cy.spy().as('register'));

    cy.visit('/register');
    cy.get('input[formcontrolname="username"]').type(user.username);
    cy.get('input[formcontrolname="email"]').type(user.email);
    cy.get('input[formcontrolname="password"]').type('faible');
    cy.contains('button', "S'inscrire").click();

    cy.contains(
      'mat-error',
      'Le mot de passe doit contenir au moins 8 caractères, dont une majuscule, une minuscule, ' +
        'un chiffre et un caractère spécial',
    );
    cy.location('pathname').should('eq', '/register');
    cy.get('@register').should('not.have.been.called');
  });
});
