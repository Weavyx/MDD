import { ValidatorFn } from '@angular/forms';

const encoder = new TextEncoder();

/**
 * Same bound as the backend password check: at most `max` bytes once the value is
 * NFC-normalised and encoded in UTF-8 (an accented letter counts 2 bytes). Returns
 * `{ maxUtf8Bytes: { max, actual } }` when the value is too long. An empty value is valid
 * here: whether the field is mandatory is `Validators.required`'s job.
 */
export function maxUtf8Bytes(max: number): ValidatorFn {
  return (control) => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value === '') {
      return null;
    }
    const actual = encoder.encode(value.normalize('NFC')).length;
    return actual <= max ? null : { maxUtf8Bytes: { max, actual } };
  };
}
