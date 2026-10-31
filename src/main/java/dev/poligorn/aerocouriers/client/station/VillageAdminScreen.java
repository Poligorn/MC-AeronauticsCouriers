package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.InGamePauseBackground;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.net.OpenStationPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import dev.poligorn.aerocouriers.village.StationReputation;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Operator-only village admin panel. Permission is re-checked on the server. */
public final class VillageAdminScreen extends Screen {
    private StationSnapshotPayload snapshot;
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private @Nullable VillageAdminDialogs dialog;
    private @Nullable EditBox intervalField;
    private @Nullable EditBox exactRepField;

    public VillageAdminScreen(StationSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.village_admin.title", snapshot.name()));
        this.snapshot = snapshot;
    }

    public void apply(StationSnapshotPayload payload) {
        this.snapshot = payload;
        if (!payload.view().openAdmin() || !payload.view().canAdmin()) {
            if (minecraft != null) {
                minecraft.setScreen(new CorkBoardScreen(payload));
            }
            return;
        }
        dialog = null;
        rebuild();
    }

    Font getFont() {
        return font;
    }

    void addDialogWidget(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        winW = Math.min(width - 8, width < 640 ? CorkBoardLayout.SMALL_W : CorkBoardLayout.LARGE_W);
        winH = Math.min(height - 8, height < 360 ? CorkBoardLayout.SMALL_H : CorkBoardLayout.LARGE_H);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        int left = winX + 16;
        int colW = (winW - 40) / 2;
        intervalField = new EditBox(font, left + 110, winY + 196, 28, 14,
                Component.translatable("ui.aerocouriers.village_admin.interval"));
        intervalField.setMaxLength(4);
        intervalField.setValue(Integer.toString(snapshot.view().refreshIntervalMin()));
        addRenderableWidget(intervalField);
        exactRepField = new EditBox(font, left + 86, winY + 118, 28, 14,
                Component.translatable("ui.aerocouriers.village_admin.rep.exact"));
        exactRepField.setMaxLength(3);
        exactRepField.setValue(Integer.toString(snapshot.reputation()));
        addRenderableWidget(exactRepField);
        if (dialog != null) {
            dialog.layout(width, height);
            dialog.init(this);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        CorkBoardGui.dimWorld(graphics, width, height);
        CorkBoardGui.frame(graphics, winX, winY, winW, winH);
        CorkBoardGui.headerBand(graphics, winX + CorkBoardLayout.FRAME, winY + CorkBoardLayout.FRAME,
                winW - CorkBoardLayout.FRAME * 2, 28);
        graphics.fill(winX + CorkBoardLayout.FRAME, winY + CorkBoardLayout.FRAME + 28,
                winX + winW - CorkBoardLayout.FRAME, winY + winH - CorkBoardLayout.FRAME, 0xFF2A1A0C);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.title", snapshot.name()),
                winX + 16, winY + 12, CorkBoardGui.HEADER, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.op"),
                winX + winW - 16 - font.width(Component.translatable("ui.aerocouriers.village_admin.op")),
                winY + 12, CorkBoardGui.GOLD, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.subtitle"),
                winX + 16, winY + 38, CorkBoardGui.INK_MUTED, false);

        int left = winX + 16;
        int right = winX + winW / 2 + 4;
        int colW = (winW - 40) / 2;
        drawName(graphics, left, 54, colW, mouseX, mouseY);
        drawReputation(graphics, left, 92, colW, mouseX, mouseY);
        drawOrders(graphics, left, 168, colW, mouseX, mouseY);
        drawStats(graphics, right, 54, colW);
        drawPolicy(graphics, right, 140, colW, mouseX, mouseY);
        drawCommands(graphics, right, 214, colW);
        drawFooter(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (dialog != null) {
            graphics.fill(winX, winY, winX + winW, winY + winH, 0x88000000);
            dialog.layout(width, height);
            dialog.render(graphics, font, mouseX, mouseY);
        }
    }

    private void drawName(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, 34, "ui.aerocouriers.village_admin.name");
        graphics.drawString(font, snapshot.name() + "  " + snapshot.name().length() + "/" + VillageBoardPolicy.NAME_MAX,
                x + 6, y + 14, CorkBoardGui.HEADER, false);
        Component rename = Component.translatable("ui.aerocouriers.village_admin.rename");
        int bw = font.width(rename) + 12;
        CorkBoardGui.button(graphics, font, x + w - bw - 6, y + 12, bw, 14, rename,
                hover(mouseX, mouseY, x + w - bw - 6, y + 12, bw, 14), true, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.name.hint"),
                x + 6, y + 26, CorkBoardGui.INK_MUTED, false);
    }

