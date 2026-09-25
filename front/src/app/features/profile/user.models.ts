import { TopicResponse } from '../topics/topic.models';

/** Mirror of the backend `UserProfileResponse` DTO. */
export interface UserProfileResponse {
  id: number;
  email: string;
  username: string;
  subscriptions: TopicResponse[];
}

/** Mirror of the backend `UpdateProfileRequest` DTO. */
export interface UpdateProfileRequest {
  username: string;
  email: string;
  /** Absent, `null` or blank keeps the current password. */
  password?: string | null;
}

/** Mirror of the backend `UserResponse` DTO. */
export interface UserResponse {
  id: number;
  email: string;
  username: string;
}
