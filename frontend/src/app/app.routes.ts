import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { ShellComponent } from './layout/shell.component';

/**
 * The route table, and with it the access policy.
 *
 * <p>Every feature is lazy-loaded with {@code loadComponent}: the warehouse dashboard is not
 * downloaded by a customer who will never open it, and the catalogue renders without waiting for
 * code no one has asked for.
 *
 * <p>Guards compose deliberately - {@code authGuard} first, then {@code roleGuard} - so an
 * anonymous visitor is sent to sign in rather than being told they lack a role they could not
 * possibly hold yet.
 */
export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'catalog' },

      {
        path: 'login',
        title: 'Sign in',
        loadComponent: () =>
          import('./features/auth/login.component').then((m) => m.LoginComponent),
      },

      {
        // Public: a visitor browses the catalogue before signing in, which is why
        // catalog-service allows anonymous reads.
        path: 'catalog',
        title: 'Catalogue',
        loadComponent: () =>
          import('./features/catalog/product-list.component').then(
            (m) => m.ProductListComponent,
          ),
      },

      {
        path: 'orders/new',
        title: 'Place an order',
        canActivate: [authGuard, roleGuard('ROLE_CLIENT', 'ROLE_ADMIN')],
        loadComponent: () =>
          import('./features/orders/order-create.component').then(
            (m) => m.OrderCreateComponent,
          ),
      },
      {
        path: 'orders',
        title: 'Orders',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/orders/order-list.component').then((m) => m.OrderListComponent),
      },
      {
        path: 'orders/:id',
        title: 'Order',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/orders/order-detail.component').then(
            (m) => m.OrderDetailComponent,
          ),
      },

      {
        path: 'inventory',
        title: 'Warehouse stock',
        canActivate: [authGuard, roleGuard('ROLE_WAREHOUSE_MANAGER', 'ROLE_ADMIN')],
        loadComponent: () =>
          import('./features/inventory/stock-dashboard.component').then(
            (m) => m.StockDashboardComponent,
          ),
      },

      {
        path: 'forbidden',
        title: 'Not available',
        loadComponent: () =>
          import('./layout/forbidden.component').then((m) => m.ForbiddenComponent),
      },

      { path: '**', redirectTo: 'catalog' },
    ],
  },
];
