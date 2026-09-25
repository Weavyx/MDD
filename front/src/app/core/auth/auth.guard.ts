import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Protected pages: a visitor without a valid token is sent to `/login`. Validity is read at
 * navigation time, so a token that expired since the page was loaded is cleared here.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.checkSession() ? true : router.createUrlTree(['/login']);
};
