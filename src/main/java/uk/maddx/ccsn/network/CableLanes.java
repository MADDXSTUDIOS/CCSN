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
 * The geometry of a cable block: where each colour's strand runs, and the tray or trunking round
 * them. Pure maths on a {@link CableRender}; the client model and the hitbox are both built from it.
 *
 * <p>Every colour has a fixed lane point from a 4x4 Latin square, so a run lines up with the next
 * block whatever else shares either block, and any two of a point's coordinates pick out exactly one
 * colour. Strands are 1 px, and arms along different axes sit in different layers: X arms at the lane
 * point (y, z), Z arms one pixel up (x, y+1), Y arms one pixel across (x+1, z+1). With lanes 2 px
 * apart that means no strand ever passes through another, straight or turning, for any set of colours.
 */
public final class CableLanes {
    /** Most runs one block can ever carry (what {@link CableRender} has room for). The config picks 1 to this. */
    public static final int ABSOLUTE_MAX_RUNS = 8;

    /** Lane centres. Half pixels, so 1 px strands sit on the pixel grid (4..12). */
    private static final float[] LANE = { 4.5f, 6.5f, 8.5f, 10.5f };
    /** Strand half-width: 1 px strands. */
    private static final float H = 0.5f;
    /** How far Z and Y arms sit off the lane point. */
    private static final float O = 1;

    public static final int COVER_NONE = 0, COVER_TRAY = 1, COVER_TRUNKING = 2, COVER_DISGUISE = 3;

    /** Which texture a box uses. */
    public static final int TEX_STRAND = 0, TEX_TRAY = 1, TEX_ROD = 2, TEX_TRUNKING = 3;

    public static final int ALL_FACES = 0x3F;

    private static final Map<CableRender, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final VoxelShape FALLBACK = Shapes.box(6 / 16.0, 6 / 16.0, 6 / 16.0, 10 / 16.0, 10 / 16.0, 10 / 16.0);

    /**
     * A box in pixels. {@code faces} says which of its 6 faces to draw (bit per direction ordinal);
     * {@code twoSided} draws each of those from the inside too (trunking, so an open end shows a hollow duct).
     */
    public record Box(float x1, float y1, float z1, float x2, float y2, float z2, int faces, int tex, boolean twoSided) {
        static Box of(float x1, float y1, float z1, float x2, float y2, float z2, int tex) {
            return new Box(x1, y1, z1, x2, y2, z2, ALL_FACES, tex, false);
        }

        public boolean draws(Direction face) {
            return (faces & bit(face)) != 0;
        }
    }

    static int bit(Direction dir) {
        return 1 << dir.ordinal();
    }

    private static boolean has(int mask, Direction dir) {
        return (mask & bit(dir)) != 0;
    }

    /** Centre of a colour's lane, in pixels. */
    public static float[] lane(DyeColor colour) {
        int k = colour.ordinal(), i = k / 4, j = k % 4;
        return new float[] { LANE[i], LANE[j], LANE[(i + j) % 4] };
    }

    // ---- Strands ----

