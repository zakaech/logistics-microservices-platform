import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** A single loading indicator, so every screen signals work in the same way. */
@Component({
  selector: 'app-loading-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="loading" role="status" aria-live="polite">
        <span class="loading__bar"></span>
        <span class="loading__label">{{ label() }}</span>
      </div>
    }
  `,
  styles: `
    .loading {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
      padding: var(--sp-2) 0;
    }
    .loading__bar {
      width: 1rem;
      height: 1rem;
      border-radius: 50%;
      border: 2px solid var(--c-border-strong);
      border-top-color: var(--c-primary);
      animation: spin 0.7s linear infinite;
    }
    .loading__label {
      font-size: var(--fs-small);
      color: var(--c-text-muted);
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .loading__bar {
        animation: none;
      }
    }
  `,
})
export class LoadingBarComponent {
  readonly loading = input(false);
  readonly label = input('Chargement…');
}
