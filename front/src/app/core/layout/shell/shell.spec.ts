import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { Shell } from './shell';

@Component({ template: '' })
class Page {}

describe('Shell', () => {
  const logout = vi.fn();

  async function render(options: { isAuthenticated: boolean; isHandset: boolean; url?: string }) {
    const state: BreakpointState = { matches: options.isHandset, breakpoints: {} };
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([{ path: '**', component: Page }]),
        {
          provide: AuthService,
          useValue: { isAuthenticated: signal(options.isAuthenticated), logout },
        },
        { provide: BreakpointObserver, useValue: { observe: () => of(state) } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    await TestBed.inject(Router).navigateByUrl(options.url ?? '/login');
    await fixture.whenStable();
    return fixture;
  }

  const toolbarText = (element: HTMLElement) =>
    element.querySelector('mat-toolbar')?.textContent?.replace(/\s+/g, ' ').trim();

  afterEach(() => logout.mockReset());

  it('shows only the logo on public pages', async () => {
    const element = (await render({ isAuthenticated: false, isHandset: false }))
      .nativeElement as HTMLElement;

    expect(toolbarText(element)).toBe('MDD');
    expect(element.querySelector('[aria-label="Ouvrir le menu"]')).toBeNull();
  });

  it.each(['/', '/?page=2'])('has no toolbar on the home page (%s)', async (url) => {
    const element = (await render({ isAuthenticated: false, isHandset: false, url }))
      .nativeElement as HTMLElement;

    expect(element.querySelector('mat-toolbar')).toBeNull();
    expect(element.querySelector('main router-outlet')).not.toBeNull();
  });

  it('keeps the toolbar until a navigation has ended', async () => {
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: signal(false), logout } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();

    expect(toolbarText(fixture.nativeElement as HTMLElement)).toBe('MDD');
  });

  it.each(['/login', '/register'])('shows the toolbar on %s', async (url) => {
    const element = (await render({ isAuthenticated: false, isHandset: false, url }))
      .nativeElement as HTMLElement;

    expect(toolbarText(element)).toBe('MDD');
  });

  it('shows the navigation links on desktop once logged in', async () => {
    const element = (await render({ isAuthenticated: true, isHandset: false }))
      .nativeElement as HTMLElement;
    const nav = element.querySelector('nav[aria-label="Navigation principale"]');

    expect(Array.from(nav?.children ?? [], (child) => child.textContent?.trim())).toEqual([
      'Se déconnecter',
      'Articles',
      'Thèmes',
      'account_circle',
    ]);
    expect(nav?.querySelector('a[href="/feed"]')).not.toBeNull();
    expect(nav?.querySelector('a[href="/topics"]')).not.toBeNull();
    expect(nav?.querySelector('a[href="/profile"][aria-label="Profil"]')).not.toBeNull();
  });

  it('logs out from the toolbar', async () => {
    const element = (await render({ isAuthenticated: true, isHandset: false }))
      .nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('nav .shell-logout')?.click();

    expect(logout).toHaveBeenCalledOnce();
  });

  it('replaces the links with a burger menu on a handset', async () => {
    const element = (await render({ isAuthenticated: true, isHandset: true }))
      .nativeElement as HTMLElement;

    expect(element.querySelector('nav[aria-label="Navigation principale"]')).toBeNull();
    expect(element.querySelector('[aria-label="Ouvrir le menu"]')).not.toBeNull();
  });

  it('opens the side menu with the same links from the burger', async () => {
    const fixture = await render({ isAuthenticated: true, isHandset: true });
    const element = fixture.nativeElement as HTMLElement;
    const sidenav = element.querySelector('mat-sidenav');
    expect(sidenav?.classList).not.toContain('mat-drawer-opened');

    element.querySelector<HTMLButtonElement>('[aria-label="Ouvrir le menu"]')?.click();
    await fixture.whenStable();

    expect(sidenav?.classList).toContain('mat-drawer-opened');
    expect(sidenav?.querySelector('a[href="/feed"]')?.textContent?.trim()).toBe('Articles');
    expect(sidenav?.querySelector('a[href="/topics"]')?.textContent?.trim()).toBe('Thèmes');
    expect(sidenav?.querySelector('a[href="/profile"][aria-label="Profil"]')).not.toBeNull();

    sidenav?.querySelector<HTMLButtonElement>('.shell-logout')?.click();
    await fixture.whenStable();

    expect(logout).toHaveBeenCalledOnce();
    expect(sidenav?.classList).not.toContain('mat-drawer-opened');
  });

  it.each(['/feed', '/topics', '/profile'])(
    'closes the side menu when its %s link is followed',
    async (href) => {
      const fixture = await render({ isAuthenticated: true, isHandset: true, url: '/posts/1' });
      const element = fixture.nativeElement as HTMLElement;
      const sidenav = element.querySelector('mat-sidenav');
      element.querySelector<HTMLButtonElement>('[aria-label="Ouvrir le menu"]')?.click();
      await fixture.whenStable();
      expect(sidenav?.classList).toContain('mat-drawer-opened');

      sidenav?.querySelector<HTMLAnchorElement>(`a[href="${href}"]`)?.click();
      await fixture.whenStable();

      expect(TestBed.inject(Router).url).toBe(href);
      expect(sidenav?.classList).not.toContain('mat-drawer-opened');
    },
  );

  // Positioned in the container, the closed menu (translated just past the right edge)
  // widened the container's scroll width by its own width.
  it('fixes the side menu to the viewport', async () => {
    const element = (await render({ isAuthenticated: true, isHandset: true }))
      .nativeElement as HTMLElement;

    expect(element.querySelector('mat-sidenav')?.classList).toContain('mat-sidenav-fixed');
  });
});
