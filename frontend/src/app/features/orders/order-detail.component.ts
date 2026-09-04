import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../core/api/order.api';
import {
  Order,
  OrderStatusHistoryEntry,
  ProblemDetail,
} from '../../core/models';
import { TokenStore } from '../../core/auth/token.store';
import { LoadingBarComponent } from '../../shared/components/loading-bar.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';
import { RequestState } from '../../shared/request-state';

/**
 * One order, and why it will ship the way it will.
 *
 * <p>This is the screen the whole platform exists to produce. It answers three questions a customer
 * actually asks: which warehouse is sending my order, why that one, and what has happened to it so
 * far.
 *
 * <p>"Why that one" is answered from data the server persisted at allocation time - the strategy
 * that decided, and the distance it used - rather than by re-running the algorithm here. Stock has
 * moved since; replaying the rules now would produce a different answer and explain nothing.
 */
@Component({
  selector: 'app-order-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    ProblemAlertComponent,
    LoadingBarComponent,
  ],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
})
export class OrderDetailComponent implements OnInit {
  /** Bound from the route path, thanks to withComponentInputBinding(). */
  readonly id = input.required<string>();

  private readonly orders = inject(OrderApi);
  private readonly tokens = inject(TokenStore);

  protected readonly order = new RequestState<Order>();
  protected readonly history = signal<OrderStatusHistoryEntry[]>([]);
  protected readonly acting = signal(false);
  protected readonly actionError = signal<ProblemDetail | null>(null);

  protected readonly isStaff = this.tokens.hasAnyRole([
    'ROLE_ADMIN',
    'ROLE_WAREHOUSE_MANAGER',
  ]);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.order.start();
    this.orders.get(this.id()).subscribe({
      next: (order) => this.order.succeed(order),
      error: (problem: ProblemDetail) => this.order.fail(problem),
    });

    this.orders.history(this.id()).subscribe({
      next: (entries) => this.history.set(entries),
      // The timeline is supporting detail: losing it degrades the screen without breaking it.
      error: () => this.history.set([]),
    });
  }

  /**
   * Resolves a product name from the order's own lines.
   *
   * <p>An allocation references a product id; the readable name lives on the order line, which
   * froze it at order time. Looking it up here rather than calling the catalogue keeps the screen
   * working even for a product since discontinued.
   */
  protected productName(order: Order, productId: string): string {
    return order.lines.find((line) => line.productId === productId)?.name ?? productId;
  }

  /** True while the order can still be cancelled - mirrors the server state machine. */
  protected canCancel(order: Order): boolean {
    return ['CREATED', 'ALLOCATED', 'CONFIRMED'].includes(order.status);
  }

  protected canShip(order: Order): boolean {
    return this.isStaff && order.status === 'CONFIRMED';
  }

  protected canDeliver(order: Order): boolean {
    return this.isStaff && order.status === 'SHIPPED';
  }

  protected cancel(): void {
    this.act(() => this.orders.cancel(this.id(), 'Cancelled from the tracking screen'));
  }

  protected ship(): void {
    this.act(() => this.orders.changeStatus(this.id(), 'SHIPPED', 'Handed to the carrier'));
  }

  protected deliver(): void {
    this.act(() => this.orders.changeStatus(this.id(), 'DELIVERED'));
  }

  /**
   * Runs a lifecycle action and reloads.
   *
   * <p>The reload is deliberate rather than patching the local object: the server may have changed
   * more than the status - the timeline gains an entry, and a cancellation releases stock - and
   * guessing at those locally is how a screen starts lying.
   */
  private act(action: () => import('rxjs').Observable<Order>): void {
    this.acting.set(true);
    this.actionError.set(null);

    action().subscribe({
      next: () => {
        this.acting.set(false);
        this.load();
      },
      error: (problem: ProblemDetail) => {
        this.acting.set(false);
        this.actionError.set(problem);
      },
    });
  }
}
