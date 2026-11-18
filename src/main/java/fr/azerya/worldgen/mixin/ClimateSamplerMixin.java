package fr.azerya.worldgen.mixin;

import fr.azerya.worldgen.ClimateSamplerAccess;
import fr.azerya.worldgen.Planet;
import fr.azerya.worldgen.PlanetAccess;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Climate.Sampler.class)
abstract class ClimateSamplerMixin implements ClimateSamplerAccess {
    @Unique private PlanetAccess azeryaWorldgen$owner;

    @Override
    public void azeryaWorldgen$setOwner(PlanetAccess owner) {
        azeryaWorldgen$owner = owner;
    }

    @Override
    public Planet azeryaWorldgen$getPlanet() {
        return azeryaWorldgen$owner == null ? null : azeryaWorldgen$owner.azeryaWorldgen$getPlanet();
    }
}
