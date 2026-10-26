package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.OpenWaybillCreatePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing order creation: destination, money, deadline. Cargo and pickup come from the stamped container.
 * Layout is computed from the current scaled window and font (see {@link WaybillCreateLayout}).
 * Clipboard layout: wooden CSI frame + paper sheet (see {@link WaybillCreateGui}).
 */
public final class WaybillCreateScreen extends Screen {
    private final OpenWaybillCreatePayload preview;
    private String toX = "";
    private String toY = "";
    private String toZ = "";
    private String reward = Long.toString(CreateContractPayload.DEFAULT_REWARD);
    private String collateral = Long.toString(CreateContractPayload.DEFAULT_COLLATERAL);
    private String minutes = Integer.toString(Pricing.MIN_DEADLINE_MINUTES);
    private String radius = Integer.toString(CreateContractPayload.DEFAULT_RADIUS);
    private @Nullable Component notice;
    private @Nullable Component hoverTip;
    private WaybillCreateLayout layout = WaybillCreateLayout.compute(400, 300, 9, 0, false, false, 0);
    private boolean xaeroMap;
    private boolean xaeroList;

    public WaybillCreateScreen(OpenWaybillCreatePayload preview) {
        super(Component.translatable("ui.aerocouriers.stamp.title"));
        this.preview = preview;
    }

    void applyPicked(XaeroPickedWaypoint picked) {
        toX = Integer.toString(picked.x());
        toY = Integer.toString(picked.y());
        toZ = Integer.toString(picked.z());
        notice = picked.yEstimated()
                ? Component.translatable("ui.aerocouriers.create.picked_y_estimated", picked.name(), picked.y())
                : Component.translatable("ui.aerocouriers.create.picked", picked.name());
    }

    void refreshAfterPick() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        xaeroMap = XaeroMapPicker.canOpenMap();
        List<XaeroPickedWaypoint> waypoints = xaeroMap || XaeroMapPicker.available()
                ? XaeroMapPicker.listWaypoints()
                : List.of();
        xaeroList = XaeroMapPicker.available();
        int mapLabelW = font.width(Component.translatable("ui.aerocouriers.create.pick_on_map"));
        layout = WaybillCreateLayout.compute(
                width, height, font.lineHeight, preview.cargo().size(), xaeroMap, xaeroList, mapLabelW);

        addRenderableWidget(coordBox(0, toX, v -> toX = v));
        addRenderableWidget(coordBox(1, toY, v -> toY = v));
        addRenderableWidget(coordBox(2, toZ, v -> toZ = v));
        if (xaeroMap) {
            addRenderableWidget(CourierButton.create(
                            layout.mapBtnX, layout.mapBtnY, layout.mapBtnW, WaybillCreateLayout.FIELD_H,
                            Component.translatable("ui.aerocouriers.create.pick_on_map"),
                            b -> XaeroMapPicker.openMapForPick(this))
                    .hint(Component.translatable(
                            XaeroMapPicker.worldMapPresent()
                                    ? "ui.aerocouriers.create.pick_on_map.hint_world"
                                    : "ui.aerocouriers.create.pick_on_map.hint_minimap")));
        }
        addStationCycle();
        if (xaeroList) {
            addWaypointCycle(waypoints);
        }

        addRenderableWidget(numberBox(0, reward, v -> reward = v));
        addRenderableWidget(numberBox(1, collateral, v -> collateral = v));
        addRenderableWidget(numberBox(2, minutes, v -> minutes = v));
        addRenderableWidget(numberBox(3, radius, v -> radius = v));

