package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Real-time tablet UI tweens ({@link Util#getMillis()}, ease-out cubic). */
public final class TabletAnim {
    private TabletAnim() {
    }

    public static boolean enabled() {
        return CouriersClientConfig.uiAnimations();
    }

    public static float easeOutCubic(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    /** Returns {@code 1} when animations are off or the tween has finished. */
    public static float progress(long startMs, long durationMs) {
        if (!enabled() || durationMs <= 0L) {
            return 1.0F;
        }
        float raw = (Util.getMillis() - startMs) / (float) durationMs;
        return easeOutCubic(Mth.clamp(raw, 0.0F, 1.0F));
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    public static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return argb & 0x00FFFFFF | a << 24;
    }

    public static void pushAlpha(GuiGraphics graphics, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
    }

    public static void popAlpha() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void withAlpha(GuiGraphics graphics, float alpha, Runnable draw) {
        if (!enabled() || alpha >= 0.999F) {
            draw.run();
            return;
        }
        pushAlpha(graphics, alpha);
        try {
            draw.run();
        } finally {
            popAlpha();
        }
    }

    public static void withTransform(GuiGraphics graphics, float translateX, float translateY, float alpha, Runnable draw) {
        if (!enabled() && translateX == 0.0F && translateY == 0.0F && alpha >= 0.999F) {
            draw.run();
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(translateX, translateY, 0.0F);
        pushAlpha(graphics, alpha);
        try {
            draw.run();
        } finally {
            popAlpha();
            graphics.pose().popPose();
        }
    }
}
