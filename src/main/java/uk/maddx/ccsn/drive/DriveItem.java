package uk.maddx.ccsn.drive;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.CCSNConfig;
import uk.maddx.ccsn.ModRegistry;

import java.util.List;
import java.util.UUID;

/**
 * A NAS drive. Each drive gets its own storage the first time it goes into a
 * NAS (world/computercraft/ccsn_drives/<id>), and keeps it wherever it goes.
 */
public final class DriveItem extends Item {
    public static final int MAX_LABEL_LENGTH = 32;
    private final int tier;

    public DriveItem(int tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public long capacity() {
        return CCSNConfig.driveCapacity(tier);
    }

    public static @Nullable UUID getId(ItemStack stack) {
        return stack.get(ModRegistry.DRIVE_ID.get());
    }

    public static @Nullable String getLabel(ItemStack stack) {
        return stack.get(ModRegistry.DRIVE_LABEL.get());
    }

    public static void setLabel(ItemStack stack, @Nullable String label) {
        if (label != null) {
            label = label.trim();
            if (label.length() > MAX_LABEL_LENGTH) label = label.substring(0, MAX_LABEL_LENGTH);
        }
        if (label == null || label.isEmpty()) stack.remove(ModRegistry.DRIVE_LABEL.get());
        else stack.set(ModRegistry.DRIVE_LABEL.get(), label);
    }

    /** Human-readable size, e.g. "16 MiB". */
    public static String formatBytes(long bytes) {
        if (bytes >= 1L << 30 && bytes % (1L << 30) == 0) return (bytes >> 30) + " GiB";
        if (bytes >= 1L << 20) return (bytes % (1L << 20) == 0 ? String.valueOf(bytes >> 20)
            : String.format("%.1f", bytes / (double) (1L << 20))) + " MiB";
        return (bytes >> 10) + " KiB";
    }

    @Override
    public Component getName(ItemStack stack) {
        String label = getLabel(stack);
        return label == null ? super.getName(stack) : Component.literal(label);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.ccsn.drive.capacity", formatBytes(capacity()))
            .withStyle(ChatFormatting.GRAY));
        if (getLabel(stack) != null) {
            tooltip.add(Component.translatable("item.ccsn.drive_t" + tier).withStyle(ChatFormatting.DARK_GRAY));
        }
        UUID id = getId(stack);
        tooltip.add(id == null
            ? Component.translatable("tooltip.ccsn.drive.blank").withStyle(ChatFormatting.DARK_GRAY)
            : Component.translatable("tooltip.ccsn.drive.id", id.toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
