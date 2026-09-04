# 06 — Repository Layout (Maven multi-module monorepo)

## 1. Why a monorepo

Five backend modules plus a frontend, developed by one person, released together. A monorepo gives one clone,
one `mvn verify`, one `docker compose up`, and an atomic commit when a change crosses services (e.g. adding a
field to the availability contract touches both `inventory-service` and `order-service`).

The counter-argument — independent release cycles per service — does not apply here and would only add
friction. Worth saying explicitly: *the monorepo is a repository layout choice, not an architecture choice.*
The services remain independently deployable, and each has its own Dockerfile and its own database.

## 2. Top-level tree

```
logistics-platform/
├── pom.xml                          # aggregator + dependencyManagement (packaging: pom)
├── docker-compose.yml               # full stack: databases + 5 services + frontend
├── docker-compose.override.yml      # local dev: exposed ports, hot reload
├── .env.example                     # every required variable, documented, no real value
├── .gitignore                       # target/, node_modules/, .env, *.log, .idea/
├── .editorconfig
├── README.md                        # what it is, how to run it, architecture in 10 lines
├── docker-compose.override.yml      # local dev: publishes ports kept internal in the base file
│
├── docs/
│   ├── 01-architecture.md
│   ├── 02-data-model.md
│   ├── 03-order-service-design.md
│   ├── 04-sequence-create-order.md
│   ├── 05-api-contract.md
│   └── 06-repository-layout.md
│
├── infrastructure/
│   ├── postgres/
│   │   └── init/01-create-databases.sql     # 3 databases + 3 users, one per service
│   └── mongo/
│       └── init/01-create-user.js           # catalog_db user with least privilege
│
├── api-gateway/
├── auth-service/
├── catalog-service/
├── inventory-service/
├── order-service/
└── frontend/
```

## 3. Parent POM

```xml
<groupId>com.logistics</groupId>
<artifactId>logistics-platform</artifactId>
<packaging>pom</packaging>

<modules>
  <module>api-gateway</module>
  <module>auth-service</module>
  <module>catalog-service</module>
  <module>inventory-service</module>
  <module>order-service</module>
</modules>
```

Three deliberate choices:

1. **No `spring-boot-starter-parent` as `<parent>`.** The Spring Boot and Spring Cloud BOMs are *imported*
   into `<dependencyManagement>` instead, which keeps `logistics-platform` as the real parent and leaves room
   for our own plugin configuration. Versions are declared once, in properties, and inherited by every module.
2. **Every version pinned in the parent** — Java release, Spring Boot, Spring Cloud, MapStruct, testcontainers,
   Lombok. No module ever declares a dependency version of its own. Exact versions are chosen and frozen at
   Phase 1 from the current release train, not guessed here.

3. **No `shared-kernel` module** (decision D10). Each service owns its own `GlobalExceptionHandler`,
   `PagedResponse<T>`, resource-server configuration and role constants — roughly 150 duplicated lines per
   service. This duplication is deliberate: a shared technical module creates a release coupling, where
   changing one class forces a rebuild and a redeploy of all four services, which is exactly what a
   microservice split is meant to avoid. The duplicated code is boilerplate, not business logic; the day it
   starts to diverge, that divergence is a legitimate service-level decision rather than a merge conflict.

The `frontend` module is *not* a Maven module: mixing a Node build into the Maven reactor buys nothing and
slows every backend build. It has its own `package.json` and its own Dockerfile.

## 4. Canonical service structure (example: `order-service`)

The same layering applies to all four business services; only the domain differs.

