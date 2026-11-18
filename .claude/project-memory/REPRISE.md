# Reprise

Travailler depuis `feat/worldgen-v1`. Les points de référence sont dans [README](../../README.md), [architecture](../../docs/architecture.md) et [diagnostics](../../docs/diagnostics/).

1. Lancer `./gradlew build` sous Java 25. Le test autonome `geographyChecks` fait partie de `check`.
2. Vérifier les transitions sur les cartes des graines 0, 1 et 42 avant tout changement de terrain, de climat ou de drainage.
3. Tester une sauvegarde fraîche puis son rechargement avec le preset `azerya_worldgen:miniature_planet`. Voir [tests dédiés](../../scripts/).
4. Après changement des règles de génération, créer une nouvelle sauvegarde de test et régénérer les cartes et coordonnées correspondantes.

Points ouverts pour la suite. Affiner les tracés des lits et niveaux des lacs, enrichir le mélange d’essences à l’échelle du peuplement, traiter les accès aux grottes et stabiliser un format de versionnement du générateur avant les migrations.
