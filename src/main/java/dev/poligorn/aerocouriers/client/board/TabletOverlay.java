package dev.poligorn.aerocouriers.client.board;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Modal window drawn inside the tablet cavity over the dimmed board. Layout is in local mockup pixels; at GUI scales
 * where it does not fit the window is scaled down as a whole (mouse coordinates are mapped back).
 */
public abstract class TabletOverlay {
    private int originX;
    private int originY;
    private float scale = 1.0F;

    protected abstract int width();

    protected abstract int height();

    /** Centres the window in the given area (cavity content box). */
    public void layout(int areaX, int areaY, int areaW, int areaH) {
        int w = width() + 2;
        int h = height() + 2;
        scale = Math.min(1.0F, Math.min(areaW / (float) w, areaH / (float) h));
        originX = Math.round(areaX + (areaW - width() * scale) / 2.0F);
        originY = Math.round(areaY + (areaH - height() * scale) / 2.0F);
    }

    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 200.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderLocal(graphics, localX(mouseX), localY(mouseY), partialTick);
        graphics.pose().popPose();
    }

    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickLocal(localX(mouseX), localY(mouseY), button);
    }

    public boolean containsScreen(double mouseX, double mouseY) {
        int lx = localX(mouseX);
        int ly = localY(mouseY);
        return lx >= 0 && ly >= 0 && lx < width() && ly < height();
    }

    protected int localX(double screenX) {
        return (int) Math.floor((screenX - originX) / scale);
    }

    protected int localY(double screenY) {
        return (int) Math.floor((screenY - originY) / scale);
    }

    /** Screen position of a local point (tooltips are drawn in screen space). */
    public int screenX(int localX) {
        return Math.round(originX + localX * scale);
    }

    public int screenY(int localY) {
        return Math.round(originY + localY * scale);
    }

    protected abstract void renderLocal(GuiGraphics graphics, int mx, int my, float partialTick);

    protected abstract boolean clickLocal(int mx, int my, int button);

    protected static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
