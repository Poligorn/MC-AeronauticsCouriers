package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.board.BoardCargoTooltip;
import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.client.board.BoardRowLayout;
import dev.poligorn.aerocouriers.client.board.BoardRowMetrics;
import dev.poligorn.aerocouriers.client.board.BoardEntryPickup;
import dev.poligorn.aerocouriers.client.board.BoardRowRenderer;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.client.board.TabletRowIconButton;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.RenameStationPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Post station window: reputation, NPC orders from this station, operator rename (GDD §6.0). */
public final class PostStationScreen extends Screen {
    private final StationSnapshotPayload snapshot;
    private final TabletScreenChrome tabletChrome = new TabletScreenChrome(false);
    private ScreenFit fit = ScreenFit.of(ScreenFit.DESIGN_W, ScreenFit.DESIGN_H);
    private final TabletListRowEffects listRowEffects = new TabletListRowEffects();
    private int scroll;
    private @Nullable EditBox nameField;
    private @Nullable Component hoverTip;
    private @Nullable List<Component> hoverTipLines;
    private @Nullable BoardCargoTooltip hoverCargoComponent;
    private @Nullable UUID mapHoverOrder;
    private @Nullable UUID crateHoverOrder;

    public PostStationScreen(StationSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.station.title", snapshot.name()));
        this.snapshot = snapshot;
        if (VillageRefreshUi.isEnabled(snapshot.nextVillageRefreshGameTime())) {
            VillageRefreshUi.applySnapshot(snapshot.nextVillageRefreshGameTime(), snapshot.snapshotOverworldGameTime());
        } else {
            VillageRefreshUi.clear();
        }
    }

    @Override
    protected void init() {
        refreshFit();
        super.init();
        int cx = contentLeft();
        int cy = contentTop();
        int cw = contentW();
        if (snapshot.canRename()) {
            nameField = new EditBox(font, cx, cy + 20, cw - 80, 18,
                    Component.translatable("ui.aerocouriers.station.rename"));
            nameField.setValue(snapshot.name());
            nameField.setMaxLength(48);
            addRenderableWidget(nameField);
            addRenderableWidget(TabletButton.create(cx + cw - 72, cy + 18, 72, 20,
                    Component.translatable("ui.aerocouriers.station.rename.action"), b -> rename()));
        }
        int btnY = contentTop() + contentH() - 28;
        addRenderableWidget(TabletButton.create(contentLeft(), btnY, 120, 20,
                Component.translatable("ui.aerocouriers.station.open_board"), b -> openBoard()));
        addRenderableWidget(TabletButton.create(contentLeft() + contentW() - 68, btnY, 68, 20,
                Component.translatable("gui.done"), b -> onClose()));
        rebuildRowIcons();
    }

    private void rename() {
        if (nameField == null) {
            return;
        }
        PacketDistributor.sendToServer(new RenameStationPayload(snapshot.stationPos(), nameField.getValue()));
    }

