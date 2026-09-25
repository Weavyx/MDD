import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import type { MockInstance } from 'vitest';

import { AuthService } from '../auth/auth.service';
import { jwtExpiringIn } from '../auth/testing/fake-jwt';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let auth: AuthService;
  let navigate: MockInstance<Router['navigateByUrl']>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  /** Sends a GET and returns the error it ends with, if any. */
  function get(url: string): { error?: unknown } {
    const outcome: { error?: unknown } = {};
    http.get(url).subscribe({ error: (error: unknown) => (outcome.error = error) });
    return outcome;
  }

  describe('Authorization header', () => {
    it('is added on a protected API call when the token is valid', () => {
      const token = jwtExpiringIn(3600);
      auth.login(token);

      get('/api/topics');

      const req = httpTesting.expectOne('/api/topics');
      expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
      req.flush([]);
    });

    it('is absent on /api/auth/** even with a valid token', () => {
      auth.login(jwtExpiringIn(3600));

      get('/api/auth/login');

      const req = httpTesting.expectOne('/api/auth/login');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({ token: 'x' });
    });

    it.each(['/assets/i18n.json', 'https://example.com/api/topics'])(
      'is absent on a URL outside /api/ (%s)',
      (url) => {
        auth.login(jwtExpiringIn(3600));

        get(url);

        const req = httpTesting.expectOne(url);
        expect(req.request.headers.has('Authorization')).toBe(false);
        req.flush({});
      },
    );

    it('is absent when the stored token is expired', () => {
      auth.login(jwtExpiringIn(-60));

      get('/api/topics');

      const req = httpTesting.expectOne('/api/topics');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush([]);
    });
  });

  describe('401 handling', () => {
    it('logs out, goes to /login and relays the error on a protected call', () => {
      auth.login(jwtExpiringIn(3600));
      const logout = vi.spyOn(auth, 'logout');

      const outcome = get('/api/topics');
      httpTesting.expectOne('/api/topics').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(logout).toHaveBeenCalledOnce();
      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenLastCalledWith('/login');
      expect(outcome.error).toBeInstanceOf(HttpErrorResponse);
      expect((outcome.error as HttpErrorResponse).status).toBe(401);
    });

    it('relays a 401 on /api/auth/login without logging out nor navigating', () => {
      auth.login(jwtExpiringIn(3600));
      const logout = vi.spyOn(auth, 'logout');

      const outcome = get('/api/auth/login');
      httpTesting
        .expectOne('/api/auth/login')
        .flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(logout).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
      expect((outcome.error as HttpErrorResponse).status).toBe(401);
    });

    it('does not log out on another error status', () => {
      auth.login(jwtExpiringIn(3600));
      const logout = vi.spyOn(auth, 'logout');

      const outcome = get('/api/topics');
      httpTesting.expectOne('/api/topics').flush(null, { status: 500, statusText: 'Server Error' });

      expect(logout).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
      expect((outcome.error as HttpErrorResponse).status).toBe(500);
    });
  });
});
