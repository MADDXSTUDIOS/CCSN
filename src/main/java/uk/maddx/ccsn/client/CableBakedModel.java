package uk.maddx.ccsn.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import uk.maddx.ccsn.network.CableLanes;
import uk.maddx.ccsn.network.NetworkCableBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a network cable block from its render key: each run is a 2 px strand in its own lane,
 * with a core and an arm out to every side it connects on. The colour travels as the quad's
 * tint index (dye ordinal + 1), which the block colour handler turns into the dye colour.
 */
public final class CableBakedModel implements IDynamicBakedModel {
    private final BakedModel original;
    private final Map<Long, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public CableBakedModel(BakedModel original) {
        this.original = original;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (side != null) return List.of();
        Long key = data.get(NetworkCableBlockEntity.RENDER_KEY);
        if (key == null || CableLanes.colours(key) == 0) return List.of();
        return cache.computeIfAbsent(key, this::build);
    }

    private List<BakedQuad> build(long key) {
        TextureAtlasSprite sprite = original.getParticleIcon();
        List<BakedQuad> quads = new ArrayList<>();
        for (DyeColor colour : CableLanes.present(key)) {
            int tint = colour.ordinal() + 1;
            for (CableLanes.Box b : CableLanes.strand(colour, CableLanes.mask(key, colour), CableLanes.HALF)) {
                for (Direction face : Direction.values()) {
                    // An arm's two ends are hidden: one inside the core, one against the next block.
                    if (b.arm() != null && (face == b.arm() || face == b.arm().getOpposite())) continue;
                    quads.add(quad(sprite, face, b, tint));
                }
            }
        }
        return List.copyOf(quads);
    }

    private static BakedQuad quad(TextureAtlasSprite sprite, Direction face, CableLanes.Box b, int tint) {
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
        int normal = packNormal(face);
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            float x = v[i][0], y = v[i][1], z = v[i][2];
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
        return new BakedQuad(data, tint, face, sprite, true);
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

    @Override
    public ItemTransforms getTransforms() {
        return original.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
