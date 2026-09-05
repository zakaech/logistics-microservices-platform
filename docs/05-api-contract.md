# 05 — Contrat de l'API REST

Toutes les routes sont exposées à travers la gateway sur `http://localhost:8080`. Les services ne sont jamais
appelés directement par le frontend.

## 0. Conventions

**Versionnage** — tous les chemins sont préfixés par `/api/v1`. Un changement cassant impose `/api/v2`, servi
en parallèle.

**Routage de la gateway**

| Prédicat de chemin | Service cible | Jeton exigé en périphérie |
|---|---|---|
| `/api/v1/auth/**` | `auth-service` | Non (public : login, register, refresh) |
| `/api/v1/users/**` | `auth-service` | Oui |
| `/api/v1/products/**`, `/api/v1/categories/**` | `catalog-service` | Uniquement pour les méthodes d'écriture |
| `/api/v1/warehouses/**`, `/api/v1/stock/**`, `/api/v1/inventory/**` | `inventory-service` | Oui |
| `/api/v1/orders/**` | `order-service` | Oui |

**Authentification** — `Authorization: Bearer <accessToken>` sur toute route non publique.

Claims du JWT :

```json
{
  "iss": "logistics-auth",
  "sub": "3f9a...-b21c",
  "email": "user@example.com",
  "roles": ["ROLE_CLIENT"],
  "iat": 1772000000,
  "exp": 1772000900,
  "jti": "0a1b...-9cd2"
}
```

**En-têtes standards**

| En-tête | Sens | Rôle |
|---|---|---|
| `X-Request-Id` | entrée/sortie | Identifiant de corrélation ; généré par la gateway s'il est absent, renvoyé dans chaque réponse |
| `Idempotency-Key` | entrée | Exigé sur `POST /orders` et `POST /inventory/reservations` |
| `Location` | sortie | Renvoyé avec chaque `201` |

**Format d'erreur — RFC 7807 (`application/problem+json`)**

```json
{
  "type": "https://logistics.local/problems/allocation-failed",
  "title": "Allocation failed",
  "status": 409,
  "detail": "No combination of warehouses can fulfil this order.",
  "instance": "/api/v1/orders",
  "timestamp": "2026-09-03T10:15:30Z",
  "requestId": "c0ffee-1234",
  "errors": [
    { "field": "lines[0].quantity", "message": "must be greater than 0" }
  ]
}
```

`errors` n'est présent que pour les problèmes de validation.

**Codes de statut utilisés sur toute la plateforme**

| Code | Signification dans cette plateforme |
|---|---|
| `200` | Lecture ou mise à jour réussie |
| `201` | Ressource créée (en-tête `Location` positionné) |
| `204` | Supprimé / déconnecté, sans corps |
| `400` | Corps malformé ou échec de Bean Validation |
| `401` | Jeton absent, expiré ou invalide |
| `403` | Jeton valide, rôle insuffisant ou non-propriétaire de la ressource |
| `404` | Ressource introuvable (ou masquée à un non-propriétaire) |
| `409` | Conflit : clé en double, transition d'état illégale, stock insuffisant, affectation impossible |
| `422` | Sémantiquement invalide : produit inconnu ou retiré du catalogue |
| `503` | Un service en aval est indisponible |

**Pagination** — paramètres de requête `page` (indexé à partir de 0), `size` (20 par défaut, 100 au
maximum), `sort` (`champ,asc|desc`). Enveloppe de réponse explicite :

```json
{
  "content": [],
  "page": 0, "size": 20, "totalElements": 137, "totalPages": 7,
  "first": true, "last": false
}
```

---

## 1. auth-service

