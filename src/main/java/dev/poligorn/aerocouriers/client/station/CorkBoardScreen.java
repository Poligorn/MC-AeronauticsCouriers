package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.InGamePauseBackground;
import dev.poligorn.aerocouriers.client.TooltipDraw;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Village post-station cork board: pinned notes, scroll by rows, no accept on the station. */
public final class CorkBoardScreen extends Screen {
    private StationSnapshotPayload snapshot;
    private final CorkBoardFilters filters = new CorkBoardFilters();
    private CorkBoardLayout layout;
    private int scroll;
    private @Nullable StationWaybillOverlay waybill;
    private @Nullable Component hoverTip;

    public CorkBoardScreen(StationSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.cork.title", snapshot.name()));
        this.snapshot = snapshot;
        syncRefresh();
    }

    public void apply(StationSnapshotPayload payload) {
        this.snapshot = payload;
        syncRefresh();
        if (waybill != null) {
            BoardSnapshotPayload.BoardEntry found = find(waybill.entry().id());
            waybill = found == null ? null : new StationWaybillOverlay(payload, found, style(found));
        }
    }

    private void syncRefresh() {
        if (VillageRefreshUi.isEnabled(snapshot.nextVillageRefreshGameTime())) {
            VillageRefreshUi.applySnapshot(snapshot.nextVillageRefreshGameTime(),
                    snapshot.snapshotOverworldGameTime());
        } else {
            VillageRefreshUi.clear();
        }
    }

    @Override
    protected void init() {
        layout = CorkBoardLayout.of(width, height);
        super.init();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = CorkBoardLayout.of(width, height);
        hoverTip = null;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        CorkBoardGui.dimWorld(graphics, width, height);
        CorkBoardGui.frame(graphics, layout.winX, layout.winY, layout.winW, layout.winH);
        CorkBoardGui.headerBand(graphics, layout.innerX, layout.innerY, layout.innerW, layout.headerH);
        CorkBoardGui.cork(graphics, layout.corkX, layout.corkY, layout.corkW, layout.corkH);
        drawHeader(graphics, mouseX, mouseY);
        List<BoardSnapshotPayload.BoardEntry> orders = filters.apply(snapshot.orders());
        scroll = Mth.clamp(scroll, 0, layout.maxScrollRows(orders.size()));
        drawNotes(graphics, orders, mouseX, mouseY);
        drawFooter(graphics, orders.size(), mouseX, mouseY);
        CorkBoardGui.scrollBar(graphics, layout.scrollX, layout.scrollY, CorkBoardLayout.SCROLL_W, layout.scrollH,
                scroll, layout.maxScrollRows(orders.size()));
        if (waybill != null) {
            graphics.fill(layout.innerX, layout.innerY, layout.innerX + layout.innerW,
                    layout.innerY + layout.innerH, 0x66000000);
            waybill.layout(layout);
            waybill.render(graphics, font, mouseX, mouseY);
            Component hint = waybill.hint();
            if (!hint.getString().isEmpty() && waybill.overButton(mouseX, mouseY)) {
                hoverTip = hint;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTip != null) {
            TooltipDraw.render(graphics, font, List.of(hoverTip), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = layout.innerX + 8;
        int y = layout.innerY + 5;
        graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.station", snapshot.name()),
                x, y, CorkBoardGui.HEADER, false);
        int timer = VillageRefreshUi.remainingTicks(minecraft);
        Component timerLabel = timer < 0
                ? Component.empty()
                : VillageRefreshUi.countdownLabel(timer);
        if (!timerLabel.getString().isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.timer", timerLabel),
                    layout.innerX + layout.innerW - 8 - font.width(Component.translatable(
                            "ui.aerocouriers.cork.timer", timerLabel)),
                    y, CorkBoardGui.HEADER, false);
        }
        y += 12;
        CorkBoardGui.reputationBar(graphics, x, y, Math.min(220, layout.innerW / 2), 7,
                snapshot.reputation(), VillageBoardPolicy.REPUTATION_MAX);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.reputation",
                        snapshot.reputation(), VillageBoardPolicy.REPUTATION_MAX,
                        snapshot.bonusPercent()),
                x + Math.min(220, layout.innerW / 2) + 6, y, CorkBoardGui.HEADER, false);
        LicenseRank rank = snapshot.view().playerRankLevel() <= 0
                ? LicenseRank.ADMIN
                : LicenseRank.byLevel(snapshot.view().playerRankLevel());
        Component rankLabel = Component.translatable("ui.aerocouriers.cork.rank",
                BoardCardRenderer.roman(rank), rank.displayName());
        graphics.drawString(font, rankLabel,
                layout.innerX + layout.innerW - 8 - font.width(rankLabel), y, CorkBoardGui.HEADER, false);