```
order-service/
├── pom.xml
├── Dockerfile                       # multi-stage: maven build → eclipse-temurin JRE, non-root user
└── src/
    ├── main/
    │   ├── java/com/logistics/order/
    │   │   ├── OrderServiceApplication.java
    │   │   │
    │   │   ├── config/                        # Spring configuration only, no business logic
    │   │   │   ├── SecurityConfig.java        # resource server, role mapping, route rules
    │   │   │   ├── RestClientConfig.java      # timeouts, base URL, interceptors
    │   │   │   ├── JpaAuditingConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   └── AllocationProperties.java  # @ConfigurationProperties("logistics.allocation")
    │   │   │
    │   │   ├── controller/                    # HTTP only: bind, delegate, map, return
    │   │   │   ├── OrderController.java
    │   │   │   └── AllocationPreviewController.java
    │   │   │
    │   │   ├── service/                       # use cases (interfaces) + orchestration
    │   │   │   ├── OrderService.java
    │   │   │   ├── AllocationPreviewService.java
    │   │   │   ├── OrderNumberGenerator.java
    │   │   │   └── impl/
    │   │   │       ├── OrderServiceImpl.java
    │   │   │       └── AllocationPreviewServiceImpl.java
    │   │   │
    │   │   ├── domain/                        # the model — no Spring, no JSON annotations
    │   │   │   ├── entity/  Order · OrderLine · OrderAllocation · OrderAllocationLine · OrderStatusHistory
    │   │   │   ├── vo/      Money · GeoPoint · DeliveryAddress
    │   │   │   └── enums/   OrderStatus
    │   │   │
    │   │   ├── allocation/                    # THE business core — pure, framework-free
    │   │   │   ├── WarehouseAllocationStrategy.java
    │   │   │   ├── AbstractAllocationStrategy.java
    │   │   │   ├── AllocationStrategyResolver.java
    │   │   │   ├── impl/  NearestWarehouseAllocationStrategy · StockBalancingAllocationStrategy
    │   │   │   ├── model/ AllocationRequest · AllocationPlan · AllocationSegment
    │   │   │   │          SegmentLine · RequestedLine · WarehouseCandidate
    │   │   │   └── distance/ DistanceCalculator · HaversineDistanceCalculator
    │   │   │
    │   │   ├── repository/                    # Spring Data JPA interfaces + specifications
    │   │   │   ├── OrderRepository.java
    │   │   │   └── spec/OrderSpecifications.java
    │   │   │
    │   │   ├── client/                        # anti-corruption layer (ports + adapters)
    │   │   │   ├── CatalogClient.java · InventoryClient.java
    │   │   │   ├── impl/ CatalogRestClient.java · InventoryRestClient.java
    │   │   │   └── dto/  ProductSnapshot · AvailabilityView · ReservationCommand · ReservationHandle
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── request/  CreateOrderRequest · OrderLineRequest · UpdateOrderStatusRequest
    │   │   │   │             CancelOrderRequest · AllocationPreviewRequest
    │   │   │   ├── response/ OrderResponse · OrderSummaryResponse · OrderLineResponse
    │   │   │   │             OrderAllocationResponse · AllocationPreviewResponse · PagedResponse
    │   │   │   └── command/  CreateOrderCommand            # controller → service, no HTTP types
    │   │   │
    │   │   ├── mapper/                        # MapStruct, entity <-> DTO, both directions
    │   │   │   ├── OrderMapper.java
    │   │   │   └── AllocationMapper.java
    │   │   │
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java          # @RestControllerAdvice
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   ├── AllocationFailedException.java
    │   │   │   ├── IllegalOrderStateException.java
    │   │   │   ├── ProductUnavailableException.java
    │   │   │   └── UpstreamServiceException.java
    │   │   │
    │   │   └── security/
    │   │       ├── JwtAuthenticationConverter.java       # claims → GrantedAuthority
    │   │       ├── ServiceTokenProvider.java             # client-credentials token, cached
    │   │       └── UserContext.java                      # id + roles extracted from the token
    │   │
    │   └── resources/
    │       ├── application.yml                # ${ENV_VAR} placeholders only
    │       ├── application-docker.yml
    │       ├── db/migration/                  # Flyway: V1__init.sql, V2__...
    │       └── logback-spring.xml
    │
    └── test/java/com/logistics/order/
        ├── allocation/                        # pure unit tests — the heart of the test suite
        │   ├── NearestWarehouseAllocationStrategyTest.java
        │   ├── StockBalancingAllocationStrategyTest.java
        │   ├── AllocationStrategyResolverTest.java
        │   └── HaversineDistanceCalculatorTest.java
        ├── service/OrderServiceImplTest.java   # Mockito on the ports
        ├── controller/OrderControllerTest.java # @WebMvcTest + MockMvc
        └── integration/OrderCreationIT.java    # @SpringBootTest + Testcontainers (PostgreSQL + WireMock)
```

### The layering rule, in one line per layer

| Layer | Allowed to depend on | Must never |
|---|---|---|
| `controller` | `service`, `dto`, `mapper` | Touch a repository, or return an entity |
| `service` | `domain`, `repository`, `client`, `allocation` | Know about `HttpServletRequest` or `ResponseEntity` |
| `allocation` | `allocation.model` only | Import Spring, JPA or any I/O type |
| `domain` | itself | Import a DTO or a framework annotation other than JPA |
| `repository` | `domain` | Contain business rules |
| `client` | `client.dto`, `domain` | Leak a foreign DTO beyond its own package |

