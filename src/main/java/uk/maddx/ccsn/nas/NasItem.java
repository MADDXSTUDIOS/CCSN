package uk.maddx.ccsn.nas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import uk.maddx.ccsn.ModRegistry;

import java.util.List;

/** NAS item: shows its label in the tooltip. Drives are separate items now. */
public final class NasItem extends BlockItem {
    public NasItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String label = stack.get(ModRegistry.NAS_LABEL.get());
        if (label != null) {
            tooltip.add(Component.translatable("tooltip.ccsn.nas.label", label).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.ccsn.nas.bays").withStyle(ChatFormatting.DARK_GRAY));
    }
}
