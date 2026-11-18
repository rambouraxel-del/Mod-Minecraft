# Worldgen diagnostic maps

`fr.azerya.worldgen.diagnostics.WorldgenDiagnostic` is a deterministic Java test utility. It samples the production `Terrain`, `Climate`, and `Hydrology` classes and writes a five-panel PNG plus a short Markdown file of candidate coordinates. It uses only the JDK (AWT and ImageIO).

Panels show land/ocean, elevation, temperature, rainfall (the climate precipitation sample), and hydrology. The top of every map is north (decreasing Z). The hydrology panel overlays drainage graph edges so subpixel rivers remain visible; sea, lakes, and rivers use separate colors. The Markdown provides `/tp @s X ~ Z` options for representative sites.

Run after compiling the project, with compiled main and test classes on the classpath:

```sh
java -cp <project-classpath> fr.azerya.worldgen.diagnostics.WorldgenDiagnostic <seed> <output-dir> [size=256] [blocks=240000]
```

The checked in overview maps cover the full 240000 by 240000 block world at 937.5 blocks per pixel. Seed 42 also has a 32768 by 32768 block detail map. Site candidates are searched within 5 km of the world edge at 250 block intervals and include local slope plus surface, bed, and water levels. Forest candidates favor a surrounding dry temperate region. Coast candidates are 200–600 blocks from ocean. Fixed seed, window, and size produce fixed samples and pixels. The overviews plot main drainage channels with discharge ≥30 in model units. The detail map plots smaller tributaries from discharge ≥4.

## Generated seeds

| Seed | Overview | Detail | Candidate coordinates |
|---:|---|---|---|
| 0 | [PNG](worldgen-0.png) | — | [Sites](worldgen-0-sites.md) |
| 1 | [PNG](worldgen-1.png) | — | [Sites](worldgen-1-sites.md) |
| 42 | [PNG](worldgen-42-overview.png) | [PNG](worldgen-42-detail.png) | [Sites](worldgen-42-sites.md) |
