package uk.maddx.ccsn.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

import java.util.Map;

/**
 * One run of coloured network cable. A run only joins the same colour next to
 * it, and wall jacks: one fitted straight onto it, or one on the far side of a
 * one-block wall. CC's grey cable and modems can't see it at all; the wall jack
 * is the only way in.
 */
public final class NetworkCableBlock extends BaseEntityBlock {
    public static final MapCodec<NetworkCableBlock> CODEC = simpleCodec(NetworkCableBlock::new);
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final Map<Direction, BooleanProperty> CONNECT = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape[] SHAPES = makeShapes();

    public NetworkCableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(COLOR, DyeColor.WHITE);
        for (BooleanProperty side : CONNECT.values()) state = state.setValue(side, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
        CONNECT.values().forEach(builder::add);
    }

    // ---- Connections ----

    /**
     * Does the cable at {@code pos} reach out on side {@code dir}? It does for the same colour, a wall jack
     * fitted straight onto it, or a wall jack on the far side of a one-block wall.
     */
    static boolean connects(BlockGetter level, BlockPos pos, BlockState self, Direction dir) {
        BlockPos otherPos = pos.relative(dir);
        BlockState other = level.getBlockState(otherPos);
        if (other.getBlock() instanceof NetworkCableBlock) return other.getValue(COLOR) == self.getValue(COLOR);
        // A wall jack fitted to this cable faces away from it.
        if (other.getBlock() instanceof WallJackBlock) return other.getValue(WallJackBlock.FACING) == dir;
        return jackThroughWall(level, otherPos, other, dir);
    }

    /** Is there a wall jack on the far side of the wall at {@code wallPos}, facing on in direction {@code dir}? */
    static boolean jackThroughWall(BlockGetter level, BlockPos wallPos, BlockState wall, Direction dir) {
        if (!isWall(level, wallPos, wall)) return false;
        BlockState jack = level.getBlockState(wallPos.relative(dir));
        return jack.getBlock() instanceof WallJackBlock && jack.getValue(WallJackBlock.FACING) == dir;
    }

    /** A full solid block a jack can reach through: bricks, planks, logs, concrete... */
    static boolean isWall(BlockGetter level, BlockPos pos, BlockState state) {
        return !(state.getBlock() instanceof NetworkCableBlock)
            && !(state.getBlock() instanceof WallJackBlock)
            && state.isCollisionShapeFullBlock(level, pos);
    }

    public static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECT.get(dir), connects(level, pos, state, dir));
        }
        return state;
    }

    /** Wall jacks on the far side of a wall don't get a shape update when we change, so tell them. */
    static void updateJacksThroughWalls(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos wallPos = pos.relative(dir);
            if (!jackThroughWall(level, wallPos, level.getBlockState(wallPos), dir)) continue;
            BlockPos jackPos = wallPos.relative(dir);
            BlockState jack = level.getBlockState(jackPos);
            BlockState updated = jack.setValue(WallJackBlock.LINK, WallJackBlock.linkFor(level, jackPos, dir));
            if (updated != jack) level.setBlock(jackPos, updated, Block.UPDATE_ALL);
            if (level.getBlockEntity(jackPos) instanceof WallJackBlockEntity be) be.scheduleRefresh();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) updateJacksThroughWalls(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && !newState.is(this)) updateJacksThroughWalls(level, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(CONNECT.get(dir), connects(level, pos, state, dir));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        if (level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable) cable.scheduleRefresh();
    }

    // ---- Shape ----

    private static VoxelShape[] makeShapes() {
        // Slightly fatter than the 3 px strand so it's not a pain to click.
        double a = 6, b = 10;
        VoxelShape core = Block.box(a, a, a, b, b, b);
        VoxelShape[] arms = new VoxelShape[6];
        arms[Direction.DOWN.ordinal()] = Block.box(a, 0, a, b, a, b);
        arms[Direction.UP.ordinal()] = Block.box(a, b, a, b, 16, b);
        arms[Direction.NORTH.ordinal()] = Block.box(a, a, 0, b, b, a);
        arms[Direction.SOUTH.ordinal()] = Block.box(a, a, b, b, b, 16);
        arms[Direction.WEST.ordinal()] = Block.box(0, a, a, a, b, b);
        arms[Direction.EAST.ordinal()] = Block.box(b, a, a, 16, b, b);

        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) != 0) shape = Shapes.or(shape, arms[dir.ordinal()]);
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(CONNECT.get(dir))) mask |= 1 << dir.ordinal();
        }
        return SHAPES[mask];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Middle-click gives the right colour, not whichever cable item registered last. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModRegistry.NETWORK_CABLE_ITEMS.get(state.getValue(COLOR)).get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkCableBlockEntity(pos, state);
    }
}
