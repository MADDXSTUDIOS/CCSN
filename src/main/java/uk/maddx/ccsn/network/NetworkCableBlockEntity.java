package uk.maddx.ccsn.network;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the runs in one cable block: which colours, which way each goes, and one CC wired
 * node per colour so runs never mix. Deliberately NOT exposed as a CC wired element, so CC's
 * grey cable and modems can't connect; we link cable to cable and cable to wall jack ourselves.
 */
public final class NetworkCableBlockEntity extends BlockEntity implements NetworkScheduler.Refreshable {
    /** Packed colours + directions, for the client model. See {@link CableLanes#key}. */
    public static final ModelProperty<Long> RENDER_KEY = new ModelProperty<>();

    private int colourBits;
    /** Per colour ordinal: the 6 sides that run reaches out to. Worked out on the server, synced. */
    private final int[] masks = new int[16];
    /** Server only. */
    private final Map<DyeColor, Strand> strands = new EnumMap<>(DyeColor.class);

    public NetworkCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.NETWORK_CABLE_BLOCK_ENTITY.get(), pos, state);
    }

    // ---- Colours ----

    public boolean has(DyeColor colour) {
        return (colourBits & (1 << colour.ordinal())) != 0;
    }

    public int colourCount() {
        return Integer.bitCount(colourBits);
    }

    public List<DyeColor> colours() {
        List<DyeColor> out = new ArrayList<>(CableLanes.MAX_RUNS);
        for (DyeColor colour : DyeColor.values()) {
            if (has(colour)) out.add(colour);
        }
        return out;
    }

    public @Nullable DyeColor firstColour() {
        for (DyeColor colour : DyeColor.values()) {
            if (has(colour)) return colour;
        }
        return null;
    }

    public long renderKey() {
        return CableLanes.key(colourBits, masks);
    }

    /** Add a run. Returns false if it's already here or the block is full. */
    public boolean addColour(DyeColor colour) {
        if (has(colour) || colourCount() >= CableLanes.MAX_RUNS) return false;
        colourBits |= 1 << colour.ordinal();
        masks[colour.ordinal()] = 0;
        setChanged();
        if (level == null) return true;
        if (level.isClientSide) {
            requestModelDataUpdate();
            return true;
        }
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        // Neighbouring cable doesn't see block-entity changes on its own.
        level.updateNeighborsAt(worldPosition, state.getBlock());
        NetworkCableBlock.updateJacks(level, worldPosition);
        scheduleRefresh();
        return true;
    }

    /** The wired node for one run (server only). */
    @Nullable WiredNode node(DyeColor colour) {
        if (!has(colour) || level == null || level.isClientSide) return null;
        return strand(colour).getNode();
    }

    private Strand strand(DyeColor colour) {
        return strands.computeIfAbsent(colour, Strand::new);
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
        if (level == null || !level.isClientSide) {
            for (Strand strand : strands.values()) strand.getNode().remove();
        }
    }

    @Override
    public void refreshConnections() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;

        boolean changed = false;
        for (DyeColor colour : colours()) {
            Strand strand = strand(colour);
            int mask = 0;
            for (Direction dir : Direction.values()) {
                int bit = 1 << dir.ordinal();
                BlockPos other = worldPosition.relative(dir);
                if (!level.isLoaded(other)) {
                    mask |= masks[colour.ordinal()] & bit; // keep drawing towards unloaded chunks
                    continue;
                }
                WiredNode target = findTarget(level, colour, dir, other);
                strand.link(dir, target);
                if (target != null) mask |= bit;
            }
            if (mask != masks[colour.ordinal()]) {
                masks[colour.ordinal()] = mask;
                changed = true;
            }
        }

        if (changed) {
            setChanged();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private @Nullable WiredNode findTarget(Level level, DyeColor colour, Direction dir, BlockPos other) {
        BlockEntity be = level.getBlockEntity(other);
        if (be instanceof NetworkCableBlockEntity cable) {
            if (!cable.has(colour)) return null;
            // Make sure the other block draws its half of the join too.
            if ((cable.masks[colour.ordinal()] & (1 << dir.getOpposite().ordinal())) == 0) cable.scheduleRefresh();
            return cable.node(colour);
        }
        if (be instanceof WallJackBlockEntity jack) return jack.feeds(dir, colour) ? jack.node() : null;

        BlockPos jackPos = other.relative(dir);
        if (level.isLoaded(jackPos)
            && NetworkCableBlock.jackThroughWall(level, other, level.getBlockState(other), dir)
            && level.getBlockEntity(jackPos) instanceof WallJackBlockEntity farJack
            && farJack.feeds(dir, colour)) {
            return farJack.node();
        }
        return null;
    }

    // ---- Save and sync ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Colours", colourBits);
        tag.putIntArray("Masks", masks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colourBits = tag.getInt("Colours") & 0xFFFF;
        int[] saved = tag.getIntArray("Masks");
        for (int i = 0; i < masks.length; i++) masks[i] = i < saved.length ? saved[i] & 0x3F : 0;

        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
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

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(RENDER_KEY, renderKey()).build();
    }

    // ---- One run ----

    private final class Strand implements WiredElement {
        private final DyeColor colour;
        private final WiredNode node;
        /** What we linked to on each side last time, so stale links can be dropped. */
        private final WiredNode[] linked = new WiredNode[6];

        Strand(DyeColor colour) {
            this.colour = colour;
            this.node = ComputerCraftAPI.createWiredNodeForElement(this);
        }

        void link(Direction dir, @Nullable WiredNode target) {
            WiredNode previous = linked[dir.ordinal()];
            if (previous != null && previous != target) node.disconnectFrom(previous);
            if (target != null) node.connectTo(target);
            linked[dir.ordinal()] = target;
        }

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
            return "ccsn_cable_" + colour.getSerializedName();
        }
    }
}
