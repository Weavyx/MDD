import { FormGroup } from '@angular/forms';

/**
 * Puts each backend `fieldErrors` message on the matching control as a `server` error, so
 * that it shows under the field until the user edits it (the next validation run clears it).
 * Returns `false` when no message matched a control of the form.
 */
export function applyFieldErrors(form: FormGroup, fieldErrors: Record<string, string>): boolean {
  let applied = false;
  for (const [field, message] of Object.entries(fieldErrors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...control.errors, server: message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}

/** Fallback when a failed call carries no usable message (network error, 500 without body). */
export const UNEXPECTED_ERROR_MESSAGE = 'Une erreur est survenue, veuillez réessayer.';
