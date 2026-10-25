import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthSessionService } from '../core/auth/auth-session.service';
import { AuthModeService } from '../core/auth/auth-mode.service';

interface NavigationItem {
  label: string;
  path: string;
  permission: string;
}

@Component({
  selector: 'app-shell',
  imports: [NgOptimizedImage, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="app-frame">
      <a class="skip-link" href="#main-content" (click)="skipToContent($event)">Skip to main content</a>
      <header class="topbar">
        <button
          class="menu-toggle"
          type="button"
          aria-label="Toggle navigation"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="primary-navigation"
          #menuButton
          (click)="menuOpen.set(!menuOpen())"
        >
          <span aria-hidden="true">☰</span>
        </button>
        <a class="brand" routerLink="/overview" aria-label="Fraud Rule Engine home">
          <img
            ngSrc="branding/capitec-logo.svg"
            width="133"
            height="19"
            alt="Capitec"
            priority
          />
          <span class="brand-divider" aria-hidden="true"></span>
          <span class="product-name">Fraud Rule Engine</span>
        </a>
        <div class="user-area">
          <div class="user-copy">
            <span class="user-name">{{ displayName() }}</span>
            <span class="user-role">{{ roleLabel() }}</span>
          </div>
          <button class="sign-out" type="button" (click)="signOut()">Sign out</button>
        </div>
      </header>

      <div class="workspace">
        <nav
          id="primary-navigation"
          class="side-nav"
          [class.side-nav--open]="menuOpen()"
          aria-label="Primary navigation"
          (keydown.escape)="closeMenu()"
        >
          <div class="nav-caption">WORKSPACE</div>
          @for (item of visibleItems(); track item.path) {
            <a
              class="nav-link"
              [routerLink]="item.path"
              routerLinkActive="nav-link--active"
              [routerLinkActiveOptions]="{ exact: true }"
              (click)="closeMenu()"
              ariaCurrentWhenActive="page"
            >
              <span class="nav-indicator" aria-hidden="true"></span>
              {{ item.label }}
            </a>
          }
          <div class="nav-footnote">
            <span class="assessment-mark" aria-hidden="true"></span>
            <span>Synthetic assessment environment</span>
          </div>
        </nav>

        <main class="main-content" id="main-content" tabindex="-1">
          <router-outlet (activate)="focusContent()" />
        </main>
      </div>
    </div>
  `,
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  private readonly session = inject(AuthSessionService);
  private readonly authMode = inject(AuthModeService);

  readonly menuOpen = signal(false);
  private readonly menuButton = viewChild<ElementRef<HTMLButtonElement>>('menuButton');

  closeMenu(): void {
    if (this.menuOpen()) {
      this.menuOpen.set(false);
      this.menuButton()?.nativeElement.focus();
    }
  }

  skipToContent(event: Event): void {
    event.preventDefault();
    this.menuOpen.set(false);
    this.focusContent();
  }

  focusContent(): void {
    document.getElementById('main-content')?.focus({ preventScroll: true });
  }
  private readonly items: NavigationItem[] = [
    { label: 'Overview', path: '/overview', permission: 'REPORTS_READ' },
    { label: 'Evaluations', path: '/evaluations', permission: 'EVALUATIONS_READ' },
    { label: 'Rules', path: '/rules', permission: 'RULES_READ' },
    { label: 'Users', path: '/users', permission: 'USERS_MANAGE' },
    { label: 'My profile', path: '/profile', permission: 'OWN_PROFILE_READ' },
  ];
  readonly visibleItems = computed(() =>
    this.items.filter(
      (item) =>
        this.session.hasPermission(item.permission) &&
        (!['/users', '/profile'].includes(item.path) || this.authMode.local()),
    ),
  );
  readonly displayName = computed(() => {
    const user = this.session.user();
    return user ? `${user.name} ${user.surname}`.trim() : 'Signed-in user';
  });
  readonly roleLabel = computed(() => this.session.user()?.role.replaceAll('_', ' ') ?? '');

  signOut(): void {
    this.session.logout();
  }
}
