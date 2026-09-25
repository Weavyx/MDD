import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/notification/notification.service';
import { errorTexts, typeInto } from '../testing';
import { Register } from './register';

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let element: HTMLElement;
  let httpTesting: HttpTestingController;
  const login = vi.fn();
  const show = vi.fn();

  const PASSWORD_RULE =
    'Le mot de passe doit contenir 8 à 72 caractères, dont une majuscule, une minuscule, un ' +
    'chiffre et un caractère spécial';
  const PASSWORD_BYTES =
    'Le mot de passe ne doit pas dépasser 72 octets (un caractère accentué en compte 2)';
  const USERNAME_CHARACTERS =
    "Le nom d'utilisateur ne peut contenir que des lettres non accentuées, des chiffres, le " +
    'point, le tiret et le tiret bas';

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { login } },
        { provide: NotificationService, useValue: { show } },
      ],
    });
    fixture = TestBed.createComponent(Register);
    element = fixture.nativeElement as HTMLElement;
    httpTesting = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
    vi.restoreAllMocks();
    login.mockReset();
    show.mockReset();
  });

  const submitButton = () => element.querySelector<HTMLButtonElement>('button[type="submit"]')!;

  async function fill(values: { username?: string; email?: string; password?: string }) {
    typeInto(element, 'username', values.username ?? 'alice');
    typeInto(element, 'email', values.email ?? 'alice@mdd.fr');
    typeInto(element, 'password', values.password ?? 'Password1!');
    await fixture.whenStable();
  }

  async function submit() {
    submitButton().click();
    await fixture.whenStable();
  }

  it('shows the title, the three fields, the button and a way back home', () => {
    expect(element.querySelector('h1')?.textContent).toBe('Inscription');
    expect(Array.from(element.querySelectorAll('mat-label'), (l) => l.textContent)).toEqual([
      "Nom d'utilisateur",
      'Adresse e-mail',
      'Mot de passe',
    ]);
    expect(submitButton().textContent?.trim()).toBe("S'inscrire");
    expect(element.querySelector('a[href="/"]')?.getAttribute('aria-label')).toBe(
      "Retour à l'accueil",
    );
  });

  it('flags the three required fields and sends nothing', async () => {
    await submit();

    expect(errorTexts(element)).toEqual([
      "Le nom d'utilisateur est obligatoire",
      "L'adresse e-mail est obligatoire",
      'Le mot de passe est obligatoire',
    ]);
    httpTesting.expectNone('/api/auth/register');
  });

  it.each([
    [
      'a username of 2 characters',
      { username: 'ab' },
      "Le nom d'utilisateur doit contenir entre 3 et 50 caractères",
    ],
    [
      'a username of 51 characters',
      { username: 'a'.repeat(51) },
      "Le nom d'utilisateur doit contenir entre 3 et 50 caractères",
    ],
    ['a malformed email', { email: 'alice' }, "L'adresse e-mail doit être valide"],
    [
      'an email of 256 characters',
      { email: 'a'.repeat(249) + '@mdd.fr' },
      "L'adresse e-mail ne doit pas dépasser 255 caractères",
    ],
    ['a password without a special character', { password: 'Password12' }, PASSWORD_RULE],
    ['a password of 7 characters', { password: 'Pass1!a' }, PASSWORD_RULE],
    ['a password of 74 UTF-8 bytes', { password: 'Aa1!' + 'é'.repeat(35) }, PASSWORD_BYTES],
    ['a username with a space', { username: 'alice dupont' }, USERNAME_CHARACTERS],
    ['a username with an accented letter', { username: 'élise' }, USERNAME_CHARACTERS],
  ])('rejects %s', async (_, values, message) => {
    await fill(values);
    await submit();

    expect(errorTexts(element)).toEqual([message]);
    httpTesting.expectNone('/api/auth/register');
  });

  it('shows the password rule without stray whitespace', async () => {
    await fill({ password: 'Password12' });
    await submit();

    const password = element.querySelectorAll('mat-form-field')[2];
    expect(password.querySelector('mat-error')?.textContent).toBe(PASSWORD_RULE);
  });

  it('shows the byte limit of the password in full', async () => {
    await fill({ password: 'Aa1!' + 'é'.repeat(35) });
    await submit();

    const password = element.querySelectorAll('mat-form-field')[2];
    expect(password.querySelector('mat-error')?.textContent).toBe(PASSWORD_BYTES);
  });

  it.each([
    ['a password of 72 UTF-8 bytes with accents', { password: 'Aa1!' + 'é'.repeat(34) }],
    ['a username made of every allowed character class', { username: 'Al.ice_9-B' }],
  ])('accepts %s', async (_, values) => {
    await fill(values);
    await submit();

    expect(errorTexts(element)).toEqual([]);
    httpTesting.expectOne('/api/auth/register');
  });

  it.each([3, 50])('accepts a username of %i characters', async (length) => {
    await fill({ username: 'a'.repeat(length) });
    await submit();

    expect(errorTexts(element)).toEqual([]);
    httpTesting.expectOne('/api/auth/register');
  });

  it('registers, logs in with the returned token and opens the feed', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fill({});
    await submit();

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/auth/register' });
    expect(req.request.body).toEqual({
      username: 'alice',
      email: 'alice@mdd.fr',
      password: 'Password1!',
    });
    expect(submitButton().disabled).toBe(true);

    req.flush({ token: 'jwt' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(login).toHaveBeenCalledExactlyOnceWith('jwt');
    expect(navigate).toHaveBeenCalledExactlyOnceWith('/feed');
  });

  it.each(['Cet email est déjà utilisé', "Ce nom d'utilisateur est déjà utilisé"])(
    'shows the 409 message "%s" and lets the user try again',
    async (message) => {
      await fill({});
      await submit();

      httpTesting
        .expectOne('/api/auth/register')
        .flush(
          { status: 409, error: 'Conflict', message, fieldErrors: null },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();

      expect(element.querySelector('[role="alert"]')?.textContent).toBe(message);
      expect(submitButton().disabled).toBe(false);
      expect(login).not.toHaveBeenCalled();
      expect(show).not.toHaveBeenCalled();
    },
  );

  it('shows each fieldErrors message under its field', async () => {
    await fill({});
    await submit();

    httpTesting.expectOne('/api/auth/register').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'Requête invalide',
        fieldErrors: {
          username: "Le nom d'utilisateur est obligatoire",
          email: "L'adresse e-mail doit être valide",
        },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    const fields = Array.from(element.querySelectorAll('mat-form-field'), (f) =>
      errorTexts(f as HTMLElement),
    );
    expect(fields).toEqual([
      ["Le nom d'utilisateur est obligatoire"],
      ["L'adresse e-mail doit être valide"],
      [],
    ]);
    expect(show).not.toHaveBeenCalled();
  });

  it('shows fieldErrors messages under the username and the password', async () => {
    await fill({});
    await submit();

    httpTesting.expectOne('/api/auth/register').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'Requête invalide',
        fieldErrors: {
          username: "Le nom d'utilisateur doit contenir entre 3 et 50 caractères",
          password: 'Le mot de passe ne doit pas dépasser 72 caractères',
        },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    const fields = Array.from(element.querySelectorAll('mat-form-field'), (f) =>
      errorTexts(f as HTMLElement),
    );
    expect(fields).toEqual([
      ["Le nom d'utilisateur doit contenir entre 3 et 50 caractères"],
      [],
      ['Le mot de passe ne doit pas dépasser 72 caractères'],
    ]);
    expect(show).not.toHaveBeenCalled();
  });

  it('clears a fieldErrors message once the field is edited', async () => {
    await fill({});
    await submit();
    httpTesting.expectOne('/api/auth/register').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'Requête invalide',
        fieldErrors: { email: "L'adresse e-mail doit être valide" },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    typeInto(element, 'email', 'bob@mdd.fr');
    await fixture.whenStable();

    expect(errorTexts(element)).toEqual([]);
  });

  it.each([
    ['the API message', { status: 500, message: 'Erreur interne' }, 'Erreur interne'],
    ['a fallback without body', null, 'Une erreur est survenue, veuillez réessayer.'],
  ])('notifies any other error with %s', async (_, body, expected) => {
    await fill({});
    await submit();

    httpTesting
      .expectOne('/api/auth/register')
      .flush(body, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith(expected);
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(submitButton().disabled).toBe(false);
  });
});
