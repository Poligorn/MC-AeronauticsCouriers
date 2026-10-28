package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Two-line order card from the approved board mockup ({@code card()} in build.py). */
public final class BoardCardRenderer {
    public static final int CARD_H = 25;
    public static final int PITCH = 28;
    /** Card right edge (inclusive) is {@code listRight - RIGHT_INSET}; leaves room for the scrollbar. */
    public static final int RIGHT_INSET = 11;
    public static final int LEFT_INSET = 3;
    /** Gap between the card's right edge and the reward/collateral column. */
    public static final int RIGHT_PAD = 8;
    private static final int DEST_MAX_W = 110;
    private static final int NEW_AGE_MINUTES = 10;

    public record Style(
            boolean boardTab,
            boolean selected,
            boolean hovered,
            boolean locked,
            long collateralDue,
            boolean traineeFree
    ) {
    }

    private BoardCardRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry entry,
                              int x0, int x1, int y, Style style) {
        int bg = style.selected() ? TabletPalette.CARD_SEL
                : style.locked() ? TabletPalette.CARD_LOCKED : TabletPalette.CARD;
        if (style.hovered() && !style.selected()) {
            bg = TabletPalette.hoverBrighten(bg);
        }
        TabletDraw.box(graphics, x0, y, x1, y + CARD_H - 1, bg,
                style.selected() ? TabletPalette.CARD_BORDER_SEL : TabletPalette.CARD_BORDER);
        if (style.selected()) {
            graphics.fill(x0, y, x0 + 3, y + CARD_H, TabletPalette.GOLD);
        }
        int tc = style.locked() ? TabletPalette.LOCKC : TabletPalette.TXT;
        int sc = style.locked() ? TabletPalette.LOCKC : TabletPalette.SEC;
        int rx = x1 - RIGHT_PAD;

        // Right column first so the route can be clipped against it.
        int line1RightLeft;
        if (style.locked()) {
            String need = Component.translatable("ui.aerocouriers.card.rank_needed", roman(entry.requiredRank()))
                    .getString();
            int xx = rx - font.width(need);
            PixelGlyphs.draw(graphics, xx - 10, y + 4, PixelGlyphs.LOCK, TabletPalette.LOCKC);
            TabletDraw.text(graphics, font, need, xx, y + 4, TabletPalette.LOCKC);
            line1RightLeft = xx - 10;
        } else {
            line1RightLeft = TabletDraw.textRight(graphics, font, "+" + TabletDraw.num(entry.reward()), rx, y + 4,
                    TabletPalette.GOLD, true);
        }

        int x = x0 + 8;
        PixelGlyphs.draw(graphics, x, y + 4, originIsPlayer(entry) ? PixelGlyphs.FACE : PixelGlyphs.HOUSE, sc);
        x += 10;
        int badgeW = badgeWidth(font, entry, style);
        int routeRoom = Math.max(40, line1RightLeft - 8 - x - badgeW);
        String from = originName(entry);
        String to = destinationName(entry);
        int fromMax = Math.max(24, routeRoom - 14 - Math.min(font.width(to), DEST_MAX_W));
        String fromShown = TabletDraw.trunc(font, from, fromMax);
        TabletDraw.text(graphics, font, fromShown, x, y + 4, tc);
        x += font.width(fromShown) + 4;
        PixelGlyphs.draw(graphics, x, y + 6, PixelGlyphs.ARROW, sc);
        x += 10;
        String toShown = TabletDraw.trunc(font, to, Math.max(16, Math.min(DEST_MAX_W, line1RightLeft - 8 - x - badgeW)));
        TabletDraw.text(graphics, font, toShown, x, y + 4, tc);
        x += font.width(toShown) + 6;
        if (isNew(entry, style)) {
            String s = "NEW";
            int w = font.width(s) + 5;
            TabletDraw.box(graphics, x, y + 2, x + w, y + 12, TabletPalette.NEW_BG, TabletPalette.TEAL);
            TabletDraw.text(graphics, font, s, x + 3, y + 3, TabletPalette.TEAL);
            x += w + 3;
        }
        if (!style.boardTab()) {
            String s = statusLabel(entry);
            int color = statusColor(entry);
            int w = font.width(s) + 5;
            TabletDraw.box(graphics, x, y + 2, x + w, y + 12, dim(color), color);
            TabletDraw.text(graphics, font, s, x + 3, y + 3, color);
            x += w + 3;
        }
        if (deadlineWarn(entry)) {
            String s = Component.translatable("ui.aerocouriers.card.deadline_warn").getString();
            TabletDraw.box(graphics, x, y + 2, x + font.width(s) + 13, y + 12, TabletPalette.WARN_BG,
                    TabletPalette.CORAL);
            PixelGlyphs.draw(graphics, x + 3, y + 4, PixelGlyphs.WARN, TabletPalette.CORAL);
            TabletDraw.text(graphics, font, s, x + 11, y + 3, TabletPalette.CORAL);
        }

        int y2 = y + 14;
        int deposColor;
        String depos;
        if (style.locked()) {
            deposColor = TabletPalette.LOCKC;
            depos = Component.translatable("ui.aerocouriers.card.collateral", TabletDraw.num(entry.collateral()))
                    .getString();
        } else if (style.traineeFree()) {
            deposColor = TabletPalette.TEAL;
            depos = Component.translatable("ui.aerocouriers.card.collateral_trainee").getString();
        } else if (style.collateralDue() <= 0) {
            deposColor = TabletPalette.TEAL;
            depos = Component.translatable("ui.aerocouriers.card.collateral_none").getString();
        } else {
            deposColor = TabletPalette.CORAL;
            depos = Component.translatable("ui.aerocouriers.card.collateral", TabletDraw.num(style.collateralDue()))
                    .getString();
        }
        TabletDraw.textRight(graphics, font, depos, rx, y2, deposColor, false);
        String middle = style.boardTab() || !"issuer".equals(entry.role())
                ? entry.rewardPer1000Blocks() + "/1000"
                : Component.translatable("ui.aerocouriers.card.role_issuer").getString();
        TabletDraw.textRight(graphics, font, TabletDraw.trunc(font, middle, 80), rx - 90, y2, sc, false);

        x = x0 + 18;
        int line2Max = rx - 90 - 84;
        String dist = Component.translatable("ui.aerocouriers.card.blocks", TabletDraw.num(entry.distance()))
                .getString();
        TabletDraw.text(graphics, font, dist, x, y2, sc);
        x += font.width(dist) + 4;
        TabletDraw.text(graphics, font, "·", x, y2, sc);
        x += 6;
        if (x + 40 < line2Max) {
            PixelGlyphs.draw(graphics, x, y2, PixelGlyphs.BOX, sc);
            x += 11;
            String cargo = Component.translatable("ui.aerocouriers.card.cargo", entry.size(),
                    TabletDraw.weight(entry.weightValue())).getString();
            TabletDraw.text(graphics, font, cargo, x, y2, sc);
            x += font.width(cargo) + 4;
        }
        if (x + 30 < line2Max && !entry.state().isFinal()) {
            TabletDraw.text(graphics, font, "·", x, y2, sc);
            x += 6;
            PixelGlyphs.draw(graphics, x, y2, PixelGlyphs.CLOCK, sc);
            x += 11;
            long minutes = entry.state() == ContractState.OPEN ? entry.deadlineMinutes() : entry.minutesLeft();
            TabletDraw.text(graphics, font,
                    Component.translatable("ui.aerocouriers.card.minutes", minutes).getString(), x, y2, sc);
        }
    }

    private static int badgeWidth(Font font, BoardSnapshotPayload.BoardEntry entry, Style style) {
        int w = 0;
        if (!style.boardTab()) {
            w += font.width(statusLabel(entry)) + 8;
        }
        if (isNew(entry, style)) {
            w += font.width("NEW") + 9;
        }
        if (deadlineWarn(entry)) {
            w += font.width(Component.translatable("ui.aerocouriers.card.deadline_warn").getString()) + 16;
        }
        return w;
    }

    public static boolean isNew(BoardSnapshotPayload.BoardEntry entry, Style style) {
        return style.boardTab() && entry.state() == ContractState.OPEN && entry.ageMinutes() < NEW_AGE_MINUTES;
    }

    /** Less than 20% of the time window left (board: until the order expires; active haul: until due). */
    public static boolean deadlineWarn(BoardSnapshotPayload.BoardEntry entry) {
        if (entry.state().isFinal() || entry.deadlineMinutes() <= 0) {
            return false;
        }
        return entry.minutesLeft() * 5 < entry.deadlineMinutes();
    }

    /** «Мои»: what the haul is waiting for, from the viewer's side (courier or customer). */
    public static String statusLabel(BoardSnapshotPayload.BoardEntry entry) {
        boolean courier = "courier".equals(entry.role());
        String key = switch (entry.state()) {
            case OPEN -> "ui.aerocouriers.card.status.open";
            case ACCEPTED -> courier ? "ui.aerocouriers.card.status.pickup" : "ui.aerocouriers.card.status.accepted";
            case IN_TRANSIT -> "ui.aerocouriers.card.status.transit";
            case DELIVERED -> "ui.aerocouriers.card.status.delivered";
            case EXPIRED -> "ui.aerocouriers.card.status.expired";
            case FAILED -> "ui.aerocouriers.card.status.failed";
        };
        return Component.translatable(key).getString();
    }

    /** Matches the world beams and Xaero waypoints: yellow = pick up, blue = deliver. */
    public static int statusColor(BoardSnapshotPayload.BoardEntry entry) {
        return switch (entry.state()) {
            case OPEN -> TabletPalette.SEC;
            case ACCEPTED -> TabletPalette.GOLD;
            case IN_TRANSIT -> TabletPalette.BLUE;
            case DELIVERED -> TabletPalette.TEAL;
            case EXPIRED -> TabletPalette.LOCKC;
            case FAILED -> TabletPalette.CORAL;
        };
    }

    static int dim(int argb) {
        int r = ((argb >> 16) & 0xFF) * 22 / 100;
        int g = ((argb >> 8) & 0xFF) * 22 / 100;
        int b = (argb & 0xFF) * 22 / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static boolean originIsPlayer(BoardSnapshotPayload.BoardEntry entry) {
        return !entry.originStation() && !entry.villageCustomer();
    }

    public static boolean destinationIsPlayer(BoardSnapshotPayload.BoardEntry entry) {
        return !entry.destinationStation() && !entry.villageCustomer();
    }

    /** Station name, or the customer's name for a player's own pickup point; hidden until revealed. */
    public static String originName(BoardSnapshotPayload.BoardEntry entry) {
        if (!entry.originRevealed()) {
            return Component.translatable("ui.aerocouriers.row.pickup_hidden").getString();
        }
        if (originIsPlayer(entry)) {
            return entry.customerName();
        }
        return entry.originLabel();
    }

    public static String destinationName(BoardSnapshotPayload.BoardEntry entry) {
        if (destinationIsPlayer(entry)) {
            return entry.customerName();
        }
        return entry.destinationLabel();
    }

    public static String roman(LicenseRank rank) {
        return switch (rank) {
            case RANK1 -> "I";
            case RANK2 -> "II";
            case RANK3 -> "III";
            case RANK4 -> "IV";
            case RANK5 -> "V";
            case ADMIN -> "A";
        };
    }
}
