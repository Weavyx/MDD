import { TestBed } from '@angular/core/testing';

import { PrivacyPolicy } from './privacy-policy';

describe('PrivacyPolicy', () => {
  async function render() {
    const fixture = TestBed.createComponent(PrivacyPolicy);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const texts = (element: HTMLElement, selector: string) =>
    Array.from(element.querySelectorAll(selector), (node) =>
      node.textContent?.replace(/\s+/g, ' ').trim(),
    );

  it('shows its title and its sections', async () => {
    const element = await render();

    expect(texts(element, 'h1')).toEqual(['Politique de confidentialité']);
    expect(texts(element, 'h2')).toEqual([
      'Responsable du traitement',
      'Données collectées',
      'Pourquoi ces données',
      'Qui peut les voir',
      'Stockage dans votre navigateur',
      'Durée de conservation',
      'Vos droits',
    ]);
  });

  it('lists the collected data', async () => {
    expect(texts(await render(), 'ul > li')).toEqual([
      "Votre nom d'utilisateur et votre adresse e-mail.",
      "Votre mot de passe, conservé uniquement sous forme chiffrée (hachage BCrypt) : il n'est jamais stocké en clair.",
      'Vos abonnements aux thèmes, ainsi que les articles et commentaires que vous publiez, avec leur date.',
    ]);
  });
});
