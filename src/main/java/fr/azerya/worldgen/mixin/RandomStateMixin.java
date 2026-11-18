package fr.azerya.worldgen.mixin;

import fr.azerya.worldgen.ClimateSamplerAccess;
import fr.azerya.worldgen.Planet;
import fr.azerya.worldgen.PlanetAccess;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomState.class)
abstract class RandomStateMixin implements PlanetAccess {
    @Unique private long azeryaWorldgen$seed;
    @Unique private volatile Planet azeryaWorldgen$planet;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void azeryaWorldgen$captureSeed(HolderGetter<NormalNoise> noises, long seed,
            boolean legacy, BlockState defaultBlock, int seaLevel, NoiseRouter router, CallbackInfo callback) {
        azeryaWorldgen$seed = seed;
    }

    @Override
    public Planet azeryaWorldgen$getPlanet() {
        Planet model = azeryaWorldgen$planet;
        if (model == null) {
            synchronized (this) {
                model = azeryaWorldgen$planet;
                if (model == null) azeryaWorldgen$planet = model = new Planet(azeryaWorldgen$seed);
            }
        }
        return model;
    }

    @Inject(method = "createClimateSampler", at = @At("RETURN"))
    private void azeryaWorldgen$linkSampler(SamplerContext context, CallbackInfoReturnable<Climate.Sampler> callback) {
        ((ClimateSamplerAccess) (Object) callback.getReturnValue()).azeryaWorldgen$setOwner(this);
    }
}
