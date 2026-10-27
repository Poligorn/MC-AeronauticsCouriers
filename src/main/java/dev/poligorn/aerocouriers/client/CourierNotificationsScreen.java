package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.MarkNotificationsReadPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tablet-style notification list (newest first). */
public final class CourierNotificationsScreen extends Screen {
    private static final int ROW_H = 22;

    private final Screen parent;
    private final TabletScreenChrome tabletChrome = new TabletScreenChrome(false);
    private final TabletListRowEffects listRowEffects = new TabletListRowEffects();
    private int scroll;

    public CourierNotificationsScreen(Screen parent) {
        super(Component.translatable("ui.aerocouriers.notifications.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        PacketDistributor.sendToServer(MarkNotificationsReadPayload.INSTANCE);
        addRenderableWidget(TabletButton.create(contentLeft() + contentW() - 68, contentTop(), 68, 20,
                Component.translatable("gui.done"), b -> onClose()));
    }

    @Override
    public void onClose() {
        tabletChrome.requestClose(() -> {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        int maxScroll = Math.max(0, entries.size() - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
                if (index < entries.size()) {
                    Optional<UUID> contract = entries.get(index).contractId();
                    onClose();
                    contract.ifPresent(ClientBoard::focusContract);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tabletChrome.tick();
        tabletChrome.drawTabletBody(graphics, () -> {
            TabletGui.drawScreenBackground(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH(), partialTick);
            for (net.minecraft.client.gui.components.Renderable widget : renderables) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            int cx = contentLeft();
            int cy = contentTop();
            graphics.drawString(font, title, cx, cy, TabletGui.TEXT, false);
            drawNotificationRows(graphics, mouseX, mouseY);
            TabletGui.drawFrameChrome(graphics, panelLeft(), panelTop(), panelW(), panelH());
        });
    }

    private void drawNotificationRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.notifications.empty"),
                    contentLeft(), listTop(), TabletGui.TEXT_MUTED, false);
            return;
        }

        int cx = contentLeft();
        int listW = contentW();
        int y = listTop();
        int visible = visibleRows();
        String listKey = scroll + "|" + entries.size();
        listRowEffects.onListPopulated(listKey, visible);
        for (int i = 0; i < visible && scroll + i < entries.size(); i++) {
            NotificationSyncPayload.Line line = entries.get(scroll + i);
            int rowY = y + i * ROW_H;
            boolean hover = mouseX >= cx && mouseX < cx + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            float hoverStrength = listRowEffects.hoverStrength(i, hover);
            float slideX = listRowEffects.rowSlideX(i);
            float rowAlpha = listRowEffects.rowAlpha(i);
            TabletGui.drawRow(graphics, cx, rowY, listW, ROW_H - 2, false, hoverStrength, rowAlpha, slideX);
            graphics.pose().pushPose();
            graphics.pose().translate(slideX, 0.0F, 0.0F);
            TabletAnim.withAlpha(graphics, rowAlpha, () -> {
                String time = formatAge(line.at());
                Component timeLine = Component.literal(time);
                graphics.drawString(font, timeLine, cx + 4, rowY + 2, TabletGui.TEXT_MUTED, false);
                int textX = cx + 4 + font.width(time) + 6;
                int textW = listW - (textX - cx) - 4;
                List<FormattedCharSequence> wrapped = font.split(line.text(), textW);
                int ty = rowY + 2;
                for (int lineIdx = 0; lineIdx < Math.min(2, wrapped.size()); lineIdx++) {
                    graphics.drawString(font, wrapped.get(lineIdx), textX, ty, TabletGui.TEXT, false);
                    ty += font.lineHeight;
                }
            });
            graphics.pose().popPose();
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        int left = contentLeft();
        int listW = contentW();
        if (mouseX < left || mouseX >= left + listW) {
            return -1;
        }
        int y = listTop();
        int visible = visibleRows();
        for (int i = 0; i < visible && scroll + i < ClientNotifications.entries().size(); i++) {
            int rowY = y + i * ROW_H;
            if (mouseY >= rowY && mouseY < rowY + ROW_H - 2) {
                return scroll + i;
            }
        }
        return -1;
    }

    private String formatAge(long at) {
        long now = ClientNotifications.worldTime();
        long ticks = Math.max(0, now - at);
        long seconds = ticks / 20;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h";
        }
        return String.format(Locale.ROOT, "%dd", hours / 24);
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    private int listTop() {
        return contentTop() + font.lineHeight + 6;
    }

    private int listBottom() {
        return contentTop() + contentH();
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
        return Math.min(420, width - 32);
    }

    private int panelH() {
        return Math.min(300, Math.max(200, TabletGui.maxFrameHeight(height)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
