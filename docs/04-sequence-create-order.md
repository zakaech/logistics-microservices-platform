# 04 — Sequence: "Create an order", end to end

Scenario covered: authentication, catalogue verification, availability check, warehouse allocation, stock
reservation, stock decrement, confirmation — plus the failure paths, because a saga is only credible when its
compensations are drawn.

## 1. Nominal flow

```mermaid
sequenceDiagram
    autonumber
    actor U as User (browser)
    participant UI as Angular SPA
    participant GW as api-gateway
    participant AU as auth-service
    participant OR as order-service
    participant CA as catalog-service
    participant IN as inventory-service
    participant PG as order_db
    participant PI as inventory_db

    rect rgb(238, 244, 252)
    note over U, AU: Phase 1 — Authentication (once, then the token is reused)
    U->>UI: submits email + password
    UI->>GW: POST /api/v1/auth/login
    GW->>AU: forwards (public route, no token required)
    AU->>AU: load user, BCrypt.matches(password, hash)
    AU->>AU: sign JWT (sub, roles, exp 15 min) + create refresh token (hash stored)
    AU-->>GW: 200 {accessToken, refreshToken, expiresIn}
    GW-->>UI: 200
    UI->>UI: keep the access token in memory, refresh token in an httpOnly cookie
    end

    rect rgb(240, 248, 240)
    note over U, PG: Phase 2 — Order submission and validation
    U->>UI: validates the cart + delivery address
    UI->>GW: POST /api/v1/orders (Bearer JWT)
    GW->>GW: JwtAuthenticationFilter: signature, exp, issuer
    GW->>GW: add X-Request-Id
    GW->>OR: forwards, token untouched
    OR->>OR: resource server re-validates the JWT
    OR->>OR: @PreAuthorize("hasRole('CLIENT')") + @Valid on CreateOrderRequest
    OR->>OR: customerId := JWT sub (never read from the body)
    end

    rect rgb(252, 248, 236)
    note over OR, CA: Phase 3 — Catalogue verification (one batch call, not N)
    OR->>GW: POST /api/v1/products/batch {productIds}
    GW->>CA: forwards
    CA->>CA: findAllById + filter status = ACTIVE
    CA-->>OR: 200 [{id, sku, name, price, status}]
    alt an id is unknown or DISCONTINUED
        OR-->>UI: 422 problem+json {unavailableProducts:[...]}
    end
    OR->>OR: build order lines from the SNAPSHOT (sku, name, unit price)
    OR->>OR: total := sum(unitPrice x quantity) — computed server-side
    OR->>PG: INSERT order (status = CREATED) + order_lines
    end

    rect rgb(248, 240, 248)
    note over OR, IN: Phase 4 — Availability and allocation
    OR->>GW: POST /api/v1/inventory/availability {productIds}
    GW->>IN: forwards
    IN->>PI: SELECT stock_items JOIN warehouses WHERE product_id IN (...) AND active
    IN-->>OR: 200 [{warehouseId, code, lat, lon, items:{productId: available}}]
    OR->>OR: strategy := AllocationStrategyResolver.resolve(request.strategy or default)
    OR->>OR: plan := strategy.allocate(AllocationRequest) — pure, in memory
    note right of OR: filter → full-coverage set →<br/>single shipment, else split →<br/>feasibility check
    alt total availability insufficient
        OR->>PG: UPDATE order SET status = REJECTED + status_history(reason)
        OR-->>UI: 409 problem+json {unsatisfiedLines:[...]}
    end
    end

    rect rgb(236, 246, 250)
    note over OR, PI: Phase 5 — Reservation (the atomic step of the saga)
    OR->>GW: POST /api/v1/inventory/reservations<br/>{reference: orderNumber, segments}
    GW->>IN: forwards
    IN->>PI: BEGIN
    IN->>PI: SELECT stock_items FOR UPDATE (ordered by id — deadlock-free)
    IN->>PI: UPDATE quantity_reserved += q (CHECK reserved <= on_hand)
    IN->>PI: INSERT reservation(ACTIVE, expires_at = now + 5 min) + reservation_lines
    IN->>PI: INSERT stock_movements(type = RESERVATION)
    IN->>PI: COMMIT
    IN-->>OR: 201 {reservationId, status: ACTIVE, expiresAt}
    OR->>PG: UPDATE order SET status = ALLOCATED, reservation_reference,<br/>allocation_strategy, split_shipment + INSERT order_allocations
    end

    rect rgb(238, 250, 238)
    note over OR, PI: Phase 6 — Confirmation (stock actually leaves)
    OR->>GW: POST /api/v1/inventory/reservations/{id}/confirm
    GW->>IN: forwards
    IN->>PI: BEGIN
    IN->>PI: UPDATE quantity_on_hand -= q, quantity_reserved -= q
    IN->>PI: INSERT stock_movements(type = OUTBOUND, reference = orderNumber)
    IN->>PI: UPDATE reservation SET status = CONFIRMED
    IN->>PI: COMMIT
    IN-->>OR: 200 {status: CONFIRMED}
    OR->>PG: UPDATE order SET status = CONFIRMED + status_history
    OR-->>GW: 201 Created, Location: /api/v1/orders/{id}
    GW-->>UI: 201 OrderResponse (lines + allocations + warehouses)
    UI-->>U: confirmation screen, one block per shipment
    end
```

