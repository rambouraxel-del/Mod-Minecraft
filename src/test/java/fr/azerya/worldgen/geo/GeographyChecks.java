package fr.azerya.worldgen.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Standalone deterministic checks for the core geography fields; run with assertions enabled or not. */
public final class GeographyChecks {
    private GeographyChecks() {}

    public static void main(String[] args) throws Exception {
        long[] seeds = {0L, 1L, 42L, 0x5EEDC0DEL};
        double[] landRatios = new double[seeds.length];
        for (int i = 0; i < seeds.length; i++) {
            Terrain terrain = new Terrain(seeds[i]);
            checkRangeAndRepeatability(terrain);
            landRatios[i] = checkContinents(terrain);
            checkMountainRanges(terrain);
            checkChunkBoundaries(terrain, seeds[i]);
            checkConcurrentOrder(terrain);
            checkClimate(terrain, seeds[i]);
            checkHydrology(terrain, seeds[i]);
            if (i == 0) checkHydrologyConcurrent(terrain, seeds[i]);
        }
        require(landRatios[0] > .12 && landRatios[0] < .88,
                "seed 0 land ratio outside broad continent bounds: " + landRatios[0]);
        require(Math.abs(landRatios[0] - landRatios[1]) + Math.abs(landRatios[1] - landRatios[2]) > .015,
                "land/continent layout did not measurably vary across seeds: " + List.of(landRatios[0], landRatios[1], landRatios[2]));
        System.out.println("GeographyChecks passed; sampled land ratios=" + List.of(landRatios[0], landRatios[1], landRatios[2]));
    }

    private static void checkRangeAndRepeatability(Terrain t) {
        for (int z = -16384; z <= 16384; z += 1024) for (int x = -16384; x <= 16384; x += 1024) {
            double h = t.height(x, z), macro = t.macroHeight(x, z), ocean = t.oceanProximity(x, z);
            Terrain.Geology g = t.geology(x, z);
            require(Double.isFinite(h) && h >= -54 && h <= 310, "height outside documented limits");
            require(Double.isFinite(macro), "non-finite macro height");
            require(inUnit(ocean) && inUnit(g.mountainStrength()), "unit field outside [0,1]");
            require(Double.isFinite(g.continentalness()) && Double.isFinite(g.uplift()), "non-finite geology");
            require(Double.doubleToLongBits(h) == Double.doubleToLongBits(t.height(x, z)), "repeated height differs");
            require(Double.doubleToLongBits(ocean) == Double.doubleToLongBits(t.oceanProximity(x, z)), "repeated ocean field differs");
        }
    }

    private static double checkContinents(Terrain t) {
        final int cells = 61, spacing = 4000;
        boolean[][] land = new boolean[cells][cells], ocean = new boolean[cells][cells];
        int landCount = 0, oceanCount = 0;
        // Cover the full playable square, including both boundary rows and columns.
        for (int gz = 0; gz < cells; gz++) for (int gx = 0; gx < cells; gx++) {
            int x = Math.min(119999, -120000 + gx * spacing);
            int z = Math.min(119999, -120000 + gz * spacing);
            double c = t.geology(x, z).continentalness();
            land[gz][gx] = c > .10;
            ocean[gz][gx] = c < -.20;
            if (land[gz][gx]) landCount++;
            if (ocean[gz][gx]) oceanCount++;
        }
        int count = cells * cells;
        double ratio = landCount / (double) count;
        require(ratio > .12 && ratio < .88, "implausible land area ratio: " + ratio);
        require(oceanCount / (double)count > .03, "ocean area absent");
        require(largestComponent(land) >= 20, "full map has no connected multikilometre landmass");
        require(largestComponent(ocean) >= 20, "full map has no connected multikilometre ocean");
        return ratio;
    }

