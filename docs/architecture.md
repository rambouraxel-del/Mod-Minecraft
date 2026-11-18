# Architecture de la Worldgen V1

| Composant | Responsabilité |
| --- | --- |
| `Terrain` | Contours continentaux, fonds océaniques, frontières de plaques, relief et escarpements locaux |
| `Climate` | Température selon latitude et altitude, influence océanique et précipitations régionales |
| `Hydrology` | Grille de drainage de 601 × 601 sommets espacés de 400 blocs, accumulation amont et sculpture des lits |
| `Planet` | Échantillon de colonne, géologie, classification écologique et galeries souterraines |
| `PlanetBiomes` | Résolution des biomes vanilla depuis la graine de l’Overworld |
| `PlanetChunkGenerator` | Colonnes de blocs, tables de hauteur, point d’apparition et décoration vanilla |

L’Overworld jouable occupe l’intervalle [−120 000, 120 000[ sur X et Z. Une mince zone de transition au-delà sert au rendu des chunks voisins. Minecraft fait respecter la limite avec sa bordure native. Le niveau de la mer est à Y=63 et la hauteur du monde s’étend de Y=−64 à Y=319.

La carte globale de drainage est calculée au premier besoin et partagée entre les workers d’un même monde. Un parcours prioritaire part des océans et des limites de carte et calcule le plus bas niveau de déversement des dépressions. Le routage dirige chaque sommet vers son aval. Les apports de pluie s’accumulent de l’amont à l’aval, ce qui fait varier largeur et profondeur. Le tracé des lits et les sols à l’échelle du bloc sont évalués en coordonnées absolues. Le coût du modèle global dépend de la résolution de la grille, tandis que la sauvegarde grandit avec les chunks visités.

La température varie sur 240 km du sud équatorial au nord polaire. Les reliefs refroidissent localement l’air et la proximité de l’océan atténue les extrêmes. Les biomes sélectionnés réutilisent leurs espèces, décorations, faune et minerais vanilla. La forêt tempérée mêle notamment chênes et bouleaux. Les formations régionales utilisent de la pierre, du granite, de l’andésite et de la diorite.

## Paramètres de cette version

Les hauteurs, la résolution du drainage, les profils des pluies, les seuils de biomes et la bordure native sont des choix d’implémentation V1. La frontière de conception définitive et les modèles d’érosion restent ouverts. Une modification de la fonction de génération changera les nouveaux chunks d’une sauvegarde existante.

## Limites et prochaine itération

- Les effets de la grille de drainage de 400 m se voient sur certains tronçons. Les fleuves gagnent à être affinés avec un reroutage local, des niveaux de lacs par bassin et des profils de berges conçus pour les débits importants.
- Les formations tectoniques organisent le relief en utilisant des frontières de plaques procédurales. Les chaînes pourraient être mieux différenciées par type de zone tectonique et par érosion.
- Les seuils de biome sont discrets autour de champs continus. Les végétaux restent ceux des listes vanilla et la composition des forêts varie encore principalement par biome. Une couche de peuplement forestier permettrait de faire varier progressivement les proportions d’essences.
- Les galeries souterraines sont générées en trois dimensions sous une couche de protection. Leur accessibilité depuis la surface, les aquifères et les gisements géologiques feront l’objet d’une itération dédiée.
- Les structures et petites mares de lave vanilla suivent leurs règles habituelles. Leur répartition pourra être raccordée à la géologie de la planète. Le monde est destiné à une sauvegarde neuve.
