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
  /**
   * Absent: the current password is kept. The API rejects a blank value (400); the profile
   * form omits the key when the field is empty.
   */
  password?: string | null;
}

/** Mirror of the backend `UserResponse` DTO. */
export interface UserResponse {
  id: number;
  email: string;
  username: string;
}
