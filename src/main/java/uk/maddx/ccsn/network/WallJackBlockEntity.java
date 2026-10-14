package uk.maddx.ccsn.network;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.network.wired.WiredNetworkChange;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The jack's node joins the cable run behind it to whatever CC things are plugged
 * into its front and sides: grey cable or wired modems.
 */
public final class WallJackBlockEntity extends BlockEntity implements NetworkScheduler.Refreshable {
    private static final int MAX_LISTED = 8;

    private final Element element = new Element();
    private final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(element);
    private @Nullable WiredNode run;
    private final WiredNode[] plugs = new WiredNode[6];
    /** Peripheral names on the network, for the status check. */
    private final Set<String> devices = new TreeSet<>();

    public WallJackBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.WALL_JACK_BLOCK_ENTITY.get(), pos, state);
    }

    WiredNode node() {
        return node;
    }

    private Direction facing() {
        return getBlockState().getValue(WallJackBlock.FACING);
    }

    /** What CC sees: every face but the back, and never another jack (so runs side by side stay apart). */
    public @Nullable WiredElement wiredElement(@Nullable Direction side) {
        if (side == null) return element;
        if (side == facing().getOpposite()) return null;
        if (level != null && level.getBlockState(worldPosition.relative(side)).getBlock() instanceof WallJackBlock) {
            return null;
        }
        return element;
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

        // Back: the cable run, directly behind or through a wall.
        WiredNode newRun = null;
        BlockPos runPos = WallJackBlock.runPos(level, worldPosition, facing);
        if (runPos != null && level.isLoaded(runPos)
            && level.getBlockEntity(runPos) instanceof NetworkCableBlockEntity cable) {
            newRun = cable.node();
        }
        run = relink(run, newRun);

        // Front and sides: CC grey cable or wired modems. Our cable exposes nothing, so it can't sneak in.
        for (Direction dir : Direction.values()) {
            WiredNode target = null;
            if (WallJackBlock.pluggedOn(level, worldPosition, facing, dir)) {
                WiredElement plugged = level.getCapability(
                    WiredElementCapability.get(), worldPosition.relative(dir), dir.getOpposite());
                if (plugged != null) target = plugged.getNode();
            }
            plugs[dir.ordinal()] = relink(plugs[dir.ordinal()], target);
        }
    }

    private @Nullable WiredNode relink(@Nullable WiredNode previous, @Nullable WiredNode target) {
        if (previous != null && previous != target) node.disconnectFrom(previous);
        if (target != null) node.connectTo(target);
        return target;
    }

    /** Right-click with an empty hand: what is this jack actually connected to? */
    void sendStatus(Player player) {
        Level level = getLevel();
        if (level == null) return;
        BlockState state = getBlockState();
        Direction facing = state.getValue(WallJackBlock.FACING);

        player.displayClientMessage(Component.translatable("chat.ccsn.wall_jack.title")
            .withStyle(ChatFormatting.GOLD), false);

        // The run
        DyeColor colour = state.getValue(WallJackBlock.LINK).colour();
        if (colour == null) {
            player.displayClientMessage(Component.translatable("chat.ccsn.wall_jack.no_run")
                .withStyle(ChatFormatting.RED), false);
        } else {
            BlockPos runPos = WallJackBlock.runPos(level, worldPosition, facing);
            boolean throughWall = runPos != null && !runPos.equals(worldPosition.relative(facing.getOpposite()));
            Component name = Component.translatable("color.minecraft." + colour.getName());
            player.displayClientMessage(Component.translatable(
                throughWall ? "chat.ccsn.wall_jack.run_through" : "chat.ccsn.wall_jack.run", name), false);
        }

        // What's plugged in
        List<Component> sides = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (!state.getValue(WallJackBlock.PLUGGED.get(dir))) continue;
            sides.add(dir == facing
                ? Component.translatable("chat.ccsn.wall_jack.side.front")
                : Component.translatable("chat.ccsn.wall_jack.side." + dir.getName()));
        }
        player.displayClientMessage(sides.isEmpty()
            ? Component.translatable("chat.ccsn.wall_jack.unplugged").withStyle(ChatFormatting.GRAY)
            : Component.translatable("chat.ccsn.wall_jack.plugged", join(sides)), false);

        // What the network can see
        List<String> names;
        synchronized (devices) {
            names = new ArrayList<>(devices);
        }
        if (names.isEmpty()) {
            player.displayClientMessage(Component.translatable("chat.ccsn.wall_jack.no_devices")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            String shown = String.join(", ", names.subList(0, Math.min(MAX_LISTED, names.size())));
            if (names.size() > MAX_LISTED) shown += " (+" + (names.size() - MAX_LISTED) + ")";
            player.displayClientMessage(Component.translatable("chat.ccsn.wall_jack.devices",
                names.size(), Component.literal(shown).withStyle(ChatFormatting.AQUA)), false);
        }
    }

    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
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

        @Override
        public void networkChanged(WiredNetworkChange change) {
            synchronized (devices) {
                devices.removeAll(change.peripheralsRemoved().keySet());
                devices.addAll(change.peripheralsAdded().keySet());
            }
        }
    }
}
