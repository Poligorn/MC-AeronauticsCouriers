package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.CouriersClientConfig.TaskPanelMode;
import dev.poligorn.aerocouriers.task.TaskView;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Right-middle task panel (190 wide, below the toast zone). Hidden with F1, F3, chat, pause and open screens. */
public final class TaskPanelHud {
    public static final int WIDTH = 190;
    private static final int PAD = 4;
    private static final int LINE = 10;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int AQUA = 0xFF55FFFF;
    private static final int RANK_BLUE = 0xFF5A82E8;

    private TaskPanelHud() {
    }

    public static TaskPanelMode mode() {
        return CouriersClientConfig.get(CouriersClientConfig.TASK_PANEL_MODE, TaskPanelMode.FULL);
    }

    public static void cycleMode() {
        TaskPanelMode next = mode().next();
        CouriersClientConfig.set(CouriersClientConfig.TASK_PANEL_MODE, next);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("ui.aerocouriers.tasks.panel_mode",
                    Component.translatable("ui.aerocouriers.tasks.mode." + next.name().toLowerCase(java.util.Locale.ROOT))),
                    true);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || ClientToasts.hidden() || mode() == TaskPanelMode.HIDDEN) {
            return;
        }
        List<TaskView> tasks = ClientTasks.onScreen();
        if (tasks.isEmpty()) {
            return;
        }
        render(event.getGuiGraphics(), mc.font, tasks);
    }

    public static int markerColor(TaskView task, int zone) {
        if (zone == 1) {
            return YELLOW;
        }
        if (zone == 2) {
            return AQUA;
        }
        return switch (task.source()) {
            case TUTORIAL -> ClientToasts.TEAL;
            case RANK_GOAL -> RANK_BLUE;
            case ORDER_CUSTOMER -> ClientToasts.GOLD;
            case MAIL -> ClientToasts.BLUE;
            case ORDER_COURIER -> {
                TaskView.Step step = current(task);
                yield step == null ? ClientToasts.GOLD : step.zone() == 2 ? AQUA : YELLOW;
            }
        };
    }

    private static TaskView.Step current(TaskView task) {
        int i = task.currentStep();
        return i < 0 ? null : task.steps().get(i);
    }

    public static String timer(TaskView task) {
        long left = ClientTasks.ticksLeft(task);
        if (left == Long.MAX_VALUE) {
            return "";
        }
        long seconds = Math.max(0, left / 20);
        if (seconds >= 3600) {
            return seconds / 3600 + "h" + String.format(java.util.Locale.ROOT, "%02d", (seconds / 60) % 60);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static void render(GuiGraphics graphics, Font font, List<TaskView> tasks) {
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int x = screenW - WIDTH - ClientToasts.MARGIN;
        TaskPanelMode mode = mode();
        int shown = Math.min(ClientTasks.PANEL_ROWS, tasks.size());
        int height = measure(font, tasks, shown, mode);
        CouriersClientConfig.ToastCorner corner =
                CouriersClientConfig.get(CouriersClientConfig.TOAST_CORNER, CouriersClientConfig.ToastCorner.TOP_RIGHT);
        int minY = corner == CouriersClientConfig.ToastCorner.TOP_RIGHT ? ClientToasts.zoneHeight() : PAD;
        int y = Math.max(minY, screenH / 2 - height / 2);
        if (y + height > screenH - PAD) {
            y = Math.max(PAD, screenH - PAD - height);
        }
        graphics.fill(x, y, x + WIDTH, y + height, 0xB0101418);
        int cy = y + PAD;
        boolean anyUrgent = tasks.stream().anyMatch(ClientTasks::urgent);
        Component header = Component.translatable("ui.aerocouriers.tasks.panel_title", tasks.size());
        graphics.drawString(font, header, x + PAD, cy, 0xFFE6E8EA, false);
        if (mode == TaskPanelMode.COLLAPSED) {
            if (anyUrgent) {
                String t = timer(tasks.getFirst());
                graphics.drawString(font, t, x + WIDTH - PAD - font.width(t), cy, ClientToasts.CORAL, false);
            }
            return;
        }
        cy += LINE + 2;
        for (int i = 0; i < shown; i++) {
            TaskView task = tasks.get(i);
            boolean urgent = ClientTasks.urgent(task);
            boolean expanded = mode == TaskPanelMode.FULL
                    && (task.pinned() || task.source() == TaskView.Source.TUTORIAL || i == 0);
            int rowH = rowHeight(task, expanded);
            if (urgent) {
                graphics.fill(x + 1, cy - 1, x + WIDTH - 1, cy + rowH - 1, 0x60FF6F61);
            }
            if (task.pinned()) {
                graphics.fill(x + 1, cy - 1, x + 2, cy + rowH - 1, ClientToasts.GOLD);
            }
            graphics.fill(x + 3, cy, x + 5, cy + 8, markerColor(task, 0));
            String t = timer(task);
            int titleW = WIDTH - PAD * 2 - 6 - (t.isEmpty() ? 0 : font.width(t) + 4);
            drawClipped(graphics, font, title(task), x + 8, cy, titleW, 0xFFFFFFFF);
            if (!t.isEmpty()) {
                graphics.drawString(font, t, x + WIDTH - PAD - font.width(t), cy, urgent ? ClientToasts.CORAL : 0xFFB8C0C8,
                        false);
            }
            cy += LINE;
            if (expanded) {
                for (TaskView.Step step : task.steps()) {
                    drawStep(graphics, font, task, step, x + 10, cy);
                    cy += LINE;
                }
            } else {
                TaskView.Step step = current(task);
                if (step != null && mode == TaskPanelMode.FULL) {
                    drawStep(graphics, font, task, step, x + 10, cy);
                    cy += LINE;
                }
            }
            cy += 2;
        }
        if (tasks.size() > shown) {
            Component more = Component.translatable("ui.aerocouriers.tasks.more", tasks.size() - shown);
            drawClipped(graphics, font, more, x + PAD, cy, WIDTH - PAD * 2, 0xFF9AA0A6);
        }
    }

    static Component title(TaskView task) {
        return task.title();
    }

    private static void drawStep(GuiGraphics graphics, Font font, TaskView task, TaskView.Step step, int x, int y) {
        String mark = switch (step.state()) {
            case DONE -> "✔";
            case CURRENT -> "▶";
            case TODO -> "○";
        };
        int color = switch (step.state()) {
            case DONE -> 0xFF7F8A93;
            case CURRENT -> step.zone() == 0 ? markerColor(task, 0) : markerColor(task, step.zone());
            case TODO -> 0xFFB8C0C8;
        };
        graphics.drawString(font, mark, x, y, color, false);
        String distance = "";
        if (step.state() == TaskView.StepState.CURRENT) {
            int d = ClientTasks.distance(task, step);
            if (d >= 0) {
                distance = d + " m";
            }
        }
        int right = TaskPanelHud.WIDTH - PAD * 2 - 14 - (distance.isEmpty() ? 0 : font.width(distance) + 4);
        drawClipped(graphics, font, step.text(), x + 9, y, right, step.state() == TaskView.StepState.DONE ? 0xFF7F8A93
                : 0xFFE6E8EA);
        if (!distance.isEmpty()) {
            graphics.drawString(font, distance, x - 10 + TaskPanelHud.WIDTH - PAD * 2 - font.width(distance), y, color,
                    false);
        }
    }

    private static void drawClipped(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        String plain = text.getString();
        if (MoneyText.iconUnits() && MoneyText.containsUnit(plain)) {
            MoneyText.draw(graphics, font, MoneyText.trunc(font, plain, Math.max(10, width)), x, y, color,
                    MoneyText.Face.DEFAULT);
            return;
        }
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, width));
        if (lines.isEmpty()) {
            return;
        }
        graphics.drawString(font, lines.getFirst(), x, y, color, false);
    }

    private static int rowHeight(TaskView task, boolean expanded) {
        int lines = 1 + (expanded ? task.steps().size() : 1);
        return lines * LINE + 2;
    }

    private static int measure(Font font, List<TaskView> tasks, int shown, TaskPanelMode mode) {
        int h = PAD * 2 + LINE;
        if (mode == TaskPanelMode.COLLAPSED) {
            return h;
        }
        h += 2;
        for (int i = 0; i < shown; i++) {
            TaskView task = tasks.get(i);
            boolean expanded = mode == TaskPanelMode.FULL
                    && (task.pinned() || task.source() == TaskView.Source.TUTORIAL || i == 0);
            if (mode == TaskPanelMode.COMPACT) {
                h += LINE + 2;
            } else {
                h += rowHeight(task, expanded);
            }
        }
        if (tasks.size() > shown) {
            h += LINE;
        }
        return h;
    }
}
