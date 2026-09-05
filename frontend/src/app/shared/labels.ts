import { MovementType, OrderStatus, Role } from '../core/models';

/**
 * Libellés d'affichage.
 *
 * <p>Cette table traduit pour l'écran, jamais pour l'API. Les valeurs techniques
 * (`CONFIRMED`, `nearest`) restent celles que le back-end envoie et attend : elles servent
 * de clés, de valeurs en base et de sélecteurs CSS. Seul le texte lu par l'utilisateur
 * change ici, ce qui garantit qu'une traduction ne peut pas casser un contrat.
 */

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: 'Créée',
  ALLOCATED: 'Affectée',
  CONFIRMED: 'Confirmée',
  SHIPPED: 'Expédiée',
  DELIVERED: 'Livrée',
  CANCELLED: 'Annulée',
  REJECTED: 'Rejetée',
};

export const ALLOCATION_STRATEGY_LABELS: Record<string, string> = {
  nearest: 'Entrepôt le plus proche',
  'single-shipment': 'Expédition unique',
};

export const MOVEMENT_TYPE_LABELS: Record<MovementType, string> = {
  INBOUND: 'Entrée',
  OUTBOUND: 'Sortie',
  ADJUSTMENT: 'Ajustement',
  RESERVATION: 'Réservation',
  RELEASE: 'Libération',
};

export const UNAVAILABLE_REASON_LABELS: Record<string, string> = {
  UNKNOWN: 'produit inconnu',
  DISCONTINUED: 'retiré du catalogue',
};

export const ROLE_LABELS: Record<Role, string> = {
  ROLE_ADMIN: 'Administrateur',
  ROLE_WAREHOUSE_MANAGER: 'Responsable entrepôt',
  ROLE_CLIENT: 'Client',
  ROLE_SERVICE: 'Compte technique',
};

/**
 * Renvoie le libellé, ou la valeur brute si elle est inconnue.
 *
 * <p>Ne jamais masquer une valeur non traduite : si le back-end introduit un nouveau
 * statut, mieux vaut l'afficher tel quel que de rendre une case vide.
 */
export function labelFor(value: string, table: Record<string, string>): string {
  return table[value] ?? value;
}
