import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  FormGroupDirective,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { toApiError } from '../../../core/http/api-error';
import { NotificationService } from '../../../core/notification/notification.service';
import { PostDetailResponse } from '../post.models';
import { PostService } from '../post.service';

/** Same limit as `@Size(max = 1000)` on the backend `CreateCommentRequest.content`. */
export const COMMENT_MAX_LENGTH = 1000;

const FALLBACK_ERROR = 'Une erreur est survenue. Veuillez réessayer.';

/** `/posts/:id`: one post, its comments and the form to add one. */
@Component({
  selector: 'app-post-detail',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './post-detail.html',
  styleUrl: './post-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostDetail {
  private readonly postService = inject(PostService);
  private readonly notification = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly commentFormDirective = viewChild(FormGroupDirective);

  protected readonly commentMaxLength = COMMENT_MAX_LENGTH;
  protected readonly post = signal<PostDetailResponse | null>(null);
  protected readonly notFound = signal(false);
  protected readonly sending = signal(false);

  protected readonly commentForm = new FormGroup({
    // `pattern(/\S/)` mirrors `@NotBlank`: a comment made of spaces only is refused.
    content: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(COMMENT_MAX_LENGTH),
      ],
    }),
  });

  private postId: number | null = null;

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed())
      .subscribe((params) => {
        const id = Number(params.get('id'));
        this.post.set(null);
        this.notFound.set(false);
        // Not a positive integer: the API would answer 400, the user just sees a missing post.
        if (!Number.isInteger(id) || id <= 0) {
          this.postId = null;
          this.notFound.set(true);
          return;
        }
        this.postId = id;
        this.load(id);
      });
  }

  protected sendComment(): void {
    const control = this.commentForm.controls.content;
    if (this.postId === null || this.commentForm.invalid || this.sending()) {
      this.commentForm.markAllAsTouched();
      return;
    }
    const postId = this.postId;
    this.sending.set(true);
    this.postService
      .addComment(postId, { content: control.value })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sending.set(false);
          // resetForm() also clears `submitted`, so the empty field is not shown in error.
          this.commentFormDirective()?.resetForm({ content: '' });
          this.load(postId);
        },
        error: (error: HttpErrorResponse) => {
          this.sending.set(false);
          const apiError = toApiError(error);
          const fieldError = apiError.fieldErrors['content'];
          if (fieldError) {
            control.setErrors({ server: fieldError });
          } else if (error.status === 404) {
            this.notFound.set(true);
          } else if (error.status !== 401) {
            // A 401 is already handled by the interceptor (logout and redirect to `/login`).
            this.notification.show(apiError.message ?? FALLBACK_ERROR);
          }
        },
      });
  }

  private load(id: number): void {
    this.postService
      .getPost(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (post) => this.post.set(post),
        error: (error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.post.set(null);
            this.notFound.set(true);
          } else if (error.status !== 401) {
            // A 401 is already handled by the interceptor (logout and redirect to `/login`).
            this.notification.show(toApiError(error).message ?? FALLBACK_ERROR);
          }
        },
      });
  }
}
