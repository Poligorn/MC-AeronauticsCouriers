package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.OpenStampPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing order creation: destination, money, deadline. Cargo and pickup come from the stamped container.
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

    public StampCreateScreen(OpenStampPayload preview) {
        super(Component.translatable("ui.aerocouriers.stamp.title"));
        this.preview = preview;
    }

    @Override
    protected void init() {
        super.init();
        int left = left();
        int top = top();
        int y = top + 44 + cargoRows() * 18 + 28;
        addRenderableWidget(coordBox(left + 8, y, 56, toX, v -> toX = v));
        addRenderableWidget(coordBox(left + 68, y, 48, toY, v -> toY = v));
        addRenderableWidget(coordBox(left + 120, y, 56, toZ, v -> toZ = v));
        addStationCycle(left + 184, y, panelW() - 192);

        y += 36;
        addRenderableWidget(numberBox(left + 8, y, 64, reward, v -> reward = v));
        addRenderableWidget(numberBox(left + 96, y, 64, collateral, v -> collateral = v));
        addRenderableWidget(numberBox(left + 184, y, 56, minutes, v -> minutes = v));
        addRenderableWidget(numberBox(left + 264, y, 48, radius, v -> radius = v));

        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.create.submit"), b -> submit())
                .bounds(left + panelW() - 126, top + panelH() - 28, 118, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 8, top + panelH() - 28, 80, 20)
                .build());
    }

    private void addStationCycle(int x, int y, int w) {
        List<BoardSnapshotPayload.StationHint> stations = preview.stations();
        List<String> values = new ArrayList<>();
        values.add("");
        stations.forEach(s -> values.add(s.name() + " @" + s.pos().toShortString()));
        addRenderableWidget(CycleButton.<String>builder(value -> value.isEmpty()
                        ? Component.translatable("ui.aerocouriers.create.no_station")
                        : Component.literal(value.contains(" @") ? value.substring(0, value.indexOf(" @")) : value))
                .withValues(values)
                .withInitialValue("")
                .create(x, y, Math.max(80, w), 20, Component.translatable("ui.aerocouriers.create.station"),
                        (button, value) -> {
                            if (value.isEmpty()) {
                                return;
                            }
                            stations.stream()
                                    .filter(s -> (s.name() + " @" + s.pos().toShortString()).equals(value))
                                    .findFirst()
                                    .ifPresent(s -> {
                                        toX = Integer.toString(s.pos().getX());
                                        toY = Integer.toString(s.pos().getY());
                                        toZ = Integer.toString(s.pos().getZ());
                                        rebuildWidgets();
                                    });
                        }));
    }

    private EditBox coordBox(int x, int y, int w, String value, java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(9);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
    }

    private EditBox numberBox(int x, int y, int w, String value, java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(12);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,12}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
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
        int left = left();
        int top = top();
        graphics.fill(left, top, left + panelW(), top + panelH(), 0xC0101010);
        graphics.fill(left + 1, top + 1, left + panelW() - 1, top + panelH() - 1, 0xFF202020);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.drawString(font, title, left + 8, top + 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.balance", preview.balance()),
                left + panelW() - 140, top + 8, 0xFFE082, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.stamp.origin",
                        preview.origin().toShortString()),
                left + 8, top + 22, 0xCCCCCC, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.stamp.weight",
                        String.format(java.util.Locale.ROOT, "%.1f", preview.weight())),
                left + 8, top + 34, 0xBBBBBB, false);

        int gridY = top + 48;
        List<ItemStack> cargo = preview.cargo();
        if (cargo.isEmpty()) {
            graphics.drawString(font, Component.translatable("contract.aerocouriers.error.empty_cargo"),
                    left + 8, gridY, 0xFF8888, false);
        } else {
            for (int i = 0; i < cargo.size(); i++) {
                int col = i % 9;
                int row = i / 9;
                int x = left + 8 + col * 18;
                int y = gridY + row * 18;
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF303030);
                graphics.renderItem(cargo.get(i), x, y);
                graphics.renderItemDecorations(font, cargo.get(i), x, y);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    graphics.renderTooltip(font, cargo.get(i), mouseX, mouseY);
                }
            }
        }

        int destY = top + 44 + cargoRows() * 18 + 16;
        graphics.drawString(font, Component.translatable("ui.aerocouriers.create.to"),
                left + 8, destY, 0xCCCCCC, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.create.reward"),
                left + 8, destY + 28, 0xCCCCCC, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.create.collateral"),
                left + 96, destY + 28, 0xCCCCCC, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.create.minutes"),
                left + 184, destY + 28, 0xCCCCCC, false);
        graphics.drawString(font, Component.translatable("ui.aerocouriers.create.radius"),
                left + 264, destY + 28, 0xCCCCCC, false);

        Optional<BlockPos> to = parsePos(toX, toY, toZ);
        long rewardValue = parseLong(reward, CreateContractPayload.DEFAULT_REWARD);
        long collateralValue = parseLong(collateral, CreateContractPayload.DEFAULT_COLLATERAL);
        long fee = Pricing.fee(rewardValue);
        int infoY = destY + 80;
        if (to.isPresent()) {
            long min = Pricing.minReward(Math.sqrt(preview.origin().distSqr(to.get())));
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.create.min_reward", min),
                    left + 8, infoY, panelW() - 16, 0x8BC34A);
            infoY += 12;
        }
        drawWrapped(graphics, Component.translatable("ui.aerocouriers.stamp.cost", rewardValue, fee, collateralValue),
                left + 8, infoY, panelW() - 16, 0xFFE082);
        infoY += 20;
        drawWrapped(graphics, Component.translatable("contract.aerocouriers.risk", collateralValue, rewardValue),
                left + 8, infoY, panelW() - 16, 0xFFCC66);
        if (notice != null) {
            drawWrapped(graphics, notice, left + 96, top + panelH() - 24, panelW() - 230, 0xFFCC66);
        }
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int drawY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, drawY, color, false);
            drawY += 9;
        }
    }

    private int cargoRows() {
        return Math.max(1, (preview.cargo().size() + 8) / 9);
    }

    private int panelW() {
        return Mth.clamp(width - 40, 360, 520);
    }

    private int panelH() {
        return Mth.clamp(height - 32, 280, 400);
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return (height - panelH()) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
