-- ---------------------------------------------------------------------------
-- inventory-service - initial schema
--
-- The constraints here are not decoration. quantity_reserved <= quantity_on_hand
-- is the invariant that makes overselling impossible at the engine level: even a
-- bug in the application cannot commit a row that violates it.
-- ---------------------------------------------------------------------------

CREATE TABLE warehouses
(
    id            UUID          NOT NULL,
    code          VARCHAR(16)   NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    latitude      NUMERIC(9, 6) NOT NULL,
    longitude     NUMERIC(9, 6) NOT NULL,
    address_line1 VARCHAR(180)  NOT NULL,
    city          VARCHAR(80)   NOT NULL,
    postal_code   VARCHAR(16)   NOT NULL,
    country       VARCHAR(2)    NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_warehouses PRIMARY KEY (id),
    CONSTRAINT ux_warehouses_code UNIQUE (code),
    CONSTRAINT ck_warehouses_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_warehouses_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX ix_warehouses_active ON warehouses (active);

-- ---------------------------------------------------------------------------
-- Stock levels. One row per (warehouse, product).
--
-- product_id is a LOGICAL reference to catalog_db.products._id: there is no
-- foreign key, on purpose, because the target lives in another service's
-- database. Validity is enforced at the application boundary.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_items
(
    id                UUID        NOT NULL,
    warehouse_id      UUID        NOT NULL,
    product_id        VARCHAR(36) NOT NULL,
    quantity_on_hand  INTEGER     NOT NULL DEFAULT 0,
    quantity_reserved INTEGER     NOT NULL DEFAULT 0,
    reorder_threshold INTEGER     NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_items PRIMARY KEY (id),
    CONSTRAINT ux_stock_items_warehouse_product UNIQUE (warehouse_id, product_id),
    CONSTRAINT fk_stock_items_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_items_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_stock_items_reserved CHECK (quantity_reserved >= 0),
    CONSTRAINT ck_stock_items_threshold CHECK (reorder_threshold >= 0),
    -- The anti-oversell invariant, enforced by the database itself.
    CONSTRAINT ck_stock_items_reserved_le_on_hand CHECK (quantity_reserved <= quantity_on_hand)
);

CREATE INDEX ix_stock_items_product_id ON stock_items (product_id);
CREATE INDEX ix_stock_items_warehouse_id ON stock_items (warehouse_id);

-- ---------------------------------------------------------------------------
-- Append-only movement ledger. Rows are never updated or deleted:
-- quantity_on_hand is the projection of this history, which is what makes a
-- stock discrepancy explainable rather than merely visible.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_movements
(
    id            UUID        NOT NULL,
    stock_item_id UUID        NOT NULL,
    type          VARCHAR(20) NOT NULL,
    quantity      INTEGER     NOT NULL,
    reference     VARCHAR(64),
    created_by    VARCHAR(64) NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (stock_item_id) REFERENCES stock_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity <> 0),
    CONSTRAINT ck_stock_movements_type CHECK (type IN ('INBOUND', 'OUTBOUND', 'ADJUSTMENT', 'RESERVATION', 'RELEASE'))
);

CREATE INDEX ix_movements_item_time ON stock_movements (stock_item_id, occurred_at DESC);
CREATE INDEX ix_movements_reference ON stock_movements (reference);

-- ---------------------------------------------------------------------------
-- Reservations: the atomic step of the order saga.
--
-- reference is the order number, and its UNIQUE constraint is the idempotency
-- guarantee: replaying a reservation request cannot reserve the stock twice.
-- ---------------------------------------------------------------------------
CREATE TABLE reservations
(
    id         UUID        NOT NULL,
    reference  VARCHAR(64) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT ux_reservations_reference UNIQUE (reference),
    CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX ix_reservations_status_expiry ON reservations (status, expires_at);

CREATE TABLE reservation_lines
(
    id             UUID    NOT NULL,
    reservation_id UUID    NOT NULL,
    stock_item_id  UUID    NOT NULL,
    quantity       INTEGER NOT NULL,
    CONSTRAINT pk_reservation_lines PRIMARY KEY (id),
    CONSTRAINT ux_reservation_lines UNIQUE (reservation_id, stock_item_id),
    CONSTRAINT fk_reservation_lines_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_lines_item FOREIGN KEY (stock_item_id) REFERENCES stock_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reservation_lines_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_reservation_lines_item ON reservation_lines (stock_item_id);
