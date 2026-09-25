/** Mirror of the backend `RegisterRequest` DTO. */
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

/** Mirror of the backend `LoginRequest` DTO; `identifier` is an email or a username. */
export interface LoginRequest {
  identifier: string;
  password: string;
}

/** Mirror of the backend `AuthResponse` DTO. */
export interface AuthResponse {
  token: string;
}
