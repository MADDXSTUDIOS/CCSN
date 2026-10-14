package uk.maddx.ccsn;

import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import uk.maddx.ccsn.network.NetworkScheduler;

@Mod(CCSN.MOD_ID)
public final class CCSN {
    public static final String MOD_ID = "ccsn";

    public CCSN(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, CCSNConfig.SPEC);

        modBus.addListener(CCSN::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(NetworkScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(NetworkScheduler::onServerStopped);
    }

    /** Exposes our block entities to CC:Tweaked. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            ModRegistry.NAS_BLOCK_ENTITY.get(),
            (nas, side) -> nas.peripheral()
        );

        // Only the wall jack's front is visible to CC (grey cable, wired modems).
        // Network cable is deliberately not registered: CC can't see it.
        event.registerBlockEntity(
            WiredElementCapability.get(),
            ModRegistry.WALL_JACK_BLOCK_ENTITY.get(),
            (jack, side) -> jack.wiredElement(side)
        );
    }
}
