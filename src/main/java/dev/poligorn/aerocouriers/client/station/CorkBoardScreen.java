package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.TooltipDraw;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Village post-station cork board. World stays visible around the frame; no accept on the station. */
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

    public void openWaybill(BoardSnapshotPayload.BoardEntry entry) {
        waybill = new StationWaybillOverlay(snapshot, entry, style(entry));
    }

    public void closeWaybill() {
        waybill = null;
    }

    public void setScrollRows(int rows) {
        layout = CorkBoardLayout.of(width, height);
        scroll = Mth.clamp(rows, 0, layout.maxScrollRows(filters.apply(snapshot.orders()).size()));
    }

    public List<BoardSnapshotPayload.BoardEntry> visibleOrders() {
        return filters.apply(snapshot.orders());
    }

    public CorkNoteStyle noteStyle(BoardSnapshotPayload.BoardEntry entry) {
        return style(entry);
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
        // World stays visible. No dim and no full-screen mask — HUD is skipped by CorkHudGuard.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = CorkBoardLayout.of(width, height);
        hoverTip = null;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        CorkBoardGui.fullBright();
        CorkBoardGui.boardWindow(graphics, layout);
        drawHeader(graphics, mouseX, mouseY);
        List<BoardSnapshotPayload.BoardEntry> orders = filters.apply(snapshot.orders());
        scroll = Mth.clamp(scroll, 0, layout.maxScrollRows(orders.size()));
        graphics.enableScissor(layout.cork.x(), layout.cork.y(),
                layout.cork.x() + layout.cork.w(), layout.cork.y() + layout.cork.h());
        drawNotes(graphics, orders, mouseX, mouseY);
        graphics.disableScissor();
        if (readOnly()) {
            if (!layout.compact) {
                CorkBoardGui.blit(graphics, CorkSprites.GLASS, layout.cork.x(), layout.cork.y(), 604, 243);
            } else {
                graphics.fill(layout.cork.x(), layout.cork.y(),
                        layout.cork.x() + layout.cork.w(), layout.cork.y() + layout.cork.h(), 0x24001840);
            }
        }
        drawFooter(graphics, mouseX, mouseY);
        CorkBoardGui.scrollBar(graphics, layout.scroll, scroll, layout.maxScrollRows(orders.size()),
                layout.visibleRows, layout.totalRows(orders.size()));
        if (waybill != null) {
            waybill.layout(layout);
            waybill.render(graphics, font, mouseX, mouseY);
            Component hint = waybill.hint();
            if (!hint.getString().isEmpty() && waybill.overButton(mouseX, mouseY)) {
                hoverTip = hint;
            }
        }
        if (hoverTip != null) {
            TooltipDraw.render(graphics, font, List.of(hoverTip), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private boolean readOnly() {
        return snapshot.view().requireTablet() && !snapshot.view().hasTablet();
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        CorkBoardGui.blit(graphics, CorkSprites.NAMEPLATE, layout.nameplate);
        CorkBoardGui.blit(graphics, CorkSprites.HOUSE_BOX, layout.houseX - 4, layout.houseY - 3, 17, 13);
        CorkBoardGui.blitTint(graphics, CorkSprites.ICON_HOUSE, layout.houseX, layout.houseY, 7, 7,
                CorkPalette.HEADER_TITLE);
        String title = Component.translatable("ui.aerocouriers.cork.station", snapshot.name()).getString();
        int titleMax = Math.max(24, layout.timerChip.x() - layout.titleX - 8);
        CorkBoardGui.bold(graphics, font, CorkBoardGui.ellipsize(font, title, titleMax),
                layout.titleX, layout.titleY, CorkPalette.HEADER_TITLE);
        if (!layout.compact) {
            CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.cork.subtitle"),
                    layout.subtitleX, layout.subtitleY, CorkPalette.HEADER_SUBTITLE);
        }

        CorkBoardGui.blit(graphics, CorkSprites.CHIP_DARK, layout.timerChip);
        int timer = VillageRefreshUi.remainingTicks(minecraft);
        Component timerLabel = timer < 0 ? Component.empty() : VillageRefreshUi.countdownLabel(timer);
        if (!timerLabel.getString().isEmpty()) {
            CorkBoardGui.blitTint(graphics, CorkSprites.ICON_CLOCK, layout.timerChip.x() + 4,
                    layout.timerChip.y() + 2, 9, 8, CorkPalette.TIMER);
            CorkBoardGui.text(graphics, font, timerLabel, layout.timerChip.x() + 15, layout.timerChip.y() + 3,
                    CorkPalette.TIMER);
        }

        CorkBoardGui.text(graphics, font, Component.translatable(layout.compact
                        ? "ui.aerocouriers.cork.reputation_label_short"
                        : "ui.aerocouriers.cork.reputation_label"),
                layout.repLabelX, layout.repLabelY, CorkPalette.HEADER_MUTED);
        CorkBoardGui.reputationBar(graphics, layout.repBarX, layout.repBarY, layout.repBarW, 7,
                snapshot.reputation(), 50);
        CorkBoardGui.bold(graphics, font, Integer.toString(snapshot.reputation()),
                layout.repValueX, layout.repValueY, CorkPalette.REPUTATION);
        Component bonus = Component.translatable(layout.compact
                        ? "ui.aerocouriers.cork.bonus_short"
                        : "ui.aerocouriers.cork.bonus",
                snapshot.bonusPercent());
        int bonusX = layout.repValueX + font.width(Integer.toString(snapshot.reputation())) + 4;
        int bonusMax = (layout.compact ? layout.headerPlank.x() + layout.headerPlank.w() : layout.rankX) - bonusX - 8;
        CorkBoardGui.text(graphics, font, CorkBoardGui.ellipsize(font, bonus.getString(), Math.max(16, bonusMax)),
                bonusX, layout.repValueY, CorkPalette.REPUTATION);
        if (!layout.compact) {
            LicenseRank rank = snapshot.view().playerRankLevel() <= 0
                    ? LicenseRank.ADMIN
                    : LicenseRank.byLevel(snapshot.view().playerRankLevel());
            String rankText = Component.translatable("ui.aerocouriers.cork.rank",
                    rank.displayName()).getString();
            CorkBoardGui.text(graphics, font, CorkBoardGui.ellipsize(font, rankText,
                            Math.max(24, layout.headerPlank.x() + layout.headerPlank.w() - layout.rankX - 8)),
                    layout.rankX, layout.rankY, CorkPalette.HEADER_MUTED);
        }

        if (readOnly()) {
            CorkBoardGui.blit(graphics, CorkSprites.BANNER, layout.filterBar);
            CorkBoardGui.blitTint(graphics, CorkSprites.ICON_WARN, layout.filterBar.x() + 8,
                    layout.filterBar.y() + 4, 7, 6, CorkPalette.BANNER_ICON);
            CorkBoardGui.text(graphics, font, CorkBoardGui.ellipsize(font,
                            Component.translatable("ui.aerocouriers.cork.readonly.banner").getString(),
                            layout.filterBar.w() - 28),
                    layout.filterBar.x() + 20, layout.filterBar.y() + 5, CorkPalette.BANNER);
        } else {
            CorkBoardGui.chip(graphics, font, layout.chips[0], filters.sizeLabel(),
                    CorkSprites.ICON_BOX, 9, 7, !filters.allSizes(), over(mouseX, mouseY, layout.chips[0]));
            CorkBoardGui.chip(graphics, font, layout.chips[1], filters.rarityLabel(),
                    CorkSprites.ICON_GEM, 7, 6, filters.rareOnly(), over(mouseX, mouseY, layout.chips[1]));
            CorkBoardGui.chip(graphics, font, layout.chips[2], filters.sortLabel(),
                    CorkSprites.ICON_SORT, 5, 8, true, over(mouseX, mouseY, layout.chips[2]));
        }
        if (snapshot.view().canAdmin()) {
            CorkBoardGui.button(graphics, font, layout.adminBtn.x(), layout.adminBtn.y(),
                    layout.adminBtn.w(), layout.adminBtn.h(),
                    Component.translatable("ui.aerocouriers.cork.admin"),
                    over(mouseX, mouseY, layout.adminBtn), true, CorkBoardGui.ButtonStyle.DANGER);
        }
    }

    private void drawNotes(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> orders, int mouseX, int mouseY) {
        if (orders.isEmpty()) {
            if (!layout.compact) {
                CorkBoardGui.blit(graphics, CorkSprites.EMPTY_PAPER,
                        layout.cork.x() + (layout.cork.w() - 280) / 2,
                        layout.cork.y() + (layout.cork.h() - 118) / 2, 280, 118);
            }
            Component empty = Component.translatable("ui.aerocouriers.cork.empty");
            CorkBoardGui.bold(graphics, font, empty,
                    layout.cork.x() + (layout.cork.w() - font.width(empty)) / 2,
                    layout.cork.y() + layout.cork.h() / 2 - 4, CorkPalette.NOTE_INK);
            return;
        }
        int start = scroll * layout.cols;
        int end = Math.min(orders.size(), start + layout.cols * layout.visibleRows);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % layout.cols;
            int row = local / layout.cols;
            int nx = layout.noteX(col, i);
            int ny = layout.noteY(row, i);
            BoardSnapshotPayload.BoardEntry entry = orders.get(i);
            boolean hover = waybill == null && mouseX >= nx && mouseX < nx + layout.noteW
                    && mouseY >= ny && mouseY < ny + layout.noteH;
            CorkNoteRenderer.render(graphics, font, entry, style(entry), nx, ny, layout.noteW, layout.noteH,
                    hover, layout.compact);
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.cork.orders_count",
                        snapshot.orders().size()),
                layout.footerCountX, layout.footerCountY, CorkPalette.FOOTER);
        if (!layout.compact) {
            Component hint = Component.translatable(readOnly()
                    ? "ui.aerocouriers.cork.readonly.hint"
                    : "ui.aerocouriers.cork.hint");
            CorkBoardGui.text(graphics, font, CorkBoardGui.ellipsize(font, hint.getString(),
                            Math.max(24, layout.tabletBtn.x() - layout.footerHintX - 8)),
                    layout.footerHintX, layout.footerHintY, CorkPalette.FOOTER_HINT);
        }
        boolean tabletOk = !readOnly();
        Component tablet = Component.translatable(tabletOk
                ? "ui.aerocouriers.cork.open_tablet"
                : "ui.aerocouriers.cork.readonly.tablet");
        CorkBoardGui.button(graphics, font, layout.tabletBtn.x(), layout.tabletBtn.y(),
                layout.tabletBtn.w(), layout.tabletBtn.h(), tablet,
                tabletOk && over(mouseX, mouseY, layout.tabletBtn), tabletOk, CorkBoardGui.ButtonStyle.GOLD);
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
        if (!readOnly() && over(mouseX, mouseY, layout.chips[0])) {
            cycleSize();
            return true;
        }
        if (!readOnly() && over(mouseX, mouseY, layout.chips[1])) {
            filters.cycleRarity();
            return true;
        }
        if (!readOnly() && over(mouseX, mouseY, layout.chips[2])) {
            filters.cycleSort();
            return true;
        }
        if (snapshot.view().canAdmin() && over(mouseX, mouseY, layout.adminBtn)) {
            PacketDistributor.sendToServer(VillageAdminPayload.open(snapshot.stationPos()));
            return true;
        }
        if (!readOnly() && over(mouseX, mouseY, layout.tabletBtn)) {
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
            int nx = layout.noteX(col, i);
            int ny = layout.noteY(row, i);
            if (mouseX >= nx && mouseX < nx + layout.noteW && mouseY >= ny && mouseY < ny + layout.noteH) {
                waybill = new StationWaybillOverlay(snapshot, orders.get(i), style(orders.get(i)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, max);
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

    private static boolean over(double mx, double my, CorkBoardLayout.Rect r) {
        return r.contains(mx, my);
    }
}
