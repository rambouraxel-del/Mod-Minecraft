package fr.azerya.worldgen.diagnostics;

import javax.imageio.ImageIO;
import fr.azerya.worldgen.geo.Climate;
import fr.azerya.worldgen.geo.Hydrology;
import fr.azerya.worldgen.geo.Terrain;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Deterministic, dependency-free overview renderer for Worldgen V1. */
public final class WorldgenDiagnostic {
    private static final int DEFAULT_SIZE = 256;
    private static final int DEFAULT_BLOCKS = 240000;
    private static final int CELL = 2;
    private static final int MAP_TOP = 26;
    private static final int PANEL_EXTRA = 48;
    private static final int GAP = 12;
    private static final int MARGIN = 18;

    private WorldgenDiagnostic() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4) {
            System.err.println("Usage: WorldgenDiagnostic <seed> <output-dir> [size=256] [blocks=240000]");
            System.exit(2);
        }
        long seed = Long.parseLong(args[0]);
        Path out = Path.of(args[1]);
        int size = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_SIZE;
        int blocks = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_BLOCKS;
        if (size < 32 || blocks < size) throw new IllegalArgumentException("size >= 32 and blocks >= size required");
        Files.createDirectories(out);
        World world = new World(seed);
        Samples samples = sample(world, size, blocks);
        String suffix = seed == 42 ? "-overview" : "";
        render(seed, size, blocks, samples, world, out.resolve("worldgen-" + seed + suffix + ".png"));
        if (seed == 42) render(seed, size, 32768, sample(world, size, 32768), world, out.resolve("worldgen-42-detail.png"));
        writeSites(seed, world, out.resolve("worldgen-" + seed + "-sites.md"));
        System.out.println(out.resolve("worldgen-" + seed + suffix + ".png"));
    }

    private static Samples sample(World world, int size, int blocks) throws Exception {
        int[][] elev = new int[size][size];
        double[][] temp = new double[size][size], rain = new double[size][size];
        boolean[][] river = new boolean[size][size], lake = new boolean[size][size], sea = new boolean[size][size];
        int origin = -blocks / 2;
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int wx = origin + (int)((long)x * blocks / size), wz = origin + (int)((long)z * blocks / size);
            elev[z][x] = world.height(wx, wz);
            sea[z][x] = elev[z][x] < 63;
            Climate.Sample climate = world.climate.sample(wx, wz, elev[z][x]);
            temp[z][x] = climate.temperature();
            rain[z][x] = climate.precipitation();
            Hydro h = world.hydro(wx, wz);
            river[z][x] = h.river; lake[z][x] = h.lake;
        }
        return new Samples(size, elev, temp, rain, river, lake, sea);
    }

    private static void render(long seed, int size, int blocks, Samples s, World world, Path file) throws Exception {
        int panelW = size * CELL, panelH = size * CELL + PANEL_EXTRA;
        int w = MARGIN * 2 + panelW * 3 + GAP * 2;
        int h = MARGIN * 2 + panelH * 2 + GAP + 30;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(242, 244, 247)); g.fillRect(0, 0, w, h);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17)); g.setColor(new Color(26, 34, 47));
        g.drawString("Worldgen V1  •  seed " + seed + "  •  " + blocks + " × " + blocks + " blocks", MARGIN, 24);
        int[][] positions = {{MARGIN, MARGIN + 16}, {MARGIN + panelW + GAP, MARGIN + 16}, {MARGIN + 2*(panelW+GAP), MARGIN+16},
                {MARGIN, MARGIN + 16 + panelH + GAP}, {MARGIN + panelW + GAP, MARGIN + 16 + panelH + GAP}};
        drawPanel(g, positions[0], "LAND / OCEAN", s, 0);
        drawPanel(g, positions[1], "ELEVATION (Y)", s, 1);
        drawPanel(g, positions[2], "TEMPERATURE", s, 2);
        drawPanel(g, positions[3], "RAINFALL", s, 3);
        drawPanel(g, positions[4], "HYDROLOGY", s, 4);
        drawDrainage(g, positions[4], size, blocks, world);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11)); g.setColor(new Color(76, 84, 96));
        g.drawString("North ↑   •   west ←   •   image top is north   •   sampled at " + (blocks/(double)size) + " blocks/pixel", MARGIN, h-9);
        g.dispose(); ImageIO.write(img, "PNG", file.toFile());
    }

    private static void drawPanel(Graphics2D g, int[] p, String title, Samples s, int kind) {
        int x0=p[0], y0=p[1], size=s.n;
        g.setColor(Color.WHITE); g.fillRoundRect(x0, y0, size*CELL, size*CELL+PANEL_EXTRA, 8, 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); g.setColor(new Color(36, 44, 58)); g.drawString(title, x0+8, y0+19);
        int min=Integer.MAX_VALUE, max=Integer.MIN_VALUE;
        if (kind==1) for(int[] row:s.elev) for(int v:row){min=Math.min(min,v);max=Math.max(max,v);}
        else if(kind==2 || kind==3) { double[][] a=kind==2?s.temp:s.rain; min=(int)Double.POSITIVE_INFINITY; max=(int)Double.NEGATIVE_INFINITY; for(double[] row:a)for(double v:row)if(Double.isFinite(v)){min=Math.min(min,(int)Math.floor(v));max=Math.max(max,(int)Math.ceil(v));} }
        for(int z=0;z<size;z++) for(int x=0;x<size;x++) {
            Color c;
            switch(kind){
                case 0: c=s.sea[z][x]?new Color(55,132,190):new Color(116,161,92); break;
                case 1: c=elevation(s.elev[z][x],min,max); break;
                case 2: c=gradient(s.temp[z][x],min,max,new Color(54,111,193),new Color(239,93,61)); break;
                case 3: c=gradient(s.rain[z][x],min,max,new Color(236,220,155),new Color(37,128,103)); break;
                default: c=s.river[z][x]?new Color(25,94,220):s.lake[z][x]?new Color(67,190,220):s.sea[z][x]?new Color(111,170,202):new Color(218,202,151); break;
            }
            g.setColor(c); g.fillRect(x0+x*CELL,y0+MAP_TOP+z*CELL,CELL,CELL);
        }
        if(kind==4){g.setColor(new Color(45,52,62));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));g.drawString("river cells blue • lakes teal • ocean slate • land tan",x0+8,y0+MAP_TOP+size*CELL+15);}
        else {g.setColor(new Color(45,52,62));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10)); String legend=kind==0?"green land • blue ocean":kind==1?"low "+min+" Y  →  high "+max+" Y":kind==2?"cool "+min+"  →  warm "+max: "dry "+min+"  →  wet "+max;g.drawString(legend,x0+8,y0+MAP_TOP+size*CELL+15);}
        g.setColor(new Color(70,78,90));g.drawRect(x0,y0+MAP_TOP,size*CELL,size*CELL);
    }

    private static void drawDrainage(Graphics2D g, int[] p, int size, int blocks, World world) {
        Graphics2D overlay = (Graphics2D) g.create();
        int pixels = size * CELL;
        int left = p[0], top = p[1] + MAP_TOP;
        int min = Math.max(-300, Math.floorDiv(-blocks / 2, Hydrology.GRID));
        int max = Math.min(300, Math.floorDiv(blocks / 2, Hydrology.GRID));
        double scale = pixels / (double) blocks;
        boolean overview = blocks > 50_000;
        int threshold = overview ? 30 : 4;
        overlay.clipRect(left, top, pixels, pixels);
        overlay.setColor(new Color(23, 91, 221, 225));
        overlay.setStroke(new BasicStroke(overview ? .95f : 1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int gz = min; gz <= max; gz++) {
            for (int gx = min; gx <= max; gx++) {
                Hydrology.Drainage edge = world.hydrology.drainage(gx, gz);
                if (edge.discharge() < threshold || edge.surface() <= Terrain.SEA_LEVEL
                        || (edge.downstreamX() == gx && edge.downstreamZ() == gz)) continue;
                int x1 = left + mapPixel(gx, blocks, scale);
                int z1 = top + mapPixel(gz, blocks, scale);
                int x2 = left + mapPixel(edge.downstreamX(), blocks, scale);
                int z2 = top + mapPixel(edge.downstreamZ(), blocks, scale);
                overlay.drawLine(x1, z1, x2, z2);
            }
        }
        overlay.dispose();
        g.setColor(new Color(23, 91, 221));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(overview ? "main channels plotted from the basin graph" : "tributaries plotted from the basin graph",
                left + 8, top + pixels + 29);
    }

    private static int mapPixel(int gridCoordinate, int blocks, double scale) {
        return (int) Math.round((gridCoordinate * Hydrology.GRID + blocks / 2.0) * scale);
    }

    private static Color elevation(int v,int min,int max){double t=norm(v,min,max); Color[] stops={new Color(47,102,72),new Color(133,164,92),new Color(191,171,120),new Color(160,126,105),new Color(245,245,240)};return palette(t,stops);}
    private static Color gradient(double v,int min,int max,Color a,Color b){return mix(a,b,norm(v,min,max));}
    private static double norm(double v,double lo,double hi){return hi<=lo?.5:Math.max(0,Math.min(1,(v-lo)/(hi-lo)));}
    private static Color palette(double t,Color[] c){double q=t*(c.length-1);int i=Math.min(c.length-2,(int)q);return mix(c[i],c[i+1],q-i);}
    private static Color mix(Color a,Color b,double t){return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));}

    private static void writeSites(long seed,World world,Path file)throws Exception{
        final int extent=115000, step=250;
        Site forest=null,mountain=null,river=null,coast=null,hot=null,cold=null;
        double forestScore=-1e9,mountainScore=-1e9,riverScore=Double.POSITIVE_INFINITY,coastScore=-1e9;
        double hotVal=.82,coldVal=.14;
        for(int z=-extent;z<=extent;z+=step) for(int x=-extent;x<=extent;x+=step){
            int y=world.height(x,z);
            Climate.Sample climate = world.climate.sample(x, z, y);
            double t = climate.temperature(), r = climate.precipitation();
            Hydro h=world.hydro(x,z); boolean land=y>=63;
            double slope = Math.hypot(world.terrain.height(x + 5, z) - world.terrain.height(x - 5, z),
                    world.terrain.height(x, z + 5) - world.terrain.height(x, z - 5)) / 10.0;
            String physical="Y="+y+", bed="+h.bed+", water="+(h.water==Integer.MIN_VALUE?"none":h.water)+", slope="+String.format(java.util.Locale.ROOT,"%.3f",slope);
            double ocean=world.oceanProximity(x,z);
            if(land&&!h.river&&!h.lake){
                if(t>=.43&&t<=.68&&r>=.43&&y>=80&&y<170&&ocean<.25&&slope<.30){
                    int forestNeighbours = world.forestNeighbours(x, z);
                    double fs = forestNeighbours * 2 + r - ocean * .35 - slope * .25;
                    if(fs>forestScore){forestScore=fs;forest=new Site("Forest (FOREST climate rules)",x,z,physical+", temp="+fmt(t)+", rain="+fmt(r)+", ocean proximity="+fmt(ocean));}
                }
                if(y>125){double ms=y+slope*180;if(ms>mountainScore){mountainScore=ms;mountain=new Site("Mountains",x,z,physical);}}
                if(t>.82&&t>hotVal){hotVal=t;hot=new Site("Hot (on land)",x,z,physical+", temp="+fmt(t));}
                if(t<.14&&t<coldVal){coldVal=t;cold=new Site("Cold (on land)",x,z,physical+", temp="+fmt(t));}
                if(y<=67&&ocean>.41){boolean nearbyOcean=false;int[] dirs={-1,0,1};for(int radius=250;radius<=500&&!nearbyOcean;radius+=250)for(int dz:dirs)for(int dx:dirs){if(dx==0&&dz==0)continue;if(world.height(x+dx*radius,z+dz*radius)<63){nearbyOcean=true;break;}}
                    double cs=nearbyOcean?-Math.abs(y-65)-slope*8:-1e9;
                    if(cs>coastScore){coastScore=cs;coast=new Site("Coast",x,z,physical+", nearby ocean at 200–600 blocks");}}
            }
            if(h.river){double rs=(x/(double)step)*(x/(double)step)+(z/(double)step)*(z/(double)step);if(rs<riverScore){riverScore=rs;river=new Site("River",x,z,physical);}}
        }
        List<Site> sites=List.of(
                found(forest,"Forest","No matching inland FOREST site found"), found(mountain,"Mountains","No mountain site found"),
                found(river,"River","No sampled river site found"), found(coast,"Coast","No qualifying coast found"),
                found(hot,"Hot","No land site exceeded temperature 0.82"), found(cold,"Cold","No land site was below temperature 0.14"));
        StringBuilder b=new StringBuilder("# Interesting sites — seed ").append(seed).append("\n\nCandidate search: X/Z −115000 to +115000 (5 km clearance from world edges), sampled every ").append(step).append(" blocks. Forest candidates favor sites surrounded by dry temperate forest conditions within 2 km. Coast candidates have ocean within 200–600 blocks. Every row includes surface, bed, water and local slope.\n\n");
        b.append("| Site | X | Z | Surface details |\n|---|---:|---:|---|\n");
        for(Site site:sites) b.append("| ").append(site.name).append(" | ").append(site.x).append(" | ").append(site.z).append(" | ").append(site.note).append(" |\n");
        b.append("\nTeleport options: `/tp @s X ~ Z`. North is decreasing Z. Candidates are representative points; inspect nearby terrain in game.\n"); Files.writeString(file,b);
    }
    private static String fmt(double v){return String.format(java.util.Locale.ROOT,"%.3f",v);}
    private static Site found(Site s,String name,String missing){return s==null?new Site(name,0,0,missing):s;}
    private record Site(String name,int x,int z,String note){}
    private record Samples(int n, int[][] elev, double[][] temp, double[][] rain, boolean[][] river, boolean[][] lake, boolean[][] sea) {}

    private static final class World {
        final Terrain terrain;
        final Climate climate;
        final Hydrology hydrology;

        World(long seed) {
            terrain = new Terrain(seed);
            climate = new Climate(seed, terrain);
            hydrology = new Hydrology(seed, terrain);
        }

        int height(int x, int z) { return (int) Math.floor(terrain.height(x, z)); }
        Hydro hydro(int x, int z) {
            Hydrology.Sample sample = hydrology.sample(x, z);
            return new Hydro(sample.bedY(), sample.waterY(), sample.river(), sample.lake());
        }
        double oceanProximity(int x, int z) { return terrain.oceanProximity(x, z); }

        int forestNeighbours(int x, int z) {
            int count = 0;
            for (int dz = -2000; dz <= 2000; dz += 1000) {
                for (int dx = -2000; dx <= 2000; dx += 1000) {
                    int px = x + dx, pz = z + dz;
                    double height = terrain.height(px, pz);
                    Climate.Sample sample = climate.sample(px, pz, height);
                    if (height > 70 && height < 170 && sample.temperature() > .42 && sample.temperature() < .69
                            && sample.precipitation() > .42 && hydrology.sample(px, pz, height).waterY() == Hydrology.NO_WATER) {
                        count++;
                    }
                }
            }
            return count;
        }
    }
    private record Hydro(int bed,int water,boolean river,boolean lake){}
}
