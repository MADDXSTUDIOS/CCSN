package uk.maddx.ccsn.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.maddx.ccsn.ModRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * A block of network cable carrying up to {@link CableLanes#MAX_RUNS} separate runs, one per
 * colour, each in its own lane. A run only joins the same colour next to it, and wall jacks
 * set to that colour: fitted straight onto the cable, or on the far side of a one-block wall.
 * CC's grey cable and modems can't see it at all; the wall jack is the only way in.
 *
 * All the per-block data (colours, which way each run goes) lives in the block entity and is
 * drawn by a dynamic model, so the block itself has a single state.
 */
public final class NetworkCableBlock extends BaseEntityBlock {
    public static final MapCodec<NetworkCableBlock> CODEC = simpleCodec(NetworkCableBlock::new);

    public NetworkCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // ---- Walls and jacks ----

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

    /** Our runs changed: jacks fitted to us, or behind a wall from us, may need to pick a different run. */
    static void updateJacks(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            BlockState nextState = level.getBlockState(next);
            if (nextState.getBlock() instanceof WallJackBlock && nextState.getValue(WallJackBlock.FACING) == dir) {
                WallJackBlock.refreshLink(level, next);
            } else if (jackThroughWall(level, next, nextState, dir)) {
                WallJackBlock.refreshLink(level, next.relative(dir));
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) updateJacks(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && !newState.is(this)) updateJacks(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        if (level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable) cable.scheduleRefresh();
    }

    // ---- Shape ----

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        long key = level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable ? cable.renderKey() : 0;
        return CableLanes.shape(key);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ---- Items ----

    /** One cable item back per run. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof NetworkCableBlockEntity cable) {
            for (DyeColor colour : cable.colours()) {
                drops.add(new ItemStack(ModRegistry.NETWORK_CABLE_ITEMS.get(colour).get()));
            }
        }
        return drops;
    }

    /** Middle-click gives the first run's colour. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        DyeColor colour = level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable ? cable.firstColour() : null;
        return new ItemStack(ModRegistry.NETWORK_CABLE_ITEMS.get(colour == null ? DyeColor.WHITE : colour).get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkCableBlockEntity(pos, state);
    }
}
