package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.RenameStationPayload;
import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Post station window: reputation, NPC orders from this station, operator rename (GDD §6.0). */
public final class PostStationScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int PAD = 8;

    private final StationSnapshotPayload snapshot;
    private int scroll;
    private @Nullable EditBox nameField;

    public PostStationScreen(StationSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.station.title", snapshot.name()));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        super.init();
        int left = panelLeft();
        int top = panelTop();
        int innerW = panelW() - PAD * 2;
        if (snapshot.canRename()) {
            nameField = new EditBox(font, left + PAD, top + 28, innerW - 80, 18,
                    Component.translatable("ui.aerocouriers.station.rename"));
            nameField.setValue(snapshot.name());
            nameField.setMaxLength(48);
            addRenderableWidget(nameField);
            addRenderableWidget(TabletButton.create(left + PAD + innerW - 72, top + 26, 72, 20,
                    Component.translatable("ui.aerocouriers.station.rename.action"), b -> rename()));
        }
        int btnY = panelTop() + panelH() - 28;
        addRenderableWidget(TabletButton.create(left + PAD, btnY, 120, 20,
                Component.translatable("ui.aerocouriers.station.open_board"), b -> openBoard()));
        addRenderableWidget(TabletButton.create(left + panelW() - PAD - 68, btnY, 68, 20,
                Component.translatable("gui.done"), b -> onClose()));
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
        scroll = Math.max(0, Math.min(max, scroll - (int) scrollY));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                UUID id = snapshot.orders().get(index).contractId();
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
        TabletGui.drawScreenBackground(graphics, panelLeft(), panelTop(), panelW(), panelH());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = panelTop();
        graphics.drawString(font, title, left + PAD, top + PAD, TabletGui.TEXT, false);
        Component rep = Component.translatable("ui.aerocouriers.station.reputation",
                snapshot.reputation(), snapshot.bonusPercent());
        graphics.drawString(font, rep, left + PAD, top + (snapshot.canRename() ? 50 : 28), TabletGui.TEXT_MUTED, false);

        int listTop = top + (snapshot.canRename() ? 66 : 44);
        List<StationSnapshotPayload.OrderLine> orders = snapshot.orders();
        if (orders.isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.station.orders.empty"),
                    left + PAD, listTop, TabletGui.TEXT_MUTED, false);
        } else {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.station.orders.header"),
                    left + PAD, listTop, TabletGui.TEXT, false);
            int y = listTop + font.lineHeight + 4;
            int listW = panelW() - PAD * 2;
            int visible = visibleRows();
            for (int i = 0; i < visible && scroll + i < orders.size(); i++) {
                StationSnapshotPayload.OrderLine line = orders.get(scroll + i);
                int rowY = y + i * ROW_H;
                boolean hover = mouseX >= left + PAD && mouseX < left + PAD + listW
                        && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
                TabletGui.drawRow(graphics, left + PAD, rowY, listW, ROW_H - 2, hover);
                String text = "#" + line.shortId() + "  " + line.route() + " · " + line.reward() + " sp";
                graphics.drawString(font, text, left + PAD + 4, rowY + 4, TabletGui.TEXT, false);
            }
        }
        TabletGui.drawFrameChrome(graphics, left, top, panelW(), panelH());
    }

    private int rowAt(double mouseX, double mouseY) {
        int left = panelLeft() + PAD;
        int listW = panelW() - PAD * 2;
        int listTop = panelTop() + (snapshot.canRename() ? 66 : 44) + font.lineHeight + 4;
        if (mouseX < left || mouseX >= left + listW) {
            return -1;
        }
        int visible = visibleRows();
        for (int i = 0; i < visible && scroll + i < snapshot.orders().size(); i++) {
            int rowY = listTop + i * ROW_H;
            if (mouseY >= rowY && mouseY < rowY + ROW_H - 2) {
                return scroll + i;
            }
        }
        return -1;
    }

    private int visibleRows() {
        int listTop = panelTop() + (snapshot.canRename() ? 66 : 44) + font.lineHeight + 4;
        int bottom = panelTop() + panelH() - 36;
        return Math.max(1, (bottom - listTop) / ROW_H);
    }

    private int panelLeft() {
        return (width - panelW()) / 2;
    }

    private int panelTop() {
        return (height - panelH()) / 2;
    }

    private int panelW() {
        return Math.min(440, width - 32);
    }

    private int panelH() {
        return Math.min(320, height - 48);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
