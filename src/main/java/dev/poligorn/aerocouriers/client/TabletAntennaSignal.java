package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/** Client-only bracket pulse from the tablet antenna tip after a successful order accept. */
public final class TabletAntennaSignal {
    /** Total pulse length when {@link TabletAnim#enabled()}. */
    public static final long DURATION_MS = 700L;
    private static final long LAMP_FLASH_MS = 160L;
    private static final int SIGNAL_W = 56;
    private static final int SIGNAL_H = 14;

    private static long triggeredAt = Long.MIN_VALUE;

    private TabletAntennaSignal() {
    }

    public static void trigger() {
        triggeredAt = Util.getMillis();
    }

    public static void render(GuiGraphics graphics, int frameX, int frameY) {
        if (triggeredAt == Long.MIN_VALUE) {
            return;
        }
        long elapsed = Util.getMillis() - triggeredAt;
        if (elapsed > DURATION_MS && CouriersClientConfig.uiAnimations()) {
            return;
        }
        if (!CouriersClientConfig.uiAnimations() && elapsed > LAMP_FLASH_MS) {
            return;
        }

        int tipX = TabletGui.antennaLeft(frameX) + TabletGui.ANTENNA_W / 2;
        int tipY = TabletGui.antennaDrawY(frameY) + TabletGui.ANTENNA_LAMP_CY;
        boolean lampOn = elapsed <= LAMP_FLASH_MS
                || (CouriersClientConfig.uiAnimations() && elapsed < DURATION_MS && stageAt(elapsed) >= 1);
        if (lampOn) {
            drawLampFlash(graphics, tipX, tipY);
        }
        if (!CouriersClientConfig.uiAnimations()) {
            return;
        }
        int stage = stageAt(elapsed);
        if (stage <= 0) {
            return;
        }
        float fade = stage == 3 ? 1.0F - TabletAnim.easeInCubic((elapsed - 500L) / 200.0F) : 1.0F;
        if (fade <= 0.01F) {
            return;
        }
        var sprite = switch (stage) {
            case 1 -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_1;
            case 2 -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_2;
            default -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_3;
        };
        TabletAnim.withAlpha(graphics, fade, () -> graphics.blitSprite(sprite,
                tipX - SIGNAL_W / 2, tipY - SIGNAL_H / 2, SIGNAL_W, SIGNAL_H));
    }

    private static int stageAt(long elapsed) {
        if (elapsed < 220L) {
            return 1;
        }
        if (elapsed < 480L) {
            return 2;
        }
        if (elapsed < DURATION_MS) {
            return 3;
        }
        return 0;
    }

    private static void drawLampFlash(GuiGraphics graphics, int tipX, int tipY) {
        int r = 5;
        graphics.fill(tipX - r, tipY - r, tipX + r, tipY + r, 0x88FFFFAA);
        graphics.fill(tipX - 3, tipY - 3, tipX + 3, tipY + 3, 0xFFFFE94A);
    }
}
