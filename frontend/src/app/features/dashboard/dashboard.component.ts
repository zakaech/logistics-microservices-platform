import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { InventoryApi } from '../../core/api/inventory.api';
import { OrderApi } from '../../core/api/order.api';
import { TokenStore } from '../../core/auth/token.store';
import { OrderSummary, ProblemDetail, Warehouse } from '../../core/models';
import { IconComponent } from '../../shared/components/icon.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';
import { OrderStatusLabelPipe } from '../../shared/pipes/label.pipe';
import { RequestState } from '../../shared/request-state';

/**
 * Le point d'entrée d'un utilisateur connecté.
 *
 * <p>Chaque chiffre affiché ici provient d'un appel réel : les compteurs sont les
 * {@code totalElements} des endpoints paginés, les entrepôts sont ceux que renvoie
 * inventory-service, et l'alerte de stock faible compte les lignes que le serveur a lui-même
 * marquées sous leur seuil. Aucune tendance, aucun pourcentage, aucune valeur composée : la
 * plateforme ne conserve pas d'historique permettant de les calculer.
 *
 * <p>Ce que voit l'utilisateur dépend de son rôle, parce que les endpoints eux-mêmes en dépendent :
 * un client n'a accès ni aux entrepôts ni aux niveaux de stock, il n'est donc pas utile de lui
 * afficher des cartes qui échoueraient en 403.
 */
@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    CurrencyPipe,
    IconComponent,
    ProblemAlertComponent,
    OrderStatusLabelPipe,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly catalog = inject(CatalogApi);
  private readonly inventory = inject(InventoryApi);
  private readonly orders = inject(OrderApi);
  protected readonly tokens = inject(TokenStore);

  protected readonly isStaff = computed(() =>
    this.tokens.hasAnyRole(['ROLE_WAREHOUSE_MANAGER', 'ROLE_ADMIN']),
  );

  protected readonly productCount = signal<number | null>(null);
  protected readonly orderCount = signal<number | null>(null);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly lowStockCount = signal<number | null>(null);

  protected readonly recent = new RequestState<OrderSummary[]>();
  protected readonly loading = signal(true);
  protected readonly error = signal<ProblemDetail | null>(null);

  /** Les entrepôts servis par la plateforme, pour la carte « couverture ». */
  protected readonly cities = computed(() =>
    this.warehouses()
      .map((w) => w.address.city)
      .filter((city, index, all) => all.indexOf(city) === index),
  );

  protected readonly referenceCount = computed(() =>
    this.warehouses().reduce((total, w) => total + (w.distinctProducts ?? 0), 0),
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.recent.start();

    // Un seul forkJoin : les appels partent ensemble plutôt qu'en cascade. Chacun retombe sur
    // null en cas d'échec, pour qu'un endpoint refusé n'efface pas tout le tableau de bord.
    forkJoin({
      products: this.catalog.searchProducts({ page: 0, size: 1 }).pipe(
        map((page) => page.totalElements),
        catchError(() => of(null)),
      ),
      orders: this.orders.list(undefined, 0, 5).pipe(catchError(() => of(null))),
      warehouses: this.isStaff()
        ? this.inventory.listWarehouses(true).pipe(
            map((page) => page.content),
            catchError(() => of([] as Warehouse[])),
          )
        : of([] as Warehouse[]),
    }).subscribe({
      next: ({ products, orders, warehouses }) => {
        this.productCount.set(products);
        this.orderCount.set(orders?.totalElements ?? null);
        this.recent.succeed(orders?.content ?? []);
        this.warehouses.set(warehouses);
        this.loading.set(false);

        if (warehouses.length > 0) {
          this.countLowStock(warehouses);
        }
      },
      error: (problem: ProblemDetail) => {
        this.loading.set(false);
        this.recent.fail(problem);
        this.error.set(problem);
      },
    });
  }

  /**
   * Le nombre de lignes sous leur seuil, entrepôt par entrepôt.
   *
   * <p>Le filtrage est fait par le serveur (`lowStock=true`) et seul {@code totalElements} est lu :
   * la page demandée est vide, on ne rapatrie donc pas des lignes pour les compter côté navigateur.
   * Il n'existe pas d'endpoint agrégé sur l'ensemble du réseau ; avec quelques entrepôts, une
   * requête par site reste acceptable.
   */
  private countLowStock(warehouses: Warehouse[]): void {
    forkJoin(
      warehouses.map((w) =>
        this.inventory.warehouseStock(w.id, true, 0, 1).pipe(
          map((page) => page.totalElements),
          catchError(() => of(0)),
        ),
      ),
    ).subscribe((counts) => {
      this.lowStockCount.set(counts.reduce((total, n) => total + n, 0));
    });
  }
}
