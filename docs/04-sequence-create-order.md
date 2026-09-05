# 04 — Séquence : « Créer une commande », de bout en bout

Scénario couvert : authentification, vérification du catalogue, contrôle de disponibilité, affectation aux
entrepôts, réservation de stock, décrément du stock, confirmation — ainsi que les chemins d'échec, car une
saga n'est crédible que lorsque ses compensations sont dessinées.

## 1. Flux nominal

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur (navigateur)
    participant UI as SPA Angular
    participant GW as api-gateway
    participant AU as auth-service
    participant OR as order-service
    participant CA as catalog-service
    participant IN as inventory-service
    participant PG as order_db
    participant PI as inventory_db

    rect rgb(238, 244, 252)
    note over U, AU: Phase 1 — Authentification (une fois, puis le jeton est réutilisé)
    U->>UI: saisit email + mot de passe
    UI->>GW: POST /api/v1/auth/login
    GW->>AU: transmet (route publique, aucun jeton requis)
    AU->>AU: charge l'utilisateur, BCrypt.matches(password, hash)
    AU->>AU: signe le JWT (sub, roles, exp 15 min) + crée le refresh token (haché en base)
    AU-->>GW: 200 {accessToken, refreshToken, expiresIn}
    GW-->>UI: 200
    UI->>UI: garde l'access token en mémoire, le refresh token dans un cookie httpOnly
    end

    rect rgb(240, 248, 240)
    note over U, PG: Phase 2 — Soumission et validation de la commande
    U->>UI: valide le panier + l'adresse de livraison
    UI->>GW: POST /api/v1/orders (Bearer JWT)
    GW->>GW: JwtAuthenticationFilter : signature, exp, issuer
    GW->>GW: ajoute X-Request-Id
    GW->>OR: transmet, jeton inchangé
    OR->>OR: le resource server revalide le JWT
    OR->>OR: @PreAuthorize("hasRole('CLIENT')") + @Valid sur CreateOrderRequest
    OR->>OR: customerId := sub du JWT (jamais lu depuis le corps)
    end

    rect rgb(252, 248, 236)
    note over OR, CA: Phase 3 — Vérification du catalogue (un appel groupé, pas N)
    OR->>GW: POST /api/v1/products/batch {productIds}
    GW->>CA: transmet
    CA->>CA: findAllById + filtre status = ACTIVE
    CA-->>OR: 200 [{id, sku, name, price, status}]
    alt un identifiant est inconnu ou DISCONTINUED
        OR-->>UI: 422 problem+json {unavailableProducts:[...]}
    end
    OR->>OR: construit les lignes depuis l'INSTANTANÉ (sku, nom, prix unitaire)
    OR->>OR: total := somme(prixUnitaire x quantité) — calculé côté serveur
    OR->>PG: INSERT order (status = CREATED) + order_lines
    end

    rect rgb(248, 240, 248)
    note over OR, IN: Phase 4 — Disponibilité et affectation
    OR->>GW: POST /api/v1/inventory/availability {productIds}
    GW->>IN: transmet
    IN->>PI: SELECT stock_items JOIN warehouses WHERE product_id IN (...) AND active
    IN-->>OR: 200 [{warehouseId, code, lat, lon, items:{productId: available}}]
    OR->>OR: strategy := AllocationStrategyResolver.resolve(request.strategy ou défaut)
    OR->>OR: plan := strategy.allocate(AllocationRequest) — pure, en mémoire
    note right of OR: filtrage → ensemble de couverture totale →<br/>expédition unique, sinon fractionnement →<br/>contrôle de faisabilité
    alt disponibilité totale insuffisante
        OR->>PG: UPDATE order SET status = REJECTED + status_history(motif)
        OR-->>UI: 409 problem+json {unsatisfiedLines:[...]}
    end
    end

    rect rgb(236, 246, 250)
    note over OR, PI: Phase 5 — Réservation (l'étape atomique de la saga)
    OR->>GW: POST /api/v1/inventory/reservations<br/>{reference: orderNumber, segments}
    GW->>IN: transmet
    IN->>PI: BEGIN
    IN->>PI: SELECT stock_items FOR UPDATE (ordonné par id — sans interblocage)
    IN->>PI: UPDATE quantity_reserved += q (CHECK reserved <= on_hand)
    IN->>PI: INSERT reservation(ACTIVE, expires_at = now + 5 min) + reservation_lines
    IN->>PI: INSERT stock_movements(type = RESERVATION)
    IN->>PI: COMMIT
    IN-->>OR: 201 {reservationId, status: ACTIVE, expiresAt}
    OR->>PG: UPDATE order SET status = ALLOCATED, reservation_reference,<br/>allocation_strategy, split_shipment + INSERT order_allocations
    end

    rect rgb(238, 250, 238)
    note over OR, PI: Phase 6 — Confirmation (le stock sort réellement)
    OR->>GW: POST /api/v1/inventory/reservations/{id}/confirm
    GW->>IN: transmet
    IN->>PI: BEGIN
    IN->>PI: UPDATE quantity_on_hand -= q, quantity_reserved -= q
    IN->>PI: INSERT stock_movements(type = OUTBOUND, reference = orderNumber)
    IN->>PI: UPDATE reservation SET status = CONFIRMED
    IN->>PI: COMMIT
    IN-->>OR: 200 {status: CONFIRMED}
    OR->>PG: UPDATE order SET status = CONFIRMED + status_history
    OR-->>GW: 201 Created, Location: /api/v1/orders/{id}
    GW-->>UI: 201 OrderResponse (lignes + affectations + entrepôts)
    UI-->>U: écran de confirmation, un bloc par expédition
    end
```

### Ce que démontrent les étapes numérotées

| Étape | Point démontré |
|---|---|
| 8 | Le refresh token n'atteint jamais JavaScript (cookie httpOnly) ; l'access token est à durée courte et reste en mémoire uniquement |
| 11–13 | Double validation : la gateway authentifie, le service autorise. Retirer la gateway n'ouvrirait aucune brèche |
| 14 | Le `customerId` provient du jeton signé, jamais du corps de la requête — sans quoi n'importe qui pourrait commander sur le compte d'autrui |
| 16–20 | **Un seul appel groupé** plutôt qu'un appel par produit : le problème N+1 existe aussi en HTTP |
| 21–22 | La commande stocke un **instantané des prix** ; un changement de tarif ultérieur au catalogue ne réécrit pas l'histoire, et le total est calculé côté serveur |
| 25–27 | **Un seul appel de disponibilité** renvoyant le stock *et* les coordonnées des entrepôts, pour que le moteur n'ait aucun second aller-retour à faire |
| 29 | L'affectation elle-même est pure et en mémoire — la seule partie du flux testable unitairement sans aucune infrastructure |
| 35–40 | La réservation est une transaction ACID unique, avec les lignes verrouillées dans un **ordre stable** (par id), ce qui supprime les interblocages entre commandes concurrentes |
| 45–48 | Le stock physique ne décroît qu'à la confirmation ; le journal (`stock_movements`) enregistre chaque étape |

## 2. Chemins d'échec et compensations

```mermaid
sequenceDiagram
    autonumber
    participant OR as order-service
    participant IN as inventory-service
    participant PG as order_db

    rect rgb(253, 240, 240)
    note over OR, IN: Cas A — stock pris par une autre commande entre l'instantané et la réservation
    OR->>IN: POST /reservations
    IN-->>OR: 409 stock insuffisant {productId, warehouseId}
    OR->>IN: POST /inventory/availability (nouvel instantané)
    IN-->>OR: 200 disponibilité actualisée
    OR->>OR: rejoue allocate() — nouvelle tentative bornée, UNE SEULE FOIS
    alt la seconde tentative réussit
        OR->>IN: POST /reservations
        IN-->>OR: 201
    else elle échoue de nouveau
        OR->>PG: status = REJECTED, motif = "stock indisponible"
        OR-->>OR: 409 renvoyé au client
    end
    end

    rect rgb(253, 244, 236)
    note over OR, IN: Cas B — la confirmation échoue (inventory-service arrêté ou en timeout)
    OR->>IN: POST /reservations/{id}/confirm
    IN --x OR: timeout / 5xx
    OR->>IN: POST /reservations/{id}/cancel  (action compensatoire)
    alt l'annulation réussit
        IN-->>OR: 200 CANCELLED, quantity_reserved -= q
    else l'annulation échoue aussi
        note over IN: le TTL de la réservation l'expire automatiquement<br/>(tâche planifiée) — le stock n'est jamais perdu
    end
    OR->>PG: status = CANCELLED, motif = "inventaire indisponible"
    OR-->>OR: 503 renvoyé au client
    end

    rect rgb(240, 240, 250)
    note over OR, IN: Cas C — order-service tombe entre la réservation et la confirmation
    note over IN: la réservation reste ACTIVE avec son expires_at
    note over IN: la tâche planifiée la bascule en EXPIRED et libère quantity_reserved
    note over PG: la commande reste ALLOCATED ; une tâche de réconciliation annule<br/>les commandes laissées en ALLOCATED au-delà du TTL
    end
```

**Pourquoi cela suffit ici.** Aucun broker de messages, aucun event sourcing, aucun gestionnaire de
transactions distribuées : la combinaison d'une réservation *idempotente* (`reference` unique), d'une
tentative de reprise *bornée*, d'une *action compensatoire* explicite et d'un *TTL* en dernière ligne de
défense maintient la cohérence du stock sans introduire une infrastructure dont le projet n'a pas besoin. La
limite honnête, qu'il vaut mieux énoncer que masquer : une partition réseau à l'instant précis de la
confirmation peut laisser la commande en `ALLOCATED` jusqu'au passage de la tâche de réconciliation. C'est de
la cohérence à terme, et c'est un compromis assumé.

## 3. Décisions non évidentes dans ce flux

1. **La commande est persistée en `CREATED` *avant* l'affectation.** Une affectation échouée laisse malgré
   tout une commande `REJECTED` avec son motif — le client peut savoir pourquoi, et le cas peut être analysé.
   Abandonner la tentative détruirait cette information.
2. **La confirmation est immédiate, dans le même cas d'utilisation.** Un vrai système e-commerce confirmerait
   au paiement, en gardant la réservation `ACTIVE` entre-temps. Le protocole retenu ici est déjà le bon ;
   seul le déclencheur changerait. Ajouter une étape de paiement plus tard revient à appeler `confirm` depuis
   un autre handler — aucune refonte.
3. **La reprise est bornée à une tentative.** Réessayer indéfiniment sous contention transforme une rupture
   de stock en effet d'avalanche. Une reprise absorbe la course ordinaire ; un second échec traduit une
   indisponibilité réelle.
4. **`X-Request-Id` se propage à chaque saut** et apparaît dans chaque ligne de journal et chaque corps
   d'erreur — avec quatre services, un ticket de support est inexploitable sans lui.
