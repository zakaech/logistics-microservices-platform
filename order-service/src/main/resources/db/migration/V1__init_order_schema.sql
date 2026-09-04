-- ---------------------------------------------------------------------------
-- order-service - initial schema
--
-- Order lines carry a SNAPSHOT of the catalogue (sku, name, unit price) rather
-- than only a product reference. That denormalisation is deliberate: a product
-- renamed or repriced next month must not rewrite what a customer agreed to,
-- and a past order must stay readable even if catalog-service is unavailable.
-- ---------------------------------------------------------------------------

-- Order numbers come from a sequence, not from a row count: counting is not
-- concurrency-safe and would reuse a number after a deletion.
CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders
(
    id                    UUID           NOT NULL,
    order_number          VARCHAR(20)    NOT NULL,
    customer_id           UUID           NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    total_amount          NUMERIC(12, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    delivery_line1        VARCHAR(180)   NOT NULL,
    delivery_city         VARCHAR(80)    NOT NULL,
    delivery_postal_code  VARCHAR(16)    NOT NULL,
    delivery_country      VARCHAR(2)     NOT NULL,
    delivery_latitude     NUMERIC(9, 6)  NOT NULL,
    delivery_longitude    NUMERIC(9, 6)  NOT NULL,
    allocation_strategy   VARCHAR(40),
    split_shipment        BOOLEAN        NOT NULL DEFAULT FALSE,
    reservation_reference VARCHAR(64),
    reservation_id        UUID,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ux_orders_order_number UNIQUE (order_number),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('CREATED', 'ALLOCATED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT ck_orders_latitude CHECK (delivery_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_orders_longitude CHECK (delivery_longitude BETWEEN -180 AND 180)
);

-- "My orders", newest first: the most frequent read of the whole service.
CREATE INDEX ix_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_status ON orders (status);
CREATE INDEX ix_orders_reservation_reference ON orders (reservation_reference);

CREATE TABLE order_lines
(
    id           UUID           NOT NULL,
    order_id     UUID           NOT NULL,
    product_id   VARCHAR(36)    NOT NULL,
    product_sku  VARCHAR(32)    NOT NULL,
    product_name VARCHAR(160)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    quantity     INTEGER        NOT NULL,
    line_total   NUMERIC(12, 2) NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id),
    -- One line per product: a duplicate is a client bug, not something to merge silently.
    CONSTRAINT ux_order_lines_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_lines_unit_price CHECK (unit_price > 0)
);

CREATE INDEX ix_order_lines_order_id ON order_lines (order_id);
CREATE INDEX ix_order_lines_product_id ON order_lines (product_id);

-- ---------------------------------------------------------------------------
-- The engine's decision, persisted rather than recomputed. Availability moves,
-- so replaying the algorithm later would give a different answer; distance_km
-- records the number the decision actually used.
-- ---------------------------------------------------------------------------
CREATE TABLE order_allocations
(
    id                UUID          NOT NULL,
    order_id          UUID          NOT NULL,
    warehouse_id      UUID          NOT NULL,
    warehouse_code    VARCHAR(16)   NOT NULL,
    shipment_sequence INTEGER       NOT NULL,
    distance_km       NUMERIC(8, 2),
    created_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_order_allocations PRIMARY KEY (id),
    CONSTRAINT ux_order_allocations UNIQUE (order_id, warehouse_id),
    CONSTRAINT fk_order_allocations_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_allocations_sequence CHECK (shipment_sequence > 0)
);

CREATE INDEX ix_order_allocations_order_id ON order_allocations (order_id);

CREATE TABLE order_allocation_lines
(
    id            UUID    NOT NULL,
    allocation_id UUID    NOT NULL,
    order_line_id UUID    NOT NULL,
    quantity      INTEGER NOT NULL,
    CONSTRAINT pk_order_allocation_lines PRIMARY KEY (id),
    CONSTRAINT ux_order_allocation_lines UNIQUE (allocation_id, order_line_id),
    CONSTRAINT fk_allocation_lines_allocation FOREIGN KEY (allocation_id) REFERENCES order_allocations (id) ON DELETE CASCADE,
    CONSTRAINT fk_allocation_lines_order_line FOREIGN KEY (order_line_id) REFERENCES order_lines (id) ON DELETE CASCADE,
    CONSTRAINT ck_allocation_lines_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_allocation_lines_order_line ON order_allocation_lines (order_line_id);

-- Append-only. The status column says where an order is; this says how it got
-- there and why, which is what makes a rejection explainable to a customer.
CREATE TABLE order_status_history
(
    id          UUID        NOT NULL,
    order_id    UUID        NOT NULL,
    from_status VARCHAR(16),
    to_status   VARCHAR(16) NOT NULL,
    reason      VARCHAR(255),
    changed_by  VARCHAR(64),
    changed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX ix_status_history_order_time ON order_status_history (order_id, changed_at);
