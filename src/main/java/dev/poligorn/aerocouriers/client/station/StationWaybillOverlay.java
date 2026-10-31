package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Waybill sheet over the cork board. The station never accepts; confirm opens the tablet order window. */
public final class StationWaybillOverlay {
    private final StationSnapshotPayload snapshot;
    private final BoardSnapshotPayload.BoardEntry entry;
    private final CorkNoteStyle style;
    private int x;
    private int y;
    private int w;
    private int h;
    private int btnX;
    private int btnY;
    private int btnW;
    private int closeX;
    private int closeY;

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
        w = Math.min(280, layout.innerW - 24);
        h = Math.min(layout.compact ? 180 : 210, layout.innerH - 16);
        x = layout.winX + (layout.winW - w) / 2;
        y = layout.winY + (layout.winH - h) / 2;
        btnW = w - 24;
        btnX = x + 12;
        btnY = y + h - 26;
        closeX = x + w - 14;
        closeY = y + 6;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0x88000000);
        CorkBoardGui.paper(graphics, x, y, w, h, CorkBoardGui.CREAM, 0xFFB0A070);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.waybill.title"),
                x + 10, y + 8, CorkBoardGui.INK, false);
        boolean closeHover = overClose(mouseX, mouseY);
        graphics.drawString(font, "×", closeX, closeY, closeHover ? CorkBoardGui.RED : CorkBoardGui.INK_MUTED, false);

        int ty = y + 22;
        int tx = x + 10;
        int max = w - 20;
        ty = line(graphics, font, tx, ty, max, Component.translatable("ui.aerocouriers.cork.waybill.from",
                snapshot.name()));
        ty = line(graphics, font, tx, ty, max, Component.translatable("ui.aerocouriers.cork.waybill.to",
                BoardCardRenderer.destinationName(entry)));
        ty = line(graphics, font, tx, ty, max, Component.translatable("ui.aerocouriers.cork.waybill.meta",
                entry.size(), entry.weight(), entry.distance(), entry.minutesLeft()));
        if (!style.hideReward()) {
            ty = line(graphics, font, tx, ty, max, Component.translatable("ui.aerocouriers.cork.waybill.pay",
                    MoneyFormat.text(entry.reward()), MoneyFormat.text(entry.collateral())));
        } else {
            ty = line(graphics, font, tx, ty, max, Component.translatable("ui.aerocouriers.cork.note.rank_needed",
                    BoardCardRenderer.roman(entry.requiredRank())));
        }
        if (style.inProgress()) {
            ty = line(graphics, font, tx, ty, max, Component.translatable("ui.aerocouriers.cork.waybill.in_progress",
                    style.courierName().isBlank() ? "—" : style.courierName()));
        }
        List<BoardSnapshotPayload.CargoLine> manifest = entry.manifest();
        if (!manifest.isEmpty()) {
            ty += 2;
            graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.waybill.cargo"),
                    tx, ty, CorkBoardGui.INK, false);
            ty += 10;
            int shown = 0;
            for (BoardSnapshotPayload.CargoLine line : manifest) {
                if (ty > btnY - 14 || shown >= 4) {
                    if (manifest.size() > shown) {
                        graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.waybill.cargo_more",
                                manifest.size() - shown), tx, ty, CorkBoardGui.INK_MUTED, false);
                    }
                    break;
                }
                graphics.drawString(font, line.count() + "× " + CorkBoardGui.ellipsize(font, line.name(), max - 20),
                        tx, ty, CorkBoardGui.INK_MUTED, false);
                ty += 10;
                shown++;
            }
        }

        boolean enabled = canConfirm();
        Component label = buttonLabel();
        boolean hover = enabled && overButton(mouseX, mouseY);
        CorkBoardGui.button(graphics, font, btnX, btnY, btnW, 18, label, hover, enabled, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (overClose(mouseX, mouseY)) {
            return true;
        }
        if (overButton(mouseX, mouseY) && canConfirm()) {
            ClientBoard.focusContract(entry.id());
            return true;
        }
        return contains(mouseX, mouseY);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public boolean overButton(double mouseX, double mouseY) {
        return mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + 18;
    }

    public boolean overClose(double mouseX, double mouseY) {
        return mouseX >= closeX - 2 && mouseX < closeX + 10 && mouseY >= closeY - 2 && mouseY < closeY + 10;
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
        if (style.inProgress()) {
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

    private static int line(GuiGraphics graphics, Font font, int x, int y, int max, Component text) {
        for (FormattedCharSequence row : font.split(text, max)) {
            graphics.drawString(font, row, x, y, CorkBoardGui.INK, false);
            y += 10;
        }
        return y;
    }
}
