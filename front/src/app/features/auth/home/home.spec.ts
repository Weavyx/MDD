import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Home } from './home';

describe('Home', () => {
  async function render() {
    TestBed.configureTestingModule({ imports: [Home], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the MDD logo', async () => {
    expect((await render()).querySelector('.home-logo')?.textContent?.trim()).toBe('MDD');
  });

  it('links to the login and register pages', async () => {
    const links = Array.from((await render()).querySelectorAll('a'), (a) => ({
      text: a.textContent?.trim(),
      href: a.getAttribute('href'),
    }));

    expect(links).toEqual([
      { text: 'Se connecter', href: '/login' },
      { text: "S'inscrire", href: '/register' },
    ]);
  });
});
