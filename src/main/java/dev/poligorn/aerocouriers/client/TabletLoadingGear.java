package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Brass Create-style gear drawn while tablet input is blocked. */
final class TabletLoadingGear {
    private static final int SIZE = 20;
    private static final float ROTATION_SPEED = 0.004F;
    private static final long FADE_MS = 150L;

    private long fadeOutSince = Long.MIN_VALUE;

    float alpha(boolean inputBlocked) {
        if (!TabletAnim.enabled()) {
            return 0.0F;
        }
        if (inputBlocked) {
            fadeOutSince = Long.MIN_VALUE;
            return 1.0F;
        }
        if (fadeOutSince == Long.MIN_VALUE) {
            fadeOutSince = Util.getMillis();
        }
        float p = (Util.getMillis() - fadeOutSince) / (float) FADE_MS;
        return 1.0F - Mth.clamp(p, 0.0F, 1.0F);
    }

    void render(GuiGraphics graphics, int cavityX, int cavityY, int cavityW, int cavityH,
                float alpha, float partialTick) {
        if (alpha <= 0.001F) {
            return;
        }
        float cx = cavityX + cavityW * 0.5F;
        float cy = cavityY + cavityH * 0.5F;
        float angle = (Util.getMillis() + partialTick) * ROTATION_SPEED;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotation(angle));
        graphics.pose().translate(-SIZE * 0.5F, -SIZE * 0.5F, 0.0F);
        TabletAnim.pushAlpha(graphics, alpha);
        try {
            graphics.blitSprite(AerocouriersGuiSprites.TABLET_LOADING_GEAR, 0, 0, SIZE, SIZE);
        } finally {
            TabletAnim.popAlpha();
            graphics.pose().popPose();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
