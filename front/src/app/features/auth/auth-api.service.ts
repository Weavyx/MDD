import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AuthResponse, LoginRequest, RegisterRequest } from './auth.models';

/** Public authentication endpoints; storing the returned token is `AuthService`'s job. */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);

  /** `POST /api/auth/register` — 201 with a token: registering logs the user in. */
  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', request);
  }

  /** `POST /api/auth/login` — 401 with an empty body on wrong credentials. */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', request);
  }
}
