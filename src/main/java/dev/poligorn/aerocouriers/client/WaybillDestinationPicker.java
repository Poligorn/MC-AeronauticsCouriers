package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.PixelGlyphs;
import dev.poligorn.aerocouriers.client.board.TabletDraw;
import dev.poligorn.aerocouriers.contract.ContractDistances;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Destination picker: stations, Xaero waypoints / map, or raw coordinates. */
final class WaybillDestinationPicker {
    enum Tab {
        STATIONS, XAERO, COORDS
    }

    private static final int WIDTH = 262;
    private static final int TAB_W = 80;
    private static final int ROW_H = 15;
    private static final int MAX_ROWS = 6;

    private final WaybillCreateScreen screen;
    private boolean open;
    private Tab tab = Tab.STATIONS;
    private int x0;
    private int y0;
    private int h;
    private int scroll;
    private int hoverRow = -1;
    private boolean hoverMap;

    WaybillDestinationPicker(WaybillCreateScreen screen) {
        this.screen = screen;
    }

    boolean isOpen() {
        return open;
    }

    Tab tab() {
        return tab;
    }

    void open(int pickX, int pickY, int pickW) {
        open = true;
        tab = Tab.STATIONS;
        scroll = 0;
        x0 = pickX + pickW - WIDTH;
        y0 = pickY + 20;
        relayout();
        if (x0 < 4) {
            x0 = 4;
        }
        if (x0 + WIDTH > screen.width - 4) {
            x0 = Math.max(4, screen.width - WIDTH - 4);
        }
        if (y0 + h > screen.height - 4) {
            y0 = Math.max(4, pickY - h - 4);
        }
    }

    void close() {
        open = false;
        hoverRow = -1;
        hoverMap = false;
    }

    void relayout() {
        int rows = visibleCapacity();
        h = 16 + rows * ROW_H + 14;
        if (tab == Tab.XAERO && XaeroMapPicker.canOpenMap()) {
            h += ROW_H;
        }
        if (tab == Tab.COORDS) {
            h = 16 + 38 + 14;
        }
    }

    int panelX() {
        return x0;
    }

    int panelY() {
        return y0;
    }

    int coordFieldY() {
        return y0 + 20;
    }

    int coordFieldX(int column) {
        int inner = WIDTH - 16;
        int gap = 4;
        int w = (inner - gap * 2) / 3;
        return x0 + 8 + column * (w + gap);
    }

    int coordFieldW() {
        int inner = WIDTH - 16;
        return (inner - 8) / 3;
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x0 && mouseX < x0 + WIDTH && mouseY >= y0 && mouseY < y0 + h;
    }

