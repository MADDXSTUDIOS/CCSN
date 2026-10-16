package uk.maddx.ccsn.network;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNetworkChange;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The switch's one wired node joins every run plugged into it, and any switch touching it. It also
 * listens for messages crossing the network, so the port lights can flicker with traffic.
 */
public final class NetworkSwitchBlockEntity extends BlockEntity implements NetworkScheduler.Refreshable {
    /** Block event id: "there was traffic", sent to clients to flicker the lights. */
    static final int EVENT_TRAFFIC = 1;
    /** Send a traffic pulse at most this often (ticks), however busy the network is. */
    private static final int PULSE_TICKS = 4;
    /** How long the lights keep flickering after the last pulse (ticks). */
    public static final int BUSY_TICKS = 20;
    private static final int MAX_LISTED = 8;

    private final Element element = new Element();
    private final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(element);
    /** Counts every message on the network. Called from computer threads, hence atomic. */
    private final AtomicLong traffic = new AtomicLong();
    private long trafficSeen;
    private long lastPulse = Long.MIN_VALUE / 2;

    /** What we linked to last time, per side and colour, so stale links can be dropped. */
    private final WiredNode[][] runLinks = new WiredNode[6][16];
    private final WiredNode[] switchLinks = new WiredNode[6];
    /** Peripheral names on the network, for the status check. */
    private final Set<String> devices = new TreeSet<>();

    /** Bit per port with a run plugged in. Server works it out; synced for the lights. */
    private int ports;
    /** Client: game time of the last traffic pulse. */
    private long lastTraffic = Long.MIN_VALUE / 2;

    public NetworkSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.NETWORK_SWITCH_BLOCK_ENTITY.get(), pos, state);
        node.addReceiver(new Listener());
    }

    WiredNode node() {
        return node;
    }

    public Direction facing() {
        return getBlockState().getValue(NetworkSwitchBlock.FACING);
    }

    /** Does a run arriving on our side {@code face} plug in? Every side but the front. */
    boolean accepts(Direction face) {
        return face != facing();
    }

    public int ports() {
        return ports;
    }

    /** Client: are the lights flickering (traffic in the last second)? */
    public boolean busy(long gameTime) {
        return gameTime - lastTraffic < BUSY_TICKS;
    }

    // ---- Connections ----

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

        int newPorts = 0;
        for (Direction face : Direction.values()) {
            BlockPos other = worldPosition.relative(face);
            BlockEntity be = accepts(face) && level.isLoaded(other) ? level.getBlockEntity(other) : null;

            // Every run in a cable touching this side plugs into its colour's port.
            WiredNode[] links = runLinks[face.ordinal()];
            for (DyeColor colour : DyeColor.values()) {
                WiredNode target = null;
                if (be instanceof NetworkCableBlockEntity cable && cable.has(colour)) target = cable.node(colour);
                links[colour.ordinal()] = relink(links[colour.ordinal()], target);
                if (target != null) newPorts |= 1 << SwitchPorts.port(face, colour);
            }

            // A switch touching this side (not by its front) chains on.
            WiredNode chained = be instanceof NetworkSwitchBlockEntity sw && sw.accepts(face.getOpposite()) ? sw.node() : null;
            switchLinks[face.ordinal()] = relink(switchLinks[face.ordinal()], chained);
        }

        if (newPorts != ports) {
            ports = newPorts;
            setChanged();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private @Nullable WiredNode relink(@Nullable WiredNode previous, @Nullable WiredNode target) {
        if (previous != null && previous != target) node.disconnectFrom(previous);
        if (target != null) node.connectTo(target);
        return target;
    }

    // ---- Traffic ----

    /** Server tick: if messages have crossed the network, tell clients (at most every few ticks). */
    static void serverTick(Level level, BlockPos pos, BlockState state, NetworkSwitchBlockEntity sw) {
        long count = sw.traffic.get();
        if (count == sw.trafficSeen) return;
        long now = level.getGameTime();
        if (now - sw.lastPulse < PULSE_TICKS) return;
        sw.trafficSeen = count;
        sw.lastPulse = now;
        level.blockEvent(pos, state.getBlock(), EVENT_TRAFFIC, 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_TRAFFIC) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) lastTraffic = level.getGameTime();
        return true; // true on the server too, or the event isn't sent on to clients
    }

    // ---- Save and sync ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Ports", ports);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ports = tag.getInt("Ports");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Status check ----

    /** Right-click with an empty hand (or the wrench): which ports are in use, and what's on the network. */
    void sendStatus(Player player) {
        player.displayClientMessage(Component.translatable("chat.ccsn.switch.title")
            .withStyle(ChatFormatting.GOLD), false);

        List<Component> used = new ArrayList<>();
        for (int port = 0; port < SwitchPorts.COUNT; port++) {
            if ((ports & (1 << port)) != 0) used.add(SwitchPorts.name(port));
        }
        player.displayClientMessage(used.isEmpty()
            ? Component.translatable("chat.ccsn.switch.no_ports").withStyle(ChatFormatting.GRAY)
            : Component.translatable("chat.ccsn.switch.ports", used.size(), SwitchPorts.COUNT, join(used)), false);

        int chained = 0;
        for (WiredNode link : switchLinks) if (link != null) chained++;
        if (chained > 0) {
            player.displayClientMessage(Component.translatable("chat.ccsn.switch.chained", chained)
                .withStyle(ChatFormatting.GRAY), false);
        }

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

    // ---- CC plumbing ----

    private final class Element implements WiredElement {
        @Override
        public WiredNode getNode() {
            return node;
        }

        @Override
        public Level getLevel() {
            return NetworkSwitchBlockEntity.this.getLevel();
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(worldPosition);
        }

        @Override
        public String getSenderID() {
            return "ccsn_switch";
        }

        @Override
        public void networkChanged(WiredNetworkChange change) {
            synchronized (devices) {
                devices.removeAll(change.peripheralsRemoved().keySet());
                devices.addAll(change.peripheralsAdded().keySet());
            }
        }
    }

    /** Hears every message on the network (whatever the channel), just to count them. */
    private final class Listener implements PacketReceiver {
        @Override
        public Level getLevel() {
            return NetworkSwitchBlockEntity.this.getLevel();
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(worldPosition);
        }

        @Override
        public double getRange() {
            return Double.MAX_VALUE;
        }

        @Override
        public boolean isInterdimensional() {
            return false;
        }

        @Override
        public void receiveSameDimension(dan200.computercraft.api.network.Packet packet, double distance) {
            traffic.incrementAndGet();
        }

        @Override
        public void receiveDifferentDimension(dan200.computercraft.api.network.Packet packet) {
            traffic.incrementAndGet();
        }
    }
}
