import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListItem, MatNavList } from '@angular/material/list';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, isActive } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Application frame: toolbar, navigation and the routed page. Navigation links only appear
 * once logged in; on a handset they move into a side menu opened by a burger button. The
 * home page (`/`) has no toolbar: its own logo already fills the screen.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbar,
    MatButton,
    MatIconButton,
    MatIcon,
    MatSidenavContainer,
    MatSidenav,
    MatSidenavContent,
    MatNavList,
    MatListItem,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly auth = inject(AuthService);

  protected readonly isAuthenticated = this.auth.isAuthenticated;
  private readonly router = inject(Router);
  private readonly homeActive = isActive('/', this.router, {
    paths: 'exact',
    queryParams: 'ignored',
  });
  /**
   * True once a navigation has landed on `/`. Before the first navigation ends, `isActive`
   * reads an empty URL as `/`: the toolbar stays, as on any other page, until that is known.
   */
  protected readonly isHome = computed(
    () => this.router.lastSuccessfulNavigation() !== null && this.homeActive(),
  );
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  private readonly menuRequested = signal(false);
  /** The side menu only exists for a logged-in user on a handset. */
  protected readonly menuOpen = computed(
    () => this.menuRequested() && this.isHandset() && this.isAuthenticated(),
  );

  protected openMenu(): void {
    this.menuRequested.set(true);
  }

  protected closeMenu(): void {
    this.menuRequested.set(false);
  }

  protected logout(): void {
    this.closeMenu();
    this.auth.logout();
  }
}
