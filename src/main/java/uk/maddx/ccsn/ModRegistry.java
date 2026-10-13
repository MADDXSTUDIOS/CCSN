package uk.maddx.ccsn;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.maddx.ccsn.nas.NasBlock;
import uk.maddx.ccsn.nas.NasBlockEntity;
import uk.maddx.ccsn.nas.NasItem;

import java.util.UUID;

import static uk.maddx.ccsn.CCSN.MOD_ID;

public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    // ---- Item data components (what a NAS item remembers when you break it) ----

    /** Which save folder this NAS's files live in. Survives breaking/placing the block. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> NAS_ID =
        COMPONENTS.register("nas_id", () -> DataComponentType.<UUID>builder()
            .persistent(UUIDUtil.CODEC)
            .networkSynchronized(UUIDUtil.STREAM_CODEC)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NAS_LABEL =
        COMPONENTS.register("nas_label", () -> DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            .build());

    // ---- NAS ----

    public static final DeferredBlock<NasBlock> NAS = BLOCKS.register("nas", () -> new NasBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.5f, 6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    ));

    public static final DeferredItem<NasItem> NAS_ITEM =
        ITEMS.register("nas", () -> new NasItem(NAS.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NasBlockEntity>> NAS_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("nas", () -> BlockEntityType.Builder.of(NasBlockEntity::new, NAS.get()).build(null));

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private ModRegistry() {
    }
}
