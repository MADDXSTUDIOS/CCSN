package uk.maddx.ccsn.network;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything needed to draw (and shape) one cable block. Worked out on the server, synced to the
 * client, and used as the cache key for the model and the hitbox.
 *
 * @param colours bit per dye ordinal: which runs are here
 * @param masks   6 side bits per run present, packed in dye-ordinal order (up to {@link CableLanes#ABSOLUTE_MAX_RUNS})
 * @param solid   sides where the neighbour has a solid face towards us (wall, floor, ceiling)
 * @param cables  sides where the neighbour is another cable block
 * @param cover   {@link CableLanes#COVER_NONE}, tray, trunking or disguise
 */
public record CableRender(int colours, long masks, int solid, int cables, int cover) {
    public static final CableRender EMPTY = new CableRender(0, 0, 0, 0, CableLanes.COVER_NONE);

    public static CableRender of(int colourBits, int[] masksByOrdinal, int solid, int cables, int cover) {
        long packed = 0;
        int slot = 0;
        for (int c = 0; c < 16 && slot < CableLanes.ABSOLUTE_MAX_RUNS; c++) {
            if ((colourBits & (1 << c)) == 0) continue;
            packed |= (long) (masksByOrdinal[c] & 0x3F) << (6 * slot);
            slot++;
        }
        return new CableRender(colourBits & 0xFFFF, packed, solid & 0x3F, cables & 0x3F, cover);
    }

    public boolean isEmpty() {
        return colours == 0;
    }

    /** The same render with the neighbour bits its cover doesn't use cleared, so caches don't fill with duplicates. */
    public CableRender normalised() {
        return switch (cover) {
            case CableLanes.COVER_TRUNKING -> this;
            case CableLanes.COVER_TRAY -> cables == 0 ? this : new CableRender(colours, masks, solid, 0, cover);
            default -> solid == 0 && cables == 0 ? this : new CableRender(colours, masks, 0, 0, cover);
        };
    }

    public List<DyeColor> present() {
        List<DyeColor> out = new ArrayList<>(CableLanes.ABSOLUTE_MAX_RUNS);
        for (DyeColor c : DyeColor.values()) {
            if ((colours & (1 << c.ordinal())) != 0 && out.size() < CableLanes.ABSOLUTE_MAX_RUNS) out.add(c);
        }
        return out;
    }

    /** The sides one run goes out on. */
    public int mask(DyeColor colour) {
        int slot = 0;
        for (int c = 0; c < 16 && slot < CableLanes.ABSOLUTE_MAX_RUNS; c++) {
            if ((colours & (1 << c)) == 0) continue;
            if (c == colour.ordinal()) return (int) ((masks >>> (6 * slot)) & 0x3F);
            slot++;
        }
        return 0;
    }

    /** Every side any run goes out on. */
    public int union() {
        int union = 0;
        for (DyeColor colour : present()) union |= mask(colour);
        return union;
    }

    public boolean solid(Direction dir) {
        return (solid & (1 << dir.ordinal())) != 0;
    }

    public boolean cable(Direction dir) {
        return (cables & (1 << dir.ordinal())) != 0;
    }
}
