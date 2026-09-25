import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { TopicResponse } from './topic.models';

/** Topics and the current user's subscriptions to them. */
@Injectable({ providedIn: 'root' })
export class TopicService {
  private readonly http = inject(HttpClient);

  /** `GET /api/topics` — every topic, `subscribed` computed for the caller. */
  getTopics(): Observable<TopicResponse[]> {
    return this.http.get<TopicResponse[]>('/api/topics');
  }

  /** `POST /api/users/me/subscriptions/{topicId}` — 200 without body, 409 if already subscribed. */
  subscribe(topicId: number): Observable<void> {
    return this.http.post<void>(`/api/users/me/subscriptions/${topicId}`, null);
  }

  /** `DELETE /api/users/me/subscriptions/{topicId}` — 204, idempotent. */
  unsubscribe(topicId: number): Observable<void> {
    return this.http.delete<void>(`/api/users/me/subscriptions/${topicId}`);
  }
}
