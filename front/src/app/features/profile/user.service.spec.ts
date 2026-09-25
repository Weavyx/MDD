import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UserProfileResponse, UserResponse } from './user.models';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('reads the profile with GET /api/users/me', () => {
    const profile: UserProfileResponse = {
      id: 1,
      email: 'alice@mdd.fr',
      username: 'alice',
      subscriptions: [{ id: 2, name: 'Angular', description: 'Front', subscribed: true }],
    };
    let response: UserProfileResponse | undefined;

    service.getProfile().subscribe((r) => (response = r));

    httpTesting.expectOne({ method: 'GET', url: '/api/users/me' }).flush(profile);
    expect(response).toEqual(profile);
  });

  it('updates the profile with PUT /api/users/me', () => {
    const body = { username: 'alice2', email: 'alice2@mdd.fr', password: null };
    let response: UserResponse | undefined;

    service.updateProfile(body).subscribe((r) => (response = r));

    const req = httpTesting.expectOne({ method: 'PUT', url: '/api/users/me' });
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1, email: 'alice2@mdd.fr', username: 'alice2' });
    expect(response).toEqual({ id: 1, email: 'alice2@mdd.fr', username: 'alice2' });
  });
});
