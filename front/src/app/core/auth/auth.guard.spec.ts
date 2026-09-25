import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { authGuard } from './auth.guard';
import { AuthService, TOKEN_KEY } from './auth.service';
import { fakeJwt } from './testing/fake-jwt';

@Component({ template: '' })
class Page {}

describe('authGuard', () => {
  const loadedAt = Date.parse('2026-09-25T10:00:00Z');
  /** Valid when the page is loaded, expired one minute later. */
  const token = fakeJwt({ sub: '1', exp: loadedAt / 1000 + 60 });

  /**
   * "Loads the page" at `loadedAt`: the real AuthService reads the storage when it is created.
   * Only `Date` is faked, so the router's own timers still run.
   */
  async function load(storedToken: string | null) {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(loadedAt);
    if (storedToken !== null) {
      localStorage.setItem(TOKEN_KEY, storedToken);
    }
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'private', canActivate: [authGuard], component: Page },
          { path: 'login', component: Page },
        ]),
      ],
    });
    const auth = TestBed.inject(AuthService);
    const harness = await RouterTestingHarness.create();
    return {
      auth,
      navigateToPrivatePage: async () => {
        await harness.navigateByUrl('/private');
        return TestBed.inject(Router).url;
      },
    };
  }

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('lets a logged-in user through', async () => {
    const { navigateToPrivatePage } = await load(token);

    expect(await navigateToPrivatePage()).toBe('/private');
    expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
  });

  it('redirects a visitor to /login', async () => {
    const { navigateToPrivatePage } = await load(null);

    expect(await navigateToPrivatePage()).toBe('/login');
  });

  it('redirects to /login and clears a token that expired after the page was loaded', async () => {
    const { auth, navigateToPrivatePage } = await load(token);
    expect(auth.isAuthenticated()).toBe(true);

    vi.setSystemTime(loadedAt + 2 * 60_000);

    expect(await navigateToPrivatePage()).toBe('/login');
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });
});
