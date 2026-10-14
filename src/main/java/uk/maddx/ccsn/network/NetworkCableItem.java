package uk.maddx.ccsn.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One item per colour, all placing the same cable block. Right-click an existing cable with a
 * colour it doesn't carry yet to add that run to the same block (up to {@link CableLanes#MAX_RUNS}).
 */
public final class NetworkCableItem extends BlockItem {
    private final DyeColor colour;

    public NetworkCableItem(Block block, DyeColor colour, Properties properties) {
        super(block, properties);
        this.colour = colour;
    }

    public DyeColor colour() {
        return colour;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable && !cable.has(colour)) {
            if (cable.colourCount() >= CableLanes.MAX_RUNS) {
                if (player != null && !level.isClientSide) {
                    player.displayClientMessage(Component.translatable("chat.ccsn.cable.full", CableLanes.MAX_RUNS), true);
                }
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide && cable.addColour(colour)) {
                if (player == null || !player.hasInfiniteMaterials()) context.getItemInHand().shrink(1);
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Same colour already there (or not a cable): place a new block as normal, extending the run.
        return super.useOn(context);
    }

    /** A freshly placed cable block starts with this item's run. */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack,
                                                 BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable) cable.addColour(colour);
        return changed;
    }

    /** BlockItem would use the block's name, making all 16 "Network Cable". */
    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
