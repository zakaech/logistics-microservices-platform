# 06 — Organisation du dépôt (monorepo Maven multi-modules)

> Document de conception de la Phase 0 : il décrit l'organisation **telle qu'elle était prévue avant
> l'implémentation**. Là où le code livré s'en écarte, c'est le dépôt qui fait foi — notamment la seconde
> stratégie, finalement nommée `SingleShipmentAllocationStrategy`, le script de démonstration
> `scripts/seed-demo-data.sh`, et le répertoire racine `logistics-microservices-platform/`.

## 1. Pourquoi un monorepo

Cinq modules backend plus un frontend, développés par une seule personne, livrés ensemble. Un monorepo offre
un seul clone, un seul `mvn verify`, un seul `docker compose up`, et un commit atomique lorsqu'un changement
traverse plusieurs services (par exemple, ajouter un champ au contrat de disponibilité touche à la fois
`inventory-service` et `order-service`).

Le contre-argument — des cycles de livraison indépendants par service — ne s'applique pas ici et n'ajouterait
que de la friction. À dire explicitement : *le monorepo est un choix d'organisation du dépôt, pas un choix
d'architecture.* Les services restent déployables indépendamment, et chacun possède son propre Dockerfile et
sa propre base de données.

## 2. Arborescence de premier niveau

```
logistics-platform/
├── pom.xml                          # agrégateur + dependencyManagement (packaging : pom)
├── docker-compose.yml               # pile complète : bases de données + 5 services + frontend
├── docker-compose.override.yml      # dev local : publie les ports gardés internes dans le fichier de base
├── .env.example                     # toutes les variables requises, documentées, sans valeur réelle
├── .gitignore                       # target/, node_modules/, .env, *.log, .idea/
├── .editorconfig
├── README.md                        # ce que c'est, comment le lancer, l'architecture en 10 lignes
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
│   │   └── init/01-create-databases.sql     # 3 bases + 3 utilisateurs, un par service
│   └── mongo/
│       └── init/01-create-user.js           # utilisateur catalog_db au privilège minimal
│
├── api-gateway/
├── auth-service/
├── catalog-service/
├── inventory-service/
├── order-service/
└── frontend/
```

## 3. POM parent

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

Trois choix délibérés :

1. **Pas de `spring-boot-starter-parent` en `<parent>`.** Les BOM Spring Boot et Spring Cloud sont
   *importés* dans `<dependencyManagement>`, ce qui laisse `logistics-platform` comme véritable parent et
   laisse la place à notre propre configuration de plugins. Les versions sont déclarées une fois, dans les
   propriétés, et héritées par chaque module.
2. **Toutes les versions figées dans le parent** — release Java, Spring Boot, Spring Cloud, MapStruct,
   Testcontainers, Lombok. Aucun module ne déclare jamais de version de dépendance qui lui soit propre. Les
   versions exactes sont choisies et gelées en Phase 1 depuis le train de publication courant, elles ne sont
   pas devinées ici.
3. **Aucun module `shared-kernel`** (décision D10). Chaque service possède son propre
   `GlobalExceptionHandler`, son `PagedResponse<T>`, sa configuration de resource server et ses constantes de
   rôles — environ 150 lignes dupliquées par service. Cette duplication est délibérée : un module technique
   partagé crée un couplage de livraison, où changer une classe force une reconstruction et un redéploiement
   des quatre services, ce qu'un découpage en microservices est précisément censé éviter. Le code dupliqué
   est du code d'infrastructure, pas de la logique métier ; le jour où il commencera à diverger, cette
   divergence sera une décision légitime au niveau du service plutôt qu'un conflit de fusion.

Le module `frontend` n'est *pas* un module Maven : mêler un build Node au réacteur Maven n'apporte rien et
ralentit chaque build backend. Il possède son propre `package.json` et son propre Dockerfile.

## 4. Structure canonique d'un service (exemple : `order-service`)

Le même découpage en couches s'applique aux quatre services métier ; seul le domaine diffère.

