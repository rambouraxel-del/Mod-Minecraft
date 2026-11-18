# État Worldgen V1

| Lot | Travail livré |
| --- | --- |
| Infrastructure | Gradle Wrapper, Fabric, preset Overworld et pipeline GitHub Actions |
| Géographie | Continents, océans et tectonique déterministes |
| Climat | Champs de température et précipitations |
| Hydrologie | Drainage global, apports amont, lacs et lits |
| Intégration | Colonne de blocs, décoration vanilla, apparition, bordure et diagnostic |

Choix provisoires. Mer Y=63, carte centrée sur l’origine, réseau au pas de 400 blocs et bordure native de Minecraft.

Le premier lancement dédié a révélé qu’une interface d’accès était déclarée dans le paquet réservé aux mixins. Elle a été déplacée. Les cartes ont aussi révélé un maillage de crêtes trop régulier, corrigé par un masque continu de compression et un tracé de plaques déformé. Les versions corrigées ont été recompilées et vérifiées. Voir [validation](../../docs/validation.md).

La création depuis le client a été vérifiée avec la graine 422. Les captures et leurs positions sont regroupées dans la [galerie](../../docs/screenshots/).