    boolean handleScroll(double scrollY) {
        if (!open || tab == Tab.COORDS) {
            return false;
        }
        int max = Math.max(0, rows().size() - visibleCapacity());
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    boolean handleClick(double mouseX, double mouseY, int button) {
        if (!open || button != 0) {
            return false;
        }
        if (!contains(mouseX, mouseY)) {
            close();
            return true;
        }
        int tabY = y0 + 3;
        for (int i = 0; i < tabs().size(); i++) {
            int tx = x0 + 4 + i * (TAB_W + 4);
            if (TabletDraw.inside(mouseX, mouseY, tx, tabY, TAB_W, WaybillCreateLayout.CHIP_H)) {
                tab = tabs().get(i);
                scroll = 0;
                relayout();
                screen.refreshForm();
                return true;
            }
        }
        if (tab == Tab.XAERO && XaeroMapPicker.canOpenMap()) {
            int mapY = listTop();
            if (TabletDraw.inside(mouseX, mouseY, x0 + 3, mapY, WIDTH - 6, ROW_H - 2)) {
                close();
                XaeroMapPicker.openMapForPick(screen);
                return true;
            }
        }
        if (tab == Tab.COORDS) {
            return true;
        }
        int index = rowAt(mouseY);
        if (index >= 0 && index < rows().size()) {
            rows().get(index).pick().run();
            close();
            screen.refreshForm();
            return true;
        }
        return true;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        hoverRow = -1;
        hoverMap = false;
        WaybillCreateGui.fill(g, x0 + 3, y0 + 3, x0 + WIDTH + 1, y0 + h + 1, 0xFF1E160C);
        WaybillCreateGui.box(g, x0, y0, x0 + WIDTH - 1, y0 + h - 1, WaybillPalette.PAPER, WaybillPalette.BLUE);
        WaybillCreateGui.outline(g, x0 + 1, y0 + 1, x0 + WIDTH - 2, y0 + h - 2, WaybillPalette.PAPER3);
        List<Tab> shown = tabs();
        int tabY = y0 + 3;
        for (int i = 0; i < shown.size(); i++) {
            Tab t = shown.get(i);
            int tx = x0 + 4 + i * (TAB_W + 4);
            WaybillCreateGui.chip(g, font, tx, tabY, TAB_W, tabLabel(t), t == tab,
                    TabletDraw.inside(mouseX, mouseY, tx, tabY, TAB_W, WaybillCreateLayout.CHIP_H));
        }
        if (tab == Tab.COORDS) {
            WaybillCreateGui.text(g, font, Component.translatable("ui.aerocouriers.create.picker.coords_hint").getString(),
                    x0 + 8, y0 + h - 12, WaybillPalette.INK2);
            return;
        }
        int listY = listTop();
        if (tab == Tab.XAERO && XaeroMapPicker.canOpenMap()) {
            hoverMap = TabletDraw.inside(mouseX, mouseY, x0 + 3, listY, WIDTH - 6, ROW_H - 2);
            if (hoverMap) {
                WaybillCreateGui.fill(g, x0 + 3, listY, x0 + WIDTH - 4, listY + ROW_H - 2, WaybillPalette.PICK_ROW);
            }
            PixelGlyphs.draw(g, x0 + 7, listY + 3, PixelGlyphs.PIN, WaybillPalette.BLUE);
            WaybillCreateGui.text(g, font, Component.translatable("ui.aerocouriers.create.picker.open_map").getString(),
                    x0 + 18, listY + 3, WaybillPalette.BLUE);
            listY += ROW_H;
        }
        List<Row> all = rows();
        if (all.isEmpty()) {
            String empty = Component.translatable(tab == Tab.XAERO
                    ? "ui.aerocouriers.create.xaero_picker.empty"
                    : "ui.aerocouriers.create.picker.empty_stations").getString();
            WaybillCreateGui.text(g, font, empty, x0 + 8, listY + 3, WaybillPalette.INK2);
        } else {
            int shownRows = visibleCapacity();
            for (int i = 0; i < shownRows; i++) {
                int index = scroll + i;
                if (index >= all.size()) {
                    break;
                }
                int ry = listY + i * ROW_H;
                boolean hover = TabletDraw.inside(mouseX, mouseY, x0 + 3, ry, WIDTH - 6, ROW_H - 2);
                if (hover) {
                    hoverRow = index;
                    WaybillCreateGui.fill(g, x0 + 3, ry, x0 + WIDTH - 4, ry + ROW_H - 2, WaybillPalette.PICK_ROW);
                    WaybillCreateGui.outline(g, x0 + 3, ry, x0 + WIDTH - 4, ry + ROW_H - 2, WaybillPalette.BLUE);
                }
                Row row = all.get(index);
                PixelGlyphs.draw(g, x0 + 7, ry + 3, row.station() ? PixelGlyphs.HOUSE : PixelGlyphs.PIN,
                        WaybillPalette.BROWN);
                String name = TabletDraw.trunc(font, row.name(), 120);
                if (hover) {
                    WaybillCreateGui.bold(g, font, name, x0 + 18, ry + 3, WaybillPalette.INK);
                } else {
                    WaybillCreateGui.text(g, font, name, x0 + 18, ry + 3, WaybillPalette.INK);
                }
                WaybillCreateGui.text(g, font, row.kind(), x0 + 18 + font.width(name) + 8, ry + 3, WaybillPalette.INK2);
                WaybillCreateGui.textRight(g, font, row.distance(), x0 + WIDTH - 7, ry + 3, WaybillPalette.INK, false);
            }
        }
        WaybillCreateGui.text(g, font, Component.translatable("ui.aerocouriers.create.picker.hint").getString(),
                x0 + 6, y0 + h - 12, WaybillPalette.INK2);
    }

    private int listTop() {
        return y0 + 18;
    }

    private int visibleCapacity() {
        return MAX_ROWS;
    }

    private int rowAt(double mouseY) {
        int listY = listTop();
        if (tab == Tab.XAERO && XaeroMapPicker.canOpenMap()) {
            listY += ROW_H;
        }
        int local = (int) mouseY - listY;
        if (local < 0) {
            return -1;
        }
        int i = local / ROW_H;
        if (i < 0 || i >= visibleCapacity()) {
            return -1;
        }
        return scroll + i;
    }

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.STATIONS);
        if (XaeroMapPicker.available() || XaeroMapPicker.canOpenMap()) {
            tabs.add(Tab.XAERO);
        }
        tabs.add(Tab.COORDS);
        return tabs;
    }

    private static String tabLabel(Tab tab) {
        return Component.translatable(switch (tab) {
            case STATIONS -> "ui.aerocouriers.create.picker.stations";
            case XAERO -> "ui.aerocouriers.create.picker.xaero";
            case COORDS -> "ui.aerocouriers.create.picker.coords";
        }).getString();
    }

    private List<Row> rows() {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos origin = screen.previewOrigin();
        ResourceKey<Level> originDim = minecraft.level != null ? minecraft.level.dimension() : Level.OVERWORLD;
        List<Row> rows = new ArrayList<>();
        if (tab == Tab.STATIONS) {
            String kind = Component.translatable("ui.aerocouriers.create.picker.station_kind").getString();
            for (BoardSnapshotPayload.StationHint station : screen.stations()) {
                rows.add(new Row(station.name(), kind, formatDistance(origin, originDim, station.pos(),
                        station.dimension()), true, () -> screen.applyStation(station)));
            }
        } else if (tab == Tab.XAERO) {
            String kind = Component.translatable("ui.aerocouriers.create.picker.xaero_kind").getString();
            for (XaeroPickedWaypoint waypoint : XaeroMapPicker.listWaypoints()) {
                rows.add(new Row(waypoint.name(), kind, formatDistance(origin, originDim,
                        new BlockPos(waypoint.x(), waypoint.y(), waypoint.z()), originDim),
                        false, () -> screen.applyPicked(waypoint)));
            }
        }
        return rows;
    }

    private static String formatDistance(BlockPos origin, ResourceKey<Level> originDim, BlockPos to,
                                         ResourceKey<Level> toDim) {
        Destination a = new Destination(originDim, origin, 8);
        Destination b = new Destination(toDim, to, 8);
        long blocks = Math.round(ContractDistances.pricingBlocks(a, b));
        return Component.translatable("ui.aerocouriers.create.picker.blocks", TabletDraw.num(blocks)).getString();
    }

    private record Row(String name, String kind, String distance, boolean station, Runnable pick) {
    }
}
