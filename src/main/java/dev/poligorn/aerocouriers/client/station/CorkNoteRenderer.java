package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class CorkNoteRenderer {
    private CorkNoteRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry entry,
                              CorkNoteStyle style, int x, int y, int w, int h, boolean hover, boolean compact) {
        int fill = style.paper();
        if (hover && !style.locked()) {
            fill = brighten(fill);
        }
        CorkBoardGui.paper(graphics, x, y, w, h, fill, style.border());
        CorkBoardGui.pin(graphics, x + w / 2, y + 3);

        int pad = compact ? 4 : 6;
        int tx = x + pad;
        int ty = y + 8;
        String size = entry.size().toUpperCase();
        CorkBoardGui.sizeStamp(graphics, font, tx, ty, size, style.largeCargo());
        int badgeX = tx + (style.largeCargo() ? 18 : 15);
        if (style.expensive()) {
            Component expensive = Component.translatable("ui.aerocouriers.cork.note.expensive");
            CorkBoardGui.badge(graphics, font, badgeX, ty, expensive, 0x33C4A020, CorkBoardGui.GOLD);
            badgeX += font.width(expensive) + 10;
        }
        if (style.rare()) {
            Component rare = Component.translatable("ui.aerocouriers.cork.note.rare");
            CorkBoardGui.badge(graphics, font, badgeX, ty, rare, 0x33C070A0, 0xFFC04070);
        }
        if (style.expiring()) {
            Component soon = Component.translatable("ui.aerocouriers.cork.note.expiring");
            CorkBoardGui.badge(graphics, font, x + w - pad - font.width(soon) - 8, ty, soon, 0x33C04030,
                    CorkBoardGui.RED);
        }

        ty += compact ? 14 : 16;
        String dest = BoardCardRenderer.destinationName(entry);
        graphics.drawString(font, "→ " + CorkBoardGui.ellipsize(font, dest, w - pad * 2 - 8), tx, ty,
                CorkBoardGui.INK, false);
        ty += 10;
        graphics.drawString(font, entry.distance() + " " + Component.translatable("ui.aerocouriers.cork.blocks")
                .getString(), tx, ty, CorkBoardGui.INK_MUTED, false);
        ty += 10;
        int timeColor = style.expiring() ? CorkBoardGui.RED : CorkBoardGui.INK_MUTED;
        graphics.drawString(font, size + "  " + entry.weight(), tx, ty, CorkBoardGui.INK_MUTED, false);
        String clock = clock(entry);
        graphics.drawString(font, clock, x + w - pad - font.width(clock), ty, timeColor, false);
        ty += compact ? 11 : 13;

        if (style.locked()) {
            CorkBoardGui.lock(graphics, tx, ty);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.note.rank_needed",
                    BoardCardRenderer.roman(entry.requiredRank())), tx + 12, ty, CorkBoardGui.INK_MUTED, false);
            ty += 10;
            graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.note.your_rank",
                    BoardCardRenderer.roman(style.playerRank())), tx + 12, ty, CorkBoardGui.INK_MUTED, false);
            return;
        }

        if (!style.hideReward()) {
            String reward = MoneyFormat.text(entry.reward());
            graphics.drawString(font, reward, tx, ty, CorkBoardGui.GOLD, false);
            ty += 10;
            if (!style.inProgress()) {
                graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.note.deposit",
                        MoneyFormat.text(entry.collateral())), tx, ty, CorkBoardGui.INK_MUTED, false);
            }
        }

        if (style.expensive()) {
            CorkBoardGui.waxSeal(graphics, x + w - 14, y + h - 16);
        }
        if (style.rare()) {
            CorkBoardGui.ribbon(graphics, x + w - 10, y + 1);
        }
        if (style.inProgress()) {
            int stampW = Math.min(w - pad * 2, font.width(Component.translatable(
                    "ui.aerocouriers.cork.note.in_progress")) + 16);
            String nick = style.courierName().isBlank() ? "—" : style.courierName();
            CorkBoardGui.progressStamp(graphics, font, x + pad, y + h - 26, stampW,
                    Component.translatable("ui.aerocouriers.cork.note.in_progress"),
                    Component.literal(CorkBoardGui.ellipsize(font, nick, stampW - 8)));
        }
    }

    private static String clock(BoardSnapshotPayload.BoardEntry entry) {
        long minutes = Math.max(0, entry.minutesLeft());
        return String.valueOf(minutes);
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 12);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 10);
        int b = Math.min(255, (argb & 0xFF) + 6);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
