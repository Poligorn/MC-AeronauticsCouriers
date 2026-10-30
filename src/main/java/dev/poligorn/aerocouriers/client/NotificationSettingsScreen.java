package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.NoticeChannel;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * «Notifications and tasks» from the Profile: category × channel matrix plus toast / panel options. Writes the same
 * {@code aerocouriers-client.toml} keys as the NeoForge config screen; the Mail log is always on.
 */
public final class NotificationSettingsScreen extends Screen {
    private static final int CELL_W = 34;
    private static final int ROW_H = 18;
    private static final int LABEL_W = 130;
    private static final int[] SECONDS = {3, 5, 8, 10, 15, 20};

    private final @Nullable Screen parent;
    private final boolean operator;

    public NotificationSettingsScreen(@Nullable Screen parent, boolean operator) {
        super(Component.translatable("ui.aerocouriers.notify.settings.title"));
        this.parent = parent;
        this.operator = operator;
    }

    private List<NoticeChannel.Row> rows() {
        return java.util.Arrays.stream(NoticeChannel.Row.values())
                .filter(r -> operator || r != NoticeChannel.Row.ADMIN)
                .toList();
    }

    private int matrixLeft() {
        int total = LABEL_W + CELL_W * 4 + 16 + 170;
        return Math.max(8, (width - total) / 2);
    }

    private int top() {
        return Math.max(28, (height - (rows().size() + 1) * ROW_H - 40) / 2);
    }