    private void openBoard() {
        onClose();
        ClientBoard.requestOpen(0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        refreshFit();
        mouseX = fit.localX(mouseX);
        mouseY = fit.localY(mouseY);
        if (tabletChrome.blocksInput(false, 0.0F)) {
            return true;
        }
        int max = Math.max(0, snapshot.orders().size() - visibleRows());
        int next = Mth.clamp(scroll - (int) scrollY, 0, max);
        if (next != scroll) {
            scroll = next;
            rebuildRowIcons();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabletChrome.blocksInput(false, 0.0F)) {
            return true;
        }
        refreshFit();
        mouseX = fit.localX(mouseX);
        mouseY = fit.localY(mouseY);
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0 && !isOverRowIcon((int) mouseX, (int) mouseY)) {
                UUID id = snapshot.orders().get(index).id();
                onClose();
                ClientBoard.focusContract(id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        refreshFit();
        return super.mouseReleased(fit.localX(mouseX), fit.localY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        refreshFit();
        double s = fit.scale;
        return super.mouseDragged(fit.localX(mouseX), fit.localY(mouseY), button, dragX / s, dragY / s);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshFit();
        tabletChrome.layout(guiH(), panelTop(), panelH());
        tabletChrome.renderBackground(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (tabletChrome.openClose().isClosing()) {
            return;
        }
        tabletChrome.requestClose(super::onClose);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabletChrome.blocksInput(false, 0.0F)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        hoverTipLines = null;
        hoverCargoComponent = null;
        refreshFit();
        int mx = fit.localXi(mouseX);
        int my = fit.localYi(mouseY);
        tabletChrome.tick();
        graphics.pose().pushPose();
        fit.apply(graphics);
        tabletChrome.layout(guiH(), panelTop(), panelH());
        float cavityAlpha = tabletChrome.cavityContentAlpha(false, false, partialTick);
        tabletChrome.drawTabletBody(graphics, partialTick, () -> {
            TabletGui.drawScreenBackground(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH(), partialTick);
            if (cavityAlpha > 0.001F) {
                TabletAnim.withAlpha(graphics, cavityAlpha, () -> {
                    for (Renderable widget : renderables) {
                        widget.render(graphics, mx, my, partialTick);
                    }
                    int cx = contentLeft();
                    int cy = contentTop();
                    graphics.drawString(font, title, cx, cy, TabletGui.TEXT, false);
                    Component next = snapshot.reputationNextThreshold() < 0
                            ? Component.translatable("ui.aerocouriers.reputation.max_tier")
                            : Component.translatable("ui.aerocouriers.station.reputation_next",
                                    snapshot.reputationNextThreshold());
                    Component rep = Component.translatable("ui.aerocouriers.station.reputation",
                            snapshot.reputation(), snapshot.reputationTier(), snapshot.bonusPercent(), next);
                    graphics.drawString(font, rep, cx, cy + (snapshot.canRename() ? 42 : 20), TabletGui.TEXT_MUTED,
                            false);

                    renderOrderList(graphics, mx, my);
                    renderRowIconsOnTop(graphics, mx, my, partialTick);
                });
            }
            TabletGui.drawFrameChrome(graphics, panelLeft(), panelTop(), panelW(), panelH());
            tabletChrome.drawLoadingGear(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH(), false, false, partialTick);
        });
        updateRowIconHover(mx, my);
        captureRowHoverTips(mx, my);
        graphics.pose().popPose();
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderOrderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = listTopY();
        List<BoardSnapshotPayload.BoardEntry> orders = snapshot.orders();
        int headerY = listTop - font.lineHeight - 2;
        renderStationOrdersHeader(graphics, headerY);
        if (orders.isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.station.orders.empty"),
                    contentLeft(), listTop, TabletGui.TEXT_MUTED, false);
            return;
        }
        int sx = contentLeft() + 2;
        int sw = contentW() - 4;
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, orders.size() - visible));
        int rowH = rowH();
        int listY = listTop;
        String listKey = scroll + "|" + orders.size();
        listRowEffects.onListPopulated(listKey, visible);
        TabletGui.drawListPanel(graphics, sx, listY - 2, sw, visible * rowH + 2);
        int frameLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE;
        int textLeft = frameLeft + BoardRowMetrics.ROW_INSET_LEFT;
        BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, false);
        int textW = Math.max(8, layout.textRight() - textLeft);
        for (int i = 0; i < visible && scroll + i < orders.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = orders.get(scroll + i);
            int rowY = listY + i * rowH;
            int frameRight = layout.crateX() + BoardRowLayout.ICON;
            int frameW = Math.max(8, frameRight - frameLeft);
            boolean hover = mouseX >= frameLeft && mouseX < frameRight
                    && mouseY >= rowY && mouseY < rowY + rowH;
            float hoverStrength = listRowEffects.hoverStrength(i, hover);
            float slideX = listRowEffects.rowSlideX(i);
            float rowAlpha = listRowEffects.rowAlpha(i);
            TabletGui.drawRow(graphics, frameLeft, rowY, frameW, rowH, false, hoverStrength, rowAlpha, slideX);
            graphics.pose().pushPose();
            graphics.pose().translate(slideX, 0.0F, 0.0F);
            TabletAnim.withAlpha(graphics, rowAlpha, () -> {
                BoardRowRenderer.renderLine1(graphics, font, entry, textLeft, BoardRowMetrics.line1Y(rowY), textW,
                        mouseX, mouseY, true, (tip, ignored) -> hoverTip = tip);
                BoardRowRenderer.renderLine2(graphics, font, entry, textLeft, BoardRowMetrics.line2Y(rowY), textW);
            });
            graphics.pose().popPose();
        }
    }

    private void renderStationOrdersHeader(GuiGraphics graphics, int headerY) {
        graphics.drawString(font, Component.translatable("ui.aerocouriers.station.orders.header"),
                contentLeft(), headerY, TabletGui.TEXT, false);
        if (!VillageRefreshUi.isEnabled(snapshot.nextVillageRefreshGameTime())) {
            return;
        }
        int remaining = VillageRefreshUi.remainingTicks(minecraft);
        if (remaining < 0) {
            return;
        }
        VillageRefreshUi.renderHeader(graphics, font, contentLeft(), contentLeft() + contentW(), headerY, remaining);
    }

