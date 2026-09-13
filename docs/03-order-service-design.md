# 03 — Conception de order-service (diagramme de classes UML + moteur d'affectation)

C'est le service qui porte la valeur du projet. Tout le reste existe pour que cet algorithme reçoive des
entrées réalistes.

## 1. Diagramme de classes

```mermaid
classDiagram
    direction TB

    %% ---------- Couche présentation ----------
    class OrderController {
        -OrderService orderService
        -AllocationPreviewService previewService
        +createOrder(CreateOrderRequest, Authentication) ResponseEntity~OrderResponse~
        +getOrder(UUID, Authentication) OrderResponse
        +listOrders(OrderFilter, Pageable, Authentication) PagedResponse~OrderSummaryResponse~
        +cancelOrder(UUID, Authentication) OrderResponse
        +updateStatus(UUID, UpdateOrderStatusRequest) OrderResponse
        +previewAllocation(AllocationPreviewRequest) AllocationPreviewResponse
    }
    class GlobalExceptionHandler {
        +handleNotFound(ResourceNotFoundException) ProblemDetail
        +handleAllocationFailed(AllocationFailedException) ProblemDetail
        +handleValidation(MethodArgumentNotValidException) ProblemDetail
        +handleIllegalTransition(IllegalOrderStateException) ProblemDetail
        +handleUpstream(UpstreamServiceException) ProblemDetail
    }

    %% ---------- Couche application ----------
    class OrderService {
        <<interface>>
        +create(CreateOrderCommand) Order
        +findById(UUID, UserContext) Order
        +search(OrderFilter, Pageable, UserContext) Page~Order~
        +cancel(UUID, UserContext) Order
        +changeStatus(UUID, OrderStatus, UserContext) Order
    }
    class OrderServiceImpl {
        -OrderRepository orderRepository
        -OrderNumberGenerator numberGenerator
        -CatalogClient catalogClient
        -InventoryClient inventoryClient
        -AllocationStrategyResolver strategyResolver
        -OrderStatusHistoryRecorder historyRecorder
        +create(CreateOrderCommand) Order
    }
    class AllocationStrategyResolver {
        -Map~String, WarehouseAllocationStrategy~ strategies
        -String defaultStrategyName
        +resolve(String name) WarehouseAllocationStrategy
        +availableStrategies() Set~String~
    }

    %% ---------- Moteur d'affectation (domaine, pur) ----------
    class WarehouseAllocationStrategy {
        <<interface>>
        +name() String
        +allocate(AllocationRequest) AllocationPlan
    }
    class AbstractAllocationStrategy {
        <<abstract>>
        #DistanceCalculator distanceCalculator
        +allocate(AllocationRequest) AllocationPlan
        #fullCoverageComparator(AllocationRequest) Comparator~WarehouseCandidate~
        #splitComparator(AllocationRequest) Comparator~WarehouseCandidate~
        #allowsSplit() boolean
    }
    class NearestWarehouseAllocationStrategy {
        +name() String
        #fullCoverageComparator(AllocationRequest) Comparator~WarehouseCandidate~
        #splitComparator(AllocationRequest) Comparator~WarehouseCandidate~
    }
    class SingleShipmentAllocationStrategy {
        +name() String
        #fullCoverageComparator(AllocationRequest) Comparator~WarehouseCandidate~
        #splitComparator(AllocationRequest) Comparator~WarehouseCandidate~
    }
    class DistanceCalculator {
        <<interface>>
        +distanceKm(GeoPoint from, GeoPoint to) double
    }
    class HaversineDistanceCalculator {
        +distanceKm(GeoPoint from, GeoPoint to) double
    }

    class AllocationRequest {
        <<record>>
        +List~RequestedLine~ lines
        +GeoPoint destination
        +List~WarehouseCandidate~ candidates
    }
    class RequestedLine {
        <<record>>
        +UUID orderLineId
        +String productId
        +int quantity
    }
    class WarehouseCandidate {
        <<record>>
        +UUID warehouseId
        +String warehouseCode
        +GeoPoint location
        +Map~String, Integer~ availableByProduct
        +available(String productId) int
        +canCoverFully(List~RequestedLine~) boolean
        +residualAfter(List~RequestedLine~) long
    }
    class AllocationPlan {
        <<record>>
        +String strategyName
        +boolean splitShipment
        +List~AllocationSegment~ segments
        +validateCovers(List~RequestedLine~) void
    }
    class AllocationSegment {
        <<record>>
        +UUID warehouseId
        +String warehouseCode
        +double distanceKm
        +int shipmentSequence
        +List~SegmentLine~ lines
    }
    class SegmentLine {
        <<record>>
        +UUID orderLineId
        +String productId
        +int quantity
    }
    class AllocationFailedException {
        +List~UnsatisfiedLine~ unsatisfied
    }

    %% ---------- Modèle du domaine ----------
    class Order {
        -UUID id
        -String orderNumber
        -UUID customerId
        -OrderStatus status
        -Money total
        -DeliveryAddress deliveryAddress
        -String allocationStrategy
        -boolean splitShipment
        -String reservationReference
        -List~OrderLine~ lines
        -List~OrderAllocation~ allocations
        +addLine(OrderLine) void
        +applyAllocation(AllocationPlan) void
        +transitionTo(OrderStatus, String reason) void
        +recomputeTotal() void
        +isOwnedBy(UUID customerId) boolean
    }
    class OrderLine {
        -UUID id
        -String productId
        -String productSku
        -String productName
        -Money unitPrice
        -int quantity
        +lineTotal() Money
    }
    class OrderAllocation {
        -UUID id
        -UUID warehouseId
        -String warehouseCode
        -int shipmentSequence
        -BigDecimal distanceKm
        -List~OrderAllocationLine~ lines
    }
    class OrderAllocationLine {
        -UUID id
        -OrderLine orderLine
        -int quantity
    }
    class OrderStatus {
        <<enumeration>>
        CREATED
        ALLOCATED
        CONFIRMED
        SHIPPED
        DELIVERED
        CANCELLED
        REJECTED
        +canTransitionTo(OrderStatus) boolean
    }
    class DeliveryAddress {
        <<embeddable>>
        -String line1
        -String city
        -String postalCode
        -String country
        -GeoPoint location
    }
    class GeoPoint {
        <<embeddable>>
        -BigDecimal latitude
        -BigDecimal longitude
    }
    class Money {
        <<embeddable>>
        -BigDecimal amount
        -String currency
        +add(Money) Money
        +multiply(int) Money
    }

    %% ---------- Infrastructure ----------
    class OrderRepository {
        <<interface>>
        +findByOrderNumber(String) Optional~Order~
        +findByCustomerId(UUID, Pageable) Page~Order~
        +existsByOrderNumber(String) boolean
    }
    class CatalogClient {
        <<interface>>
        +fetchProducts(Set~String~ productIds) Map~String, ProductSnapshot~
    }
    class CatalogRestClient {
        -RestClient restClient
        +fetchProducts(Set~String~) Map~String, ProductSnapshot~
    }
    class InventoryClient {
        <<interface>>
        +fetchAvailability(Set~String~ productIds) List~WarehouseCandidate~
        +reserve(ReservationCommand) ReservationHandle
        +confirm(UUID reservationId) void
        +cancel(UUID reservationId) void
    }
    class InventoryRestClient {
        -RestClient restClient
        -ServiceTokenProvider tokenProvider
    }
    class OrderMapper {
        <<interface>>
        +toResponse(Order) OrderResponse
        +toSummary(Order) OrderSummaryResponse
        +toCommand(CreateOrderRequest, UUID customerId) CreateOrderCommand
    }

    %% ---------- Relations ----------
    OrderController --> OrderService : utilise
    OrderController --> OrderMapper : utilise
    OrderServiceImpl ..|> OrderService
    OrderServiceImpl --> OrderRepository
    OrderServiceImpl --> CatalogClient
    OrderServiceImpl --> InventoryClient
    OrderServiceImpl --> AllocationStrategyResolver
    AllocationStrategyResolver o-- WarehouseAllocationStrategy : registre
    AbstractAllocationStrategy ..|> WarehouseAllocationStrategy
    NearestWarehouseAllocationStrategy --|> AbstractAllocationStrategy
    SingleShipmentAllocationStrategy --|> AbstractAllocationStrategy
    AbstractAllocationStrategy --> DistanceCalculator
    HaversineDistanceCalculator ..|> DistanceCalculator
    AbstractAllocationStrategy ..> AllocationRequest : consomme
    AbstractAllocationStrategy ..> AllocationPlan : produit
    AbstractAllocationStrategy ..> AllocationFailedException : lève
    AllocationRequest *-- RequestedLine
    AllocationRequest *-- WarehouseCandidate
    AllocationPlan *-- AllocationSegment
    AllocationSegment *-- SegmentLine
    Order *-- OrderLine
    Order *-- OrderAllocation
    OrderAllocation *-- OrderAllocationLine
    OrderAllocationLine --> OrderLine
    Order --> OrderStatus
    Order *-- DeliveryAddress
    DeliveryAddress *-- GeoPoint
    OrderLine *-- Money
    Order ..> AllocationPlan : applique
    CatalogRestClient ..|> CatalogClient
    InventoryRestClient ..|> InventoryClient
```