    private static int largestComponent(boolean[][] field) {
        int size = field.length, largest = 0;
        boolean[][] seen = new boolean[size][size];
        int[] queueX = new int[size * size], queueZ = new int[size * size];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            if (!field[z][x] || seen[z][x]) continue;
            int head = 0, tail = 0;
            queueX[tail] = x; queueZ[tail++] = z; seen[z][x] = true;
            while (head < tail) {
                int cx = queueX[head], cz = queueZ[head++];
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = cx + dx, nz = cz + dz;
                    if (nx >= 0 && nx < size && nz >= 0 && nz < size && field[nz][nx] && !seen[nz][nx]) {
                        seen[nz][nx] = true; queueX[tail] = nx; queueZ[tail++] = nz;
                    }
                }
            }
            largest = Math.max(largest, tail);
        }
        return largest;
    }

    private static void checkMountainRanges(Terrain t) {
        int active = 0, linked = 0;
        // Long-spacing samples should expose regional uplift bands rather than isolated peaks.
        for (int z = -24000; z <= 24000; z += 2000) for (int x = -24000; x <= 24000; x += 2000) {
            Terrain.Geology g = t.geology(x, z);
            if (g.mountainStrength() > .55 && g.continentalness() > -.08) {
                active++;
                if (t.geology(x + 2000, z).mountainStrength() > .35 || t.geology(x, z + 2000).mountainStrength() > .35) linked++;
            }
        }
        require(active >= 5, "no substantial mountain uplift sampled");
        require(linked / (double)active > .25, "mountain uplift lacks connected chain structure: " + linked + "/" + active);
    }

    private static void checkChunkBoundaries(Terrain t, long seed) {
        Terrain independentlySampled = new Terrain(seed);
        // Every field is global-coordinate based. Probe both sides and corners of many chunk edges.
        for (int sample = 0; sample <= 60; sample++) {
            int edge = Math.min(119984, -120000 + sample * 4000);
            edge = Math.floorDiv(edge, 16) * 16;
            int k = Math.min(119999, -120000 + sample * 4000);
            for (int[] p : new int[][] {{edge - 1, k}, {edge, k}, {edge + 1, k}, {k, edge - 1}, {k, edge}, {k, edge + 1}}) {
                int x = p[0], z = p[1];
                double h = t.height(x, z);
                require(Double.doubleToLongBits(h) == Double.doubleToLongBits(independentlySampled.height(x, z)),
                        "boundary sample depends on instance/query order");
                if (x == edge - 1) require(Math.abs(t.height(x + 1, z) - h) < 8, "height jump across chunk edge");
                if (z == edge - 1) require(Math.abs(t.height(x, z + 1) - h) < 8, "height jump across chunk edge");
            }
        }
    }

    private static void checkClimate(Terrain t, long seed) {
        Climate climate = new Climate(seed, t);
        double north = climate.sample(0, 95000, 80).temperature();
        double south = climate.sample(0, -95000, 80).temperature();
        require(north >= 0 && north <= 1 && south >= 0 && south <= 1, "temperature outside [0,1]");
        require(north > south + .55, "latitude temperature transition is missing");
        double[] bandTemperature = new double[5], bandRain = new double[5];
        for (int band = 0; band < 5; band++) {
            int z = -100000 + band * 50000;
            for (int x = -100000; x <= 100000; x += 20000) {
                Climate.Sample s = climate.sample(x, z);
                bandTemperature[band] += s.temperature() / 11;
                bandRain[band] += s.precipitation() / 11;
            }
        }
        for (int band = 0; band < 4; band++) {
            require(bandTemperature[band + 1] > bandTemperature[band] + .05,
                    "temperature means do not progress across latitude bands: " + List.of(bandTemperature[band], bandTemperature[band + 1]));
        }
        require(bandRain[4] > bandRain[3] + .02, "equatorial rainfall band not wetter than subtropics");
        for (int z = -32000; z <= 32000; z += 4000) for (int x = -32000; x <= 32000; x += 4000) {
            Climate.Sample s = climate.sample(x, z);
            require(inUnit(s.temperature()) && inUnit(s.precipitation()), "climate outside [0,1]");
            require(Double.doubleToLongBits(s.temperature()) == Double.doubleToLongBits(climate.sample(x, z).temperature()),
                    "climate is not repeatable");
        }
    }

    private static void checkHydrology(Terrain t, long seed) {
        Hydrology water = new Hydrology(seed, t);
        for (int z = -12; z <= 12; z += 3) for (int x = -12; x <= 12; x += 3) {
            Hydrology.Drainage d = water.drainage(x, z);
            boolean sink = d.downstreamX() == x && d.downstreamZ() == z;
            require(sink || d.downstreamSurface() < d.surface(), "drainage edge is not strictly downhill");
            require(Double.isFinite(d.surface()) && Double.isFinite(d.downstreamSurface()) && d.discharge() >= 0,
                    "invalid drainage values");
            if (!sink) {
                Hydrology.Drainage downstream = water.drainage(d.downstreamX(), d.downstreamZ());
                require(downstream.discharge() + 1e-9 >= d.discharge(), "downstream discharge lost upstream flow");
            }
            Hydrology.Sample s = water.sample(x * 128, z * 128);
            require(s.bedY() <= s.waterY() || s.waterY() == Hydrology.NO_WATER, "water bed is above surface");
            require(!s.river() || s.waterY() != Hydrology.NO_WATER, "river missing water surface");
            require(!s.lake() || s.waterY() != Hydrology.NO_WATER, "lake missing water surface");
        }
        // Chunk edges should not change a river/lake decision when the same block is queried again.
        for (int edge = -2048; edge <= 2048; edge += 16) {
            for (int offset = -128; offset <= 128; offset += 32) {
                checkWaterRepeatability(water, t, edge - 1, offset);
                checkWaterRepeatability(water, t, edge, offset);
                checkWaterRepeatability(water, t, edge + 1, offset);
            }
        }
        // A sampled downstream walk must terminate at a sink, proving graph connectivity.
        for (int z = -9; z <= 9; z += 6) for (int x = -9; x <= 9; x += 6) {
            int gx = x, gz = z;
            boolean ended = false;
            for (int step = 0; step < 256; step++) {
                Hydrology.Drainage d = water.drainage(gx, gz);
                if (d.downstreamX() == gx && d.downstreamZ() == gz) { ended = true; break; }
                gx = d.downstreamX(); gz = d.downstreamZ();
            }
            require(ended, "drainage path failed to reach a sink");
        }
    }

    private static void checkWaterRepeatability(Hydrology water, Terrain t, int x, int z) {
        double height = t.height(x, z);
        Hydrology.Sample a = water.sample(x, z, height), b = water.sample(x, z, height);
        require(a.equals(b), "hydrology depends on sample order near chunk seam");
        require(a.bedY() <= a.waterY() || a.waterY() == Hydrology.NO_WATER, "invalid seam water level");
    }

    private static void checkHydrologyConcurrent(Terrain t, long seed) throws Exception {
        Hydrology shared = new Hydrology(seed, t);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Hydrology.Drainage>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final int x = i * 3 - 10, z = 10 - i * 2;
                jobs.add(() -> shared.drainage(x, z));
            }
            for (var future : pool.invokeAll(jobs)) {
                Hydrology.Drainage d = future.get();
                require(Double.isFinite(d.surface()) && Double.isFinite(d.downstreamSurface()),
                        "concurrent hydrology initialization returned invalid data");
                require(d.downstreamX() == d.gridX() && d.downstreamZ() == d.gridZ() || d.downstreamSurface() < d.surface(),
                        "concurrent drainage edge is not downhill");
            }
        }
    }

    private static void checkConcurrentOrder(Terrain t) throws Exception {
        List<int[]> points = new ArrayList<>();
        for (int z = -4096; z <= 4096; z += 257) for (int x = -4096; x <= 4096; x += 263) points.add(new int[] {x, z});
        double[] expected = new double[points.size()];
        for (int i = 0; i < points.size(); i++) expected[i] = t.height(points.get(i)[0], points.get(i)[1]);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = points.size() - 1; i >= 0; i--) {
                final int index = i;
                jobs.add(() -> {
                    int[] p = points.get(index);
                    return Double.doubleToLongBits(expected[index]) == Double.doubleToLongBits(t.height(p[0], p[1]));
                });
            }
            for (var result : pool.invokeAll(jobs)) require(result.get(), "concurrent/query-order result changed");
        }
    }

    private static boolean inUnit(double x) { return Double.isFinite(x) && x >= 0 && x <= 1; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