    /**
     * One colour's strand: a core plus an arm out to each side in {@code mask}. {@code grow} fattens
     * every box (for the hitbox; 0 to draw). The core covers exactly the layers its arms use, so a
     * straight run is one even line and a turn steps up or across by a pixel inside the block.
     */
    public static List<Box> strand(DyeColor colour, int mask, float grow) {
        float[] p = lane(colour);
        float x = p[0], y = p[1], z = p[2], g = grow;
        boolean ax = has(mask, Direction.WEST) || has(mask, Direction.EAST);
        boolean ay = has(mask, Direction.DOWN) || has(mask, Direction.UP);
        boolean az = has(mask, Direction.NORTH) || has(mask, Direction.SOUTH);
        boolean none = mask == 0; // a lone stub: show the full 2 px core so it's not lost

        // Cross-sections: X arms (y, z), Z arms (x, y+O), Y arms (x+O, z+O).
        float[] cx = coreSpan(az || none, x - H, x + H, ay || none, x + O - H, x + O + H, x - H, x + H);
        float[] cy = coreSpan(ax || none, y - H, y + H, az || none, y + O - H, y + O + H, y - H, y + H);
        float[] cz = coreSpan(ax || none, z - H, z + H, ay || none, z + O - H, z + O + H, z - H, z + H);

        List<Box> boxes = new ArrayList<>(7);
        boxes.add(Box.of(cx[0] - g, cy[0] - g, cz[0] - g, cx[1] + g, cy[1] + g, cz[1] + g, TEX_STRAND));
        for (Direction dir : Direction.values()) {
            if (!has(mask, dir)) continue;
            // An arm's two ends are never seen: one is inside the core, the other meets the next block.
            int faces = ALL_FACES & ~bit(dir) & ~bit(dir.getOpposite());
            float xa = x - H - g, xb = x + H + g, ya = y - H - g, yb = y + H + g, za = z - H - g, zb = z + H + g;
            float zya = y + O - H - g, zyb = y + O + H + g; // Z arm's layer
            float yxa = x + O - H - g, yxb = x + O + H + g, yza = z + O - H - g, yzb = z + O + H + g; // Y arm's column
            boxes.add(switch (dir) {
                case WEST -> new Box(0, ya, za, cx[0], yb, zb, faces, TEX_STRAND, false);
                case EAST -> new Box(cx[1], ya, za, 16, yb, zb, faces, TEX_STRAND, false);
                case DOWN -> new Box(yxa, 0, yza, yxb, cy[0], yzb, faces, TEX_STRAND, false);
                case UP -> new Box(yxa, cy[1], yza, yxb, 16, yzb, faces, TEX_STRAND, false);
                case NORTH -> new Box(xa, zya, 0, xb, zyb, cz[0], faces, TEX_STRAND, false);
                case SOUTH -> new Box(xa, zya, cz[1], xb, zyb, 16, faces, TEX_STRAND, false);
            });
        }
        return boxes;
    }

    /** The span a core needs along one axis: covering range a and/or b, or the default if neither applies. */
    private static float[] coreSpan(boolean useA, float a1, float a2, boolean useB, float b1, float b2, float d1, float d2) {
        if (useA && useB) return new float[] { Math.min(a1, b1), Math.max(a2, b2) };
        if (useA) return new float[] { a1, a2 };
        if (useB) return new float[] { b1, b2 };
        return new float[] { d1, d2 };
    }

    // ---- Cable tray ----

