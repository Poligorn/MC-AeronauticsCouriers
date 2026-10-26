package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Inventory-style profile preview with clamped look angles and compressed Z depth so large scales
 * do not clip through the GUI orthographic near plane when the head pitches.
 */
final class ProfileSkinRenderer {
    /** GUI depth of the entity anchor (vanilla inventory uses {@code 50}, too shallow at scale ~200). */
    private static final double ANCHOR_Z = 150.0;
    /** Flattens depth extent; orthographic GUI so the silhouette is unchanged. */
    private static final float Z_SCALE_FACTOR = 0.25F;
    private static final float MOUSE_DELTA_DIVISOR = 40.0F;
    /**
     * Vanilla multiplies {@link #angleXFromMouse} by 40 (degrees) for body yaw and
     * {@link #angleYFromMouse} by 20 for pitch — clamp atan inputs to ±1 rad equivalent.
     */
    static final float MAX_ANGLE_COMPONENT = 1.0F;

    private ProfileSkinRenderer() {
    }

    static float angleXFromMouse(float anchorX, float mouseX) {
        return clampAngleX((float) Math.atan((anchorX - mouseX) / MOUSE_DELTA_DIVISOR));
    }

    static float angleYFromMouse(float anchorY, float mouseY) {
        return clampAngleY((float) Math.atan((anchorY - mouseY) / MOUSE_DELTA_DIVISOR));
    }

    static float clampAngleX(float angleComponent) {
        return Mth.clamp(angleComponent, -MAX_ANGLE_COMPONENT, MAX_ANGLE_COMPONENT);
    }

    static float clampAngleY(float angleComponent) {
        return Mth.clamp(angleComponent, -MAX_ANGLE_COMPONENT, MAX_ANGLE_COMPONENT);
    }

    /** Max body yaw offset in degrees ({@code angleX * 40}). */
    static float maxYawDegrees() {
        return MAX_ANGLE_COMPONENT * 40.0F;
    }

    /** Max pitch in degrees ({@code angleY * 20}). */
    static float maxPitchDegrees() {
        return MAX_ANGLE_COMPONENT * 20.0F;
    }

    static void render(
            GuiGraphics graphics,
            float screenX,
            float screenY,
            float scale,
            Vector3f modelTranslate,
            Quaternionf pose,
            Quaternionf cameraOrientation,
            LivingEntity entity) {
        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, ANCHOR_Z);
        graphics.pose().scale(scale, scale, -scale * Z_SCALE_FACTOR);
        graphics.pose().translate(modelTranslate.x, modelTranslate.y, modelTranslate.z);
        graphics.pose().mulPose(pose);
        Lighting.setupForEntityInInventory();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (cameraOrientation != null) {
            dispatcher.overrideCameraOrientation(
                    cameraOrientation.conjugate(new Quaternionf()).rotateY((float) Math.PI));
        }
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(
                entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, graphics.pose(), graphics.bufferSource(), 15728880));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