### Pourquoi le diagramme a cette forme

- **Le moteur est pur.** `AbstractAllocationStrategy` prend une `AllocationRequest` (de simples records) et
  renvoie un `AllocationPlan`. Aucun repository, aucun client HTTP, aucune annotation Spring, aucune horloge.
  Conséquence : chaque scénario d'affectation est un simple test JUnit, sans mock et sans conteneur.
- **Le domaine n'est pas anémique.** `Order.applyAllocation(...)`, `Order.transitionTo(...)` et
  `OrderStatus.canTransitionTo(...)` portent les règles ; la couche service orchestre mais ne décide pas.
- **Des records pour le moteur, des entités pour la persistance.** Les types d'affectation sont des `record`
  immuables (Java 17), ce qui supprime toute une catégorie de bogues de mutation accidentelle.
- **Deux ports, deux adaptateurs.** `CatalogClient` et `InventoryClient` sont des interfaces détenues par le
  domaine ; les classes `*RestClient` sont le seul endroit qui connaisse HTTP, JSON et les URL des autres
  services.

## 2. L'algorithme d'affectation

### 2.1 Entrées et sorties

```
ENTRÉE  lines        : [(orderLineId, productId, quantity)]           -- quantités demandées
        destination  : (latitude, longitude)                          -- point de livraison
        candidates   : [(warehouseId, code, location, available{productId -> qty})]
                       available = quantity_on_hand - quantity_reserved, entrepôts actifs uniquement

SORTIE  AllocationPlan(strategyName, splitShipment, segments[])
        segment = (warehouseId, code, distanceKm, shipmentSequence, lines[(orderLineId, productId, qty)])

ERREUR  AllocationFailedException(unsatisfied[]) lorsque la quantité totale disponible sur l'ensemble
        des entrepôts est inférieure à ce qu'exige la commande
```

