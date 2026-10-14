package uk.maddx.ccsn.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A data faceplate for walls, floors and ceilings. The back plugs into the
 * network cable directly behind it; the front takes CC's grey cable or a CC
 * wired modem. It's the only place CC's own networking meets ours.
 */
public final class WallJackBlock extends BaseEntityBlock {
    public static final MapCodec<WallJackBlock> CODEC = simpleCodec(WallJackBlock::new);
    /** The way the front of the jack faces. It's fitted to the block behind it. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<JackLink> LINK = EnumProperty.create("link", JackLink.class);

    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        SHAPES[Direction.NORTH.ordinal()] = Block.box(4, 3, 14, 12, 13, 16);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(4, 3, 0, 12, 13, 2);
        SHAPES[Direction.WEST.ordinal()] = Block.box(14, 3, 4, 16, 13, 12);
        SHAPES[Direction.EAST.ordinal()] = Block.box(0, 3, 4, 2, 13, 12);
        SHAPES[Direction.UP.ordinal()] = Block.box(4, 0, 3, 12, 2, 13);
        SHAPES[Direction.DOWN.ordinal()] = Block.box(4, 14, 3, 12, 16, 13);
    }

    public WallJackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LINK, JackLink.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LINK);
    }

    private static JackLink linkBehind(BlockState behind) {
        return behind.getBlock() instanceof NetworkCableBlock
            ? JackLink.of(behind.getValue(NetworkCableBlock.COLOR))
            : JackLink.NONE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        BlockPos behind = context.getClickedPos().relative(facing.getOpposite());
        BlockState state = defaultBlockState()
            .setValue(FACING, facing)
            .setValue(LINK, linkBehind(context.getLevel().getBlockState(behind)));
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Fits onto network cable (even though it's thin) or any solid face. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behindPos = pos.relative(facing.getOpposite());
        BlockState behind = level.getBlockState(behindPos);
        return behind.getBlock() instanceof NetworkCableBlock || behind.isFaceSturdy(level, behindPos, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (dir != state.getValue(FACING).getOpposite()) return state;
        if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return state.setValue(LINK, linkBehind(neighbour));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        if (level.getBlockEntity(pos) instanceof WallJackBlockEntity jack) jack.scheduleRefresh();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        // Let CC's cable or modem in front notice there's something to plug into.
        if (!level.isClientSide) level.invalidateCapabilities(pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallJackBlockEntity(pos, state);
    }
}
