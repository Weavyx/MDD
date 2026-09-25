import { HttpErrorResponse } from '@angular/common/http';

/** What a screen needs from a failed call: a global message and per-field messages. */
export interface ApiError {
  message: string | null;
  fieldErrors: Record<string, string>;
}

/**
 * Extracts `message` and `fieldErrors` from an `ErrorResponse` body. Any other body (the
 * empty body of a 401, a network error, Spring's default JSON) gives `message: null` and
 * no field errors: the caller derives its own message from the status.
 */
export function toApiError(error: HttpErrorResponse): ApiError {
  const body: unknown = error.error;
  if (typeof body !== 'object' || body === null || !('message' in body)) {
    return { message: null, fieldErrors: {} };
  }
  if (typeof body.message !== 'string') {
    return { message: null, fieldErrors: {} };
  }
  const fieldErrors: Record<string, string> = {};
  if ('fieldErrors' in body && typeof body.fieldErrors === 'object' && body.fieldErrors !== null) {
    for (const [field, message] of Object.entries(body.fieldErrors)) {
      if (typeof message === 'string') {
        fieldErrors[field] = message;
      }
    }
  }
  return { message: body.message, fieldErrors };
}
