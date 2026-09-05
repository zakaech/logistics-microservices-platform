import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ProblemDetail } from '../../core/models';
import { OrderStatusLabelPipe, UnavailableReasonLabelPipe } from '../pipes/label.pipe';
import { ORDER_STATUS_LABELS, labelFor } from '../labels';

/**
 * Renders an RFC 7807 problem.
 *
 * <p>One component for every error in the app, because the backend answers one shape everywhere.
 * It reads the structured extras the services attach - which field failed validation, which lines
 * could not be allocated, which products are unavailable - so a user gets "we hold 6 of the 10 you
 * asked for" instead of "request failed".
 */
@Component({
  selector: 'app-problem-alert',
  imports: [OrderStatusLabelPipe, UnavailableReasonLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (problem(); as p) {
      <div class="alert" role="alert">
        <div class="alert__head">
          <strong>{{ p.title }}</strong>
          @if (p.status) {
            <span class="alert__status">{{ p.status }}</span>
          }
        </div>
        <p class="alert__detail">{{ p.detail }}</p>

        @if (p.errors?.length) {
          <ul class="alert__list">
            @for (violation of p.errors; track violation.field) {
              <li><code>{{ violation.field }}</code> — {{ violation.message }}</li>
            }
          </ul>
        }

        @if (p.unsatisfiedLines?.length) {
          <ul class="alert__list">
            @for (line of p.unsatisfiedLines; track line.productId) {
              <li>
                {{ line.requested }} demandé(s), mais l'ensemble du réseau n'en détient que
                {{ line.availableAcrossNetwork }}.
              </li>
            }
          </ul>
        }

        @if (p.unavailableProducts?.length) {
          <ul class="alert__list">
            @for (product of p.unavailableProducts; track product.productId) {
              <li>{{ product.productId }} — {{ product.reason | unavailableReasonLabel }}</li>
            }
          </ul>
        }

        @if (p.allowedTargets?.length) {
          <p class="alert__hint">
            Statut actuel : {{ p.currentStatus | orderStatusLabel }} — transitions possibles :
            {{ allowedLabels(p.allowedTargets) }}
          </p>
        }

        @if (p.requestId) {
          <p class="alert__meta">Référence : <code>{{ p.requestId }}</code></p>
        }
      </div>
    }
  `,
  styles: `
    .alert {
      background: var(--c-error-bg);
      border: 1px solid var(--c-error-border);
      border-left: 3px solid var(--c-error);
      border-radius: var(--radius);
      padding: var(--sp-3) var(--sp-4);
      margin: var(--sp-3) 0;
      color: #6b1f16;
    }
    .alert__head {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: var(--sp-3);
    }
    .alert__head strong {
      font-size: var(--fs-body);
      color: var(--c-error);
    }
    .alert__status {
      font-size: var(--fs-label);
      font-weight: var(--fw-semibold);
      opacity: 0.7;
      font-variant-numeric: tabular-nums;
    }
    .alert__detail {
      margin-top: var(--sp-1);
      font-size: var(--fs-body);
    }
    .alert__list {
      margin: var(--sp-2) 0 0;
      padding-left: 1.2rem;
      font-size: var(--fs-small);
    }
    .alert__list li {
      margin-top: 2px;
    }
    .alert__hint,
    .alert__meta {
      margin-top: var(--sp-2);
      font-size: var(--fs-label);
      opacity: 0.8;
    }
    code {
      font-family: var(--font-mono);
    }
  `,
})
export class ProblemAlertComponent {
  readonly problem = input<ProblemDetail | null>(null);

  /** Libellés des transitions possibles. Les valeurs de l'API restent intactes. */
  allowedLabels(targets: string[] | undefined): string {
    return (targets ?? []).map((t) => labelFor(t, ORDER_STATUS_LABELS)).join(', ');
  }
}
