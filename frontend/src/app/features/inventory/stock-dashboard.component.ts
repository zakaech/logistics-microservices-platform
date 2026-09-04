import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InventoryApi } from '../../core/api/inventory.api';
import {
  PagedResponse,
  ProblemDetail,
  StockItem,
  Warehouse,
} from '../../core/models';
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
  imports: [FormsModule, DatePipe, ProblemAlertComponent, LoadingBarComponent],
  templateUrl: './stock-dashboard.component.html',
  styleUrl: './stock-dashboard.component.scss',
})
export class StockDashboardComponent implements OnInit {
  private readonly inventory = inject(InventoryApi);

  protected readonly warehouses = new RequestState<Warehouse[]>();
  protected readonly stock = new RequestState<PagedResponse<StockItem>>();

  protected selectedWarehouseId = '';
  protected lowStockOnly = false;
  protected readonly page = signal(0);

  /** Alert count for the page in view; the server decides what "low" means. */
  protected readonly lowStockCount = computed(
    () => this.stock.data()?.content.filter((item) => item.lowStock).length ?? 0,
  );

  ngOnInit(): void {
    this.loadWarehouses();
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

  protected selectedWarehouse(): Warehouse | undefined {
    return this.warehouses
      .data()
      ?.find((warehouse) => warehouse.id === this.selectedWarehouseId);
  }
}
