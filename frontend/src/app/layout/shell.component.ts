import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { TokenStore } from '../core/auth/token.store';
import { CartStore } from '../features/orders/cart.store';

/**
 * The application frame: navigation, identity, basket.
 *
 * <p>Links are hidden according to the caller's roles. That is presentation, not protection - the
 * route guard stops navigation and the API refuses the call. Hiding a link the user cannot use is
 * simply a better screen than one that leads to a 403.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <a class="topbar__brand" routerLink="/catalog">Logistics</a>

      <nav class="topbar__nav">
        <a routerLink="/catalog" routerLinkActive="active">Catalogue</a>

        @if (tokens.isAuthenticated()) {
          <a routerLink="/orders" routerLinkActive="active">Orders</a>

          @if (isWarehouseStaff()) {
            <a routerLink="/inventory" routerLinkActive="active">Stock</a>
          }
        }
      </nav>

      <div class="topbar__right">
        @if (cart.count() > 0) {
          <a class="topbar__cart" routerLink="/orders/new">
            Basket <span class="topbar__badge">{{ cart.count() }}</span>
          </a>
        }

        @if (tokens.user(); as user) {
          <span class="topbar__user" [title]="user.roles.join(', ')">{{ user.email }}</span>
          <button type="button" (click)="auth.logout()">Sign out</button>
        } @else {
          <a routerLink="/login">Sign in</a>
        }
      </div>
    </header>

    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .topbar {
      display: flex; align-items: center; gap: 1.5rem;
      padding: 0.75rem 1.25rem; background: #1f4667; color: #fff;
    }
    .topbar__brand { color: #fff; font-weight: 700; text-decoration: none; font-size: 1.05rem; }
    .topbar__nav { display: flex; gap: 1rem; flex: 1; }
    .topbar__nav a {
      color: #cfe0ee; text-decoration: none; font-size: 0.92rem;
      padding-bottom: 2px; border-bottom: 2px solid transparent;
    }
    .topbar__nav a.active { color: #fff; border-bottom-color: #fff; }
    .topbar__right { display: flex; align-items: center; gap: 0.9rem; font-size: 0.88rem; }
    .topbar__right a { color: #cfe0ee; text-decoration: none; }
    .topbar__cart { display: flex; align-items: center; gap: 0.35rem; }
    .topbar__badge {
      background: #fff; color: #1f4667; border-radius: 10px;
      padding: 0 0.4rem; font-size: 0.75rem; font-weight: 700;
    }
    .topbar__user { opacity: 0.85; }
    .topbar__right button {
      background: transparent; border: 1px solid #4a7ba3; color: #fff;
      padding: 0.3rem 0.7rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem;
    }
    main { min-height: calc(100vh - 3.5rem); background: #f7f9fb; }
  `,
})
export class ShellComponent {
  protected readonly tokens = inject(TokenStore);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartStore);

  protected isWarehouseStaff(): boolean {
    return this.tokens.hasAnyRole(['ROLE_WAREHOUSE_MANAGER', 'ROLE_ADMIN']);
  }
}
