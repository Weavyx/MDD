import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  GuardResult,
  MaybeAsync,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { AuthService } from './auth.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  function runGuard(isAuthenticated: boolean): MaybeAsync<GuardResult> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: signal(isAuthenticated) } },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  it('lets a visitor through', () => {
    expect(runGuard(false)).toBe(true);
  });

  it('redirects a logged-in user to /feed', () => {
    const result = runGuard(true);

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/feed');
  });
});
