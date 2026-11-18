package fr.azerya.worldgen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AzeryaWorldgen implements ModInitializer {
    public static final String ID = "azerya_worldgen";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(ID, "climate"), PlanetBiomes.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(ID, "planet"), PlanetChunkGenerator.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var overworld = server.overworld();
            if (overworld.getChunkSource().getGenerator() instanceof PlanetChunkGenerator) {
                var border = overworld.getWorldBorder();
                border.setCenter(0, 0);
                border.setSize(2.0 * Planet.RADIUS);
                LOGGER.info("Azerya planet active: seed={}, border={}", overworld.getSeed(), 2 * Planet.RADIUS);
            }
        });
    }
}
