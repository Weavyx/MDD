/** Mirror of the backend `ErrorResponse` DTO. */
export interface ErrorResponse {
  /** ISO-8601 UTC instant (suffix `Z`). */
  timestamp: string;
  status: number;
  error: string;
  message: string;
  /** Present on validation errors only; `null` otherwise. */
  fieldErrors: Record<string, string> | null;
}
