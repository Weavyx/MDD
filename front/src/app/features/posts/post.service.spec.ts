import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PostDetailResponse, PostSummaryResponse } from './post.models';
import { PostService } from './post.service';

describe('PostService', () => {
  let service: PostService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PostService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it.each(['asc', 'desc'] as const)(
    'reads the feed with GET /api/users/me/feed?sort=%s',
    (sort) => {
      const feed: PostSummaryResponse[] = [
        {
          id: 1,
          title: 'Titre',
          excerpt: 'Extrait',
          createdAt: '2026-09-19T09:53:09.847',
          topicName: 'Angular',
          author: 'alice',
        },
      ];
      let response: PostSummaryResponse[] | undefined;

      service.getFeed(sort).subscribe((r) => (response = r));

      httpTesting.expectOne({ method: 'GET', url: `/api/users/me/feed?sort=${sort}` }).flush(feed);
      expect(response).toEqual(feed);
    },
  );

  it('sorts the feed newest first by default', () => {
    service.getFeed().subscribe();

    httpTesting.expectOne({ method: 'GET', url: '/api/users/me/feed?sort=desc' }).flush([]);
  });

  it('creates a post with POST /api/posts', () => {
    let done = false;
    const body = { topicId: 2, title: 'Titre', content: 'Contenu' };

    service.createPost(body).subscribe(() => (done = true));

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/posts' });
    expect(req.request.body).toEqual(body);
    req.flush(null, { status: 201, statusText: 'Created' });
    expect(done).toBe(true);
  });

  it('reads a post with GET /api/posts/{id}', () => {
    const post: PostDetailResponse = {
      id: 7,
      title: 'Titre',
      content: 'Contenu',
      createdAt: '2026-09-19T09:53:09.847',
      topicName: 'Angular',
      author: 'alice',
      comments: [{ id: 1, content: 'Bravo', createdAt: '2026-09-19T10:00:00', author: 'bob' }],
    };
    let response: PostDetailResponse | undefined;

    service.getPost(7).subscribe((r) => (response = r));

    httpTesting.expectOne({ method: 'GET', url: '/api/posts/7' }).flush(post);
    expect(response).toEqual(post);
  });

  it('comments with POST /api/posts/{id}/comments', () => {
    let done = false;

    service.addComment(7, { content: 'Bravo' }).subscribe(() => (done = true));

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/posts/7/comments' });
    expect(req.request.body).toEqual({ content: 'Bravo' });
    req.flush(null, { status: 201, statusText: 'Created' });
    expect(done).toBe(true);
  });
});