This is checked, not just documented: an **ArchUnit** test asserts that `..controller..` never reaches
`..repository..` and that `..allocation..` imports nothing from `org.springframework`. That test is itself an
interview talking point.

## 5. Other backend modules

`api-gateway` is thinner — no domain, no database:

```
api-gateway/src/main/java/com/logistics/gateway/
├── GatewayApplication.java
├── config/  SecurityConfig · RouteConfig · CorsConfig
├── filter/  JwtAuthenticationFilter · CorrelationIdFilter · RequestLoggingFilter
└── exception/ GatewayExceptionHandler        # RFC 7807 even for edge errors
```

`catalog-service` follows the same layering, with `repository` holding Spring Data **MongoDB** interfaces and
`domain/document/` instead of `domain/entity/` — the naming makes the persistence model visible at a glance.
It has no `allocation` package and no Flyway; index creation lives in a `MongoIndexInitializer`.

## 6. Frontend structure

```
frontend/
├── package.json · angular.json · tsconfig.json · Dockerfile · nginx.conf
├── .env.example                      # API_BASE_URL injected at container start
└── src/app/
    ├── core/                         # singletons, imported once
    │   ├── auth/       auth.service.ts · auth.guard.ts · role.guard.ts · token.store.ts
    │   ├── http/       auth.interceptor.ts · error.interceptor.ts · correlation.interceptor.ts
    │   ├── models/     user.model.ts · product.model.ts · order.model.ts · warehouse.model.ts
    │   └── services/   catalog.api.ts · inventory.api.ts · order.api.ts
    ├── shared/                       # reusable, stateless
    │   ├── components/ data-table · status-badge · money · confirm-dialog · problem-alert
    │   ├── pipes/      currency-format.pipe.ts · distance.pipe.ts
    │   └── directives/ has-role.directive.ts
    ├── features/                     # one lazy-loaded route per feature
    │   ├── auth/       login · register
    │   ├── catalog/    product-list · product-detail · product-form
    │   ├── inventory/  warehouse-list · warehouse-form · stock-board · movement-history
    │   ├── orders/     order-create · order-list · order-detail · allocation-map
    │   └── admin/      user-list · role-editor · allocation-simulator
    ├── layout/         shell · sidebar · topbar
    └── app.routes.ts · app.config.ts
```

Angular conventions applied: **standalone components** (no `NgModule`), **signals** for local state,
lazy-loaded routes with `loadComponent`, a typed `HttpClient` layer isolated in `core/services`, and
`ChangeDetectionStrategy.OnPush` everywhere. The exact Angular version is read from `ng version` at Phase 1
and pinned in `package.json` — not guessed here.

Two screens carry the project's message and deserve care:

- **`orders/allocation-map`** — the order detail showing each shipment, its warehouse, and the distance used
  by the decision.
- **`admin/allocation-simulator`** — calls `POST /orders/allocation-preview` with both strategies and shows
  the two plans side by side. This is the screen to demo in an interview.

## 7. Git conventions

**Branches** — `main` (always runnable), `develop`, `feature/<service>-<subject>`, `fix/<subject>`.

**Commits — Conventional Commits, in English:**

```
feat(order): add warehouse allocation strategy resolver
fix(inventory): prevent negative stock on concurrent reservations
docs(architecture): document polyglot persistence rationale
test(order): cover split allocation across three warehouses
chore(docker): pin postgres image version
```

Scope is the module name. One commit per coherent change — a reviewer (or an interviewer) reading
`git log --oneline` should see the project being built, not fifteen commits named "update".

**Phase tags**: `v0.1-phase0-design`, `v0.2-auth`, `v0.3-catalog`, … so each milestone stays checkoutable.

## 8. Build and run

| Command | Effect |
|---|---|
| `mvn -q verify` | Compiles the five services, runs unit + integration tests (Testcontainers) |
| `docker compose up --build` | Databases, five services, frontend; the gateway is the only backend port published |
| `docker compose down -v` | Full teardown including volumes |
| `scripts/seed.sh` | Loads a demo dataset: categories, products, four warehouses, initial stock (added with `inventory-service`, phase 3) |

The seed script inserts data **through the public API**, not with raw SQL — so it doubles as an
end-to-end smoke test of the contract.
