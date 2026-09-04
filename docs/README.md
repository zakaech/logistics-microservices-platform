# Design documentation — Phase 0

Multi-Warehouse Logistics Platform. These documents are the design baseline; no code exists yet.

| # | Document | Contains |
|---|---|---|
| 01 | [Architecture Overview](01-architecture.md) | Context and deployment diagrams, service boundaries and their justification, polyglot persistence rationale, security model, saga, decision log, design patterns |
| 02 | [Data Model](02-data-model.md) | Per-service model: tables/collections, columns, types, constraints, indexes, cross-service references |
| 03 | [order-service Design](03-order-service-design.md) | UML class diagram, allocation algorithm in pseudocode, the two strategies, edge cases, order state machine |
| 04 | [Create-order Sequence](04-sequence-create-order.md) | End-to-end sequence diagram, plus failure paths and compensations |
| 05 | [REST API Contract](05-api-contract.md) | Every endpoint: method, path, payloads, status codes, required role |
| 06 | [Repository Layout](06-repository-layout.md) | Maven multi-module tree, package layering per service, Angular structure, Git conventions |

## Reading order for a reviewer

1. `01` §4 and §5 — why the system is split this way and why two databases.
2. `03` — the allocation engine, which is the point of the project.
3. `04` — how the pieces cooperate, and what happens when one fails.

## Diagrams

All diagrams are Mermaid embedded in Markdown: they are versioned as text, diffable in a pull request, and
rendered natively by GitHub. No binary image, no external tool needed.
