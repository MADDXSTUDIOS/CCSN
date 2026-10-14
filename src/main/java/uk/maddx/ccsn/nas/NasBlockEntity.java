package uk.maddx.ccsn.nas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.ModRegistry;
import uk.maddx.ccsn.drive.DriveItem;

import java.util.Objects;
import java.util.UUID;

public final class NasBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BAYS = 4;
    public static final int MAX_LABEL_LENGTH = 32;
    private static final String TAG_LABEL = "Label";

    private @Nullable String label;
    private final NasPeripheral peripheral = new NasPeripheral(this);
    private final UUID[] bayIds = new UUID[BAYS];
    private boolean loading;

    /** The four drive bays. Top bay is slot 0. */
    private final SimpleContainer drives = new SimpleContainer(BAYS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof DriveItem;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(NasBlockEntity.this, player);
        }
    };

    public NasBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.NAS_BLOCK_ENTITY.get(), pos, state);
        drives.addListener(c -> onDrivesChanged());
    }

    public NasPeripheral peripheral() {
        return peripheral;
    }

    public Container drives() {
        return drives;
    }

    public ItemStack getDrive(int bay) {
        return drives.getItem(bay);
    }

    /** The storage ID of the drive in a bay, or null if the bay is empty. */
    public @Nullable UUID getBayId(int bay) {
        return bayIds[bay];
    }

    // ---- Hot-swap ----

    /** Called whenever a slot changes: give new drives storage, re-mount, update the model. */
    private void onDrivesChanged() {
        if (loading || level == null || level.isClientSide) return;

        for (int bay = 0; bay < BAYS; bay++) {
            ItemStack stack = drives.getItem(bay);
            UUID id = null;
            if (stack.getItem() instanceof DriveItem) {
                id = DriveItem.getId(stack);
                if (id == null) {
                    id = UUID.randomUUID();
                    stack.set(ModRegistry.DRIVE_ID.get(), id);
                }
            }
            if (!Objects.equals(id, bayIds[bay])) {
                bayIds[bay] = id;
                peripheral.onBayChanged(bay);
            }
        }

        BlockState state = getBlockState();
        BlockState updated = state;
        for (int bay = 0; bay < BAYS; bay++) {
            updated = updated.setValue(NasBlock.BAY[bay], BayState.of(drives.getItem(bay)));
        }
        if (updated != state) level.setBlock(worldPosition, updated, 3);

        setChanged();
    }

    /** Put a drive in the first empty bay. Returns false if the NAS is full. */
    public boolean insertDrive(ItemStack drive) {
        for (int bay = 0; bay < BAYS; bay++) {
            if (drives.getItem(bay).isEmpty()) {
                drives.setItem(bay, drive);
                return true;
            }
        }
        return false;
    }

    // ---- Label ----

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

    // ---- GUI ----

    @Override
    public Component getDisplayName() {
        return label != null ? Component.literal(label) : Component.translatable("container.ccsn.nas");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new NasMenu(id, inventory, drives);
    }

    // ---- Saving to the world ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, drives.getItems(), registries);
        if (label != null) tag.putString(TAG_LABEL, label);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loading = true;
        try {
            NonNullList<ItemStack> items = NonNullList.withSize(BAYS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
            for (int bay = 0; bay < BAYS; bay++) {
                drives.setItem(bay, items.get(bay));
                bayIds[bay] = items.get(bay).isEmpty() ? null : DriveItem.getId(items.get(bay));
            }
        } finally {
            loading = false;
        }
        label = tag.contains(TAG_LABEL) ? tag.getString(TAG_LABEL) : null;
    }

    // ---- Label travels with the NAS item ----

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        String itemLabel = input.get(ModRegistry.NAS_LABEL.get());
        if (itemLabel != null) label = itemLabel;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (label != null) components.set(ModRegistry.NAS_LABEL.get(), label);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TAG_LABEL);
    }
}
