package uk.maddx.ccsn.network;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

/**
 * Holds the cable's node in CC's wired network. Deliberately NOT exposed as a
 * CC wired element capability, so CC's grey cable and modems never connect to
 * it; we link cable to cable and cable to wall jack ourselves.
 */
public final class NetworkCableBlockEntity extends BlockEntity implements NetworkScheduler.Refreshable {
    private final Element element = new Element();
    private final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(element);
    /** What we linked to on each side last time, so stale links can be dropped. */
    private final WiredNode[] linked = new WiredNode[6];

    public NetworkCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.NETWORK_CABLE_BLOCK_ENTITY.get(), pos, state);
    }

    public DyeColor colour() {
        return getBlockState().getValue(NetworkCableBlock.COLOR);
    }

    WiredNode node() {
        return node;
    }

    void scheduleRefresh() {
        if (level != null && !level.isClientSide) NetworkScheduler.schedule(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        scheduleRefresh();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || !level.isClientSide) node.remove();
    }

    @Override
    public void refreshConnections() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;

        for (Direction dir : Direction.values()) {
            BlockPos other = worldPosition.relative(dir);
            if (!level.isLoaded(other)) continue;

            WiredNode target = null;
            BlockEntity be = level.getBlockEntity(other);
            if (be instanceof NetworkCableBlockEntity cable) {
                if (cable.colour() == colour()) target = cable.node();
            } else if (be instanceof WallJackBlockEntity jack) {
                if (jack.getBlockState().getValue(WallJackBlock.FACING) == dir) target = jack.node();
            } else {
                BlockPos jackPos = other.relative(dir);
                if (level.isLoaded(jackPos)
                    && NetworkCableBlock.jackThroughWall(level, other, level.getBlockState(other), dir)
                    && level.getBlockEntity(jackPos) instanceof WallJackBlockEntity farJack) {
                    target = farJack.node();
                }
            }
            link(dir, target);
        }
    }

    private void link(Direction dir, @Nullable WiredNode target) {
        WiredNode previous = linked[dir.ordinal()];
        if (previous != null && previous != target) node.disconnectFrom(previous);
        if (target != null) node.connectTo(target);
        linked[dir.ordinal()] = target;
    }

    private final class Element implements WiredElement {
        @Override
        public WiredNode getNode() {
            return node;
        }

        @Override
        public Level getLevel() {
            return NetworkCableBlockEntity.this.getLevel();
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(worldPosition);
        }

        @Override
        public String getSenderID() {
            return "ccsn_cable";
        }
    }
}
