import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { ShellComponent } from './layout/shell.component';

/**
 * The route table, and with it the access policy.
 *
 * <p>Two layouts, and the route tree is what separates them. The authentication screens stand on
 * their own at the top level; everything else is a child of {@link ShellComponent} and therefore
 * renders inside the application frame - sidebar, top bar, basket. A screen belongs to one group
 * or the other by where it sits in this tree, not by a condition evaluated inside a component.
 *
 * <p>That distinction matters beyond tidiness: signing in is not a place inside the application,
 * it is the door to it. Wrapping it in the frame offered a user who is not signed in a navigation
 * bar full of links they cannot follow, and a "Connexion" button on the page that already is the
 * sign-in page.
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
  // --- Authentification : hors du cadre applicatif -------------------------
  // Déclarée avant la route du shell, dont le chemin vide accepterait sinon cette URL.
  {
    path: 'login',
    title: 'Connexion',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },

  // --- Application : tout ce qui vit dans le cadre --------------------------
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'catalog' },

      {
        // Le point d'entrée d'un utilisateur connecté. Il n'agrège que des endpoints
        // existants ; ce qu'il affiche dépend du rôle, comme les endpoints eux-mêmes.
        path: 'dashboard',
        title: 'Tableau de bord',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
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
        title: 'Passer une commande',
        canActivate: [authGuard, roleGuard('ROLE_CLIENT', 'ROLE_ADMIN')],
        loadComponent: () =>
          import('./features/orders/order-create.component').then(
            (m) => m.OrderCreateComponent,
          ),
      },
      {
        path: 'orders',
        title: 'Commandes',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/orders/order-list.component').then((m) => m.OrderListComponent),
      },
      {
        path: 'orders/:id',
        title: 'Commande',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/orders/order-detail.component').then(
            (m) => m.OrderDetailComponent,
          ),
      },

      {
        path: 'inventory',
        title: 'Stock par entrepôt',
        canActivate: [authGuard, roleGuard('ROLE_WAREHOUSE_MANAGER', 'ROLE_ADMIN')],
        loadComponent: () =>
          import('./features/inventory/stock-dashboard.component').then(
            (m) => m.StockDashboardComponent,
          ),
      },

      {
        path: 'forbidden',
        title: 'Accès non autorisé',
        loadComponent: () =>
          import('./layout/forbidden.component').then((m) => m.ForbiddenComponent),
      },

      { path: '**', redirectTo: 'catalog' },
    ],
  },
];