    private void drawReputation(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, 70, "ui.aerocouriers.village_admin.rep");
        graphics.drawString(font, snapshot.reputation() + " / " + VillageBoardPolicy.REPUTATION_MAX,
                x + w - 50, y + 4, CorkBoardGui.HEADER, false);
        CorkBoardGui.reputationBar(graphics, x + 6, y + 16, w - 12, 7, snapshot.reputation(),
                VillageBoardPolicy.REPUTATION_MAX);
        int bx = x + 6;
        int by = y + 28;
        bx = quickRep(graphics, bx, by, mouseX, mouseY, -10);
        bx = quickRep(graphics, bx, by, mouseX, mouseY, -1);
        bx = quickRep(graphics, bx, by, mouseX, mouseY, 1);
        quickRep(graphics, bx, by, mouseX, mouseY, 10);
        Component set = Component.translatable("ui.aerocouriers.village_admin.rep.set");
        Component adjust = Component.translatable("ui.aerocouriers.village_admin.rep.adjust");
        CorkBoardGui.button(graphics, font, x + 118, y + 46, 52, 14, set,
                hover(mouseX, mouseY, x + 118, y + 46, 52, 14), true, false);
        CorkBoardGui.button(graphics, font, x + 174, y + 46, font.width(adjust) + 12, 14, adjust,
                hover(mouseX, mouseY, x + 174, y + 46, font.width(adjust) + 12, 14), true, false);
    }

    private int quickRep(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, int delta) {
        Component label = Component.literal((delta > 0 ? "+" : "") + delta);
        int w = 28;
        CorkBoardGui.button(graphics, font, x, y, w, 14, label, hover(mouseX, mouseY, x, y, w, 14), true, false);
        return x + w + 3;
    }

    private void drawOrders(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, 78, "ui.aerocouriers.village_admin.orders");
        StationSnapshotPayload.View v = snapshot.view();
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.orders.summary",
                v.onBoard(), v.boardMax(), v.expensiveCount(), v.rareCount()),
                x + 6, y + 14, CorkBoardGui.HEADER, false);
        int ticks = VillageRefreshUi.remainingTicks(minecraft);
        Component timer = ticks < 0 ? Component.empty() : VillageRefreshUi.countdownLabel(ticks);
        graphics.drawString(font, timer, x + 6, y + 24, CorkBoardGui.INK_MUTED, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.interval"),
                x + 6, y + 38, CorkBoardGui.HEADER, false);
        Component apply = Component.translatable("ui.aerocouriers.village_admin.apply");
        CorkBoardGui.button(graphics, font, x + 144, y + 36, 48, 14, apply,
                hover(mouseX, mouseY, x + 144, y + 36, 48, 14), true, false);
        Component refresh = Component.translatable("ui.aerocouriers.village_admin.orders.refresh");
        Component regen = Component.translatable("ui.aerocouriers.village_admin.orders.regen");
        Component clear = Component.translatable("ui.aerocouriers.village_admin.orders.clear");
        CorkBoardGui.button(graphics, font, x + 6, y + 54, (w - 18) / 2, 14, refresh,
                hover(mouseX, mouseY, x + 6, y + 54, (w - 18) / 2, 14), true, false);
        CorkBoardGui.button(graphics, font, x + 10 + (w - 18) / 2, y + 54, (w - 18) / 2, 14, regen,
                hover(mouseX, mouseY, x + 10 + (w - 18) / 2, y + 54, (w - 18) / 2, 14), true, false);
        CorkBoardGui.button(graphics, font, x + 6, y + 70, w - 12, 14, clear,
                hover(mouseX, mouseY, x + 6, y + 70, w - 12, 14), true, true);
    }

    private void drawStats(GuiGraphics graphics, int x, int y, int w) {
        section(graphics, x, y, w, 80, "ui.aerocouriers.village_admin.stats");
        StationSnapshotPayload.View v = snapshot.view();
        int ty = y + 14;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.rank",
                snapshot.reputationTier(), StationReputation.tier(VillageBoardPolicy.REPUTATION_MAX)));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.rep",
                snapshot.reputation(), VillageBoardPolicy.REPUTATION_MAX));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.done", v.completed()));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.active", v.active()));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.board",
                v.onBoard(), v.boardMax()));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.special",
                v.expensiveCount(), v.rareCount()));
    }

    private void drawPolicy(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, 68, "ui.aerocouriers.village_admin.policy");
        StationSnapshotPayload.View v = snapshot.view();
        CorkBoardGui.checkbox(graphics, font, x + 6, y + 16,
                Component.translatable("ui.aerocouriers.village_admin.expensive"), v.allowExpensive());
        CorkBoardGui.checkbox(graphics, font, x + 6, y + 30,
                Component.translatable("ui.aerocouriers.village_admin.rare"), v.allowRare());
        int sx = x + 6;
        int sy = y + 46;
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.sizes"),
                sx, sy, CorkBoardGui.HEADER, false);
        sx += 70;
        for (CargoSize size : CargoSize.values()) {
            boolean on = (v.allowedSizesMask() & VillageBoardPolicy.maskOf(size)) != 0;
            Component label = Component.literal(size.name());
            int cw = CorkBoardGui.chipW(font, label);
            CorkBoardGui.chip(graphics, font, sx, sy - 2, cw, 14, label, on,
                    hover(mouseX, mouseY, sx, sy - 2, cw, 14));
            sx += cw + 3;
        }
    }

    private void drawCommands(GuiGraphics graphics, int x, int y, int w) {
        section(graphics, x, y, w, 48, "ui.aerocouriers.village_admin.commands");
        graphics.drawString(font, "/couriers admin village rename <name>", x + 6, y + 14,
                CorkBoardGui.INK_MUTED, false);
        graphics.drawString(font, "/couriers admin village here rep add 10", x + 6, y + 24,
                CorkBoardGui.INK_MUTED, false);
        graphics.drawString(font, "/couriers admin village here refresh", x + 6, y + 34,
                CorkBoardGui.INK_MUTED, false);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.persist"),
                winX + 16, winY + winH - 22, CorkBoardGui.INK_MUTED, false);
        Component close = Component.translatable("ui.aerocouriers.village_admin.close");
        int bw = font.width(close) + 16;
        int bx = winX + winW - 16 - bw;
        int by = winY + winH - 26;
        CorkBoardGui.button(graphics, font, bx, by, bw, 16, close, hover(mouseX, mouseY, bx, by, bw, 16), true, false);
    }

    private void section(GuiGraphics graphics, int x, int y, int w, int h, String key) {
        CorkBoardGui.panel(graphics, x, y, w, h);
        graphics.drawString(font, Component.translatable(key), x + 6, y + 3, CorkBoardGui.GOLD, false);
    }

    private void line(GuiGraphics graphics, int x, int y, Component text) {
        graphics.drawString(font, text, x, y, CorkBoardGui.HEADER, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dialog != null) {
            if (dialog.mouseClicked(mouseX, mouseY)) {
                dialog = null;
                rebuild();
            }
            return true;
        }
        int left = winX + 16;
        int right = winX + winW / 2 + 4;
        int colW = (winW - 40) / 2;
        Component rename = Component.translatable("ui.aerocouriers.village_admin.rename");
        int bw = font.width(rename) + 12;
        if (hover(mouseX, mouseY, left + colW - bw - 6, winY + 66, bw, 14)) {
            openDialog(new VillageAdminDialogs.Rename(snapshot));
            return true;
        }
        int qx = left + 6;
        int qy = winY + 120;
        int[] deltas = {-10, -1, 1, 10};
        for (int delta : deltas) {
            if (hover(mouseX, mouseY, qx, qy, 28, 14)) {
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.REP_ADD, delta));
                return true;
            }
            qx += 31;
        }
        if (hover(mouseX, mouseY, left + 118, winY + 138, 52, 14) && exactRepField != null) {
            try {
                int value = Integer.parseInt(exactRepField.getValue().strip());
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.REP_SET, value));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        Component adjust = Component.translatable("ui.aerocouriers.village_admin.rep.adjust");
        if (hover(mouseX, mouseY, left + 174, winY + 138, font.width(adjust) + 12, 14)) {
            openDialog(new VillageAdminDialogs.Reputation(snapshot));
            return true;
        }
        if (hover(mouseX, mouseY, left + 144, winY + 204, 48, 14) && intervalField != null) {
            try {
                int minutes = Integer.parseInt(intervalField.getValue().strip());
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.INTERVAL, minutes));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        int half = (colW - 18) / 2;
        if (hover(mouseX, mouseY, left + 6, winY + 222, half, 14)) {
            PacketDistributor.sendToServer(VillageAdminPayload.of(snapshot.stationPos(),
                    VillageAdminPayload.Action.REFRESH));
            return true;
        }
        if (hover(mouseX, mouseY, left + 10 + half, winY + 222, half, 14)) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.regen"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.regen"),
                    VillageAdminPayload.Action.REGENERATE));
            return true;
        }
        if (hover(mouseX, mouseY, left + 6, winY + 238, colW - 12, 14)) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.clear"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.clear"),
                    VillageAdminPayload.Action.CLEAR));
            return true;
        }
        if (hover(mouseX, mouseY, right + 6, winY + 156, 140, 12)) {
            PacketDistributor.sendToServer(VillageAdminPayload.flag(snapshot.stationPos(),
                    VillageAdminPayload.Action.TOGGLE_EXPENSIVE, !snapshot.view().allowExpensive()));
            return true;
        }
        if (hover(mouseX, mouseY, right + 6, winY + 170, 140, 12)) {
            PacketDistributor.sendToServer(VillageAdminPayload.flag(snapshot.stationPos(),
                    VillageAdminPayload.Action.TOGGLE_RARE, !snapshot.view().allowRare()));
            return true;
        }
        int sx = right + 76;
        int sy = winY + 184;
        for (CargoSize size : CargoSize.values()) {
            Component label = Component.literal(size.name());
            int cw = CorkBoardGui.chipW(font, label);
            if (hover(mouseX, mouseY, sx, sy, cw, 14)) {
                boolean on = (snapshot.view().allowedSizesMask() & VillageBoardPolicy.maskOf(size)) != 0;
                PacketDistributor.sendToServer(VillageAdminPayload.size(snapshot.stationPos(), size.ordinal(), !on));
                return true;
            }
            sx += cw + 3;
        }
        Component close = Component.translatable("ui.aerocouriers.village_admin.close");
        int cbw = font.width(close) + 16;
        if (hover(mouseX, mouseY, winX + winW - 16 - cbw, winY + winH - 26, cbw, 16)) {
            PacketDistributor.sendToServer(new OpenStationPayload(snapshot.stationPos()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openDialog(VillageAdminDialogs next) {
        dialog = next;
        rebuild();
    }

    private static boolean hover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && dialog != null) {
            dialog = null;
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
