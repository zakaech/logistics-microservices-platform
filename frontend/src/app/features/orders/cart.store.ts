import { Injectable, computed, signal } from '@angular/core';
import { ProductSummary } from '../../core/models';

export interface CartLine {
  product: ProductSummary;
  quantity: number;
}

const STORAGE_KEY = 'logistics.cart';

/**
 * The basket.
 *
 * <p>Kept in a signal so every consumer - the badge in the toolbar, the checkout screen - reads one
 * source and updates without any subscription bookkeeping.
 *
 * <p>Persisted to localStorage on every change, so a refresh does not empty the basket. It holds only product ids and quantities; the price shown is whatever the
 * catalogue returns now, and the price actually charged is what the server computes at checkout, so
 * a stale basket cannot lock in an old price.
 */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly items = signal<CartLine[]>(this.restore());

  readonly lines = this.items.asReadonly();
  readonly count = computed(() =>
    this.items().reduce((total, line) => total + line.quantity, 0),
  );
  readonly isEmpty = computed(() => this.items().length === 0);

  /** Indicative only: the authoritative total is computed server-side at checkout. */
  readonly estimatedTotal = computed(() =>
    this.items().reduce(
      (total, line) => total + line.product.price.amount * line.quantity,
      0,
    ),
  );

  readonly currency = computed(
    () => this.items()[0]?.product.price.currency ?? 'EUR',
  );

  add(product: ProductSummary, quantity = 1): void {
    this.items.update((lines) => {
      const existing = lines.find((line) => line.product.id === product.id);
      // Adding the same product twice increases the line rather than creating a second one: the
      // API rejects a duplicated productId outright.
      const next = existing
        ? lines.map((line) =>
            line.product.id === product.id
              ? { ...line, quantity: line.quantity + quantity }
              : line,
          )
        : [...lines, { product, quantity }];
      return next;
    });
    this.persist();
  }

  setQuantity(productId: string, quantity: number): void {
    if (quantity <= 0) {
      this.remove(productId);
      return;
    }
    this.items.update((lines) =>
      lines.map((line) =>
        line.product.id === productId ? { ...line, quantity } : line,
      ),
    );
    this.persist();
  }

  remove(productId: string): void {
    this.items.update((lines) =>
      lines.filter((line) => line.product.id !== productId),
    );
    this.persist();
  }

  clear(): void {
    this.items.set([]);
    this.persist();
  }

  private persist(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.items()));
    } catch {
      // A full or disabled storage must not break checkout; the basket simply stops surviving
      // a reload.
    }
  }

  private restore(): CartLine[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as CartLine[]) : [];
    } catch {
      return [];
    }
  }
}
