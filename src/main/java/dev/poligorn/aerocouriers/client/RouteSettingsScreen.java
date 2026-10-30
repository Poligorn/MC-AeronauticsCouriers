package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Profile → "Route on Xaero maps" (mockup 07). Writes straight into {@code aerocouriers-client.toml [route]}. */
public final class RouteSettingsScreen extends Screen {
    private static final int ROW = 20;
    private static final int PAD = TabletGui.CONTENT_INSET;

    private final Screen parent;
    private final List<AbstractWidget> routeWidgets = new ArrayList<>();
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    public RouteSettingsScreen(Screen parent) {
        super(Component.translatable("ui.aerocouriers.route.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        routeWidgets.clear();
        boxW = Math.min(440, width - 16);
        boxH = Math.min(PAD * 2 + 12 + ROW * 7 + 40, height - 16);
        boxX = (width - boxW) / 2;
        boxY = (height - boxH) / 2;
        int colW = (boxW - PAD * 2 - 8) / 2;
        int left = boxX + PAD;
        int right = left + colW + 8;
        int y = boxY + PAD + 12;

        toggle(left, y, colW, "enabled", CouriersClientConfig.ROUTE_ENABLED);
        toggle(left, y + ROW, colW, "world_map", CouriersClientConfig.ROUTE_WORLD_MAP);
        toggle(left, y + ROW * 2, colW, "minimap", CouriersClientConfig.ROUTE_MINIMAP);
        toggle(left, y + ROW * 3, colW, "label", CouriersClientConfig.ROUTE_LABEL);
        toggle(left, y + ROW * 4, colW, "erase", CouriersClientConfig.ROUTE_ERASE_PASSED);
        toggle(left, y + ROW * 5, colW, "preview", CouriersClientConfig.ROUTE_WAYBILL_PREVIEW);
        toggle(left, y + ROW * 6, colW, "others", CouriersClientConfig.ROUTE_OTHER_ORDERS);

        slider(right, y, colW, "corridor", 16, 96, 1,
                () -> CouriersClientConfig.get(CouriersClientConfig.ROUTE_CORRIDOR),
                v -> CouriersClientConfig.set(CouriersClientConfig.ROUTE_CORRIDOR, (int) Math.round(v)),
                v -> Integer.toString((int) Math.round(v)));
        slider(right, y + ROW, colW, "delay", 0.5, 10, 0.5,
                () -> CouriersClientConfig.get(CouriersClientConfig.ROUTE_REROUTE_DELAY),
                v -> CouriersClientConfig.set(CouriersClientConfig.ROUTE_REROUTE_DELAY, v),
                v -> String.format(java.util.Locale.ROOT, "%.1f", v));
        cycle(right, y + ROW * 2, colW, "thickness", CouriersClientConfig.ROUTE_THICKNESS,
                CouriersClientConfig.RouteThickness.values());
        toggle(right, y + ROW * 3, colW, "contrast", CouriersClientConfig.ROUTE_CONTRAST);
        cycle(right, y + ROW * 4, colW, "eta", CouriersClientConfig.ROUTE_ETA_MODE,
                CouriersClientConfig.RouteEtaMode.values());

        boolean xaero = XaeroRouteHooks.status() != XaeroRouteHooks.Status.NO_XAERO;
        for (AbstractWidget widget : routeWidgets) {
            widget.active = xaero;
        }
        int btnY = boxY + boxH - PAD - 18;
        addRenderableWidget(TabletButton.create(left, btnY, 90, 18,
                Component.translatable("ui.aerocouriers.route.settings.reset"), b -> resetAll()));
        addRenderableWidget(TabletButton.create(boxX + boxW - PAD - 90, btnY, 90, 18,
                Component.translatable("ui.aerocouriers.route.settings.done"), b -> onClose()));
    }

    private void toggle(int x, int y, int w, String key, ModConfigSpec.BooleanValue value) {
        Component name = Component.translatable("ui.aerocouriers.route.settings." + key);
        TabletButton button = TabletButton.create(x, y, w, 18, onOff(name, CouriersClientConfig.get(value)), b -> {
            boolean next = !CouriersClientConfig.get(value);
            CouriersClientConfig.set(value, next);
            b.setMessage(onOff(name, next));
        });
        button.hint(Component.translatable("ui.aerocouriers.route.settings." + key + ".hint"));
        button.muteWhenInactive();
        add(button);
    }

    private static Component onOff(Component name, boolean on) {
        return Component.translatable("options.generic_value", name,
                on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private <E extends Enum<E>> void cycle(int x, int y, int w, String key, ModConfigSpec.EnumValue<E> value,
                                           E[] values) {
        Component name = Component.translatable("ui.aerocouriers.route.settings." + key);
        TabletButton button = TabletButton.create(x, y, w, 18, enumLabel(name, key, CouriersClientConfig.get(value)), b -> {
            E next = values[(CouriersClientConfig.get(value).ordinal() + 1) % values.length];
            CouriersClientConfig.set(value, next);
            b.setMessage(enumLabel(name, key, next));
        });
        button.muteWhenInactive();
        add(button);
    }

    private static Component enumLabel(Component name, String key, Enum<?> value) {
        return Component.translatable("options.generic_value", name, Component.translatable(
                "ui.aerocouriers.route.settings." + key + "." + value.name().toLowerCase(java.util.Locale.ROOT),
                CouriersClientConfig.get(CouriersClientConfig.ROUTE_FIXED_SPEED)));
    }

    private void slider(int x, int y, int w, String key, double min, double max, double step,
                        DoubleSupplier current, DoubleConsumer apply, DoubleFunction<String> format) {
        double start = (current.getAsDouble() - min) / (max - min);
        DoubleUnaryOperator snap = raw -> Math.round((min + raw * (max - min)) / step) * step;
        AbstractSliderButton slider = new AbstractSliderButton(x, y, w, 18, Component.empty(), start) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("ui.aerocouriers.route.settings." + key,
                        format.apply(snap.applyAsDouble(value))));
            }

            @Override
            protected void applyValue() {
                apply.accept(snap.applyAsDouble(value));
            }

            @Override
            protected ResourceLocation getSprite() {
                return active ? super.getSprite() : ResourceLocation.withDefaultNamespace("widget/slider");
            }

            @Override
            protected ResourceLocation getHandleSprite() {
                return active ? super.getHandleSprite() : ResourceLocation.withDefaultNamespace("widget/slider_handle");
            }
        };
        add(slider);
    }

    private void add(AbstractWidget widget) {
        routeWidgets.add(widget);
        addRenderableWidget(widget);
    }

    private void resetAll() {
        for (ModConfigSpec.ConfigValue<?> value : List.of(CouriersClientConfig.ROUTE_ENABLED,
                CouriersClientConfig.ROUTE_WORLD_MAP, CouriersClientConfig.ROUTE_MINIMAP,
                CouriersClientConfig.ROUTE_LABEL, CouriersClientConfig.ROUTE_ERASE_PASSED,
                CouriersClientConfig.ROUTE_WAYBILL_PREVIEW, CouriersClientConfig.ROUTE_OTHER_ORDERS,
                CouriersClientConfig.ROUTE_CORRIDOR, CouriersClientConfig.ROUTE_REROUTE_DELAY,
                CouriersClientConfig.ROUTE_THICKNESS, CouriersClientConfig.ROUTE_CONTRAST,
                CouriersClientConfig.ROUTE_ETA_MODE)) {
            reset(value);
        }
        rebuildWidgets();
    }

    private static <T> void reset(ModConfigSpec.ConfigValue<T> value) {
        CouriersClientConfig.set(value, value.getDefault());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xC0000000);
        TabletGui.drawListPanel(graphics, boxX, boxY, boxW, boxH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, boxX + PAD, boxY + PAD - 2, TabletGui.TEXT_HEADER, false);
        int colW = (boxW - PAD * 2 - 8) / 2;
        int right = boxX + PAD + colW + 8;
        int noteY = boxY + PAD + 12 + ROW * 5 + 4;
        for (var line : font.split(Component.translatable("ui.aerocouriers.route.settings.note"), colW)) {
            graphics.drawString(font, line, right, noteY, TabletGui.TEXT_MUTED, false);
            noteY += font.lineHeight;
        }
        int statusY = boxY + boxH - PAD - 18 - font.lineHeight - 6;
        graphics.drawString(font, statusLine(), boxX + PAD, statusY,
                XaeroRouteHooks.status() == XaeroRouteHooks.Status.LINE ? TabletGui.TEXT_BRIGHT
                        : TabletGui.TEXT_WARN, false);
        for (var widget : renderables) {
            if (widget instanceof TabletButton button && button.active && button.isHovered() && button.hint() != null) {
                graphics.renderTooltip(font, font.split(button.hint(), 220), mouseX, mouseY);
            }
        }
    }

    private Component statusLine() {
        String versions = XaeroRouteHooks.versions();
        return switch (XaeroRouteHooks.status()) {
            case NO_XAERO -> Component.translatable("ui.aerocouriers.route.settings.status.no_xaero");
            case WAITING -> Component.translatable("ui.aerocouriers.route.settings.status.waiting", versions);
            case LINE -> Component.translatable("ui.aerocouriers.route.settings.status.line", versions);
            case FALLBACK -> Component.translatable("ui.aerocouriers.route.settings.status.fallback", versions);
        };
    }
}
