-- ---------------------------------------------------------------------------
-- auth-service - initial schema
--
-- Flyway owns the schema; Hibernate runs with ddl-auto=validate and never
-- alters a table. Every constraint below is enforced by the engine, so a bug
-- in the application cannot produce an inconsistent row.
-- ---------------------------------------------------------------------------

CREATE TABLE users
(
    id            UUID         NOT NULL,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- Uniqueness on the lower-cased address: 'Aya@x.com' and 'aya@x.com' are the
-- same account. A plain UNIQUE(email) would let both exist.
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));

CREATE TABLE roles
(
    id          UUID         NOT NULL,
    name        VARCHAR(40)  NOT NULL,
    description VARCHAR(160),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT ux_roles_name UNIQUE (name)
);

CREATE TABLE user_roles
(
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

CREATE TABLE refresh_tokens
(
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------------
-- Seed the roles the platform recognises.
--
-- Only roles are seeded, never a user: an account would require committing a
-- password hash, which is a credential shared by every clone of the repository.
-- The first administrator is created at startup from environment variables.
-- ---------------------------------------------------------------------------
INSERT INTO roles (id, name, description)
VALUES (gen_random_uuid(), 'ROLE_ADMIN', 'Full administration of the platform'),
       (gen_random_uuid(), 'ROLE_WAREHOUSE_MANAGER', 'Manages warehouses, stock and shipments'),
       (gen_random_uuid(), 'ROLE_CLIENT', 'Places and follows orders'),
       (gen_random_uuid(), 'ROLE_SERVICE', 'Technical account for service-to-service calls');
