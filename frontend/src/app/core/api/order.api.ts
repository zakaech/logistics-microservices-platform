import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AllocationPreviewRequest,
  AllocationPreviewResponse,
  AllocationStrategy,
  CreateOrderRequest,
  Order,
  OrderStatus,
  OrderStatusHistoryEntry,
  OrderSummary,
  PagedResponse,
} from '../models';

/** Typed access to order-service, including the allocation engine. */
@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/orders`;

  /**
   * Places an order.
   *
   * <p>Neither the customer nor the total is sent: the server takes the customer from the signed
   * token and prices the order from the catalogue. Neither value is trusted from the client.
   */
  create(request: CreateOrderRequest): Observable<Order> {
    return this.http.post<Order>(this.baseUrl, request);
  }

  list(
    status?: OrderStatus,
    page = 0,
    size = 20,
  ): Observable<PagedResponse<OrderSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    // The server scopes this to the caller: a client sees their own orders, staff see all.
    return this.http.get<PagedResponse<OrderSummary>>(this.baseUrl, { params });
  }

  get(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.baseUrl}/${id}`);
  }

  /** The timeline: every transition and the reason recorded with it. */
  history(id: string): Observable<OrderStatusHistoryEntry[]> {
    return this.http.get<OrderStatusHistoryEntry[]>(`${this.baseUrl}/${id}/history`);
  }

  cancel(id: string, reason?: string): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/${id}/cancel`, { reason });
  }

  changeStatus(id: string, status: OrderStatus, reason?: string): Observable<Order> {
    return this.http.patch<Order>(`${this.baseUrl}/${id}/status`, { status, reason });
  }

  /** The strategies a caller may ask for, and which is the default. */
  strategies(): Observable<AllocationStrategy[]> {
    return this.http.get<AllocationStrategy[]>(`${this.baseUrl}/allocation-strategies`);
  }

  /** Runs the engine against live stock without creating an order or holding anything. */
  previewAllocation(
    request: AllocationPreviewRequest,
  ): Observable<AllocationPreviewResponse> {
    return this.http.post<AllocationPreviewResponse>(
      `${this.baseUrl}/allocation-preview`,
      request,
    );
  }
}
