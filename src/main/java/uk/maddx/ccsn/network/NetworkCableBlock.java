package uk.maddx.ccsn.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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
 * All the per-block data (colours, which way each run goes, any cover) lives in the block entity
 * and is drawn by a dynamic model. The only block-state property is "disguised", because that
 * changes how the block behaves in the world (solid, blocks light).
 *
 * Covers: a cable tray, trunking, or a disguise (right-click with a plain full block). The wrench
 * takes them off.
 */
public final class NetworkCableBlock extends BaseEntityBlock {
    public static final MapCodec<NetworkCableBlock> CODEC = simpleCodec(NetworkCableBlock::new);
    /** Disguised as a full block: solid, blocks light, full collision. The look itself is in the block entity. */
    public static final BooleanProperty DISGUISED = BooleanProperty.create("disguised");

    public NetworkCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DISGUISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISGUISED);
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
        if (state.getValue(DISGUISED)) return Shapes.block();
        long key = level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable ? cable.renderKey() : 0;
        return CableLanes.shape(key);
    }

    /** Only a disguised cable hides the faces of the blocks around it (and blocks light). */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(DISGUISED) ? Shapes.block() : Shapes.empty();
    }

    // ---- Disguise ----

    /** A block you can disguise cable as: a plain, solid, full cube with no block entity (bricks, planks, stone...). */
    static boolean canDisguiseAs(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof NetworkCableBlock || block instanceof WallJackBlock) return false;
        if (state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL) return false;
        return state.isSolidRender(level, pos) && state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Right-click with a plain full block (bricks, planks, concrete...) to disguise the cable as it.
     * Sneak to place the block next to the cable instead. Anything else: carry on as normal.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
            || !(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockState look = blockItem.getBlock().getStateForPlacement(
            new BlockPlaceContext(new UseOnContext(player, hand, hit)));
        if (look == null || !canDisguiseAs(look, level, pos)) look = blockItem.getBlock().defaultBlockState();
        if (!canDisguiseAs(look, level, pos)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (look.equals(cable.disguise())) return ItemInteractionResult.CONSUME;

        if (!level.isClientSide) {
            ItemStack old = cable.setDisguise(look);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
                if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
            }
            SoundType sound = look.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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

    /** One cable item back per run, plus the cover (tray, trunking or the disguise block). */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof NetworkCableBlockEntity cable) {
            for (DyeColor colour : cable.colours()) {
                drops.add(new ItemStack(ModRegistry.NETWORK_CABLE_ITEMS.get(colour).get()));
            }
            ItemStack cover = cable.coverItem();
            if (!cover.isEmpty()) drops.add(cover);
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
