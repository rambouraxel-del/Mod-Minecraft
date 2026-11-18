package fr.azerya.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

public final class PlanetChunkGenerator extends ChunkGenerator {
    static final MapCodec<PlanetChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
            .xmap(PlanetChunkGenerator::new, PlanetChunkGenerator::getBiomeSource);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    private record Surface(BlockState top, BlockState soil, int depth, BlockState rock) {}

    private PlanetChunkGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState random,
            StructureManager structures, BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
        Planet planet = planet(random);
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int bottom = chunk.getMinY();
        for (int dz = 0; dz < 16; dz++) {
            int z = chunkPos.getBlockZ(dz);
            for (int dx = 0; dx < 16; dx++) {
                int x = chunkPos.getBlockX(dx);
                Planet.Column column = planet.column(x, z);
                Surface surface = surface(planet, column, x, z);
                int top = Math.min(chunk.getMaxY(), Math.max(column.bed(), column.water()));
                for (int y = bottom; y <= top; y++) {
                    BlockState state = block(planet, column, surface, x, y, z);
                    if (!state.isAir()) chunk.setBlockState(position.set(x, y, z), state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types kind, LevelHeightAccessor height, RandomState random) {
        Planet.Column column = planet(random).column(x, z);
        int highest = kind.isOpaque().test(WATER) ? Math.max(column.bed(), column.water()) : column.bed();
        return Math.clamp(highest + 1, height.getMinY(), height.getMaxY() + 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        Planet planet = planet(random);
        Planet.Column column = planet.column(x, z);
        Surface surface = surface(planet, column, x, z);
        int min = height.getMinY();
        BlockState[] states = new BlockState[height.getHeight()];
        for (int index = 0; index < states.length; index++) {
            states[index] = block(planet, column, surface, x, min + index, z);
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public ChunkPos getOrigin(RandomState random) {
        Planet planet = planet(random);
        // Search in chunk-sized coordinates; vanilla then finds a safe standing position
        // and places the optional bonus chest using its normal spawn routine.
        ChunkPos fallback = null;
        for (int radius = 0; radius <= Planet.RADIUS - 1024; radius += 256) {
            for (int offset = -radius; offset <= radius; offset += 256) {
                ChunkPos[] candidates = {
                    new ChunkPos(offset / 16, -radius / 16), new ChunkPos(offset / 16, radius / 16),
                    new ChunkPos(-radius / 16, offset / 16), new ChunkPos(radius / 16, offset / 16)
                };
                for (ChunkPos candidate : candidates) {
                    int x = candidate.getMiddleBlockX();
                    int z = candidate.getMiddleBlockZ();
                    Planet.Column column = planet.column(x, z);
                    if (!column.wet() && column.bed() >= Planet.SEA + 6) {
                        if (fallback == null) fallback = candidate;
                        if (column.bed() <= 155 && column.slope() < .16) {
                            AzeryaWorldgen.LOGGER.info("Azerya spawn region: x={}, z={}", x, z);
                            return candidate;
                        }
                    }
                }
            }
            if (fallback != null && radius >= 12_288) break;
        }
        if (fallback != null) {
            AzeryaWorldgen.LOGGER.info("Azerya spawn region: x={}, z={}", fallback.getMiddleBlockX(), fallback.getMiddleBlockZ());
            return fallback;
        }
        return new ChunkPos(0, 0);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor height) { return height.getMinY() - 1; }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos chunk = region.getCenter();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(region.getSeed()));
        random.setDecorationSeed(region.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, chunk.getWorldPosition().atY(region.getMaxY()), chunk, random);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos position, SamplerContext context) {
        Planet planet = planet(random);
        Planet.Column column = planet.column(position.getX(), position.getZ());
        lines.add(String.format(java.util.Locale.ROOT, "Azerya | y=%d | T=%.2f P=%.2f | %s",
                column.bed(), column.temperature(), column.rain(), column.rock()));
    }

    @Override public int getGenDepth() { return Planet.HEIGHT; }
    @Override public int getMinY() { return Planet.MIN_Y; }
    @Override public int getSeaLevel() { return Planet.SEA; }

    private static Planet planet(RandomState random) {
        return ((PlanetAccess) (Object) random).azeryaWorldgen$getPlanet();
    }

    private static Surface surface(Planet planet, Planet.Column column, int x, int z) {
        BlockState rock = switch (column.rock()) {
            case SEDIMENTARY -> STONE;
            case GRANITIC -> GRANITE;
            case VOLCANIC -> ANDESITE;
            case MIXED -> DIORITE;
        };
        int bed = column.bed();
        if (column.wet()) {
            if (column.lake() && column.rain() > .48) return new Surface(CLAY, DIRT, 2, rock);
            if (column.river() || bed < Planet.SEA - 12 || column.slope() > .25) {
                return new Surface(GRAVEL, GRAVEL, 2, rock);
            }
            return new Surface(SAND, SAND, 3, rock);
        }
        Planet.Region biome = planet.region(x, z, column);
        if (biome == Planet.Region.BEACH || biome == Planet.Region.SNOWY_BEACH || biome == Planet.Region.DESERT) {
            return new Surface(SAND, SANDSTONE, 5, rock);
        }
        if (biome == Planet.Region.ROCKY_SHORE || column.slope() > .55 || bed > 232) {
            return new Surface(rock, rock, 0, rock);
        }
        if (column.rain() < .40 && (column.slope() > .18 || column.rock() == Planet.Rock.GRANITIC)) {
            return new Surface(COARSE_DIRT, DIRT, 2, rock);
        }
        return new Surface(GRASS, DIRT, column.rain() > .60 ? 4 : 3, rock);
    }

    private static BlockState block(Planet planet, Planet.Column column, Surface surface, int x, int y, int z) {
        int depth = column.bed() - y;
        if (depth < 0) return y <= column.water() ? WATER : AIR;
        if (planet.bedrock(x, y, z)) return BEDROCK;
        if (planet.cave(x, y, z, column)) return AIR;
        if (depth == 0) return surface.top();
        if (depth <= surface.depth()) return surface.soil();
        if (y < 0) return DEEPSLATE;
        // Alternate regional parent formations with stone to preserve vanilla ore host types.
        return ((y + Math.floorDiv(x, 37) + Math.floorDiv(z, 43)) & 31) < 10 ? STONE : surface.rock();
    }
}
