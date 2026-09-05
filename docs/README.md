# Documentation de conception — Phase 0

Plateforme Microservices de Gestion Logistique. Ces documents constituent la référence de
conception : ils ont été rédigés avant la moindre ligne de code, et le code implémente les
décisions qu'ils actent.

| # | Document | Contenu |
|---|---|---|
| 01 | [Vue d'ensemble de l'architecture](01-architecture.md) | Diagrammes de contexte et de déploiement, frontières des services et leur justification, choix de la persistance polyglotte, modèle de sécurité, saga, journal des décisions, patrons de conception |
| 02 | [Modèle de données](02-data-model.md) | Modèle par service : tables/collections, colonnes, types, contraintes, index, références inter-services |
| 03 | [Conception de order-service](03-order-service-design.md) | Diagramme de classes UML, algorithme d'affectation en pseudocode, les deux stratégies, cas limites, machine à états de la commande |
| 04 | [Séquence de création d'une commande](04-sequence-create-order.md) | Diagramme de séquence de bout en bout, chemins d'échec et compensations |
| 05 | [Contrat de l'API REST](05-api-contract.md) | Chaque endpoint : méthode, chemin, charges utiles, codes de statut, rôle requis |
| 06 | [Organisation du dépôt](06-repository-layout.md) | Arborescence Maven multi-modules, découpage en couches par service, structure Angular, conventions Git |

## Ordre de lecture conseillé à un relecteur

1. `01` §4 et §5 — pourquoi le système est découpé ainsi, et pourquoi deux bases de données.
2. `03` — le moteur d'affectation, qui est le cœur du projet.
3. `04` — comment les pièces coopèrent, et ce qui se passe lorsque l'une d'elles échoue.

## Diagrammes

Tous les diagrammes sont en Mermaid, intégrés au Markdown : ils sont versionnés comme du texte,
comparables dans une pull request, et rendus nativement par GitHub. Aucune image binaire, aucun
outil externe nécessaire.
