package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Antenna tip pulse: three pixel arcs per side (radii 6/11/16 GUI px, ±52°). Send — yellow arcs run outward from
 * the lamp; receive — blue arcs run inward and the lamp flashes at the end.
 */
public final class TabletAntennaSignal {
    public static final long DURATION_MS = 900L;
    private static final long LAMP_FLASH_MS = 160L;
    private static final long ARC_STEP_MS = 200L;
    private static final long ARC_LIFE_MS = 460L;
    private static final long ARC_FADE_IN_MS = 70L;
    private static final int[] RADII = {6, 11, 16};
    private static final int HALF_ANGLE_DEG = 52;
    private static final int SEND_COLOR = 0xFFE94A;
    private static final int SEND_OUTLINE = 0x3A2306;
    /** Saturated cyan with a navy outline: the pale blue used before vanished against the daytime sky. */
    private static final int RECEIVE_COLOR = 0x2EE6FF;
    private static final int RECEIVE_OUTLINE = 0x06204A;
    private static final int[][] ARC_POINTS = buildArcPoints();

    public enum Direction {
        SEND,
        RECEIVE
    }

    private static long triggeredAt = Long.MIN_VALUE;
    private static Direction direction = Direction.SEND;
    /** Accept sends the order and the server mails a notice right after; the receive pulse waits for the send. */
    private static boolean receiveQueued;

    private TabletAntennaSignal() {
    }

    /** Outgoing actions (e.g. order accept). Restarts animation if already playing. */
    public static void triggerSend() {
        direction = Direction.SEND;
        triggeredAt = Util.getMillis();
        receiveQueued = false;
    }

    /** Incoming notification alert (inbox / order updates). Restarts animation if already playing. */
    public static void triggerReceive() {
        long now = Util.getMillis();
        if (direction == Direction.SEND && triggeredAt != Long.MIN_VALUE && now - triggeredAt < DURATION_MS) {
            receiveQueued = true;
            return;
        }
        direction = Direction.RECEIVE;
        triggeredAt = now;
    }

    public static void render(GuiGraphics graphics, int frameX, int frameY) {
        if (triggeredAt == Long.MIN_VALUE) {
            return;
        }
        long elapsed = Util.getMillis() - triggeredAt;
        if (elapsed > DURATION_MS && receiveQueued) {
            receiveQueued = false;
            direction = Direction.RECEIVE;
            triggeredAt = Util.getMillis();
            elapsed = 0L;
        }
        boolean animate = CouriersClientConfig.uiAnimations();
        if (elapsed > DURATION_MS || !animate && elapsed > LAMP_FLASH_MS) {
            return;
        }
        int tipX = TabletGui.antennaLeft(frameX) + TabletGui.ANTENNA_W / 2;
        int tipY = TabletGui.antennaDrawY(frameY) + TabletGui.ANTENNA_LAMP_CY;
        boolean send = direction == Direction.SEND;
        if (send ? elapsed <= LAMP_FLASH_MS : !animate || elapsed >= DURATION_MS - LAMP_FLASH_MS) {
            drawLampFlash(graphics, tipX, tipY, send);
        }
        if (!animate) {
            return;
        }
        for (int i = 0; i < RADII.length; i++) {
            int order = send ? i : RADII.length - 1 - i;
            float alpha = arcAlpha(elapsed - order * ARC_STEP_MS);
            if (alpha <= 0.01F) {
                continue;
            }
            int argb = ((int) (alpha * 255.0F) << 24) | (send ? SEND_COLOR : RECEIVE_COLOR);
            int outline = ((int) (alpha * 200.0F) << 24) | (send ? SEND_OUTLINE : RECEIVE_OUTLINE);
            int[] points = ARC_POINTS[i];
            for (int k = 0; k < points.length; k += 2) {
                int dx = points[k];
                int dy = points[k + 1];
                graphics.fill(tipX + dx - 1, tipY + dy - 1, tipX + dx + 2, tipY + dy + 2, outline);
                graphics.fill(tipX - dx - 2, tipY + dy - 1, tipX - dx + 1, tipY + dy + 2, outline);
            }
            for (int k = 0; k < points.length; k += 2) {
                int dx = points[k];
                int dy = points[k + 1];
                graphics.fill(tipX + dx, tipY + dy, tipX + dx + 1, tipY + dy + 1, argb);
                graphics.fill(tipX - dx - 1, tipY + dy, tipX - dx, tipY + dy + 1, argb);
            }
        }
    }

    private static float arcAlpha(long t) {
        if (t < 0L || t > ARC_LIFE_MS) {
            return 0.0F;
        }
        if (t < ARC_FADE_IN_MS) {
            return t / (float) ARC_FADE_IN_MS;
        }
        return 1.0F - TabletAnim.easeInCubic((t - ARC_FADE_IN_MS) / (float) (ARC_LIFE_MS - ARC_FADE_IN_MS));
    }

    /** Right-side pixel offsets per radius; the left side is mirrored. Deduplicated so alpha does not stack. */
    private static int[][] buildArcPoints() {
        int[][] out = new int[RADII.length][];
        for (int i = 0; i < RADII.length; i++) {
            java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
            for (int a = -HALF_ANGLE_DEG; a <= HALF_ANGLE_DEG; a++) {
                double t = Math.toRadians(a);
                int dx = (int) Math.round(RADII[i] * Math.cos(t));
                int dy = (int) Math.round(RADII[i] * Math.sin(t));
                seen.add(((long) dx << 32) | (dy & 0xFFFFFFFFL));
            }
            int[] pts = new int[seen.size() * 2];
            int k = 0;
            for (long packed : seen) {
                pts[k++] = (int) (packed >> 32);
                pts[k++] = (int) packed;
            }
            out[i] = pts;
        }
        return out;
    }

    private static void drawLampFlash(GuiGraphics graphics, int tipX, int tipY, boolean send) {
        int r = 5;
        if (send) {
            graphics.fill(tipX - r, tipY - r, tipX + r, tipY + r, 0x88FFFFAA);
            graphics.fill(tipX - 3, tipY - 3, tipX + 3, tipY + 3, 0xFFFFE94A);
        } else {
            graphics.fill(tipX - r - 1, tipY - r - 1, tipX + r + 1, tipY + r + 1, 0xC006204A);
            graphics.fill(tipX - r, tipY - r, tipX + r, tipY + r, 0xB02EE6FF);
            graphics.fill(tipX - 3, tipY - 3, tipX + 3, tipY + 3, 0xFFB8F6FF);
        }
    }
}
