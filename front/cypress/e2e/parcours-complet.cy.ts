import { newUser, submitLogin } from '../support/users';

/** The whole user journey on a desktop screen, through the real API and database. */
describe('Parcours complet (1280 px)', () => {
  it('inscription, abonnement, article, commentaire, profil, déconnexion et reconnexion', () => {
    const user = newUser();
    const newUsername = `${user.username}_bis`;
    const title = `Article e2e ${user.username}`;
    const content = `Contenu de l'article créé par ${user.username}.`;
    const comment = `Commentaire de ${user.username}.`;

    // Inscription → fil d'actualité
    cy.visit('/');
    cy.contains('a', "S'inscrire").click();
    cy.location('pathname').should('eq', '/register');
    cy.get('input[formcontrolname="username"]').type(user.username);
    cy.get('input[formcontrolname="email"]').type(user.email);
    cy.get('input[formcontrolname="password"]').type(user.password, { log: false });
    cy.contains('button', "S'inscrire").click();
    cy.location('pathname').should('eq', '/feed');
    cy.contains('h1', 'Articles');

    // Thèmes : abonnement à « Java », le bouton devient « Déjà abonné » inactif
    cy.contains('nav a', 'Thèmes').click();
    cy.location('pathname').should('eq', '/topics');
    cy.contains('h2', /^Java$/)
      .parents('mat-card')
      .within(() => {
        cy.contains('button', "S'abonner").click();
        cy.contains('button', 'Déjà abonné').should('be.disabled');
      });

    // Création d'un article dans « Java » → en tête du fil
    cy.contains('nav a', 'Articles').click();
    cy.contains('a', 'Créer un article').click();
    cy.location('pathname').should('eq', '/posts/new');
    cy.get('mat-select[formcontrolname="topicId"]').click();
    cy.contains('mat-option', /^\s*Java\s*$/).click();
    cy.get('input[formcontrolname="title"]').type(title);
    cy.get('textarea[formcontrolname="content"]').type(content);
    cy.contains('button', 'Créer').click();
    cy.location('pathname').should('eq', '/feed');
    cy.get('main li').first().should('contain', title).and('contain', user.username);

    // Ouverture de l'article, ajout d'un commentaire → affiché avec le nom de l'utilisateur
    cy.contains('main li', title).click();
    cy.location('pathname').should('match', /^\/posts\/\d+$/);
    cy.contains('h1', title);
    cy.contains('Aucun commentaire pour le moment.');
    cy.get('textarea[formcontrolname="content"]').type(comment);
    cy.get('button[aria-label="Envoyer le commentaire"]').click();
    cy.contains('li', comment).should('contain', user.username);

    // Profil : « Java » listé, désabonnement → retiré
    cy.get('nav a[aria-label="Profil"]').click();
    cy.location('pathname').should('eq', '/profile');
    cy.get('input[formcontrolname="username"]').should('have.value', user.username);
    cy.contains('h2', /^Java$/)
      .parents('mat-card')
      .within(() => cy.contains('button', 'Se désabonner').click());
    cy.contains('h2', /^Java$/).should('not.exist');
    cy.contains("Vous n'êtes abonné à aucun thème.");

    // Modification du nom d'utilisateur → succès
    cy.get('input[formcontrolname="username"]').clear().type(newUsername);
    cy.contains('button', 'Sauvegarder').click();
    cy.contains('Profil mis à jour');
    cy.get('input[formcontrolname="username"]').should('have.value', newUsername);

    // Déconnexion → accueil
    cy.contains('nav button', 'Se déconnecter').click();
    cy.location('pathname').should('eq', '/');
    cy.contains('h1', 'Accueil');

    // Reconnexion avec le (nouveau) nom d'utilisateur → fil d'actualité
    cy.contains('a', 'Se connecter').click();
    cy.location('pathname').should('eq', '/login');
    submitLogin(newUsername, user.password);
    cy.location('pathname').should('eq', '/feed');

    // Mauvais mot de passe → « Identifiants incorrects », pas de connexion
    cy.contains('nav button', 'Se déconnecter').click();
    cy.location('pathname').should('eq', '/');
    cy.contains('a', 'Se connecter').click();
    submitLogin(newUsername, 'Mauvais1!');
    cy.contains('[role="alert"]', 'Identifiants incorrects');
    cy.location('pathname').should('eq', '/login');
  });
});
