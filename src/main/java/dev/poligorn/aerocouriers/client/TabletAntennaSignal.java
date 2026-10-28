package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/** Antenna tip pulse: yellow outward (send) or light-blue inward (receive), ~0.7 s. */
public final class TabletAntennaSignal {
    public static final long DURATION_MS = 700L;
    private static final long LAMP_FLASH_MS = 160L;

    public enum Direction {
        SEND,
        RECEIVE
    }

    private static long triggeredAt = Long.MIN_VALUE;
    private static Direction direction = Direction.SEND;

    private TabletAntennaSignal() {
    }

    public static void triggerSend() {
        direction = Direction.SEND;
        triggeredAt = Util.getMillis();
    }

    public static void triggerReceive() {
        direction = Direction.RECEIVE;
        triggeredAt = Util.getMillis();
    }

    public static void render(GuiGraphics graphics, int frameX, int frameY) {
        if (triggeredAt == Long.MIN_VALUE) {
            return;
        }
        long elapsed = Util.getMillis() - triggeredAt;
        if (elapsed > DURATION_MS) {
            return;
        }
        if (!CouriersClientConfig.uiAnimations() && elapsed > LAMP_FLASH_MS) {
            return;
        }

        int tipX = TabletGui.antennaLeft(frameX) + TabletGui.ANTENNA_W / 2;
        int tipY = TabletGui.antennaDrawY(frameY) + TabletGui.ANTENNA_LAMP_CY;
        if (lampActive(elapsed)) {
            drawLampFlash(graphics, tipX, tipY, direction == Direction.SEND);
        }
        if (!CouriersClientConfig.uiAnimations()) {
            return;
        }
        int stage = stageAt(elapsed, direction);
        if (stage <= 0) {
            return;
        }
        float fade = fadeAt(elapsed, direction, stage);
        drawSignalArcs(graphics, tipX, tipY, direction, stage, fade);
    }

    private static void drawSignalArcs(GuiGraphics graphics, int tipX, int tipY, Direction dir, int stage, float fade) {
        int alpha = (int) (255 * fade);
        if (alpha < 8) {
            return;
        }
        int[] radii = {6, 11, 16};
        for (int s = 0; s < Math.min(stage, radii.length); s++) {
            drawArcBracket(graphics, tipX, tipY, radii[s], dir == Direction.SEND, arcColor(dir, alpha, s));
        }
    }

    private static int arcColor(Direction dir, int alpha, int band) {
        if (dir == Direction.SEND) {
            int yellow = band == 0 ? 0xFFE94A : band == 1 ? 0xFFD726 : 0xFFB800;
            return (alpha << 24) | (yellow & 0xFFFFFF);
        }
        int blue = band == 0 ? 0x88CCFF : band == 1 ? 0x66BBFF : 0x4499EE;
        return (alpha << 24) | (blue & 0xFFFFFF);
    }

    private static void drawArcBracket(GuiGraphics graphics, int cx, int cy, int radius, boolean outward, int color) {
        for (int side = -1; side <= 1; side += 2) {
            int ox = cx + side * (outward ? radius : -radius / 3);
            for (int dy = -radius; dy <= radius; dy++) {
                int dx = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
                int px = ox + (outward ? side * dx : -side * dx);
                graphics.fill(px, cy + dy, px + 2, cy + dy + 1, color);
            }
        }
    }

    private static boolean lampActive(long elapsed) {
        if (direction == Direction.SEND) {
            return elapsed <= LAMP_FLASH_MS
                    || elapsed < DURATION_MS && stageAt(elapsed, Direction.SEND) >= 1;
        }
        return elapsed >= DURATION_MS - LAMP_FLASH_MS;
    }

    private static int stageAt(long elapsed, Direction dir) {
        if (dir == Direction.SEND) {
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
        if (elapsed < 220L) {
            return 3;
        }
        if (elapsed < 480L) {
            return 2;
        }
        if (elapsed < DURATION_MS - LAMP_FLASH_MS) {
            return 1;
        }
        return 0;
    }

    private static float fadeAt(long elapsed, Direction dir, int stage) {
        if (dir == Direction.SEND && stage == 3) {
            return 1.0F - TabletAnim.easeInCubic((elapsed - 500L) / 200.0F);
        }
        if (dir == Direction.RECEIVE && stage == 3) {
            return TabletAnim.easeInCubic(elapsed / 220.0F);
        }
        return 1.0F;
    }

    private static void drawLampFlash(GuiGraphics graphics, int tipX, int tipY, boolean send) {
        int r = 5;
        if (send) {
            graphics.fill(tipX - r, tipY - r, tipX + r, tipY + r, 0x88FFFFAA);
            graphics.fill(tipX - 3, tipY - 3, tipX + 3, tipY + 3, 0xFFFFE94A);
        } else {
            graphics.fill(tipX - r, tipY - r, tipX + r, tipY + r, 0x8899DDFF);
            graphics.fill(tipX - 3, tipY - 3, tipX + 3, tipY + 3, 0xFF88CCFF);
        }
    }
}
