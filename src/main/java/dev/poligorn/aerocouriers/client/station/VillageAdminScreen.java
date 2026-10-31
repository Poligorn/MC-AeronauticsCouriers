package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.InGamePauseBackground;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Operator-only village admin panel. Permission is re-checked on the server. */
public final class VillageAdminScreen extends Screen {
    private StationSnapshotPayload snapshot;
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int left;
    private int right;
    private int colW;
    private int nameY;
    private int nameH;
    private int repY;
    private int repH;
    private int ordY;
    private int ordH;
    private int statY;
    private int statH;
    private int polY;
    private int polH;
    private int cmdY;
    private int cmdH;
    private int intervalMinusX;
    private int intervalPlusX;
    private int intervalApplyX;
    private int intervalRowY;
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

    private void layoutChrome() {
        winW = Math.min(width - 8, width < 640 ? CorkBoardLayout.SMALL_W : CorkBoardLayout.LARGE_W);
        winH = Math.min(height - 8, height < 360 ? CorkBoardLayout.SMALL_H : CorkBoardLayout.LARGE_H);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        left = winX + 16;
        right = winX + winW / 2 + 4;
        colW = (winW - 40) / 2;
        boolean compact = winH < 340;
        int gap = compact ? 4 : 6;
        nameH = compact ? 30 : 36;
        repH = compact ? 62 : 72;
        ordH = compact ? 78 : 88;
        statH = compact ? 70 : 82;
        polH = compact ? 56 : 68;
        cmdH = compact ? 36 : 46;
        nameY = winY + (compact ? 42 : 50);
        repY = nameY + nameH + gap;
        ordY = repY + repH + gap;
        statY = nameY;
        polY = statY + statH + gap;
        cmdY = polY + polH + gap;
    }

    private void rebuild() {
        clearWidgets();
        layoutChrome();
        intervalRowY = ordY + 36;
        Component intervalLabel = Component.translatable("ui.aerocouriers.village_admin.interval");
        int fieldX = left + 8 + font.width(intervalLabel) + 4;
        intervalField = new EditBox(font, fieldX, intervalRowY, 28, 14, intervalLabel);
        intervalField.setMaxLength(4);
        intervalField.setValue(Integer.toString(snapshot.view().refreshIntervalMin()));
        intervalMinusX = fieldX + 28 + 4 + font.width(Component.translatable(
                "ui.aerocouriers.village_admin.interval.unit")) + 4;
        intervalPlusX = intervalMinusX + 16;
        intervalApplyX = intervalPlusX + 18;
        exactRepField = new EditBox(font, left + 6, repY + 50, 28, 14,
                Component.translatable("ui.aerocouriers.village_admin.rep.exact"));
        exactRepField.setMaxLength(3);
        exactRepField.setValue(Integer.toString(snapshot.reputation()));
        if (dialog == null) {
            addRenderableWidget(intervalField);
            addRenderableWidget(exactRepField);
        } else {
            dialog.layout(width, height);
            dialog.init(this);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layoutChrome();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        CorkBoardGui.fullBright();
        CorkBoardGui.frame(graphics, winX, winY, winW, winH);
        CorkBoardGui.headerBand(graphics, winX + CorkBoardLayout.FRAME, winY + CorkBoardLayout.FRAME,
                winW - CorkBoardLayout.FRAME * 2, 26);
        CorkBoardGui.interior(graphics, winX + CorkBoardLayout.FRAME, winY + CorkBoardLayout.FRAME + 26,
                winW - CorkBoardLayout.FRAME * 2, winH - CorkBoardLayout.FRAME * 2 - 26);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.title", snapshot.name()),
                winX + 16, winY + 12, CorkBoardGui.HEADER, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.op"),
                winX + winW - 16 - font.width(Component.translatable("ui.aerocouriers.village_admin.op")),
                winY + 12, CorkBoardGui.GOLD, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.subtitle"),
                winX + 16, winY + 36, CorkBoardGui.INK_MUTED, false);

        drawName(graphics, left, nameY, colW, mouseX, mouseY);
        drawReputation(graphics, left, repY, colW, mouseX, mouseY);
        drawOrders(graphics, left, ordY, colW, mouseX, mouseY);
        drawStats(graphics, right, statY, colW);
        drawPolicy(graphics, right, polY, colW, mouseX, mouseY);
        drawCommands(graphics, right, cmdY, colW);
        drawFooter(graphics, mouseX, mouseY);

        if (dialog == null) {
            for (var widget : renderables) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        } else {
            graphics.fill(winX, winY, winX + winW, winY + winH, 0x88000000);
            dialog.layout(width, height);
            dialog.render(graphics, font, mouseX, mouseY);
            for (var widget : renderables) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private int pinned() {
        return snapshot.orders().size();
    }

    private int expensivePinned() {
        int n = 0;
        for (BoardSnapshotPayload.BoardEntry entry : snapshot.orders()) {
            if (entry.reward() >= VillageBoardPolicy.EXPENSIVE_REWARD) {
                n++;
            }
        }
        return n;
    }

    private int rarePinned() {
        int n = 0;
        for (BoardSnapshotPayload.BoardEntry entry : snapshot.orders()) {
            if (entry.villageRare()) {
                n++;
            }
        }
        return n;
    }

    private void drawName(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, nameH, "ui.aerocouriers.village_admin.name");
        graphics.drawString(font, snapshot.name() + "  " + snapshot.name().length() + "/" + VillageBoardPolicy.NAME_MAX,
                x + 6, y + 16, CorkBoardGui.HEADER, false);
        Component rename = Component.translatable("ui.aerocouriers.village_admin.rename");
        int bw = font.width(rename) + 12;
        CorkBoardGui.button(graphics, font, x + w - bw - 6, y + 14, bw, 14, rename,
                hover(mouseX, mouseY, x + w - bw - 6, y + 14, bw, 14), true, false);
    }

    private void drawReputation(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, repH, "ui.aerocouriers.village_admin.rep");
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
        CorkBoardGui.button(graphics, font, x + 38, y + 50, 52, 14, set,
                hover(mouseX, mouseY, x + 38, y + 50, 52, 14), true, false);
        CorkBoardGui.button(graphics, font, x + 94, y + 50, font.width(adjust) + 12, 14, adjust,
                hover(mouseX, mouseY, x + 94, y + 50, font.width(adjust) + 12, 14), true, false);
    }

