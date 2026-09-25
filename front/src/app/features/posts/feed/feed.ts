import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { toApiError } from '../../../core/http/api-error';
import { NotificationService } from '../../../core/notification/notification.service';
import { FeedSort, PostSummaryResponse } from '../post.models';
import { PostService } from '../post.service';

/** `/feed`: posts of the subscribed topics, newest first unless the user flips the order. */
@Component({
  selector: 'app-feed',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './feed.html',
  styleUrl: './feed.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Feed {
  private readonly postService = inject(PostService);
  private readonly notification = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly sort = signal<FeedSort>('desc');
  /** `null` until the first response, so that the empty message does not flash. */
  protected readonly posts = signal<PostSummaryResponse[] | null>(null);

  constructor() {
    this.load();
  }

  protected toggleSort(): void {
    this.sort.update((sort) => (sort === 'desc' ? 'asc' : 'desc'));
    this.load();
  }

  private load(): void {
    this.postService
      .getFeed(this.sort())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (posts) => this.posts.set(posts),
        error: (error: HttpErrorResponse) =>
          this.notification.show(
            toApiError(error).message ?? 'Impossible de charger le fil d’actualité.',
          ),
      });
  }
}
