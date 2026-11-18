package fr.azerya.worldgen.geo;

import java.util.Arrays;
import java.util.PriorityQueue;

/** Globally routed drainage graph, built once and shared safely by concurrent samplers. */
public final class Hydrology {
    public static final int NO_WATER = Integer.MIN_VALUE;
    public static final int GRID = 400;
    private static final int MIN = -300, MAX = 300, SIDE = MAX - MIN + 1, COUNT = SIDE * SIDE;
    private static final double EPSILON = 0.001;
    private final long seed;
    private final Terrain terrain;
    private final Climate climate;
    private volatile Network network;

    public Hydrology(long seed, Terrain terrain) {
        this.seed = seed;
        this.terrain = terrain;
        this.climate = new Climate(seed, terrain);
    }

    /** bedY is the top solid block; waterY is the top water block or NO_WATER. */
    public record Sample(int bedY, int waterY, boolean river, boolean lake) {}
    /** Coordinates are signed grid indices, each GRID blocks apart. */
    public record Drainage(int gridX, int gridZ, int downstreamX, int downstreamZ,
                           double surface, double downstreamSurface, double discharge) {}
    private record Entry(int index, double level) implements Comparable<Entry> {
        public int compareTo(Entry other) {
            int c = Double.compare(level, other.level);
            return c != 0 ? c : Integer.compare(index, other.index);
        }
    }
    private static final class Network {
        final double[] raw = new double[COUNT];
        final double[] filled = new double[COUNT];
        final double[] discharge = new double[COUNT];
        final int[] next = new int[COUNT];
    }

    private Network network() {
        Network result = network;
        if (result == null) synchronized (this) {
            result = network;
            if (result == null) network = result = build();
        }
        return result;
    }

    private Network build() {
        Network n = new Network();
        Arrays.fill(n.next, -1);
        boolean[] visited = new boolean[COUNT];
        int[] order = new int[COUNT];
        PriorityQueue<Entry> queue = new PriorityQueue<>();
        for (int z = MIN; z <= MAX; z++) for (int x = MIN; x <= MAX; x++) {
            int i = index(x, z), wx = x * GRID, wz = z * GRID;
            double h = terrain.height(wx, wz);
            n.raw[i] = h;
            n.discharge[i] = 0.25 + climate.sample(wx, wz, h).precipitation();
            if (h <= 63 || x == MIN || x == MAX || z == MIN || z == MAX) {
                visited[i] = true;
                n.filled[i] = h;
                n.next[i] = i;
                queue.add(new Entry(i, h));
            }
        }
        int used = 0;
        while (!queue.isEmpty()) {
            Entry e = queue.remove();
            int i = e.index(), gx = gridX(i), gz = gridZ(i);
            order[used++] = i;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int x = gx + dx, z = gz + dz;
                if (x < MIN || x > MAX || z < MIN || z > MAX) continue;
                int j = index(x, z);
                if (visited[j]) continue;
                visited[j] = true;
                n.next[j] = i;
                n.filled[j] = Math.max(n.raw[j], n.filled[i] + EPSILON);
                queue.add(new Entry(j, n.filled[j]));
            }
        }
        // Every edge points to an earlier popped cell. Reverse order therefore accumulates
        // contributions from the entire upstream basin, including water passing through lakes.
        for (int k = used - 1; k >= 0; k--) {
            int i = order[k], j = n.next[i];
            if (i != j) n.discharge[j] += n.discharge[i];
        }
        return n;
    }

    public Sample sample(int x, int z) { return sample(x, z, terrain.height(x, z)); }

    public Sample sample(int x, int z, double terrainHeight) {
        int original = (int) Math.floor(terrainHeight);
        if (original < 63) return new Sample(original, 63, false, false);
        Network n = network();
        int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
        if (gx < MIN || gx >= MAX || gz < MIN || gz >= MAX) return new Sample(original, NO_WATER, false, false);
        double fx = Math.floorMod(x, GRID) / (double) GRID, fz = Math.floorMod(z, GRID) / (double) GRID;
        double fill = bilinear(n.filled, gx, gz, fx, fz);
        double raw = bilinear(n.raw, gx, gz, fx, fz);
        // The filled-minus-original field identifies depression storage; interpolate it
        // rather than extending a lake as a circular patch around a single grid vertex.
        if (fill - raw > 1.0 && terrainHeight < fill - 0.5 && fill > 63) {
            int water = (int) Math.floor(fill);
            int bed = Math.min(original, water - 1);
            return new Sample(bed, water, false, true);
        }
        double best = 0, bestWater = 0, bestDepth = 0;
        for (int dz = -1; dz <= 2; dz++) for (int dx = -1; dx <= 2; dx++) {
            int cx = gx + dx, cz = gz + dz;
            if (cx < MIN || cx > MAX || cz < MIN || cz > MAX) continue;
            int i = index(cx, cz), j = n.next[i];
            if (i == j || n.discharge[i] < 4) continue;
            int nx = gridX(j), nz = gridZ(j);
            double ax = cx * GRID, az = cz * GRID, vx = (nx - cx) * GRID, vz = (nz - cz) * GRID;
            double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            // The offset fades at graph vertices, so tributaries meet without seams.
            long hash = seed ^ ((long) cx * 0x9e3779b97f4a7c15L) ^ ((long) cz * 0xc2b2ae3d27d4eb4fL);
            hash ^= hash >>> 29; hash *= 0xbf58476d1ce4e5b9L; hash ^= hash >>> 31;
            double bend = (((hash >>> 11) * 0x1.0p-53) * 2 - 1) * 24 * Math.sin(Math.PI * t);
            double length = Math.hypot(vx, vz);
            double distance = Math.hypot(x - ax - t * vx + bend * vz / length, z - az - t * vz - bend * vx / length);
            double width = Math.min(45, 3 + Math.sqrt(n.discharge[i]) * 0.7);
            double strength = smooth(1 - distance / (width + 7));
            double level = Math.max(63, n.filled[i] + (n.filled[j] - n.filled[i]) * t - 1);
            if (strength > best) {
                best = strength; bestWater = level;
                bestDepth = Math.min(9, 1.5 + Math.sqrt(n.discharge[i]) * 0.22);
            }
        }
        if (best == 0) return new Sample(original, NO_WATER, false, false);
        int bed = Math.min(original, (int) Math.floor(terrainHeight + (bestWater - bestDepth - terrainHeight) * best));
        int water = (int) Math.floor(bestWater);
        return bed < water ? new Sample(bed, water, true, false) : new Sample(bed, NO_WATER, false, false);
    }

    public Drainage drainage(int gridX, int gridZ) {
        if (gridX < MIN || gridX > MAX || gridZ < MIN || gridZ > MAX) throw new IllegalArgumentException("Outside drainage grid");
        Network n = network();
        int i = index(gridX, gridZ), j = n.next[i];
        return new Drainage(gridX, gridZ, gridX(j), gridZ(j), n.filled[i], n.filled[j], n.discharge[i]);
    }
    private static double bilinear(double[] values, int x, int z, double fx, double fz) {
        double a = values[index(x, z)], b = values[index(x + 1, z)];
        double c = values[index(x, z + 1)], d = values[index(x + 1, z + 1)];
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }
    private static int index(int x, int z) { return (z - MIN) * SIDE + x - MIN; }
    private static int gridX(int i) { return i % SIDE + MIN; }
    private static int gridZ(int i) { return i / SIDE + MIN; }
    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
}
