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
 * it and the back of a wall jack fitted to it. CC's grey cable and modems can't
 * see it at all; the wall jack is the only way in.
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

    /** Does a cable in {@code self} reach over to {@code other}, which is on side {@code dir}? */
    static boolean connects(BlockState self, BlockState other, Direction dir) {
        if (other.getBlock() instanceof NetworkCableBlock) return other.getValue(COLOR) == self.getValue(COLOR);
        // A wall jack fitted to this cable faces away from it.
        if (other.getBlock() instanceof WallJackBlock) return other.getValue(WallJackBlock.FACING) == dir;
        return false;
    }

    public static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECT.get(dir), connects(state, level.getBlockState(pos.relative(dir)), dir));
        }
        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(CONNECT.get(dir), connects(state, neighbour, dir));
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
