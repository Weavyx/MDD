import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthApiService } from './auth-api.service';
import { AuthResponse } from './auth.models';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('registers with POST /api/auth/register', () => {
    let response: AuthResponse | undefined;
    const body = { username: 'alice', email: 'alice@mdd.fr', password: 'Password1!' };

    service.register(body).subscribe((r) => (response = r));

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/auth/register' });
    expect(req.request.body).toEqual(body);
    req.flush({ token: 'jwt' }, { status: 201, statusText: 'Created' });
    expect(response).toEqual({ token: 'jwt' });
  });

  it('logs in with POST /api/auth/login', () => {
    let response: AuthResponse | undefined;
    const body = { identifier: 'alice', password: 'Password1!' };

    service.login(body).subscribe((r) => (response = r));

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/auth/login' });
    expect(req.request.body).toEqual(body);
    req.flush({ token: 'jwt' });
    expect(response).toEqual({ token: 'jwt' });
  });
});
