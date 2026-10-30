package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.TabletDraw;
import dev.poligorn.aerocouriers.client.board.TabletPalette;
import dev.poligorn.aerocouriers.net.TutorialIntentPayload;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import dev.poligorn.aerocouriers.task.TaskView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * «Tasks» tab: grouped list with eye / pin toggles on the left, details of the selected task on the right. All
 * choices go to the server ({@code TaskPrefPayload}); the list re-renders from the next {@code TaskSyncPayload}.
 */
final class BoardTasksUi {
    private static final int ROW_H = 16;
    private static final int GROUP_H = 13;
    private static final int TOP_H = 14;

    private enum Group {
        ACTIVE("ui.aerocouriers.tasks.group.active"),
        GOALS("ui.aerocouriers.tasks.group.goals"),
        MAIL("ui.aerocouriers.tasks.group.mail"),
        DONE("ui.aerocouriers.tasks.group.done");

        final String key;

        Group(String key) {
            this.key = key;
        }

        static Group of(TaskView task) {
            if (task.done()) {
                return DONE;
            }
            return switch (task.source()) {
                case RANK_GOAL -> GOALS;
                case MAIL -> MAIL;
                default -> ACTIVE;
            };
        }
    }

    /** One clickable rectangle of the last frame. */
    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private @Nullable String selected;
    private boolean doneExpanded;
    private int scroll;

    void reset() {
        scroll = 0;
    }

    void select(String id) {
        selected = id;
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        hits.clear();
        List<TaskView> all = new ArrayList<>(ClientTasks.active());
        all.addAll(ClientTasks.done());
        long onScreen = ClientTasks.onScreen().size();
        Component counter = Component.translatable("ui.aerocouriers.tasks.on_screen",
                Math.min(onScreen, ClientTasks.PANEL_ROWS), ClientTasks.PANEL_ROWS);
        graphics.drawString(font, counter, x + 2, y + 2, TabletPalette.SEC, false);
        Component auto = Component.translatable("ui.aerocouriers.tasks.auto_track",
                Component.translatable(ClientTasks.autoTrack() ? "ui.aerocouriers.common.on" : "ui.aerocouriers.common.off"));
        int autoW = font.width(auto);
        int autoX = x + w - autoW - 2;
        link(graphics, font, auto, autoX, y + 2, mouseX, mouseY, ClientTasks.autoTrack() ? TabletPalette.TEAL
                : TabletPalette.SEC, ClientTasks::toggleAutoTrack);

        int listW = Math.max(160, w * 55 / 100);
        int listTop = y + TOP_H;
        int listH = h - TOP_H;
        TabletGui.drawListPanel(graphics, x, listTop, listW, listH);
        if (all.isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.tasks.empty"), x + 6, listTop + 6,
                    TabletGui.TEXT_MUTED, false);
        }
        if (selected == null || all.stream().noneMatch(t -> t.id().equals(selected))) {
            selected = all.isEmpty() ? null : all.getFirst().id();
        }
        int cy = listTop + 3 - scroll * ROW_H;
        int bottom = listTop + listH - 3;
        Group current = null;
        for (TaskView task : all) {
            Group group = Group.of(task);
            if (group != current) {
                current = group;
                if (cy >= listTop && cy + GROUP_H <= bottom) {
                    Component label = Component.translatable(group.key);
                    if (group == Group.DONE) {
                        long n = all.stream().filter(TaskView::done).count();
                        label = Component.literal((doneExpanded ? "▾ " : "▸ ")).append(label).append(" (" + n + ")");
                        int gy = cy;
                        hits.add(new Hit(x + 2, gy, x + listW - 2, gy + GROUP_H, () -> doneExpanded = !doneExpanded));
                    }
                    graphics.drawString(font, label, x + 5, cy + 3, TabletPalette.SEC, false);
                }
                cy += GROUP_H;
            }
            if (group == Group.DONE && !doneExpanded) {
                continue;
            }
            if (cy >= listTop && cy + ROW_H <= bottom) {
                drawRow(graphics, font, task, x + 2, cy, listW - 4, mouseX, mouseY);
            }
            cy += ROW_H;
        }
        int total = cy + scroll * ROW_H - (listTop + 3);
        maxScroll = Math.max(0, (total - (listH - 6) + ROW_H - 1) / ROW_H);
        scroll = Math.min(scroll, maxScroll);

