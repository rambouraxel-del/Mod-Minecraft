# Validation de Worldgen V1

## Compilation et modèle géographique

- Compilation et `check` sous Java 25, Gradle 9.6.0 et Loom 1.17.21.
- `geographyChecks` parcourt quatre graines (0, 1, 42 et 1592639710), avec mesure de la continuité des continents et océans, gradient climatique en cinq bandes, flux aval et reproductibilité des calculs en parallèle. Les points testés incluent les limites de chunks et de la carte.
- Contrôle indépendant sur une grille de 2 km. Les proportions de terres émergées mesurées varient de 46,2 à 54,6 % selon les graines échantillonnées. Les plus grandes masses continues couvrent, selon la graine, 9 300 à 30 676 km² de terre et 10 288 à 26 796 km² d’océan. Le contrôle utilise la connexité à huit voisins.
- Après correction du relief, un million de paires de colonnes adjacentes par graine ont donné un écart maximal observé d’environ 2,5 blocs sur le champ de terrain avant sculpture fluviale.
- Échantillonnage du biome de forêt tempérée sur la graine 42 autour de X=−67 500, Z=27 500. Les 81 points d’une grille régulière de 500 m sur un carré de 4 km résolvent le biome forêt vanilla, où chênes et bouleaux sont générés ensemble.
- Les quatre cartes de diagnostic sont disponibles dans [diagnostics](diagnostics/).

## Minecraft Java 26.3 sur serveur dédié

| Graine | Vérification | Résultat |
| ---: | --- | --- |
| 1 | Installation autonome avec le JAR de distribution, Fabric Loader et Fabric API | Monde créé, apparition terrestre, bordure de 240 000 blocs et arrêt propre |
| 42 | Création puis rechargement par le scénario automatisé | Preset actif, bordure vérifiée, matériaux de montagne et eau de rivière et de lac retrouvés après redémarrage |
| 42 | Modification de la sauvegarde | Bloc placé à X=30 325, Y=300, Z=109 875 retrouvé après redémarrage |

Sur la graine 42, les vérifications de blocs ont chargé une montagne à X=30 325, Z=109 875, une rivière à X=35 750, Z=106 250 et un lac à X=35 925, Z=106 750. Le serveur a choisi une région de départ autour de X=−248, Z=−248. La sauvegarde dédiée contient 2 716 enregistrements de chunks après chargement des régions de test, contre 529 pour l’essai de distribution sur la graine 1. Ces nombres sont lus dans les tables de positions des fichiers de région.

Le serveur de distribution sur la graine 1 a préparé la zone d’apparition en environ 1,1 s et annoncé son démarrage en 1,9 s sur cette machine. Ce sont des mesures ponctuelles. Le calcul initial du drainage en Java autonome a pris environ 0,6 à 0,9 s, avec environ 11 Mio de mémoire retenue après GC. Une courte hausse de latence a été relevée lors de chargements forcés dans un essai dédié.

La procédure de reproduction est dans [scripts](../scripts/).

## Empreinte du JAR

SHA-256 de [azerya-worldgen-0.1.0.jar](../dist/azerya-worldgen-0.1.0.jar)

```text
3aabe50095a664e4ecef368ece5e5171204086aa5089b93b9da1a46e03616f35
```