### 2.2 Squelette partagé (Template Method — `AbstractAllocationStrategy.allocate`)

```
ÉTAPE 0  Rejeter les requêtes vides et les quantités non strictement positives
                                                                  -> IllegalArgumentException (déjà filtré par @Valid)

ÉTAPE 1  ÉLIGIBILITÉ
         candidates := candidates dont l'entrepôt est actif
                       et contribue au moins une unité à au moins une ligne
         si candidates est vide                                   -> lever AllocationFailedException(toutes les lignes)

ÉTAPE 2  ENSEMBLE DE COUVERTURE TOTALE
         full := { w dans candidates | pour chaque ligne l : w.available(l.productId) >= l.quantity }

ÉTAPE 3  CAS DE L'EXPÉDITION UNIQUE  (préféré)
         si full n'est pas vide :
             best := min(full, fullCoverageComparator)             -- fourni par la sous-classe
             renvoyer AllocationPlan(name, splitShipment = false,
                                     [ segment(best, toutes les lignes, seq = 1) ])

ÉTAPE 4  CAS DU FRACTIONNEMENT
         si non allowsSplit()                                      -> lever AllocationFailedException(toutes les lignes)
         remaining := copie mutable de lines
         ordered   := trier(candidates, splitComparator)           -- fourni par la sous-classe
         segments  := []
         seq       := 1
         pour w dans ordered :
             taken := []
             pour l dans remaining où l.remaining > 0 :
                 q := min(l.remaining, w.available(l.productId))
                 si q > 0 :
                     taken.ajouter((l.orderLineId, l.productId, q))
                     l.remaining -= q
             si taken n'est pas vide :
                 segments.ajouter(segment(w, taken, seq)); seq += 1
             si tous les remaining == 0 : sortir de la boucle

ÉTAPE 5  CONTRÔLE DE FAISABILITÉ
         si un remaining > 0                                       -> lever AllocationFailedException(remaining)
         renvoyer AllocationPlan(name, splitShipment = segments.size > 1, segments)

POSTCONDITION (vérifiée par AllocationPlan.validateCovers)
         pour chaque ligne l : somme des quantités affectées sur tous les segments == l.quantity
```