        int detailX = x + listW + 6;
        int detailW = w - listW - 6;
        TabletGui.drawListPanel(graphics, detailX, listTop, detailW, listH);
        Optional<TaskView> sel = selected == null ? Optional.empty() : ClientTasks.byId(selected);
        sel.ifPresent(task -> drawDetails(graphics, font, task, detailX + 6, listTop + 6, detailW - 12, mouseX, mouseY));
    }

    private int maxScroll;

    boolean scroll(double delta) {
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return before != scroll;
    }

    private void drawRow(GuiGraphics graphics, Font font, TaskView task, int x, int y, int w, int mouseX, int mouseY) {
        boolean sel = task.id().equals(selected);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H - 1;
        TabletGui.drawRow(graphics, x, y, w, ROW_H - 1, sel);
        if (hover && !sel) {
            graphics.fill(x, y, x + w, y + ROW_H - 1, 0x18FFFFFF);
        }
        boolean urgent = !task.done() && ClientTasks.urgent(task);
        if (urgent) {
            graphics.fill(x, y, x + w, y + ROW_H - 1, 0x40EC8068);
        }
        graphics.fill(x + 1, y + 2, x + 3, y + ROW_H - 3, TaskPanelHud.markerColor(task, 0));
        int right = x + w - 4;
        if (!task.done()) {
            String pin = task.pinned() ? "▲" : "△";
            int pinX = right - font.width(pin);
            iconToggle(graphics, font, pin, pinX, y + 4, mouseX, mouseY, task.pinned() ? TabletPalette.GOLD
                    : TabletPalette.LOCKC, () -> ClientTasks.togglePinned(task.id()));
            String eye = task.tracked() ? "◉" : "○";
            int eyeX = pinX - 6 - font.width(eye);
            iconToggle(graphics, font, eye, eyeX, y + 4, mouseX, mouseY, task.tracked() ? TabletPalette.TEAL
                    : TabletPalette.LOCKC, () -> ClientTasks.toggleTracked(task.id()));
            right = eyeX - 4;
        }
        String timer = task.done() ? "" : TaskPanelHud.timer(task);
        if (!timer.isEmpty()) {
            int tx = right - font.width(timer);
            graphics.drawString(font, timer, tx, y + 4, urgent ? TabletPalette.CORAL : TabletPalette.SEC, false);
            right = tx - 4;
        }
        String title = TabletDraw.trunc(font, task.title().getString(), right - (x + 7));
        graphics.drawString(font, title, x + 7, y + 4, task.done() ? TabletGui.TEXT_MUTED : TabletPalette.TXT, false);
        hits.add(new Hit(x, y, right, y + ROW_H - 1, () -> selected = task.id()));
    }

    private void drawDetails(GuiGraphics graphics, Font font, TaskView task, int x, int y, int w, int mouseX, int mouseY) {
        graphics.drawString(font, TabletDraw.trunc(font, task.title().getString(), w), x, y,
                TaskPanelHud.markerColor(task, 0), false);
        y += 12;
        String timer = TaskPanelHud.timer(task);
        if (!timer.isEmpty() && !task.done()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.tasks.time_left", timer), x, y,
                    ClientTasks.urgent(task) ? TabletPalette.CORAL : TabletPalette.SEC, false);
            y += 11;
        }
        if (task.progress() >= 0 && task.source() == TaskView.Source.RANK_GOAL) {
            graphics.fill(x, y, x + w, y + 4, TabletPalette.SLOT_BG);
            graphics.fill(x, y, x + Math.round(w * Math.min(1F, task.progress())), y + 4, TabletPalette.BLUE);
            y += 8;
        }
        for (TaskView.Step step : task.steps()) {
            String mark = switch (step.state()) {
                case DONE -> "✔";
                case CURRENT -> "▶";
                case TODO -> "○";
            };
            int color = step.state() == TaskView.StepState.DONE ? TabletGui.TEXT_MUTED
                    : step.state() == TaskView.StepState.CURRENT ? TaskPanelHud.markerColor(task, step.zone())
                    : TabletPalette.SEC;
            graphics.drawString(font, mark, x, y, color, false);
            Component text = step.text();
            if (step.state() == TaskView.StepState.CURRENT) {
                int d = ClientTasks.distance(task, step);
                if (d >= 0) {
                    text = text.copy().append(" · ").append(Component.translatable("ui.aerocouriers.tasks.blocks", d));
                }
            }
            for (FormattedCharSequence line : font.split(text, w - 10)) {
                graphics.drawString(font, line, x + 10, y, step.state() == TaskView.StepState.DONE
                        ? TabletGui.TEXT_MUTED : TabletPalette.TXT, false);
                y += 10;
            }
            y += 1;
        }
        if (task.done()) {
            return;
        }
        y += 6;
        Component show = Component.translatable("ui.aerocouriers.tasks.show_on_screen",
                Component.translatable(task.tracked() ? "ui.aerocouriers.common.yes" : "ui.aerocouriers.common.no"));
        link(graphics, font, show, x, y, mouseX, mouseY, task.tracked() ? TabletPalette.TEAL : TabletPalette.SEC,
                () -> ClientTasks.toggleTracked(task.id()));
        y += 12;
        Component pin = Component.translatable("ui.aerocouriers.tasks.pin_top",
                Component.translatable(task.pinned() ? "ui.aerocouriers.common.yes" : "ui.aerocouriers.common.no"));
        link(graphics, font, pin, x, y, mouseX, mouseY, task.pinned() ? TabletPalette.GOLD : TabletPalette.SEC,
                () -> ClientTasks.togglePinned(task.id()));
        y += 14;
        Optional<ZoneSyncPayload.Zone> zone = currentZone(task);
        if (zone.isPresent() && XaeroOrderBridge.xaeroWorldMapPresent()) {
            ZoneSyncPayload.Zone target = zone.get();
            link(graphics, font, Component.translatable("ui.aerocouriers.tasks.on_map"), x, y, mouseX, mouseY,
                    TabletPalette.BLUE, () -> XaeroOrderBridge.openWorldMapAt(target.pos()));
            y += 12;
        }
        if (task.contractId().isPresent() && task.source() != TaskView.Source.TUTORIAL) {
            link(graphics, font, Component.translatable("ui.aerocouriers.tasks.open_order"), x, y, mouseX, mouseY,
                    TabletPalette.GOLD, () -> ClientBoard.focusContract(task.contractId().get()));
            y += 12;
        }
        if (task.source() == TaskView.Source.MAIL) {
            link(graphics, font, Component.translatable("ui.aerocouriers.tasks.open_mail"), x, y, mouseX, mouseY,
                    TabletPalette.BLUE, openMail);
            y += 12;
        }
        if (task.source() == TaskView.Source.TUTORIAL) {
            link(graphics, font, Component.translatable("ui.aerocouriers.tutorial.skip"), x, y, mouseX, mouseY,
                    TabletPalette.SEC, () -> ClientBoard.tutorial(TutorialIntentPayload.Op.SKIP, 0));
        }
    }

    private Runnable openMail = () -> {
    };

    void onOpenMail(Runnable action) {
        openMail = action;
    }

    private static Optional<ZoneSyncPayload.Zone> currentZone(TaskView task) {
        int i = task.currentStep();
        if (i < 0) {
            return Optional.empty();
        }
        int zone = task.steps().get(i).zone();
        if (zone == 0 && task.source() == TaskView.Source.ORDER_COURIER) {
            zone = i <= 1 ? 1 : 2;
        }
        return ClientTasks.zone(task, zone);
    }

    private void link(GuiGraphics graphics, Font font, Component text, int x, int y, int mouseX, int mouseY, int color,
                      Runnable action) {
        int w = font.width(text);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 9;
        graphics.drawString(font, text, x, y, hover ? TabletGui.TEXT_BRIGHT : color, false);
        if (hover) {
            graphics.fill(x, y + 9, x + w, y + 10, color);
        }
        hits.add(new Hit(x, y - 1, x + w, y + 9, action));
    }

    private void iconToggle(GuiGraphics graphics, Font font, String glyph, int x, int y, int mouseX, int mouseY,
                            int color, Runnable action) {
        int w = font.width(glyph);
        boolean hover = mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= y - 3 && mouseY < y + 10;
        graphics.drawString(font, glyph, x, y, hover ? TabletGui.TEXT_BRIGHT : color, false);
        hits.add(0, new Hit(x - 2, y - 3, x + w + 2, y + 10, action));
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action().run();
                ClientCouriersSounds.playUi(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
                return true;
            }
        }
        return false;
    }
}
