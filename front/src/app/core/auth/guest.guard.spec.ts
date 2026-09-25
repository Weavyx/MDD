import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService, TOKEN_KEY } from './auth.service';
import { guestGuard } from './guest.guard';
import { fakeJwt } from './testing/fake-jwt';

@Component({ template: '' })
class Page {}

describe('guestGuard', () => {
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
          { path: 'login', canActivate: [guestGuard], component: Page },
          { path: 'feed', component: Page },
        ]),
      ],
    });
    const auth = TestBed.inject(AuthService);
    const harness = await RouterTestingHarness.create();
    return {
      auth,
      navigateToLoginPage: async () => {
        await harness.navigateByUrl('/login');
        return TestBed.inject(Router).url;
      },
    };
  }

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('lets a visitor through', async () => {
    const { navigateToLoginPage } = await load(null);

    expect(await navigateToLoginPage()).toBe('/login');
  });

  it('redirects a logged-in user to /feed', async () => {
    const { navigateToLoginPage } = await load(token);

    expect(await navigateToLoginPage()).toBe('/feed');
    expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
  });

  it('lets through and clears a token that expired after the page was loaded', async () => {
    const { auth, navigateToLoginPage } = await load(token);
    expect(auth.isAuthenticated()).toBe(true);

    vi.setSystemTime(loadedAt + 2 * 60_000);

    expect(await navigateToLoginPage()).toBe('/login');
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });
});
