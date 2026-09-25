import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Home, login and register: a logged-in user is sent to `/feed`. Validity is read at
 * navigation time: a token that expired since the page was loaded is cleared, and the
 * visitor goes through.
 */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.checkSession() ? router.createUrlTree(['/feed']) : true;
};
