package uk.maddx.ccsn.network;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each colour's strand sits inside a cable block, and the packed "render key"
 * that describes a cable block (which colours, which way each one goes).
 *
 * Every colour has a fixed lane point, so a run lines up with the next block whatever
 * else shares either block. The points come from a 4x4 Latin square: looking down any
 * axis, no two colours share a lane, so strands running side by side never overlap.
 */
public final class CableLanes {
    public static final int MAX_RUNS = 4;
    private static final float[] LANE = { 5, 7, 9, 11 };
    /** Strand half-width in pixels (2 px strands). */
    public static final float HALF = 1;

    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final VoxelShape FALLBACK = Shapes.box(6 / 16.0, 6 / 16.0, 6 / 16.0, 10 / 16.0, 10 / 16.0, 10 / 16.0);

    /** Centre of a colour's strand, in pixels. */
    public static float[] lane(DyeColor colour) {
        int k = colour.ordinal(), i = k / 4, j = k % 4;
        return new float[] { LANE[i], LANE[j], LANE[(i + j) % 4] };
    }

    // ---- Render key: bits 0-15 colours present, then 6 side bits per present colour in ordinal order ----

    public static long key(int colourBits, int[] masksByOrdinal) {
        long key = colourBits & 0xFFFFL;
        int slot = 0;
        for (int c = 0; c < 16 && slot < MAX_RUNS; c++) {
            if ((colourBits & (1 << c)) == 0) continue;
            key |= (long) (masksByOrdinal[c] & 0x3F) << (16 + 6 * slot);
            slot++;
        }
        return key;
    }

    public static int colours(long key) {
        return (int) (key & 0xFFFF);
    }

    public static int mask(long key, DyeColor colour) {
        int bits = colours(key), slot = 0;
        for (int c = 0; c < 16 && slot < MAX_RUNS; c++) {
            if ((bits & (1 << c)) == 0) continue;
            if (c == colour.ordinal()) return (int) ((key >>> (16 + 6 * slot)) & 0x3F);
            slot++;
        }
        return 0;
    }

    public static List<DyeColor> present(long key) {
        List<DyeColor> out = new ArrayList<>(MAX_RUNS);
        int bits = colours(key);
        for (DyeColor c : DyeColor.values()) {
            if ((bits & (1 << c.ordinal())) != 0) out.add(c);
        }
        return out;
    }

    // ---- Geometry ----

    // ---- Covers: bits 40-41 cover type, bit 42 "ceiling above" ----

    public static final int COVER_NONE = 0, COVER_TRAY = 1, COVER_TRUNKING = 2, COVER_DISGUISE = 3;

    public static long withCover(long key, int cover, boolean ceiling) {
        return key | ((long) (cover & 3) << 40) | (ceiling ? 1L << 42 : 0);
    }

    public static int cover(long key) {
        return (int) ((key >>> 40) & 3);
    }

    public static boolean ceiling(long key) {
        return ((key >>> 42) & 1) != 0;
    }

    /** Every side any run goes out on. */
    public static int unionMask(long key) {
        int union = 0;
        for (DyeColor colour : present(key)) union |= mask(key, colour);
        return union;
    }

    // ---- Geometry ----

    /** Which texture a box uses. */
    public static final int TEX_STRAND = 0, TEX_TRAY = 1, TEX_TRAY_FLOOR = 2, TEX_ROD = 3, TEX_TRUNKING = 4;

    /** A box in pixels: x1, y1, z1, x2, y2, z2. {@code arm} is set for strand arms; {@code tex} picks the texture. */
    public record Box(float x1, float y1, float z1, float x2, float y2, float z2, Direction arm, int tex) {
        Box(float x1, float y1, float z1, float x2, float y2, float z2, Direction arm) {
            this(x1, y1, z1, x2, y2, z2, arm, TEX_STRAND);
        }

        static Box of(float x1, float y1, float z1, float x2, float y2, float z2, int tex) {
            return new Box(x1, y1, z1, x2, y2, z2, null, tex);
        }
    }

    private static boolean has(int mask, Direction dir) {
        return (mask & (1 << dir.ordinal())) != 0;
    }

    private static final float T0 = 3, T1 = 13, FLOOR_TOP = 4, RAIL_TOP = 9.5f;

