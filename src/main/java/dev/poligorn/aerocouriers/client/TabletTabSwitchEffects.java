package dev.poligorn.aerocouriers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** Tab switch is instant; open/close tablet animation lives in {@link TabletScreenChrome}. */
public final class TabletTabSwitchEffects {

    public void onTabSwitch(AbstractWidget previous, AbstractWidget next) {
    }

    public boolean isTabLoadingActive() {
        return false;
    }

    public float contentAlpha() {
        return 1.0F;
    }

    public void drawHighlight(GuiGraphics graphics, int tabTop, int tabHeight) {
    }
}
