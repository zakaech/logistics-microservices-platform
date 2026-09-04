# Multi-Warehouse Logistics Platform

A distributed platform managing product catalogue, per-warehouse stock and customer orders across
several geographically distributed warehouses. Its functional core is not CRUD but the **warehouse
allocation engine**: when an order is placed, the system decides which warehouse - or which
combination of warehouses - fulfils it.

Java 17 · Spring Boot 3 · Spring Cloud Gateway · PostgreSQL · MongoDB · Angular · Docker

## Status

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Design: architecture, data model, UML, REST contract | Done - see [docs/](docs/) |
| 1 | Repository skeleton, `auth-service`, `api-gateway`, Docker Compose | Done |
| 2 | `catalog-service` (MongoDB) and `inventory-service` (stock, reservations) | Done |
| 3 | `order-service`: allocation engine, saga, simulation endpoint | Done |
| 4 | Angular front-end | Not started |


## Architecture in ten lines

Five backend services behind a single entry point. `api-gateway` authenticates every request at the
edge and routes it; each service re-validates the token itself and applies its own role checks, so
no service depends on network isolation for its security. `auth-service` is the only holder of the
JWT signing key and publishes the public half as a JWKS. Each service owns its own database and no
service reads another's tables - cross-service references are logical, without foreign keys.
PostgreSQL backs the transactional contexts (identity, stock, orders); MongoDB backs the catalogue,
the one context whose documents have genuinely variable structure.

The reasoning behind each of those choices is written down in [docs/01-architecture.md](docs/01-architecture.md).

## Documentation

| Document | Contents |
|---|---|
| [01-architecture.md](docs/01-architecture.md) | Diagrams, service boundaries, polyglot persistence, security model, decision log |
| [02-data-model.md](docs/02-data-model.md) | Tables, collections, columns, constraints, indexes |
| [03-order-service-design.md](docs/03-order-service-design.md) | UML class diagram and the allocation algorithm |
| [04-sequence-create-order.md](docs/04-sequence-create-order.md) | End-to-end sequence and failure paths |
| [05-api-contract.md](docs/05-api-contract.md) | Every endpoint, payload, status code and required role |
| [06-repository-layout.md](docs/06-repository-layout.md) | Module layout, package layering, Git conventions |

## Running the stack

Requirements: Docker with Compose v2. Nothing else - the JDK and Maven live inside the build image.

```bash
cp .env.example .env      # then edit: every value in it is a placeholder
docker compose up --build
```

The gateway then answers on <http://localhost:8080>. It is the only backend port published;
`docker-compose.override.yml` additionally exposes PostgreSQL, MongoDB and `auth-service` for local
debugging.

> **The repository path must contain only ASCII characters.** Docker Compose v2.3x+ builds through
> `buildx bake`, which derives the gRPC header `x-docker-expose-session-sharedkey` from the build
> context path. A non-ASCII character anywhere in that path - an accent in a folder name, for
> instance - makes the session handshake fail with:
>
> ```
> failed to dial gRPC: ... header key "x-docker-expose-session-sharedkey"
> contains value with non-printable ASCII characters
> ```
>
> A plain `docker build` is unaffected, which makes this confusing to diagnose. Clone into a path
> such as `C:\dev\logistics-platform`. A Windows junction pointing at an accented directory does
> **not** work: Compose resolves it back to the real path.

### Signing keys

With `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` left empty, `auth-service` generates an **ephemeral** RSA
pair at startup and logs a warning: convenient for a first run, but every restart invalidates issued
tokens. To pin a real pair:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
base64 -w0 private.pem    # macOS: base64 -i private.pem
base64 -w0 public.pem
```

Paste the two single-line values into `.env`. Both keys are git-ignored, and `.env` is too.

## Trying it out

```bash
# Register (always granted ROLE_CLIENT, whatever the request says)
curl -sX POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"aya@example.com","password":"Str0ngPass!","firstName":"Aya","lastName":"Bennani"}'

# Log in
TOKEN=$(curl -sX POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"aya@example.com","password":"Str0ngPass!"}' | jq -r .accessToken)

# Call a protected endpoint
curl -s http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN"

# Without a token: 401 as application/problem+json, from the gateway
curl -si http://localhost:8080/api/v1/users/me | head -5

# The public keys anyone can verify a signature with
curl -s http://localhost:8080/api/v1/auth/.well-known/jwks.json
```

## Building and testing locally

Requires JDK 17 or later and Maven 3.9+.

```bash
mvn -B verify          # compiles both modules and runs the unit tests
mvn -B -pl auth-service test
```

API documentation is served by each service at `/swagger-ui.html` (reachable on
<http://localhost:8081/swagger-ui.html> with the override file active).

## Repository layout

```
logistics-platform/
├── pom.xml                  # aggregator: imports the Spring Boot and Spring Cloud BOMs
├── docker-compose.yml       # databases + services
├── .env.example             # every required variable, documented, no real value
├── docs/                    # design documentation (phase 0)
├── infrastructure/          # database initialisation scripts
├── api-gateway/             # routing, edge authentication, identity propagation
└── auth-service/            # identity, roles, JWT issuance
```

Each service follows the same layering: `controller` → `service` → `repository` → `domain`, with
`dto`, `mapper`, `exception` and `config` alongside. No JPA entity ever crosses a controller
boundary. See [docs/06-repository-layout.md](docs/06-repository-layout.md).

## Conventions

- Code, comments and commit messages in English; Conventional Commits (`feat(auth): ...`).
- Errors are RFC 7807 `application/problem+json`, everywhere, including from the gateway.
- No secret in source: configuration reads `${ENV_VAR}` placeholders, and a missing secret stops the
  service at startup rather than falling back to a default.
