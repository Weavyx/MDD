import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { UpdateProfileRequest, UserProfileResponse, UserResponse } from './user.models';

/** The current user's profile; the identity comes from the token, never from the URL. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  /** `GET /api/user` — profile with its subscriptions. */
  getProfile(): Observable<UserProfileResponse> {
    return this.http.get<UserProfileResponse>('/api/user');
  }

  /** `PATCH /api/user` — partial update: username and email; password optional (absent = unchanged). */
  updateProfile(request: UpdateProfileRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>('/api/user', request);
  }
}
