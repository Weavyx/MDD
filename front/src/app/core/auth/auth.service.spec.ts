import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService, TOKEN_KEY } from './auth.service';
import { fakeJwt, jwtExpiringIn } from './testing/fake-jwt';

describe('AuthService', () => {
  function createService(): AuthService {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    localStorage.clear();
  });

  describe('storage', () => {
    it('stores the token under "mdd.token" on login', () => {
      const service = createService();
      const token = jwtExpiringIn(3600);

      service.login(token);

      expect(localStorage.getItem('mdd.token')).toBe(token);
      expect(service.token()).toBe(token);
    });

    it('reads the token stored by a previous session', () => {
      const token = jwtExpiringIn(3600);
      localStorage.setItem(TOKEN_KEY, token);

      const service = createService();

      expect(service.token()).toBe(token);
      expect(service.isAuthenticated()).toBe(true);
    });

    it('clears the token and goes to "/" on logout', () => {
      const service = createService();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      service.login(jwtExpiringIn(3600));

      service.logout();

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(service.token()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith('/');
    });

    it('starts logged out when reading the storage throws', () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('SecurityError');
      });

      const service = createService();

      expect(service.token()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('keeps the session in memory when writing the storage throws', () => {
      const service = createService();
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('QuotaExceededError');
      });

      service.login(jwtExpiringIn(3600));

      expect(service.isAuthenticated()).toBe(true);
    });

    it('still logs out when clearing the storage throws', () => {
      const service = createService();
      vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      service.login(jwtExpiringIn(3600));
      vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
        throw new Error('SecurityError');
      });

      service.logout();

      expect(service.isAuthenticated()).toBe(false);
    });
  });

  describe('isAuthenticated', () => {
    it('is false without a token', () => {
      expect(createService().isAuthenticated()).toBe(false);
    });

    it.each([
      ['not a JWT', 'not-a-jwt'],
      ['a payload that is not base64', 'header.%%%.signature'],
      ['a payload that is not JSON', `header.${btoa('not json')}.signature`],
      ['a payload that is not an object', `header.${btoa('42')}.signature`],
    ])('is false for an unreadable token (%s)', (_, token) => {
      const service = createService();

      service.login(token);

      expect(service.isAuthenticated()).toBe(false);
    });

    it.each([
      ['two', (token: string) => token.split('.').slice(0, 2).join('.')],
      ['four', (token: string) => `${token}.extra`],
    ])('is false for a readable, unexpired payload in %s segments', (_, reshape) => {
      const service = createService();

      service.login(reshape(jwtExpiringIn(3600)));

      expect(service.isAuthenticated()).toBe(false);
    });

    it('is false for a token without exp', () => {
      const service = createService();

      service.login(fakeJwt({ sub: '1' }));

      expect(service.isAuthenticated()).toBe(false);
    });

    it('is false for a token whose exp is not a number', () => {
      const service = createService();

      service.login(fakeJwt({ sub: '1', exp: String(Math.floor(Date.now() / 1000) + 3600) }));

      expect(service.isAuthenticated()).toBe(false);
    });

    it('is false for an expired token', () => {
      const service = createService();

      service.login(jwtExpiringIn(-60));

      expect(service.isAuthenticated()).toBe(false);
    });

    it('is false at the exact expiry instant', () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2026-09-25T10:00:00Z'));
      const service = createService();

      service.login(fakeJwt({ sub: '1', exp: Date.parse('2026-09-25T10:00:00Z') / 1000 }));

      expect(service.isAuthenticated()).toBe(false);
    });

    it('is true for a token that is not expired yet', () => {
      const service = createService();

      service.login(jwtExpiringIn(60));

      expect(service.isAuthenticated()).toBe(true);
    });

    it('decodes base64url payloads (with "-" and "_")', () => {
      const service = createService();
      const token = fakeJwt({ exp: Math.floor(Date.now() / 1000) + 3600, sub: '~~~???>>>' });
      expect(token.split('.')[1]).toMatch(/[-_]/);

      service.login(token);

      expect(service.isAuthenticated()).toBe(true);
    });
  });
});