    private void rebuildRowIcons() {
        removeRowIcons();
        List<BoardSnapshotPayload.BoardEntry> orders = snapshot.orders();
        if (orders.isEmpty()) {
            return;
        }
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, orders.size() - visible));
        int sx = contentLeft() + 2;
        int sw = contentW() - 4;
        int listY = listTopY();
        int rowH = rowH();
        for (int i = 0; i < visible && scroll + i < orders.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = orders.get(scroll + i);
            int rowY = listY + i * rowH;
            BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, false);
            int iconY = BoardRowMetrics.buttonY(rowY);
            addRenderableWidget(new TabletRowIconButton(
                    layout.mapX(), iconY, entry.id(), TabletRowIconButton.Kind.MAP, this::onMapIconClick));
            addRenderableWidget(new TabletRowIconButton(
                    layout.crateX(), iconY, entry.id(), TabletRowIconButton.Kind.CRATE, null));
        }
    }

    private void removeRowIcons() {
        List<AbstractWidget> remove = new ArrayList<>();
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton) {
                remove.add((AbstractWidget) widget);
            }
        }
        for (AbstractWidget widget : remove) {
            removeWidget(widget);
        }
    }

    private void onMapIconClick(UUID id) {
        findEntry(id).ifPresent(entry -> XaeroOrderBridge.onHoverDestination(entry.id(), entry.destinationDimension(),
                entry.destinationPos(), entry.shortId()));
    }

    private void renderRowIconsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton icon) {
                icon.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void updateRowIconHover(int mouseX, int mouseY) {
        UUID nextMap = null;
        UUID nextCrate = null;
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton icon && icon.isMouseOver(mouseX, mouseY)) {
                if (icon.kind() == TabletRowIconButton.Kind.MAP) {
                    nextMap = icon.orderId();
                } else {
                    nextCrate = icon.orderId();
                }
            }
        }
        if (nextMap != null) {
            final UUID hovered = nextMap;
            findEntry(hovered).ifPresent(entry -> {
                hoverTip = buildMapTooltip(entry);
                if (!Objects.equals(mapHoverOrder, hovered)) {
                    XaeroOrderBridge.onHoverDestination(entry.id(), entry.destinationDimension(), entry.destinationPos(),
                            entry.shortId());
                }
            });
        } else if (mapHoverOrder != null) {
            findEntry(mapHoverOrder).ifPresent(e -> XaeroOrderBridge.onLeaveDestination(e.id()));
        }
        mapHoverOrder = nextMap;

        if (nextCrate != null) {
            findEntry(nextCrate).ifPresent(this::applyCrateHover);
        } else {
            hoverCargoComponent = null;
        }
        crateHoverOrder = nextCrate;
    }

    private void captureRowHoverTips(int mouseX, int mouseY) {
        if (hoverTip != null || hoverTipLines != null || hoverCargoComponent != null || isOverRowIcon(mouseX, mouseY)) {
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        BoardSnapshotPayload.BoardEntry entry = snapshot.orders().get(index);
        int sx = contentLeft() + 2;
        int sw = contentW() - 4;
        int listY = listTopY();
        int rowY = listY + (index - scroll) * rowH();
        int textLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE + BoardRowMetrics.ROW_INSET_LEFT;
        BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, false);
        int textW = Math.max(8, layout.textRight() - textLeft);
        Component rare = BoardRowRenderer.hitRareCargoHover(
                entry, textLeft, BoardRowMetrics.line2Y(rowY), textW, mouseX, mouseY);
        if (rare != null) {
            hoverTip = rare;
            return;
        }
        hoverTipLines = List.of(
                Component.translatable("ui.aerocouriers.row.summary.reward", entry.reward()),
                Component.translatable("ui.aerocouriers.row.summary.collateral", entry.collateral()),
                Component.translatable("ui.aerocouriers.row.summary.rank", entry.requiredRank().displayName()),
                Component.translatable("ui.aerocouriers.row.summary.deadline", entry.deadlineMinutes()),
                Component.translatable("ui.aerocouriers.row.summary.time", entry.minutesLeft()),
                Component.translatable("ui.aerocouriers.row.summary.cargo", entry.weight(),
                        AcceptEligibility.sizeLabel(CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT)))),
                Component.translatable("ui.aerocouriers.row.summary.distance", entry.distance()));
    }

    private void applyCrateHover(BoardSnapshotPayload.BoardEntry entry) {
        List<net.minecraft.world.item.ItemStack> stacks = entry.cargoStacks();
        int shown = Math.min(BoardCargoTooltip.MAX_SHOWN, stacks.size());
        int hidden = Math.max(0, entry.manifest().size() - shown);
        hoverCargoComponent = new BoardCargoTooltip(stacks.subList(0, shown), hidden, entry.weight());
        hoverTip = Component.translatable("ui.aerocouriers.row.cargo_heading");
    }

    private Component buildMapTooltip(BoardSnapshotPayload.BoardEntry entry) {
        int distance = XaeroOrderBridge.distanceBlocks(entry.destinationPos(), entry.destinationDimension());
        if (distance < 0) {
            distance = entry.distance();
        }
        Component pickup = BoardEntryPickup.mapTooltipPickup(entry);
        Component delivery = formatPos(entry.destinationPos(), entry.destinationDimension());
        if (XaeroOrderBridge.xaeroMinimapPresent()) {
            return Component.translatable("ui.aerocouriers.row.map_tooltip.xaero",
                    pickup, delivery, distance,
                    Component.translatable("ui.aerocouriers.row.map_open_hint"));
        }
        return Component.translatable("ui.aerocouriers.row.map_tooltip", pickup, delivery, distance);
    }

    private static Component formatPos(BlockPos pos, ResourceKey<Level> dimension) {
        return Component.translatable("ui.aerocouriers.row.coords",
                pos.getX(), pos.getY(), pos.getZ(), dimension.location().toString());
    }

    private Optional<BoardSnapshotPayload.BoardEntry> findEntry(UUID id) {
        return snapshot.orders().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoverCargoComponent != null) {
            List<Component> lines = hoverTip == null ? List.of() : List.of(hoverTip);
            graphics.renderTooltip(font, lines, Optional.of(hoverCargoComponent), mouseX, mouseY);
            return;
        }
        if (hoverTipLines != null && !hoverTipLines.isEmpty()) {
            graphics.renderTooltip(font, hoverTipLines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoverTip != null) {
            graphics.renderTooltip(font, List.of(hoverTip), Optional.empty(), mouseX, mouseY);
        }
    }

    private boolean isOverRowIcon(int mouseX, int mouseY) {
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton icon && icon.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private int rowAt(double mouseX, double mouseY) {
        int sx = contentLeft() + 2;
        int sw = contentW() - 4;
        int listY = listTopY();
        int rowH = rowH();
        int frameLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE;
        BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, false);
        int frameRight = layout.crateX() + BoardRowLayout.ICON;
        if (mouseX < frameLeft || mouseX >= frameRight) {
            return -1;
        }
        int visible = visibleRows();
        for (int i = 0; i < visible && scroll + i < snapshot.orders().size(); i++) {
            int rowY = listY + i * rowH;
            if (mouseY >= rowY && mouseY < rowY + rowH) {
                return scroll + i;
            }
        }
        return -1;
    }

    private int listTopY() {
        return contentTop() + (snapshot.canRename() ? 58 : 36) + font.lineHeight + 6;
    }

    private int visibleRows() {
        int bottom = contentTop() + contentH() - 36;
        return Math.max(1, (bottom - listTopY()) / rowH());
    }

    private static int rowH() {
        return BoardRowMetrics.rowHeight();
    }

    private int cavityLeft() {
        return TabletGui.cavityLeft(panelLeft());
    }

    private int cavityTop() {
        return TabletGui.cavityTop(panelTop());
    }

    private int cavityW() {
        return TabletGui.cavityWidth(panelW());
    }

    private int cavityH() {
        return TabletGui.cavityHeight(panelH());
    }

    private int contentLeft() {
        return TabletGui.contentLeft(panelLeft());
    }

    private int contentTop() {
        return TabletGui.contentTop(panelTop());
    }

    private int contentW() {
        return TabletGui.contentWidth(panelW());
    }

    private int contentH() {
        return TabletGui.contentHeight(panelH());
    }

    private int panelLeft() {
        return (guiW() - panelW()) / 2;
    }

    private int panelTop() {
        return TabletGui.frameTop(guiH(), panelH());
    }

    private int panelW() {
        return Math.min(440, guiW() - 32);
    }

    private int panelH() {
        return Math.min(360, Math.max(220, TabletGui.maxFrameHeight(guiH())));
    }

    private int guiW() {
        return fit.shrinking() ? fit.layoutW : width;
    }

    private int guiH() {
        return fit.shrinking() ? fit.layoutH : height;
    }

    private void refreshFit() {
        fit = ScreenFit.of(width, height);
    }

    @Override
    public void removed() {
        if (mapHoverOrder != null) {
            findEntry(mapHoverOrder).ifPresent(e -> XaeroOrderBridge.onLeaveDestination(e.id()));
            mapHoverOrder = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
