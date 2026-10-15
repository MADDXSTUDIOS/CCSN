package uk.maddx.ccsn.network;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.CCSNConfig;
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
    /** Everything the client model needs to draw this block. */
    public static final ModelProperty<CableRender> RENDER = new ModelProperty<>();
    /** The block a disguised cable looks like. */
    public static final ModelProperty<BlockState> DISGUISE = new ModelProperty<>();

    private int colourBits;
    /** Per colour ordinal: the 6 sides that run reaches out to. Worked out on the server, synced. */
    private final int[] masks = new int[16];
    /** Server only. */
    private final Map<DyeColor, Strand> strands = new EnumMap<>(DyeColor.class);
    /** Tray or trunking (or none). A disguise is tracked separately. */
    private int cover = CableLanes.COVER_NONE;
    private @Nullable BlockState disguise;
    /**
     * Worked out on the server, synced: sides with a solid face next to us (walls, floor, ceiling),
     * sides trunking is left open (joins more cable), and sides trunking widens out to.
     */
    private int solidSides, openSides, widenSides;

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
        List<DyeColor> out = new ArrayList<>(CableLanes.ABSOLUTE_MAX_RUNS);
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

    public CableRender render() {
        int coverType = disguise != null ? CableLanes.COVER_DISGUISE : cover;
        return CableRender.of(colourBits, masks, solidSides, openSides, widenSides, coverType);
    }

    // ---- Covers ----

    public int cover() {
        return cover;
    }

    public @Nullable BlockState disguise() {
        return disguise;
    }

    /** The item you'd get back for the current cover (tray, trunking or the disguise block), or empty. */
    public ItemStack coverItem() {
        if (disguise != null) return new ItemStack(disguise.getBlock());
        if (cover == CableLanes.COVER_TRAY) return new ItemStack(ModRegistry.CABLE_TRAY.get());
        if (cover == CableLanes.COVER_TRUNKING) return new ItemStack(ModRegistry.CABLE_TRUNKING.get());
        return ItemStack.EMPTY;
    }

    /** Fit a tray or trunking. Returns whatever cover came off (to give back). */
    public ItemStack setCover(int newCover) {
        ItemStack old = coverItem();
        cover = newCover;
        setDisguiseState(null);
        coverChanged();
        return old;
    }

    /** Disguise as a block. Returns whatever cover came off (to give back). */
    public ItemStack setDisguise(BlockState state) {
        ItemStack old = coverItem();
        cover = CableLanes.COVER_NONE;
        setDisguiseState(state);
        coverChanged();
        return old;
    }

    /** Take any cover off. Returns it (to give back). */
    public ItemStack removeCover() {
        ItemStack old = coverItem();
        cover = CableLanes.COVER_NONE;
        setDisguiseState(null);
        coverChanged();
        return old;
    }

    private void setDisguiseState(@Nullable BlockState state) {
        disguise = state;
        // The block itself only knows "disguised or not": that's what makes it solid, block light and so on.
        if (level != null && !level.isClientSide) {
            BlockState own = getBlockState();
            boolean disguised = state != null;
            if (own.getValue(NetworkCableBlock.DISGUISED) != disguised) {
                level.setBlock(worldPosition, own.setValue(NetworkCableBlock.DISGUISED, disguised), Block.UPDATE_ALL);
            }
        }
    }

    private void coverChanged() {
        setChanged();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        refreshNeighbours(); // whether their trunking joins ours depends on our cover
    }

    private boolean isTrunking() {
        return cover == CableLanes.COVER_TRUNKING && disguise == null;
    }

    /** Have the cable blocks round us look again (after something they look at here has changed). */
    private void refreshNeighbours() {
        if (level == null || level.isClientSide) return;
        for (Direction dir : Direction.values()) {
            BlockPos other = worldPosition.relative(dir);
            if (level.isLoaded(other) && level.getBlockEntity(other) instanceof NetworkCableBlockEntity cable) {
                cable.scheduleRefresh();
            }
        }
    }

    /** Add a run. Returns false if it's already here or the block is full. */
    public boolean addColour(DyeColor colour) {
        if (has(colour) || colourCount() >= CCSNConfig.maxRunsPerBlock()) return false;
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

        // What's round us, for the tray's supports and the trunking's shape and ends.
        int union = 0;
        for (DyeColor colour : colours()) union |= masks[colour.ordinal()];
        int solidNow = 0;
        long diagonal = 0;
        for (Direction dir : Direction.values()) {
            BlockPos other = worldPosition.relative(dir);
            int bit = 1 << dir.ordinal();
            if (!level.isLoaded(other)) {
                solidNow |= solidSides & bit;
                continue;
            }
            if (sturdy(level, other, dir.getOpposite())) solidNow |= bit;
            if ((union & bit) == 0) continue;
            // Round the corner from each arm: is there a wall the next trunking along is widened to?
            for (Direction d : Direction.values()) {
                if (d.getAxis() == dir.getAxis()) continue;
                BlockPos corner = other.relative(d);
                if (level.isLoaded(corner) && sturdy(level, corner, d.getOpposite())) {
                    diagonal |= 1L << (dir.ordinal() * 6 + d.ordinal());
                }
            }
        }
        int widenNow = CableLanes.widen(union, solidNow, diagonal);
        int openNow = 0;
        for (Direction dir : Direction.values()) {
            BlockPos other = worldPosition.relative(dir);
            int bit = 1 << dir.ordinal();
            if (!level.isLoaded(other)) {
                openNow |= openSides & bit;
                continue;
            }
            // Open into more cable; but where two lengths of trunking meet with different shapes, cap both.
            if (level.getBlockEntity(other) instanceof NetworkCableBlockEntity cable
                && (!cable.isTrunking() || CableLanes.profilesMatch(dir, widenNow, cable.widenSides))) {
                openNow |= bit;
            }
        }
        boolean widened = widenNow != widenSides;
        if (solidNow != solidSides || openNow != openSides || widened) {
            solidSides = solidNow;
            openSides = openNow;
            widenSides = widenNow;
            changed = true;
        }
        if (widened) refreshNeighbours();

        if (changed) {
            setChanged();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean sturdy(Level level, BlockPos pos, Direction face) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, face);
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
        tag.putInt("Cover", cover);
        tag.putInt("Solid", solidSides);
        tag.putInt("Open", openSides);
        tag.putInt("Widen", widenSides);
        if (disguise != null) tag.put("Disguise", NbtUtils.writeBlockState(disguise));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colourBits = tag.getInt("Colours") & 0xFFFF;
        int[] saved = tag.getIntArray("Masks");
        for (int i = 0; i < masks.length; i++) masks[i] = i < saved.length ? saved[i] & 0x3F : 0;
        cover = tag.getInt("Cover") & 3;
        if (cover == CableLanes.COVER_DISGUISE) cover = CableLanes.COVER_NONE;
        solidSides = tag.getInt("Solid") & 0x3F;
        openSides = tag.getInt("Open") & 0x3F;
        widenSides = tag.getInt("Widen") & 0x3F;
        disguise = tag.contains("Disguise")
            ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Disguise"))
            : null;
        if (disguise != null && disguise.isAir()) disguise = null;

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
        ModelData.Builder data = ModelData.builder().with(RENDER, render());
        if (disguise != null) data.with(DISGUISE, disguise);
        return data.build();
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