    private int quickRep(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, int delta) {
        Component label = Component.literal((delta > 0 ? "+" : "") + delta);
        int w = 28;
        CorkBoardGui.button(graphics, font, x, y, w, 14, label, hover(mouseX, mouseY, x, y, w, 14), true, false);
        return x + w + 3;
    }

    private void drawOrders(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, ordH, "ui.aerocouriers.village_admin.orders");
        StationSnapshotPayload.View v = snapshot.view();
        graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.orders.summary",
                pinned(), v.boardMax(), expensivePinned(), rarePinned()),
                x + 6, y + 14, CorkBoardGui.HEADER, false);
        int ticks = VillageRefreshUi.remainingTicks(minecraft);
        Component timer = ticks < 0 ? Component.empty() : VillageRefreshUi.countdownLabel(ticks);
        graphics.drawString(font, timer, x + 6, y + 24, CorkBoardGui.INK_MUTED, false);
        Component interval = Component.translatable("ui.aerocouriers.village_admin.interval");
        graphics.drawString(font, interval, x + 6, intervalRowY + 3, CorkBoardGui.HEADER, false);
        Component unit = Component.translatable("ui.aerocouriers.village_admin.interval.unit");
        int unitX = (intervalField == null ? x + 80 : intervalField.getX() + intervalField.getWidth()) + 4;
        graphics.drawString(font, unit, unitX, intervalRowY + 3, CorkBoardGui.HEADER, false);
        CorkBoardGui.button(graphics, font, intervalMinusX, intervalRowY, 14, 14,
                Component.translatable("ui.aerocouriers.village_admin.minus"),
                hover(mouseX, mouseY, intervalMinusX, intervalRowY, 14, 14), true, false);
        CorkBoardGui.button(graphics, font, intervalPlusX, intervalRowY, 14, 14,
                Component.translatable("ui.aerocouriers.village_admin.plus"),
                hover(mouseX, mouseY, intervalPlusX, intervalRowY, 14, 14), true, false);
        Component apply = Component.translatable("ui.aerocouriers.village_admin.apply");
        int applyW = Math.max(48, font.width(apply) + 10);
        CorkBoardGui.button(graphics, font, intervalApplyX, intervalRowY, applyW, 14, apply,
                hover(mouseX, mouseY, intervalApplyX, intervalRowY, applyW, 14), true, false);
        Component refresh = Component.translatable("ui.aerocouriers.village_admin.orders.refresh");
        Component regen = Component.translatable("ui.aerocouriers.village_admin.orders.regen");
        Component clear = Component.translatable("ui.aerocouriers.village_admin.orders.clear");
        int half = (w - 18) / 2;
        CorkBoardGui.button(graphics, font, x + 6, y + 54, half, 14, refresh,
                hover(mouseX, mouseY, x + 6, y + 54, half, 14), true, false);
        CorkBoardGui.button(graphics, font, x + 10 + half, y + 54, half, 14, regen,
                hover(mouseX, mouseY, x + 10 + half, y + 54, half, 14), true, false);
        CorkBoardGui.button(graphics, font, x + 6, y + 70, w - 12, 14, clear,
                hover(mouseX, mouseY, x + 6, y + 70, w - 12, 14), true, true);
    }

    private void drawStats(GuiGraphics graphics, int x, int y, int w) {
        section(graphics, x, y, w, statH, "ui.aerocouriers.village_admin.stats");
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
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.open", v.onBoard()));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.active", v.active()));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.board",
                pinned(), v.boardMax()));
        ty += 10;
        line(graphics, x + 6, ty, Component.translatable("ui.aerocouriers.village_admin.stats.special",
                expensivePinned(), rarePinned()));
    }

    private void drawPolicy(GuiGraphics graphics, int x, int y, int w, int mouseX, int mouseY) {
        section(graphics, x, y, w, polH, "ui.aerocouriers.village_admin.policy");
        StationSnapshotPayload.View v = snapshot.view();
        Component expensive = Component.translatable("ui.aerocouriers.village_admin.expensive",
                MoneyFormat.component(VillageBoardPolicy.EXPENSIVE_REWARD));
        Component rare = Component.translatable("ui.aerocouriers.village_admin.rare");
        CorkBoardGui.toggle(graphics, font, x + 6, y + 16, expensive, v.allowExpensive());
        CorkBoardGui.toggle(graphics, font, x + 6, y + 30, rare, v.allowRare());
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
        section(graphics, x, y, w, cmdH, "ui.aerocouriers.village_admin.commands");
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
        layoutChrome();
        Component rename = Component.translatable("ui.aerocouriers.village_admin.rename");
        int bw = font.width(rename) + 12;
        if (hover(mouseX, mouseY, left + colW - bw - 6, nameY + 14, bw, 14)) {
            openDialog(new VillageAdminDialogs.Rename(snapshot));
            return true;
        }
        int qx = left + 6;
        int qy = repY + 28;
        int[] deltas = {-10, -1, 1, 10};
        for (int delta : deltas) {
            if (hover(mouseX, mouseY, qx, qy, 28, 14)) {
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.REP_ADD, delta));
                return true;
            }
            qx += 31;
        }
        if (hover(mouseX, mouseY, left + 38, repY + 50, 52, 14) && exactRepField != null) {
            try {
                int value = Integer.parseInt(exactRepField.getValue().strip());
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.REP_SET, value));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        Component adjust = Component.translatable("ui.aerocouriers.village_admin.rep.adjust");
        if (hover(mouseX, mouseY, left + 94, repY + 50, font.width(adjust) + 12, 14)) {
            openDialog(new VillageAdminDialogs.Reputation(snapshot));
            return true;
        }
        if (hover(mouseX, mouseY, intervalMinusX, intervalRowY, 14, 14)) {
            nudgeInterval(-1);
            return true;
        }
        if (hover(mouseX, mouseY, intervalPlusX, intervalRowY, 14, 14)) {
            nudgeInterval(1);
            return true;
        }
        Component apply = Component.translatable("ui.aerocouriers.village_admin.apply");
        int applyW = Math.max(48, font.width(apply) + 10);
        if (hover(mouseX, mouseY, intervalApplyX, intervalRowY, applyW, 14) && intervalField != null) {
            try {
                int minutes = Integer.parseInt(intervalField.getValue().strip());
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.INTERVAL, minutes));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        int half = (colW - 18) / 2;
        if (hover(mouseX, mouseY, left + 6, ordY + 54, half, 14)) {
            PacketDistributor.sendToServer(VillageAdminPayload.of(snapshot.stationPos(),
                    VillageAdminPayload.Action.REFRESH));
            return true;
        }
        if (hover(mouseX, mouseY, left + 10 + half, ordY + 54, half, 14)) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.regen"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.regen"),
                    VillageAdminPayload.Action.REGENERATE));
            return true;
        }
        if (hover(mouseX, mouseY, left + 6, ordY + 70, colW - 12, 14)) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.clear"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.clear"),
                    VillageAdminPayload.Action.CLEAR));
            return true;
        }
        Component expensive = Component.translatable("ui.aerocouriers.village_admin.expensive",
                MoneyFormat.component(VillageBoardPolicy.EXPENSIVE_REWARD));
        if (hover(mouseX, mouseY, right + 6, polY + 16, CorkBoardGui.toggleW(font, expensive), 12)) {
            PacketDistributor.sendToServer(VillageAdminPayload.flag(snapshot.stationPos(),
                    VillageAdminPayload.Action.TOGGLE_EXPENSIVE, !snapshot.view().allowExpensive()));
            return true;
        }
        Component rare = Component.translatable("ui.aerocouriers.village_admin.rare");
        if (hover(mouseX, mouseY, right + 6, polY + 30, CorkBoardGui.toggleW(font, rare), 12)) {
            PacketDistributor.sendToServer(VillageAdminPayload.flag(snapshot.stationPos(),
                    VillageAdminPayload.Action.TOGGLE_RARE, !snapshot.view().allowRare()));
            return true;
        }
        int sx = right + 76;
        int sy = polY + 44;
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

    private void nudgeInterval(int delta) {
        if (intervalField == null) {
            return;
        }
        int current;
        try {
            current = Integer.parseInt(intervalField.getValue().strip());
        } catch (NumberFormatException e) {
            current = snapshot.view().refreshIntervalMin();
        }
        intervalField.setValue(Integer.toString(Mth.clamp(current + delta, 1, 1440)));
    }

    public void openRenameDialog() {
        openDialog(new VillageAdminDialogs.Rename(snapshot));
    }

    public void openReputationDialog() {
        openDialog(new VillageAdminDialogs.Reputation(snapshot));
    }

    public void openConfirmDialog(VillageAdminPayload.Action action) {
        if (action == VillageAdminPayload.Action.REGENERATE) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.regen"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.regen"),
                    action));
            return;
        }
        if (action == VillageAdminPayload.Action.CLEAR) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.clear"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.clear"),
                    action));
        }
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
