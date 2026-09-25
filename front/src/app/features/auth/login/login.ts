import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { toApiError } from '../../../core/http/api-error';
import { NotificationService } from '../../../core/notification/notification.service';
import { AuthApiService } from '../auth-api.service';
import { UNEXPECTED_ERROR_MESSAGE, applyFieldErrors } from '../auth-form-errors';

/**
 * Login form, by email or username. The API answers wrong credentials with a 401 and an
 * empty body: the page turns it into "Identifiants incorrects".
 */
@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButton,
    MatIconButton,
    MatIcon,
    MatFormField,
    MatLabel,
    MatInput,
    MatError,
  ],
  templateUrl: './login.html',
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly authApi = inject(AuthApiService);
  private readonly auth = inject(AuthService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    identifier: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected readonly submitting = signal(false);
  protected readonly badCredentials = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.badCredentials.set(false);
    this.submitting.set(true);
    this.authApi
      .login(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ token }) => {
          this.auth.login(token);
          void this.router.navigateByUrl('/feed');
        },
        error: (error: HttpErrorResponse) => {
          this.showError(error);
          this.submitting.set(false);
        },
      });
  }

  private showError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      this.badCredentials.set(true);
      return;
    }
    const { message, fieldErrors } = toApiError(error);
    if (!applyFieldErrors(this.form, fieldErrors)) {
      this.notification.show(message ?? UNEXPECTED_ERROR_MESSAGE);
    }
  }
}
