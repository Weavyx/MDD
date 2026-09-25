/**
 * The home page fits the screen: nothing scrolls, neither the document nor the shell's
 * content area (`mat-sidenav-content`, which is the actual scroll container), and the
 * legal links of the footer are visible without scrolling.
 */
const VIEWPORTS: [number, number][] = [
  [1280, 800],
  [375, 667],
];

describe('Accueil', () => {
  VIEWPORTS.forEach(([width, height]) => {
    it(`tient dans l'écran sans défilement (${width} × ${height})`, () => {
      cy.viewport(width, height);
      cy.visit('/');
      cy.contains('h1', 'Accueil');

      cy.window().should((win) => {
        const scrolling = win.document.scrollingElement!;
        expect(scrolling.scrollHeight, 'document').to.be.at.most(win.innerHeight);
        const content = win.document.querySelector('mat-sidenav-content')!;
        expect(content.scrollHeight, 'mat-sidenav-content').to.be.at.most(content.clientHeight);
      });

      ['Mentions légales', 'Politique de confidentialité'].forEach((label) => {
        cy.contains('footer a', label)
          .should('be.visible')
          .should(($link) => {
            expect($link[0].getBoundingClientRect().bottom).to.be.at.most(height);
          });
      });
    });
  });
});
