import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NotificationService } from '../../../core/notification/notification.service';
import { TopicResponse } from '../topic.models';
import { TopicList } from './topic-list';

describe('TopicList', () => {
  const show = vi.fn();
  let httpTesting: HttpTestingController;

  const topics: TopicResponse[] = [
    { id: 1, name: 'Angular', description: 'Front', subscribed: false },
    { id: 2, name: 'Spring', description: 'Back', subscribed: true },
  ];

  async function render() {
    TestBed.configureTestingModule({
      imports: [TopicList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TopicList);
    httpTesting.expectOne({ method: 'GET', url: '/api/topics' }).flush(topics);
    await fixture.whenStable();
    return fixture;
  }

  /** The action button of the card whose title is `name`. */
  function button(element: HTMLElement, name: string): HTMLButtonElement {
    const card = Array.from(element.querySelectorAll('app-topic-card')).find(
      (c) => c.querySelector('h2')?.textContent?.trim() === name,
    );
    const found = card?.querySelector('button');
    if (!found) {
      throw new Error(`No button for ${name}`);
    }
    return found;
  }

  afterEach(() => {
    httpTesting.verify();
    show.mockReset();
  });

  it('shows every topic with its name and description', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    const cards = Array.from(element.querySelectorAll('app-topic-card'), (c) => [
      c.querySelector('h2')?.textContent?.trim(),
      c.querySelector('p')?.textContent?.trim(),
    ]);
    expect(cards).toEqual([
      ['Angular', 'Front'],
      ['Spring', 'Back'],
    ]);
  });

  it('offers to subscribe to a topic not followed and disables the followed one', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    expect(button(element, 'Angular').textContent?.trim()).toBe("S'abonner");
    expect(button(element, 'Angular').disabled).toBe(false);
    expect(button(element, 'Spring').textContent?.trim()).toBe('Déjà abonné');
    expect(button(element, 'Spring').disabled).toBe(true);
  });

  it('turns the button into a disabled "Déjà abonné" once the subscription succeeds', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    button(element, 'Angular').click();
    await fixture.whenStable();
    expect(button(element, 'Angular').disabled).toBe(true);
    httpTesting.expectOne({ method: 'POST', url: '/api/users/me/subscriptions/1' }).flush(null);
    await fixture.whenStable();

    expect(button(element, 'Angular').textContent?.trim()).toBe('Déjà abonné');
    expect(button(element, 'Angular').disabled).toBe(true);
    expect(show).not.toHaveBeenCalled();
  });

  it('keeps "S\'abonner" and notifies the API message when the subscription fails', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    button(element, 'Angular').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/users/me/subscriptions/1' })
      .flush(
        { status: 409, message: 'Vous êtes déjà abonné à ce topic', fieldErrors: null },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(button(element, 'Angular').textContent?.trim()).toBe("S'abonner");
    expect(button(element, 'Angular').disabled).toBe(false);
    expect(show).toHaveBeenCalledExactlyOnceWith('Vous êtes déjà abonné à ce topic');
  });

  it('notifies a fallback message when the error has no body', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    button(element, 'Angular').click();
    httpTesting
      .expectOne('/api/users/me/subscriptions/1')
      .error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith("L'abonnement a échoué");
  });

  it('leaves a 401 to the interceptor', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    button(element, 'Angular').click();
    httpTesting
      .expectOne('/api/users/me/subscriptions/1')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(show).not.toHaveBeenCalled();
  });

  it('notifies when the topics cannot be loaded', async () => {
    TestBed.configureTestingModule({
      imports: [TopicList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TopicList);
    httpTesting
      .expectOne('/api/topics')
      .flush(null, { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith('Impossible de charger les thèmes');
    expect((fixture.nativeElement as HTMLElement).querySelector('app-topic-card')).toBeNull();
  });
});
