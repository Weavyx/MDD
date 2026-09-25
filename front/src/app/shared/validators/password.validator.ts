import { ValidatorFn } from '@angular/forms';

/**
 * Same rule as the backend `RegisterRequest`/`UpdateProfileRequest` password: a digit, a
 * lowercase letter, an uppercase letter, a special character, 8 to 72 characters.
 * `[!-\/:-@\[-`{-~]` is the exact ASCII equivalent of Java's `\p{Punct}`; `{8,72}` merges
 * `@Size(min = 8, max = 72)` into the pattern.
 */
const PASSWORD_PATTERN = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!-\/:-@\[-`{-~]).{8,72}$/;

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
