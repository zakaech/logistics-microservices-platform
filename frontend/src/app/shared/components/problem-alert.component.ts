import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ProblemDetail } from '../../core/models';

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
                Requested {{ line.requested }}, but the whole network holds only
                {{ line.availableAcrossNetwork }}.
              </li>
            }
          </ul>
        }

        @if (p.unavailableProducts?.length) {
          <ul class="alert__list">
            @for (product of p.unavailableProducts; track product.productId) {
              <li>{{ product.productId }} — {{ product.reason.toLowerCase() }}</li>
            }
          </ul>
        }

        @if (p.allowedTargets?.length) {
          <p class="alert__hint">
            Currently {{ p.currentStatus }}; allowed next: {{ p.allowedTargets?.join(', ') }}
          </p>
        }

        @if (p.requestId) {
          <p class="alert__meta">Reference: <code>{{ p.requestId }}</code></p>
        }
      </div>
    }
  `,
  styles: `
    .alert {
      border: 1px solid #f0b4b4;
      background: #fdf3f3;
      border-left: 4px solid #c0392b;
      border-radius: 4px;
      padding: 0.85rem 1rem;
      margin: 0.75rem 0;
      color: #6b1f16;
    }
    .alert__head { display: flex; justify-content: space-between; align-items: baseline; }
    .alert__status { font-size: 0.8rem; opacity: 0.7; }
    .alert__detail { margin: 0.4rem 0 0; }
    .alert__list { margin: 0.5rem 0 0; padding-left: 1.2rem; font-size: 0.9rem; }
    .alert__hint, .alert__meta { margin: 0.5rem 0 0; font-size: 0.8rem; opacity: 0.75; }
    code { font-family: ui-monospace, monospace; }
  `,
})
export class ProblemAlertComponent {
  readonly problem = input<ProblemDetail | null>(null);
}
