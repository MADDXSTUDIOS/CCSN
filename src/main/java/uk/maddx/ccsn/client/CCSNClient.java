package uk.maddx.ccsn.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import uk.maddx.ccsn.CCSN;
import uk.maddx.ccsn.ModRegistry;

/** Client-only setup. */
@EventBusSubscriber(modid = CCSN.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CCSNClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.NAS_MENU.get(), NasScreen::new);
    }

    private CCSNClient() {
    }
}
