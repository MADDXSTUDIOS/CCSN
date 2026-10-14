package uk.maddx.ccsn.nas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;

import java.util.UUID;

public final class NasBlockEntity extends BlockEntity {
    private static final String TAG_ID = "NasId";
    private static final String TAG_LABEL = "Label";
    public static final int MAX_LABEL_LENGTH = 32;

    private @Nullable UUID nasId;
    private @Nullable String label;
    private final NasPeripheral peripheral = new NasPeripheral(this);

    public NasBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.NAS_BLOCK_ENTITY.get(), pos, state);
    }

    public NasPeripheral peripheral() {
        return peripheral;
    }

    /**
     * The ID decides which folder on the server holds this NAS's files
     * (world/computercraft/ccsn_nas/<id>). Created the first time it's needed.
     */
    public synchronized UUID getOrCreateId() {
        if (nasId == null) {
            nasId = UUID.randomUUID();
            setChanged();
        }
        return nasId;
    }

    /** True once this NAS has storage assigned (i.e. it may hold files). */
    public boolean hasId() {
        return nasId != null;
    }

    public @Nullable String getLabel() {
        return label;
    }

    public void setLabel(@Nullable String newLabel) {
        if (newLabel != null) {
            newLabel = newLabel.trim();
            if (newLabel.isEmpty()) newLabel = null;
            else if (newLabel.length() > MAX_LABEL_LENGTH) newLabel = newLabel.substring(0, MAX_LABEL_LENGTH);
        }
        label = newLabel;
        setChanged();
    }

    // ---- Saving to the world ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (nasId != null) tag.putUUID(TAG_ID, nasId);
        if (label != null) tag.putString(TAG_LABEL, label);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nasId = tag.hasUUID(TAG_ID) ? tag.getUUID(TAG_ID) : null;
        label = tag.contains(TAG_LABEL) ? tag.getString(TAG_LABEL) : null;
    }

    // ---- Carrying data on the item when broken ----

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        UUID id = input.get(ModRegistry.NAS_ID.get());
        if (id != null) nasId = id;
        String itemLabel = input.get(ModRegistry.NAS_LABEL.get());
        if (itemLabel != null) label = itemLabel;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (nasId != null) components.set(ModRegistry.NAS_ID.get(), nasId);
        if (label != null) components.set(ModRegistry.NAS_LABEL.get(), label);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TAG_ID);
        tag.remove(TAG_LABEL);
    }
}
