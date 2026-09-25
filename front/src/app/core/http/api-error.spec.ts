import { HttpErrorResponse } from '@angular/common/http';

import { toApiError } from './api-error';
import { ErrorResponse } from './error-response';

describe('toApiError', () => {
  const errorResponse = (body: Partial<ErrorResponse>): ErrorResponse => ({
    timestamp: '2026-09-19T07:53:09.847Z',
    status: 400,
    error: 'Bad Request',
    message: 'Requête invalide',
    fieldErrors: null,
    ...body,
  });

  it('returns the message and the field errors of a validation error', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: errorResponse({
        fieldErrors: { email: "L'adresse e-mail doit être valide" },
      }),
    });

    expect(toApiError(error)).toEqual({
      message: 'Requête invalide',
      fieldErrors: { email: "L'adresse e-mail doit être valide" },
    });
  });

  it('returns the message and no field errors when fieldErrors is null', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: errorResponse({ status: 409, message: 'Cet email est déjà utilisé' }),
    });

    expect(toApiError(error)).toEqual({ message: 'Cet email est déjà utilisé', fieldErrors: {} });
  });

  it.each([
    ['null', null],
    ['an empty string', ''],
  ])('returns nothing for a 401 whose body is %s', (_, body) => {
    const error = new HttpErrorResponse({ status: 401, error: body });

    expect(toApiError(error)).toEqual({ message: null, fieldErrors: {} });
  });

  it.each([
    ['a network error', new ProgressEvent('error')],
    ['a message that is not a string', { status: 500, message: 42 }],
  ])('returns nothing for a body that is not an ErrorResponse (%s)', (_, body) => {
    const error = new HttpErrorResponse({ status: 0, error: body });

    expect(toApiError(error)).toEqual({ message: null, fieldErrors: {} });
  });
});
