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

    /** A box in pixels: x1, y1, z1, x2, y2, z2. */
    public record Box(float x1, float y1, float z1, float x2, float y2, float z2, Direction arm) {
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

    /** Hit/collision shape: every strand, fattened to 4 px so it's not a pain to click. */
    public static VoxelShape shape(long key) {
        if (colours(key) == 0) return FALLBACK;
        return SHAPES.computeIfAbsent(key, k -> {
            VoxelShape shape = Shapes.empty();
            for (DyeColor colour : present(k)) {
                for (Box b : strand(colour, mask(k, colour), 2)) {
                    shape = Shapes.or(shape, Shapes.box(
                        clamp(b.x1()) / 16, clamp(b.y1()) / 16, clamp(b.z1()) / 16,
                        clamp(b.x2()) / 16, clamp(b.y2()) / 16, clamp(b.z2()) / 16));
                }
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