        addRenderableWidget(CourierButton.create(
                layout.submitX, layout.buttonY, layout.submitW, WaybillCreateLayout.FIELD_H,
                Component.translatable("ui.aerocouriers.create.submit"), b -> submit()));
        addRenderableWidget(CourierButton.create(
                layout.cancelX, layout.buttonY, layout.cancelW, WaybillCreateLayout.FIELD_H,
                Component.translatable("gui.cancel"), b -> onClose()));
    }

    private void addStationCycle() {
        List<BoardSnapshotPayload.StationHint> stations = preview.stations();
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("ui.aerocouriers.create.no_station"));
        stations.forEach(s -> labels.add(Component.literal(s.name())));
        addRenderableWidget(CourierButton.cycle(
                layout.stationX, layout.stationY, Math.max(64, layout.stationW), WaybillCreateLayout.FIELD_H,
                Component.translatable("ui.aerocouriers.create.station"), labels, index -> {
                    if (index <= 0 || index > stations.size()) {
                        return;
                    }
                    BoardSnapshotPayload.StationHint station = stations.get(index - 1);
                    toX = Integer.toString(station.pos().getX());
                    toY = Integer.toString(station.pos().getY());
                    toZ = Integer.toString(station.pos().getZ());
                    rebuildWidgets();
                }));
    }

    private void addWaypointCycle(List<XaeroPickedWaypoint> waypoints) {
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("ui.aerocouriers.create.no_waypoint"));
        waypoints.forEach(w -> labels.add(Component.literal(w.name())));
        addRenderableWidget(CourierButton.cycle(
                layout.waypointX, layout.stationY, Math.max(64, layout.waypointW), WaybillCreateLayout.FIELD_H,
                Component.translatable("ui.aerocouriers.create.waypoint"), labels, index -> {
                    if (index <= 0 || index > waypoints.size()) {
                        return;
                    }
                    applyPicked(waypoints.get(index - 1));
                    rebuildWidgets();
                }));
    }

    private PaperEditBox coordBox(int column, String value, java.util.function.Consumer<String> sink) {
        PaperEditBox box = styledBox(layout.coordX[column], layout.coordFieldY, layout.coordW[column], coordLabel(column));
        box.setMaxLength(9);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
    }

    private PaperEditBox numberBox(int column, String value, java.util.function.Consumer<String> sink) {
        PaperEditBox box = styledBox(layout.moneyX[column], layout.moneyFieldY, layout.moneyW[column], moneyLabel(column));
        box.setMaxLength(12);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,12}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
    }

    private PaperEditBox styledBox(int x, int y, int w, Component label) {
        int h = WaybillCreateLayout.FIELD_H;
        PaperEditBox box = new PaperEditBox(font, x + 4, y + 2, Math.max(8, w - 8), h - 3, label) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                WaybillCreateGui.drawPaperField(graphics, x, y, w, h, isFocused());
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            protected boolean clicked(double mouseX, double mouseY) {
                return visible && active && mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                return visible && mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
            }
        };
        return box;
    }

    private static Component coordLabel(int column) {
        return switch (column) {
            case 0 -> Component.translatable("ui.aerocouriers.create.coord.x");
            case 1 -> Component.translatable("ui.aerocouriers.create.coord.y");
            default -> Component.translatable("ui.aerocouriers.create.coord.z");
        };
    }

    private static Component moneyLabel(int column) {
        return switch (column) {
            case 0 -> Component.translatable("ui.aerocouriers.create.reward");
            case 1 -> Component.translatable("ui.aerocouriers.create.collateral");
            case 2 -> Component.translatable("ui.aerocouriers.create.minutes");
            default -> Component.translatable("ui.aerocouriers.create.radius");
        };
    }

    private void submit() {
        Optional<BlockPos> to = parsePos(toX, toY, toZ);
        if (to.isEmpty()) {
            notice = Component.translatable("ui.aerocouriers.create.bad_coords");
            return;
        }
        PacketDistributor.sendToServer(new CreateContractPayload(
                preview.origin(), to.get(),
                parseLong(reward, CreateContractPayload.DEFAULT_REWARD),
                parseLong(collateral, CreateContractPayload.DEFAULT_COLLATERAL),
                (int) parseLong(minutes, Pricing.MIN_DEADLINE_MINUTES),
                (int) parseLong(radius, CreateContractPayload.DEFAULT_RADIUS)));
    }

    private static Optional<BlockPos> parsePos(String x, String y, String z) {
        try {
            if (x.isEmpty() || y.isEmpty() || z.isEmpty() || x.equals("-") || y.equals("-") || z.equals("-")) {
                return Optional.empty();
            }
            return Optional.of(new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        WaybillCreateGui.drawWoodenFrame(graphics, layout.left, layout.top, layout.panelW, layout.panelH);
        WaybillCreateGui.drawPaperSheet(graphics, layout.paperLeft, layout.paperTop, layout.paperW, layout.paperH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        super.render(graphics, mouseX, mouseY, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        WaybillCreateGui.drawClip(graphics, layout.clipX, layout.clipY);
        int textX = layout.paperLeft + WaybillCreateLayout.PAD;
        int titleW = Math.max(32, layout.balanceX - textX - 8);
        drawClipped(graphics, title, textX, layout.headerY, titleW, WaybillCreateGui.TEXT, mouseX, mouseY);
        drawClipped(graphics, Component.translatable("ui.aerocouriers.balance", preview.balance()),
                layout.balanceX, layout.headerY,
                layout.paperLeft + layout.paperW - WaybillCreateLayout.PAD - layout.balanceX,
                WaybillCreateGui.TEXT_MUTED, mouseX, mouseY);
        Component originText = Component.translatable("ui.aerocouriers.stamp.origin", preview.origin().toShortString());
        Component weightText = Component.translatable("ui.aerocouriers.stamp.weight",
                String.format(java.util.Locale.ROOT, "%.1f", preview.weight()));
        if (layout.originY == layout.weightY) {
            int originW = layout.innerW * 62 / 100;
            drawClipped(graphics, originText, textX, layout.originY,
                    originW, WaybillCreateGui.TEXT, mouseX, mouseY);
            drawClipped(graphics, weightText, textX + originW + WaybillCreateLayout.GAP,
                    layout.weightY, layout.innerW - originW - WaybillCreateLayout.GAP, WaybillCreateGui.TEXT_MUTED, mouseX, mouseY);
        } else {
            drawClipped(graphics, originText, textX, layout.originY,
                    layout.innerW, WaybillCreateGui.TEXT, mouseX, mouseY);
            drawClipped(graphics, weightText, textX, layout.weightY,
                    layout.innerW, WaybillCreateGui.TEXT_MUTED, mouseX, mouseY);
        }

        if (layout.cargoRows > 0) {
            int cargoH = layout.cargoRows * WaybillCreateLayout.SLOT + 4;
            WaybillCreateGui.drawPaperField(graphics, layout.cargoX - 2, layout.cargoY - 2,
                    Math.min(layout.innerW, 9 * WaybillCreateLayout.SLOT + 4), cargoH, false);
        }
        renderCargo(graphics, mouseX, mouseY);

        if (layout.destTitleY + layout.lineH <= layout.coordLabelY) {
            drawClipped(graphics, Component.translatable("ui.aerocouriers.create.to"),
                    textX, layout.destTitleY, layout.innerW, WaybillCreateGui.TEXT, mouseX, mouseY);
        }
        for (int i = 0; i < 3; i++) {
            drawClipped(graphics, coordLabel(i), layout.coordX[i], layout.coordLabelY, layout.coordW[i], WaybillCreateGui.TEXT_MUTED,
                    mouseX, mouseY);
        }
        for (int i = 0; i < 4; i++) {
            drawClipped(graphics, moneyLabel(i), layout.moneyX[i], layout.moneyLabelY, layout.moneyW[i], WaybillCreateGui.TEXT_MUTED,
                    mouseX, mouseY);
        }

        Optional<BlockPos> to = parsePos(toX, toY, toZ);
        long rewardValue = parseLong(reward, CreateContractPayload.DEFAULT_REWARD);
        long collateralValue = parseLong(collateral, CreateContractPayload.DEFAULT_COLLATERAL);
        long fee = Pricing.fee(rewardValue);
        int infoY = layout.infoY;
        int infoBottom = layout.infoY + layout.infoH;
        if (to.isPresent()) {
            long min = Pricing.minReward(Math.sqrt(preview.origin().distSqr(to.get())));
            infoY = drawWrappedLimited(graphics, Component.translatable("ui.aerocouriers.create.min_reward", min),
                    textX, infoY, layout.infoW, infoBottom, WaybillCreateGui.TEXT_OK, mouseX, mouseY);
        }
        infoY = drawWrappedLimited(graphics, Component.translatable("ui.aerocouriers.stamp.cost",
                        rewardValue, fee, collateralValue),
                textX, infoY, layout.infoW, infoBottom, WaybillCreateGui.TEXT_ACCENT, mouseX, mouseY);
        drawWrappedLimited(graphics, Component.translatable("contract.aerocouriers.risk", collateralValue, rewardValue),
                textX, infoY, layout.infoW, infoBottom, WaybillCreateGui.TEXT_WARN, mouseX, mouseY);
        if (notice != null) {
            drawWrappedLimited(graphics, notice, layout.noticeX, layout.buttonY + 2, layout.noticeW,
                    layout.buttonY + WaybillCreateLayout.FIELD_H, WaybillCreateGui.TEXT_WARN, mouseX, mouseY);
        }
        if (hoverTip == null) {
            getChildAt(mouseX, mouseY).ifPresent(widget -> {
                if (widget instanceof CourierButton button && button.hint() != null) {
                    hoverTip = button.hint();
                }
            });
        }
        if (hoverTip != null) {
            CreateGui.drawSpeechTooltip(graphics, font, hoverTip, mouseX, mouseY, 240);
        }
    }

    private void renderCargo(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ItemStack> cargo = preview.cargo();
        if (cargo.isEmpty()) {
            drawClipped(graphics, Component.translatable("contract.aerocouriers.error.empty_cargo"),
                    layout.cargoX, layout.cargoY, layout.innerW, WaybillCreateGui.TEXT_BAD, mouseX, mouseY);
            return;
        }
        int shown = Math.min(cargo.size(), layout.cargoRows * 9);
        for (int i = 0; i < shown; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = layout.cargoX + col * WaybillCreateLayout.SLOT;
            int y = layout.cargoY + row * WaybillCreateLayout.SLOT;
            WaybillCreateGui.drawPaperSlot(graphics, x - 1, y - 1);
            graphics.renderItem(cargo.get(i), x, y);
            graphics.renderItemDecorations(font, cargo.get(i), x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(font, cargo.get(i), mouseX, mouseY);
            }
        }
    }

    private void drawClipped(GuiGraphics graphics, Component text, int x, int y, int width, int color,
                             int mouseX, int mouseY) {
        if (width <= 0) {
            return;
        }
        String raw = text.getString();
        String shown = font.plainSubstrByWidth(raw, width);
        if (!shown.equals(raw)) {
            shown = font.plainSubstrByWidth(raw, Math.max(8, width - font.width("…"))) + "…";
        }
        graphics.drawString(font, shown, x, y, color, false);
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + layout.lineH && !shown.equals(raw)) {
            hoverTip = text;
        }
    }

    private int drawWrappedLimited(GuiGraphics graphics, Component text, int x, int y, int width, int bottom,
                                   int color, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int drawY = y;
        int shown = 0;
        for (FormattedCharSequence line : lines) {
            if (drawY + layout.lineH > bottom) {
                break;
            }
            graphics.drawString(font, line, x, drawY, color, false);
            drawY += layout.lineH;
            shown++;
        }
        if (shown < lines.size() && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < bottom) {
            hoverTip = text;
        }
        return drawY + 2;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
