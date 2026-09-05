import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ProblemDetail } from '../../core/models';
import { IconComponent } from '../../shared/components/icon.component';
import { LoadingBarComponent } from '../../shared/components/loading-bar.component';
import { ProblemAlertComponent } from '../../shared/components/problem-alert.component';

/**
 * Sign-in.
 *
 * <p>Validation mirrors the server's, which is a convenience and nothing more: the server validates
 * again because a browser is not a trust boundary. The point of doing it here is to spare a round
 * trip for an obviously empty field, not to be the check that matters.
 */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IconComponent,
    ProblemAlertComponent,
    LoadingBarComponent,
  ],
  template: `
    <div class="auth">
      <section class="auth__panel card">
        <header class="auth__head">
          <span class="auth__mark" aria-hidden="true"><app-icon name="package" [size]="22" /></span>
          <div>
            <h2 class="auth__title">Connexion</h2>
            <p class="auth__sub">Plateforme de gestion logistique multi-entrepôts</p>
          </div>
        </header>

        @if (sessionExpired()) {
          <p class="auth__notice" role="status">
            <app-icon name="alert" [size]="16" />
            Votre session a expiré. Merci de vous reconnecter.
          </p>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label class="field__label" for="email">Adresse e-mail</label>
            <input
              class="input"
              id="email"
              type="email"
              formControlName="email"
              autocomplete="username"
              [attr.aria-invalid]="form.controls.email.touched && form.controls.email.invalid"
            />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <small class="field__error">Une adresse e-mail valide est requise.</small>
            }
          </div>

          <div class="field">
            <label class="field__label" for="password">Mot de passe</label>
            <input
              class="input"
              id="password"
              type="password"
              formControlName="password"
              autocomplete="current-password"
              [attr.aria-invalid]="form.controls.password.touched && form.controls.password.invalid"
            />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <small class="field__error">Le mot de passe est requis.</small>
            }
          </div>

          <app-loading-bar [loading]="loading()" label="Connexion en cours…" />
          <app-problem-alert [problem]="error()" />

          <button type="submit" class="btn btn--primary auth__submit" [disabled]="loading()">
            Se connecter
          </button>
        </form>
      </section>

      <p class="auth__foot">
        Le catalogue est consultable sans compte.
        <a routerLink="/catalog">Parcourir le catalogue</a>
      </p>
    </div>
  `,
  styles: `
    .auth {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--sp-4);
      padding: var(--sp-6) var(--sp-3);
    }
    .auth__panel {
      width: 100%;
      max-width: 24rem;
      padding: var(--sp-5);
    }
    .auth__head {
      display: flex;
      align-items: center;
      gap: var(--sp-3);
      margin-bottom: var(--sp-5);
    }
    .auth__mark {
      display: grid;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      flex: none;
      border-radius: var(--radius-sm);
      background: var(--c-primary);
      color: #fff;
    }
    .auth__title {
      font-size: 1.25rem;
      font-weight: var(--fw-semibold);
    }
    .auth__sub {
      font-size: var(--fs-small);
      color: var(--c-text-muted);
    }
    .auth__notice {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
      margin-bottom: var(--sp-4);
      padding: var(--sp-2) var(--sp-3);
      background: var(--c-warning-bg);
      border: 1px solid var(--c-warning-border);
      border-left: 3px solid var(--c-warning);
      border-radius: var(--radius-sm);
      font-size: var(--fs-small);
      color: var(--c-warning);
    }
    form {
      display: flex;
      flex-direction: column;
      gap: var(--sp-3);
    }
    .auth__submit {
      margin-top: var(--sp-2);
      justify-content: center;
      padding: 0.65rem;
    }
    .auth__foot {
      font-size: var(--fs-small);
      color: var(--c-text-muted);
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly loading = signal(false);
  protected readonly error = signal<ProblemDetail | null>(null);

  protected readonly sessionExpired = signal(
    this.route.snapshot.queryParamMap.get('reason') === 'session-expired',
  );

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        // Back to wherever the guard interrupted, or the catalogue by default.
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/catalog';
        void this.router.navigateByUrl(returnUrl);
      },
      error: (problem: ProblemDetail) => {
        this.loading.set(false);
        this.error.set(problem);
      },
    });
  }
}
