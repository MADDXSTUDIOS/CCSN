package uk.maddx.ccsn;

import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.maddx.ccsn.drive.DriveItem;
import uk.maddx.ccsn.nas.NasBlock;
import uk.maddx.ccsn.nas.NasBlockEntity;
import uk.maddx.ccsn.nas.NasItem;
import uk.maddx.ccsn.nas.NasMenu;
import uk.maddx.ccsn.network.NetworkCableBlock;
import uk.maddx.ccsn.network.NetworkCableBlockEntity;
import uk.maddx.ccsn.network.NetworkCableItem;
import uk.maddx.ccsn.network.WallJackBlock;
import uk.maddx.ccsn.network.WallJackBlockEntity;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static uk.maddx.ccsn.CCSN.MOD_ID;

public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    // ---- Item data components ----

    /** Which save folder a drive's files live in. Assigned the first time it goes in a NAS. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> DRIVE_ID =
        COMPONENTS.register("drive_id", () -> DataComponentType.<UUID>builder()
            .persistent(UUIDUtil.CODEC)
            .networkSynchronized(UUIDUtil.STREAM_CODEC)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DRIVE_LABEL =
        COMPONENTS.register("drive_label", () -> DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NAS_LABEL =
        COMPONENTS.register("nas_label", () -> DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            .build());

    /** v0.1 leftover. Kept registered so old test items still load; no longer used. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> NAS_ID =
        COMPONENTS.register("nas_id", () -> DataComponentType.<UUID>builder()
            .persistent(UUIDUtil.CODEC)
            .networkSynchronized(UUIDUtil.STREAM_CODEC)
            .build());

    // ---- Chipsets (crafting ingredients for every tiered item) ----

    public static final DeferredItem<Item> CHIPSET_T1 = ITEMS.registerSimpleItem("chipset_t1");
    public static final DeferredItem<Item> CHIPSET_T2 = ITEMS.registerSimpleItem("chipset_t2");
    public static final DeferredItem<Item> CHIPSET_T3 =
        ITEMS.registerSimpleItem("chipset_t3", new Item.Properties().rarity(Rarity.UNCOMMON));

    // ---- Drives ----

    public static final DeferredItem<DriveItem> DRIVE_T1 =
        ITEMS.register("drive_t1", () -> new DriveItem(1, new Item.Properties()));
    public static final DeferredItem<DriveItem> DRIVE_T2 =
        ITEMS.register("drive_t2", () -> new DriveItem(2, new Item.Properties()));
    public static final DeferredItem<DriveItem> DRIVE_T3 =
        ITEMS.register("drive_t3", () -> new DriveItem(3, new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---- NAS ----

    public static final DeferredBlock<NasBlock> NAS = BLOCKS.register("nas", () -> new NasBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.5f, 6.0f)
            .sound(SoundType.METAL)
    ));

    public static final DeferredItem<NasItem> NAS_ITEM =
        ITEMS.register("nas", () -> new NasItem(NAS.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NasBlockEntity>> NAS_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("nas", () -> BlockEntityType.Builder.of(NasBlockEntity::new, NAS.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<NasMenu>> NAS_MENU =
        MENUS.register("nas", () -> new MenuType<>(NasMenu::new, FeatureFlags.VANILLA_SET));

    // ---- Networking ----

    public static final DeferredBlock<NetworkCableBlock> NETWORK_CABLE = BLOCKS.register("network_cable", () -> new NetworkCableBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOL)
            .strength(0.8f)
            .sound(SoundType.WOOL)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY)
    ));

    /** One item per dye colour, all placing {@link #NETWORK_CABLE}. */
    public static final Map<DyeColor, DeferredItem<NetworkCableItem>> NETWORK_CABLE_ITEMS =
        Util.make(new EnumMap<>(DyeColor.class), items -> {
            for (DyeColor colour : DyeColor.values()) {
                items.put(colour, ITEMS.register("network_cable_" + colour.getSerializedName(),
                    () -> new NetworkCableItem(NETWORK_CABLE.get(), colour, new Item.Properties())));
            }
        });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkCableBlockEntity>> NETWORK_CABLE_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("network_cable",
            () -> BlockEntityType.Builder.of(NetworkCableBlockEntity::new, NETWORK_CABLE.get()).build(null));

    public static final DeferredBlock<WallJackBlock> WALL_JACK = BLOCKS.register("wall_jack", () -> new WallJackBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.QUARTZ)
            .strength(0.8f)
            .sound(SoundType.STONE)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY)
    ));

    public static final DeferredItem<BlockItem> WALL_JACK_ITEM =
        ITEMS.register("wall_jack", () -> new BlockItem(WALL_JACK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WallJackBlockEntity>> WALL_JACK_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("wall_jack",
            () -> BlockEntityType.Builder.of(WallJackBlockEntity::new, WALL_JACK.get()).build(null));

    // ---- Creative tab ----

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ccsn"))
            .icon(() -> NAS_ITEM.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(NAS_ITEM.get());
                output.accept(DRIVE_T1.get());
                output.accept(DRIVE_T2.get());
                output.accept(DRIVE_T3.get());
                output.accept(CHIPSET_T1.get());
                output.accept(CHIPSET_T2.get());
                output.accept(CHIPSET_T3.get());
                output.accept(WALL_JACK_ITEM.get());
                for (DyeColor colour : DyeColor.values()) output.accept(NETWORK_CABLE_ITEMS.get(colour).get());
            })
            .build());

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    private ModRegistry() {
    }
}
