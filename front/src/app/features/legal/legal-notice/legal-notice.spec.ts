import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LegalNotice } from './legal-notice';

describe('LegalNotice', () => {
  async function render() {
    TestBed.configureTestingModule({ imports: [LegalNotice], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(LegalNotice);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const texts = (element: HTMLElement, selector: string) =>
    Array.from(element.querySelectorAll(selector), (node) => node.textContent?.trim());

  it('shows its title and its sections', async () => {
    const element = await render();

    expect(texts(element, 'h1')).toEqual(['Mentions légales']);
    expect(texts(element, 'h2')).toEqual([
      'Éditeur',
      'Hébergement',
      'Contenus publiés',
      'Données personnelles',
    ]);
  });

  it('links to the privacy policy', async () => {
    const link = (await render()).querySelector('a');

    expect(link?.textContent?.trim()).toBe('politique de confidentialité');
    expect(link?.getAttribute('href')).toBe('/confidentialite');
  });
});