        y = layout.innerY + layout.headerH - 20;
        int chipX = x;
        chipX = drawChip(graphics, chipX, y, filters.sizeLabel(), mouseX, mouseY);
        chipX = drawChip(graphics, chipX, y, filters.rarityLabel(), mouseX, mouseY);
        drawChip(graphics, chipX, y, filters.sortLabel(), mouseX, mouseY);

        if (snapshot.view().canAdmin()) {
            Component admin = Component.translatable("ui.aerocouriers.cork.admin");
            int aw = CorkBoardGui.chipW(font, admin);
            int ax = layout.innerX + layout.innerW - 8 - aw;
            boolean hover = mouseX >= ax && mouseX < ax + aw && mouseY >= y && mouseY < y + 14;
            CorkBoardGui.chip(graphics, font, ax, y, aw, 14, admin, false, hover);
        }
    }

    private int drawChip(GuiGraphics graphics, int x, int y, Component label, int mouseX, int mouseY) {
        int w = CorkBoardGui.chipW(font, label);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        CorkBoardGui.chip(graphics, font, x, y, w, 14, label, false, hover);
        return x + w + 4;
    }

    private void drawNotes(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> orders, int mouseX, int mouseY) {
        if (orders.isEmpty()) {
            Component empty = Component.translatable("ui.aerocouriers.cork.empty");
            graphics.drawString(font, empty,
                    layout.corkX + (layout.corkW - font.width(empty)) / 2,
                    layout.corkY + layout.corkH / 2 - 4, CorkBoardGui.INK_MUTED, false);
            return;
        }
        int start = scroll * layout.cols;
        int end = Math.min(orders.size(), start + layout.cols * layout.visibleRows);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % layout.cols;
            int row = local / layout.cols;
            int seed = orders.get(i).id().hashCode();
            int nx = layout.noteX(col, layout.jitter(seed, 0));
            int ny = layout.noteY(row, layout.jitter(seed, 1));
            BoardSnapshotPayload.BoardEntry entry = orders.get(i);
            boolean hover = waybill == null && mouseX >= nx && mouseX < nx + layout.noteW
                    && mouseY >= ny && mouseY < ny + layout.noteH;
            CorkNoteRenderer.render(graphics, font, entry, style(entry), nx, ny, layout.noteW, layout.noteH,
                    hover, layout.compact);
        }
    }

    private void drawFooter(GuiGraphics graphics, int visible, int mouseX, int mouseY) {
        int y = layout.innerY + layout.innerH - layout.footerH + 8;
        graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.orders_count",
                        visible, snapshot.view().boardMax()),
                layout.innerX + 8, y, CorkBoardGui.HEADER, false);
        if (!layout.compact) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.cork.hint"),
                    layout.innerX + 8, y + 10, CorkBoardGui.INK_MUTED, false);
        }
        Component tablet = Component.translatable("ui.aerocouriers.cork.open_tablet");
        int bw = Math.max(120, font.width(tablet) + 16);
        int bx = layout.innerX + layout.innerW - 8 - bw;
        int by = y - 2;
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16;
        CorkBoardGui.button(graphics, font, bx, by, bw, 16, tablet, hover, true, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (waybill != null) {
            if (waybill.overClose(mouseX, mouseY) || !waybill.contains(mouseX, mouseY)) {
                waybill = null;
                return true;
            }
            return waybill.mouseClicked(mouseX, mouseY);
        }
        int y = layout.innerY + layout.headerH - 20;
        int chipX = layout.innerX + 8;
        if (clickChip(mouseX, mouseY, chipX, y, filters.sizeLabel())) {
            cycleSize();
            return true;
        }
        chipX += CorkBoardGui.chipW(font, filters.sizeLabel()) + 4;
        if (clickChip(mouseX, mouseY, chipX, y, filters.rarityLabel())) {
            filters.cycleRarity();
            return true;
        }
        chipX += CorkBoardGui.chipW(font, filters.rarityLabel()) + 4;
        if (clickChip(mouseX, mouseY, chipX, y, filters.sortLabel())) {
            filters.cycleSort();
            return true;
        }
        if (snapshot.view().canAdmin()) {
            Component admin = Component.translatable("ui.aerocouriers.cork.admin");
            int aw = CorkBoardGui.chipW(font, admin);
            int ax = layout.innerX + layout.innerW - 8 - aw;
            if (clickChip(mouseX, mouseY, ax, y, admin)) {
                PacketDistributor.sendToServer(VillageAdminPayload.open(snapshot.stationPos()));
                return true;
            }
        }
        Component tablet = Component.translatable("ui.aerocouriers.cork.open_tablet");
        int bw = Math.max(120, font.width(tablet) + 16);
        int bx = layout.innerX + layout.innerW - 8 - bw;
        int by = layout.innerY + layout.innerH - layout.footerH + 6;
        if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16) {
            onClose();
            ClientBoard.requestOpen(0);
            return true;
        }
        List<BoardSnapshotPayload.BoardEntry> orders = filters.apply(snapshot.orders());
        int start = scroll * layout.cols;
        int end = Math.min(orders.size(), start + layout.cols * layout.visibleRows);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % layout.cols;
            int row = local / layout.cols;
            int seed = orders.get(i).id().hashCode();
            int nx = layout.noteX(col, layout.jitter(seed, 0));
            int ny = layout.noteY(row, layout.jitter(seed, 1));
            if (mouseX >= nx && mouseX < nx + layout.noteW && mouseY >= ny && mouseY < ny + layout.noteH) {
                BoardSnapshotPayload.BoardEntry entry = orders.get(i);
                waybill = new StationWaybillOverlay(snapshot, entry, style(entry));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickChip(double mouseX, double mouseY, int x, int y, Component label) {
        int w = CorkBoardGui.chipW(font, label);
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
    }

    private void cycleSize() {
        if (filters.allSizes()) {
            filters.toggleSize(CargoSize.S);
            for (CargoSize size : CargoSize.values()) {
                if (size != CargoSize.S && filters.sizes().contains(size)) {
                    filters.toggleSize(size);
                }
            }
            return;
        }
        CargoSize current = filters.sizes().iterator().next();
        CargoSize[] all = CargoSize.values();
        int next = current.ordinal() + 1;
        if (next >= all.length) {
            for (CargoSize size : all) {
                if (!filters.sizes().contains(size)) {
                    filters.toggleSize(size);
                }
            }
            return;
        }
        filters.toggleSize(all[next]);
        filters.toggleSize(current);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = layout.maxScrollRows(filters.apply(snapshot.orders()).size());
        int next = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, max);
        if (next != scroll) {
            scroll = next;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && waybill != null) {
            waybill = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private CorkNoteStyle style(BoardSnapshotPayload.BoardEntry entry) {
        int index = snapshot.orders().indexOf(entry);
        return CorkNoteStyle.of(entry, snapshot.view().playerRankLevel(), snapshot.view().courierName(index));
    }

    private @Nullable BoardSnapshotPayload.BoardEntry find(java.util.UUID id) {
        for (BoardSnapshotPayload.BoardEntry entry : snapshot.orders()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}
