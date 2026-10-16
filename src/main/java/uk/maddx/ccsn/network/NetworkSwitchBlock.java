package uk.maddx.ccsn.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

/**
 * Network Switch: the hub. Every run of network cable touching it (on any side but the front)
 * plugs into the port for its colour (see {@link SwitchPorts}), and the switch joins them all into
 * one network. Switches touching each other join too, so you can chain them. The front is the port
 * panel: a light per port, lit when a run is plugged in, flickering when messages cross the network.
 *
 * <p>CC's own cable and modems can't plug in: wall jacks are still the only way CC kit gets onto
 * our cabling.
 */
public final class NetworkSwitchBlock extends BaseEntityBlock {
    public static final MapCodec<NetworkSwitchBlock> CODEC = simpleCodec(NetworkSwitchBlock::new);
    /** The way the port panel faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NetworkSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The panel faces whoever places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ---- Connections ----

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level.isClientSide) return;
        // Turned round (or newly placed): we and everything round us look again.
        if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity sw) sw.scheduleRefresh();
        refreshNeighbours(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && !newState.is(this)) refreshNeighbours(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity sw) sw.scheduleRefresh();
    }

    /** Cable and switches next to us relink (cable redraws its arm towards us, or drops it). */
    private static void refreshNeighbours(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof NetworkCableBlockEntity cable) cable.scheduleRefresh();
            else if (be instanceof NetworkSwitchBlockEntity sw) sw.scheduleRefresh();
        }
    }

    // ---- Status check ----

    /** Empty hand: status. Holding anything: use or place it as normal (cable against the switch...). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty()
            ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity sw) {
            sw.sendStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkSwitchBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, ModRegistry.NETWORK_SWITCH_BLOCK_ENTITY.get(), NetworkSwitchBlockEntity::serverTick);
    }

    // ---- Shape, rotation ----

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
