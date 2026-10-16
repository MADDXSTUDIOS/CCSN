package uk.maddx.ccsn.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import uk.maddx.ccsn.CCSN;
import uk.maddx.ccsn.ModRegistry;
import uk.maddx.ccsn.network.CableLanes;
import uk.maddx.ccsn.network.CableRender;
import uk.maddx.ccsn.network.NetworkCableBlockEntity;

import java.util.OptionalDouble;

/**
 * Holding the Network Wrench (either hand) shows every cable run nearby as coloured outlines, drawn
 * through walls, floors and disguises, so you can see where your runs actually go.
 */
@EventBusSubscriber(modid = CCSN.MOD_ID, value = Dist.CLIENT)
public final class WrenchXray {
    private static final int RADIUS = 24;
    /** Black runs still need to show up against dark blocks. */
    private static final int BLACK_LINE = 0xFF6A6A78;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !holdingWrench(player)) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(XrayLines.TYPE);

        BlockPos centre = player.blockPosition();
        int chunkX = centre.getX() >> 4, chunkZ = centre.getZ() >> 4, reach = (RADIUS >> 4) + 1;
        for (int x = chunkX - reach; x <= chunkX + reach; x++) {
            for (int z = chunkZ - reach; z <= chunkZ + reach; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof NetworkCableBlockEntity cable)) continue;
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(centre) > RADIUS * RADIUS) continue;
                    draw(pose, lines, pos, cable.render());
                }
            }
        }

        pose.popPose();
        buffers.endBatch(XrayLines.TYPE);
    }

    private static void draw(PoseStack pose, VertexConsumer lines, BlockPos pos, CableRender render) {
        for (DyeColor colour : render.present()) {
            int argb = colour == DyeColor.BLACK ? BLACK_LINE : CCSNClient.tint(colour);
            float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
            for (CableLanes.Box box : CableLanes.strand(colour, render.mask(colour), 0)) {
                LevelRenderer.renderLineBox(pose, lines,
                    pos.getX() + box.x1() / 16, pos.getY() + box.y1() / 16, pos.getZ() + box.z1() / 16,
                    pos.getX() + box.x2() / 16, pos.getY() + box.y2() / 16, pos.getZ() + box.z2() / 16,
                    r, g, b, 1.0f);
            }
        }
    }

    private static boolean holdingWrench(Player player) {
        return player.getMainHandItem().is(ModRegistry.WRENCH.get()) || player.getOffhandItem().is(ModRegistry.WRENCH.get());
    }

    /** Vanilla's line type, minus the depth test, so lines show through blocks. */
    private static final class XrayLines extends RenderType {
        static final RenderType TYPE = create("ccsn_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 1536, false, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setOutputState(MAIN_TARGET)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .createCompositeState(false));

        private XrayLines(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }

    private WrenchXray() {
    }
}
