package uk.maddx.ccsn.network;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One item per colour, all placing the same cable block in that colour. */
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
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = getBlock().getStateForPlacement(context);
        if (state == null) return null;
        // Connections depend on colour, so work them out again once the colour is set.
        state = NetworkCableBlock.withConnections(
            state.setValue(NetworkCableBlock.COLOR, colour), context.getLevel(), context.getClickedPos());
        return canPlace(context, state) ? state : null;
    }

    /** BlockItem would use the block's name, making all 16 "Network Cable". */
    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
