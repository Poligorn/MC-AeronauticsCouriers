package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Antenna tip pulse: yellow outward (send) or light-blue inward (receive), ~0.7 s. */
public final class TabletAntennaSignal {
    public static final long DURATION_MS = 700L;
    private static final long LAMP_FLASH_MS = 160L;
    private static final int SIGNAL_W = 56;
    private static final int SIGNAL_H = 14;

    public enum Direction {
        SEND,
        RECEIVE
    }

    private static long triggeredAt = Long.MIN_VALUE;
    private static Direction direction = Direction.SEND;

    private TabletAntennaSignal() {
    }

    /** Outgoing actions (e.g. order accept). Restarts animation if already playing. */
    public static void triggerSend() {
        direction = Direction.SEND;
        triggeredAt = Util.getMillis();
    }

    /** Incoming notification alert (inbox / order updates). Restarts animation if already playing. */
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
        boolean lampOn = lampActive(elapsed);
        if (lampOn) {
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
        if (fade <= 0.01F) {
            return;
        }
        ResourceLocation sprite = signalSprite(direction, stage);
        TabletAnim.withAlpha(graphics, fade, () -> graphics.blitSprite(sprite,
                tipX - SIGNAL_W / 2, tipY - SIGNAL_H / 2, SIGNAL_W, SIGNAL_H));
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

    private static ResourceLocation signalSprite(Direction dir, int stage) {
        if (dir == Direction.RECEIVE) {
            return switch (stage) {
                case 1 -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_RX_1;
                case 2 -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_RX_2;
                default -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_RX_3;
            };
        }
        return switch (stage) {
            case 1 -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_1;
            case 2 -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_2;
            default -> AerocouriersGuiSprites.TABLET_ANTENNA_SIGNAL_3;
        };
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
