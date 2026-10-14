package uk.maddx.ccsn.nas;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import uk.maddx.ccsn.ModRegistry;
import uk.maddx.ccsn.drive.DriveItem;

/** The NAS bay GUI: four drive slots stacked like the front of the NAS, plus the player inventory. */
public final class NasMenu extends AbstractContainerMenu {
    public static final int BAY_X = 44;
    public static final int BAY_Y = 18;
    public static final int INV_Y = 104;

    private final Container drives;

    /** Client side. */
    public NasMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(NasBlockEntity.BAYS));
    }

    /** Server side. */
    public NasMenu(int id, Inventory inventory, Container drives) {
        super(ModRegistry.NAS_MENU.get(), id);
        checkContainerSize(drives, NasBlockEntity.BAYS);
        this.drives = drives;
        drives.startOpen(inventory.player);

        for (int bay = 0; bay < NasBlockEntity.BAYS; bay++) {
            addSlot(new Slot(drives, bay, BAY_X, BAY_Y + bay * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof DriveItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return drives.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        drives.stopOpen(player);
    }

    /** Shift-click: drives go into the first free bay, bays go back to the inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int bays = NasBlockEntity.BAYS;

        if (index < bays) {
            if (!moveItemStackTo(stack, bays, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof DriveItem) {
            if (!moveItemStackTo(stack, 0, bays, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    public ItemStack getDrive(int bay) {
        return slots.get(bay).getItem();
    }
}
