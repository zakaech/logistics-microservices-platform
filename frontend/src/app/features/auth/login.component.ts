import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ProblemDetail } from '../../core/models';
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
  imports: [ReactiveFormsModule, RouterLink, ProblemAlertComponent, LoadingBarComponent],
  template: `
    <div class="auth">
      <h1>Sign in</h1>

      @if (sessionExpired()) {
        <p class="auth__notice">Your session expired. Please sign in again.</p>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <label for="email">Email</label>
        <input id="email" type="email" formControlName="email" autocomplete="username" />
        @if (form.controls.email.touched && form.controls.email.invalid) {
          <small class="auth__error">A valid email address is required.</small>
        }

        <label for="password">Password</label>
        <input
          id="password"
          type="password"
          formControlName="password"
          autocomplete="current-password"
        />
        @if (form.controls.password.touched && form.controls.password.invalid) {
          <small class="auth__error">Your password is required.</small>
        }

        <app-loading-bar [loading]="loading()" label="Signing in…" />
        <app-problem-alert [problem]="error()" />

        <button type="submit" [disabled]="loading()">Sign in</button>
      </form>

      <p class="auth__foot">
        No account yet? <a routerLink="/register">Create one</a>
      </p>
    </div>
  `,
  styles: `
    .auth { max-width: 22rem; margin: 3rem auto; }
    .auth h1 { margin-bottom: 1.25rem; }
    form { display: flex; flex-direction: column; gap: 0.35rem; }
    label { font-size: 0.85rem; font-weight: 600; margin-top: 0.6rem; }
    input {
      padding: 0.55rem 0.7rem; border: 1px solid #c8d2dd;
      border-radius: 4px; font-size: 0.95rem;
    }
    button {
      margin-top: 1rem; padding: 0.6rem; border: 0; border-radius: 4px;
      background: #2c5f8a; color: #fff; font-weight: 600; cursor: pointer;
    }
    button:disabled { opacity: 0.6; cursor: progress; }
    .auth__error { color: #c0392b; font-size: 0.8rem; }
    .auth__notice {
      background: #fff6e0; border-left: 3px solid #d98c00;
      padding: 0.6rem 0.8rem; font-size: 0.88rem; border-radius: 3px;
    }
    .auth__foot { margin-top: 1.5rem; font-size: 0.88rem; }
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
