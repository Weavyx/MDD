import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/notification/notification.service';
import { errorTexts, typeInto } from '../testing';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let element: HTMLElement;
  let httpTesting: HttpTestingController;
  const login = vi.fn();
  const show = vi.fn();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { login } },
        { provide: NotificationService, useValue: { show } },
      ],
    });
    fixture = TestBed.createComponent(Login);
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
  const alertText = () => element.querySelector('[role="alert"]')?.textContent;

  async function fillAndSubmit(identifier = 'alice', password = 'Password1!') {
    typeInto(element, 'identifier', identifier);
    typeInto(element, 'password', password);
    submitButton().click();
    await fixture.whenStable();
  }

  it('shows the title, the two fields, the button and a way back home', () => {
    expect(element.querySelector('h1')?.textContent).toBe('Se connecter');
    expect(Array.from(element.querySelectorAll('mat-label'), (l) => l.textContent)).toEqual([
      "E-mail ou nom d'utilisateur",
      'Mot de passe',
    ]);
    expect(submitButton().textContent?.trim()).toBe('Se connecter');
    expect(element.querySelector('a[href="/"]')?.getAttribute('aria-label')).toBe(
      "Retour à l'accueil",
    );
  });

  it('flags both required fields and sends nothing', async () => {
    submitButton().click();
    await fixture.whenStable();

    expect(errorTexts(element)).toEqual([
      "L'identifiant est obligatoire",
      'Le mot de passe est obligatoire',
    ]);
    httpTesting.expectNone('/api/auth/login');
  });

  it('applies no complexity rule to the password', async () => {
    await fillAndSubmit('alice', 'a');

    expect(errorTexts(element)).toEqual([]);
    expect(httpTesting.expectOne('/api/auth/login').request.body.password).toBe('a');
  });

  it('logs in with the returned token and opens the feed', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fillAndSubmit();

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/auth/login' });
    expect(req.request.body).toEqual({ identifier: 'alice', password: 'Password1!' });
    expect(submitButton().disabled).toBe(true);

    req.flush({ token: 'jwt' });
    await fixture.whenStable();

    expect(login).toHaveBeenCalledExactlyOnceWith('jwt');
    expect(navigate).toHaveBeenCalledExactlyOnceWith('/feed');
  });

  it('turns a 401 into "Identifiants incorrects" on the page', async () => {
    await fillAndSubmit();

    httpTesting
      .expectOne('/api/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(alertText()).toBe('Identifiants incorrects');
    expect(submitButton().disabled).toBe(false);
    expect(login).not.toHaveBeenCalled();
    expect(show).not.toHaveBeenCalled();
  });

  it('hides "Identifiants incorrects" again on the next attempt', async () => {
    await fillAndSubmit();
    httpTesting
      .expectOne('/api/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    submitButton().click();
    await fixture.whenStable();

    expect(alertText()).toBeUndefined();
    httpTesting.expectOne('/api/auth/login');
  });

  it('shows a fieldErrors message under its field', async () => {
    await fillAndSubmit();

    httpTesting.expectOne('/api/auth/login').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'Requête invalide',
        fieldErrors: { identifier: "L'identifiant ne doit pas dépasser 255 caractères" },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(errorTexts(element.querySelector('mat-form-field') as HTMLElement)).toEqual([
      "L'identifiant ne doit pas dépasser 255 caractères",
    ]);
    expect(show).not.toHaveBeenCalled();
  });

  it('shows fieldErrors messages under the identifier and the password', async () => {
    await fillAndSubmit();

    httpTesting.expectOne('/api/auth/login').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'Requête invalide',
        fieldErrors: {
          identifier: "L'identifiant ne doit pas dépasser 255 caractères",
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
      ["L'identifiant ne doit pas dépasser 255 caractères"],
      ['Le mot de passe ne doit pas dépasser 72 caractères'],
    ]);
    expect(show).not.toHaveBeenCalled();
  });

  it('notifies a fieldErrors message that matches no field', async () => {
    await fillAndSubmit();

    httpTesting.expectOne('/api/auth/login').flush(
      {
        status: 400,
        error: 'Bad Request',
        message: 'Requête invalide',
        fieldErrors: { unknown: 'Champ inconnu' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(errorTexts(element)).toEqual([]);
    expect(show).toHaveBeenCalledExactlyOnceWith('Requête invalide');
  });

  it('notifies any other error', async () => {
    await fillAndSubmit();

    httpTesting
      .expectOne('/api/auth/login')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(show).toHaveBeenCalledExactlyOnceWith('Une erreur est survenue, veuillez réessayer.');
    expect(alertText()).toBeUndefined();
  });
});
