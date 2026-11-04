package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.TabletPalette;
import dev.poligorn.aerocouriers.config.EconomySettingsView;
import dev.poligorn.aerocouriers.contract.CollateralProfile;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.economy.MoneyScale;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Operator «Экономика»: the collateral profile block first (always fully visible unscrolled), then the five existing
 * valuation tools and a read-only list of related settings. The page scrolls with the mouse wheel.
 */
final class AdminEconomyUi {
    /** Example cargo value from the design mockups, in spurs. */
    private static final long EXAMPLE_VALUE_SPURS = 3456;
    private static final int SCROLL_STEP = 12;
    private static final int LINE_H = 11;

    private static final int SEGMENTS_Y = 12;
    private static final int TEXT_Y = 34;
    private static final int APPLY_Y = 92;
    private static final int TOOLS_Y = 136;
    private static final int TOOLS_BUTTONS_Y = TOOLS_Y + 12;
    private static final int RELATED_Y = TOOLS_BUTTONS_Y + 5 * 24 + 6;

    private final CourierBoardScreen screen;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<TabletTabButton> segments = new ArrayList<>();
    private final List<AbstractWidget> tools = new ArrayList<>();
    private @Nullable TabletButton applyButton;
    private @Nullable TabletButton confirmYes;
    private @Nullable TabletButton confirmNo;
    private boolean built;
    private int scroll;
    private @Nullable CollateralProfile selected;
    private boolean confirming;

    AdminEconomyUi(CourierBoardScreen screen) {
        this.screen = screen;
    }

    void reset() {
        clearWidgets();
        scroll = 0;
        selected = null;
        confirming = false;
    }

    /** Profile segments are {@code TabletTabButton}s, which the board's button sweep does not remove. */
    private void clearWidgets() {
        for (AbstractWidget widget : widgets) {
            screen.adminRemoveWidget(widget);
        }
        widgets.clear();
        segments.clear();
        tools.clear();
        applyButton = null;
        confirmYes = null;
        confirmNo = null;
        built = false;
    }

    boolean owns(AbstractWidget widget) {
        return widgets.contains(widget);
    }

    private void add(AbstractWidget widget) {
        widgets.add(widget);
        screen.adminAddWidget(widget);
    }

