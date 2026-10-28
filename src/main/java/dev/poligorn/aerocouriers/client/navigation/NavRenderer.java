package dev.poligorn.aerocouriers.client.navigation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.poligorn.aerocouriers.client.ClientZones;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Courier navigation: a vertical beam at every active zone (yellow pickup, blue delivery) + HUD compass for the
 * focused or nearest one (GDD §10.3, no mod dependencies). The beam is plain coloured geometry, no texture.
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

    public static void onRenderHud(RenderGuiEvent.Post event) {
        if (!CouriersClientConfig.navHudEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        if (mc.gui.getChat().isChatFocused() || mc.screen != null && mc.screen.isPauseScreen()) {
            return;
        }
        if (mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Optional<ZoneSyncPayload.Zone> target = pickTarget(mc);
        if (target.isEmpty()) {
            return;
        }
        ZoneSyncPayload.Zone zone = target.get();
        Vec3 player = mc.player.position();
        double dist;
        float bearing;
        boolean wrongDim = !zone.dimension().equals(mc.level.dimension());
        if (wrongDim) {
            dist = -1;
            bearing = 0;
        } else {
            Vec3 center = Vec3.atCenterOf(zone.pos());
            dist = NavMath.horizontalDistance(player.x, player.z, center.x, center.z);
            bearing = NavMath.bearingDegrees(player.x, player.z, center.x, center.z);
        }
        float yawDelta = wrongDim ? 0 : NavMath.yawDelta(mc.player.getYRot(), bearing);
        drawHud(event.getGuiGraphics(), mc, zone, dist, yawDelta, wrongDim);
    }

    private static Optional<ZoneSyncPayload.Zone> pickTarget(Minecraft mc) {
        List<ZoneSyncPayload.Zone> zones = ClientZones.get();
        if (zones.isEmpty()) {
            return Optional.empty();
        }
        var level = mc.level;
        if (level == null) {
            return Optional.empty();
        }
        var focus = ClientNavigation.focusedOrder();
        if (focus != null) {
            for (ZoneSyncPayload.Zone zone : zones) {
                if (zone.orderId().equals(focus)) {
                    return Optional.of(zone);
                }
            }
        }
        Vec3 player = mc.player.position();
        return zones.stream()
                .filter(z -> z.dimension().equals(level.dimension()))
                .min(Comparator.comparingDouble(z -> NavMath.horizontalDistanceSq(
                        player.x, player.z, z.pos().getX() + 0.5, z.pos().getZ() + 0.5)));
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

    private static void drawHud(GuiGraphics graphics, Minecraft mc, ZoneSyncPayload.Zone zone, double dist,
                                float yawDelta, boolean wrongDim) {
        int[] pos = CouriersClientConfig.navHudPosition(mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        int x = pos[0];
        int y = pos[1];
        int pad = 6;
        int boxW = 118;
        int boxH = 42;
        graphics.fill(x, y, x + boxW, y + boxH, 0xA0101010);
        graphics.fill(x, y, x + boxW, y + 1, 0xFF8B6914);

        Component id = Component.translatable("ui.aerocouriers.nav.order", zone.contract());
        graphics.drawString(mc.font, id, x + pad, y + pad, 0xFFE8D48B, false);

        String distText = wrongDim
                ? Component.translatable("ui.aerocouriers.nav.wrong_dimension").getString()
                : Component.translatable("ui.aerocouriers.nav.distance", (int) dist).getString();
        graphics.drawString(mc.font, distText, x + pad, y + pad + 10, 0xFFCCCCCC, false);

        int cx = x + boxW - 28;
        int cy = y + boxH / 2 + 2;
        drawCompassArrow(graphics, cx, cy, wrongDim ? 0 : yawDelta);
    }

    private static void drawCompassArrow(GuiGraphics graphics, int cx, int cy, float yawDeltaDeg) {
        RenderSystem.enableBlend();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(yawDeltaDeg));
        int size = 10;
        int color = 0xFFFFD060;
        graphics.fill(-1, -size, 1, 0, color);
        graphics.fill(-3, -size - 2, 3, -size, color);
        pose.popPose();
        RenderSystem.disableBlend();
    }

    /** Reserve space so the vanilla chat does not overlap the HUD (best-effort). */
    public static void onChatLayout(CustomizeGuiOverlayEvent.Chat event) {
        if (!CouriersClientConfig.navHudEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || ClientZones.get().isEmpty()) {
            return;
        }
        int[] pos = CouriersClientConfig.navHudPosition(mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        if (pos[1] > mc.getWindow().getGuiScaledHeight() / 2) {
            return;
        }
        int reserved = 48;
        if (event.getPosY() < pos[1] + reserved) {
            event.setPosY(pos[1] + reserved);
        }
    }
}
