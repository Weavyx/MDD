import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationService } from '../../../core/notification/notification.service';
import { PostDetailResponse } from '../post.models';
import { PostDetail } from './post-detail';

@Component({ template: '' })
class Blank {}

describe('PostDetail', () => {
  let harness: RouterTestingHarness;
  let httpTesting: HttpTestingController;
  let show: ReturnType<typeof vi.fn>;

  const post: PostDetailResponse = {
    id: 7,
    title: 'Les signaux',
    content: '<b>Première</b> ligne\nDeuxième ligne',
    createdAt: '2026-09-19T09:53:09.847',
    topicName: 'Angular',
    author: 'alice',
    comments: [{ id: 1, content: 'Bravo', createdAt: '2026-09-19T10:00:00', author: 'bob' }],
  };

  beforeEach(async () => {
    show = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'feed', component: Blank },
          { path: 'posts/:id', component: PostDetail },
        ]),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => httpTesting.verify());

  const el = (): HTMLElement => harness.routeNativeElement!;
  const stable = () => harness.fixture.whenStable();

  async function open(url: string, body: PostDetailResponse | null = post): Promise<void> {
    await harness.navigateByUrl(url);
    const req = httpTesting.expectOne({ method: 'GET', url: '/api/posts/7' });
    if (body) {
      req.flush(body);
    } else {
      req.flush(
        { status: 404, message: 'Article introuvable', fieldErrors: null },
        { status: 404, statusText: 'Not Found' },
      );
    }
    await stable();
  }

  function typeComment(value: string): void {
    const textarea = el().querySelector<HTMLTextAreaElement>('textarea')!;
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new Event('blur'));
  }

  async function send(): Promise<void> {
    el().querySelector<HTMLButtonElement>('.comment-send')!.click();
    await stable();
  }

  it('shows the title, date, author, topic and content as text', async () => {
    await open('/posts/7');

    expect(el().querySelector('h1')?.textContent).toContain('Les signaux');
    const meta = el().querySelector('.post-meta')!.textContent;
    expect(meta).toContain('19/09/2026');
    expect(meta).toContain('alice');
    expect(meta).toContain('Angular');
    const content = el().querySelector('.post-content')!;
    expect(content.textContent).toBe('<b>Première</b> ligne\nDeuxième ligne');
    expect(content.querySelector('b')).toBeNull();
  });

  it('lists the comments with their author', async () => {
    await open('/posts/7');

    const comments = el().querySelectorAll('.comment');
    expect(comments.length).toBe(1);
    expect(comments[0].querySelector('.comment-author')?.textContent).toBe('bob');
    expect(comments[0].querySelector('.comment-content')?.textContent).toBe('Bravo');
  });

  it('goes back to the feed with the arrow', async () => {
    await open('/posts/7');

    el().querySelector<HTMLAnchorElement>('a[aria-label="Retour au fil d’actualité"]')!.click();
    await stable();

    expect(TestBed.inject(Router).url).toBe('/feed');
  });

  it('sends a comment, reloads the post and clears the field', async () => {
    await open('/posts/7');
    typeComment('Très clair');

    await send();

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/posts/7/comments' });
    expect(req.request.body).toEqual({ content: 'Très clair' });
    req.flush(null, { status: 201, statusText: 'Created' });
    await stable();
    httpTesting.expectOne({ method: 'GET', url: '/api/posts/7' }).flush({
      ...post,
      comments: [
        ...post.comments,
        { id: 2, content: 'Très clair', createdAt: '2026-09-19T11:00:00', author: 'demo' },
      ],
    });
    await stable();

    const comments = el().querySelectorAll('.comment');
    expect(comments.length).toBe(2);
    expect(comments[1].textContent).toContain('demo');
    expect(comments[1].textContent).toContain('Très clair');
    expect(el().querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('');
    expect(el().querySelector('mat-error')).toBeNull();
  });

  it.each([
    ['empty', ''],
    ['made of spaces only', '   '],
  ])('refuses a comment that is %s', async (_, value) => {
    await open('/posts/7');
    typeComment(value);

    await send();

    httpTesting.expectNone({ method: 'POST', url: '/api/posts/7/comments' });
    expect(el().querySelector('mat-error')?.textContent).toContain(
      'Le commentaire est obligatoire',
    );
  });

  it('refuses a comment longer than 1000 characters', async () => {
    await open('/posts/7');
    typeComment('a'.repeat(1001));

    await send();

    httpTesting.expectNone({ method: 'POST', url: '/api/posts/7/comments' });
    expect(el().querySelector('mat-error')?.textContent).toContain('1000 caractères');
  });

  it('shows the API field error under the comment field', async () => {
    await open('/posts/7');
    typeComment('Bravo');
    await send();

    httpTesting.expectOne({ method: 'POST', url: '/api/posts/7/comments' }).flush(
      {
        status: 400,
        message: 'Requête invalide',
        fieldErrors: { content: 'Le contenu est obligatoire' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await stable();

    expect(el().querySelector('mat-error')?.textContent).toContain('Le contenu est obligatoire');
    expect(show).not.toHaveBeenCalled();
  });

  it('reports a failed comment through the notification service and keeps the text', async () => {
    await open('/posts/7');
    typeComment('Bravo');
    await send();

    httpTesting
      .expectOne({ method: 'POST', url: '/api/posts/7/comments' })
      .flush(null, { status: 500, statusText: 'Server Error' });
    await stable();

    expect(show).toHaveBeenCalledWith('Une erreur est survenue. Veuillez réessayer.');
    expect(el().querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('Bravo');
  });

  it('leaves a 401 on a comment to the interceptor and notifies nothing', async () => {
    await open('/posts/7');
    typeComment('Bravo');
    await send();

    httpTesting
      .expectOne({ method: 'POST', url: '/api/posts/7/comments' })
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await stable();

    expect(show).not.toHaveBeenCalled();
  });

  it('leaves a 401 on load to the interceptor and notifies nothing', async () => {
    await harness.navigateByUrl('/posts/7');
    httpTesting.expectOne('/api/posts/7').flush(null, { status: 401, statusText: 'Unauthorized' });
    await stable();

    expect(show).not.toHaveBeenCalled();
  });

  it('reports a failed load that is not a 404 through the notification service', async () => {
    await harness.navigateByUrl('/posts/7');
    httpTesting.expectOne('/api/posts/7').flush(null, { status: 500, statusText: 'Server Error' });
    await stable();

    expect(show).toHaveBeenCalledWith('Une erreur est survenue. Veuillez réessayer.');
    expect(el().querySelector('.post-not-found')).toBeNull();
  });

  it('shows a message and a link to the feed when the post does not exist', async () => {
    await open('/posts/7', null);

    expect(el().querySelector('.post-not-found')?.textContent).toContain('Article introuvable');
    expect(el().querySelector('form')).toBeNull();
    el().querySelector<HTMLAnchorElement>('.post-not-found a')!.click();
    await stable();
    expect(TestBed.inject(Router).url).toBe('/feed');
    expect(show).not.toHaveBeenCalled();
  });

  it('treats an id that is not a number as a missing post, without calling the API', async () => {
    await harness.navigateByUrl('/posts/abc');

    httpTesting.expectNone('/api/posts/abc');
    expect(el().querySelector('.post-not-found')).not.toBeNull();
  });
});
