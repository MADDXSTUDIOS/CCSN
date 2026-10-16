package uk.maddx.ccsn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import uk.maddx.ccsn.CCSN;
import uk.maddx.ccsn.network.NetworkSwitchBlockEntity;
import uk.maddx.ccsn.network.SwitchPorts;

/**
 * The switch's port lights. Each port has two: a link light in the port's colour, lit while a run
 * is plugged in, and an amber activity light that flickers while messages cross the network.
 * Drawn full-bright just in front of the panel; the panel texture shows them dark when off.
 */
public final class SwitchRenderer implements BlockEntityRenderer<NetworkSwitchBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(CCSN.MOD_ID, "textures/block/switch_led.png");
    private static final int ACTIVITY = 0xFFFFB020;
    /** Black is a run colour too; its light still has to show up. */
    private static final int BLACK_LIGHT = 0xFF5A5A66;

    public SwitchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NetworkSwitchBlockEntity sw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        int ports = sw.ports();
        Level level = sw.getLevel();
        if (ports == 0 || level == null) return;
        long time = level.getGameTime();
        boolean busy = sw.busy(time);

        pose.pushPose();
        // Work as if the panel faces north (z = 0), then turn to the real facing.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - sw.facing().toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose last = pose.last();

        for (int port = 0; port < SwitchPorts.COUNT; port++) {
            if ((ports & (1 << port)) == 0) continue;
            int col = SwitchPorts.ledColumn(port), row = SwitchPorts.ledRow(port);
            int link = port == 15 ? BLACK_LIGHT : CCSNClient.tint(SwitchPorts.colour(port));
            led(out, last, col, row, link);
            if (busy && flicker(port, time)) led(out, last, col + 1, row, ACTIVITY);
        }
        pose.popPose();
    }

    /** Mostly on, blinking off now and then, differently per port, like real activity lights. */
    private static boolean flicker(int port, long time) {
        long h = (time / 2) * 0x9E3779B97F4A7C15L + port * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        return (h & 3) != 0;
    }

    /** One pixel-sized light at texture column/row (as you look at the panel), just in front of it. */
    private static void led(VertexConsumer out, PoseStack.Pose pose, int col, int row, int argb) {
        // Looking at a north face, texture left is +x: column c covers x 15-c .. 16-c.
        float x1 = (15 - col) / 16f, x2 = (16 - col) / 16f;
        float y1 = (15 - row) / 16f, y2 = (16 - row) / 16f;
        float z = -0.002f;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        vertex(out, pose, x2, y2, z, r, g, b, 0, 0);
        vertex(out, pose, x2, y1, z, r, g, b, 0, 1);
        vertex(out, pose, x1, y1, z, r, g, b, 1, 1);
        vertex(out, pose, x1, y2, z, r, g, b, 1, 0);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                               int r, int g, int b, float u, float v) {
        out.addVertex(pose, x, y, z)
            .setColor(r, g, b, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 0, -1);
    }
}
