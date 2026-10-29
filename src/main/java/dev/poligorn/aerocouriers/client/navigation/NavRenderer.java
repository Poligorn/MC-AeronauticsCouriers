package dev.poligorn.aerocouriers.client.navigation;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.poligorn.aerocouriers.client.ClientZones;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Vertical beam at every active haul zone. Pickup and delivery share one prism (core + glow); only the RGB
 * changes. Yellow {@code #FFFF55} is pickup, aqua {@code #55FFFF} is delivery. Map pins are Xaero's.
 */
public final class NavRenderer {
    private static final RenderType BEAM = RenderType.create("aerocouriers_nav_beam",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final float CORE_HALF = 0.18f;
    private static final float GLOW_HALF = 0.42f;
    /** Far beams are widened so they keep roughly the same on-screen width instead of shrinking to a pixel. */
    private static final float WIDEN_PER_BLOCK = 0.0045f;

    private NavRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!CouriersClientConfig.navBeamEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        List<ZoneSyncPayload.Zone> zones = ClientZones.get();
        if (zones.isEmpty()) {
            return;
        }
        double maxDist = CouriersClientConfig.NAV_BEAM_RENDER_DISTANCE.get();
        float height = CouriersClientConfig.NAV_BEAM_HEIGHT.get().floatValue();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(BEAM);
        float pulse = 0.85f + 0.15f * Mth.sin((System.nanoTime() / 1_000_000_000f) * 2.4f);
        boolean any = false;
        for (ZoneSyncPayload.Zone zone : zones) {
            if (!zone.dimension().equals(mc.level.dimension())) {
                continue;
            }
            Vec3 base = new Vec3(zone.pos().getX() + 0.5, zone.pos().getY(), zone.pos().getZ() + 0.5);
            double dist = NavMath.horizontalDistance(eye.x, eye.z, base.x, base.z);
            if (dist > maxDist) {
                continue;
            }
            float[] rgb = CouriersClientConfig.navBeamColorRgb(zone.delivery());
            float alpha = beamAlpha(dist);
            float widen = 1f + (float) dist * WIDEN_PER_BLOCK / CORE_HALF;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(base.x - eye.x, base.y - eye.y, base.z - eye.z);
            Matrix4f matrix = pose.last().pose();
            prism(consumer, matrix, GLOW_HALF * widen, height, rgb, alpha * 0.28f * pulse);
            prism(consumer, matrix, CORE_HALF * widen, height, rgb, alpha);
            pose.popPose();
            any = true;
        }
        if (any) {
            buffers.endBatch(BEAM);
        }
    }

    private static float beamAlpha(double distance) {
        double fadeStart = CouriersClientConfig.NAV_BEAM_FADE_START.get();
        if (distance >= fadeStart) {
            return 0.9f;
        }
        return (float) Mth.clamp(distance / fadeStart, 0.15, 0.9);
    }

    /** Four side faces of a square column from y = 0 to {@code height}; colour fades out towards the top. */
    private static void prism(VertexConsumer consumer, Matrix4f matrix, float half, float height, float[] rgb,
                              float alpha) {
        int r = (int) (rgb[0] * 255);
        int g = (int) (rgb[1] * 255);
        int b = (int) (rgb[2] * 255);
        int a0 = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
        int a1 = a0 / 4;
        float[][] corners = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
        for (int i = 0; i < 4; i++) {
            float[] c0 = corners[i];
            float[] c1 = corners[(i + 1) % 4];
            consumer.addVertex(matrix, c0[0], 0, c0[1]).setColor(r, g, b, a0);
            consumer.addVertex(matrix, c0[0], height, c0[1]).setColor(r, g, b, a1);
            consumer.addVertex(matrix, c1[0], height, c1[1]).setColor(r, g, b, a1);
            consumer.addVertex(matrix, c1[0], 0, c1[1]).setColor(r, g, b, a0);
        }
    }
}
