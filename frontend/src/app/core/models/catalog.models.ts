import { Money } from './common.models';

export type ProductStatus = 'ACTIVE' | 'DISCONTINUED';

export type AttributeType = 'STRING' | 'NUMBER' | 'BOOLEAN';

/**
 * One entry of a category attribute schema.
 *
 * <p>This is what makes the catalogue schemaless in shape but not unconstrained: a category
 * declares which technical attributes its products must carry, and the form is built from it.
 */
export interface AttributeDefinition {
  key: string;
  label: string;
  type: AttributeType;
  required: boolean;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  parentId?: string;
  /** Materialised path, e.g. handling/pallet-trucks. */
  path: string;
  attributeSchema: AttributeDefinition[];
  children?: Category[];
  createdAt?: string;
  updatedAt?: string;
}

/** Listing view: what the catalogue grid shows, deliberately narrower than the detail. */
export interface ProductSummary {
  id: string;
  sku: string;
  name: string;
  brand?: string;
  categoryId: string;
  categoryPath: string;
  price: Money;
  primaryImage?: string;
  status: ProductStatus;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  description?: string;
  brand?: string;
  categoryId: string;
  categoryPath: string;
  price: Money;
  /** Free-form technical sheet, constrained by the category schema. */
  attributes: Record<string, string>;
  dimensions?: Dimensions;
  images: string[];
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Dimensions {
  lengthMm?: number;
  widthMm?: number;
  heightMm?: number;
  weightG?: number;
}

/** Query parameters of the catalogue search. */
export interface ProductQuery {
  q?: string;
  categoryId?: string;
  status?: ProductStatus;
  page?: number;
  size?: number;
}
