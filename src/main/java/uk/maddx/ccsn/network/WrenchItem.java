package uk.maddx.ccsn.network;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Network Wrench. On a network cable: sneak + right-click takes off its cover (tray, trunking or
 * disguise) and gives it back; right-click says which runs and cover it has. On a wall jack or a
 * switch: right-click shows its status. Held in either hand, it shows runs through walls (client).
 */
public final class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (level.getBlockEntity(pos) instanceof WallJackBlockEntity jack) {
            if (!level.isClientSide) jack.sendStatus(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity sw) {
            if (!level.isClientSide) sw.sendStatus(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (player.isSecondaryUseActive()) {
            ItemStack old = cable.removeCover();
            if (old.isEmpty()) {
                player.displayClientMessage(Component.translatable("chat.ccsn.wrench.no_cover"), true);
            } else {
                if (!player.hasInfiniteMaterials()) player.getInventory().placeItemBackInInventory(old);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
                player.displayClientMessage(Component.translatable("chat.ccsn.wrench.removed", old.getHoverName()), true);
            }
            return InteractionResult.SUCCESS;
        }

        // Status: which runs, which cover.
        MutableComponent runs = Component.empty();
        boolean first = true;
        for (DyeColor colour : cable.colours()) {
            if (!first) runs.append(", ");
            runs.append(Component.translatable("color.minecraft." + colour.getName()));
            first = false;
        }
        ItemStack cover = cable.coverItem();
        Component coverName = cover.isEmpty()
            ? Component.translatable("chat.ccsn.wrench.cover_none")
            : cover.getHoverName();
        player.displayClientMessage(Component.translatable("chat.ccsn.wrench.status", runs, coverName)
            .withStyle(ChatFormatting.GRAY), false);
        return InteractionResult.SUCCESS;
    }
}
