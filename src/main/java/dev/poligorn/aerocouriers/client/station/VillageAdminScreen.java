package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.cargo.CargoSize;
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

/** Operator village admin. Rects from layout.json screen 07 (640×360). */
public final class VillageAdminScreen extends Screen {
    private StationSnapshotPayload snapshot;
    private CorkBoardLayout layout;
    private int ox;
    private int oy;
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

    private void origin() {
        layout = CorkBoardLayout.of(width, height);
        ox = layout.winX;
        oy = layout.winY;
    }

    private void rebuild() {
        clearWidgets();
        origin();
        intervalField = new EditBox(font, ox + 146, oy + 261, 28, 13,
                Component.translatable("ui.aerocouriers.village_admin.interval"));
        intervalField.setMaxLength(4);
        intervalField.setBordered(false);
        intervalField.setTextColor(CorkPalette.INPUT);
        intervalField.setValue(Integer.toString(snapshot.view().refreshIntervalMin()));
        exactRepField = new EditBox(font, ox + 82, oy + 190, 44, 14,
                Component.translatable("ui.aerocouriers.village_admin.rep.exact"));
        exactRepField.setMaxLength(3);
        exactRepField.setBordered(false);
        exactRepField.setTextColor(CorkPalette.INPUT);
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
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        origin();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        CorkBoardGui.fullBright();
        CorkBoardGui.adminWindow(graphics, layout);
        CorkBoardGui.blit(graphics, CorkSprites.HOUSE_BOX, ox + 29, oy + 26, 17, 13);
        CorkBoardGui.blitTint(graphics, CorkSprites.ICON_HOUSE, ox + 33, oy + 29, 7, 7, CorkPalette.HEADER_TITLE);
        String title = Component.translatable("ui.aerocouriers.cork.station", snapshot.name()).getString();
        CorkBoardGui.bold(graphics, font, CorkBoardGui.ellipsize(font, title, 400),
                ox + 50, oy + 25, CorkPalette.HEADER_TITLE);
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.village_admin.panel"),
                ox + 50, oy + 34, CorkPalette.ADMIN_OP);
        CorkBoardGui.blitTint(graphics, CorkSprites.ICON_GEAR, ox + 470, oy + 26, 7, 7, CorkPalette.ADMIN_OP);
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.village_admin.op"),
                ox + 481, oy + 27, CorkPalette.ADMIN_OP);
        CorkBoardGui.text(graphics, font, CorkBoardGui.ellipsize(font,
                        Component.translatable("ui.aerocouriers.village_admin.subtitle").getString(), 590),
                ox + 26, oy + 47, CorkPalette.HEADER_MUTED);

        drawName(graphics, mouseX, mouseY);
        drawReputation(graphics, mouseX, mouseY);
        drawOrders(graphics, mouseX, mouseY);
        drawStats(graphics);
        drawPolicy(graphics, mouseX, mouseY);
        drawCommands(graphics);
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.village_admin.persist.line1"),
                ox + 26, oy + 323, CorkPalette.ADMIN_HINT);
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.village_admin.persist.line2"),
                ox + 26, oy + 332, CorkPalette.ADMIN_HINT);
        CorkBoardGui.button(graphics, font, ox + 541, oy + 323, 76, 16,
                Component.translatable("ui.aerocouriers.village_admin.close"),
                hover(mouseX, mouseY, 541, 323, 76, 16), true, CorkBoardGui.ButtonStyle.NORMAL);

        if (dialog == null) {
            if (exactRepField != null) {
                CorkBoardGui.input(graphics, exactRepField.getX(), exactRepField.getY(),
                        exactRepField.getWidth(), exactRepField.getHeight(), exactRepField.isFocused(), false);
            }
            if (intervalField != null) {
                CorkBoardGui.input(graphics, intervalField.getX(), intervalField.getY(),
                        intervalField.getWidth(), intervalField.getHeight(), intervalField.isFocused(), false);
            }
            for (var widget : renderables) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        } else {
            graphics.fill(ox + 18, oy + 18, ox + 622, oy + 342, 0x88000000);
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

    private void drawName(GuiGraphics g, int mx, int my) {
        CorkBoardGui.section(g, font, ox + 26, oy + 66, 296, 44,
                Component.translatable("ui.aerocouriers.village_admin.name"));
        CorkBoardGui.input(g, ox + 32, oy + 83, 170, 14, false, false);
        CorkBoardGui.text(g, font, snapshot.name(), ox + 37, oy + 86, CorkPalette.ADMIN_VALUE);
        String counter = snapshot.name().length() + "/" + VillageBoardPolicy.NAME_MAX;
        CorkBoardGui.text(g, font, counter, ox + 196 - font.width(counter), oy + 86, CorkPalette.ADMIN_HINT);
        CorkBoardGui.button(g, font, ox + 208, oy + 83, 108, 14,
                Component.translatable("ui.aerocouriers.village_admin.rename"),
                hover(mx, my, 208, 83, 108, 14), true, CorkBoardGui.ButtonStyle.GOLD);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.name.hint"),
                ox + 32, oy + 100, CorkPalette.ADMIN_LABEL);
    }

    private void drawReputation(GuiGraphics g, int mx, int my) {
        CorkBoardGui.section(g, font, ox + 26, oy + 114, 296, 102,
                Component.translatable("ui.aerocouriers.village_admin.rep"));
        CorkBoardGui.reputationBar(g, ox + 32, oy + 133, 200, 7, snapshot.reputation(),
                VillageBoardPolicy.REPUTATION_MAX);
        CorkBoardGui.bold(g, font, Integer.toString(snapshot.reputation()), ox + 244, oy + 133, CorkPalette.REPUTATION);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.rep.of",
                VillageBoardPolicy.REPUTATION_MAX), ox + 260, oy + 134, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.text(g, font, "10", ox + 68, oy + 145, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.text(g, font, "25", ox + 128, oy + 145, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.text(g, font, "50", ox + 226, oy + 145, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.rep.thresholds"),
                ox + 34, oy + 157, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.rep.quick"),
                ox + 34, oy + 173, CorkPalette.ADMIN_LABEL);
        int[] deltas = {-10, -1, 1, 10};
        int[] xs = {82, 116, 150, 184};
        for (int i = 0; i < deltas.length; i++) {
            Component label = Component.literal((deltas[i] > 0 ? "+" : "") + deltas[i]);
            CorkBoardGui.ButtonStyle st = deltas[i] < 0
                    ? CorkBoardGui.ButtonStyle.DANGER : CorkBoardGui.ButtonStyle.NORMAL;
            CorkBoardGui.button(g, font, ox + xs[i], oy + 170, 30, 14, label,
                    hover(mx, my, xs[i], 170, 30, 14), true, st);
        }
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.rep.exact_label"),
                ox + 34, oy + 193, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.button(g, font, ox + 130, oy + 190, 62, 14,
                Component.translatable("ui.aerocouriers.village_admin.rep.set"),
                hover(mx, my, 130, 190, 62, 14), true, CorkBoardGui.ButtonStyle.GOLD);
        CorkBoardGui.button(g, font, ox + 198, oy + 190, 112, 14,
                Component.translatable("ui.aerocouriers.village_admin.rep.adjust"),
                hover(mx, my, 198, 190, 112, 14), true, CorkBoardGui.ButtonStyle.NORMAL);
    }

    private void drawOrders(GuiGraphics g, int mx, int my) {
        CorkBoardGui.section(g, font, ox + 26, oy + 220, 296, 98,
                Component.translatable("ui.aerocouriers.village_admin.orders"));
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.orders.summary",
                        pinned(), snapshot.view().boardMax(), expensivePinned(), rarePinned()),
                ox + 32, oy + 237, CorkPalette.ADMIN_VALUE);
        int ticks = VillageRefreshUi.remainingTicks(minecraft);
        if (ticks >= 0) {
            CorkBoardGui.text(g, font, VillageRefreshUi.countdownLabel(ticks), ox + 32, oy + 248, CorkPalette.ADMIN_LABEL);
        }
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.interval"),
                ox + 32, oy + 264, CorkPalette.ADMIN_VALUE);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.interval.unit"),
                ox + 178, oy + 264, CorkPalette.ADMIN_VALUE);
        CorkBoardGui.button(g, font, ox + 202, oy + 261, 14, 13,
                Component.translatable("ui.aerocouriers.village_admin.minus"),
                hover(mx, my, 202, 261, 14, 13), true, CorkBoardGui.ButtonStyle.NORMAL);
        CorkBoardGui.button(g, font, ox + 218, oy + 261, 14, 13,
                Component.translatable("ui.aerocouriers.village_admin.plus"),
                hover(mx, my, 218, 261, 14, 13), true, CorkBoardGui.ButtonStyle.NORMAL);
        CorkBoardGui.button(g, font, ox + 236, oy + 261, 74, 13,
                Component.translatable("ui.aerocouriers.village_admin.apply"),
                hover(mx, my, 236, 261, 74, 13), true, CorkBoardGui.ButtonStyle.GOLD);
        CorkBoardGui.button(g, font, ox + 32, oy + 280, 134, 14,
                Component.translatable("ui.aerocouriers.village_admin.orders.refresh"),
                hover(mx, my, 32, 280, 134, 14), true, CorkBoardGui.ButtonStyle.NORMAL);
        CorkBoardGui.button(g, font, ox + 170, oy + 280, 140, 14,
                Component.translatable("ui.aerocouriers.village_admin.orders.regen"),
                hover(mx, my, 170, 280, 140, 14), true, CorkBoardGui.ButtonStyle.NORMAL);
        CorkBoardGui.button(g, font, ox + 32, oy + 298, 278, 14,
                Component.translatable("ui.aerocouriers.village_admin.orders.clear"),
                hover(mx, my, 32, 298, 278, 14), true, CorkBoardGui.ButtonStyle.DANGER);
    }

    private void drawStats(GuiGraphics g) {
        CorkBoardGui.section(g, font, ox + 330, oy + 66, 284, 112,
                Component.translatable("ui.aerocouriers.village_admin.stats"));
        StationSnapshotPayload.View v = snapshot.view();
        int maxTier = StationReputation.tier(VillageBoardPolicy.REPUTATION_MAX);
        stat(g, 84, "ui.aerocouriers.village_admin.stats.rank_label",
                snapshot.reputationTier() + " "
                        + Component.translatable("ui.aerocouriers.village_admin.stats.of_tier", maxTier).getString());
        int next = snapshot.reputationNextThreshold();
        String repValue = next > snapshot.reputation()
                ? snapshot.reputation() + " "
                + Component.translatable("ui.aerocouriers.village_admin.stats.rep_next",
                VillageBoardPolicy.REPUTATION_MAX, next - snapshot.reputation()).getString()
                : snapshot.reputation() + " / " + VillageBoardPolicy.REPUTATION_MAX;
        stat(g, 97, "ui.aerocouriers.village_admin.stats.rep_label", repValue);
        stat(g, 110, "ui.aerocouriers.village_admin.stats.done_label", Integer.toString(v.completed()));
        stat(g, 123, "ui.aerocouriers.village_admin.stats.active_label",
                v.active() + " " + Component.translatable("ui.aerocouriers.village_admin.stats.in_transit").getString());
        stat(g, 136, "ui.aerocouriers.village_admin.stats.board_label",
                Component.translatable("ui.aerocouriers.village_admin.stats.board_value",
                        pinned(), v.boardMax()).getString());
        stat(g, 149, "ui.aerocouriers.village_admin.stats.special_label",
                expensivePinned() + " / " + rarePinned());
    }

    private void stat(GuiGraphics g, int y, String labelKey, String value) {
        CorkBoardGui.text(g, font, Component.translatable(labelKey), ox + 338, oy + y, CorkPalette.ADMIN_LABEL);
        CorkBoardGui.text(g, font, value, ox + 440, oy + y, CorkPalette.ADMIN_VALUE);
    }

    private void drawPolicy(GuiGraphics g, int mx, int my) {
        CorkBoardGui.section(g, font, ox + 330, oy + 182, 284, 66,
                Component.translatable("ui.aerocouriers.village_admin.policy"));
        StationSnapshotPayload.View v = snapshot.view();
        CorkBoardGui.toggle(g, ox + 338, oy + 200, v.allowExpensive());
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.expensive",
                        MoneyFormat.component(VillageBoardPolicy.EXPENSIVE_REWARD)),
                ox + 362, oy + 200, CorkPalette.ADMIN_VALUE);
        CorkBoardGui.toggle(g, ox + 338, oy + 213, v.allowRare());
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.rare"),
                ox + 362, oy + 213, CorkPalette.ADMIN_VALUE);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.sizes"),
                ox + 338, oy + 228, CorkPalette.ADMIN_LABEL);
        int[] xs = {390, 422, 454, 486};
        CargoSize[] sizes = CargoSize.values();
        for (int i = 0; i < sizes.length; i++) {
            boolean on = (v.allowedSizesMask() & VillageBoardPolicy.maskOf(sizes[i])) != 0;
            CorkBoardGui.checkbox(g, ox + xs[i], oy + 228, on);
            CorkBoardGui.text(g, font, sizes[i].name(), ox + xs[i] + 12, oy + 228, CorkPalette.ADMIN_VALUE);
        }
    }

    private void drawCommands(GuiGraphics g) {
        CorkBoardGui.section(g, font, ox + 330, oy + 252, 284, 66,
                Component.translatable("ui.aerocouriers.village_admin.commands"));
        CorkBoardGui.text(g, font, "/couriers admin village rename <имя>", ox + 338, oy + 270,
                CorkPalette.ADMIN_COMMAND);
        CorkBoardGui.text(g, font, "/couriers admin village rep add 10", ox + 338, oy + 281,
                CorkPalette.ADMIN_COMMAND);
        CorkBoardGui.text(g, font, "/couriers admin village orders refresh", ox + 338, oy + 292,
                CorkPalette.ADMIN_COMMAND);
        CorkBoardGui.text(g, font, Component.translatable("ui.aerocouriers.village_admin.commands.readme"),
                ox + 338, oy + 304, CorkPalette.ADMIN_HINT);
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
        origin();
        if (hover(mouseX, mouseY, 208, 83, 108, 14)) {
            openDialog(new VillageAdminDialogs.Rename(snapshot));
            return true;
        }
        int[] deltas = {-10, -1, 1, 10};
        int[] xs = {82, 116, 150, 184};
        for (int i = 0; i < deltas.length; i++) {
            if (hover(mouseX, mouseY, xs[i], 170, 30, 14)) {
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.REP_ADD, deltas[i]));
                return true;
            }
        }
        if (hover(mouseX, mouseY, 130, 190, 62, 14) && exactRepField != null) {
            try {
                int value = Integer.parseInt(exactRepField.getValue().strip());
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.REP_SET, value));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        if (hover(mouseX, mouseY, 198, 190, 112, 14)) {
            openDialog(new VillageAdminDialogs.Reputation(snapshot));
            return true;
        }
        if (hover(mouseX, mouseY, 202, 261, 14, 13)) {
            nudgeInterval(-1);
            return true;
        }
        if (hover(mouseX, mouseY, 218, 261, 14, 13)) {
            nudgeInterval(1);
            return true;
        }
        if (hover(mouseX, mouseY, 236, 261, 74, 13) && intervalField != null) {
            try {
                int minutes = Integer.parseInt(intervalField.getValue().strip());
                PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                        VillageAdminPayload.Action.INTERVAL, minutes));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        if (hover(mouseX, mouseY, 32, 280, 134, 14)) {
            PacketDistributor.sendToServer(VillageAdminPayload.of(snapshot.stationPos(),
                    VillageAdminPayload.Action.REFRESH));
            return true;
        }
        if (hover(mouseX, mouseY, 170, 280, 140, 14)) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.regen"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.regen"),
                    VillageAdminPayload.Action.REGENERATE));
            return true;
        }
        if (hover(mouseX, mouseY, 32, 298, 278, 14)) {
            openDialog(new VillageAdminDialogs.Confirm(snapshot,
                    Component.translatable("ui.aerocouriers.village_admin.orders.clear"),
                    Component.translatable("ui.aerocouriers.village_admin.confirm.clear"),
                    VillageAdminPayload.Action.CLEAR));
            return true;
        }
        if (hover(mouseX, mouseY, 338, 200, 160, 12)) {
            PacketDistributor.sendToServer(VillageAdminPayload.flag(snapshot.stationPos(),
                    VillageAdminPayload.Action.TOGGLE_EXPENSIVE, !snapshot.view().allowExpensive()));
            return true;
        }
        if (hover(mouseX, mouseY, 338, 213, 160, 12)) {
            PacketDistributor.sendToServer(VillageAdminPayload.flag(snapshot.stationPos(),
                    VillageAdminPayload.Action.TOGGLE_RARE, !snapshot.view().allowRare()));
            return true;
        }
        int[] sizeX = {390, 422, 454, 486};
        CargoSize[] sizes = CargoSize.values();
        for (int i = 0; i < sizes.length; i++) {
            if (hover(mouseX, mouseY, sizeX[i], 228, 28, 12)) {
                boolean on = (snapshot.view().allowedSizesMask() & VillageBoardPolicy.maskOf(sizes[i])) != 0;
                PacketDistributor.sendToServer(VillageAdminPayload.size(snapshot.stationPos(), sizes[i].ordinal(), !on));
                return true;
            }
        }
        if (hover(mouseX, mouseY, 541, 323, 76, 16)) {
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

    private boolean hover(double mx, double my, int x, int y, int w, int h) {
        return mx >= ox + x && mx < ox + x + w && my >= oy + y && my < oy + y + h;
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
