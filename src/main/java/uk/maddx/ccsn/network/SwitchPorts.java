package uk.maddx.ccsn.network;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * How runs arriving at a switch map onto its ports. This is the one place that decides what a
 * port is, so it can change later (ports per side, fewer ports, numbered jacks...) without
 * touching the linking, the lights or the status.
 *
 * <p>Right now: one port per colour, 16 in all. Any run of that colour arriving on any side
 * (except the front) plugs into it. Ports are numbered 1-16 in dye order, white to black, and
 * laid out on the front 4 x 4, row by row.
 */
public final class SwitchPorts {
    public static final int COUNT = 16;

    /** The port (0-based) a run of {@code colour} arriving on {@code face} uses. */
    public static int port(Direction face, DyeColor colour) {
        return colour.ordinal();
    }

    /** The colour a port is for (for labels). */
    public static DyeColor colour(int port) {
        return DyeColor.byId(port);
    }

    /** "3 Magenta": the port number as printed on the front, and its colour. */
    public static Component name(int port) {
        return Component.translatable("chat.ccsn.switch.port", port + 1,
            Component.translatable("color.minecraft." + colour(port).getName()));
    }

    /** Where a port's link light is on the front texture: column and row, as you look at it. */
    public static int ledColumn(int port) {
        return 1 + 4 * (port % 4);
    }

    public static int ledRow(int port) {
        return 1 + 4 * (port / 4);
    }

    private SwitchPorts() {
    }
}
