import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { toApiError } from '../../../core/http/api-error';
import { NotificationService } from '../../../core/notification/notification.service';
import { maxUtf8Bytes } from '../../../shared/validators/max-utf8-bytes.validator';
import { passwordValidator } from '../../../shared/validators/password.validator';
import { TopicCard } from '../../topics/topic-card/topic-card';
import { TopicResponse } from '../../topics/topic.models';
import { TopicService } from '../../topics/topic.service';
import { UserService } from '../user.service';

/** Same bounds as the backend `UpdateProfileRequest`. */
const USERNAME_MIN = 3;
const USERNAME_MAX = 50;
const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
const EMAIL_MAX = 255;
const PASSWORD_MAX_BYTES = 72;

/** The user's profile form and the list of topics they follow. */
@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    TopicCard,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Profile {
  private readonly userService = inject(UserService);
  private readonly topicService = inject(TopicService);
  private readonly notification = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly usernameMin = USERNAME_MIN;
  protected readonly usernameMax = USERNAME_MAX;
  protected readonly emailMax = EMAIL_MAX;
  protected readonly passwordMaxBytes = PASSWORD_MAX_BYTES;

  readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(USERNAME_MIN),
        Validators.maxLength(USERNAME_MAX),
        Validators.pattern(USERNAME_PATTERN),
      ],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(EMAIL_MAX)],
    }),
    /** Empty keeps the current password. */
    password: new FormControl('', {
      nonNullable: true,
      validators: [passwordValidator, maxUtf8Bytes(PASSWORD_MAX_BYTES)],
    }),
  });

  readonly subscriptions = signal<TopicResponse[]>([]);
  readonly saving = signal(false);

  constructor() {
    this.userService
      .getProfile()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (profile) => {
          this.form.patchValue({ username: profile.username, email: profile.email });
          this.subscriptions.set(profile.subscriptions);
        },
        error: (error: HttpErrorResponse) =>
          this.notifyError(error, 'Impossible de charger le profil'),
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, email, password } = this.form.getRawValue();
    this.saving.set(true);
    this.userService
      .updateProfile({ username, email, password: password === '' ? null : password })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.form.reset({ username: user.username, email: user.email, password: '' });
          this.notification.show('Profil mis à jour');
        },
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.showSaveError(error);
        },
      });
  }

  unsubscribe(topic: TopicResponse): void {
    this.topicService
      .unsubscribe(topic.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.subscriptions.update((topics) => topics.filter((t) => t.id !== topic.id)),
        error: (error: HttpErrorResponse) => this.notifyError(error, 'Le désabonnement a échoué'),
      });
  }

  /** `fieldErrors` go under their field; anything else (409 included) is a notification. */
  private showSaveError(error: HttpErrorResponse): void {
    const { fieldErrors } = toApiError(error);
    let shownUnderField = false;
    for (const [field, message] of Object.entries(fieldErrors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
        shownUnderField = true;
      }
    }
    if (!shownUnderField) {
      this.notifyError(error, 'La mise à jour du profil a échoué');
    }
  }

  /** A 401 is already handled by the interceptor (logout and redirect to `/login`). */
  private notifyError(error: HttpErrorResponse, fallback: string): void {
    if (error.status !== 401) {
      this.notification.show(toApiError(error).message ?? fallback);
    }
  }
}
