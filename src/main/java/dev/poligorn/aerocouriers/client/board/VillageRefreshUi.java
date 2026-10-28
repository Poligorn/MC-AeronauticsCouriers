package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Countdown until the next {@link dev.poligorn.aerocouriers.village.StationContractSource} board fill. */
public final class VillageRefreshUi {
    /** At or below this many ticks, show the “soon” string instead of a numeric countdown. */
    public static final int SOON_TICKS = 20;

    private static long nextRefreshGameTime;
    private static long snapshotOverworldGameTime;
    private static int snapshotGuiTick;

    private VillageRefreshUi() {
    }

    public static void applySnapshot(long nextRefresh, long overworldGameTime) {
        nextRefreshGameTime = nextRefresh;
        snapshotOverworldGameTime = overworldGameTime;
        snapshotGuiTick = Minecraft.getInstance().gui.getGuiTicks();
    }

    public static void clear() {
        nextRefreshGameTime = 0L;
        snapshotOverworldGameTime = 0L;
        snapshotGuiTick = 0;
    }

    public static boolean isEnabled(long nextRefreshField) {
        return nextRefreshField > 0L;
    }

    public static boolean relevantForBoardFilters(BoardTabFilterState filters) {
        return filters.contractKind() != BoardContractKind.PLAYER;
    }

    public static int remainingTicks(Minecraft minecraft) {
        if (nextRefreshGameTime <= 0L) {
            return -1;
        }
        long now = estimateOverworldGameTime(minecraft);
        return (int) Math.max(0L, nextRefreshGameTime - now);
    }

    private static long estimateOverworldGameTime(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD) {
            return minecraft.level.getGameTime();
        }
        int elapsedGui = minecraft.gui.getGuiTicks() - snapshotGuiTick;
        return snapshotOverworldGameTime + elapsedGui;
    }

    public static String formatDuration(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + two(minutes) + ":" + two(seconds);
        }
        return minutes + ":" + two(seconds);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    public static Component countdownLabel(int remainingTicks) {
        if (remainingTicks <= SOON_TICKS) {
            return Component.translatable("ui.aerocouriers.village_refresh.soon");
        }
        return Component.translatable("ui.aerocouriers.village_refresh.in", formatDuration(remainingTicks));
    }

    /** Right-aligned clock icon + countdown in the list header band. */
    public static void renderHeader(GuiGraphics graphics, Font font, int contentLeft, int contentRight, int y,
                                    int remainingTicks) {
        Component label = countdownLabel(remainingTicks);
        int icon = BoardRowMetrics.LINE2_ICON;
        int textW = font.width(label);
        int blockW = icon + 2 + textW;
        int x = contentRight - blockW;
        graphics.blitSprite(AerocouriersGuiSprites.ICON_CLOCK, x, y, icon, icon);
        graphics.drawString(font, label, x + icon + 2, y + 1, TabletGui.TEXT_MUTED, false);
    }
}
