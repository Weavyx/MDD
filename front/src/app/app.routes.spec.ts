import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Type, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { Home } from './features/auth/home/home';
import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { LegalNotice } from './features/legal/legal-notice/legal-notice';
import { PrivacyPolicy } from './features/legal/privacy-policy/privacy-policy';
import { Feed } from './features/posts/feed/feed';
import { PostCreate } from './features/posts/post-create/post-create';
import { PostDetail } from './features/posts/post-detail/post-detail';
import { Profile } from './features/profile/profile/profile';
import { TopicList } from './features/topics/topic-list/topic-list';

describe('routes', () => {
  // Pages are identified by their component, not by their content, so that this spec does not
  // change when a feature replaces its page. The HTTP testing backend keeps the pages that load
  // data on creation from sending real requests.
  async function navigate(url: string, isAuthenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: signal(isAuthenticated),
            checkSession: () => isAuthenticated,
          },
        },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    const component: unknown = harness.routeDebugElement?.componentInstance;
    return {
      url: TestBed.inject(Router).url,
      component: (component as object | undefined)?.constructor,
    };
  }

  describe('as a visitor', () => {
    it.each<[string, Type<unknown>]>([
      ['/', Home],
      ['/login', Login],
      ['/register', Register],
    ])('opens the public page %s', async (url, component) => {
      expect(await navigate(url, false)).toEqual({ url, component });
    });

    it.each(['/feed', '/posts/new', '/posts/42', '/topics', '/profile'])(
      'redirects the protected page %s to /login',
      async (url) => {
        expect(await navigate(url, false)).toEqual({ url: '/login', component: Login });
      },
    );

    it('sends an unknown URL to the home page', async () => {
      expect(await navigate('/nope', false)).toEqual({ url: '/', component: Home });
    });
  });

  describe('as a logged-in user', () => {
    it.each(['/', '/login', '/register'])('redirects the public page %s to /feed', async (url) => {
      expect(await navigate(url, true)).toEqual({ url: '/feed', component: Feed });
    });

    it.each<[string, Type<unknown>]>([
      ['/feed', Feed],
      ['/posts/new', PostCreate],
      ['/posts/42', PostDetail],
      ['/topics', TopicList],
      ['/profile', Profile],
    ])('opens the protected page %s', async (url, component) => {
      expect(await navigate(url, true)).toEqual({ url, component });
    });
  });

  describe('legal pages', () => {
    it.each<[string, boolean, Type<unknown>]>([
      ['/mentions-legales', false, LegalNotice],
      ['/mentions-legales', true, LegalNotice],
      ['/confidentialite', false, PrivacyPolicy],
      ['/confidentialite', true, PrivacyPolicy],
    ])('opens %s for everyone (logged in: %s)', async (url, isAuthenticated, component) => {
      expect(await navigate(url, isAuthenticated)).toEqual({ url, component });
    });
  });

  describe('page titles', () => {
    it.each([
      ['/', 'Accueil | MDD', false],
      ['/login', 'Connexion | MDD', false],
      ['/register', 'Inscription | MDD', false],
      ['/feed', 'Articles | MDD', true],
      ['/posts/new', 'Nouvel article | MDD', true],
      ['/posts/42', 'Article | MDD', true],
      ['/topics', 'Thèmes | MDD', true],
      ['/profile', 'Profil | MDD', true],
      ['/mentions-legales', 'Mentions légales | MDD', false],
      ['/mentions-legales', 'Mentions légales | MDD', true],
      ['/confidentialite', 'Confidentialité | MDD', false],
      ['/confidentialite', 'Confidentialité | MDD', true],
    ])('titles %s "%s"', async (url, title, isAuthenticated) => {
      await navigate(url, isAuthenticated);

      expect(TestBed.inject(Title).getTitle()).toBe(title);
    });
  });
});
