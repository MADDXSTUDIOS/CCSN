package uk.maddx.ccsn.network;

import com.mojang.serialization.MapCodec;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A data faceplate for walls, floors and ceilings.
 * <ul>
 *   <li>Back: the network cable directly behind it, or behind a one-block wall. If the cable
 *   carries several runs, the jack uses one: the first, or the one picked by right-clicking
 *   the jack with a cable of that colour.</li>
 *   <li>Front and four sides: CC grey cable or a CC wired modem. A grey patch lead
 *   is drawn from the socket to each one.</li>
 * </ul>
 * It's the only place CC's own networking meets ours. Jacks never connect to each other.
 */
public final class WallJackBlock extends BaseEntityBlock {
    public static final MapCodec<WallJackBlock> CODEC = simpleCodec(WallJackBlock::new);
    /** The way the front of the jack faces. It's fitted to the block behind it. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<JackLink> LINK = EnumProperty.create("link", JackLink.class);
    /** Which sides have CC cable or a modem plugged in. The back is always false. */
    public static final Map<Direction, BooleanProperty> PLUGGED = PipeBlock.PROPERTY_BY_DIRECTION;

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
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LINK, JackLink.NONE);
        for (BooleanProperty side : PLUGGED.values()) state = state.setValue(side, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LINK);
        PLUGGED.values().forEach(builder::add);
    }

    // ---- The cable run behind ----

    /** Where the cable run feeding this jack is: directly behind, or behind a one-block wall. Null if none. */
    static @Nullable BlockPos runPos(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockState behindState = level.getBlockState(behind);
        if (behindState.getBlock() instanceof NetworkCableBlock) return behind;
        if (NetworkCableBlock.isWall(level, behind, behindState)) {
            BlockPos far = behind.relative(facing.getOpposite());
            if (level.getBlockState(far).getBlock() instanceof NetworkCableBlock) return far;
        }
        return null;
    }

    /**
     * Which run the jack uses: the one picked with a cable if it's still there behind the jack,
     * otherwise the first run behind it.
     */
    static JackLink linkFor(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos run = runPos(level, pos, facing);
        if (run == null || !(level.getBlockEntity(run) instanceof NetworkCableBlockEntity cable)) return JackLink.NONE;
        DyeColor wanted = level.getBlockEntity(pos) instanceof WallJackBlockEntity jack ? jack.wanted() : null;
        if (wanted != null && cable.has(wanted)) return JackLink.of(wanted);
        return JackLink.of(cable.firstColour());
    }

    /** Work out the jack's run again, and get the jack and the cable behind it to relink. Server only. */
    static void refreshLink(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WallJackBlock)) return;
        Direction facing = state.getValue(FACING);
        JackLink link = linkFor(level, pos, facing);
        if (link != state.getValue(LINK)) level.setBlock(pos, state.setValue(LINK, link), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof WallJackBlockEntity jack) jack.scheduleRefresh();
        scheduleRunRefresh(level, pos, facing);
    }

    /** The cable feeding us redraws its arm towards us (it may be behind a wall and miss the update). */
    private static void scheduleRunRefresh(Level level, BlockPos pos, Direction facing) {
        BlockPos run = runPos(level, pos, facing);
        if (run != null && level.getBlockEntity(run) instanceof NetworkCableBlockEntity cable) cable.scheduleRefresh();
    }

    // ---- CC side ----

    /** Can CC plug into this jack from side {@code dir}? Never the back, never another jack or our cable. */
    static boolean pluggedOn(Level level, BlockPos pos, Direction facing, Direction dir) {
        if (dir == facing.getOpposite()) return false;
        BlockPos other = pos.relative(dir);
        if (!level.isLoaded(other)) return false;
        BlockState otherState = level.getBlockState(other);
        if (otherState.getBlock() instanceof WallJackBlock || otherState.getBlock() instanceof NetworkCableBlock) {
            return false;
        }
        return level.getCapability(WiredElementCapability.get(), other, dir.getOpposite()) != null;
    }

    private static BlockState withPlugs(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        for (Direction dir : Direction.values()) {
            state = state.setValue(PLUGGED.get(dir), pluggedOn(level, pos, facing, dir));
        }
        return state;
    }

    // ---- Placement and updates ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(LINK, linkFor(level, pos, facing));
        if (!canSurvive(state, level, pos)) return null;
        return withPlugs(state, level, pos);
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
        Direction facing = state.getValue(FACING);
        if (dir == facing.getOpposite()) {
            if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
            JackLink link = linkFor(level, pos, facing);
            if (link != state.getValue(LINK) && level instanceof Level real && !real.isClientSide) {
                if (real.getBlockEntity(pos) instanceof WallJackBlockEntity jack) jack.scheduleRefresh();
                scheduleRunRefresh(real, pos, facing);
            }
            return state.setValue(LINK, link);
        }
        if (level instanceof Level real) {
            state = state.setValue(PLUGGED.get(dir), pluggedOn(real, pos, facing, dir));
        }
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        if (level.getBlockEntity(pos) instanceof WallJackBlockEntity jack) jack.scheduleRefresh();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level.isClientSide) return;
        // Let CC's cable or modem next to us notice there's something to plug into.
        level.invalidateCapabilities(pos);
        if (!oldState.is(this)) updateCableThroughWall(level, pos, state.getValue(FACING));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && !newState.is(this)) updateCableThroughWall(level, pos, state.getValue(FACING));
    }

    /** A cable behind a wall doesn't get a neighbour update when we come or go, so tell it. */
    private static void updateCableThroughWall(Level level, BlockPos pos, Direction facing) {
        BlockPos far = pos.relative(facing.getOpposite(), 2);
        if (level.getBlockEntity(far) instanceof NetworkCableBlockEntity cable) cable.scheduleRefresh();
    }

    // ---- Status check ----

    /**
     * Holding network cable: switch the jack to that colour's run. Holding anything else: let it be
     * placed (CC cable into the front, say). Empty hand: status check.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof NetworkCableItem cable) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof WallJackBlockEntity jack) {
                jack.chooseRun(cable.colour(), player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.isEmpty()
            ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WallJackBlockEntity jack) {
            jack.sendStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---- Shape, rotation ----

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
        BlockState rotated = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction dir : Direction.values()) {
            rotated = rotated.setValue(PLUGGED.get(rotation.rotate(dir)), state.getValue(PLUGGED.get(dir)));
        }
        return rotated;
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
