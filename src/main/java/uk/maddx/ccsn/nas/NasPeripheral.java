package uk.maddx.ccsn.nas;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.drive.DriveItem;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lua side of the NAS. Peripheral type: "nas".
 *
 * Every drive in the NAS is mounted on every attached computer as its own
 * folder: nas, nas2, nas3... Drives can be swapped while computers are running;
 * attached computers get "nas_drive_insert" / "nas_drive_eject" events with
 * (peripheralName, bay).
 *
 * Bays are numbered 1-4 in Lua, top to bottom.
 */
public final class NasPeripheral implements IPeripheral {
    public static final String TYPE = "nas";
    private static final int MAX_MOUNTS = 64;
    private static final int BAYS = NasBlockEntity.BAYS;

    private final NasBlockEntity nas;
    /** For each attached computer, where each bay is mounted (null = not mounted). */
    private final Map<IComputerAccess, String[]> computers = new HashMap<>();
    private final WritableMount[] mounts = new WritableMount[BAYS];
    private final UUID[] mountIds = new UUID[BAYS];

    NasPeripheral(NasBlockEntity nas) {
        this.nas = nas;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof NasPeripheral o && o.nas == nas;
    }

    @Override
    public Object getTarget() {
        return nas;
    }

    // ---- Mounting ----

    private @Nullable WritableMount mountFor(int bay) {
        UUID id = nas.getBayId(bay);
        if (id == null || !(nas.getDrive(bay).getItem() instanceof DriveItem drive)) return null;
        if (mounts[bay] != null && id.equals(mountIds[bay])) return mounts[bay];

        Level level = nas.getLevel();
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null) return null;
        mounts[bay] = ComputerCraftAPI.createSaveDirMount(server, "ccsn_drives/" + id, drive.capacity());
        mountIds[bay] = id;
        return mounts[bay];
    }

    /** CC won't pick a free name for us, so number them like disk drives do: nas, nas2, nas3... */
    private static @Nullable String mountOn(IComputerAccess computer, WritableMount mount) {
        for (int i = 1; i <= MAX_MOUNTS; i++) {
            String path = computer.mountWritable(i == 1 ? "nas" : "nas" + i, mount);
            if (path != null) return path;
        }
        return null;
    }

    @Override
    public synchronized void attach(IComputerAccess computer) {
        String[] paths = new String[BAYS];
        computers.put(computer, paths);
        for (int bay = 0; bay < BAYS; bay++) {
            WritableMount mount = mountFor(bay);
            if (mount != null) paths[bay] = mountOn(computer, mount);
        }
    }

    @Override
    public synchronized void detach(IComputerAccess computer) {
        String[] paths = computers.remove(computer);
        if (paths == null) return;
        for (String path : paths) {
            if (path != null) computer.unmount(path);
        }
    }

    /** A drive went in or came out (server thread). Re-mount it everywhere and tell the computers. */
    synchronized void onBayChanged(int bay) {
        mounts[bay] = null;
        mountIds[bay] = null;
        WritableMount mount = mountFor(bay);

        for (Map.Entry<IComputerAccess, String[]> entry : computers.entrySet()) {
            IComputerAccess computer = entry.getKey();
            String[] paths = entry.getValue();
            if (paths[bay] != null) {
                computer.unmount(paths[bay]);
                paths[bay] = null;
            }
            if (mount != null) paths[bay] = mountOn(computer, mount);
            computer.queueEvent(mount != null ? "nas_drive_insert" : "nas_drive_eject",
                computer.getAttachmentName(), bay + 1);
        }
    }

    // ---- Lua API ----

    private static int checkBay(int bay) throws LuaException {
        if (bay < 1 || bay > BAYS) throw new LuaException("Bay must be between 1 and " + BAYS);
        return bay - 1;
    }

    private synchronized @Nullable Map<String, Object> describe(IComputerAccess computer, int bay) throws LuaException {
        ItemStack stack = nas.getDrive(bay);
        if (!(stack.getItem() instanceof DriveItem drive)) return null;

        Map<String, Object> info = new HashMap<>();
        info.put("bay", bay + 1);
        info.put("tier", drive.tier());
        info.put("capacity", drive.capacity());
        UUID id = nas.getBayId(bay);
        if (id != null) info.put("id", id.toString());
        String label = DriveItem.getLabel(stack);
        if (label != null) info.put("label", label);
        String[] paths = computers.get(computer);
        if (paths != null && paths[bay] != null) info.put("mount", paths[bay]);

        WritableMount mount = mountFor(bay);
        if (mount != null) {
            try {
                info.put("free", mount.getRemainingSpace());
            } catch (IOException e) {
                throw new LuaException("Could not read drive free space: " + e.getMessage());
            }
        }
        return info;
    }

    /** Number of drive bays (4). */
    @LuaFunction
    public final int getBayCount() {
        return BAYS;
    }

    /**
     * Info about the drive in a bay, or nil if it's empty:
     * { bay, tier, capacity, free, mount, id, label }
     */
    @LuaFunction
    public final @Nullable Map<String, Object> getDrive(IComputerAccess computer, int bay) throws LuaException {
        return describe(computer, checkBay(bay));
    }

    /** Info about every drive, keyed by bay number. Empty bays are missing. */
    @LuaFunction
    public final Map<Integer, Map<String, Object>> getDrives(IComputerAccess computer) throws LuaException {
        Map<Integer, Map<String, Object>> result = new HashMap<>();
        for (int bay = 0; bay < BAYS; bay++) {
            Map<String, Object> info = describe(computer, bay);
            if (info != null) result.put(bay + 1, info);
        }
        return result;
    }

    /** Where a bay's drive is mounted on this computer, e.g. "nas2", or nil. */
    @LuaFunction
    public final synchronized @Nullable String getMountPath(IComputerAccess computer, int bay) throws LuaException {
        String[] paths = computers.get(computer);
        return paths == null ? null : paths[checkBay(bay)];
    }

    /** Set or clear (pass nothing) a drive's label, max 32 characters. The label shows as the item name. */
    @LuaFunction(mainThread = true)
    public final void setDriveLabel(int bay, Optional<String> label) throws LuaException {
        int i = checkBay(bay);
        ItemStack stack = nas.getDrive(i);
        if (!(stack.getItem() instanceof DriveItem)) throw new LuaException("No drive in bay " + bay);
        DriveItem.setLabel(stack, label.orElse(null));
        nas.setChanged();
    }

    /** The NAS's own label (survives breaking the block). */
    @LuaFunction
    public final @Nullable String getLabel() {
        return nas.getLabel();
    }

    /** Set or clear (pass nothing) the NAS label, max 32 characters. */
    @LuaFunction(mainThread = true)
    public final void setLabel(Optional<String> label) {
        nas.setLabel(label.orElse(null));
    }
}
