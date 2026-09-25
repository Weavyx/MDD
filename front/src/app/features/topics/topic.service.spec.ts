import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TopicResponse } from './topic.models';
import { TopicService } from './topic.service';

describe('TopicService', () => {
  let service: TopicService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TopicService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists topics with GET /api/topics', () => {
    const topics: TopicResponse[] = [
      { id: 1, name: 'Angular', description: 'Front', subscribed: true },
    ];
    let response: TopicResponse[] | undefined;

    service.getTopics().subscribe((r) => (response = r));

    httpTesting.expectOne({ method: 'GET', url: '/api/topics' }).flush(topics);
    expect(response).toEqual(topics);
  });

  it('subscribes with POST /api/users/me/subscriptions/{topicId}', () => {
    let done = false;

    service.subscribe(3).subscribe(() => (done = true));

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/users/me/subscriptions/3' });
    expect(req.request.body).toBeNull();
    req.flush(null);
    expect(done).toBe(true);
  });

  it('unsubscribes with DELETE /api/users/me/subscriptions/{topicId}', () => {
    let done = false;

    service.unsubscribe(3).subscribe(() => (done = true));

    httpTesting
      .expectOne({ method: 'DELETE', url: '/api/users/me/subscriptions/3' })
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });
});