| Méthode | Chemin | Corps de requête | Corps de réponse | Codes | Rôle |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | `RegisterRequest` | `UserResponse` | `201` `400` `409` | public |
| `POST` | `/api/v1/auth/login` | `LoginRequest` | `TokenResponse` | `200` `400` `401` | public |
| `POST` | `/api/v1/auth/refresh` | `RefreshRequest` | `TokenResponse` | `200` `401` | public |
| `POST` | `/api/v1/auth/logout` | `RefreshRequest` | — | `204` `401` | authentifié |
| `POST` | `/api/v1/auth/token` | `ServiceTokenRequest` | `TokenResponse` | `200` `401` | interne (client credentials) |
| `GET` | `/api/v1/auth/.well-known/jwks.json` | — | JWKS | `200` | public |
| `GET` | `/api/v1/users/me` | — | `UserResponse` | `200` `401` | authentifié |
| `GET` | `/api/v1/users` | — (`?page&size&q`) | `PagedResponse<UserResponse>` | `200` `403` | `ADMIN` |
| `GET` | `/api/v1/users/{id}` | — | `UserResponse` | `200` `403` `404` | `ADMIN` ou soi-même |
| `PUT` | `/api/v1/users/{id}/roles` | `UpdateRolesRequest` | `UserResponse` | `200` `400` `403` `404` | `ADMIN` |
| `PATCH` | `/api/v1/users/{id}/status` | `UpdateUserStatusRequest` | `UserResponse` | `200` `403` `404` | `ADMIN` |

### Charges utiles

```jsonc
// RegisterRequest — @NotBlank @Email ; password @Size(min=10) + un chiffre + une majuscule
{ "email": "aya@example.com", "password": "Str0ngPass!", "firstName": "Aya", "lastName": "Bennani" }

// LoginRequest
{ "email": "aya@example.com", "password": "Str0ngPass!" }

// TokenResponse
{ "accessToken": "eyJhbGciOi...", "refreshToken": "b7f2...c91", "tokenType": "Bearer", "expiresIn": 900 }

// UserResponse — ne contient jamais passwordHash
{ "id": "3f9a...", "email": "aya@example.com", "firstName": "Aya", "lastName": "Bennani",
  "enabled": true, "roles": ["ROLE_CLIENT"], "createdAt": "2026-03-01T09:00:00Z" }

// UpdateRolesRequest — @NotEmpty, valeurs validées contre la table des rôles
{ "roles": ["ROLE_CLIENT", "ROLE_WAREHOUSE_MANAGER"] }
```

