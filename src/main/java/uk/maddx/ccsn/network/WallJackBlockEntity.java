package uk.maddx.ccsn.network;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

/**
 * The jack's node joins the cable run behind it to whatever CC thing is plugged
 * into the front: grey cable or a wired modem.
 */
public final class WallJackBlockEntity extends BlockEntity implements NetworkScheduler.Refreshable {
    private final Element element = new Element();
    private final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(element);
    private @Nullable WiredNode behind;
    private @Nullable WiredNode front;

    public WallJackBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.WALL_JACK_BLOCK_ENTITY.get(), pos, state);
    }

    WiredNode node() {
        return node;
    }

    private Direction facing() {
        return getBlockState().getValue(WallJackBlock.FACING);
    }

    /** What CC sees: only the front face is a socket. */
    public @Nullable WiredElement wiredElement(@Nullable Direction side) {
        return side == null || side == facing() ? element : null;
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
        Direction facing = facing();

        // Back: the cable run this jack is fitted to.
        WiredNode newBehind = null;
        BlockPos behindPos = worldPosition.relative(facing.getOpposite());
        if (level.isLoaded(behindPos) && level.getBlockEntity(behindPos) instanceof NetworkCableBlockEntity cable) {
            newBehind = cable.node();
        }
        behind = relink(behind, newBehind);

        // Front: CC grey cable or a wired modem. Our own cable exposes nothing here, so it can't sneak in.
        WiredNode newFront = null;
        BlockPos frontPos = worldPosition.relative(facing);
        if (level.isLoaded(frontPos)) {
            WiredElement plugged = level.getCapability(WiredElementCapability.get(), frontPos, facing.getOpposite());
            if (plugged != null) newFront = plugged.getNode();
        }
        front = relink(front, newFront);
    }

    private @Nullable WiredNode relink(@Nullable WiredNode previous, @Nullable WiredNode target) {
        if (previous != null && previous != target) node.disconnectFrom(previous);
        if (target != null) node.connectTo(target);
        return target;
    }

    private final class Element implements WiredElement {
        @Override
        public WiredNode getNode() {
            return node;
        }

        @Override
        public Level getLevel() {
            return WallJackBlockEntity.this.getLevel();
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(worldPosition);
        }

        @Override
        public String getSenderID() {
            return "ccsn_wall_jack";
        }
    }
}
