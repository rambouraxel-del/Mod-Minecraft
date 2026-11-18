package fr.azerya.worldgen.geo;

/** Continuous climate fields. Values are normalized to the interval [0, 1]. */
public final class Climate {
    private final long seed;
    private final Terrain terrain;

    public Climate(long seed, Terrain terrain) {
        this.seed = seed;
        this.terrain = terrain;
    }

    public record Sample(double temperature, double precipitation) {}

    public Sample sample(int x, int z, double height) {
        // Z grows southward. The full map spans the pole-to-equator gradient.
        double latitude = clamp(0.5 + z / 240_000.0);
        double variation = noise(x / 11000.0, z / 11000.0, 0x51edL) - 0.5;
        double ocean = terrain.oceanProximity(x, z);
        double temperature = clamp(latitude + variation * .12
                + (0.50 - latitude) * ocean * .12 - Math.max(0, height - 90) / 480.0);

        double humidity = noise(x / 17000.0, z / 17000.0, 0x8ac7L);
        double equatorialRain = .24 * smooth((latitude - .72) / .25);
        double subtropicalDryness = .07 * Math.max(0, 1 - Math.abs(latitude - .71) / .19);
        double temperateRain = .13 * Math.max(0, 1 - Math.abs(latitude - .48) / .31);
        double altitudeRain = .08 * smooth((height - 70) / 90) - .15 * smooth((height - 155) / 150);
        double precipitation = clamp(.15 + humidity * .36 + ocean * .25
                + equatorialRain - subtropicalDryness + temperateRain + altitudeRain);
        return new Sample(temperature, precipitation);
    }

    public Sample sample(int x, int z) { return sample(x, z, terrain.height(x, z)); }

    private double noise(double x, double z, long salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = random(ix, iz, salt), b = random(ix + 1, iz, salt);
        double c = random(ix, iz + 1, salt), d = random(ix + 1, iz + 1, salt);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }
    private double random(int x, int z, long salt) {
        long v = seed ^ salt ^ (x * 0x9e3779b97f4a7c15L) ^ (z * 0xc2b2ae3d27d4eb4fL);
        v ^= v >>> 30; v *= 0xbf58476d1ce4e5b9L; v ^= v >>> 27;
        v *= 0x94d049bb133111ebL; v ^= v >>> 31;
        return (v >>> 11) * 0x1.0p-53;
    }
    private static double smooth(double value) {
        double t = clamp(value);
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
