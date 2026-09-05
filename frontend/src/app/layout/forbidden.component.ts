import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../shared/components/icon.component';

/** Where roleGuard sends a user who is signed in but lacks the role for a screen. */
@Component({
  selector: 'app-forbidden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    <section class="forbidden card">
      <span class="forbidden__icon" aria-hidden="true"><app-icon name="alert" [size]="28" /></span>
      <h2 class="forbidden__title">Accès non autorisé</h2>
      <p class="forbidden__text">
        Vous êtes connecté, mais cet écran requiert un rôle que votre compte ne possède pas. Si cela
        vous semble incorrect, demandez à un administrateur de vérifier vos rôles.
      </p>
      <a class="btn btn--primary btn--sm" routerLink="/catalog">Retour au catalogue</a>
    </section>
  `,
  styles: `
    .forbidden {
      max-width: 30rem;
      margin: var(--sp-7) auto;
      padding: var(--sp-6) var(--sp-5);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--sp-3);
      text-align: center;
    }
    .forbidden__icon {
      display: grid;
      place-items: center;
      width: 3rem;
      height: 3rem;
      border-radius: 50%;
      background: var(--c-warning-bg);
      color: var(--c-warning);
    }
    .forbidden__title {
      font-size: var(--fs-page-title);
      font-weight: var(--fw-semibold);
    }
    .forbidden__text {
      color: var(--c-text-muted);
      font-size: var(--fs-body);
      line-height: 1.6;
    }
  `,
})
export class ForbiddenComponent {}
