package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Waybill sheet 344×244. Station never accepts; confirm opens the tablet order window. */
public final class StationWaybillOverlay {
    private static final int W = 344;
    private static final int H = 244;

    private final StationSnapshotPayload snapshot;
    private final BoardSnapshotPayload.BoardEntry entry;
    private final CorkNoteStyle style;
    private int x;
    private int y;
    private int btnX;
    private int btnY;
    private int btnW;
    private int closeX;
    private int closeY;
    private int closeBtnX;
    private int closeBtnY;
    private int closeBtnW;

    public StationWaybillOverlay(StationSnapshotPayload snapshot, BoardSnapshotPayload.BoardEntry entry,
                                 CorkNoteStyle style) {
        this.snapshot = snapshot;
        this.entry = entry;
        this.style = style;
    }

    public BoardSnapshotPayload.BoardEntry entry() {
        return entry;
    }

    public void layout(CorkBoardLayout layout) {
        x = layout.winX + Math.max(8, (layout.winW - W) / 2);
        y = layout.winY + Math.max(8, (layout.winH - H) / 2);
        btnX = x + 12;
        btnY = y + H - 34;
        btnW = W - 100;
        closeX = x + W - 24;
        closeY = y + 12;
        closeBtnX = x + W - 82;
        closeBtnY = y + H - 34;
        closeBtnW = 70;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        CorkBoardGui.blit(g, CorkSprites.waybill(style), x, y, W, H);
        int ink = CorkPalette.ink(style);
        int muted = CorkPalette.muted(style);
        CorkBoardGui.blit(g, CorkSprites.PIN_GOLD, x + 9, y + 3, 7, 7);
        CorkBoardGui.blit(g, CorkSprites.PIN_GOLD, x + W - 16, y + 3, 7, 7);
        CorkBoardGui.bold(g, font, snapshot.name(), x + 12, y + 14, ink);
        CorkBoardGui.blitTint(g, CorkSprites.ICON_ARROW, x + 12 + font.width(snapshot.name()) + 5, y + 16, 7, 5, ink);
        CorkBoardGui.bold(g, font, BoardCardRenderer.destinationName(entry),
                x + 12 + font.width(snapshot.name()) + 15, y + 14, ink);
        CorkBoardGui.text(g, font, "#" + entry.shortId(), x + W - 30 - font.width("#" + entry.shortId()),
                y + 15, muted);
        CorkBoardGui.blitTint(g, CorkSprites.ICON_CROSS, x + W - 21, y + 15, 5, 5,
                overClose(mouseX, mouseY) ? CorkPalette.ADMIN_ERROR : muted);

        CorkBoardGui.blitTint(g, CorkSprites.stamp(entry.size(), style.largeCargo()), x + 12, y + 28, 17, 15, ink);
        int badgeX = x + 33;
        if (style.expensive()) {
            CorkBoardGui.blit(g, CorkSprites.BADGE_EXP, badgeX, y + 30, 52, 11);
            CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.note.expensive"),
                    badgeX + 4, y + 32, CorkPalette.BADGE_EXP);
            badgeX += 56;
        }
        if (style.rare()) {
            CorkBoardGui.blit(g, CorkSprites.BADGE_RARE, badgeX, y + 30, 48, 11);
            CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.note.rare"),
                    badgeX + 4, y + 32, CorkPalette.BADGE_RARE);
        }

        row(g, font, 55, "ui.aerocouriers.cork.waybill.from_label", snapshot.name(), muted, ink);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.waybill.station_tag"),
                x + 149, y + 55, muted);
        row(g, font, 68, "ui.aerocouriers.cork.waybill.to_label", BoardCardRenderer.destinationName(entry), muted, ink);
        CorkBoardGui.text(g, font, "· " + MoneyFormat.grouped(entry.distance()) + " "
                        + Component.translatable("ui.aerocouriers.cork.blocks").getString(),
                x + 138, y + 68, muted);
        row(g, font, 81, "ui.aerocouriers.cork.waybill.customer", snapshot.name(), muted, ink);
        row(g, font, 94, "ui.aerocouriers.cork.waybill.cargo_label",
                entry.size().toUpperCase() + " · " + entry.weight(), muted, ink);
        row(g, font, 107, "ui.aerocouriers.cork.waybill.reward_label", MoneyFormat.text(entry.reward()), muted,
                CorkPalette.reward(style));
        if (snapshot.bonusPercent() > 0) {
            CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.waybill.rep_bonus",
                    snapshot.bonusPercent()), x + 133, y + 107, CorkPalette.SHEET_OK);
        }
        row(g, font, 120, "ui.aerocouriers.cork.waybill.deposit_label", MoneyFormat.text(entry.collateral()),
                muted, CorkPalette.deposit(style));
        row(g, font, 146, "ui.aerocouriers.cork.waybill.deadline_label", clock(), muted, ink);
        row(g, font, 159, "ui.aerocouriers.cork.waybill.rank_label",
                Component.translatable("ui.aerocouriers.cork.note.rank_needed",
                        BoardCardRenderer.roman(entry.requiredRank())).getString(), muted, ink);
        if (!style.locked()) {
            CorkBoardGui.blitTint(g, CorkSprites.ICON_CHECK, x + 118, y + 160, 7, 5, CorkPalette.SHEET_OK);
            CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.waybill.rank_have",
                    BoardCardRenderer.roman(style.playerRank())), x + 127, y + 159, CorkPalette.SHEET_OK);
        }

        CorkBoardGui.blit(g, CorkSprites.crate(entry.size()), x + W - 76, y + 52, 52, 52);
        String crate = Component.translatable("ui.aerocouriers.cork.waybill.container",
                entry.size().toUpperCase()).getString();
        CorkBoardGui.text(g, font, crate, x + W - 76 + (52 - font.width(crate)) / 2, y + 106, ink);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.cork.waybill.village_order"),
                x + W - 87, y + 116, muted);

        boolean enabled = canConfirm();
        Component label = buttonLabel();
        CorkBoardGui.ButtonStyle styleBtn = enabled ? CorkBoardGui.ButtonStyle.GOLD : CorkBoardGui.ButtonStyle.NORMAL;
        CorkBoardGui.button(g, font, btnX, btnY, btnW, 20, label,
                enabled && overButton(mouseX, mouseY), enabled, styleBtn);
        CorkBoardGui.button(g, font, closeBtnX, closeBtnY, closeBtnW, 20,
                Component.translatable("ui.aerocouriers.cork.waybill.close"),
                overCloseBtn(mouseX, mouseY), true, CorkBoardGui.ButtonStyle.NORMAL);
        CorkBoardGui.text(g, font, Component.translatable(enabled
                        ? "ui.aerocouriers.cork.waybill.confirm_hint"
                        : "ui.aerocouriers.cork.waybill.need_tablet"),
                x + 12, y + H - 11, muted);
    }

    private void row(GuiGraphics g, Font font, int relY, String key, String value, int muted, int ink) {
        CorkBoardGui.text(g, font, Component.translatable(key), x + 12, y + relY, muted);
        CorkBoardGui.text(g, font, value, x + 68, y + relY, ink);
    }

    private String clock() {
        long m = Math.max(0, entry.minutesLeft());
        if (m >= 60) {
            return (m / 60) + Component.translatable("ui.aerocouriers.cork.hours").getString();
        }
        return m + Component.translatable("ui.aerocouriers.cork.minutes_short").getString();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (overClose(mouseX, mouseY) || overCloseBtn(mouseX, mouseY)) {
            return true;
        }
        if (overButton(mouseX, mouseY) && canConfirm()) {
            ClientBoard.focusContract(entry.id());
            return true;
        }
        return contains(mouseX, mouseY);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + W && mouseY >= y && mouseY < y + H;
    }

    public boolean overButton(double mouseX, double mouseY) {
        return mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + 20;
    }

    public boolean overClose(double mouseX, double mouseY) {
        return mouseX >= closeX - 2 && mouseX < closeX + 13 && mouseY >= closeY - 2 && mouseY < closeY + 13;
    }

    private boolean overCloseBtn(double mouseX, double mouseY) {
        return mouseX >= closeBtnX && mouseX < closeBtnX + closeBtnW && mouseY >= closeBtnY && mouseY < closeBtnY + 20;
    }

    public Component hint() {
        if (style.inProgress()) {
            return Component.translatable("ui.aerocouriers.cork.waybill.in_progress",
                    style.courierName().isBlank() ? "—" : style.courierName());
        }
        if (snapshot.view().requireTablet() && !snapshot.view().hasTablet()) {
            return Component.translatable("ui.aerocouriers.cork.waybill.need_tablet");
        }
        return Component.empty();
    }

    private boolean canConfirm() {
        if (style.inProgress() || style.locked()) {
            return false;
        }
        return !snapshot.view().requireTablet() || snapshot.view().hasTablet();
    }

    private Component buttonLabel() {
        if (style.inProgress()) {
            return Component.translatable("ui.aerocouriers.cork.waybill.in_progress",
                    style.courierName().isBlank() ? "—" : style.courierName());
        }
        return Component.translatable("ui.aerocouriers.cork.waybill.confirm");
    }
}
