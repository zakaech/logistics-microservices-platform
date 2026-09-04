import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Category,
  PagedResponse,
  Product,
  ProductQuery,
  ProductSummary,
} from '../models';

/**
 * Typed access to catalog-service.
 *
 * <p>One class per backend service, each returning the interfaces in {@code core/models}. Components
 * never touch {@link HttpClient} or a URL: they ask for products and get {@link ProductSummary}.
 * The typing is the point - a field renamed on the server fails the build here.
 */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1`;

  /** Catalogue search. Undefined filters are omitted rather than sent empty. */
  searchProducts(query: ProductQuery): Observable<PagedResponse<ProductSummary>> {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.size ?? 12);

    if (query.q?.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.categoryId) {
      params = params.set('categoryId', query.categoryId);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }

    return this.http.get<PagedResponse<ProductSummary>>(`${this.baseUrl}/products`, {
      params,
    });
  }

  getProduct(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/products/${id}`);
  }

  /** The category tree, for the catalogue filter. */
  getCategories(asTree = true): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.baseUrl}/categories`, {
      params: new HttpParams().set('tree', asTree),
    });
  }
}
