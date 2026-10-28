package dev.poligorn.aerocouriers.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
/**
 * Scrollable Xaero waypoint list for {@link WaybillCreateScreen}. Tablet-styled panel (dark fill, brass outline).
 */
final class XaeroWaypointPickerOverlay {
    static final int ROW_H = 18;
    private static final int MAX_ROWS = 7;
    private static final int PAD = 4;
    private static final int SCROLL_W = 6;

    private final WaybillCreateScreen screen;
    private boolean open;
    private int anchorX;
    private int anchorY;
    private int panelW;
    private int panelH;
    private int scrollOffset;
    private List<Row> rows = List.of();

    XaeroWaypointPickerOverlay(WaybillCreateScreen screen) {
        this.screen = screen;
    }

    boolean isOpen() {
        return open;
    }

    void toggle(int anchorX, int anchorY, int anchorW, List<XaeroPickedWaypoint> waypoints) {
        if (open) {
            close();
            return;
        }
        open = true;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        scrollOffset = 0;
        rows = buildRows(waypoints);
        Font font = screen.waybillFont();
        int maxText = font.width(Component.translatable("ui.aerocouriers.create.xaero_picker.empty"));
        for (Row row : rows) {
            maxText = Math.max(maxText, font.width(row.label));
        }
        panelW = Math.min(Math.max(anchorW, maxText + PAD * 2 + SCROLL_W + 4), screen.width - 16);
        int visibleRows = Math.max(1, Math.min(MAX_ROWS, rows.isEmpty() ? 1 : rows.size()));
        panelH = PAD * 2 + visibleRows * ROW_H;
        int belowY = anchorY + WaybillCreateLayout.FIELD_H + 2;
        int aboveY = anchorY - panelH - 2;
        this.anchorY = belowY + panelH <= screen.height - 4 ? belowY : Math.max(4, aboveY);
        this.anchorX = Math.max(4, Math.min(anchorX, screen.width - panelW - 4));
        clampScroll();
    }

    void close() {
        open = false;
        scrollOffset = 0;
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= anchorX && mouseX < anchorX + panelW && mouseY >= anchorY && mouseY < anchorY + panelH;
    }

    boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) {
            return false;
        }
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int listTop = anchorY + PAD;
        int listH = panelH - PAD * 2;
        if (rows.isEmpty()) {
            close();
            return true;
        }
        int relY = (int) mouseY - listTop;
        int index = scrollOffset + relY / ROW_H;
        if (relY >= 0 && relY < listH && index >= 0 && index < rows.size()) {
            screen.applyPicked(rows.get(index).waypoint);
            screen.refreshForm();
            close();
        }
        return true;
    }

    boolean handleScroll(double scrollY) {
        if (!open || rows.size() <= visibleRowCount()) {
            return false;
        }
        scrollOffset = clamp(scrollOffset - (int) scrollY, 0, rows.size() - visibleRowCount());
        return true;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        TabletGui.drawListPanel(graphics, anchorX, anchorY, panelW, panelH);
        graphics.renderOutline(anchorX, anchorY, panelW, panelH, 0xFFFFAF55);
        int listTop = anchorY + PAD;
        int listH = panelH - PAD * 2;
        if (rows.isEmpty()) {
            Font font = screen.waybillFont();
            Component empty = Component.translatable("ui.aerocouriers.create.xaero_picker.empty");
            graphics.drawString(font, empty, anchorX + PAD, listTop + 4, TabletGui.TEXT_MUTED, false);
            return;
        }
        int visible = visibleRowCount();
        graphics.enableScissor(anchorX + PAD, listTop, anchorX + panelW - PAD - SCROLL_W, listTop + listH);
        for (int i = 0; i < visible; i++) {
            int rowIndex = scrollOffset + i;
            if (rowIndex >= rows.size()) {
                break;
            }
            Row row = rows.get(rowIndex);
            int rowY = listTop + i * ROW_H;
            boolean hover = mouseX >= anchorX + PAD && mouseX < anchorX + panelW - PAD - SCROLL_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            float slideX = 0.0F;
            TabletGui.drawRow(graphics, anchorX + PAD, rowY, panelW - PAD * 2 - SCROLL_W, ROW_H - 1, false,
                    hover ? 1.0F : 0.0F, 1.0F, slideX);
            graphics.pose().pushPose();
            graphics.pose().translate(anchorX + PAD + 3.0F, rowY + 5.0F, 0.0F);
            graphics.drawString(screen.waybillFont(), row.label, 0, 0, TabletGui.TEXT, false);
            graphics.pose().popPose();
        }
        graphics.disableScissor();
        if (rows.size() > visible) {
            float progress = scrollOffset / (float) (rows.size() - visible);
            float thumb = visible / (float) rows.size();
            TabletGui.drawScrollbar(graphics, anchorX + panelW - PAD - SCROLL_W, listTop, listH, progress, thumb);
        }
    }

    private int visibleRowCount() {
        return Math.max(1, (panelH - PAD * 2) / ROW_H);
    }

    private void clampScroll() {
        scrollOffset = clamp(scrollOffset, 0, Math.max(0, rows.size() - visibleRowCount()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Row> buildRows(List<XaeroPickedWaypoint> waypoints) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Row> built = new ArrayList<>(waypoints.size());
        for (XaeroPickedWaypoint waypoint : waypoints) {
            double dist = distanceBlocks(waypoint);
            built.add(new Row(waypoint, rowLabel(waypoint, dist)));
        }
        built.sort(Comparator.comparingDouble(Row::distance));
        return built;
    }

    private static double distanceBlocks(XaeroPickedWaypoint waypoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(minecraft.player.distanceToSqr(waypoint.x() + 0.5, waypoint.y() + 0.5, waypoint.z() + 0.5));
    }

    private static Component rowLabel(XaeroPickedWaypoint waypoint, double blocks) {
        String symbol = waypoint.symbol();
        if (symbol == null || symbol.isBlank()) {
            symbol = "★";
        }
        Component distance = formatDistance(blocks);
        return Component.translatable(
                "ui.aerocouriers.create.xaero_row",
                symbol,
                waypoint.name(),
                waypoint.x(),
                waypoint.y(),
                waypoint.z(),
                distance);
    }

    static Component formatDistance(double blocks) {
        if (blocks == Double.POSITIVE_INFINITY || blocks < 0) {
            return Component.literal("—");
        }
        if (blocks >= 1000.0) {
            return Component.translatable(
                    "ui.aerocouriers.create.xaero_distance_km",
                    String.format(Locale.ROOT, "%.1f", blocks / 1000.0));
        }
        return Component.translatable("ui.aerocouriers.create.xaero_distance_m", (int) blocks);
    }

    private record Row(XaeroPickedWaypoint waypoint, Component label) {
        double distance() {
            return distanceBlocks(waypoint);
        }
    }
}
