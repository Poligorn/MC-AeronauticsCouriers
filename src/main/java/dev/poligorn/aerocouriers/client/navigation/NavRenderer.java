package dev.poligorn.aerocouriers.client.navigation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Courier navigation: vertical beacon beam at the active zone + HUD compass (GDD §10.3, no mod dependencies).
 */
public final class NavRenderer {
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");

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
        Optional<ZoneSyncPayload.Zone> target = pickTarget(mc);
        if (target.isEmpty()) {
            return;
        }
        ZoneSyncPayload.Zone zone = target.get();
        if (!zone.dimension().equals(mc.level.dimension())) {
            return;
        }
        double maxDist = CouriersClientConfig.NAV_BEAM_RENDER_DISTANCE.get();
        Vec3 eye = event.getCamera().getPosition();
        Vec3 center = Vec3.atCenterOf(zone.pos());
        double dist = eye.distanceTo(center);
        if (dist > maxDist) {
            return;
        }
        float[] rgb = CouriersClientConfig.navBeamColorRgb();
        float alpha = beamAlpha(dist);
        float height = CouriersClientConfig.NAV_BEAM_HEIGHT.get().floatValue();
        renderBeam(event.getPoseStack(), event.getCamera(), center, height, rgb, alpha);
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
            return 0.85f;
        }
        return (float) Mth.clamp(distance / fadeStart, 0.15, 0.85);
    }

    private static void renderBeam(PoseStack pose, Camera camera, Vec3 center, float height, float[] rgb,
                                   float alpha) {
        pose.pushPose();
        pose.translate(center.x - camera.getPosition().x, center.y - camera.getPosition().y,
                center.z - camera.getPosition().z);
        float half = 0.2f;
        float time = (System.nanoTime() / 1_000_000_000f) % 1f;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.beaconBeam(BEAM_TEXTURE, false));
        int r = (int) (rgb[0] * 255);
        int g = (int) (rgb[1] * 255);
        int b = (int) (rgb[2] * 255);
        int a = (int) (alpha * 255);
        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 1f : 0.4f;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * 360f + layer * 90f));
            drawBeamQuad(pose.last().pose(), consumer, half * scale, height, r, g, b, a);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
    }

    private static void drawBeamQuad(Matrix4f matrix, VertexConsumer consumer, float half, float height,
                                     int r, int g, int b, int a) {
        consumer.addVertex(matrix, -half, 0, -half).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, -half, height, -half).setColor(r, g, b, a).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, half, height, -half).setColor(r, g, b, a).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, half, 0, -half).setColor(r, g, b, a).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);

        consumer.addVertex(matrix, half, 0, half).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, half, height, half).setColor(r, g, b, a).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, -half, height, half).setColor(r, g, b, a).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, -half, 0, half).setColor(r, g, b, a).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
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
