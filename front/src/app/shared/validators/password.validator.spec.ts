import { FormControl } from '@angular/forms';

import { passwordValidator } from './password.validator';

describe('passwordValidator', () => {
  const validate = (value: string | null) => passwordValidator(new FormControl(value));

  /** Every character of Java's `\p{Punct}` (POSIX punctuation, ASCII only). */
  const JAVA_PUNCT = '!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~';

  it.each(['Password1!', 'Aa1!aaaa', 'Aa1!'.padEnd(72, 'x')])('accepts %s', (value) => {
    expect(validate(value)).toBeNull();
  });

  it.each([...JAVA_PUNCT])('accepts "%s" as the special character', (special) => {
    expect(validate(`Passwor1${special}`)).toBeNull();
  });

  it.each([
    ['a digit', 'Password!!'],
    ['a lowercase letter', 'PASSWORD1!'],
    ['an uppercase letter', 'password1!'],
    ['a special character', 'Password12'],
  ])('rejects a value without %s', (_, value) => {
    expect(validate(value)).toEqual({ password: true });
  });

  it.each([
    ['a space', 'Password1 '],
    ['a non-ASCII letter', 'Password1é'],
  ])('does not count %s as a special character', (_, value) => {
    expect(validate(value)).toEqual({ password: true });
  });

  it('rejects 7 characters', () => {
    expect(validate('Pass1!a')).toEqual({ password: true });
  });

  it('rejects 73 characters', () => {
    expect(validate('Aa1!'.padEnd(73, 'x'))).toEqual({ password: true });
  });

  it.each([
    ['empty', ''],
    ['null', null],
  ])('leaves an %s value to Validators.required', (_, value) => {
    expect(validate(value)).toBeNull();
  });
});
