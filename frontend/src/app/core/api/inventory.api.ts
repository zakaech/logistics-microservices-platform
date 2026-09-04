import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MovementType,
  PagedResponse,
  StockItem,
  StockMovement,
  Warehouse,
} from '../models';

/** Typed access to inventory-service. Every endpoint here requires a warehouse or admin role. */
@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1`;

  listWarehouses(active?: boolean): Observable<PagedResponse<Warehouse>> {
    let params = new HttpParams().set('page', 0).set('size', 100);
    if (active !== undefined) {
      params = params.set('active', active);
    }
    return this.http.get<PagedResponse<Warehouse>>(`${this.baseUrl}/warehouses`, {
      params,
    });
  }

  /**
   * Stock held by one warehouse.
   *
   * <p>{@code lowStock} asks the server to filter, rather than fetching everything and filtering
   * here: the threshold comparison belongs where the data is, and a warehouse can hold far more
   * lines than a page should carry.
   */
  warehouseStock(
    warehouseId: string,
    lowStockOnly = false,
    page = 0,
    size = 20,
  ): Observable<PagedResponse<StockItem>> {
    const params = new HttpParams()
      .set('lowStock', lowStockOnly)
      .set('page', page)
      .set('size', size);

    return this.http.get<PagedResponse<StockItem>>(
      `${this.baseUrl}/warehouses/${warehouseId}/stock`,
      { params },
    );
  }

  /** The append-only ledger: what actually happened to a stock level, and why. */
  movements(filters: {
    warehouseId?: string;
    productId?: string;
    type?: MovementType;
    page?: number;
    size?: number;
  }): Observable<PagedResponse<StockMovement>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20);

    if (filters.warehouseId) {
      params = params.set('warehouseId', filters.warehouseId);
    }
    if (filters.productId) {
      params = params.set('productId', filters.productId);
    }
    if (filters.type) {
      params = params.set('type', filters.type);
    }

    return this.http.get<PagedResponse<StockMovement>>(
      `${this.baseUrl}/stock/movements`,
      { params },
    );
  }
}
