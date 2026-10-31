package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.ClientCouriersSounds;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import java.util.List;
import java.util.UUID;
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
    private @Nullable List<Component> hoverTipLines;
    private @Nullable CorkBoardLayout.Rect hoverNote;
    private int qaHoverIndex = -1;

    public CorkBoardScreen(StationSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.cork.title", snapshot.name()));
        this.snapshot = snapshot;
        syncRefresh();
    }

    public void apply(StationSnapshotPayload payload) {
        this.snapshot = payload;
        syncRefresh();
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

    /** QA / click: open the courier tablet on this village order. */
    public void openOrderOnTablet(BoardSnapshotPayload.BoardEntry entry) {
        if (readOnly()) {
            hoverTipLines = List.of(Component.translatable("ui.aerocouriers.cork.need_tablet"));
            ClientCouriersSounds.playOrderDeny();
            return;
        }
        ClientBoard.focusContract(entry.id(), entry, snapshot.orders());
    }

    /** QA: force the first visible note's hover tooltip without a GLFW cursor. */
    public void qaHoverFirstNote() {
        qaHoverIndex = 0;
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
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = CorkBoardLayout.of(width, height);
        hoverTipLines = null;
        hoverNote = null;
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
            if (!layout.narrow && layout.cork.w() == 604) {
                CorkBoardGui.blit(graphics, CorkSprites.GLASS, layout.cork.x(), layout.cork.y(), 604, 243);
            } else {
                graphics.fill(layout.cork.x(), layout.cork.y(),
                        layout.cork.x() + layout.cork.w(), layout.cork.y() + layout.cork.h(), 0x24001840);
            }
        }
        drawFooter(graphics, mouseX, mouseY);
        CorkBoardGui.scrollBar(graphics, layout.scroll, scroll, layout.maxScrollRows(orders.size()),
                layout.visibleRows, layout.totalRows(orders.size()));
        if (hoverTipLines != null && !hoverTipLines.isEmpty()) {
            int[] box = CorkNoteTooltip.size(font, hoverTipLines);
            CorkBoardLayout.Rect note = hoverNote != null
                    ? hoverNote
                    : new CorkBoardLayout.Rect(mouseX, mouseY, 1, 1);
            CorkBoardLayout.Rect tip = CorkNoteTooltip.place(width, height, note, box[0], box[1]);
            CorkNoteTooltip.render(graphics, font, hoverTipLines, tip.x(), tip.y());
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
        int headerRight = layout.headerPlank.x() + layout.headerPlank.w() - 8;
        int chipLeftMin = layout.nameplate.x() + layout.nameplate.w() + 8;
        int chipAvail = Math.max(layout.timerChip.w(), headerRight - chipLeftMin);
        int timer = VillageRefreshUi.remainingTicks(minecraft);
        String chip = timer < 0 ? "" : VillageRefreshUi.chipText(timer, Math.max(8, chipAvail - 19), font::width);
        int chipW = chip.isBlank()
                ? layout.timerChip.w()
                : Math.min(chipAvail, Math.max(layout.timerChip.w(), 19 + font.width(chip)));
        int chipX = headerRight - chipW;
        int chipY = layout.timerChip.y();
        int titleMax = Math.max(24, chipX - layout.titleX - 8);
        CorkBoardGui.bold(graphics, font, font.width(title) <= titleMax ? title
                        : CorkBoardGui.ellipsize(font, title, titleMax),
                layout.titleX, layout.titleY, CorkPalette.HEADER_TITLE);
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.cork.subtitle"),
                layout.subtitleX, layout.subtitleY, CorkPalette.HEADER_SUBTITLE);

        if (!chip.isBlank()) {
            CorkBoardGui.blit(graphics, CorkSprites.CHIP_DARK, chipX, chipY, chipW, layout.timerChip.h());
            CorkBoardGui.blitTint(graphics, CorkSprites.ICON_CLOCK, chipX + 4, chipY + 2, 9, 8, CorkPalette.TIMER);
            CorkBoardGui.text(graphics, font, chip, chipX + 15, chipY + 3, CorkPalette.TIMER);
        }

        String repLabel = Component.translatable(layout.narrow
                ? "ui.aerocouriers.cork.reputation_label_short"
                : "ui.aerocouriers.cork.reputation_label").getString();
        String bonus = Component.translatable("ui.aerocouriers.cork.bonus", snapshot.bonusPercent()).getString();
        LicenseRank rank = snapshot.view().playerRankLevel() <= 0
                ? LicenseRank.ADMIN
                : LicenseRank.byLevel(snapshot.view().playerRankLevel());
        String rankText = Component.translatable("ui.aerocouriers.cork.rank", rank.displayName()).getString();
        String repValue = Integer.toString(snapshot.reputation());
        int labelW = font.width(repLabel);
        int valueW = font.width(repValue);
        int rankW = font.width(rankText);
        int barW = layout.narrow ? 80 : 150;
        int rankDrawX = layout.narrow ? layout.rankX : headerRight - rankW;
        int rowRight = layout.narrow ? headerRight : rankDrawX - 8;
        int overflow = (layout.repLabelX + labelW + 6 + barW + 6 + valueW + 6) - rowRight;
        if (overflow > 0) {
            barW = Math.max(40, barW - overflow);
        }
        CorkBoardGui.text(graphics, font, repLabel, layout.repLabelX, layout.repLabelY, CorkPalette.HEADER_MUTED);
        int barX = layout.repLabelX + labelW + 6;
        CorkBoardGui.reputationBar(graphics, barX, layout.repLabelY - 1, barW, 7, snapshot.reputation(), 50);
        int valueX = barX + barW + 6;
        CorkBoardGui.bold(graphics, font, repValue, valueX, layout.repLabelY, CorkPalette.REPUTATION);
        int bonusX = valueX + valueW + 6;
        int bonusMax = Math.max(0, rowRight - bonusX);
        if (bonusMax >= 12) {
            if (font.width(bonus) > bonusMax) {
                bonus = Component.translatable("ui.aerocouriers.cork.bonus_short", snapshot.bonusPercent()).getString();
            }
            CorkBoardGui.text(graphics, font, CorkBoardGui.ellipsize(font, bonus, bonusMax),
                    bonusX, layout.repLabelY, CorkPalette.REPUTATION);
        }
        CorkBoardGui.text(graphics, font, rankText, rankDrawX, layout.rankY, CorkPalette.HEADER_MUTED);

        if (readOnly()) {
            CorkBoardGui.blit(graphics, CorkSprites.BANNER, layout.filterBar);
            CorkBoardGui.blitTint(graphics, CorkSprites.ICON_WARN, layout.filterBar.x() + 8,
                    layout.filterBar.y() + 4, 7, 6, CorkPalette.BANNER_ICON);
            CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.cork.readonly.banner"),
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
            CorkBoardGui.blit(graphics, CorkSprites.EMPTY_PAPER,
                    layout.cork.x() + (layout.cork.w() - 280) / 2,
                    layout.cork.y() + (layout.cork.h() - 118) / 2, 280, 118);
            Component empty = Component.translatable("ui.aerocouriers.cork.empty");
            CorkBoardGui.bold(graphics, font, empty,
                    layout.cork.x() + (layout.cork.w() - font.width(empty)) / 2,
                    layout.cork.y() + layout.cork.h() / 2 - 4, CorkPalette.NOTE_INK);
            return;
        }
        int start = scroll * layout.cols;
        int end = Math.min(orders.size(), start + layout.cols * layout.drawRows);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % layout.cols;
            int row = local / layout.cols;
            int nx = layout.noteX(col, i);
            int ny = layout.noteY(row, i);
            BoardSnapshotPayload.BoardEntry entry = orders.get(i);
            boolean hover = mouseX >= nx && mouseX < nx + layout.noteW
                    && mouseY >= ny && mouseY < ny + layout.noteH
                    || qaHoverIndex == i;
            CorkNoteStyle noteStyle = style(entry);
            CorkNoteRenderer.render(graphics, font, entry, noteStyle, nx, ny, layout.noteW, layout.noteH,
                    hover, false);
            if (hover) {
                hoverTipLines = CorkNoteTooltip.lines(entry, noteStyle, readOnly());
                hoverNote = new CorkBoardLayout.Rect(nx, ny, layout.noteW, layout.noteH);
            }
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        CorkBoardGui.text(graphics, font, Component.translatable(
                        "ui.aerocouriers.cork.orders_count", snapshot.orders().size()),
                layout.footerCountX, layout.footerCountY, CorkPalette.FOOTER);
        if (!layout.narrow) {
            Component hint = Component.translatable(readOnly()
                    ? "ui.aerocouriers.cork.readonly.hint"
                    : "ui.aerocouriers.cork.hint");
            CorkBoardGui.text(graphics, font, hint, layout.footerHintX, layout.footerHintY, CorkPalette.FOOTER_HINT);
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
        int end = Math.min(orders.size(), start + layout.cols * layout.drawRows);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % layout.cols;
            int row = local / layout.cols;
            int nx = layout.noteX(col, i);
            int ny = layout.noteY(row, i);
            if (mouseX >= nx && mouseX < nx + layout.noteW && mouseY >= ny && mouseY < ny + layout.noteH) {
                openOrderOnTablet(orders.get(i));
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
    public boolean isPauseScreen() {
        return false;
    }

    private CorkNoteStyle style(BoardSnapshotPayload.BoardEntry entry) {
        int index = snapshot.orders().indexOf(entry);
        return CorkNoteStyle.of(entry, snapshot.view().playerRankLevel(), snapshot.view().courierName(index));
    }

    public @Nullable UUID firstVisibleOrderId() {
        List<BoardSnapshotPayload.BoardEntry> orders = filters.apply(snapshot.orders());
        return orders.isEmpty() ? null : orders.getFirst().id();
    }

    private static boolean over(double mx, double my, CorkBoardLayout.Rect r) {
        return r.contains(mx, my);
    }
}
