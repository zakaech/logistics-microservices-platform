import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { TokenStore } from '../core/auth/token.store';
import { CartStore } from '../features/orders/cart.store';
import { IconComponent } from '../shared/components/icon.component';
import { ROLE_LABELS, labelFor } from '../shared/labels';

/**
 * Le cadre de l'application : navigation latérale, barre supérieure, identité, panier.
 *
 * <p>Les liens sont masqués selon les rôles de l'appelant. C'est de la présentation, pas de la
 * protection : le garde de route bloque la navigation et l'API refuse l'appel. Masquer un lien
 * inutilisable est simplement un meilleur écran qu'un lien menant à un 403.
 *
 * <p>La barre latérale est le repère de navigation sur écran large ; sous 900 px elle devient un
 * tiroir, parce qu'une colonne fixe de 15 rem ne laisse pas assez de place aux tableaux.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <div class="shell" [class.shell--auth-only]="!tokens.isAuthenticated()">
      <a class="skip" href="#contenu">Aller au contenu</a>

      <aside class="sidebar" [class.sidebar--open]="drawerOpen()" id="navigation">
        <div class="sidebar__brand">
          <span class="sidebar__mark" aria-hidden="true">
            <app-icon name="package" [size]="20" />
          </span>
          <span class="sidebar__wordmark">
            <strong>Logistique</strong>
            <small>Plateforme multi-entrepôts</small>
          </span>
        </div>

        <nav class="sidebar__nav" aria-label="Navigation principale">
          @if (tokens.isAuthenticated()) {
            <a routerLink="/dashboard" routerLinkActive="is-active" (click)="closeDrawer()">
              <app-icon name="dashboard" />Tableau de bord
            </a>
          }

          <a routerLink="/catalog" routerLinkActive="is-active" (click)="closeDrawer()">
            <app-icon name="catalog" />Catalogue
          </a>

          @if (tokens.isAuthenticated()) {
            <a routerLink="/orders" routerLinkActive="is-active" (click)="closeDrawer()">
              <app-icon name="orders" />Commandes
            </a>

            @if (isWarehouseStaff()) {
              <a routerLink="/inventory" routerLinkActive="is-active" (click)="closeDrawer()">
                <app-icon name="stock" />Stocks
              </a>
            }
          }
        </nav>

        <div class="sidebar__foot">
          @if (tokens.user(); as user) {
            <div class="sidebar__account">
              <span class="sidebar__avatar" aria-hidden="true">{{ initials(user.email) }}</span>
              <span class="sidebar__identity">
                <span class="sidebar__email" [title]="user.email">{{ user.email }}</span>
                <span class="sidebar__role">{{ primaryRole(user.roles) }}</span>
              </span>
            </div>
          }
        </div>
      </aside>

      @if (drawerOpen()) {
        <button
          type="button"
          class="scrim"
          aria-label="Fermer la navigation"
          (click)="closeDrawer()"
        ></button>
      }

      <div class="frame">
        <header class="topbar">
          <button
            type="button"
            class="topbar__burger"
            [attr.aria-expanded]="drawerOpen()"
            aria-controls="navigation"
            aria-label="Ouvrir la navigation"
            (click)="toggleDrawer()"
          >
            <app-icon [name]="drawerOpen() ? 'close' : 'menu'" [size]="20" />
          </button>

          <h1 class="topbar__title">{{ pageTitle() }}</h1>

          <div class="topbar__actions">
            @if (cart.count() > 0) {
              <a class="topbar__cart" routerLink="/orders/new">
                <app-icon name="cart" />
                <span class="topbar__cart-text">Panier</span>
                <span class="topbar__badge">{{ cart.count() }}</span>
              </a>
            }

            @if (tokens.isAuthenticated()) {
              <button type="button" class="btn btn--secondary btn--sm" (click)="auth.logout()">
                <app-icon name="logout" [size]="16" />Déconnexion
              </button>
            } @else {
              <a class="btn btn--primary btn--sm" routerLink="/login">
                <app-icon name="user" [size]="16" />Connexion
              </a>
            }
          </div>
        </header>

        <main id="contenu" class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .shell {
      display: grid;
      grid-template-columns: var(--sidebar-w) 1fr;
      min-height: 100vh;
    }

    .skip {
      position: absolute;
      left: -999px;
      top: var(--sp-2);
      z-index: 60;
      background: var(--c-surface);
      padding: var(--sp-2) var(--sp-3);
      border-radius: var(--radius-sm);
      box-shadow: var(--shadow);
    }
    .skip:focus {
      left: var(--sp-2);
    }

    /* --- Barre latérale --------------------------------------------------- */
    .sidebar {
      grid-column: 1;
      display: flex;
      flex-direction: column;
      background: var(--c-sidebar);
      color: var(--c-text-on-sidebar);
      position: sticky;
      top: 0;
      height: 100vh;
    }

    .sidebar__brand {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      padding: var(--sp-4);
      height: var(--topbar-h);
      border-bottom: 1px solid rgb(255 255 255 / 8%);
    }
    .sidebar__mark {
      display: grid;
      place-items: center;
      width: 2rem;
      height: 2rem;
      border-radius: var(--radius-sm);
      background: rgb(255 255 255 / 10%);
      color: #fff;
    }
    .sidebar__wordmark {
      display: flex;
      flex-direction: column;
      line-height: 1.15;
      overflow: hidden;
    }
    .sidebar__wordmark strong {
      color: #fff;
      font-size: 0.95rem;
      font-weight: var(--fw-semibold);
    }
    .sidebar__wordmark small {
      font-size: 0.68rem;
      color: var(--c-text-on-sidebar);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .sidebar__nav {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: var(--sp-3) var(--sp-2);
      flex: 1;
      overflow-y: auto;
    }
    .sidebar__nav a {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      padding: 0.55rem var(--sp-3);
      border-radius: var(--radius-sm);
      color: var(--c-text-on-sidebar);
      font-size: var(--fs-body);
      font-weight: var(--fw-medium);
      text-decoration: none;
      transition: background var(--transition), color var(--transition);
    }
    .sidebar__nav a:hover {
      background: var(--c-sidebar-hover);
      color: #fff;
      text-decoration: none;
    }
    .sidebar__nav a.is-active {
      background: var(--c-sidebar-hover);
      color: #fff;
      box-shadow: inset 3px 0 0 #6ea8d8;
    }

    .sidebar__foot {
      padding: var(--sp-3);
      border-top: 1px solid rgb(255 255 255 / 8%);
    }
    .sidebar__account {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      min-width: 0;
    }
    .sidebar__avatar {
      display: grid;
      place-items: center;
      width: 2rem;
      height: 2rem;
      flex: none;
      border-radius: 50%;
      background: rgb(255 255 255 / 12%);
      color: #fff;
      font-size: 0.72rem;
      font-weight: var(--fw-semibold);
      letter-spacing: 0.03em;
    }
    .sidebar__identity {
      display: flex;
      flex-direction: column;
      min-width: 0;
      line-height: 1.25;
    }
    .sidebar__email {
      color: #fff;
      font-size: 0.78rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .sidebar__role {
      font-size: 0.7rem;
      color: var(--c-text-on-sidebar);
    }

    /* --- Cadre de droite -------------------------------------------------- */
    .frame {
      grid-column: 2;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .topbar {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      height: var(--topbar-h);
      padding: 0 var(--sp-5);
      background: var(--c-surface);
      border-bottom: 1px solid var(--c-border);
      position: sticky;
      top: 0;
      z-index: 20;
    }
    .topbar__burger {
      display: none;
      align-items: center;
      justify-content: center;
      width: 2.25rem;
      height: 2.25rem;
      border: 1px solid var(--c-border-strong);
      border-radius: var(--radius-sm);
      background: var(--c-surface);
      color: var(--c-text);
      cursor: pointer;
    }
    .topbar__title {
      font-size: var(--fs-subtitle);
      font-weight: var(--fw-semibold);
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .topbar__actions {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
    }
    .topbar__cart {
      display: inline-flex;
      align-items: center;
      gap: var(--sp-2);
      padding: 0.35rem 0.7rem;
      border: 1px solid var(--c-primary-border);
      border-radius: var(--radius-sm);
      background: var(--c-primary-soft);
      color: var(--c-primary);
      font-size: var(--fs-small);
      font-weight: var(--fw-medium);
      text-decoration: none;
    }
    .topbar__cart:hover {
      text-decoration: none;
      background: #dbe8f3;
    }
    .topbar__badge {
      display: inline-grid;
      place-items: center;
      min-width: 1.15rem;
      height: 1.15rem;
      padding: 0 0.3rem;
      border-radius: var(--radius-pill);
      background: var(--c-primary);
      color: #fff;
      font-size: 0.7rem;
      font-weight: var(--fw-bold);
    }

    .content {
      flex: 1;
      padding: var(--sp-5);
      min-width: 0;
    }

    .scrim {
      display: none;
      position: fixed;
      inset: 0;
      z-index: 30;
      border: 0;
      padding: 0;
      background: rgb(20 33 46 / 45%);
      cursor: pointer;
    }

    /* --- Tablette et mobile ------------------------------------------------ */
    @media (max-width: 900px) {
      .shell {
        grid-template-columns: 1fr;
      }
      .sidebar {
        position: fixed;
        z-index: 40;
        width: var(--sidebar-w);
        transform: translateX(-100%);
        transition: transform var(--transition);
      }
      .sidebar--open {
        transform: translateX(0);
      }
      .frame {
        grid-column: 1;
      }
      .topbar__burger {
        display: inline-flex;
      }
      .scrim {
        display: block;
      }
    }

    @media (max-width: 560px) {
      .topbar {
        padding: 0 var(--sp-3);
        gap: var(--sp-2);
      }
      .content {
        padding: var(--sp-4) var(--sp-3);
      }
      .topbar__cart-text {
        display: none;
      }
    }
  `,
})
export class ShellComponent {
  protected readonly tokens = inject(TokenStore);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartStore);
  private readonly router = inject(Router);

  protected readonly drawerOpen = signal(false);

  /**
   * Le titre affiché dans la barre supérieure reprend celui déjà déclaré par la route : une seule
   * source, donc l'onglet du navigateur et l'en-tête ne peuvent pas diverger.
   */
  private readonly navigation = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.deepestTitle()),
    ),
    { initialValue: '' },
  );

  protected readonly pageTitle = computed(() => this.navigation() || this.deepestTitle());

  protected isWarehouseStaff(): boolean {
    return this.tokens.hasAnyRole(['ROLE_WAREHOUSE_MANAGER', 'ROLE_ADMIN']);
  }

  protected toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  /** Deux lettres tirées de l'adresse : aucune donnée supplémentaire n'est inventée. */
  protected initials(email: string): string {
    return email.slice(0, 2).toUpperCase();
  }

  protected primaryRole(roles: string[]): string {
    const ordered = ['ROLE_ADMIN', 'ROLE_WAREHOUSE_MANAGER', 'ROLE_CLIENT', 'ROLE_SERVICE'];
    const best = ordered.find((role) => roles.includes(role)) ?? roles[0];
    return best ? labelFor(best, ROLE_LABELS) : '';
  }

  private deepestTitle(): string {
    let route = this.router.routerState.snapshot.root;
    let title = '';
    while (route) {
      title = route.title ?? title;
      route = route.firstChild as typeof route;
    }
    return title;
  }
}
