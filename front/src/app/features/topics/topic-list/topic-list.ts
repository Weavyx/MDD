import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';

import { toApiError } from '../../../core/http/api-error';
import { NotificationService } from '../../../core/notification/notification.service';
import { TopicCard } from '../topic-card/topic-card';
import { TopicResponse } from '../topic.models';
import { TopicService } from '../topic.service';

/** Every topic, with a button to subscribe to the ones the user does not follow yet. */
@Component({
  selector: 'app-topic-list',
  imports: [MatButtonModule, TopicCard],
  templateUrl: './topic-list.html',
  styleUrl: './topic-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopicList {
  private readonly topicService = inject(TopicService);
  private readonly notification = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly topics = signal<TopicResponse[]>([]);
  /** Topics whose subscription request is in flight: their button is disabled meanwhile. */
  readonly pending = signal<ReadonlySet<number>>(new Set());

  constructor() {
    this.topicService
      .getTopics()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (topics) => this.topics.set(topics),
        error: (error: HttpErrorResponse) =>
          this.notifyError(error, 'Impossible de charger les thèmes'),
      });
  }

  subscribe(topic: TopicResponse): void {
    this.setPending(topic.id, true);
    this.topicService
      .subscribe(topic.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.setPending(topic.id, false);
          this.markSubscribed(topic.id);
        },
        error: (error: HttpErrorResponse) => {
          this.setPending(topic.id, false);
          // 409: the subscription already exists (another tab, a double click): not an error.
          if (error.status === 409) {
            this.markSubscribed(topic.id);
            return;
          }
          this.notifyError(error, "L'abonnement a échoué");
        },
      });
  }

  private markSubscribed(topicId: number): void {
    this.topics.update((topics) =>
      topics.map((t) => (t.id === topicId ? { ...t, subscribed: true } : t)),
    );
  }

  private setPending(topicId: number, pending: boolean): void {
    this.pending.update((ids) => {
      const next = new Set(ids);
      if (pending) {
        next.add(topicId);
      } else {
        next.delete(topicId);
      }
      return next;
    });
  }

  /** A 401 is already handled by the interceptor (logout and redirect to `/login`). */
  private notifyError(error: HttpErrorResponse, fallback: string): void {
    if (error.status !== 401) {
      this.notification.show(toApiError(error).message ?? fallback);
    }
  }
}