```
order-service/
├── pom.xml
├── Dockerfile                       # multi-stage : build maven → JRE eclipse-temurin, utilisateur non root
└── src/
    ├── main/
    │   ├── java/com/logistics/order/
    │   │   ├── OrderServiceApplication.java
    │   │   │
    │   │   ├── config/                        # configuration Spring uniquement, aucune logique métier
    │   │   │   ├── SecurityConfig.java        # resource server, mapping des rôles, règles de routes
    │   │   │   ├── RestClientConfig.java      # timeouts, URL de base, intercepteurs
    │   │   │   ├── JpaAuditingConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   └── AllocationProperties.java  # @ConfigurationProperties("logistics.allocation")
    │   │   │
    │   │   ├── controller/                    # HTTP seulement : lier, déléguer, mapper, renvoyer
    │   │   │   ├── OrderController.java
    │   │   │   └── AllocationPreviewController.java
    │   │   │
    │   │   ├── service/                       # cas d'usage (interfaces) + orchestration
    │   │   │   ├── OrderService.java
    │   │   │   ├── AllocationPreviewService.java
    │   │   │   ├── OrderNumberGenerator.java
    │   │   │   └── impl/
    │   │   │       ├── OrderServiceImpl.java
    │   │   │       └── AllocationPreviewServiceImpl.java
    │   │   │
    │   │   ├── domain/                        # le modèle — sans Spring, sans annotation JSON
    │   │   │   ├── entity/  Order · OrderLine · OrderAllocation · OrderAllocationLine · OrderStatusHistory
    │   │   │   ├── vo/      Money · GeoPoint · DeliveryAddress
    │   │   │   └── enums/   OrderStatus
    │   │   │
    │   │   ├── allocation/                    # LE cœur métier — pur, sans framework
    │   │   │   ├── WarehouseAllocationStrategy.java
    │   │   │   ├── AbstractAllocationStrategy.java
    │   │   │   ├── AllocationStrategyResolver.java
    │   │   │   ├── impl/  NearestWarehouseAllocationStrategy · StockBalancingAllocationStrategy
    │   │   │   ├── model/ AllocationRequest · AllocationPlan · AllocationSegment
    │   │   │   │          SegmentLine · RequestedLine · WarehouseCandidate
    │   │   │   └── distance/ DistanceCalculator · HaversineDistanceCalculator
    │   │   │
    │   │   ├── repository/                    # interfaces Spring Data JPA + spécifications
    │   │   │   ├── OrderRepository.java
    │   │   │   └── spec/OrderSpecifications.java
    │   │   │
    │   │   ├── client/                        # couche anticorruption (ports + adaptateurs)
    │   │   │   ├── CatalogClient.java · InventoryClient.java
    │   │   │   ├── impl/ CatalogRestClient.java · InventoryRestClient.java
    │   │   │   └── dto/  ProductSnapshot · AvailabilityView · ReservationCommand · ReservationHandle
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── request/  CreateOrderRequest · OrderLineRequest · UpdateOrderStatusRequest
    │   │   │   │             CancelOrderRequest · AllocationPreviewRequest
    │   │   │   ├── response/ OrderResponse · OrderSummaryResponse · OrderLineResponse
    │   │   │   │             OrderAllocationResponse · AllocationPreviewResponse · PagedResponse
    │   │   │   └── command/  CreateOrderCommand            # contrôleur → service, aucun type HTTP
    │   │   │
    │   │   ├── mapper/                        # MapStruct, entité <-> DTO, dans les deux sens
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
    │   │       ├── ServiceTokenProvider.java             # jeton client-credentials, mis en cache
    │   │       └── UserContext.java                      # id + rôles extraits du jeton
    │   │
    │   └── resources/
    │       ├── application.yml                # uniquement des substituants ${ENV_VAR}
    │       ├── application-docker.yml
    │       ├── db/migration/                  # Flyway : V1__init.sql, V2__...
    │       └── logback-spring.xml
    │
    └── test/java/com/logistics/order/
        ├── allocation/                        # tests unitaires purs — le cœur de la suite de tests
        │   ├── NearestWarehouseAllocationStrategyTest.java
        │   ├── StockBalancingAllocationStrategyTest.java
        │   ├── AllocationStrategyResolverTest.java
        │   └── HaversineDistanceCalculatorTest.java
        ├── service/OrderServiceImplTest.java   # Mockito sur les ports
        ├── controller/OrderControllerTest.java # @WebMvcTest + MockMvc
        └── integration/OrderCreationIT.java    # @SpringBootTest + Testcontainers (PostgreSQL + WireMock)
```

### La règle de couches, en une ligne par couche

| Couche | Peut dépendre de | Ne doit jamais |
|---|---|---|
| `controller` | `service`, `dto`, `mapper` | Toucher un repository, ni renvoyer une entité |
| `service` | `domain`, `repository`, `client`, `allocation` | Connaître `HttpServletRequest` ou `ResponseEntity` |
| `allocation` | `allocation.model` uniquement | Importer Spring, JPA ou le moindre type d'E/S |
| `domain` | lui-même | Importer un DTO ou une annotation de framework autre que JPA |
| `repository` | `domain` | Contenir des règles métier |
| `client` | `client.dto`, `domain` | Laisser fuir un DTO étranger hors de son propre paquet |

Ceci est vérifié, pas seulement documenté : un test **ArchUnit** affirme que `..controller..` n'atteint
jamais `..repository..` et que `..allocation..` n'importe rien de `org.springframework`. Ce test est
lui-même un sujet de discussion en entretien.

