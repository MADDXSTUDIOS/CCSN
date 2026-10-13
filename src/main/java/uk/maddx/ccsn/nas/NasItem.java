package uk.maddx.ccsn.nas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import uk.maddx.ccsn.ModRegistry;

import java.util.List;
import java.util.UUID;

/** NAS item: shows its label and ID in the tooltip so you know whose data you're holding. */
public final class NasItem extends BlockItem {
    public NasItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        String label = stack.get(ModRegistry.NAS_LABEL.get());
        UUID id = stack.get(ModRegistry.NAS_ID.get());

        if (label != null) {
            tooltip.add(Component.translatable("tooltip.ccsn.nas.label", label).withStyle(ChatFormatting.GRAY));
        }
        if (id != null) {
            tooltip.add(Component.translatable("tooltip.ccsn.nas.id", id.toString().substring(0, 8))
                .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.ccsn.nas.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
