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
    .loading { display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0; }
    .loading__bar {
      width: 1rem; height: 1rem; border-radius: 50%;
      border: 2px solid #c8d2dd; border-top-color: #2c5f8a;
      animation: spin 0.7s linear infinite;
    }
    .loading__label { font-size: 0.85rem; color: #5a6672; }
    @keyframes spin { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .loading__bar { animation: none; } }
  `,
})
export class LoadingBarComponent {
  readonly loading = input(false);
  readonly label = input('Loading…');
}