### What the numbered steps demonstrate

| Step | Point being made |
|---|---|
| 8 | The refresh token never reaches JavaScript (httpOnly cookie); the access token is short-lived and kept in memory only |
| 11–13 | Double validation: the gateway authenticates, the service authorises. Removing the gateway would not open a hole |
| 14 | `customerId` comes from the signed token, never from the request body — otherwise anyone could order on someone else's account |
| 16–20 | **One batch call** rather than one call per product: the N+1 problem exists over HTTP too |
| 21–22 | The order stores a **price snapshot**; a later catalogue price change does not rewrite history, and the total is server-computed |
| 25–27 | **One availability call** returning stock *and* warehouse coordinates, so the engine needs no second round trip |
| 29 | The allocation itself is pure and in-memory — the only part of the flow that is unit-testable with no infrastructure |
| 35–40 | The reservation is a single ACID transaction with rows locked in a **stable order** (by id), which removes deadlocks between concurrent orders |
| 45–48 | On-hand stock decreases only at confirmation; the ledger (`stock_movements`) records every step |

## 2. Failure paths and compensations

```mermaid
sequenceDiagram
    autonumber
    participant OR as order-service
    participant IN as inventory-service
    participant PG as order_db

    rect rgb(253, 240, 240)
    note over OR, IN: Case A — stock taken by another order between snapshot and reservation
    OR->>IN: POST /reservations
    IN-->>OR: 409 insufficient stock {productId, warehouseId}
    OR->>IN: POST /inventory/availability (fresh snapshot)
    IN-->>OR: 200 updated availability
    OR->>OR: re-run allocate() — bounded retry, ONCE
    alt the second attempt succeeds
        OR->>IN: POST /reservations
        IN-->>OR: 201
    else it fails again
        OR->>PG: status = REJECTED, reason = "stock unavailable"
        OR-->>OR: 409 to the client
    end
    end

    rect rgb(253, 244, 236)
    note over OR, IN: Case B — confirmation fails (inventory-service down or timeout)
    OR->>IN: POST /reservations/{id}/confirm
    IN --x OR: timeout / 5xx
    OR->>IN: POST /reservations/{id}/cancel  (compensating action)
    alt cancellation succeeds
        IN-->>OR: 200 CANCELLED, quantity_reserved -= q
    else cancellation also fails
        note over IN: the reservation TTL expires it automatically<br/>(scheduled job) — stock is never lost
    end
    OR->>PG: status = CANCELLED, reason = "inventory unavailable"
    OR-->>OR: 503 to the client
    end

    rect rgb(240, 240, 250)
    note over OR, IN: Case C — order-service crashes between reservation and confirmation
    note over IN: reservation stays ACTIVE with expires_at
    note over IN: the scheduled job flips it to EXPIRED and releases quantity_reserved
    note over PG: the order stays ALLOCATED; a reconciliation job cancels<br/>orders left in ALLOCATED past the TTL
    end
```

**Why this is enough here.** No message broker, no event sourcing, no distributed transaction manager: the
combination of an *idempotent* reservation (unique `reference`), a *bounded* retry, an explicit *compensating
action*, and a *TTL* as the last line of defence keeps stock consistent without introducing infrastructure
the project does not need. The honest limitation, worth stating rather than hiding: a network partition at
the exact moment of confirmation can leave the order in `ALLOCATED` until the reconciliation job runs. That
is eventual consistency, and it is a deliberate trade-off.

## 3. Non-obvious decisions in this flow

1. **The order is persisted as `CREATED` *before* allocation.** A failed allocation still leaves a `REJECTED`
   order with its reason — a customer can be told why, and the case can be analysed. Discarding the attempt
   would destroy that information.
2. **Confirmation is immediate, in the same use case.** A real e-commerce system would confirm at payment,
   keeping the reservation `ACTIVE` in between. The protocol here is already the right one; only the trigger
   would change. Adding a payment step later means calling `confirm` from another handler — no redesign.
3. **The retry is bounded to one attempt.** Retrying indefinitely under contention turns a stockout into a
   thundering herd. One retry absorbs the ordinary race; a second failure is genuine unavailability.
4. **`X-Request-Id` propagates through every hop** and appears in every log line and every error body — with
   four services, a support case is unusable without it.