    /**
     * A steel ladder tray around the runs, following them round bends and junctions, all inside the
     * block. With a ceiling above it hangs from two drop rods on a strut; going up or down it becomes
     * an enclosed riser.
     */
    public static List<Box> tray(int union, boolean ceiling) {
        boolean n = has(union, Direction.NORTH), s = has(union, Direction.SOUTH);
        boolean w = has(union, Direction.WEST), e = has(union, Direction.EAST);
        boolean up = has(union, Direction.UP), down = has(union, Direction.DOWN);
        List<Box> out = new ArrayList<>();

        // Floor: middle, plus out to each side a run leaves by.
        if (!down) out.add(Box.of(T0, 3, T0, T1, FLOOR_TOP, T1, TEX_TRAY_FLOOR));
        if (n) out.add(Box.of(T0, 3, 0, T1, FLOOR_TOP, T0, TEX_TRAY_FLOOR));
        if (s) out.add(Box.of(T0, 3, T1, T1, FLOOR_TOP, 16, TEX_TRAY_FLOOR));
        if (w) out.add(Box.of(0, 3, T0, T0, FLOOR_TOP, T1, TEX_TRAY_FLOOR));
        if (e) out.add(Box.of(T1, 3, T0, 16, FLOOR_TOP, T1, TEX_TRAY_FLOOR));

        // Side rails: round the middle where nothing leaves, and along both sides of each arm.
        if (!n) out.add(Box.of(T0, FLOOR_TOP, T0, T1, RAIL_TOP, T0 + 1, TEX_TRAY));
        if (!s) out.add(Box.of(T0, FLOOR_TOP, T1 - 1, T1, RAIL_TOP, T1, TEX_TRAY));
        if (!w) out.add(Box.of(T0, FLOOR_TOP, T0 + 1, T0 + 1, RAIL_TOP, T1 - 1, TEX_TRAY));
        if (!e) out.add(Box.of(T1 - 1, FLOOR_TOP, T0 + 1, T1, RAIL_TOP, T1 - 1, TEX_TRAY));
        if (n) {
            out.add(Box.of(T0, FLOOR_TOP, 0, T0 + 1, RAIL_TOP, T0, TEX_TRAY));
            out.add(Box.of(T1 - 1, FLOOR_TOP, 0, T1, RAIL_TOP, T0, TEX_TRAY));
        }
        if (s) {
            out.add(Box.of(T0, FLOOR_TOP, T1, T0 + 1, RAIL_TOP, 16, TEX_TRAY));
            out.add(Box.of(T1 - 1, FLOOR_TOP, T1, T1, RAIL_TOP, 16, TEX_TRAY));
        }
        if (w) {
            out.add(Box.of(0, FLOOR_TOP, T0, T0, RAIL_TOP, T0 + 1, TEX_TRAY));
            out.add(Box.of(0, FLOOR_TOP, T1 - 1, T0, RAIL_TOP, T1, TEX_TRAY));
        }
        if (e) {
            out.add(Box.of(T1, FLOOR_TOP, T0, 16, RAIL_TOP, T0 + 1, TEX_TRAY));
            out.add(Box.of(T1, FLOOR_TOP, T1 - 1, 16, RAIL_TOP, T1, TEX_TRAY));
        }

        // Risers: enclosed going up or down.
        if (up) riser(out, RAIL_TOP, 16);
        if (down) riser(out, 0, 3);

        // Hangers: a strut under the tray and two drop rods to the ceiling (not where an arm is in the way).
        if (ceiling && !up) {
            boolean alongZ = (n || s) && !(w || e);
            if (alongZ) {
                out.add(Box.of(1, 2, 7, 15, 3, 9, TEX_ROD));
                if (!w) out.add(Box.of(1.5f, 3, 7.5f, 2.5f, 16, 8.5f, TEX_ROD));
                if (!e) out.add(Box.of(13.5f, 3, 7.5f, 14.5f, 16, 8.5f, TEX_ROD));
            } else {
                out.add(Box.of(7, 2, 1, 9, 3, 15, TEX_ROD));
                if (!n) out.add(Box.of(7.5f, 3, 1.5f, 8.5f, 16, 2.5f, TEX_ROD));
                if (!s) out.add(Box.of(7.5f, 3, 13.5f, 8.5f, 16, 14.5f, TEX_ROD));
            }
        }
        return out;
    }

