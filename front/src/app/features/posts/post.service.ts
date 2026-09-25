import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateCommentRequest,
  CreatePostRequest,
  FeedSort,
  PostDetailResponse,
  PostSummaryResponse,
} from './post.models';

/** Feed, posts and comments. */
@Injectable({ providedIn: 'root' })
export class PostService {
  private readonly http = inject(HttpClient);

  /** `GET /api/users/me/feed?sort=` — posts of the subscribed topics, not paginated. */
  getFeed(sort: FeedSort = 'desc'): Observable<PostSummaryResponse[]> {
    return this.http.get<PostSummaryResponse[]>('/api/users/me/feed', { params: { sort } });
  }

  /** `POST /api/posts` — 201 without body (the new post's URL is in `Location`). */
  createPost(request: CreatePostRequest): Observable<void> {
    return this.http.post<void>('/api/posts', request);
  }

  /** `GET /api/posts/{id}` — the post with its comments, oldest first. */
  getPost(id: number): Observable<PostDetailResponse> {
    return this.http.get<PostDetailResponse>(`/api/posts/${id}`);
  }

  /** `POST /api/posts/{id}/comments` — 201 without body; re-read the post to see it. */
  addComment(postId: number, request: CreateCommentRequest): Observable<void> {
    return this.http.post<void>(`/api/posts/${postId}/comments`, request);
  }
}
