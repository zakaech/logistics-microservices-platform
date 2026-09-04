/** Identity, roles and tokens. */

/**
 * The roles the platform recognises.
 *
 * <p>Carrying the {@code ROLE_} prefix because that is exactly what the JWT claim contains: one
 * spelling from the database through the token to this guard means no translation layer to get
 * wrong.
 */
export type Role =
  | 'ROLE_ADMIN'
  | 'ROLE_WAREHOUSE_MANAGER'
  | 'ROLE_CLIENT'
  | 'ROLE_SERVICE';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  /** Lifetime of the access token, in seconds. */
  expiresIn: number;
}

export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: Role[];
  createdAt: string;
}

/** What the app knows about the signed-in user, decoded from the token. */
export interface AuthenticatedUser {
  id: string;
  email: string;
  roles: Role[];
  expiresAt: number;
}