## 5. Les autres modules backend

`api-gateway` est plus mince — aucun domaine, aucune base de données :
```
api-gateway/src/main/java/com/logistics/gateway/
├── GatewayApplication.java
├── config/  SecurityConfig · RouteConfig · CorsConfig
├── filter/  JwtAuthenticationFilter · CorrelationIdFilter · RequestLoggingFilter
└── exception/ GatewayExceptionHandler        # RFC 7807 même pour les erreurs de périphérie
```

`catalog-service` suit le même découpage en couches, avec un `repository` qui contient des interfaces Spring
Data **MongoDB** et un `domain/document/` au lieu d'un `domain/entity/` — le nommage rend le modèle de
persistance visible d'un coup d'œil. Il n'a ni paquet `allocation` ni Flyway ; la création des index vit dans
un `MongoIndexInitializer`.

## 6. Structure du frontend
```
frontend/
├── package.json · angular.json · tsconfig.json · Dockerfile · nginx.conf
├── .env.example                      # API_BASE_URL injectée au démarrage du conteneur
└── src/app/
    ├── core/                         # singletons, importés une seule fois
    │   ├── auth/       auth.service.ts · auth.guard.ts · role.guard.ts · token.store.ts
    │   ├── http/       auth.interceptor.ts · error.interceptor.ts · correlation.interceptor.ts
    │   ├── models/     user.model.ts · product.model.ts · order.model.ts · warehouse.model.ts
    │   └── services/   catalog.api.ts · inventory.api.ts · order.api.ts
    ├── shared/                       # réutilisables, sans état
    │   ├── components/ data-table · status-badge · money · confirm-dialog · problem-alert
    │   ├── pipes/      currency-format.pipe.ts · distance.pipe.ts
    │   └── directives/ has-role.directive.ts
    ├── features/                     # une route chargée paresseusement par fonctionnalité
    │   ├── auth/       login · register
    │   ├── catalog/    product-list · product-detail · product-form
    │   ├── inventory/  warehouse-list · warehouse-form · stock-board · movement-history
    │   ├── orders/     order-create · order-list · order-detail · allocation-map
    │   └── admin/      user-list · role-editor · allocation-simulator
    ├── layout/         shell · sidebar · topbar
    └── app.routes.ts · app.config.ts
```

Conventions Angular appliquées : **composants standalone** (aucun `NgModule`), **signals** pour l'état local,
routes chargées paresseusement avec `loadComponent`, une couche `HttpClient` typée isolée dans
`core/services`, et `ChangeDetectionStrategy.OnPush` partout. La version exacte d'Angular est relevée avec
`ng version` en Phase 1 et figée dans `package.json` — elle n'est pas devinée ici.

Deux écrans portent le message du projet et méritent du soin :

- **`orders/allocation-map`** — le détail d'une commande, montrant chaque expédition, son entrepôt et la
  distance qui a motivé la décision.
- **`admin/allocation-simulator`** — appelle `POST /orders/allocation-preview` avec les deux stratégies et
  affiche les deux plans côte à côte. C'est l'écran à démontrer en entretien.

## 7. Conventions Git

**Branches** — `main` (toujours exécutable), `develop`, `feature/<service>-<sujet>`, `fix/<sujet>`.

**Commits — Conventional Commits, en anglais :**

```
feat(order): add warehouse allocation strategy resolver
fix(inventory): prevent negative stock on concurrent reservations
docs(architecture): document polyglot persistence rationale
test(order): cover split allocation across three warehouses
chore(docker): pin postgres image version
```

La portée est le nom du module. Un commit par changement cohérent — un relecteur (ou un examinateur) qui lit
`git log --oneline` doit voir le projet se construire, et non quinze commits intitulés « update ».

> Les messages de commit restent en anglais : c'est la convention Conventional Commits, et elle est
> volontairement conservée telle quelle (voir la section « Conventions » du README racine).

**Étiquettes de phase** : `v0.1-phase0-design`, `v0.2-auth`, `v0.3-catalog`, … afin que chaque jalon reste
récupérable par `checkout`.

## 8. Construction et exécution

| Commande | Effet |
|---|---|
| `mvn -q verify` | Compile les cinq services, exécute les tests unitaires et d'intégration (Testcontainers) |
| `docker compose up --build` | Bases de données, cinq services, frontend ; la gateway est le seul port backend publié |
| `docker compose down -v` | Arrêt complet, volumes compris |
| `scripts/seed.sh` | Charge un jeu de données de démonstration : catégories, produits, quatre entrepôts, stock initial (ajouté avec `inventory-service`, phase 3) |

Le script de seed insère les données **à travers l'API publique**, et non par du SQL brut — il fait donc
aussi office de test de bout en bout du contrat.
