package uk.maddx.ccsn;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(CCSN.MOD_ID)
public final class CCSN {
    public static final String MOD_ID = "ccsn";

    public CCSN(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, CCSNConfig.SPEC);

        modBus.addListener(CCSN::registerCapabilities);
    }

    /** Exposes our block entities to CC:Tweaked as peripherals. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            ModRegistry.NAS_BLOCK_ENTITY.get(),
            (nas, side) -> nas.peripheral()
        );
    }
}
