import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogApi } from '../../core/api/catalog.api';
import { InventoryApi } from '../../core/api/inventory.api';
import {
  PagedResponse,
  ProblemDetail,
  StockItem,
  Warehouse,
} from '../../core/models';
import { IconComponent } from '../../shared/components/icon.component';
import { LoadingBarComponent } from '../../shared/components/loading-bar.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';
import { RequestState } from '../../shared/request-state';

/**
 * Warehouse dashboard: stock levels and low-stock alerts.
 *
 * <p>The low-stock filter is applied by the server, not here. The threshold comparison belongs where
 * the data is - a warehouse holds far more lines than one page carries, so filtering a page in the
 * browser would only ever find the alerts that happened to be on it.
 *
 * <p>Three quantities are shown rather than one, because they answer different questions: what is
 * physically present, what is already promised to orders, and what a new order could still take.
 * Showing only the first is how a warehouse oversells.
 */
@Component({
  selector: 'app-stock-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe, IconComponent, ProblemAlertComponent, LoadingBarComponent],
  templateUrl: './stock-dashboard.component.html',
  styleUrl: './stock-dashboard.component.scss',
})
export class StockDashboardComponent implements OnInit {
  private readonly inventory = inject(InventoryApi);
  private readonly catalog = inject(CatalogApi);

  protected readonly warehouses = new RequestState<Warehouse[]>();
  protected readonly stock = new RequestState<PagedResponse<StockItem>>();

  protected selectedWarehouseId = '';
  protected lowStockOnly = false;
  protected readonly page = signal(0);

  /** Alert count for the page in view; the server decides what "low" means. */
  protected readonly lowStockCount = computed(
    () => this.stock.data()?.content.filter((item) => item.lowStock).length ?? 0,
  );

  /**
   * Identifiant produit -> nom lisible.
   *
   * <p>Une ligne de stock ne transporte que le {@code productId} : inventory-service ne connaît
   * pas les libellés du catalogue, et c'est bien ainsi — la frontière entre les deux services est
   * exactement là. Mais afficher `6a9b5035…` à un responsable d'entrepôt n'a aucun sens, donc le
   * nom est résolu ici, à l'affichage, par un seul appel au catalogue plutôt qu'un par ligne.
   *
   * <p>Un identifiant absent de la table reste affiché tel quel : mieux vaut une référence brute
   * qu'une case vide.
   */
  protected readonly productNames = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.loadWarehouses();
    this.loadProductNames();
  }

  protected productName(productId: string): string {
    return this.productNames()[productId] ?? productId;
  }

  protected productSku(productId: string): string | null {
    return this.productSkus()[productId] ?? null;
  }

  private readonly productSkus = signal<Record<string, string>>({});

  private loadProductNames(): void {
    this.catalog.searchProducts({ page: 0, size: 200 }).subscribe({
      // Une table de libellés manquante dégrade l'écran, elle ne le casse pas : les quantités
      // restent justes, seule la colonne produit retombe sur l'identifiant.
      next: (page) => {
        const names: Record<string, string> = {};
        const skus: Record<string, string> = {};
        for (const product of page.content) {
          names[product.id] = product.name;
          skus[product.id] = product.sku;
        }
        this.productNames.set(names);
        this.productSkus.set(skus);
      },
      error: () => undefined,
    });
  }

  protected loadWarehouses(): void {
    this.warehouses.start();
    this.inventory.listWarehouses(true).subscribe({
      next: (page) => {
        this.warehouses.succeed(page.content);
        // Pre-select the first site so the dashboard is useful on arrival rather than empty.
        if (page.content.length > 0 && !this.selectedWarehouseId) {
          this.selectedWarehouseId = page.content[0].id;
          this.loadStock();
        }
      },
      error: (problem: ProblemDetail) => this.warehouses.fail(problem),
    });
  }

  protected loadStock(resetPage = true): void {
    if (!this.selectedWarehouseId) {
      return;
    }
    if (resetPage) {
      this.page.set(0);
    }
    this.stock.start();

    this.inventory
      .warehouseStock(this.selectedWarehouseId, this.lowStockOnly, this.page(), 20)
      .subscribe({
        next: (page) => this.stock.succeed(page),
        error: (problem: ProblemDetail) => this.stock.fail(problem),
      });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.loadStock(false);
  }

  /** Raccourci depuis le bandeau d'alerte vers la vue filtrée. */
  protected showOnlyLowStock(): void {
    this.lowStockOnly = true;
    this.loadStock();
  }

  protected selectedWarehouse(): Warehouse | undefined {
    return this.warehouses
      .data()
      ?.find((warehouse) => warehouse.id === this.selectedWarehouseId);
  }
}