    private static void riser(List<Box> out, float y1, float y2) {
        out.add(Box.of(T0, y1, T0, T1, y2, T0 + 1, TEX_TRAY));
        out.add(Box.of(T0, y1, T1 - 1, T1, y2, T1, TEX_TRAY));
        out.add(Box.of(T0, y1, T0 + 1, T0 + 1, y2, T1 - 1, TEX_TRAY));
        out.add(Box.of(T1 - 1, y1, T0 + 1, T1, y2, T1 - 1, TEX_TRAY));
    }

    private static final float K0 = 3.5f, K1 = 12.5f;

    /** Closed trunking round the runs: a box in the middle plus one out to each side a run leaves by. */
    public static List<Box> trunking(int union) {
        List<Box> out = new ArrayList<>();
        out.add(Box.of(K0, K0, K0, K1, K1, K1, TEX_TRUNKING));
        for (Direction dir : Direction.values()) {
            if (!has(union, dir)) continue;
            out.add(switch (dir) {
                case DOWN -> new Box(K0, 0, K0, K1, K0, K1, dir, TEX_TRUNKING);
                case UP -> new Box(K0, K1, K0, K1, 16, K1, dir, TEX_TRUNKING);
                case NORTH -> new Box(K0, K0, 0, K1, K1, K0, dir, TEX_TRUNKING);
                case SOUTH -> new Box(K0, K0, K1, K1, K1, 16, dir, TEX_TRUNKING);
                case WEST -> new Box(0, K0, K0, K0, K1, K1, dir, TEX_TRUNKING);
                case EAST -> new Box(K1, K0, K0, 16, K1, K1, dir, TEX_TRUNKING);
            });
        }
        return out;
    }

    /**
     * The strand for one colour: a small core at its lane point plus an arm out to each side in
     * {@code mask}. {@code half} is the half-width (the strand is drawn thinner than its hitbox).
     * Arms carry their direction; the core has null.
     */
    public static List<Box> strand(DyeColor colour, int mask, float half) {
        float[] p = lane(colour);
        float x = p[0], y = p[1], z = p[2];
        List<Box> boxes = new ArrayList<>(7);
        boxes.add(new Box(x - half, y - half, z - half, x + half, y + half, z + half, null));
        for (Direction dir : Direction.values()) {
            if ((mask & (1 << dir.ordinal())) == 0) continue;
            boxes.add(switch (dir) {
                case DOWN -> new Box(x - half, 0, z - half, x + half, y - half, z + half, dir);
                case UP -> new Box(x - half, y + half, z - half, x + half, 16, z + half, dir);
                case NORTH -> new Box(x - half, y - half, 0, x + half, y + half, z - half, dir);
                case SOUTH -> new Box(x - half, y - half, z + half, x + half, y + half, 16, dir);
                case WEST -> new Box(0, y - half, z - half, x - half, y + half, z + half, dir);
                case EAST -> new Box(x + half, y - half, z - half, 16, y + half, z + half, dir);
            });
        }
        return boxes;
    }

    /** Hit/collision shape: every strand (fattened to 4 px so it's not a pain to click), plus any tray or trunking. */
    public static VoxelShape shape(long key) {
        if (colours(key) == 0) return FALLBACK;
        return SHAPES.computeIfAbsent(key, k -> {
            List<Box> boxes = new ArrayList<>();
            for (DyeColor colour : present(k)) boxes.addAll(strand(colour, mask(k, colour), 2));
            int cover = cover(k);
            if (cover == COVER_TRAY) {
                for (Box b : tray(unionMask(k), ceiling(k))) if (b.tex() != TEX_ROD) boxes.add(b);
            } else if (cover == COVER_TRUNKING) {
                boxes.addAll(trunking(unionMask(k)));
            }
            VoxelShape shape = Shapes.empty();
            for (Box b : boxes) {
                shape = Shapes.or(shape, Shapes.box(
                    clamp(b.x1()) / 16, clamp(b.y1()) / 16, clamp(b.z1()) / 16,
                    clamp(b.x2()) / 16, clamp(b.y2()) / 16, clamp(b.z2()) / 16));
            }
            return shape.optimize();
        });
    }

    private static double clamp(float v) {
        return Math.max(0, Math.min(16, v));
    }

    private CableLanes() {
    }
}
