# Frontend

Ce projet a été généré avec [Angular CLI](https://github.com/angular/angular-cli) version 21.2.23.

> Pour lancer la plateforme complète (frontend + microservices + bases de données) en une
> seule commande, voir le [README à la racine du dépôt](../README.md). Les commandes
> ci-dessous ne concernent que le développement du frontend seul.

## Serveur de développement

Pour démarrer un serveur de développement local :

```bash
ng serve
```

Une fois le serveur lancé, ouvrez `http://localhost:4200/` dans votre navigateur.
L'application se recharge automatiquement à chaque modification d'un fichier source.

## Génération de code

Angular CLI fournit des outils de génération de code. Pour créer un nouveau composant :

```bash
ng generate component nom-du-composant
```

Pour la liste complète des schematics disponibles (`components`, `directives`, `pipes`, etc.) :

```bash
ng generate --help
```

## Compilation

Pour compiler le projet :

```bash
ng build
```

Les artefacts de compilation sont déposés dans le répertoire `dist/`. Par défaut, le build de
production optimise l'application pour la performance et la taille des bundles.

## Tests unitaires

Pour exécuter les tests unitaires avec le lanceur de tests [Vitest](https://vitest.dev/) :

```bash
ng test
```

## Tests de bout en bout

Pour les tests de bout en bout (e2e) :

```bash
ng e2e
```

Angular CLI n'embarque aucun framework de test de bout en bout par défaut : il revient au
projet d'en choisir un. Aucun n'est mis en place ici, ce que la section « limites connues »
du README racine assume explicitement.

## Ressources complémentaires

Pour plus d'informations sur Angular CLI, y compris la référence détaillée des commandes,
consultez la page [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli).
