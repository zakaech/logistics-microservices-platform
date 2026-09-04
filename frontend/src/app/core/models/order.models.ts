/** Orders, and the allocation decisions behind them. */

export type OrderStatus =
  | 'CREATED'
  | 'ALLOCATED'
  | 'CONFIRMED'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REJECTED';

export interface DeliveryAddress {
  line1: string;
  city: string;
  postalCode: string;
  country: string;
  latitude: number;
  longitude: number;
}

export interface OrderLine {
  id: string;
  productId: string;
  /** Frozen at order time: a later rename or repricing must not rewrite history. */
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  currency: string;
}

export interface AllocationLine {
  orderLineId: string;
  productId: string;
  quantity: number;
}

/**
 * One shipment.
 *
 * <p>{@code distanceKm} is the number the engine actually used to decide. Surfacing it is what
 * turns "shipped from Rabat" into "shipped from Rabat, 87 km away, because it was the only site
 * holding the whole order".
 */
export interface OrderAllocation {
  warehouseId: string;
  warehouseCode: string;
  shipmentSequence: number;
  distanceKm: number;
  lines: AllocationLine[];
}

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  customerId: string;
  totalAmount: number;
  currency: string;
  deliveryAddress: DeliveryAddress;
  /** Which strategy decided, persisted so a past choice can be explained. */
  allocationStrategy?: string;
  splitShipment: boolean;
  lines: OrderLine[];
  allocations: OrderAllocation[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderSummary {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  totalAmount: number;
  currency: string;
  lineCount: number;
  splitShipment: boolean;
  createdAt: string;
}

/** One transition of the order timeline. */
export interface OrderStatusHistoryEntry {
  fromStatus?: OrderStatus;
  toStatus: OrderStatus;
  reason?: string;
  changedBy?: string;
  changedAt: string;
}

export interface CreateOrderRequest {
  lines: { productId: string; quantity: number }[];
  deliveryAddress: DeliveryAddress;
  /** Optional: falls back to the server-configured default. */
  strategy?: string;
}

export interface AllocationStrategy {
  name: string;
  description: string;
  isDefault: boolean;
}

export interface AllocationPreviewRequest {
  lines: { productId: string; quantity: number }[];
  destination: { latitude: number; longitude: number };
  /** Empty means every registered strategy, which is the useful default for a comparison. */
  strategies?: string[];
}

export interface AllocationPreviewSegment {
  warehouseId: string;
  warehouseCode: string;
  distanceKm: number;
  shipmentSequence: number;
  lines: { productId: string; quantity: number }[];
}

/** What one strategy would do. An infeasible one is reported, not hidden. */
export interface StrategyResult {
  strategy: string;
  description: string;
  feasible: boolean;
  splitShipment?: boolean;
  shipmentCount?: number;
  maxDistanceKm?: number;
  segments?: AllocationPreviewSegment[];
  failureReason?: string;
}

export interface AllocationPreviewResponse {
  results: StrategyResult[];
}
