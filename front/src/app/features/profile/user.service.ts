import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { UpdateProfileRequest, UserProfileResponse, UserResponse } from './user.models';

/** The current user's profile; the identity comes from the token, never from the URL. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  /** `GET /api/users/me` — profile with its subscriptions. */
  getProfile(): Observable<UserProfileResponse> {
    return this.http.get<UserProfileResponse>('/api/users/me');
  }

  /** `PUT /api/users/me` — full replacement of username and email; password optional. */
  updateProfile(request: UpdateProfileRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>('/api/users/me', request);
  }
}
