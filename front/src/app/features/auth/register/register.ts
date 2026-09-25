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
import { maxUtf8Bytes } from '../../../shared/validators/max-utf8-bytes.validator';
import { passwordValidator } from '../../../shared/validators/password.validator';
import { AuthApiService } from '../auth-api.service';
import { UNEXPECTED_ERROR_MESSAGE, applyFieldErrors } from '../auth-form-errors';

/**
 * Registration form. The rules mirror the backend `RegisterRequest`; registering returns a
 * token, so a success logs the user in and opens the feed.
 */
@Component({
  selector: 'app-register',
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
  templateUrl: './register.html',
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly authApi = inject(AuthApiService);
  private readonly auth = inject(AuthService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(50),
        Validators.pattern(/^[A-Za-z0-9._-]+$/),
      ],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, passwordValidator, maxUtf8Bytes(72)]],
  });

  protected readonly passwordRule =
    'Le mot de passe doit contenir au moins 8 caractères, dont une majuscule, une minuscule, ' +
    'un chiffre et un caractère spécial';

  protected readonly submitting = signal(false);
  /** Message of a 409 (email or username already used), shown above the button. */
  protected readonly conflict = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.conflict.set(null);
    this.submitting.set(true);
    this.authApi
      .register(this.form.getRawValue())
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
    const { message, fieldErrors } = toApiError(error);
    if (applyFieldErrors(this.form, fieldErrors)) {
      return;
    }
    if (error.status === 409 && message) {
      this.conflict.set(message);
      return;
    }
    this.notification.show(message ?? UNEXPECTED_ERROR_MESSAGE);
  }
}
