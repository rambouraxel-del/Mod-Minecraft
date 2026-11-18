package fr.azerya.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

public final class PlanetBiomes extends BiomeSource {
    static final MapCodec<PlanetBiomes> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(RegistryOps.<Biome, PlanetBiomes>retrieveGetter(Registries.BIOME)).apply(instance, PlanetBiomes::new));

    private final Map<Planet.Region, Holder<Biome>> biomes = new EnumMap<>(Planet.Region.class);

    private PlanetBiomes(HolderGetter<Biome> registry) {
        for (Planet.Region region : Planet.Region.values()) {
            biomes.put(region, registry.getOrThrow(vanillaBiome(region)));
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() { return biomes.values().stream(); }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        Planet planet = ((PlanetAccess) (Object) sampler).azeryaWorldgen$getPlanet();
        if (planet == null) return (x, y, z) -> biomes.get(Planet.Region.FOREST);
        return (x, y, z) -> resolve(planet, x, z);
    }

    @Override
    public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minX, int minY,
            int minZ, int sizeX, int sizeY, int sizeZ) {
        Planet planet = ((PlanetAccess) (Object) sampler).azeryaWorldgen$getPlanet();
        if (planet == null || sizeX != 4 || sizeZ != 4) return createResolver(sampler);
        // A chunk stores four by four quart columns. Reuse their surface classification at all elevations.
        var columns = new java.util.ArrayList<Holder<Biome>>(16);
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) columns.add(resolve(planet, minX + x, minZ + z));
        }
        return (x, y, z) -> {
            int localX = x - minX;
            int localZ = z - minZ;
            return (localX | localZ) >= 0 && localX < 4 && localZ < 4
                    ? columns.get(localZ * 4 + localX) : resolve(planet, x, z);
        };
    }

    private Holder<Biome> resolve(Planet planet, int x, int z) {
        return biomes.get(planet.region(QuartPos.toBlock(x), QuartPos.toBlock(z)));
    }

    private static ResourceKey<Biome> vanillaBiome(Planet.Region region) {
        return switch (region) {
            case OPEN_OCEAN -> Biomes.OCEAN;
            case DEEP_OCEAN -> Biomes.DEEP_OCEAN;
            case WARM_OCEAN -> Biomes.LUKEWARM_OCEAN;
            case COLD_OCEAN -> Biomes.COLD_OCEAN;
            case FROZEN_OCEAN -> Biomes.FROZEN_OCEAN;
            case BEACH -> Biomes.BEACH;
            case SNOWY_BEACH -> Biomes.SNOWY_BEACH;
            case ROCKY_SHORE -> Biomes.STONY_SHORE;
            case RIVER -> Biomes.RIVER;
            case FROZEN_RIVER -> Biomes.FROZEN_RIVER;
            case JUNGLE -> Biomes.JUNGLE;
            case SPARSE_JUNGLE -> Biomes.SPARSE_JUNGLE;
            case SAVANNA -> Biomes.SAVANNA;
            case DESERT -> Biomes.DESERT;
            case FOREST -> Biomes.FOREST;
            case PLAINS -> Biomes.PLAINS;
            case TAIGA -> Biomes.TAIGA;
            case SNOWY_TAIGA -> Biomes.SNOWY_TAIGA;
            case TUNDRA -> Biomes.SNOWY_PLAINS;
            case PEAKS -> Biomes.STONY_PEAKS;
            case SNOWY_PEAKS -> Biomes.FROZEN_PEAKS;
        };
    }
}
