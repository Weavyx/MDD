import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { toApiError } from '../../../core/http/api-error';
import { NotificationService } from '../../../core/notification/notification.service';
import { TopicService } from '../../topics/topic.service';
import { PostService } from '../post.service';

/** Same limit as `@Size(max = 255)` on the backend `CreatePostRequest.title`. */
export const TITLE_MAX_LENGTH = 255;

const FALLBACK_ERROR = 'Une erreur est survenue. Veuillez réessayer.';

type PostField = 'topicId' | 'title' | 'content';

/** `/posts/new`: author and date are set by the API, the user only picks topic, title and content. */
@Component({
  selector: 'app-post-create',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './post-create.html',
  styleUrl: './post-create.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostCreate {
  private readonly postService = inject(PostService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly titleMaxLength = TITLE_MAX_LENGTH;
  protected readonly submitting = signal(false);

  protected readonly topics = toSignal(
    inject(TopicService)
      .getTopics()
      .pipe(
        catchError((error: HttpErrorResponse) => {
          // A 401 is already handled by the interceptor (logout and redirect to `/login`).
          if (error.status !== 401) {
            this.notification.show(
              toApiError(error).message ?? 'Impossible de charger la liste des thèmes.',
            );
          }
          return of([]);
        }),
      ),
    { initialValue: [] },
  );

  // `pattern(/\S/)` mirrors `@NotBlank`: a value made of spaces only is refused.
  protected readonly form = new FormGroup({
    topicId: new FormControl<number | null>(null, Validators.required),
    title: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(TITLE_MAX_LENGTH),
      ],
    }),
    content: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
  });

  protected submit(): void {
    const { topicId, title, content } = this.form.getRawValue();
    if (this.form.invalid || topicId === null || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.postService
      .createPost({ topicId, title, content })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigateByUrl('/feed'),
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          if (error.status === 401) {
            return; // Already handled by the interceptor.
          }
          const apiError = toApiError(error);
          const fields = Object.entries(apiError.fieldErrors).filter(
            (entry): entry is [PostField, string] => entry[0] in this.form.controls,
          );
          if (fields.length === 0) {
            this.notification.show(apiError.message ?? FALLBACK_ERROR);
            return;
          }
          for (const [field, message] of fields) {
            this.form.controls[field].setErrors({ server: message });
            this.form.controls[field].markAsTouched();
          }
        },
      });
  }
}
