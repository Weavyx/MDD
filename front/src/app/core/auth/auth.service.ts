import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

/** `localStorage` key of the JWT. */
export const TOKEN_KEY = 'mdd.token';

/**
 * Holds the JWT and the derived authentication state.
 *
 * The signature is not checked here (the API does it): the token is only decoded to read
 * its `exp` claim, so that an unreadable or expired token counts as no token at all.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly router = inject(Router);
  private readonly tokenState = signal<string | null>(readStoredToken());

  /** The stored token, valid or not; use `isAuthenticated` before sending it. */
  readonly token = this.tokenState.asReadonly();

  /**
   * True if and only if the token is present, decodable, has a numeric `exp` that is still
   * in the future. Re-evaluated when the token changes, not when time passes: a token that
   * expires while the page is open is caught by the interceptor's 401 handling.
   */
  readonly isAuthenticated = computed(() => {
    const exp = readExpiry(this.tokenState());
    return exp !== null && exp * 1000 > Date.now();
  });

  /** Stores the token returned by login or register. */
  login(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage unavailable (private mode, quota): the session still works in memory.
    }
    this.tokenState.set(token);
  }

  /** Forgets the token (there is no server-side logout) and goes back to the home page. */
  logout(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // Storage unavailable: nothing persisted to remove.
    }
    this.tokenState.set(null);
    void this.router.navigateByUrl('/');
  }
}

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

/** Returns the numeric `exp` claim of a JWT, or `null` if it cannot be read. */
function readExpiry(token: string | null): number | null {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const payload: unknown = JSON.parse(decodeBase64Url(parts[1]));
    if (typeof payload !== 'object' || payload === null || !('exp' in payload)) {
      return null;
    }
    return typeof payload.exp === 'number' && Number.isFinite(payload.exp) ? payload.exp : null;
  } catch {
    return null;
  }
}

function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  return atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
}
