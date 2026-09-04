export interface Address {
  line1: string;
  city: string;
  postalCode: string;
  country: string;
}

export interface Warehouse {
  id: string;
  code: string;
  name: string;
  address: Address;
  latitude: number;
  longitude: number;
  active: boolean;
  distinctProducts: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * A stock level.
 *
 * <p>{@code availableQuantity} and {@code lowStock} are computed by the server and never stored:
 * the dashboard displays them rather than recomputing, so the two cannot disagree.
 */
export interface StockItem {
  id: string;
  warehouseId: string;
  warehouseCode: string;
  productId: string;
  quantityOnHand: number;
  quantityReserved: number;
  availableQuantity: number;
  reorderThreshold: number;
  lowStock: boolean;
  updatedAt: string;
}

export type MovementType =
  | 'INBOUND'
  | 'OUTBOUND'
  | 'ADJUSTMENT'
  | 'RESERVATION'
  | 'RELEASE';

/** One entry of the append-only ledger. */
export interface StockMovement {
  id: string;
  warehouseId: string;
  warehouseCode: string;
  productId: string;
  type: MovementType;
  /** Signed: positive adds, negative removes. */
  quantity: number;
  reference?: string;
  createdBy: string;
  occurredAt: string;
}
