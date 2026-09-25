import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/** Protected API calls: `/api/**` except the public `/api/auth/**`. */
function isProtectedApiUrl(url: string): boolean {
  return url.startsWith('/api/') && !url.startsWith('/api/auth/');
}

/**
 * Adds `Authorization: Bearer <token>` to protected API calls when a valid token exists.
 * A 401 on such a call means the token is no longer accepted: log out, go to `/login`,
 * and still relay the error to the caller. A 401 on `/api/auth/**` (wrong credentials)
 * is relayed untouched.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isProtectedApiUrl(req.url)) {
    return next(req);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.isAuthenticated() ? auth.token() : null;
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
