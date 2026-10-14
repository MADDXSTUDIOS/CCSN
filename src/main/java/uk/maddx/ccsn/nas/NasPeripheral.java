package uk.maddx.ccsn.nas;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.CCSNConfig;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua side of the NAS. Peripheral type: "nas".
 *
 * When a computer attaches (directly or via a wired modem), the NAS's storage
 * is mounted into that computer's filesystem at /nas (or /nas2, /nas3... if
 * taken). Several computers can share one NAS at the same time.
 */
public final class NasPeripheral implements IPeripheral {
    public static final String TYPE = "nas";
    private static final int MAX_MOUNTS = 64;

    private final NasBlockEntity nas;
    private final Map<IComputerAccess, String> mounts = new ConcurrentHashMap<>();
    private @Nullable WritableMount mount;

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

    private synchronized @Nullable WritableMount mount() {
        if (mount == null) {
            Level level = nas.getLevel();
            MinecraftServer server = level == null ? null : level.getServer();
            if (server == null) return null;
            mount = ComputerCraftAPI.createSaveDirMount(
                server,
                "ccsn_nas/" + nas.getOrCreateId(),
                CCSNConfig.NAS_CAPACITY.get()
            );
        }
        return mount;
    }

    @Override
    public void attach(IComputerAccess computer) {
        WritableMount storage = mount();
        if (storage == null) return;
        // CC won't pick a free name for us, so number them like disk drives do:
        // nas, nas2, nas3...
        for (int i = 1; i <= MAX_MOUNTS; i++) {
            String path = computer.mountWritable(i == 1 ? "nas" : "nas" + i, storage);
            if (path != null) {
                mounts.put(computer, path);
                return;
            }
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        String path = mounts.remove(computer);
        if (path != null) computer.unmount(path);
    }

    // ---- Lua API ----

    /** Unique ID of this NAS. Stays the same if the block is broken and re-placed. */
    @LuaFunction
    public final String getId() {
        return nas.getOrCreateId().toString();
    }

    @LuaFunction
    public final @Nullable String getLabel() {
        return nas.getLabel();
    }

    /** Set or clear (pass nothing) the label, max 32 characters. */
    @LuaFunction(mainThread = true)
    public final void setLabel(Optional<String> label) {
        nas.setLabel(label.orElse(null));
    }

    /** Where the NAS is mounted on the calling computer, e.g. "nas". */
    @LuaFunction
    public final @Nullable String getMountPath(IComputerAccess computer) {
        return mounts.get(computer);
    }

    /** Total capacity in bytes. */
    @LuaFunction
    public final long getCapacity() {
        return CCSNConfig.NAS_CAPACITY.get();
    }

    /** Free space remaining in bytes. */
    @LuaFunction
    public final long getFreeSpace() throws LuaException {
        WritableMount storage = mount();
        if (storage == null) throw new LuaException("NAS storage is unavailable");
        try {
            return storage.getRemainingSpace();
        } catch (IOException e) {
            throw new LuaException("Could not read NAS free space: " + e.getMessage());
        }
    }
}
