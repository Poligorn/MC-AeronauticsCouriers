package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.OpenStampPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing order creation: destination, money, deadline. Cargo and pickup come from the stamped container.
 * Layout is computed from the current scaled window and font (see {@link StampCreateLayout}).
 * Chrome is assembled from Create 6.0.10 GUI textures (see {@link CreateGui}).
 */
public final class StampCreateScreen extends Screen {
    private final OpenStampPayload preview;
    private String toX = "";
    private String toY = "";
    private String toZ = "";
    private String reward = Long.toString(CreateContractPayload.DEFAULT_REWARD);
    private String collateral = Long.toString(CreateContractPayload.DEFAULT_COLLATERAL);
    private String minutes = Integer.toString(Pricing.MIN_DEADLINE_MINUTES);
    private String radius = Integer.toString(CreateContractPayload.DEFAULT_RADIUS);
    private @Nullable Component notice;
    private @Nullable Component hoverTip;
    private StampCreateLayout layout = StampCreateLayout.compute(400, 300, 9, 0, false, false, 0);
    private boolean xaeroMap;
    private boolean xaeroList;

    public StampCreateScreen(OpenStampPayload preview) {
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
        layout = StampCreateLayout.compute(
                width, height, font.lineHeight, preview.cargo().size(), xaeroMap, xaeroList, mapLabelW);

        addRenderableWidget(coordBox(0, toX, v -> toX = v));
        addRenderableWidget(coordBox(1, toY, v -> toY = v));
        addRenderableWidget(coordBox(2, toZ, v -> toZ = v));
        if (xaeroMap) {
            addRenderableWidget(CreateStyleButton.create(
                            layout.mapBtnX, layout.mapBtnY, layout.mapBtnW, StampCreateLayout.FIELD_H,
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

        addRenderableWidget(CreateStyleButton.create(
                        layout.submitX, layout.buttonY, layout.submitW, StampCreateLayout.FIELD_H,
                        Component.translatable("ui.aerocouriers.create.submit"), b -> submit())
                .icon(CreateGuiTextures.ICON_CONFIRM)
                .green(true));
        addRenderableWidget(CreateStyleButton.create(
                        layout.cancelX, layout.buttonY, layout.cancelW, StampCreateLayout.FIELD_H,
                        Component.translatable("gui.cancel"), b -> onClose())
                .icon(CreateGuiTextures.ICON_CLOSE));
    }

    private void addStationCycle() {
        List<BoardSnapshotPayload.StationHint> stations = preview.stations();
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("ui.aerocouriers.create.no_station"));
        stations.forEach(s -> labels.add(Component.literal(s.name())));
        addRenderableWidget(CreateStyleButton.cycle(
                layout.stationX, layout.stationY, Math.max(64, layout.stationW), StampCreateLayout.FIELD_H,
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
        addRenderableWidget(CreateStyleButton.cycle(
                layout.waypointX, layout.stationY, Math.max(64, layout.waypointW), StampCreateLayout.FIELD_H,
                Component.translatable("ui.aerocouriers.create.waypoint"), labels, index -> {
                    if (index <= 0 || index > waypoints.size()) {
                        return;
                    }
                    applyPicked(waypoints.get(index - 1));
                    rebuildWidgets();
                }));
    }

    private EditBox coordBox(int column, String value, java.util.function.Consumer<String> sink) {
        EditBox box = styledBox(layout.coordX[column], layout.coordFieldY, layout.coordW[column], coordLabel(column));
        box.setMaxLength(9);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
    }

    private EditBox numberBox(int column, String value, java.util.function.Consumer<String> sink) {
        EditBox box = styledBox(layout.moneyX[column], layout.moneyFieldY, layout.moneyW[column], moneyLabel(column));
        box.setMaxLength(12);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,12}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
    }

    private EditBox styledBox(int x, int y, int w, Component label) {
        int h = StampCreateLayout.FIELD_H;
        EditBox box = new EditBox(font, x + 4, y + 2, Math.max(8, w - 8), h - 3, label) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                CreateGui.drawField(graphics, x, y, w, h);
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
        box.setBordered(false);
        box.setTextColor(CreateGui.TEXT);
        box.setTextColorUneditable(CreateGui.TEXT_MUTED);
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
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        CreateGui.drawWindow(graphics, layout.left, layout.top, layout.panelW, layout.panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        CreateGui.drawParchmentStrip(graphics, layout.left + StampCreateLayout.PAD, layout.headerY - 2,
                layout.innerW, layout.lineH + 4);
        int titleW = Math.max(32, layout.balanceX - layout.left - StampCreateLayout.PAD - 8);
        drawClipped(graphics, title, layout.left + StampCreateLayout.PAD, layout.headerY, titleW, CreateGui.TEXT_TITLE,
                mouseX, mouseY);
        drawClipped(graphics, Component.translatable("ui.aerocouriers.balance", preview.balance()),
                layout.balanceX, layout.headerY, layout.left + layout.panelW - StampCreateLayout.PAD - layout.balanceX,
                CreateGui.TEXT_BRASS, mouseX, mouseY);
        Component originText = Component.translatable("ui.aerocouriers.stamp.origin", preview.origin().toShortString());
        Component weightText = Component.translatable("ui.aerocouriers.stamp.weight",
                String.format(java.util.Locale.ROOT, "%.1f", preview.weight()));
        if (layout.originY == layout.weightY) {
            int originW = layout.innerW * 62 / 100;
            drawClipped(graphics, originText, layout.left + StampCreateLayout.PAD, layout.originY,
                    originW, CreateGui.TEXT, mouseX, mouseY);
            drawClipped(graphics, weightText, layout.left + StampCreateLayout.PAD + originW + StampCreateLayout.GAP,
                    layout.weightY, layout.innerW - originW - StampCreateLayout.GAP, CreateGui.TEXT_MUTED, mouseX, mouseY);
        } else {
            drawClipped(graphics, originText, layout.left + StampCreateLayout.PAD, layout.originY,
                    layout.innerW, CreateGui.TEXT, mouseX, mouseY);
            drawClipped(graphics, weightText, layout.left + StampCreateLayout.PAD, layout.weightY,
                    layout.innerW, CreateGui.TEXT_MUTED, mouseX, mouseY);
        }

        renderCargo(graphics, mouseX, mouseY);

        if (layout.destTitleY + layout.lineH <= layout.coordLabelY) {
            drawClipped(graphics, Component.translatable("ui.aerocouriers.create.to"),
                    layout.left + StampCreateLayout.PAD, layout.destTitleY, layout.innerW, CreateGui.TEXT, mouseX, mouseY);
        }
        for (int i = 0; i < 3; i++) {
            drawClipped(graphics, coordLabel(i), layout.coordX[i], layout.coordLabelY, layout.coordW[i], CreateGui.TEXT_MUTED,
                    mouseX, mouseY);
        }
        for (int i = 0; i < 4; i++) {
            drawClipped(graphics, moneyLabel(i), layout.moneyX[i], layout.moneyLabelY, layout.moneyW[i], CreateGui.TEXT_MUTED,
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
                    layout.left + StampCreateLayout.PAD, infoY, layout.infoW, infoBottom, CreateGui.TEXT_OK, mouseX, mouseY);
        }
        infoY = drawWrappedLimited(graphics, Component.translatable("ui.aerocouriers.stamp.cost",
                        rewardValue, fee, collateralValue),
                layout.left + StampCreateLayout.PAD, infoY, layout.infoW, infoBottom, CreateGui.TEXT_BRASS, mouseX, mouseY);
        drawWrappedLimited(graphics, Component.translatable("contract.aerocouriers.risk", collateralValue, rewardValue),
                layout.left + StampCreateLayout.PAD, infoY, layout.infoW, infoBottom, CreateGui.TEXT_WARN, mouseX, mouseY);
        if (notice != null) {
            drawWrappedLimited(graphics, notice, layout.noticeX, layout.buttonY + 2, layout.noticeW,
                    layout.buttonY + StampCreateLayout.FIELD_H, CreateGui.TEXT_WARN, mouseX, mouseY);
        }
        if (hoverTip == null) {
            getChildAt(mouseX, mouseY).ifPresent(widget -> {
                if (widget instanceof CreateStyleButton button && button.hint() != null) {
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
                    layout.cargoX, layout.cargoY, layout.innerW, CreateGui.TEXT_BAD, mouseX, mouseY);
            return;
        }
        int shown = Math.min(cargo.size(), layout.cargoRows * 9);
        for (int i = 0; i < shown; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = layout.cargoX + col * StampCreateLayout.SLOT;
            int y = layout.cargoY + row * StampCreateLayout.SLOT;
            CreateGui.drawSlot(graphics, x - 1, y - 1);
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
