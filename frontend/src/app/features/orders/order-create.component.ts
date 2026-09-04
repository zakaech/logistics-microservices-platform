import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { OrderApi } from '../../core/api/order.api';
import { AllocationStrategy, Order, ProblemDetail } from '../../core/models';
import { LoadingBarComponent } from '../../shared/components/loading-bar.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';
import { CartStore } from './cart.store';

/**
 * Checkout: the basket, the delivery address, and the strategy to use.
 *
 * <p>The order is placed with product ids and quantities only. No price and no customer id are
 * sent - the server takes the customer from the token and prices the lines from the catalogue, so
 * nothing here can be tampered with to change what is charged.
 *
 * <p>Coordinates are part of the address because the allocation engine ranks warehouses by distance
 * to the delivery point. Without them the order could not be allocated at all, which is why they are
 * required rather than optional.
 */
@Component({
  selector: 'app-order-create',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    ProblemAlertComponent,
    LoadingBarComponent,
  ],
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss',
})
export class OrderCreateComponent implements OnInit {
  protected readonly cart = inject(CartStore);
  private readonly orders = inject(OrderApi);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly error = signal<ProblemDetail | null>(null);
  protected readonly strategies = signal<AllocationStrategy[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    line1: ['', [Validators.required, Validators.maxLength(180)]],
    city: ['', [Validators.required, Validators.maxLength(80)]],
    postalCode: ['', [Validators.required, Validators.maxLength(16)]],
    country: ['MA', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
    latitude: [33.5899, [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: [-7.6039, [Validators.required, Validators.min(-180), Validators.max(180)]],
    strategy: [''],
  });

  ngOnInit(): void {
    // Offering the choice only makes sense if the server actually registers several strategies.
    this.orders.strategies().subscribe({
      next: (strategies) => this.strategies.set(strategies),
      error: () => this.strategies.set([]),
    });
  }

  protected placeOrder(): void {
    if (this.form.invalid || this.cart.isEmpty()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const address = this.form.getRawValue();

    this.orders
      .create({
        lines: this.cart.lines().map((line) => ({
          productId: line.product.id,
          quantity: line.quantity,
        })),
        deliveryAddress: {
          line1: address.line1,
          city: address.city,
          postalCode: address.postalCode,
          country: address.country,
          latitude: address.latitude,
          longitude: address.longitude,
        },
        strategy: address.strategy || undefined,
      })
      .subscribe({
        next: (order: Order) => {
          // The basket is cleared only once the server confirms: a failure must leave the user
          // with their basket intact so they can retry or adjust it.
          this.cart.clear();
          void this.router.navigate(['/orders', order.id]);
        },
        error: (problem: ProblemDetail) => {
          this.submitting.set(false);
          this.error.set(problem);
        },
      });
  }

  protected updateQuantity(productId: string, value: string): void {
    this.cart.setQuantity(productId, Number(value));
  }
}
