package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Note anatomy from layout.json (std 136×108 / compact 136×84). No graph-paper overlay. */
public final class CorkNoteRenderer {
    private CorkNoteRenderer() {
    }

    public static void render(GuiGraphics g, Font font, BoardSnapshotPayload.BoardEntry entry,
                              CorkNoteStyle style, int x, int y, int w, int h, boolean hover, boolean compact) {
        int drawY = hover && !style.locked() ? y - 3 : y;
        if (hover && !style.locked()) {
            CorkBoardGui.blit(g, CorkSprites.SHADOW, x + 5, drawY + 9, w, h);
            g.fill(x + 5, drawY + 9, x + 5 + w, drawY + 9 + h, CorkPalette.SHADOW_HOVER);
            g.fill(x + 7, drawY + 11, x + 7 + w, drawY + 11 + h, CorkPalette.SHADOW_FAR);
        } else {
            g.fill(x + 2, y + 3, x + 2 + w, y + 3 + h, CorkPalette.SHADOW);
        }
        CorkBoardGui.blit(g, style.paperSprite(compact), x, drawY, w, h);
        if (style.expiring()) {
            CorkBoardGui.blit(g, CorkSprites.BORDER_EXPIRE, x, drawY, w, h);
        }
        if (style.inProgress()) {
            CorkBoardGui.blit(g, CorkSprites.BORDER_DOING, x, drawY, w, h);
        }
        if (hover && !style.locked()) {
            CorkBoardGui.blit(g, CorkSprites.HOVER, x - 1, drawY - 1, w + 2, h + 2);
        }

        int ink = CorkPalette.ink(style);
        int muted = CorkPalette.muted(style);
        CorkBoardGui.blit(g, CorkSprites.pin(style), x + w / 2 - 3, drawY + 1, 7, 7);
        if (style.largeCargo()) {
            CorkBoardGui.blit(g, CorkSprites.pin(style), x + 4, drawY + 2, 7, 7);
            CorkBoardGui.blit(g, CorkSprites.pin(style), x + w - 11, drawY + 2, 7, 7);
        }

        int stampY = compact ? 9 : 10;
        CorkBoardGui.blitTint(g, CorkSprites.stamp(entry.size(), style.largeCargo()),
                x + 8, drawY + stampY, 17, 15, ink);

        int badgeY = compact ? 11 : 12;
        int badgeX = 29;
        if (style.expensive() && style.rare()) {
            CorkBoardGui.blit(g, CorkSprites.BADGE_EXP_ICON, x + badgeX, drawY + badgeY, 15, 11);
            badgeX += 18;
            CorkBoardGui.blit(g, CorkSprites.BADGE_RARE_ICON, x + badgeX, drawY + badgeY, 15, 11);
        } else if (style.expensive()) {
            badgeX += drawBadge(g, font, x + badgeX, drawY + badgeY,
                    Component.translatable("ui.aerocouriers.cork.note.expensive"),
                    CorkSprites.BADGE_EXP, CorkSprites.ICON_STAR, 9, 9, CorkPalette.BADGE_EXP);
        } else if (style.rare()) {
            badgeX += drawBadge(g, font, x + badgeX, drawY + badgeY,
                    Component.translatable("ui.aerocouriers.cork.note.rare"),
                    CorkSprites.BADGE_RARE, CorkSprites.ICON_GEM, 7, 6, CorkPalette.BADGE_RARE);
        }
        if (style.expiring()) {
            Component soon = Component.translatable("ui.aerocouriers.cork.note.expiring");
            int bw = font.width(soon) + 16;
            drawBadge(g, font, x + w - 8 - bw, drawY + badgeY, soon,
                    CorkSprites.BADGE_EXPIRE, CorkSprites.ICON_WARN, 7, 6, CorkPalette.BADGE_EXPIRE);
        }

        if (style.rare()) {
            CorkBoardGui.blit(g, CorkSprites.RIBBON, x + w - 18, drawY - 2, 11, 26);
        }

        int destY = compact ? 27 : 31;
        int destIconY = compact ? 28 : 32;
        CorkBoardGui.blitTint(g, CorkSprites.ICON_ARROW, x + 8, drawY + destIconY, 7, 5, muted);
        String dest = BoardCardRenderer.destinationName(entry);
        CorkBoardGui.bold(g, font, CorkBoardGui.ellipsize(font, dest, w - 28), x + 18, drawY + destY, ink);

        if (!compact) {
            CorkBoardGui.text(g, font, grouped(entry.distance()) + " "
                    + Component.translatable("ui.aerocouriers.cork.blocks").getString(),
                    x + 18, drawY + 43, muted);
            dotted(g, x + 8, drawY + 55, x + w - 9, muted);
        }

        int cargoY = compact ? 38 : 60;
        CorkBoardGui.blitTint(g, CorkSprites.ICON_BOX, x + 8, drawY + cargoY, 9, 7, muted);
        String cargo = entry.size().toUpperCase() + " " + weight(entry);
        CorkBoardGui.text(g, font, cargo, x + 19, drawY + cargoY, ink);
        String clock = clock(entry);
        int clockColor = style.expiring() ? CorkPalette.EXPIRE_TIMER : ink;
        int clockTextW = font.width(clock);
        CorkBoardGui.text(g, font, clock, x + w - 9 - clockTextW, drawY + cargoY, clockColor);
        CorkBoardGui.blitTint(g, CorkSprites.ICON_CLOCK, x + w - 9 - clockTextW - 11, drawY + cargoY, 9, 8,
                style.expiring() ? CorkPalette.EXPIRE_TIMER : muted);

        if (compact) {
            CorkBoardGui.text(g, font, grouped(entry.distance()) + " "
                    + Component.translatable("ui.aerocouriers.cork.blocks").getString(),
                    x + 18, drawY + 48, muted);
        }

        if (style.locked()) {
            int lockY = compact ? 58 : 78;
            CorkBoardGui.blitTint(g, CorkSprites.ICON_LOCK, x + 8, drawY + lockY, 7, 7, muted);
            CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.note.rank_needed",
                    BoardCardRenderer.roman(entry.requiredRank())), x + 18, drawY + lockY, muted);
            return;
        }

        int rewardY = style.inProgress() ? (compact ? 58 : 72) : (compact ? 58 : 78);
        if (!style.hideReward()) {
            CorkBoardGui.bold(g, font, MoneyFormat.text(entry.reward()), x + 8, drawY + rewardY,
                    CorkPalette.reward(style));
        }

        if (style.inProgress()) {
            int stampX = x + 8;
            int stampW = w - 16;
            int stampH = 22;
            int stampTop = drawY + h - 25;
            CorkBoardGui.blit(g, CorkSprites.DOING_STAMP, stampX, stampTop, stampW, stampH);
            Component title = Component.translatable("ui.aerocouriers.cork.note.in_progress");
            CorkBoardGui.bold(g, font, title, stampX + (stampW - font.width(title)) / 2, stampTop + 4,
                    CorkPalette.DOING_TITLE);
            String nick = style.courierName().isBlank() ? "—" : style.courierName();
            int nickW = font.width(nick);
            int nx = stampX + (stampW - nickW - 10) / 2;
            CorkBoardGui.blitTint(g, CorkSprites.ICON_FACE, nx, stampTop + 12, 7, 7, CorkPalette.DOING_NICK);
            CorkBoardGui.text(g, font, nick, nx + 10, stampTop + 12, CorkPalette.DOING_NICK);
            return;
        }

        if (!style.hideReward()) {
            int depY = compact ? 71 : 91;
            Component deposit = Component.translatable("ui.aerocouriers.cork.note.deposit",
                    MoneyFormat.text(entry.collateral()));
            CorkBoardGui.text(g, font, deposit, x + 8, drawY + depY, CorkPalette.deposit(style));
        }
        if (style.expensive()) {
            CorkBoardGui.blit(g, CorkSprites.WAX, x + w - 23, drawY + h - 24, 17, 17);
        }
        if (style.expiring()) {
            g.fill(x + 4, drawY + h - 6, x + w - 4, drawY + h - 3, CorkPalette.EXPIRE_TRACK);
            int fill = Math.max(4, (w - 9) / 8);
            g.fill(x + 4, drawY + h - 6, x + 4 + fill, drawY + h - 3, CorkPalette.EXPIRE_BAR);
        }
    }

    private static int drawBadge(GuiGraphics g, Font font, int x, int y, Component label,
                                 net.minecraft.resources.ResourceLocation bg,
                                 net.minecraft.resources.ResourceLocation icon, int iw, int ih, int color) {
        int w = 4 + iw + 3 + font.width(label) + 4;
        CorkBoardGui.blit(g, bg, x, y, w, 11);
        CorkBoardGui.blitTint(g, icon, x + 4, y + (11 - ih) / 2, iw, ih, color);
        CorkBoardGui.text(g, font, label, x + 4 + iw + 3, y + 2, color);
        return w + 3;
    }

    private static void dotted(GuiGraphics g, int x0, int y, int x1, int color) {
        int faded = (color & 0x00FFFFFF) | 0x66000000;
        for (int x = x0; x < x1; x += 2) {
            g.fill(x, y, x + 1, y + 1, faded);
        }
    }

    private static String clock(BoardSnapshotPayload.BoardEntry entry) {
        long minutes = Math.max(0, entry.minutesLeft());
        if (minutes >= 60) {
            return (minutes / 60) + Component.translatable("ui.aerocouriers.cork.hours").getString();
        }
        return minutes + Component.translatable("ui.aerocouriers.cork.minutes_short").getString();
    }

    private static String weight(BoardSnapshotPayload.BoardEntry entry) {
        String raw = entry.weight();
        if (raw == null || raw.isBlank()) {
            return grouped((int) Math.round(entry.weightValue()))
                    + Component.translatable("ui.aerocouriers.cork.kg").getString();
        }
        return raw.replace(" ", "");
    }

    private static String grouped(int value) {
        return MoneyFormat.grouped(value);
    }
}
