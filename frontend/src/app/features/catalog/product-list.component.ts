import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import {
  Category,
  PagedResponse,
  ProblemDetail,
  ProductSummary,
} from '../../core/models';
import { CartStore } from '../orders/cart.store';
import { IconComponent } from '../../shared/components/icon.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';
import { RequestState } from '../../shared/request-state';

/**
 * The catalogue: search, filter, paginate, add to basket.
 *
 * <p>Filtering happens on the server, not in the browser. Fetching the whole catalogue to filter it
 * here would work with a demo dataset and fall over with a real one; and the text search uses a
 * MongoDB text index that no client-side {@code includes()} can reproduce.
 */
@Component({
  selector: 'app-product-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CurrencyPipe, ProblemAlertComponent, IconComponent],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss',
})
export class ProductListComponent implements OnInit {
  private readonly catalog = inject(CatalogApi);
  protected readonly cart = inject(CartStore);

  protected readonly products = new RequestState<PagedResponse<ProductSummary>>();
  protected readonly categories = signal<Category[]>([]);

  protected searchTerm = '';
  protected selectedCategoryId = '';
  protected readonly page = signal(0);
  private readonly pageSize = 12;

  /** Which product was just added, so the button can confirm it without a toast library. */
  protected readonly justAdded = signal<string | null>(null);

  /** Emplacements des cartes fantômes pendant le chargement : autant que la taille de page. */
  protected readonly skeletons = Array.from({ length: this.pageSize }, (_, i) => i);

  /**
   * Le nom lisible de la catégorie d'un produit.
   *
   * <p>Le produit ne transporte que son {@code categoryPath} (`handling/pallet-trucks`). Le
   * dernier segment est retrouvé dans l'arbre déjà chargé pour le filtre, ce qui évite un appel
   * de plus par carte. Si l'arbre n'a pas pu être chargé, le segment brut est affiché plutôt
   * qu'un blanc.
   */
  protected categoryLabel(path: string): string {
    const slug = path.split('/').pop() ?? '';
    const match = this.findBySlug(this.categories(), slug);
    return match?.name ?? slug;
  }

  private findBySlug(nodes: Category[], slug: string): Category | undefined {
    for (const node of nodes) {
      if (node.slug === slug) {
        return node;
      }
      const found = this.findBySlug(node.children ?? [], slug);
      if (found) {
        return found;
      }
    }
    return undefined;
  }

  ngOnInit(): void {
    this.loadCategories();
    this.search();
  }

  protected search(resetPage = true): void {
    if (resetPage) {
      this.page.set(0);
    }
    this.products.start();

    this.catalog
      .searchProducts({
        q: this.searchTerm,
        categoryId: this.selectedCategoryId || undefined,
        status: 'ACTIVE',
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (result) => this.products.succeed(result),
        error: (problem: ProblemDetail) => this.products.fail(problem),
      });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.search(false);
  }

  protected clearFilters(): void {
    this.searchTerm = '';
    this.selectedCategoryId = '';
    this.search();
  }

  protected addToCart(product: ProductSummary): void {
    this.cart.add(product);
    this.justAdded.set(product.id);
    setTimeout(() => this.justAdded.set(null), 1500);
  }

  /**
   * Flattens the category tree for the filter dropdown, indenting by depth.
   *
   * <p>The server returns a tree; a select needs a list. Depth comes from the materialised path, so
   * the indentation is derived from the data rather than from a recursive walk.
   */
  protected flatCategories(): { id: string; label: string }[] {
    const flatten = (nodes: Category[]): { id: string; label: string }[] =>
      nodes.flatMap((node) => [
        {
          id: node.id,
          label: `${'  '.repeat(node.path.split('/').length - 1)}${node.name}`,
        },
        ...flatten(node.children ?? []),
      ]);
    return flatten(this.categories());
  }

  private loadCategories(): void {
    this.catalog.getCategories(true).subscribe({
      next: (categories) => this.categories.set(categories),
      // A missing filter list is a degraded screen, not a broken one: the grid still works, so
      // this failure is deliberately not surfaced as a page-level error.
      error: () => this.categories.set([]),
    });
  }
}
