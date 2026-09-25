/** Sort order of the feed, lowercase as required by the API. */
export type FeedSort = 'asc' | 'desc';

/** Mirror of the backend `PostSummaryResponse` DTO. */
export interface PostSummaryResponse {
  id: number;
  title: string;
  excerpt: string;
  /** ISO-8601 local date-time, without time zone. */
  createdAt: string;
  topicName: string;
  author: string;
}

/** Mirror of the backend `CommentResponse` DTO. */
export interface CommentResponse {
  id: number;
  content: string;
  /** ISO-8601 local date-time, without time zone. */
  createdAt: string;
  author: string;
}

/** Mirror of the backend `PostDetailResponse` DTO. */
export interface PostDetailResponse {
  id: number;
  title: string;
  content: string;
  /** ISO-8601 local date-time, without time zone. */
  createdAt: string;
  topicName: string;
  author: string;
  comments: CommentResponse[];
}

/** Mirror of the backend `CreatePostRequest` DTO. */
export interface CreatePostRequest {
  topicId: number;
  title: string;
  content: string;
}

/** Mirror of the backend `CreateCommentRequest` DTO. */
export interface CreateCommentRequest {
  content: string;
}
