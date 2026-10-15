package uk.maddx.ccsn.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Cable Tray / Cable Trunking: right-click a network cable to fit it round the runs there. */
public final class CableCoverItem extends Item {
    private final int cover;

    public CableCoverItem(int cover, Properties properties) {
        super(properties);
        this.cover = cover;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (cable.cover() == cover && cable.disguise() == null) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("chat.ccsn.cable.already_covered"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            ItemStack old = cable.setCover(cover);
            if (player == null || !player.hasInfiniteMaterials()) {
                context.getItemInHand().shrink(1);
                if (player != null && !old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
            }
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 1.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
