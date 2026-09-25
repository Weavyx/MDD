/** Test helper: an unsigned JWT with the given payload, base64url-encoded without padding. */
export function fakeJwt(payload: object): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

/** Test helper: a JWT whose `exp` is `secondsFromNow` seconds from the current clock. */
export function jwtExpiringIn(secondsFromNow: number): string {
  return fakeJwt({ sub: '1', exp: Math.floor(Date.now() / 1000) + secondsFromNow });
}
