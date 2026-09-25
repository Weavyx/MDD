import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NotificationService } from '../../../core/notification/notification.service';
import { UserProfileResponse } from '../user.models';
import { Profile } from './profile';

describe('Profile', () => {
  const show = vi.fn();
  let httpTesting: HttpTestingController;

  const profile: UserProfileResponse = {
    id: 1,
    email: 'alice@mdd.fr',
    username: 'alice',
    subscriptions: [
      { id: 2, name: 'Angular', description: 'Front', subscribed: true },
      { id: 3, name: 'Spring', description: 'Back', subscribed: true },
    ],
  };

  /** Creates the page; the GET /api/users/me it sends is left pending. */
  function create(): ComponentFixture<Profile> {
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(Profile);
  }

  async function render(loaded: UserProfileResponse = profile) {
    const fixture = create();
    httpTesting.expectOne({ method: 'GET', url: '/api/users/me' }).flush(loaded);
    await fixture.whenStable();
    return fixture;
  }

  const input = (element: HTMLElement, name: string) =>
    element.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;

  /** The `<mat-error>` of the form field that holds the `name` input, if any. */
  const fieldError = (element: HTMLElement, name: string) =>
    input(element, name).closest('mat-form-field')?.querySelector('mat-error')?.textContent?.trim();

  const cardTitles = (element: HTMLElement) =>
    Array.from(element.querySelectorAll('app-topic-card h2'), (h) => h.textContent?.trim());

  async function type(fixture: ComponentFixture<Profile>, name: string, value: string) {
    const field = input(fixture.nativeElement, name);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit(fixture: ComponentFixture<Profile>) {
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button[type="submit"]')!
      .click();
    await fixture.whenStable();
  }

  afterEach(() => {
    httpTesting.verify();
    show.mockReset();
  });

  it('shows the title and pre-fills the form from GET /api/users/me', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent?.trim()).toBe('Profil utilisateur');
    expect(input(element, 'username').value).toBe('alice');
    expect(input(element, 'email').value).toBe('alice@mdd.fr');
    expect(input(element, 'password').value).toBe('');
  });

  it.each([
    ['the API message', { status: 500, message: 'Erreur interne' }, 'Erreur interne'],
    ['a fallback without body', null, 'Impossible de charger le profil'],
  ])('notifies a failed load with %s and leaves the form empty', async (_, body, expected) => {
    const fixture = create();
    httpTesting
      .expectOne({ method: 'GET', url: '/api/users/me' })
      .flush(body, { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith(expected);
    expect(input(fixture.nativeElement, 'username').value).toBe('');
    expect(cardTitles(fixture.nativeElement)).toEqual([]);
  });

  it('leaves a 401 on load to the interceptor', async () => {
    const fixture = create();
    httpTesting
      .expectOne({ method: 'GET', url: '/api/users/me' })
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(show).not.toHaveBeenCalled();
  });

  it('sends a null password when the field is left empty', async () => {
    const fixture = await render();
    await type(fixture, 'username', 'alice2');

    await submit(fixture);

    const req = httpTesting.expectOne({ method: 'PUT', url: '/api/users/me' });
    expect(req.request.body).toEqual({ username: 'alice2', email: 'alice@mdd.fr', password: null });
    req.flush({ id: 1, email: 'alice@mdd.fr', username: 'alice2' });
  });

  it('refuses an invalid password without calling the API', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    await type(fixture, 'password', 'password');

    await submit(fixture);

    httpTesting.expectNone({ method: 'PUT' });
    expect(fieldError(element, 'password')).toContain('une majuscule');
  });

  it.each([
    ['empty', 'username', '', "Le nom d'utilisateur est obligatoire"],
    ['too short', 'username', 'ab', "Le nom d'utilisateur doit contenir entre 3 et 50 caractères"],
    [
      'too long',
      'username',
      'a'.repeat(51),
      "Le nom d'utilisateur doit contenir entre 3 et 50 caractères",
    ],
    [
      'non-whitelisted',
      'username',
      'élise dupont',
      "Le nom d'utilisateur ne peut contenir que des lettres non accentuées, des chiffres, le " +
        'point, le tiret et le tiret bas',
    ],
    [
      'too long (74 UTF-8 bytes)',
      'password',
      'Aa1!' + 'é'.repeat(35),
      'Le mot de passe ne doit pas dépasser 72 octets (un caractère accentué en compte 2)',
    ],
    ['empty', 'email', '', "L'adresse e-mail est obligatoire"],
    ['malformed', 'email', 'alice', "L'adresse e-mail doit être valide"],
    [
      'too long',
      'email',
      `${'a'.repeat(64)}@${`${'b'.repeat(63)}.`.repeat(3)}fr`,
      "L'adresse e-mail ne doit pas dépasser 255 caractères",
    ],
  ])('refuses a %s %s', async (_, name, value, message) => {
    const fixture = await render();
    await type(fixture, name, value);

    await submit(fixture);

    httpTesting.expectNone({ method: 'PUT' });
    expect(fieldError(fixture.nativeElement, name)).toBe(message);
  });

  it('sends a valid new password, then confirms and empties the password field', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    await type(fixture, 'password', 'Password1!');

    await submit(fixture);
    const req = httpTesting.expectOne({ method: 'PUT', url: '/api/users/me' });
    expect(req.request.body).toEqual({
      username: 'alice',
      email: 'alice@mdd.fr',
      password: 'Password1!',
    });
    req.flush({ id: 1, email: 'alice@mdd.fr', username: 'alice' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith('Profil mis à jour');
    expect(input(element, 'password').value).toBe('');
    expect(input(element, 'username').value).toBe('alice');
  });

  it('notifies the API message of a 409', async () => {
    const fixture = await render();
    await type(fixture, 'email', 'bob@mdd.fr');

    await submit(fixture);
    httpTesting
      .expectOne({ method: 'PUT', url: '/api/users/me' })
      .flush(
        { status: 409, message: 'Cet email est déjà utilisé', fieldErrors: null },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith('Cet email est déjà utilisé');
    expect(input(fixture.nativeElement, 'email').value).toBe('bob@mdd.fr');
  });

  it('shows fieldErrors under the matching field', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    await submit(fixture);
    httpTesting.expectOne({ method: 'PUT', url: '/api/users/me' }).flush(
      {
        status: 400,
        message: 'Requête invalide',
        fieldErrors: { email: "L'adresse e-mail doit être valide" },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(fieldError(element, 'email')).toBe("L'adresse e-mail doit être valide");
    expect(fieldError(element, 'username')).toBeUndefined();
    expect(show).not.toHaveBeenCalled();
  });

  it('shows fieldErrors under the username and the password', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    await submit(fixture);
    httpTesting.expectOne({ method: 'PUT', url: '/api/users/me' }).flush(
      {
        status: 400,
        message: 'Requête invalide',
        fieldErrors: {
          username: "Le nom d'utilisateur doit contenir entre 3 et 50 caractères",
          password: 'Le mot de passe ne doit pas dépasser 72 caractères',
        },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(fieldError(element, 'username')).toBe(
      "Le nom d'utilisateur doit contenir entre 3 et 50 caractères",
    );
    expect(fieldError(element, 'password')).toBe(
      'Le mot de passe ne doit pas dépasser 72 caractères',
    );
    expect(fieldError(element, 'email')).toBeUndefined();
    expect(show).not.toHaveBeenCalled();
  });

  it('lists the subscriptions with a "Se désabonner" button', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    expect(cardTitles(element)).toEqual(['Angular', 'Spring']);
    expect(
      Array.from(element.querySelectorAll('app-topic-card button'), (b) => b.textContent?.trim()),
    ).toEqual(['Se désabonner', 'Se désabonner']);
  });

  it('removes the card once the unsubscription succeeds', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('app-topic-card button')!.click();
    httpTesting
      .expectOne({ method: 'DELETE', url: '/api/users/me/subscriptions/2' })
      .flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(cardTitles(element)).toEqual(['Spring']);
  });

  it('keeps the card and notifies when the unsubscription fails', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('app-topic-card button')!.click();
    httpTesting
      .expectOne('/api/users/me/subscriptions/2')
      .flush(null, { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(cardTitles(element)).toEqual(['Angular', 'Spring']);
    expect(show).toHaveBeenCalledExactlyOnceWith('Le désabonnement a échoué');
  });

  it('shows a short message when there is no subscription', async () => {
    const element = (await render({ ...profile, subscriptions: [] })).nativeElement as HTMLElement;

    expect(element.querySelector('app-topic-card')).toBeNull();
    expect(element.querySelector('.profile-empty')?.textContent?.trim()).toBe(
      "Vous n'êtes abonné à aucun thème.",
    );
  });
});
