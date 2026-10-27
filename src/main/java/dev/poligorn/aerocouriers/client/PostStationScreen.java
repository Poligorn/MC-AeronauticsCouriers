package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.board.BoardCargoTooltip;
import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.client.board.BoardRowLayout;
import dev.poligorn.aerocouriers.client.board.BoardRowMetrics;
import dev.poligorn.aerocouriers.client.board.BoardRowRenderer;
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
    }

    @Override
    protected void init() {
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
        TabletGui.drawScreenBackground(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        hoverTipLines = null;
        hoverCargoComponent = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = contentLeft();
        int cy = contentTop();
        graphics.drawString(font, title, cx, cy, TabletGui.TEXT, false);
        Component next = snapshot.reputationNextThreshold() < 0
                ? Component.translatable("ui.aerocouriers.reputation.max_tier")
                : Component.translatable("ui.aerocouriers.station.reputation_next", snapshot.reputationNextThreshold());
        Component rep = Component.translatable("ui.aerocouriers.station.reputation",
                snapshot.reputation(), snapshot.reputationTier(), snapshot.bonusPercent(), next);
        graphics.drawString(font, rep, cx, cy + (snapshot.canRename() ? 42 : 20), TabletGui.TEXT_MUTED, false);

        renderOrderList(graphics, mouseX, mouseY);
        renderRowIconsOnTop(graphics, mouseX, mouseY, partialTick);
        updateRowIconHover(mouseX, mouseY);
        captureRowHoverTips(mouseX, mouseY);
        renderTooltips(graphics, mouseX, mouseY);
        TabletGui.drawFrameChrome(graphics, panelLeft(), panelTop(), panelW(), panelH());
    }

    private void renderOrderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = listTopY();
        List<BoardSnapshotPayload.BoardEntry> orders = snapshot.orders();
        if (orders.isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.station.orders.empty"),
                    contentLeft(), listTop, TabletGui.TEXT_MUTED, false);
            return;
        }
        graphics.drawString(font, Component.translatable("ui.aerocouriers.station.orders.header"),
                contentLeft(), listTop - font.lineHeight - 2, TabletGui.TEXT, false);
        int sx = contentLeft() + 2;
        int sw = contentW() - 4;
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, orders.size() - visible));
        int rowH = rowH();
        int listY = listTop;
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
            TabletGui.drawRow(graphics, frameLeft, rowY, frameW, rowH, hover);
            BoardRowRenderer.renderLine1(graphics, font, entry, textLeft, BoardRowMetrics.line1Y(rowY), textW,
                    mouseX, mouseY, true, (tip, ignored) -> hoverTip = tip);
            BoardRowRenderer.renderLine2(graphics, font, entry, textLeft, BoardRowMetrics.line2Y(rowY), textW);
        }
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
        Component pickup = formatPos(entry.originPos(), entry.originDimension());
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
        return (width - panelW()) / 2;
    }

    private int panelTop() {
        return TabletGui.frameTop(height, panelH());
    }

    private int panelW() {
        return Math.min(440, width - 32);
    }

    private int panelH() {
        return Math.min(360, Math.max(220, TabletGui.maxFrameHeight(height)));
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
