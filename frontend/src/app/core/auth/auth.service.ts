import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  UserResponse,
} from '../models';
import { TokenStore } from './token.store';

/**
 * Sign-in, sign-out and the current profile.
 *
 * <p>Everything goes through the gateway; this app never learns the address of an individual
 * service. Credentials handling is delegated to {@link TokenStore}, so this class stays about the
 * flow rather than about storage.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStore);
  private readonly router = inject(Router);

  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/auth`;

  login(credentials: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.baseUrl}/login`, credentials)
      .pipe(tap((tokens) => this.tokens.save(tokens)));
  }

  register(request: RegisterRequest): Observable<UserResponse> {
    // Registration always yields ROLE_CLIENT; the server decides, not this form.
    return this.http.post<UserResponse>(`${this.baseUrl}/register`, request);
  }

  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${environment.apiBaseUrl}/api/v1/users/me`);
  }

  /**
   * Signs out.
   *
   * <p>The local credentials are cleared first and unconditionally. Waiting for the server, or
   * clearing only on success, would leave a usable token in the browser whenever the network is
   * down - which is exactly when someone is most likely to walk away from the machine.
   */
  logout(): void {
    const refreshToken = this.tokens.refreshToken();
    this.tokens.clear();

    if (refreshToken) {
      // Best effort: revoking the refresh token server-side is what makes the sign-out real, but
      // its failure must not keep the user signed in locally.
      this.http
        .post(`${this.baseUrl}/logout`, { refreshToken })
        .subscribe({ error: () => undefined });
    }
    void this.router.navigate(['/login']);
  }
}