Complexité : `O(W log W + W x L)`, où `W` est le nombre d'entrepôts et `L` le nombre de lignes de commande.
Le tri domine. Pour des tailles réalistes (quelques dizaines d'entrepôts, quelques dizaines de lignes) c'est
négligeable, et tout le calcul se fait en mémoire à partir d'un unique instantané de disponibilité — **un
seul appel distant, pas un par entrepôt**.

### 2.3 Les deux implémentations interchangeables

Les deux sous-classes implémentent la même interface et ne diffèrent **que** par deux comparateurs — ce qui
est toute la raison d'être du découpage en Template Method.

#### `NearestWarehouseAllocationStrategy` (`nearest`, par défaut)

Optimise le **délai de livraison et le coût de transport** : parcourir les sites du plus proche au plus
lointain, prendre ce que chacun peut donner, jusqu'à ce que la commande soit servie.

| Ordre de comparaison | Critère | Sens | Justification |
|---|---|---|---|
| 1 | Distance de Haversine jusqu'au point de livraison | croissant | L'entrepôt le plus proche expédie le plus vite et le moins cher |
| 2 | Couverture de la commande | décroissant | À distance égale, le site qui donne le plus évite une expédition supplémentaire inutile |
| 3 | Code de l'entrepôt | croissant | **Départage déterministe — rend l'algorithme reproductible et testable** |

Le compromis est explicite : cette stratégie fractionnera une commande entre deux sites proches même
lorsqu'un unique entrepôt légèrement plus lointain aurait pu l'expédier entière. C'est le bon choix lorsque
le transport est facturé à la distance, et le mauvais lorsqu'il est facturé à l'expédition — ce qui est
précisément la raison d'être de la seconde stratégie.

#### `SingleShipmentAllocationStrategy` (`single-shipment`)

Optimise le **nombre de colis** plutôt que les kilomètres parcourus. Une seule expédition, c'est une seule
tournée de préparation, un seul bon de livraison, une seule remise au transporteur, et une seule livraison
que le client doit attendre.

Deux phases :

1. **Expédition unique.** Si un entrepôt peut couvrir toutes les lignes à lui seul, l'utiliser — le plus
   proche de ceux qui le peuvent. C'est le cas pour lequel la stratégie existe, et elle ira chercher un
   entrepôt *plus lointain* pour l'obtenir.
2. **Le moins d'expéditions possible.** Sinon, prendre gloutonnement l'entrepôt qui couvre le plus de ce
   qui reste, puis recommencer. C'est l'heuristique gloutonne classique du problème de couverture
   d'ensembles : sans optimalité prouvée, mais le problème exact est NP-difficile et une recherche
   exhaustive sur les sous-ensembles d'entrepôts n'a pas sa place dans une requête de passage de commande.

| Ordre de comparaison | Critère | Sens | Justification |
|---|---|---|---|
| 1 | Couverture de ce qui reste à servir | décroissant | Moins d'unités restantes, c'est moins d'expéditions |
| 2 | Distance de Haversine | croissant | À utilité égale, préférer malgré tout le plus proche |
| 3 | Code de l'entrepôt | croissant | Départage déterministe |

La couverture est recalculée sur ce qui reste *encore* à servir après chaque prise, et non classée une fois
pour toutes au départ : un classement statique continuerait de favoriser un entrepôt détenant un produit
déjà entièrement affecté.

**Le contraste est tout l'intérêt.** Sur la même commande et le même stock, `nearest` fractionne entre deux
sites proches tandis que `single-shipment` envoie un seul colis depuis un site plus lointain. Les deux plans
sont complets et corrects ; chacun est meilleur sur l'axe qu'il optimise. **Une troisième stratégie
(`cheapest`, `fastest-carrier`, …) est une nouvelle classe plus une annotation `@Component` — aucune classe
existante ne change.** C'est le principe ouvert/fermé, concrètement.

### 2.4 Sélection de la stratégie (Registry / Factory)

Spring injecte une `List<WarehouseAllocationStrategy>` dans `AllocationStrategyResolver`, qui les indexe par
`name()`. Ordre de sélection :

1. Le champ `strategy` de la requête, lorsqu'il est présent et connu — utilisé par le back-office et par
   l'endpoint `/allocation-preview` pour comparer les deux stratégies sur la même commande.
2. Sinon, la valeur par défaut configurée : `logistics.allocation.default-strategy=nearest`.
3. Un nom inconnu produit un problème `400` énumérant les stratégies disponibles (jamais un repli
   silencieux).

Le nom de la stratégie est **persisté sur la commande** (`orders.allocation_strategy`), de sorte qu'une
décision passée puisse être expliquée des mois plus tard.

### 2.5 Cas limites et traitement de chacun

| Cas | Traitement |
|---|---|
| Commande vide / quantité ≤ 0 | Rejetée par `@Valid` avant d'atteindre le moteur → `400` |
| `productId` en double dans la requête | Rejetée → `400` (fusionner silencieusement masquerait un bogue client) |
| Produit inconnu ou `DISCONTINUED` | La résolution auprès de `catalog-service` fait échouer le lot → `422` nommant les identifiants fautifs |
| Aucun entrepôt ne détient l'un des produits | `AllocationFailedException` avec toutes les lignes insatisfaites → `409` |
| Disponibilité partielle sur le réseau | Plan fractionné sur plusieurs entrepôts |
| Disponibilité totale inférieure au besoin | `AllocationFailedException` avec les lignes insatisfaites → `409`, commande stockée en `REJECTED` avec un motif |
| Tous les entrepôts inactifs | Traité comme « aucun candidat » → `409` |
| Stock modifié entre l'instantané et la réservation | `inventory-service` renvoie `409` ; `OrderServiceImpl` relit la disponibilité et rejoue l'affectation **une seule fois**, puis rejette. Reprise bornée — aucune boucle infinie |
| Deux commandes identiques soumises en concurrence | Numéros de commande indépendants → réservations indépendantes ; la contrainte `CHECK` arbitre, le perdant reçoit `409` |
| Rejeu de la *même* création de commande | La `reference` de réservation est unique → idempotent, aucune double réservation |

### 2.6 Exemple déroulé (illustratif, ce ne sont pas des mesures)

Commande : 10 × `P1`, 4 × `P2`. Destination : une adresse de livraison à Casablanca, distincte du hub.

Les distances ci-dessous sont les distances orthodromiques entre les villes concernées, arrondies ; les
quantités, elles, sont fictives et choisies pour faire apparaître chaque branche de l'algorithme.

| Entrepôt | Ville | `P1` disponible | `P2` disponible | Distance (km) |
|---|---|---|---|---|
| `WH-CASA-01` | Casablanca | 6 | 10 | ≈ 2 |
| `WH-RABA-01` | Rabat | 12 | 9 | ≈ 85 |
| `WH-TANG-01` | Tanger | 20 | 0 | ≈ 291 |

- **Étape 2** — couverture totale : `WH-CASA-01` échoue sur `P1` (6 < 10), `WH-TANG-01` échoue sur `P2`
  (0 < 4). Seul `WH-RABA-01` couvre l'ensemble.
- **Étape 3** — `nearest` renvoie une **expédition unique depuis `WH-RABA-01`** bien que Casablanca soit
  plus proche : couvrir la commande en une seule expédition prime sur la distance, exactement comme la règle
  l'exige.
- Si `WH-RABA-01` est rendu inactif, l'ensemble de couverture totale est vide et `nearest` bascule sur un
  **fractionnement** : `WH-CASA-01` expédie 6 × `P1` + 4 × `P2`, puis `WH-TANG-01` expédie les 4 × `P1`
  restants.
- Sur la même entrée, `single-shipment` refuse tout fractionnement tant qu'un site peut couvrir la commande :
  il expédie tout depuis `WH-RABA-01` même lorsque Casablanca est plus proche, et ne bascule sur le
  fractionnement au moindre nombre d'expéditions que si aucun site ne le peut. Même entrée, deux plans
  défendables — c'est la démonstration que les stratégies sont réellement interchangeables.

## 3. Machine à états de la commande

`OrderStatus.canTransitionTo` est une table de transitions statique ; `Order.transitionTo` refuse un
mouvement illégal par une `IllegalOrderStateException` → `409`. Chaque transition acceptée ajoute une ligne
à `order_status_history`.

| Depuis | Cibles autorisées | Déclencheur |
|---|---|---|
| `CREATED` | `ALLOCATED`, `REJECTED` | Réservation réussie / affectation impossible |
| `ALLOCATED` | `CONFIRMED`, `CANCELLED` | Réservation confirmée / compensation |
| `CONFIRMED` | `SHIPPED`, `CANCELLED` | Responsable d'entrepôt / annulation avec mouvement d'entrée compensatoire |
| `SHIPPED` | `DELIVERED` | Responsable d'entrepôt |
| `DELIVERED`, `CANCELLED`, `REJECTED` | — | États terminaux |

> Un véritable **patron State** (une classe par statut) a été envisagé puis écarté : avec sept statuts et
> aucun comportement variant au-delà des transitions autorisées, une table de transitions est plus simple et
> plus lisible. Savoir quand *ne pas* appliquer un patron fait partie de la conception.
