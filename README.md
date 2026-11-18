# Azerya Worldgen V1

Un mod Fabric pour Minecraft Java 26.3. Le type de monde **Azerya – Planète miniature** génère un Overworld fini de **240 000 × 240 000 blocs**, avec des continents, de grands océans, des chaînes montagneuses et des bassins versants déterminés par la graine.

## Installer et créer un monde

1. Installe [Fabric Loader](https://fabricmc.net/use/installer/) **0.20.1** pour **Minecraft Java 26.3**. Le jeu utilise **Java 25**.
2. Place [azerya-worldgen-0.1.0.jar](dist/azerya-worldgen-0.1.0.jar) et [Fabric API 0.166.2+26.3](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.166.2+26.3/fabric-api-0.166.2+26.3.jar) dans le dossier `mods`.
3. Démarre le profil Fabric. Dans **Un joueur → Créer un nouveau monde → Monde**, sélectionne **Azerya – Planète miniature** dans **Type de monde**. Choisis une graine ou laisse Minecraft en tirer une.
4. Crée le monde. Le jeu cherche un point d’apparition terrestre et la première génération calcule le réseau d’écoulement à l’échelle de la carte. Les chunks se génèrent ensuite au fil de l’exploration.

La bordure de l’Overworld est centrée en X=0, Z=0. Le nord correspond aux Z négatifs. Le Nether et l’End utilisent leurs générateurs vanilla.

Sur serveur dédié, installe les mêmes fichiers dans `mods` et règle `level-type=azerya_worldgen:miniature_planet` avant de créer la sauvegarde. `level-seed=42` reproduit la carte et les points de repère documentés.

## Compiler et vérifier

Avec Java 25, lance `./gradlew build` ou `gradlew.bat build` sous Windows. Le mod se trouve dans `build/libs/azerya-worldgen-0.1.0.jar`. Le workflow [Build](https://github.com/rambouraxel-del/Mod-Minecraft/actions/workflows/build.yml) publie les JAR en artefact. Pour jouer, prends le fichier sans suffixe `-sources`.

- [Résultats des tests](docs/validation.md)
- [Architecture et limites](docs/architecture.md)
- [Cartes de diagnostic et coordonnées](docs/diagnostics/)
- [Tests sur serveur dédié](scripts/)
- [Mémoire de projet](.claude/project-memory/)