    @Override
    protected void init() {
        int x = matrixLeft();
        int y = top() + ROW_H;
        for (NoticeChannel.Row row : rows()) {
            int cx = x + LABEL_W;
            for (NoticeChannel channel : NoticeChannel.values()) {
                addRenderableWidget(Button.builder(mark(CouriersClientConfig.channelEnabled(row, channel)), b -> {
                    boolean next = !CouriersClientConfig.channelEnabled(row, channel);
                    CouriersClientConfig.setChannel(row, channel, next);
                    b.setMessage(mark(next));
                }).bounds(cx, y, CELL_W - 4, ROW_H - 2).build());
                cx += CELL_W;
            }
            Button log = Button.builder(mark(true), b -> {
            }).bounds(cx, y, CELL_W - 4, ROW_H - 2).build();
            log.active = false;
            addRenderableWidget(log);
            y += ROW_H;
        }

        int ox = x + LABEL_W + CELL_W * 4 + 16;
        int oy = top();
        int ow = 170;
        addRenderableWidget(Button.builder(cornerLabel(), b -> {
            CouriersClientConfig.ToastCorner[] all = CouriersClientConfig.ToastCorner.values();
            CouriersClientConfig.ToastCorner cur = CouriersClientConfig.get(CouriersClientConfig.TOAST_CORNER,
                    CouriersClientConfig.ToastCorner.TOP_RIGHT);
            CouriersClientConfig.set(CouriersClientConfig.TOAST_CORNER, all[(cur.ordinal() + 1) % all.length]);
            b.setMessage(cornerLabel());
        }).bounds(ox, oy, ow, ROW_H - 2).build());
        oy += ROW_H;
        addRenderableWidget(Button.builder(maxLabel(), b -> {
            int cur = CouriersClientConfig.get(CouriersClientConfig.TOAST_MAX_VISIBLE, 3);
            CouriersClientConfig.set(CouriersClientConfig.TOAST_MAX_VISIBLE, cur >= 5 ? 1 : cur + 1);
            b.setMessage(maxLabel());
        }).bounds(ox, oy, ow, ROW_H - 2).build());
        oy += ROW_H;
        addRenderableWidget(Button.builder(secondsLabel(), b -> {
            int cur = CouriersClientConfig.get(CouriersClientConfig.TOAST_SECONDS, 5);
            int next = SECONDS[0];
            for (int s : SECONDS) {
                if (s > cur) {
                    next = s;
                    break;
                }
            }
            CouriersClientConfig.set(CouriersClientConfig.TOAST_SECONDS, next);
            b.setMessage(secondsLabel());
        }).bounds(ox, oy, ow, ROW_H - 2).build());
        oy += ROW_H;
        addRenderableWidget(Button.builder(panelLabel(), b -> {
            CouriersClientConfig.set(CouriersClientConfig.TASK_PANEL_MODE, TaskPanelHud.mode().next());
            b.setMessage(panelLabel());
        }).bounds(ox, oy, ow, ROW_H - 2).build());
        oy += ROW_H;
        addRenderableWidget(Button.builder(autoTrackLabel(ClientTasks.autoTrack()), b -> {
            boolean next = !ClientTasks.autoTrack();
            ClientTasks.toggleAutoTrack();
            b.setMessage(autoTrackLabel(next));
        }).bounds(ox, oy, ow, ROW_H - 2).build());
        oy += ROW_H;
        addRenderableWidget(Button.builder(volumeLabel(), b -> {
            double cur = CouriersClientConfig.get(CouriersClientConfig.SOUNDS_VOLUME, 1.0);
            double next = cur >= 2.0 - 1e-6 ? 0.0 : Math.min(2.0, Math.round((cur + 0.25) * 4) / 4.0);
            CouriersClientConfig.set(CouriersClientConfig.SOUNDS_VOLUME, next);
            b.setMessage(volumeLabel());
        }).bounds(ox, oy, ow, ROW_H - 2).build());
        oy += ROW_H;
        addRenderableWidget(Button.builder(dndLabel(), b -> {
            boolean next = !CouriersClientConfig.get(CouriersClientConfig.DO_NOT_DISTURB, false);
            CouriersClientConfig.set(CouriersClientConfig.DO_NOT_DISTURB, next);
            b.setMessage(dndLabel());
        }).bounds(ox, oy, ow, ROW_H - 2).build());

        int backY = Math.max(top() + (rows().size() + 1) * ROW_H, oy + ROW_H) + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 60, Math.min(height - 24, backY), 120, 20).build());
    }

    private static Component mark(boolean on) {
        return Component.literal(on ? "✔" : "—");
    }

    private static Component option(String key, Component value) {
        return Component.translatable("ui.aerocouriers.notify.settings." + key, value);
    }

    private static Component cornerLabel() {
        CouriersClientConfig.ToastCorner corner = CouriersClientConfig.get(CouriersClientConfig.TOAST_CORNER,
                CouriersClientConfig.ToastCorner.TOP_RIGHT);
        return option("corner", Component.translatable("ui.aerocouriers.notify.corner."
                + corner.name().toLowerCase(Locale.ROOT)));
    }

    private static Component maxLabel() {
        return option("max_visible", Component.literal(
                Integer.toString(CouriersClientConfig.get(CouriersClientConfig.TOAST_MAX_VISIBLE, 3))));
    }

    private static Component secondsLabel() {
        return option("seconds", Component.literal(
                Integer.toString(CouriersClientConfig.get(CouriersClientConfig.TOAST_SECONDS, 5))));
    }

    private static Component panelLabel() {
        return option("panel", Component.translatable("ui.aerocouriers.tasks.mode."
                + TaskPanelHud.mode().name().toLowerCase(Locale.ROOT)));
    }

    private static Component autoTrackLabel(boolean on) {
        return option("auto_track", Component.translatable(on ? "ui.aerocouriers.common.on" : "ui.aerocouriers.common.off"));
    }

    private static Component volumeLabel() {
        double v = CouriersClientConfig.get(CouriersClientConfig.SOUNDS_VOLUME, 1.0);
        return option("volume", Component.literal(Math.round(v * 100) + "%"));
    }

    private static Component dndLabel() {
        return option("dnd", Component.translatable(CouriersClientConfig.get(CouriersClientConfig.DO_NOT_DISTURB, false)
                ? "ui.aerocouriers.common.on" : "ui.aerocouriers.common.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, Math.max(8, top() - 22), 0xFFFFFFFF);
        int x = matrixLeft();
        int y = top();
        int cx = x + LABEL_W;
        for (String column : new String[] {"toast", "sound", "chat", "log"}) {
            Component label = Component.translatable("ui.aerocouriers.notify.channel." + column);
            graphics.drawCenteredString(font, label, cx + (CELL_W - 4) / 2, y + 5, 0xFFB8C0C8);
            cx += CELL_W;
        }
        y += ROW_H;
        for (NoticeChannel.Row row : rows()) {
            graphics.fill(x, y + 2, x + 3, y + ROW_H - 4, ClientToasts.color(row));
            graphics.drawString(font, Component.translatable("ui.aerocouriers.notify.row." + row.key()), x + 7, y + 4,
                    0xFFE6E8EA, false);
            y += ROW_H;
        }
        String key = BoardKeyMappings.TASK_PANEL.getTranslatedKeyMessage().getString();
        Component keyLine = Component.translatable("ui.aerocouriers.notify.settings.key", key);
        graphics.drawString(font, keyLine, x, Mth.clamp(y + 6, 0, height - 34), 0xFF9AA0A6, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
