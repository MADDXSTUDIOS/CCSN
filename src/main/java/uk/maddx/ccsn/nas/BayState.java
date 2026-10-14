package uk.maddx.ccsn.nas;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import uk.maddx.ccsn.drive.DriveItem;

/** What's in a NAS bay, as a block state, so the model can show the right drive. */
public enum BayState implements StringRepresentable {
    EMPTY("empty"),
    T1("t1"),
    T2("t2"),
    T3("t3");

    private final String name;

    BayState(String name) {
        this.name = name;
    }

    public static BayState of(ItemStack stack) {
        if (!(stack.getItem() instanceof DriveItem drive)) return EMPTY;
        return switch (drive.tier()) {
            case 2 -> T2;
            case 3 -> T3;
            default -> T1;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
