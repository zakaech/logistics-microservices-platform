import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Where roleGuard sends a user who is signed in but lacks the role for a screen. */
@Component({
  selector: 'app-forbidden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="forbidden">
      <h1>Not available to your account</h1>
      <p>
        You are signed in, but this screen needs a role your account does not hold. If you believe
        that is wrong, ask an administrator to review your roles.
      </p>
      <a routerLink="/catalog">Back to the catalogue</a>
    </section>
  `,
  styles: `
    .forbidden { max-width: 32rem; margin: 4rem auto; padding: 1rem; text-align: center; }
    h1 { font-size: 1.3rem; }
    p { color: #5a6672; line-height: 1.6; }
    a { color: #2c5f8a; }
  `,
})
export class ForbiddenComponent {}
