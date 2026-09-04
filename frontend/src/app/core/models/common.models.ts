/**
 * Shapes shared by every service.
 *
 * <p>These mirror the backend DTOs deliberately closely. They are the contract, written down once
 * in TypeScript, so that a field renamed on the server breaks compilation here rather than
 * surfacing as an empty cell in a table.
 */

/** The explicit pagination envelope the backend returns (decision D7). */
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Money {
  amount: number;
  currency: string;
}

/**
 * RFC 7807 problem detail.
 *
 * <p>Every service answers errors in this shape, including the gateway, so the front-end parses one
 * document whatever failed and wherever it failed.
 */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  timestamp?: string;
  requestId?: string;
  /** Present on validation failures. */
  errors?: FieldViolation[];
  /** Present when an order cannot be allocated. */
  unsatisfiedLines?: UnsatisfiedLine[];
  /** Present when a product is unknown or discontinued. */
  unavailableProducts?: UnavailableProduct[];
  /** Present when a reservation cannot be taken. */
  shortages?: Shortage[];
  /** Present on an illegal state transition. */
  currentStatus?: string;
  allowedTargets?: string[];
}

export interface FieldViolation {
  field: string;
  message: string;
}

export interface UnsatisfiedLine {
  productId: string;
  requested: number;
  availableAcrossNetwork: number;
}

export interface UnavailableProduct {
  productId: string;
  reason: 'UNKNOWN' | 'DISCONTINUED';
}

export interface Shortage {
  warehouseId: string;
  productId: string;
  requested: number;
  available: number;
}