Règles : `register` accorde toujours `ROLE_CLIENT` — un rôle ne peut jamais être auto-attribué. `login`
renvoie le même message `401` pour un email inconnu et pour un mot de passe erroné (aucune énumération
d'utilisateurs). `refresh` fait tourner le jeton : l'ancien est révoqué dans la même transaction.

---

## 2. catalog-service

| Méthode | Chemin | Corps de requête | Corps de réponse | Codes | Rôle |
|---|---|---|---|---|---|
| `GET` | `/api/v1/products` | — (`?page&size&categoryId&q&status`) | `PagedResponse<ProductSummaryResponse>` | `200` | public |
| `GET` | `/api/v1/products/{id}` | — | `ProductResponse` | `200` `404` | public |
| `POST` | `/api/v1/products/batch` | `ProductBatchRequest` | `List<ProductSnapshotResponse>` | `200` `400` | `SERVICE`, `ADMIN` |
| `POST` | `/api/v1/products` | `CreateProductRequest` | `ProductResponse` | `201` `400` `409` `422` | `ADMIN` |
| `PUT` | `/api/v1/products/{id}` | `UpdateProductRequest` | `ProductResponse` | `200` `400` `404` `422` | `ADMIN` |
| `PATCH` | `/api/v1/products/{id}/status` | `UpdateProductStatusRequest` | `ProductResponse` | `200` `404` | `ADMIN` |
| `DELETE` | `/api/v1/products/{id}` | — | — | `204` `404` | `ADMIN` |
| `GET` | `/api/v1/categories` | — (`?tree=true`) | `List<CategoryResponse>` | `200` | public |
| `GET` | `/api/v1/categories/{id}` | — | `CategoryResponse` | `200` `404` | public |
| `POST` | `/api/v1/categories` | `CreateCategoryRequest` | `CategoryResponse` | `201` `400` `409` | `ADMIN` |
| `PUT` | `/api/v1/categories/{id}` | `UpdateCategoryRequest` | `CategoryResponse` | `200` `400` `404` | `ADMIN` |
| `DELETE` | `/api/v1/categories/{id}` | — | — | `204` `404` `409` | `ADMIN` |

### Charges utiles

```jsonc
// CreateProductRequest
{ "sku": "PAL-TRK-2500", "name": "Transpalette 2500 kg", "description": "Transpalette manuel.",
  "brand": "Logimove", "categoryId": "c1a4...", "price": { "amount": 349.90, "currency": "EUR" },
  "attributes": { "capacityKg": "2500", "forkLengthMm": "1150" },
  "dimensions": { "lengthMm": 1550, "widthMm": 685, "heightMm": 1230, "weightG": 78000 },
  "images": ["https://cdn.example/pal-trk-2500.jpg"] }

// ProductBatchRequest — utilisé par order-service, @NotEmpty @Size(max=100)
{ "productIds": ["8f2b...", "1d7c..."] }

// ProductSnapshotResponse — délibérément minimal : exactement ce dont une ligne de commande a besoin
[ { "id": "8f2b...", "sku": "PAL-TRK-2500", "name": "Transpalette 2500 kg",
    "price": { "amount": 349.90, "currency": "EUR" }, "status": "ACTIVE" } ]

// CategoryResponse (?tree=true imbrique les enfants)
{ "id": "c1a4...", "name": "Transpalettes", "slug": "pallet-trucks", "parentId": "b70f...",
  "path": "handling/pallet-trucks",
  "attributeSchema": [ { "key": "capacityKg", "label": "Capacité (kg)", "type": "NUMBER", "required": true } ],
  "children": [] }
```

Règles : les `attributes` sont validés contre l'`attributeSchema` de la catégorie ; un attribut requis
manquant ou une clé inconnue produit un `422`. `DELETE /products/{id}` est une **suppression logique**
(`status = DISCONTINUED`) afin que les commandes existantes conservent une référence résoluble.
`DELETE /categories/{id}` renvoie `409` lorsque la catégorie possède encore des enfants ou des produits.

---

## 3. inventory-service

### Entrepôts

| Méthode | Chemin | Corps de requête | Corps de réponse | Codes | Rôle |
|---|---|---|---|---|---|
| `GET` | `/api/v1/warehouses` | — (`?active&page&size`) | `PagedResponse<WarehouseResponse>` | `200` `401` | authentifié |
| `GET` | `/api/v1/warehouses/{id}` | — | `WarehouseResponse` | `200` `404` | authentifié |
| `POST` | `/api/v1/warehouses` | `CreateWarehouseRequest` | `WarehouseResponse` | `201` `400` `409` | `ADMIN` |
| `PUT` | `/api/v1/warehouses/{id}` | `UpdateWarehouseRequest` | `WarehouseResponse` | `200` `400` `404` | `ADMIN` |
| `PATCH` | `/api/v1/warehouses/{id}/status` | `{ "active": false }` | `WarehouseResponse` | `200` `404` | `ADMIN` |
| `GET` | `/api/v1/warehouses/{id}/stock` | — (`?lowStock&page&size`) | `PagedResponse<StockItemResponse>` | `200` `403` `404` | `WAREHOUSE_MANAGER`, `ADMIN` |

### Stock et mouvements

| Méthode | Chemin | Corps de requête | Corps de réponse | Codes | Rôle |
|---|---|---|---|---|---|
| `GET` | `/api/v1/stock` | — (`?productId&warehouseId&page&size`) | `PagedResponse<StockItemResponse>` | `200` `403` | `WAREHOUSE_MANAGER`, `ADMIN` |
| `PUT` | `/api/v1/stock` | `UpsertStockItemRequest` | `StockItemResponse` | `200` `201` `400` `404` | `WAREHOUSE_MANAGER` |
| `POST` | `/api/v1/stock/movements` | `CreateStockMovementRequest` | `StockMovementResponse` | `201` `400` `404` `409` | `WAREHOUSE_MANAGER` |
| `GET` | `/api/v1/stock/movements` | — (`?warehouseId&productId&type&from&to&page&size`) | `PagedResponse<StockMovementResponse>` | `200` `403` | `WAREHOUSE_MANAGER`, `ADMIN` |

### Disponibilité et réservations (l'API de la saga)

| Méthode | Chemin | Corps de requête | Corps de réponse | Codes | Rôle |
|---|---|---|---|---|---|
| `POST` | `/api/v1/inventory/availability` | `AvailabilityRequest` | `AvailabilityResponse` | `200` `400` | `SERVICE`, `WAREHOUSE_MANAGER`, `ADMIN` |
| `POST` | `/api/v1/inventory/reservations` | `CreateReservationRequest` | `ReservationResponse` | `201` `400` `409` | `SERVICE` |
| `GET` | `/api/v1/inventory/reservations/{id}` | — | `ReservationResponse` | `200` `404` | `SERVICE`, `ADMIN` |
| `POST` | `/api/v1/inventory/reservations/{id}/confirm` | — | `ReservationResponse` | `200` `404` `409` | `SERVICE` |
| `POST` | `/api/v1/inventory/reservations/{id}/cancel` | — | `ReservationResponse` | `200` `404` `409` | `SERVICE` |

### Charges utiles

```jsonc
// CreateWarehouseRequest — latitude/longitude @NotNull et bornées
{ "code": "WH-CASA-01", "name": "Hub Casablanca",
  "address": { "line1": "Zone industrielle Sidi Bernoussi", "city": "Casablanca",
               "postalCode": "20600", "country": "MA" },
  "latitude": 33.573100, "longitude": -7.589800 }

// WarehouseResponse
{ "id": "a11e...", "code": "WH-CASA-01", "name": "Hub Casablanca", "address": { }, 
  "latitude": 33.573100, "longitude": -7.589800, "active": true, "distinctProducts": 42 }

// UpsertStockItemRequest — fixe un niveau absolu (chargement initial / inventaire)
{ "warehouseId": "a11e...", "productId": "8f2b...", "quantityOnHand": 120, "reorderThreshold": 20 }

// StockItemResponse — availableQuantity est dérivé, jamais stocké
{ "id": "77aa...", "warehouseId": "a11e...", "warehouseCode": "WH-CASA-01", "productId": "8f2b...",
  "quantityOnHand": 120, "quantityReserved": 14, "availableQuantity": 106,
  "reorderThreshold": 20, "lowStock": false, "updatedAt": "2026-09-01T11:02:00Z" }

// CreateStockMovementRequest — ajustement relatif ; type dans {INBOUND, OUTBOUND, ADJUSTMENT}
{ "warehouseId": "a11e...", "productId": "8f2b...", "type": "INBOUND",
  "quantity": 50, "reference": "DN-2026-0912" }

// AvailabilityRequest
{ "productIds": ["8f2b...", "1d7c..."] }

// AvailabilityResponse — le stock ET les coordonnées en un seul appel, pour que le moteur
// d'affectation n'ait aucun second aller-retour à faire
{ "warehouses": [
    { "warehouseId": "a11e...", "code": "WH-CASA-01", "latitude": 33.5731, "longitude": -7.5898,
      "availability": { "8f2b...": 6, "1d7c...": 10 } },
    { "warehouseId": "b22f...", "code": "WH-RABA-01", "latitude": 34.0209, "longitude": -6.8416,
      "availability": { "8f2b...": 12, "1d7c...": 9 } } ] }

// CreateReservationRequest — `reference` est la clé d'idempotence (contrainte d'unicité)
{ "reference": "ORD-2026-000123", "ttlSeconds": 300,
  "segments": [ { "warehouseId": "b22f...",
                  "lines": [ { "productId": "8f2b...", "quantity": 10 },
                             { "productId": "1d7c...", "quantity": 4 } ] } ] }

// ReservationResponse
{ "id": "9c3d...", "reference": "ORD-2026-000123", "status": "ACTIVE",
  "expiresAt": "2026-09-03T10:20:30Z",
  "segments": [ { "warehouseId": "b22f...", "lines": [ { "productId": "8f2b...", "quantity": 10 } ] } ] }

// 409 en cas de stock insuffisant — indique précisément ce qui manque
{ "type": "https://logistics.local/problems/insufficient-stock", "title": "Insufficient stock",
  "status": 409, "detail": "Stock changed since the availability snapshot.",
  "shortages": [ { "warehouseId": "b22f...", "productId": "8f2b...", "requested": 10, "available": 7 } ] }
```

Règles : rejouer `POST /reservations` avec la même `reference` renvoie la réservation **existante** avec un
`200` au lieu d'en créer une seconde. `confirm` et `cancel` ne sont légaux que depuis l'état `ACTIVE` ; tout
autre état renvoie `409` avec le statut courant. Une réservation `EXPIRED` ne peut plus être confirmée.

---

## 4. order-service

| Méthode | Chemin | Corps de requête | Corps de réponse | Codes | Rôle |
|---|---|---|---|---|---|
| `POST` | `/api/v1/orders` | `CreateOrderRequest` | `OrderResponse` | `201` `400` `401` `409` `422` `503` | `CLIENT`, `ADMIN` |
| `GET` | `/api/v1/orders` | — (`?status&from&to&page&size`) | `PagedResponse<OrderSummaryResponse>` | `200` `401` | `CLIENT` (ses commandes), `ADMIN`/`WAREHOUSE_MANAGER` (toutes) |
| `GET` | `/api/v1/orders/{id}` | — | `OrderResponse` | `200` `403` `404` | propriétaire, `ADMIN`, `WAREHOUSE_MANAGER` |
| `GET` | `/api/v1/orders/{id}/allocations` | — | `List<OrderAllocationResponse>` | `200` `403` `404` | propriétaire, `ADMIN`, `WAREHOUSE_MANAGER` |
| `GET` | `/api/v1/orders/{id}/history` | — | `List<OrderStatusHistoryResponse>` | `200` `403` `404` | propriétaire, `ADMIN` |
| `POST` | `/api/v1/orders/{id}/cancel` | `CancelOrderRequest` | `OrderResponse` | `200` `403` `404` `409` | propriétaire (avant `SHIPPED`), `ADMIN` |
| `PATCH` | `/api/v1/orders/{id}/status` | `UpdateOrderStatusRequest` | `OrderResponse` | `200` `400` `403` `404` `409` | `WAREHOUSE_MANAGER`, `ADMIN` |
| `POST` | `/api/v1/orders/allocation-preview` | `AllocationPreviewRequest` | `AllocationPreviewResponse` | `200` `400` `409` | `ADMIN`, `WAREHOUSE_MANAGER` |
| `GET` | `/api/v1/orders/allocation-strategies` | — | `List<String>` | `200` | authentifié |

### Charges utiles

```jsonc
// CreateOrderRequest — @Valid ; `strategy` facultatif, revient à la valeur par défaut configurée
{ "lines": [ { "productId": "8f2b...", "quantity": 10 },
             { "productId": "1d7c...", "quantity": 4 } ],
  "deliveryAddress": { "line1": "12 rue des Oliviers", "city": "Casablanca",
                       "postalCode": "20250", "country": "MA",
                       "latitude": 33.589900, "longitude": -7.603900 },
  "strategy": "nearest" }
```

Validation : `lines` `@NotEmpty` `@Size(max=50)`, `quantity` `@Min(1)` `@Max(1000)`, `productId`
`@NotBlank`, aucun `productId` en double, latitude/longitude `@NotNull` et bornées, `country` sur deux
lettres ISO-3166. **`customerId` est délibérément absent de la charge utile** — il est lu depuis le JWT.

```jsonc
// OrderResponse — 201, Location: /api/v1/orders/{id}
{ "id": "5b1c...", "orderNumber": "ORD-2026-000123", "status": "CONFIRMED",
  "customerId": "3f9a...", "createdAt": "2026-09-03T10:15:30Z",
  "total": { "amount": 3859.60, "currency": "EUR" },
  "deliveryAddress": { "line1": "12 rue des Oliviers", "city": "Casablanca",
                       "postalCode": "20250", "country": "MA",
                       "latitude": 33.5899, "longitude": -7.6039 },
  "allocationStrategy": "nearest", "splitShipment": false,
  "lines": [ { "id": "aa01...", "productId": "8f2b...", "sku": "PAL-TRK-2500",
               "name": "Transpalette 2500 kg", "quantity": 10,
               "unitPrice": { "amount": 349.90, "currency": "EUR" },
               "lineTotal": { "amount": 3499.00, "currency": "EUR" } } ],
  "allocations": [ { "warehouseId": "b22f...", "warehouseCode": "WH-RABA-01",
                     "shipmentSequence": 1, "distanceKm": 85.19,
                     "lines": [ { "orderLineId": "aa01...", "productId": "8f2b...", "quantity": 10 } ] } ] }

// 409 lorsque l'affectation est impossible — la commande existe à l'état REJECTED et est renvoyée
{ "type": "https://logistics.local/problems/allocation-failed", "title": "Allocation failed",
  "status": 409, "detail": "No combination of warehouses can fulfil this order.",
  "orderNumber": "ORD-2026-000126",
  "unsatisfiedLines": [ { "productId": "8f2b...", "requested": 10, "availableAcrossNetwork": 6 } ] }

// 422 lorsqu'un produit est inconnu ou retiré du catalogue
{ "type": "https://logistics.local/problems/product-unavailable", "title": "Product unavailable",
  "status": 422, "detail": "Some products are unknown or discontinued.",
  "unavailableProducts": [ { "productId": "1d7c...", "reason": "DISCONTINUED" } ] }

// UpdateOrderStatusRequest — seuls SHIPPED et DELIVERED sont acceptés ici
{ "status": "SHIPPED", "reason": "Remis au transporteur" }

// AllocationPreviewRequest — exécute le moteur SANS rien persister.
// Intérêt pour la démonstration : comparer les deux stratégies sur la même commande.
{ "lines": [ { "productId": "8f2b...", "quantity": 10 } ],
  "destination": { "latitude": 33.5899, "longitude": -7.6039 },
  "strategies": ["nearest", "single-shipment"] }

// AllocationPreviewResponse
{ "results": [
    { "strategy": "nearest", "feasible": true, "splitShipment": false,
      "segments": [ { "warehouseCode": "WH-RABA-01", "distanceKm": 85.19,
                      "lines": [ { "productId": "8f2b...", "quantity": 10 } ] } ] },
    { "strategy": "single-shipment", "feasible": true, "splitShipment": false,
      "segments": [ { "warehouseCode": "WH-TANG-01", "distanceKm": 290.50,
                      "lines": [ { "productId": "8f2b...", "quantity": 10 } ] } ] } ] }
```

> `POST /orders/allocation-preview` existe pour une raison qui dépasse le confort : il rend la règle métier
> **observable dans l'interface**. Pouvoir montrer, en direct, que deux stratégies produisent deux plans
> différents sur la même commande est la démonstration la plus forte du patron Strategy en entretien.

---

## 5. Endpoints techniques

| Méthode | Chemin | Rôle | Accès |
|---|---|---|---|
| `GET` | `/actuator/health` | Vivacité/disponibilité, utilisé par les healthchecks Docker Compose | interne |
| `GET` | `/actuator/info` | Version de build et identifiant de commit | interne |
| `GET` | `/v3/api-docs` | Spécification OpenAPI 3 par service | interne |
| `GET` | `/swagger-ui.html` | Documentation interactive | `ADMIN` (profil dev : ouvert) |

## 6. Matrice rôle × endpoint (récapitulatif)

| Capacité | `CLIENT` | `WAREHOUSE_MANAGER` | `ADMIN` | `SERVICE` |
|---|:--:|:--:|:--:|:--:|
| Parcourir le catalogue | ✓ | ✓ | ✓ | ✓ |
| Gérer produits / catégories | | | ✓ | |
| Consulter les entrepôts | ✓ | ✓ | ✓ | ✓ |
| Gérer les entrepôts | | | ✓ | |
| Consulter / ajuster le stock | | ✓ | ✓ | |
| Disponibilité, réservations | | lecture | lecture | ✓ |
| Créer une commande | ✓ | | ✓ | |
| Consulter ses propres commandes | ✓ | ✓ | ✓ | |
| Consulter toutes les commandes | | ✓ | ✓ | |
| Changer le statut d'une commande (expédier/livrer) | | ✓ | ✓ | |
| Annuler une commande | les siennes, avant `SHIPPED` | | ✓ | |
| Prévisualiser une affectation | | ✓ | ✓ | |
| Gérer les utilisateurs et les rôles | | | ✓ | |
