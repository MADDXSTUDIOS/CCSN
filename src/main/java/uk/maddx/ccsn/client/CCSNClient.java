package uk.maddx.ccsn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import uk.maddx.ccsn.CCSN;
import uk.maddx.ccsn.ModRegistry;
import uk.maddx.ccsn.network.NetworkCableBlockEntity;
import uk.maddx.ccsn.network.NetworkCableItem;
import uk.maddx.ccsn.network.WallJackBlock;

import java.util.EnumMap;
import java.util.Map;

/** Client-only setup. */
@EventBusSubscriber(modid = CCSN.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CCSNClient {
    /** Cable tints: vanilla dye colours. Alpha set so tints never come out invisible. */
    private static final Map<DyeColor, Integer> TINT = new EnumMap<>(DyeColor.class);
    private static final int UNLINKED = 0xFF5A5E66;

    static {
        int[] rgb = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21,
        };
        for (DyeColor colour : DyeColor.values()) TINT.put(colour, 0xFF000000 | rgb[colour.ordinal()]);
    }

    public static int tint(DyeColor colour) {
        return TINT.get(colour);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.NAS_MENU.get(), NasScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.NETWORK_SWITCH_BLOCK_ENTITY.get(), SwitchRenderer::new);
    }

    @SubscribeEvent
    public static void registerBlockColours(RegisterColorHandlersEvent.Block event) {
        // Cable strands carry their colour as tint index (STRAND_TINT + ordinal); see CableBakedModel.
        // Lower tint indices belong to a disguise block (grass, say): ask that block's own colour handler.
        event.register((state, level, pos, tintIndex) -> {
            int strand = tintIndex - CableBakedModel.STRAND_TINT;
            if (strand >= 0 && strand < 16) return tint(DyeColor.byId(strand));
            if (tintIndex >= 0 && level != null && pos != null
                && level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable && cable.disguise() != null) {
                return Minecraft.getInstance().getBlockColors().getColor(cable.disguise(), level, pos, tintIndex);
            }
            return -1;
        }, ModRegistry.NETWORK_CABLE.get());

        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) return -1;
            DyeColor colour = state.getValue(WallJackBlock.LINK).colour();
            return colour == null ? UNLINKED : tint(colour);
        }, ModRegistry.WALL_JACK.get());
    }

    /** Network cable is drawn from its block entity's data, not a fixed model. */
    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (BlockState state : ModRegistry.NETWORK_CABLE.get().getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = event.getModels().get(location);
            if (original != null) event.getModels().put(location, new CableBakedModel(original));
        }
    }

    @SubscribeEvent
    public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
        for (var item : ModRegistry.NETWORK_CABLE_ITEMS.values()) {
            event.register((stack, tintIndex) ->
                tintIndex == 0 && stack.getItem() instanceof NetworkCableItem cable ? tint(cable.colour()) : -1, item.get());
        }
        event.register((stack, tintIndex) -> tintIndex == 0 ? UNLINKED : -1, ModRegistry.WALL_JACK_ITEM.get());
    }

    private CCSNClient() {
    }
}
