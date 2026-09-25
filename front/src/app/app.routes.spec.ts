import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

describe('routes', () => {
  async function navigate(url: string, isAuthenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: AuthService, useValue: { isAuthenticated: signal(isAuthenticated) } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return {
      url: TestBed.inject(Router).url,
      heading: harness.routeNativeElement?.querySelector('h1')?.textContent,
    };
  }

  describe('as a visitor', () => {
    it.each(['/', '/login', '/register'])('opens the public page %s', async (url) => {
      expect((await navigate(url, false)).url).toBe(url);
    });

    it.each(['/feed', '/posts/new', '/posts/42', '/topics', '/profile'])(
      'redirects the protected page %s to /login',
      async (url) => {
        expect(await navigate(url, false)).toEqual({ url: '/login', heading: 'Se connecter' });
      },
    );

    it('sends an unknown URL to the home page', async () => {
      expect(await navigate('/nope', false)).toEqual({ url: '/', heading: 'Accueil' });
    });
  });

  describe('as a logged-in user', () => {
    it.each(['/', '/login', '/register'])('redirects the public page %s to /feed', async (url) => {
      expect(await navigate(url, true)).toEqual({ url: '/feed', heading: 'Articles' });
    });

    it.each([
      ['/feed', 'Articles'],
      ['/posts/new', 'Créer un nouvel article'],
      ['/posts/42', 'Article'],
      ['/topics', 'Thèmes'],
      ['/profile', 'Profil utilisateur'],
    ])('opens the protected page %s', async (url, heading) => {
      expect(await navigate(url, true)).toEqual({ url, heading });
    });
  });
});
