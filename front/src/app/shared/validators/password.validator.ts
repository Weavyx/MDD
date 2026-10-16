import { ValidatorFn } from '@angular/forms';

/**
 * Same rule as the backend `RegisterRequest`/`UpdateProfileRequest` password: a digit, a
 * lowercase letter, an uppercase letter, a special character, at least 8 characters.
 * `[\x21-\x2F\x3A-\x40\x5B-\x60\x7B-\x7E]` is the exact ASCII equivalent of Java's `\p{Punct}`:
 * the 32 printable ASCII characters that are neither letters, digits nor space. The upper bound
 * is 72 UTF-8 bytes, not characters: `maxUtf8Bytes(72)` checks it next to this validator.
 */
const PASSWORD_PATTERN =
  /^(?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[\x21-\x2F\x3A-\x40\x5B-\x60\x7B-\x7E]).{8,}$/;

/**
 * Returns `{ password: true }` when the value breaks the rule. An empty value is valid here:
 * whether the field is mandatory is `Validators.required`'s job.
 */
export const passwordValidator: ValidatorFn = (control) => {
  const value: unknown = control.value;
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return typeof value === 'string' && PASSWORD_PATTERN.test(value) ? null : { password: true };
};