    boolean scroll(double delta, BoardSnapshotPayload snapshot, int top) {
        int before = scroll;
        int viewport = Math.max(1, screen.adminContentBottom() - top);
        int max = Math.max(0, contentHeight(snapshot) - viewport);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * SCROLL_STEP, 0, max);
        return scroll != before;
    }

    private int contentHeight(BoardSnapshotPayload snapshot) {
        return RELATED_Y + 14 + snapshot.extras().economyLines().size() * LINE_H + 4;
    }

    private CollateralProfile active(BoardSnapshotPayload snapshot) {
        return snapshot.extras().profile();
    }

    private CollateralProfile shown(BoardSnapshotPayload snapshot) {
        return selected != null ? selected : active(snapshot);
    }

    void init(BoardSnapshotPayload snapshot, int sx, int top, int sw) {
        // Structural refresh (open, resize, sub-tab, confirm). Scroll must call {@link #move} instead:
        // rebuilding here on every wheel notch stacked a second row of segment labels.
        rebuild(snapshot, sx, sw);
        move(snapshot, sx, top, sw);
    }

    /**
     * Wheel scroll only. Widgets stay mounted and change Y; creating a new row here stacked labels on the old one
     * because profile segments are not removed by the board's button sweep.
     */
    void move(BoardSnapshotPayload snapshot, int sx, int top, int sw) {
        if (!built) {
            init(snapshot, sx, top, sw);
            return;
        }
        int bottom = screen.adminContentBottom();
        CollateralProfile active = active(snapshot);
        CollateralProfile shown = shown(snapshot);
        CollateralProfile[] order = CollateralProfile.values();
        int gap = 3;
        int segW = (sw - 4 - gap * (order.length - 1)) / order.length;
        int segY = top + SEGMENTS_Y - scroll;
        for (int i = 0; i < segments.size() && i < order.length; i++) {
            TabletTabButton seg = segments.get(i);
            seg.setX(sx + i * (segW + gap));
            seg.setY(segY);
            seg.visible = visible(segY, 18, top, bottom);
            seg.selected(order[i] == shown);
        }
        int applyY = top + APPLY_Y - scroll;
        if (applyButton != null) {
            applyButton.setX(sx);
            applyButton.setY(applyY);
            applyButton.visible = visible(applyY, 18, top, bottom);
            applyButton.active = shown != active;
        }
        int by = applyY + 34;
        if (confirmYes != null) {
            confirmYes.setX(sx);
            confirmYes.setY(by);
            confirmYes.visible = visible(by, 18, top, bottom);
        }
        if (confirmNo != null) {
            confirmNo.setX(sx + 100);
            confirmNo.setY(by);
            confirmNo.visible = visible(by, 18, top, bottom);
        }
        int toolsY = top + TOOLS_BUTTONS_Y - scroll;
        int[] offsets = {0, 24, 48, 72, 96};
        int[] heights = {20, 20, 20, 20, 20};
        for (int i = 0; i < tools.size() && i < offsets.length; i++) {
            AbstractWidget tool = tools.get(i);
            tool.setX(sx);
            tool.setY(toolsY + offsets[i]);
            tool.visible = visible(toolsY + offsets[i], heights[i], top, bottom);
        }
    }

    private void rebuild(BoardSnapshotPayload snapshot, int sx, int sw) {
        clearWidgets();
        CollateralProfile shown = shown(snapshot);
        CollateralProfile[] order = CollateralProfile.values();
        int gap = 3;
        int segW = Math.max(1, (sw - 4 - gap * (order.length - 1)) / order.length);
        for (int i = 0; i < order.length; i++) {
            CollateralProfile p = order[i];
            TabletTabButton seg = new TabletTabButton(sx + i * (segW + gap), 0, segW, 18,
                    Component.translatable(p.langKey()), () -> {
                        selected = p;
                        confirming = false;
                        screen.refreshAdminContent();
                    });
            seg.selected(p == shown);
            segments.add(seg);
            add(seg);
        }
        if (!confirming) {
            applyButton = TabletButton.create(sx, 0, Math.min(160, sw - 4), 18,
                    Component.translatable("ui.aerocouriers.admin.economy.profile.apply"), b -> {
                        confirming = true;
                        screen.refreshAdminContent();
                    });
            add(applyButton);
        } else {
            confirmYes = TabletButton.create(sx, 0, 96, 18,
                    Component.translatable("ui.aerocouriers.admin.economy.profile.confirm_yes"), b -> {
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.ECONOMY_SET_COLLATERAL_PROFILE, Optional.empty(),
                                Optional.empty(), Optional.of(shown(snapshot).key()), Optional.empty(), 0L, true));
                        confirming = false;
                        selected = null;
                        screen.refreshAdminContent();
                    });
            confirmNo = TabletButton.create(sx + 100, 0, 72, 18,
                    Component.translatable("gui.cancel"), b -> {
                        confirming = false;
                        screen.refreshAdminContent();
                    });
            add(confirmYes);
            add(confirmNo);
        }
        int w = Math.min(200, sw - 4);
        addTool(sx, w, "ui.aerocouriers.admin.economy.reload", OperatorBoardPayload.Action.ECONOMY_RELOAD, 0);
        addTool(sx, w, "ui.aerocouriers.admin.economy.show_value", OperatorBoardPayload.Action.ECONOMY_SHOW_VALUE, 0);
        addTool(sx, w, "ui.aerocouriers.admin.economy.show_weight", OperatorBoardPayload.Action.ECONOMY_SHOW_WEIGHT, 0);
        AbstractWidget setValue = TabletButton.create(sx, 0, w, 20,
                Component.translatable("ui.aerocouriers.admin.economy.set_value", MoneyFormat.component(100)),
                b -> send(OperatorBoardPayload.Action.ECONOMY_SET_VALUE, 100));
        AbstractWidget setWeight = TabletButton.create(sx, 0, w, 20,
                Component.translatable("ui.aerocouriers.admin.economy.set_weight", 1.0),
                b -> send(OperatorBoardPayload.Action.ECONOMY_SET_WEIGHT, Double.doubleToLongBits(1.0)));
        tools.add(setValue);
        tools.add(setWeight);
        add(setValue);
        add(setWeight);
        built = true;
    }

    private void addTool(int sx, int w, String key, OperatorBoardPayload.Action action, long arg) {
        AbstractWidget button = TabletButton.create(sx, 0, w, 20, Component.translatable(key), b -> send(action, arg));
        tools.add(button);
        add(button);
    }

    private static boolean visible(int y, int h, int top, int bottom) {
        return y >= top && y + h <= bottom;
    }

    private static void send(OperatorBoardPayload.Action action, long arg) {
        PacketDistributor.sendToServer(new OperatorBoardPayload(action, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), arg, false));
    }

    void render(GuiGraphics g, BoardSnapshotPayload snapshot, int sx, int top, int sw) {
        Font font = screen.adminFont();
        int bottom = screen.adminContentBottom();
        CollateralProfile active = active(snapshot);
        CollateralProfile shown = shown(snapshot);
        int x1 = sx + sw - 4;

        line(g, font, Component.translatable("ui.aerocouriers.admin.economy.profile.title"), sx, top - scroll,
                TabletPalette.TXT, top, bottom);
        Component current = Component.translatable("ui.aerocouriers.admin.economy.profile.current",
                Component.translatable(active.langKey()));
        int cw = font.width(current);
        line(g, font, current, x1 - cw, top - scroll, TabletPalette.SEC, top, bottom);

        int y = top + TEXT_Y - scroll;
        for (FormattedCharSequence seq : font.split(description(shown, snapshot), sw - 6)) {
            if (y >= top && y + 9 <= bottom) {
                g.drawString(font, seq, sx, y, TabletPalette.SEC, false);
            }
            y += 10;
        }
        y = Math.max(y, top + TEXT_Y + 22 - scroll);
        for (Component preview : preview(shown, snapshot)) {
            line(g, font, preview, sx, y, TabletPalette.GOLD, top, bottom);
            y += LINE_H;
        }

        int applyY = top + APPLY_Y - scroll;
        if (confirming) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("ui.aerocouriers.admin.economy.profile.confirm",
                    Component.translatable(active.langKey()), Component.translatable(shown.langKey())));
            lines.add(Component.translatable("ui.aerocouriers.admin.economy.profile.confirm_players"));
            lines.add(Component.translatable(shown.noCollateral()
                    ? "ui.aerocouriers.admin.economy.profile.confirm_orders_peace"
                    : "ui.aerocouriers.admin.economy.profile.confirm_orders"));
            int cy = applyY - 2;
            for (Component c : lines) {
                line(g, font, c, sx, cy, cy == applyY - 2 ? TabletGui.TEXT_WARN : TabletPalette.SEC, top, bottom);
                cy += 10;
            }
        } else if (shown == active) {
            line(g, font, Component.translatable("ui.aerocouriers.admin.economy.profile.active_hint"),
                    sx + Math.min(160, sw - 4) + 6, applyY + 5, TabletPalette.LOCKC, top, bottom);
        }
        if (scroll == 0) {
            Component more = Component.translatable("ui.aerocouriers.admin.economy.more_below");
            int hintY = Math.min(bottom - 10, top + TOOLS_Y - 14);
            g.drawString(font, more, x1 - font.width(more), hintY, TabletPalette.LOCKC, false);
        }

        int toolsY = top + TOOLS_Y - scroll;
        line(g, font, Component.translatable("ui.aerocouriers.admin.economy.tools"), sx, toolsY, TabletPalette.TXT,
                top, bottom);
        if (active.noCollateral()) {
            Component note = Component.translatable("ui.aerocouriers.admin.economy.tools_peace_note");
            line(g, font, note, x1 - font.width(note), toolsY, TabletPalette.LOCKC, top, bottom);
        }

        int relY = top + RELATED_Y - scroll;
        line(g, font, Component.translatable("ui.aerocouriers.admin.economy.related"), sx, relY, TabletPalette.TXT,
                top, bottom);
        int ly = relY + 14;
        for (String raw : snapshot.extras().economyLines()) {
            int eq = raw.indexOf('=');
            String key = eq >= 0 ? raw.substring(0, eq) : raw;
            String value = eq >= 0 ? raw.substring(eq + 1) : "";
            Status status = status(key, active);
            Component statusText = Component.translatable("ui.aerocouriers.admin.economy.status." + status.key);
            int stW = font.width(statusText);
            String left = clip(font, key + " = " + value, Math.max(20, sw - stW - 14));
            Component leftC = status == Status.OFF
                    ? Component.literal(left).withStyle(ChatFormatting.STRIKETHROUGH)
                    : Component.literal(left);
            int color = status == Status.OFF || status == Status.UNUSED ? TabletPalette.LOCKC : TabletPalette.SEC;
            line(g, font, leftC, sx, ly, color, top, bottom);
            line(g, font, statusText, x1 - stW, ly, status.color, top, bottom);
            ly += LINE_H;
        }
        int total = contentHeight(snapshot);
        int viewport = bottom - top;
        if (total > viewport) {
            int trackX = sx + sw - 2;
            g.fill(trackX, top, trackX + 2, bottom, TabletPalette.SCROLL_TRACK);
            int thumbH = Math.max(12, viewport * viewport / total);
            int maxScroll = Math.max(1, total - viewport);
            int thumbY = top + (viewport - thumbH) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, TabletPalette.BRASS_LIGHT);
        }
    }

    private static void line(GuiGraphics g, Font font, Component text, int x, int y, int color, int top, int bottom) {
        if (y >= top && y + 9 <= bottom) {
            String plain = text.getString();
            if (MoneyText.iconUnits() && MoneyText.containsUnit(plain)) {
                MoneyText.draw(g, font, plain, x, y, color, MoneyText.Face.DEFAULT);
            } else {
                g.drawString(font, text, x, y, color, false);
            }
        }
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && font.width(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private static FormattedText description(CollateralProfile profile, BoardSnapshotPayload snapshot) {
        String key = "ui.aerocouriers.admin.economy.profile." + profile.key() + ".desc";
        if (profile.freeCollateral()) {
            return Component.translatable(key, percent(setting(snapshot, EconomySettingsView.NPC_CUSTOM_RATIO, 0.5)));
        }
        return Component.translatable(key);
    }

    private static List<Component> preview(CollateralProfile profile, BoardSnapshotPayload snapshot) {
        long value = MoneyScale.fromSpurs(EXAMPLE_VALUE_SPURS);
        double factor = setting(snapshot, EconomySettingsView.COLLATERAL_FACTOR, 1.0);
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("ui.aerocouriers.admin.economy.preview.cargo", MoneyFormat.component(value)));
        if (profile.noCollateral()) {
            out.add(Component.translatable("ui.aerocouriers.admin.economy.preview.none"));
            return out;
        }
        double npcShare = profile.freeCollateral()
                ? setting(snapshot, EconomySettingsView.NPC_CUSTOM_RATIO, 0.5)
                : profile.ratio();
        long npc = Math.round(value * npcShare * factor);
        if (profile.freeCollateral()) {
            out.add(Component.translatable("ui.aerocouriers.admin.economy.preview.custom",
                    MoneyFormat.component(Math.round(value * CollateralProfile.CUSTOM_MAX_RATIO)),
                    MoneyFormat.component(npc)));
        } else {
            out.add(Component.translatable("ui.aerocouriers.admin.economy.preview.min",
                    MoneyFormat.component(Math.round(value * profile.ratio())), MoneyFormat.component(npc)));
        }
        return out;
    }

    private static double setting(BoardSnapshotPayload snapshot, String key, double fallback) {
        for (String raw : snapshot.extras().economyLines()) {
            if (raw.startsWith(key + "=")) {
                try {
                    return Double.parseDouble(raw.substring(key.length() + 1));
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private enum Status {
        PROFILE("profile", TabletPalette.BLUE),
        CHANGES("changes", TabletPalette.GOLD),
        SAME("same", TabletPalette.LOCKC),
        OFF("off", TabletPalette.CORAL),
        UNUSED("unused", TabletPalette.LOCKC);

        final String key;
        final int color;

        Status(String key, int color) {
            this.key = key;
            this.color = color;
        }
    }

    private static Status status(String key, CollateralProfile profile) {
        return switch (key) {
            case EconomySettingsView.P2P_RATIO -> Status.PROFILE;
            case EconomySettingsView.COLLATERAL_FACTOR -> profile.noCollateral() ? Status.OFF : Status.CHANGES;
            case EconomySettingsView.NPC_CUSTOM_RATIO -> profile.freeCollateral() ? Status.CHANGES : Status.UNUSED;
            case EconomySettingsView.RANK_CAPS, EconomySettingsView.TRAINEE_FREE ->
                    profile.noCollateral() ? Status.OFF : Status.SAME;
            default -> Status.SAME;
        };
    }
}
