import { Pipe, PipeTransform } from '@angular/core';
import {
  ALLOCATION_STRATEGY_LABELS,
  MOVEMENT_TYPE_LABELS,
  ORDER_STATUS_LABELS,
  ROLE_LABELS,
  UNAVAILABLE_REASON_LABELS,
  labelFor,
} from '../labels';

/**
 * Pipes d'affichage.
 *
 * <p>Des pipes plutôt que des méthodes de composant : la traduction reste au plus près du
 * gabarit, sans être dupliquée dans chaque écran qui affiche un statut.
 */

@Pipe({ name: 'orderStatusLabel' })
export class OrderStatusLabelPipe implements PipeTransform {
  transform(status: string | null | undefined): string {
    return status ? labelFor(status, ORDER_STATUS_LABELS) : '';
  }
}

@Pipe({ name: 'strategyLabel' })
export class StrategyLabelPipe implements PipeTransform {
  transform(strategy: string | null | undefined): string {
    return strategy ? labelFor(strategy, ALLOCATION_STRATEGY_LABELS) : '';
  }
}

@Pipe({ name: 'movementTypeLabel' })
export class MovementTypeLabelPipe implements PipeTransform {
  transform(type: string | null | undefined): string {
    return type ? labelFor(type, MOVEMENT_TYPE_LABELS) : '';
  }
}

@Pipe({ name: 'roleLabel' })
export class RoleLabelPipe implements PipeTransform {
  transform(role: string | null | undefined): string {
    return role ? labelFor(role, ROLE_LABELS) : '';
  }
}

@Pipe({ name: 'unavailableReasonLabel' })
export class UnavailableReasonLabelPipe implements PipeTransform {
  transform(reason: string | null | undefined): string {
    return reason ? labelFor(reason, UNAVAILABLE_REASON_LABELS) : '';
  }
}
