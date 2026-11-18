package fr.azerya.worldgen;

import fr.azerya.worldgen.geo.Climate;
import fr.azerya.worldgen.geo.Hydrology;
import fr.azerya.worldgen.geo.Terrain;

/** Immutable world model. The drainage graph is shared by all chunk workers for this world. */
public final class Planet {
    public static final int RADIUS = 120_000;
    public static final int SEA = Terrain.SEA_LEVEL;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    private final long seed;
    private final Terrain terrain;
    private final Climate climate;
    private final Hydrology hydrology;

    public Planet(long seed) {
        this.seed = seed;
        terrain = new Terrain(seed);
        climate = new Climate(seed, terrain);
        hydrology = new Hydrology(seed, terrain);
    }

    enum Rock { SEDIMENTARY, GRANITIC, VOLCANIC, MIXED }

    enum Region {
        OPEN_OCEAN, DEEP_OCEAN, WARM_OCEAN, COLD_OCEAN, FROZEN_OCEAN,
        BEACH, SNOWY_BEACH, ROCKY_SHORE, RIVER, FROZEN_RIVER,
        JUNGLE, SPARSE_JUNGLE, SAVANNA, DESERT, FOREST, PLAINS,
        TAIGA, SNOWY_TAIGA, TUNDRA, PEAKS, SNOWY_PEAKS
    }

    record Column(int bed, int water, double temperature, double rain, double slope,
                  boolean river, boolean lake, Rock rock) {
        boolean wet() { return water > bed; }
    }

    Column column(int x, int z) {
        if (x < -RADIUS || x >= RADIUS || z < -RADIUS || z >= RADIUS) {
            // A quiet buffer outside the playable border for chunk rendering and generation neighbors.
            int cx = Math.clamp(x, -RADIUS, RADIUS - 1);
            int cz = Math.clamp(z, -RADIUS, RADIUS - 1);
            double distance = Math.max(Math.abs((long) x - cx), Math.abs((long) z - cz));
            Hydrology.Sample edge = hydrology.sample(cx, cz);
            double blend = Math.min(1.0, distance / 160.0);
            int floor = (int) Math.round(edge.bedY() + (45 - edge.bedY()) * blend);
            int water = floor < SEA ? SEA : Hydrology.NO_WATER;
            return new Column(floor, water, climate.sample(cx, cz, floor).temperature(),
                    .6, 0, false, false, Rock.SEDIMENTARY);
        }
        double height = terrain.height(x, z);
        Hydrology.Sample drainage = hydrology.sample(x, z, height);
        Climate.Sample weather = climate.sample(x, z, height);
        double slope = Math.hypot(terrain.height(x + 5, z) - terrain.height(x - 5, z),
                terrain.height(x, z + 5) - terrain.height(x, z - 5)) / 10.0;
        return new Column(drainage.bedY(), drainage.waterY(), weather.temperature(), weather.precipitation(),
                slope, drainage.river(), drainage.lake(), rock(x, z));
    }

    Region region(int x, int z) {
        return region(x, z, column(x, z));
    }

    Region region(int x, int z, Column column) {
        double temperature = column.temperature();
        double rain = column.rain();
        int bed = column.bed();
        if (column.river() || column.lake()) {
            return temperature < .19 ? Region.FROZEN_RIVER : Region.RIVER;
        }
        if (column.wet()) {
            if (temperature < .15) return Region.FROZEN_OCEAN;
            if (temperature < .32) return Region.COLD_OCEAN;
            if (temperature > .76 && bed > 28) return Region.WARM_OCEAN;
            return bed < 35 ? Region.DEEP_OCEAN : Region.OPEN_OCEAN;
        }
        if (bed <= SEA + 4 && terrain.oceanProximity(x, z) > .41) {
            if (column.slope() > .3) return Region.ROCKY_SHORE;
            return temperature < .19 ? Region.SNOWY_BEACH : Region.BEACH;
        }
        if (bed > 212 || (bed > 157 && column.slope() > .65)) {
            return temperature < .38 ? Region.SNOWY_PEAKS : Region.PEAKS;
        }
        if (temperature < .14) return Region.TUNDRA;
        if (temperature < .27) return Region.SNOWY_TAIGA;
        if (temperature < .42) return Region.TAIGA;
        if (temperature > .69) {
            if (rain > .60) return Region.JUNGLE;
            if (rain > .48 && temperature > .81) return Region.SPARSE_JUNGLE;
            if (rain < .35) return Region.DESERT;
            if (rain < .52) return Region.SAVANNA;
        }
        double forestThreshold = column.rock() == Rock.GRANITIC ? .42 : .40;
        return rain >= forestThreshold ? Region.FOREST : Region.PLAINS;
    }

    private Rock rock(int x, int z) {
        double formation = noise(x / 9600.0, 0.0, z / 9600.0, 301);
        if (formation < -.27) return Rock.SEDIMENTARY;
        if (formation > .30) return Rock.GRANITIC;
        if (terrain.geology(x, z).mountainStrength() > .42 && formation > -.08) return Rock.VOLCANIC;
        return Rock.MIXED;
    }

    /** Coherent underground galleries with a protective roof below soil and water. */
    boolean cave(int x, int y, int z, Column column) {
        int roof = column.wet() ? 18 : 8;
        if (y <= MIN_Y + 5 || y >= column.bed() - roof) return false;
        double first = noise(x / 55.0, y / 34.0, z / 55.0, 401);
        if (Math.abs(first) > .15) return false;
        double second = noise(x / 48.0, y / 38.0, z / 48.0, 479);
        return Math.abs(second) < .10 && first * first + second * second < .024;
    }

    boolean bedrock(int x, int y, int z) {
        if (y == MIN_Y) return true;
        return y < MIN_Y + 4 && random(x, y, z, 601) * 4 >= y - MIN_Y;
    }

    private double noise(double x, double y, double z, int salt) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double dx = smooth(x - ix), dy = smooth(y - iy), dz = smooth(z - iz);
        double a = lerp(random(ix, iy, iz, salt), random(ix + 1, iy, iz, salt), dx);
        double b = lerp(random(ix, iy + 1, iz, salt), random(ix + 1, iy + 1, iz, salt), dx);
        double c = lerp(random(ix, iy, iz + 1, salt), random(ix + 1, iy, iz + 1, salt), dx);
        double d = lerp(random(ix, iy + 1, iz + 1, salt), random(ix + 1, iy + 1, iz + 1, salt), dx);
        return 2.0 * lerp(lerp(a, b, dy), lerp(c, d, dy), dz) - 1.0;
    }

    private double random(int x, int y, int z, int salt) {
        long hash = seed ^ (x * 0x9e3779b97f4a7c15L) ^ (z * 0xc2b2ae3d27d4eb4fL)
                ^ (y * 0x165667b19e3779f9L) ^ (salt * 0x94d049bb133111ebL);
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        return ((hash ^ (hash >>> 31)) >>> 11) * 0x1.0p-53;
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double smooth(double fraction) {
        return fraction * fraction * (3 - 2 * fraction);
    }
}
