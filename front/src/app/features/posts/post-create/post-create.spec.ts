import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { Router, provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notification/notification.service';
import { TopicResponse } from '../../topics/topic.models';
import { PostCreate } from './post-create';

@Component({ template: '' })
class Blank {}

describe('PostCreate', () => {
  let fixture: ComponentFixture<PostCreate>;
  let loader: HarnessLoader;
  let httpTesting: HttpTestingController;
  let show: ReturnType<typeof vi.fn>;

  const topics: TopicResponse[] = [
    { id: 1, name: 'Java', description: 'Le langage', subscribed: true },
    { id: 2, name: 'Angular', description: 'Le framework', subscribed: false },
  ];

  beforeEach(async () => {
    show = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'feed', component: Blank }]),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PostCreate);
    loader = TestbedHarnessEnvironment.loader(fixture);
    httpTesting.expectOne({ method: 'GET', url: '/api/topics' }).flush(topics);
    await fixture.whenStable();
  });

  afterEach(() => httpTesting.verify());

  const el = (): HTMLElement => fixture.nativeElement;
  const errors = (): string[] =>
    Array.from(el().querySelectorAll('mat-error')).map((e) => e.textContent!.trim());

  function type(selector: string, value: string): void {
    const input = el().querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  async function fill(): Promise<void> {
    await (await loader.getHarness(MatSelectHarness)).clickOptions({ text: 'Angular' });
    type('input', 'Mon titre');
    type('textarea', 'Mon contenu');
  }

  async function submit(): Promise<void> {
    el().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
  }

  it('shows the title, the back arrow and the three fields, without author nor date', () => {
    expect(el().querySelector('h1')?.textContent).toBe('Créer un nouvel article');
    expect(el().querySelector('a[href="/feed"]')).not.toBeNull();
    const labels = Array.from(el().querySelectorAll('mat-label')).map((l) => l.textContent);
    expect(labels).toEqual(['Sélectionner un thème', 'Titre de l’article', 'Contenu de l’article']);
    expect(el().querySelectorAll('input, textarea, mat-select').length).toBe(3);
    expect(el().textContent).not.toMatch(/auteur|date/i);
  });

  it('offers the topics read from the API', async () => {
    const select = await loader.getHarness(MatSelectHarness);
    await select.open();

    const options = await select.getOptions();
    expect(await Promise.all(options.map((o) => o.getText()))).toEqual(['Java', 'Angular']);
  });

  it('requires every field and sends nothing while one is empty', async () => {
    await submit();

    httpTesting.expectNone({ method: 'POST', url: '/api/posts' });
    expect(errors()).toEqual([
      'Le thème est obligatoire',
      'Le titre est obligatoire',
      'Le contenu est obligatoire',
    ]);
  });

  it('refuses a title longer than 255 characters', async () => {
    await fill();
    type('input', 'a'.repeat(256));

    await submit();

    httpTesting.expectNone({ method: 'POST', url: '/api/posts' });
    expect(errors()).toEqual(['Le titre ne doit pas dépasser 255 caractères']);
  });

  it('creates the post then goes to the feed', async () => {
    await fill();

    await submit();

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/posts' });
    expect(req.request.body).toEqual({ topicId: 2, title: 'Mon titre', content: 'Mon contenu' });
    req.flush(null, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/feed');
  });

  it('shows the API field errors under the matching fields', async () => {
    await fill();
    await submit();

    httpTesting.expectOne({ method: 'POST', url: '/api/posts' }).flush(
      {
        status: 400,
        message: 'Requête invalide',
        fieldErrors: { title: 'Titre refusé', content: 'Contenu refusé' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(errors()).toEqual(['Titre refusé', 'Contenu refusé']);
    expect(show).not.toHaveBeenCalled();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('reports any other error through the notification service', async () => {
    await fill();
    await submit();

    httpTesting
      .expectOne({ method: 'POST', url: '/api/posts' })
      .flush(
        { status: 404, message: 'Topic introuvable', fieldErrors: null },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();

    expect(show).toHaveBeenCalledWith('Topic introuvable');
    expect(errors()).toEqual([]);
  });

  it('reports a failed topic list through the notification service', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PostCreate);

    httpTesting.expectOne('/api/topics').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledWith('Impossible de charger la liste des thèmes.');
    expect(el().querySelector('form')).not.toBeNull();
  });
});