    /** Tray footprint (outer) and its parts' heights. Rails and walls are 1 px steel. */
    private static final float T0 = 3, T1 = 13;
    private static final float RUNG_BOTTOM = 3, RUNG_TOP = 4, STRINGER_TOP = 4.5f, LIP_BOTTOM = 11.5f, RAIL_TOP = 12.5f;
    /** Rungs, rail posts and riser slats: 2 px every 4 px, the same in every block so they line up. */
    private static final float[] BEAT = { 1, 5, 9, 13 };
    private static final Direction[] HORIZONTAL = { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    /**
     * An open ladder tray round the runs, following them round bends and junctions, inside the block:
     * rungs across the way it runs, slotted side rails, and a slatted riser where runs go up or down
     * (open on the sides runs leave by). Held up by feet on a floor, brackets or clips to a wall, or
     * drop rods from a ceiling.
     */
    public static List<Box> tray(int union, int solid) {
        boolean n = has(union, Direction.NORTH), s = has(union, Direction.SOUTH);
        boolean w = has(union, Direction.WEST), e = has(union, Direction.EAST);
        boolean up = has(union, Direction.UP), down = has(union, Direction.DOWN);
        boolean riser = up || down;
        List<Box> out = new ArrayList<>();

        for (Direction dir : HORIZONTAL) if (has(union, dir)) trayArm(out, dir);
        if (riser) riserCore(out, n, s, w, e, up, down);
        else trayCore(out, n, s, w, e);
        supports(out, union, solid, riser, up, down);
        return out;
    }

    /** A stretch of tray from the middle out to one side: a rail each side and rungs across. */
    private static void trayArm(List<Box> out, Direction dir) {
        boolean alongX = dir == Direction.WEST || dir == Direction.EAST;
        boolean positive = dir == Direction.SOUTH || dir == Direction.EAST;
        float a1 = positive ? T1 : 0, a2 = positive ? 16 : T0;
        rail(out, alongX, a1, a2, T0, T0 + 1);
        rail(out, alongX, a1, a2, T1 - 1, T1);
        rungs(out, alongX, a1, a2, T0 + 1, T1 - 1);
    }

    /** The middle of a flat tray: rails round the sides nothing leaves by, rungs across the main direction. */
    private static void trayCore(List<Box> out, boolean n, boolean s, boolean w, boolean e) {
        // North/south rails own the corners; west/east rails stretch to meet an arm's rail where there is one.
        if (!n) rail(out, true, T0, T1, T0, T0 + 1);
        if (!s) rail(out, true, T0, T1, T1 - 1, T1);
        float z1 = n ? T0 : T0 + 1, z2 = s ? T1 : T1 - 1;
        if (!w) rail(out, false, z1, z2, T0, T0 + 1);
        if (!e) rail(out, false, z1, z2, T1 - 1, T1);
        // Inside corner between two arms: a post, so the rails meet.
        if (n && w) post(out, T0, T0);
        if (n && e) post(out, T1 - 1, T0);
        if (s && w) post(out, T0, T1 - 1);
        if (s && e) post(out, T1 - 1, T1 - 1);

        int alongXCount = (w ? 1 : 0) + (e ? 1 : 0), alongZCount = (n ? 1 : 0) + (s ? 1 : 0);
        if (alongXCount > alongZCount) rungs(out, true, T0, T1, z1, z2);
        else rungs(out, false, T0, T1, w ? T0 : T0 + 1, e ? T1 : T1 - 1);
    }

    /** The middle of a riser: corner posts and slatted walls, open where runs leave sideways. */
    private static void riserCore(List<Box> out, boolean n, boolean s, boolean w, boolean e, boolean up, boolean down) {
        float yb = down ? 0 : RUNG_BOTTOM, yt = up ? 16 : RAIL_TOP;
        for (float px : new float[] { T0, T1 - 1 }) {
            for (float pz : new float[] { T0, T1 - 1 }) out.add(Box.of(px, yb, pz, px + 1, yt, pz + 1, TEX_TRAY));
        }
        riserWall(out, true, T0, n, yb, yt);
        riserWall(out, true, T1 - 1, s, yb, yt);
        riserWall(out, false, T0, w, yb, yt);
        riserWall(out, false, T1 - 1, e, yb, yt);
        // A floor if it doesn't carry on down.
        if (!down) {
            boolean alongX = (w || e) && !(n || s);
            rungs(out, alongX, T0, T1, T0 + 1, T1 - 1);
        }
    }

    /** One wall of a riser between its corner posts: slats every 4 px, open where a run leaves this side. */
    private static void riserWall(List<Box> out, boolean alongX, float c, boolean open, float yb, float yt) {
        float a1 = T0 + 1, a2 = T1 - 1;
        if (yb > 0) out.add(span(alongX, a1, a2, RUNG_BOTTOM, RUNG_TOP, c, c + 1));
        if (yt < 16 && !open) out.add(span(alongX, a1, a2, LIP_BOTTOM, RAIL_TOP, c, c + 1));
        float lo = yb > 0 ? RUNG_TOP : 0, hi = yt < 16 ? LIP_BOTTOM : 16;
        for (float b : BEAT) {
            if (b < lo || b + 2 > hi) continue;
            if (open && b + 2 > RUNG_BOTTOM && b < 13) continue; // the gap the runs go through
            out.add(span(alongX, a1, a2, b, b + 2, c, c + 1));
        }
    }

    /** A slotted side rail along X or Z from a1 to a2, at cross-position c1..c2: stringer, lip, and posts between. */
    private static void rail(List<Box> out, boolean alongX, float a1, float a2, float c1, float c2) {
        out.add(span(alongX, a1, a2, RUNG_BOTTOM, STRINGER_TOP, c1, c2));
        out.add(span(alongX, a1, a2, LIP_BOTTOM, RAIL_TOP, c1, c2));
        for (float b : BEAT) {
            if (b >= a1 && b + 2 <= a2) out.add(span(alongX, b, b + 2, STRINGER_TOP, LIP_BOTTOM, c1, c2));
        }
    }

    /** Rungs across a stretch of tray running along X or Z (from a1 to a2), each spanning c1..c2. */
    private static void rungs(List<Box> out, boolean alongX, float a1, float a2, float c1, float c2) {
        for (float b : BEAT) {
            if (b >= a1 && b + 2 <= a2) out.add(span(alongX, b, b + 2, RUNG_BOTTOM, RUNG_TOP, c1, c2));
        }
    }

    private static void post(List<Box> out, float x, float z) {
        out.add(Box.of(x, RUNG_BOTTOM, z, x + 1, RAIL_TOP, z + 1, TEX_TRAY));
    }

    /** A tray box running along X (a = x, c = z) or along Z (a = z, c = x). */
    private static Box span(boolean alongX, float a1, float a2, float y1, float y2, float c1, float c2) {
        return alongX ? Box.of(a1, y1, c1, a2, y2, c2, TEX_TRAY) : Box.of(c1, y1, a1, c2, y2, a2, TEX_TRAY);
    }

    /**
     * Whatever holds the tray up: on a floor, a strut on two feet (a riser stands on its corner posts);
     * brackets (tray) or clips (riser) to walls; a strut on drop rods from a ceiling.
     */
    private static void supports(List<Box> out, int union, int solid, boolean riser, boolean up, boolean down) {
        boolean n = has(union, Direction.NORTH), s = has(union, Direction.SOUTH);
        boolean w = has(union, Direction.WEST), e = has(union, Direction.EAST);
        // Struts go across the tray: along X under a tray running north-south, along Z otherwise.
        boolean strutAlongX = (n || s) && !(w || e);

        boolean feet = has(solid, Direction.DOWN) && !down;
        if (feet && riser) {
            for (float px : new float[] { T0, T1 - 1 }) {
                for (float pz : new float[] { T0, T1 - 1 }) {
                    out.add(Box.of(px, 1, pz, px + 1, RUNG_BOTTOM, pz + 1, TEX_ROD));
                    out.add(Box.of(px - 1, 0, pz - 1, px + 2, 1, pz + 2, TEX_ROD));
                }
            }
        } else if (feet) {
            if (strutAlongX) {
                out.add(Box.of(1, 2, 7, 15, 3, 9, TEX_ROD));
                out.add(Box.of(1, 0, 6.5f, 3, 2, 9.5f, TEX_ROD));
                out.add(Box.of(13, 0, 6.5f, 15, 2, 9.5f, TEX_ROD));
            } else {
                out.add(Box.of(7, 2, 1, 9, 3, 15, TEX_ROD));
                out.add(Box.of(6.5f, 0, 1, 9.5f, 2, 3, TEX_ROD));
                out.add(Box.of(6.5f, 0, 13, 9.5f, 2, 15, TEX_ROD));
            }
        }

        boolean onWall = false;
        for (Direction dir : HORIZONTAL) {
            if (!has(solid, dir) || has(union, dir)) continue;
            onWall = true;
            if (riser) {
                clip(out, dir, 5);
                if (up) clip(out, dir, 13);
            } else if (!feet) {
                bracket(out, dir);
            }
        }

        if (has(solid, Direction.UP) && !riser && !feet && !onWall) {
            if (strutAlongX) {
                out.add(Box.of(1, 2, 7, 15, 3, 9, TEX_ROD));
                out.add(Box.of(1.5f, 3, 7.5f, 2.5f, 16, 8.5f, TEX_ROD));
                out.add(Box.of(13.5f, 3, 7.5f, 14.5f, 16, 8.5f, TEX_ROD));
            } else {
                out.add(Box.of(7, 2, 1, 9, 3, 15, TEX_ROD));
                if (!n) out.add(Box.of(7.5f, 3, 1.5f, 8.5f, 16, 2.5f, TEX_ROD));
                if (!s) out.add(Box.of(7.5f, 3, 13.5f, 8.5f, 16, 14.5f, TEX_ROD));
            }
        }
    }

    /** A cantilever bracket from the wall on {@code dir}: a bar under the tray and a plate on the wall. */
    private static void bracket(List<Box> out, Direction dir) {
        switch (dir) {
            case EAST -> {
                out.add(Box.of(T0, 2, 7, 16, 3, 9, TEX_ROD));
                out.add(Box.of(15, 0, 6, 16, 7, 10, TEX_ROD));
                out.add(Box.of(14, 0, 7, 15, 2, 9, TEX_ROD));
            }
            case WEST -> {
                out.add(Box.of(0, 2, 7, T1, 3, 9, TEX_ROD));
                out.add(Box.of(0, 0, 6, 1, 7, 10, TEX_ROD));
                out.add(Box.of(1, 0, 7, 2, 2, 9, TEX_ROD));
            }
            case SOUTH -> {
                out.add(Box.of(7, 2, T0, 9, 3, 16, TEX_ROD));
                out.add(Box.of(6, 0, 15, 10, 7, 16, TEX_ROD));
                out.add(Box.of(7, 0, 14, 9, 2, 15, TEX_ROD));
            }
            case NORTH -> {
                out.add(Box.of(7, 2, 0, 9, 3, T1, TEX_ROD));
                out.add(Box.of(6, 0, 0, 10, 7, 1, TEX_ROD));
                out.add(Box.of(7, 0, 1, 9, 2, 2, TEX_ROD));
            }
            default -> {
            }
        }
    }

    /** A clip from a riser wall to the wall on {@code dir}, at height y..y+2, with a plate on the wall. */
    private static void clip(List<Box> out, Direction dir, float y) {
        switch (dir) {
            case EAST -> {
                out.add(Box.of(T1, y, 7, 16, y + 2, 9, TEX_ROD));
                out.add(Box.of(15, y - 1, 6, 16, y + 3, 10, TEX_ROD));
            }
            case WEST -> {
                out.add(Box.of(0, y, 7, T0, y + 2, 9, TEX_ROD));
                out.add(Box.of(0, y - 1, 6, 1, y + 3, 10, TEX_ROD));
            }
            case SOUTH -> {
                out.add(Box.of(7, y, T1, 9, y + 2, 16, TEX_ROD));
                out.add(Box.of(6, y - 1, 15, 10, y + 3, 16, TEX_ROD));
            }
            case NORTH -> {
                out.add(Box.of(7, y, 0, 9, y + 2, T0, TEX_ROD));
                out.add(Box.of(6, y - 1, 0, 10, y + 3, 1, TEX_ROD));
            }
            default -> {
            }
        }
    }

    /** The tray's outline for the hitbox: no gaps to click through, no supports in the way. */
    private static List<Box> trayHull(int union) {
        boolean up = has(union, Direction.UP), down = has(union, Direction.DOWN);
        List<Box> out = new ArrayList<>();
        out.add(Box.of(T0, down ? 0 : RUNG_BOTTOM, T0, T1, up ? 16 : RAIL_TOP, T1, TEX_TRAY));
        if (has(union, Direction.NORTH)) out.add(Box.of(T0, RUNG_BOTTOM, 0, T1, RAIL_TOP, T0, TEX_TRAY));
        if (has(union, Direction.SOUTH)) out.add(Box.of(T0, RUNG_BOTTOM, T1, T1, RAIL_TOP, 16, TEX_TRAY));
        if (has(union, Direction.WEST)) out.add(Box.of(0, RUNG_BOTTOM, T0, T0, RAIL_TOP, T1, TEX_TRAY));
        if (has(union, Direction.EAST)) out.add(Box.of(T1, RUNG_BOTTOM, T0, 16, RAIL_TOP, T1, TEX_TRAY));
        return out;
    }

    // ---- Trunking ----

    private static final float K0 = 3.5f, K1 = 12.5f;

    /**
     * Closed trunking round the runs: a duct out to each side a run leaves by. It's widened out to the
     * sides in {@code widen} (see {@link #widen}) so it sits flush against walls, floors and ceilings.
     * A face on the edge of the block is only drawn where it's open to the air: against a wall it's
     * hidden, and where it joins more cable ({@code open}) it's left open, drawn hollow so you see the
     * runs going in.
     */
    public static List<Box> trunking(int union, int solid, int open, int widen) {
        float x1 = has(widen, Direction.WEST) ? 0 : K0, x2 = has(widen, Direction.EAST) ? 16 : K1;
        float y1 = has(widen, Direction.DOWN) ? 0 : K0, y2 = has(widen, Direction.UP) ? 16 : K1;
        float z1 = has(widen, Direction.NORTH) ? 0 : K0, z2 = has(widen, Direction.SOUTH) ? 16 : K1;
        int hidden = solid | open; // block edges not to draw
        List<Box> out = new ArrayList<>();

        int arms = 0;
        for (Direction dir : Direction.values()) {
            if (!has(union, dir)) continue;
            Box arm = switch (dir) {
                case DOWN -> y1 > 0 ? duct(x1, 0, z1, x2, y1, z2, hidden) : null;
                case UP -> y2 < 16 ? duct(x1, y2, z1, x2, 16, z2, hidden) : null;
                case NORTH -> z1 > 0 ? duct(x1, y1, 0, x2, y2, z1, hidden) : null;
                case SOUTH -> z2 < 16 ? duct(x1, y1, z2, x2, y2, 16, hidden) : null;
                case WEST -> x1 > 0 ? duct(0, y1, z1, x1, y2, z2, hidden) : null;
                case EAST -> x2 < 16 ? duct(x2, y1, z1, 16, y2, z2, hidden) : null;
            };
            if (arm == null) continue;
            arms |= bit(dir);
            out.add(new Box(arm.x1(), arm.y1(), arm.z1(), arm.x2(), arm.y2(), arm.z2(),
                arm.faces() & ~bit(dir.getOpposite()), TEX_TRUNKING, true));
        }
        Box centre = duct(x1, y1, z1, x2, y2, z2, hidden);
        out.add(new Box(x1, y1, z1, x2, y2, z2, centre.faces() & ~arms, TEX_TRUNKING, true));
        return out;
    }

    /**
     * Which sides trunking widens out to: every solid side, so it sits flush; plus, where it turns
     * round the outside corner of a wall, the corner the two widened neighbours meet in, so the bend
     * is filled in rather than leaving a hole. {@code diagonal} has bit {@code a * 6 + d} set when the
     * block one step along arm {@code a} then one along {@code d} is solid on its {@code d.opposite} face
     * (only perpendicular pairs matter).
     */
    public static int widen(int union, int solid, long diagonal) {
        int widen = solid;
        for (Direction d : Direction.values()) {
            if (!has(union, d)) continue;
            for (Direction a : Direction.values()) {
                if (a == d || a == d.getOpposite() || !has(union, a)) continue;
                if (((diagonal >>> (a.ordinal() * 6 + d.ordinal())) & 1) != 0) {
                    widen |= bit(d);
                    break;
                }
            }
        }
        return widen;
    }

    /** Whether two trunking blocks joined on {@code joint} have the same shape where they meet. */
    public static boolean profilesMatch(Direction joint, int widenA, int widenB) {
        int across = ALL_FACES & ~bit(joint) & ~bit(joint.getOpposite());
        return (widenA & across) == (widenB & across);
    }

    /** A trunking box, drawing every face except those on a block edge listed in {@code hidden}. */
    private static Box duct(float x1, float y1, float z1, float x2, float y2, float z2, int hidden) {
        int faces = ALL_FACES;
        if (x1 == 0 && has(hidden, Direction.WEST)) faces &= ~bit(Direction.WEST);
        if (x2 == 16 && has(hidden, Direction.EAST)) faces &= ~bit(Direction.EAST);
        if (y1 == 0 && has(hidden, Direction.DOWN)) faces &= ~bit(Direction.DOWN);
        if (y2 == 16 && has(hidden, Direction.UP)) faces &= ~bit(Direction.UP);
        if (z1 == 0 && has(hidden, Direction.NORTH)) faces &= ~bit(Direction.NORTH);
        if (z2 == 16 && has(hidden, Direction.SOUTH)) faces &= ~bit(Direction.SOUTH);
        return new Box(x1, y1, z1, x2, y2, z2, faces, TEX_TRUNKING, true);
    }

    // ---- Everything together ----

    /** The cover's boxes for a cable block (none for bare or disguised cable). */
    public static List<Box> cover(CableRender r) {
        return switch (r.cover()) {
            case COVER_TRAY -> tray(r.union(), r.solid());
            case COVER_TRUNKING -> trunking(r.union(), r.solid(), r.open(), r.widen());
            default -> List.of();
        };
    }

    /** Hit/collision shape: every strand (fattened to 4 px so it's not a pain to click), plus the tray or trunking. */
    public static VoxelShape shape(CableRender render) {
        if (render.isEmpty()) return FALLBACK;
        if (SHAPES.size() > 4096) SHAPES.clear();
        return SHAPES.computeIfAbsent(render.normalised(), r -> {
            List<Box> boxes = new ArrayList<>();
            for (DyeColor colour : r.present()) boxes.addAll(strand(colour, r.mask(colour), 1.5f));
            if (r.cover() == COVER_TRAY) boxes.addAll(trayHull(r.union()));
            else if (r.cover() == COVER_TRUNKING) boxes.addAll(trunking(r.union(), r.solid(), r.open(), r.widen()));
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
