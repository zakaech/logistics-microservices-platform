import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'dashboard'
  | 'catalog'
  | 'orders'
  | 'stock'
  | 'cart'
  | 'search'
  | 'logout'
  | 'user'
  | 'alert'
  | 'check'
  | 'truck'
  | 'route'
  | 'package'
  | 'warehouse'
  | 'arrow-left'
  | 'menu'
  | 'close'
  | 'inbox';

/**
 * Le jeu d'icônes de l'application.
 *
 * <p>Des SVG écrits ici plutôt qu'une bibliothèque : une vingtaine de glyphes ne justifie ni une
 * dépendance de plusieurs centaines de kilo-octets, ni une police à télécharger avant le premier
 * rendu. Tous partagent la même grille de 24, le même trait de 1.75 et les mêmes extrémités
 * arrondies — c'est précisément ce qui fait qu'un jeu d'icônes paraît cohérent.
 *
 * <p>Le balisage est littéral, dans un {@code @switch}, plutôt qu'injecté via {@code innerHTML} :
 * Angular désinfecte le HTML lié et retirerait les balises SVG, laissant des icônes vides.
 *
 * <p>Les icônes sont décoratives — elles accompagnent toujours un texte. D'où {@code aria-hidden},
 * pour qu'un lecteur d'écran ne les annonce pas en double.
 */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (name()) {
        @case ('dashboard') {
          <rect x="3" y="3" width="7" height="9" rx="1" />
          <rect x="14" y="3" width="7" height="5" rx="1" />
          <rect x="14" y="12" width="7" height="9" rx="1" />
          <rect x="3" y="16" width="7" height="5" rx="1" />
        }
        @case ('catalog') {
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <path d="M3 9h18M9 9v11" />
        }
        @case ('orders') {
          <path d="M8 3h8a1 1 0 0 1 1 1v1H7V4a1 1 0 0 1 1-1z" />
          <rect x="4" y="5" width="16" height="16" rx="2" />
          <path d="M9 11h6M9 15h4" />
        }
        @case ('stock') {
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <path d="M3 10h18M3 15h18M9 4v16" />
        }
        @case ('cart') {
          <circle cx="9" cy="20" r="1.4" />
          <circle cx="18" cy="20" r="1.4" />
          <path d="M2 3h2.2l2.3 12.2a1.5 1.5 0 0 0 1.5 1.2h9.3a1.5 1.5 0 0 0 1.5-1.2L21 7H5" />
        }
        @case ('search') {
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        }
        @case ('logout') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="m16 16 5-4-5-4M21 12H9" />
        }
        @case ('user') {
          <circle cx="12" cy="8" r="3.5" />
          <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
        }
        @case ('alert') {
          <path d="M12 4.5 2.8 20h18.4z" />
          <path d="M12 10v4" />
          <circle cx="12" cy="17" r="0.7" fill="currentColor" stroke="none" />
        }
        @case ('check') {
          <circle cx="12" cy="12" r="9" />
          <path d="m8.5 12.2 2.4 2.4 4.6-5" />
        }
        @case ('truck') {
          <path d="M3 6h11v10H3z" />
          <path d="M14 9h4l3 3v4h-7z" />
          <circle cx="7" cy="18" r="1.6" />
          <circle cx="17.5" cy="18" r="1.6" />
        }
        @case ('route') {
          <circle cx="6" cy="7" r="2.5" />
          <circle cx="18" cy="17" r="2.5" />
          <path d="M8.5 7H14a3 3 0 0 1 0 6h-4a3 3 0 0 0 0 6h5.5" />
        }
        @case ('package') {
          <path d="m12 3 8 4.2v9.6L12 21l-8-4.2V7.2z" />
          <path d="m4 7.2 8 4.2 8-4.2M12 11.4V21" />
        }
        @case ('warehouse') {
          <path d="M3 10 12 4l9 6v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z" />
          <path d="M8 21v-7h8v7" />
        }
        @case ('arrow-left') {
          <path d="M19 12H5" />
          <path d="m11 6-6 6 6 6" />
        }
        @case ('menu') {
          <path d="M4 7h16M4 12h16M4 17h16" />
        }
        @case ('close') {
          <path d="m6 6 12 12M18 6 6 18" />
        }
        @case ('inbox') {
          <path d="M4.6 5.3 3 13v5a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5l-1.6-7.7A2 2 0 0 0 17.4 4H6.6a2 2 0 0 0-2 1.3z" />
          <path d="M3 13h5l1.5 3h5L16 13h5" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
    svg {
      display: block;
    }
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(18);
}
