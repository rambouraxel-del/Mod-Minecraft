package fr.azerya.worldgen.geo;

/** A deterministic, global height field. Coordinates and heights are in blocks. */
public final class Terrain {
    public static final int SEA_LEVEL = 63;
    private final long seed;

    public Terrain(long seed) { this.seed = seed; }

    /** Continental setting and tectonic uplift, sampled independently of chunk boundaries. */
    public record Geology(double continentalness, double mountainStrength, double uplift) {}

    public Geology geology(int x, int z) {
        double wx = x + 2800 * fbm(x / 17000.0, z / 17000.0, 11, 3);
        double wz = z + 2800 * fbm(x / 17000.0, z / 17000.0, 29, 3);
        // Large-scale continental interiors are tens of kilometres across. The low-frequency
        // term lets different regions have different ocean/land proportions.
        double continent = .78 * fbm(wx / 18500.0, wz / 18500.0, 47, 4)
                + .22 * noise(wx / 52000.0, wz / 52000.0, 61);
        double plateX = wx / 12500.0, plateZ = wz / 12500.0;
        int cx = floor(plateX), cz = floor(plateZ);
        double first = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            int px = cx + dx, pz = cz + dz;
            long h = hash(px, pz, 83);
            double fx = px + .15 + .7 * unit(h), fz = pz + .15 + .7 * unit(mix(h));
            double d = (plateX-fx)*(plateX-fx) + (plateZ-fz)*(plateZ-fz);
            if (d < first) { second = first; first = d; }
            else if (d < second) { second = d; }
        }
        // Distance to a Voronoi plate boundary gives long, connected ranges; a
        // broad shoulder and individual uplift variation avoid uniform walls.
        double edge = Math.sqrt(second) - Math.sqrt(first);
        double boundary = 1.0 - smoothstep(.025, .24, edge);
        double activity = .72 + .20 * noise(wx / 9000.0, wz / 9000.0, 97);
        double land = smoothstep(-.08, .12, continent);
        double strength = boundary * activity * land;
        return new Geology(continent, strength, boundary * activity);
    }

    /** Smooth continental water influence: zero inland and one in the deep ocean. */
    public double oceanProximity(int x, int z) {
        double c = geology(x, z).continentalness();
        return 1.0 - smoothstep(-.20, .10, c);
    }

    /** Coarse drainage elevation, without local hills or fine terrain detail. */
    public double macroHeight(int x, int z) {
        Geology g = geology(x, z);
        double c = g.continentalness();
        double land = smoothstep(-.08, .10, c);
        double seabed = -(8 + 96 * smoothstep(-.09, -.42, c));
        double ground = 4 + 28 * smoothstep(.0, .48, c) + 125 * g.mountainStrength();
        return SEA_LEVEL + lerp(seabed, ground, land);
    }

    /** Surface elevation, continuous at chunk borders and stable for any sampling order. */
    public double height(int x, int z) {
        Geology g = geology(x, z);
        double c = g.continentalness();
        double land = smoothstep(-.08, .10, c);
        double oceanDepth = 8 + 96 * smoothstep(-.09, -.42, c);
        double landBase = 4 + 28 * smoothstep(.0, .48, c);
        double rolling = fbm(x / 1900.0, z / 1900.0, 101, 4);
        double detail = fbm(x / 340.0, z / 340.0, 137, 3);
        double fine = fbm(x / 85.0, z / 85.0, 151, 2);
        double ridgeNoise = 1 - Math.abs(fbm(x / 2400.0, z / 2400.0, 173, 3));
        double mountains = 190 * g.mountainStrength() * (.35 + .65 * ridgeNoise * ridgeNoise);
        double featureMask = smoothstep(.20, .48, fbm(x / 2800.0, z / 2800.0, 211, 3));
        double escarpment = 14 * featureMask * (smoothstep(-.055, .055, noise(x / 500.0, z / 500.0, 223)) - .5);
        double ravine = 17 * featureMask * (1 - smoothstep(.015, .07, Math.abs(noise(x / 650.0, z / 650.0, 227))));
        double ground = landBase + escarpment - ravine + rolling * (12 + 20 * g.mountainStrength())
                + detail * (4 + 12 * g.mountainStrength()) + fine * 2 + mountains;
        double seabed = -oceanDepth + 5 * fbm(x / 2400.0, z / 2400.0, 193, 3);
        return Math.max(-54, Math.min(310, SEA_LEVEL + lerp(seabed, ground, land)));
    }

    private double fbm(double x, double z, long salt, int octaves) {
        double value = 0, amplitude = 1, total = 0;
        for (int i = 0; i < octaves; i++) {
            value += amplitude * noise(x, z, salt + i * 997);
            total += amplitude;
            x *= 2.03; z *= 2.03; amplitude *= .5;
        }
        return value / total;
    }

    private double noise(double x, double z, long salt) {
        int ix = floor(x), iz = floor(z);
        double tx = fade(x-ix), tz = fade(z-iz);
        double a = 2*unit(hash(ix, iz, salt))-1;
        double b = 2*unit(hash(ix+1, iz, salt))-1;
        double c = 2*unit(hash(ix, iz+1, salt))-1;
        double d = 2*unit(hash(ix+1, iz+1, salt))-1;
        return lerp(lerp(a,b,tx), lerp(c,d,tx), tz);
    }
    private long hash(int x, int z, long salt) {
        return mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L));
    }
    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27; x *= 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
    private static double unit(long x) { return (x >>> 11) * 0x1.0p-53; }
    private static int floor(double x) { int i = (int)x; return x < i ? i - 1 : i; }
    private static double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double lerp(double a, double b, double t) { return a + (b-a)*t; }
    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x-a)/(b-a)));
        return t*t*(3-2*t);
    }
}
