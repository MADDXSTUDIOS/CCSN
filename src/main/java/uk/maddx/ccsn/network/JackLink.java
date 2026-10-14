package uk.maddx.ccsn.network;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/** Which cable run a wall jack is plugged into: none, or the colour of the cable behind it. */
public enum JackLink implements StringRepresentable {
    NONE(null),
    WHITE(DyeColor.WHITE),
    ORANGE(DyeColor.ORANGE),
    MAGENTA(DyeColor.MAGENTA),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW),
    LIME(DyeColor.LIME),
    PINK(DyeColor.PINK),
    GRAY(DyeColor.GRAY),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY),
    CYAN(DyeColor.CYAN),
    PURPLE(DyeColor.PURPLE),
    BLUE(DyeColor.BLUE),
    BROWN(DyeColor.BROWN),
    GREEN(DyeColor.GREEN),
    RED(DyeColor.RED),
    BLACK(DyeColor.BLACK);

    private final @Nullable DyeColor colour;

    JackLink(@Nullable DyeColor colour) {
        this.colour = colour;
    }

    public @Nullable DyeColor colour() {
        return colour;
    }

    public static JackLink of(@Nullable DyeColor colour) {
        if (colour == null) return NONE;
        for (JackLink link : values()) {
            if (link.colour == colour) return link;
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return colour == null ? "none" : colour.getSerializedName();
    }
}
