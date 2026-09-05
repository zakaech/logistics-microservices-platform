import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../core/api/order.api';
import {
  OrderStatus,
  OrderSummary,
  PagedResponse,
  ProblemDetail,
} from '../../core/models';
import { LoadingBarComponent } from '../../shared/components/loading-bar.component';
import { IconComponent } from '../../shared/components/icon.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';
import { OrderStatusLabelPipe } from '../../shared/pipes/label.pipe';
import { RequestState } from '../../shared/request-state';

/**
 * Order tracking.
 *
 * <p>The same screen serves customers and staff: the server scopes the result to the caller, so a
 * client sees their own orders and a warehouse manager sees all. Filtering here by customer would
 * mean trusting the browser to hide other people's orders, which is not a control at all.
 */
@Component({
  selector: 'app-order-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    CurrencyPipe,
    DatePipe,
    OrderStatusLabelPipe,
    IconComponent,
    ProblemAlertComponent,
    LoadingBarComponent,
  ],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.scss',
})
export class OrderListComponent implements OnInit {
  private readonly orders = inject(OrderApi);

  protected readonly result = new RequestState<PagedResponse<OrderSummary>>();
  protected readonly page = signal(0);
  protected selectedStatus: OrderStatus | '' = '';

  protected readonly statuses: OrderStatus[] = [
    'CREATED',
    'ALLOCATED',
    'CONFIRMED',
    'SHIPPED',
    'DELIVERED',
    'CANCELLED',
    'REJECTED',
  ];

  ngOnInit(): void {
    this.load();
  }

  protected load(resetPage = true): void {
    if (resetPage) {
      this.page.set(0);
    }
    this.result.start();

    this.orders.list(this.selectedStatus || undefined, this.page(), 20).subscribe({
      next: (page) => this.result.succeed(page),
      error: (problem: ProblemDetail) => this.result.fail(problem),
    });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.load(false);
  }
}
