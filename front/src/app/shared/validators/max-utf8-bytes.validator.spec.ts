import { FormControl } from '@angular/forms';

import { maxUtf8Bytes } from './max-utf8-bytes.validator';

describe('maxUtf8Bytes', () => {
  const validate = (value: string | null) => maxUtf8Bytes(72)(new FormControl(value));

  it('accepts 72 ASCII characters (72 bytes)', () => {
    expect(validate('a'.repeat(72))).toBeNull();
  });

  it('accepts 36 "é" (72 bytes)', () => {
    expect(validate('é'.repeat(36))).toBeNull();
  });

  it('rejects 37 "é" (74 bytes) and reports the limit and the actual size', () => {
    expect(validate('é'.repeat(37))).toEqual({ maxUtf8Bytes: { max: 72, actual: 74 } });
  });

  it('counts a decomposed "é" (NFD) as its NFC form', () => {
    const decomposed = 'é'.repeat(36);
    expect(decomposed).toHaveLength(72);
    expect(new TextEncoder().encode(decomposed).length).toBe(108);

    expect(validate(decomposed)).toBeNull();
  });

  it.each([
    ['empty', ''],
    ['null', null],
  ])('leaves an %s value to Validators.required', (_, value) => {
    expect(validate(value)).toBeNull();
  });
});
