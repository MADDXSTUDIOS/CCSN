package uk.maddx.ccsn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.CCSN;
import uk.maddx.ccsn.network.CableLanes;
import uk.maddx.ccsn.network.CableRender;
import uk.maddx.ccsn.network.NetworkCableBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a network cable block from its {@link CableRender}: each run is a 1 px strand in its own
 * lane (see {@link CableLanes}), plus any tray or trunking round them. A disguised cable is drawn as
 * its disguise block instead. A strand's colour travels as the quad's tint index
 * ({@link #STRAND_TINT} + dye ordinal), which the block colour handler turns into the dye colour;
 * low tint indices are left for the disguise block's own tinting (grass...).
 */
public final class CableBakedModel implements IDynamicBakedModel {
    public static final int STRAND_TINT = 1000;
    private static final ResourceLocation[] COVER_TEXTURES = {
        null,
        ResourceLocation.fromNamespaceAndPath(CCSN.MOD_ID, "block/cable_tray"),
        ResourceLocation.fromNamespaceAndPath(CCSN.MOD_ID, "block/cable_rod"),
        ResourceLocation.fromNamespaceAndPath(CCSN.MOD_ID, "block/cable_trunking"),
    };
    /** Plenty for any build; past this the cache starts again rather than growing for ever. */
    private static final int CACHE_LIMIT = 2048;

    private final BakedModel original;
    private final Map<CableRender, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public CableBakedModel(BakedModel original) {
        this.original = original;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        BlockState disguise = data.get(NetworkCableBlockEntity.DISGUISE);
        if (disguise != null) {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(disguise);
            return model.getQuads(disguise, side, rand, ModelData.EMPTY, renderType);
        }
        if (side != null) return List.of();
        CableRender render = data.get(NetworkCableBlockEntity.RENDER);
        if (render == null || render.isEmpty()) return List.of();
        if (cache.size() > CACHE_LIMIT) cache.clear();
        return cache.computeIfAbsent(render.normalised(), this::build);
    }

    private List<BakedQuad> build(CableRender render) {
        List<BakedQuad> quads = new ArrayList<>();

        // The runs. Inside trunking they're only seen through an open end, so skip them if there's none.
        boolean hidden = render.cover() == CableLanes.COVER_TRUNKING && (render.union() & render.open()) == 0;
        if (!hidden) {
            TextureAtlasSprite strandSprite = original.getParticleIcon();
            for (DyeColor colour : render.present()) {
                int tint = STRAND_TINT + colour.ordinal();
                for (CableLanes.Box b : CableLanes.strand(colour, render.mask(colour), 0)) addBox(quads, strandSprite, b, tint);
            }
        }

        // Tray or trunking round them.
        List<CableLanes.Box> cover = CableLanes.cover(render);
        if (!cover.isEmpty()) {
            var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            for (CableLanes.Box b : cover) addBox(quads, atlas.apply(COVER_TEXTURES[b.tex()]), b, -1);
        }
        return List.copyOf(quads);
    }

    private static void addBox(List<BakedQuad> quads, TextureAtlasSprite sprite, CableLanes.Box b, int tint) {
        for (Direction face : Direction.values()) {
            if (!b.draws(face)) continue;
            quads.add(quad(sprite, face, b, tint, false));
            if (b.twoSided()) quads.add(quad(sprite, face, b, tint, true));
        }
    }

    /** One face of a box. {@code inside} draws it facing into the box instead (for hollow trunking). */
    private static BakedQuad quad(TextureAtlasSprite sprite, Direction face, CableLanes.Box b, int tint, boolean inside) {
        float x1 = b.x1(), y1 = b.y1(), z1 = b.z1(), x2 = b.x2(), y2 = b.y2(), z2 = b.z2();
        // Corner order matches vanilla's FaceInfo, so each face winds outwards.
        float[][] v = switch (face) {
            case DOWN -> new float[][] { { x1, y1, z2 }, { x1, y1, z1 }, { x2, y1, z1 }, { x2, y1, z2 } };
            case UP -> new float[][] { { x1, y2, z1 }, { x1, y2, z2 }, { x2, y2, z2 }, { x2, y2, z1 } };
            case NORTH -> new float[][] { { x2, y2, z1 }, { x2, y1, z1 }, { x1, y1, z1 }, { x1, y2, z1 } };
            case SOUTH -> new float[][] { { x1, y2, z2 }, { x1, y1, z2 }, { x2, y1, z2 }, { x2, y2, z2 } };
            case WEST -> new float[][] { { x1, y2, z1 }, { x1, y1, z1 }, { x1, y1, z2 }, { x1, y2, z2 } };
            case EAST -> new float[][] { { x2, y2, z2 }, { x2, y1, z2 }, { x2, y1, z1 }, { x2, y2, z1 } };
        };
        Direction facing = inside ? face.getOpposite() : face;
        int normal = packNormal(facing);
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            float[] p = v[inside ? 3 - i : i]; // reversed winding faces the other way
            float x = p[0], y = p[1], z = p[2];
            // Texture coordinates from position, like vanilla's automatic UVs (0..16).
            float[] uv = switch (face) {
                case DOWN -> new float[] { x, 16 - z };
                case UP -> new float[] { x, z };
                case NORTH -> new float[] { 16 - x, 16 - y };
                case SOUTH -> new float[] { x, 16 - y };
                case WEST -> new float[] { z, 16 - y };
                case EAST -> new float[] { 16 - z, 16 - y };
            };
            float u = uv[0], w = uv[1];
            int o = i * 8;
            data[o] = Float.floatToRawIntBits(x / 16);
            data[o + 1] = Float.floatToRawIntBits(y / 16);
            data[o + 2] = Float.floatToRawIntBits(z / 16);
            data[o + 3] = -1; // vertex colour: white
            data[o + 4] = Float.floatToRawIntBits(sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u / 16);
            data[o + 5] = Float.floatToRawIntBits(sprite.getV0() + (sprite.getV1() - sprite.getV0()) * w / 16);
            data[o + 6] = 0; // lightmap, filled in by the renderer
            data[o + 7] = normal;
        }
        return new BakedQuad(data, tint, facing, sprite, true);
    }

    private static int packNormal(Direction face) {
        int x = (byte) (face.getStepX() * 127), y = (byte) (face.getStepY() * 127), z = (byte) (face.getStepZ() * 127);
        return (x & 0xFF) | ((y & 0xFF) << 8) | ((z & 0xFF) << 16);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return original.getParticleIcon();
    }

    /** Breaking a disguised cable gives the disguise block's particles. */
    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState disguise = data.get(NetworkCableBlockEntity.DISGUISE);
        if (disguise != null) return Minecraft.getInstance().getBlockRenderer().getBlockModel(disguise).getParticleIcon(ModelData.EMPTY);
        return original.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return original.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
