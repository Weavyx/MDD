import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notification/notification.service';
import { PostSummaryResponse } from '../post.models';
import { Feed } from './feed';

@Component({ template: '' })
class Blank {}

describe('Feed', () => {
  let fixture: ComponentFixture<Feed>;
  let httpTesting: HttpTestingController;
  let show: ReturnType<typeof vi.fn>;

  const posts: PostSummaryResponse[] = [
    {
      id: 1,
      title: 'Les signaux',
      excerpt: 'Un extrait sur les signaux',
      createdAt: '2026-09-19T09:53:09.847',
      topicName: 'Angular',
      author: 'alice',
    },
    {
      id: 2,
      title: 'Les records',
      excerpt: 'Un extrait sur les records',
      createdAt: '2026-09-18T08:00:00',
      topicName: 'Java',
      author: 'bob',
    },
  ];

  beforeEach(async () => {
    show = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'posts/new', component: Blank },
          { path: 'posts/:id', component: Blank },
        ]),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Feed);
    await fixture.whenStable();
  });

  afterEach(() => httpTesting.verify());

  const el = (): HTMLElement => fixture.nativeElement;

  async function flushFeed(sort: 'asc' | 'desc', body: PostSummaryResponse[]): Promise<void> {
    httpTesting.expectOne({ method: 'GET', url: `/api/users/me/feed?sort=${sort}` }).flush(body);
    await fixture.whenStable();
  }

  it('loads the feed newest first by default and shows each card', async () => {
    await flushFeed('desc', posts);

    const cards = el().querySelectorAll('.feed-card');
    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Les signaux');
    expect(cards[0].textContent).toContain('19/09/2026');
    expect(cards[0].textContent).toContain('alice');
    expect(cards[0].textContent).toContain('Un extrait sur les signaux');
    expect(el().querySelector('.feed-sort mat-icon')?.textContent).toBe('arrow_downward');
  });

  it('flips the order on each click on "Trier par" and reloads the feed', async () => {
    await flushFeed('desc', posts);
    const sortButton = el().querySelector<HTMLButtonElement>('.feed-sort')!;

    sortButton.click();
    await flushFeed('asc', [...posts].reverse());
    expect(el().querySelector('.feed-card')?.textContent).toContain('Les records');
    expect(el().querySelector('.feed-sort mat-icon')?.textContent).toBe('arrow_upward');

    sortButton.click();
    await flushFeed('desc', posts);
    expect(el().querySelector('.feed-card')?.textContent).toContain('Les signaux');
  });

  it('opens the post when its card is clicked', async () => {
    await flushFeed('desc', posts);

    el().querySelectorAll<HTMLAnchorElement>('.feed-card')[1].click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/posts/2');
  });

  it('links "Créer un article" to /posts/new', async () => {
    await flushFeed('desc', posts);
    const create = Array.from(el().querySelectorAll<HTMLAnchorElement>('a')).find(
      (a) => a.textContent?.trim() === 'Créer un article',
    )!;

    create.click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/posts/new');
  });

  it('shows a message when the feed is empty', async () => {
    await flushFeed('desc', []);

    expect(el().querySelector('.feed-card')).toBeNull();
    expect(el().querySelector('.feed-empty')?.textContent).toContain('Aucun article');
  });

  it('reports a failed load through the notification service', async () => {
    httpTesting
      .expectOne('/api/users/me/feed?sort=desc')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledWith('Impossible de charger le fil d’actualité.');
  });

  it('leaves a 401 to the interceptor and notifies nothing', async () => {
    httpTesting
      .expectOne('/api/users/me/feed?sort=desc')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(show).not.toHaveBeenCalled();
  });
});
