package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.TabletButton;
import dev.poligorn.aerocouriers.client.TabletGui;
import dev.poligorn.aerocouriers.client.XaeroOrderBridge;
import net.neoforged.fml.ModList;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Order details overlay on the board tab (mockup 3_details). */
public final class BoardOrderDetailsOverlay {
    private static final int PAD = 8;
    private static final int SLOT = 18;
    private static final int COLS = 9;

    private final Host host;
    private final BoardSnapshotPayload.BoardEntry entry;
    private final BoardSnapshotPayload.BoardProfile profile;
    private final long balance;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private @Nullable Integer hoverCustomerX;

    public interface Host {
        Font font();

        void addOverlayWidget(AbstractWidget widget);

        void removeOverlayWidgets(List<AbstractWidget> widgets);

        void closeOverlay();

        void openAccept(BoardSnapshotPayload.BoardEntry entry);
    }

    public BoardOrderDetailsOverlay(
            Host host,
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            long balance
    ) {
        this.host = host;
        this.entry = entry;
        this.profile = profile;
        this.balance = balance;
    }

    public List<AbstractWidget> widgets() {
        return widgets;
    }

    public void init(int cavityX, int cavityY, int cavityW, int cavityH) {
        widgets.clear();
        panelW = Math.min(420, cavityW - 12);
        panelH = Math.min(250, cavityH - 12);
        panelX = cavityX + (cavityW - panelW) / 2;
        panelY = cavityY + (cavityH - panelH) / 2;

        int closeW = 14;
        TabletButton close = TabletButton.create(panelX + panelW - PAD - closeW, panelY + PAD, closeW, closeW,
                Component.literal("×"), b -> host.closeOverlay());
        widgets.add(close);
        host.addOverlayWidget(close);

        int btnY = panelY + panelH - PAD - 18;
        int btnH = 18;
        int third = (panelW - PAD * 2) / 3;

        if (ModList.get().isLoaded("xaerominimap")) {
            TabletButton map = TabletButton.create(panelX + PAD, btnY, third - 4, btnH,
                    Component.translatable("ui.aerocouriers.details.map"), b -> showOnMap());
            widgets.add(map);
            host.addOverlayWidget(map);
        }

        long due = collateralDue();
        TabletButton accept = TabletButton.create(panelX + PAD + third, btnY, third + 8, btnH,
                Component.translatable("ui.aerocouriers.accept.overlay.action", due),
                b -> host.openAccept(entry));
        widgets.add(accept);
        host.addOverlayWidget(accept);

        TabletButton back = TabletButton.create(panelX + panelW - PAD - third + 4, btnY, third - 4, btnH,
                Component.translatable("ui.aerocouriers.details.back"), b -> host.closeOverlay());
        widgets.add(back);
        host.addOverlayWidget(back);
    }

    public void dispose() {
        host.removeOverlayWidgets(widgets);
        widgets.clear();
    }

    public boolean clickCustomer(int mouseX, int mouseY) {
        if (entry.villageCustomer() || hoverCustomerX == null) {
            return false;
        }
        if (mouseX >= hoverCustomerX && mouseX < hoverCustomerX + 80 && mouseY >= panelY + 72 && mouseY < panelY + 82) {
            entry.customerId().ifPresent(ClientBoard::requestPlayerProfile);
            return true;
        }
        return false;
    }

    public void render(GuiGraphics graphics, int cavityX, int cavityY, int cavityW, int cavityH, int mouseX, int mouseY) {
        graphics.fill(cavityX, cavityY, cavityX + cavityW, cavityY + cavityH, 0xB0000000);
        TabletGui.drawListPanel(graphics, panelX, panelY, panelW, panelH);

        Font font = host.font();
        int x = panelX + PAD;
        int y = panelY + PAD;
        int innerW = panelW - PAD * 2;
        int leftW = panelW / 2 - PAD;

        String route = BoardEntryPickup.routeOriginLabel(entry).getString() + " → " + entry.destinationLabel();
        drawClipped(graphics, font, route, x, y, innerW - 40, TabletGui.TEXT_HEADER);
        String id = "#" + entry.shortId();
        graphics.drawString(font, id, panelX + panelW - PAD - font.width(id), y + 2, TabletGui.TEXT_MUTED, false);
        y += font.lineHeight + 6;

        int cargoTop = y;
        graphics.drawString(font, Component.translatable("ui.aerocouriers.row.cargo_heading"), x, y, TabletGui.TEXT, false);
        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        drawSizeStrip(graphics, font, x + leftW - 52, y, size);
        y += font.lineHeight + 2;
        Component container = Component.translatable("ui.aerocouriers.details.container", size.name(), size.slots());
        graphics.drawString(font, container, x, y, TabletGui.TEXT_MUTED, false);
        y += font.lineHeight + 2;

        List<ItemStack> stacks = entry.cargoStacks();
        int gridTop = y;
        for (int i = 0; i < Math.min(stacks.size(), size.slots()); i++) {
            int col = i % COLS;
            int row = i / COLS;
            if (row >= 3) {
                break;
            }
            int sx = x + col * SLOT;
            int sy = gridTop + row * SLOT;
            graphics.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF1A0A04);
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(font, stack, sx + 1, sy + 1);
            }
        }
        int used = (int) stacks.stream().filter(s -> !s.isEmpty()).count();
        y = gridTop + Math.min(3, (Math.min(stacks.size(), size.slots()) + COLS - 1) / COLS) * SLOT + 2;
        graphics.drawString(font, Component.translatable("ui.aerocouriers.details.slots_used", used, size.slots()),
                x, y, TabletGui.TEXT_MUTED, false);
        y += font.lineHeight + 2;
        y = drawWeightBar(graphics, font, x, y, leftW - 8, entry.weight(), size);

        int rx = panelX + panelW / 2 + 2;
        int ry = cargoTop;
        int rw = panelW / 2 - PAD - 4;
        graphics.vLine(rx - 2, panelY + PAD, panelY + panelH - PAD - 24, 0xFFB36833);
        ry = drawEndpoint(graphics, font, rx, ry, rw,
                Component.translatable("ui.aerocouriers.details.from"),
                endpointLabel(entry.originLabel(), entry.villageCustomer()),
                coordLine(entry.originPos(), entry.originDimension(), entry.originRevealed()));
        ry = drawEndpoint(graphics, font, rx, ry + 2, rw,
                Component.translatable("ui.aerocouriers.details.to"),
                Component.literal(entry.destinationLabel()),
                coordLine(entry.destinationPos(), entry.destinationDimension(), true));
        ry += 2;
        long eff = BoardRewardEfficiency.perThousandBlocks(entry);
        drawLine(graphics, font, rx, ry, rw, Component.translatable("ui.aerocouriers.details.distance"),
                Component.translatable("ui.aerocouriers.details.distance_eff",
                        entry.distance(), eff));
        ry += font.lineHeight + 3;
        drawLine(graphics, font, rx, ry, rw, Component.translatable("ui.aerocouriers.accept.confirm.reward.label"),
                Component.literal("+" + entry.reward()), TabletGui.TEXT_REWARD);
        ry += font.lineHeight + 2;
        long due = collateralDue();
        drawLine(graphics, font, rx, ry, rw, Component.translatable("ui.aerocouriers.details.deposit"),
                Component.literal(Long.toString(due)), TabletGui.TEXT_RISK);
        ry += font.lineHeight + 2;
        drawLine(graphics, font, rx, ry, rw, Component.translatable("ui.aerocouriers.details.deadline"),
                Component.translatable("ui.aerocouriers.row.time_compact", entry.deadlineMinutes()));
        ry += font.lineHeight + 2;
        Component rankNeed = Component.translatable("ui.aerocouriers.details.rank_need", entry.requiredRank().displayName());
        Component rankYou = RankRules.satisfies(profile.rank(), entry.requiredRank())
                ? Component.translatable("ui.aerocouriers.details.rank_ok", profile.rank().displayName())
                : Component.empty();
        graphics.drawString(font, rankNeed.copy().append(" ").append(rankYou), rx, ry, TabletGui.TEXT_ACCENT, false);
        ry += font.lineHeight + 2;
        hoverCustomerX = null;
        if (entry.villageCustomer()) {
            drawLine(graphics, font, rx, ry, rw, Component.translatable("ui.aerocouriers.details.customer"),
                    Component.literal(entry.customerName()));
        } else {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.details.customer"), rx, ry,
                    TabletGui.TEXT_MUTED, false);
            int nx = rx + font.width(Component.translatable("ui.aerocouriers.details.customer").getString()) + 4;
            hoverCustomerX = nx;
            boolean hover = mouseX >= nx && mouseX < nx + font.width(entry.customerName()) + 4
                    && mouseY >= ry && mouseY < ry + 9;
            graphics.drawString(font, entry.customerName(), nx, ry, hover ? TabletGui.TEXT_INFO : TabletGui.TEXT_INFO,
                    false);
        }
        ry += font.lineHeight + 2;
        Component type = entry.villageCustomer()
                ? Component.translatable("ui.aerocouriers.details.type.village")
                : Component.translatable("ui.aerocouriers.details.type.player");
        drawLine(graphics, font, rx, ry, rw, Component.translatable("ui.aerocouriers.details.type"), type);
    }

    private void showOnMap() {
        XaeroOrderBridge.onPreviewDestination(
                entry.id(), entry.destinationDimension(), entry.destinationPos(), entry.shortId());
        XaeroOrderBridge.openWorldMapAt(entry.destinationPos());
    }

    private long collateralDue() {
        return TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                profile.villageDelivered(), profile.traineeFreeContracts());
    }

    private static Component endpointLabel(String label, boolean village) {
        if (label.isEmpty()) {
            return Component.translatable("ui.aerocouriers.row.pickup_hidden");
        }
        return village
                ? Component.translatable("ui.aerocouriers.details.station_named", label)
                : Component.literal(label);
    }

    private static Component coordLine(BlockPos pos, ResourceKey<Level> dim, boolean revealed) {
        if (!revealed || pos.equals(BlockPos.ZERO)) {
            return Component.empty();
        }
        return Component.translatable("ui.aerocouriers.details.coords_xz", pos.getX(), pos.getZ());
    }

    private static void drawSizeStrip(GuiGraphics g, Font font, int x, int y, CargoSize selected) {
        for (CargoSize size : CargoSize.values()) {
            String label = size.name();
            int w = font.width(label) + 6;
            boolean on = size == selected;
            if (on) {
                g.renderOutline(x, y, w, 10, TabletGui.TEXT_REWARD);
            }
            g.drawString(font, label, x + 3, y + 1, on ? TabletGui.TEXT_REWARD : TabletGui.TEXT_DISABLED, false);
            x += w + 2;
        }
    }

    private static int drawWeightBar(GuiGraphics g, Font font, int x, int y, int w, String weightField, CargoSize size) {
        g.drawString(font, Component.translatable("ui.aerocouriers.details.weight"), x, y, TabletGui.TEXT_MUTED, false);
        g.drawString(font, weightField.replace('.', ' ').replace(" / ", "/"), x + w - font.width(weightField), y,
                TabletGui.TEXT, false);
        y += font.lineHeight + 2;
        double current = parseWeight(weightField);
        float ratio = size.maxWeight() > 0 ? (float) (current / size.maxWeight()) : 0;
        int barW = w;
        g.fill(x, y, x + barW, y + 4, 0xFF1A0A04);
        g.fill(x, y, x + Math.round(barW * Math.min(1, ratio)), y + 4, TabletGui.TEXT_REWARD);
        y += 6;
        g.drawString(font, Component.translatable("ui.aerocouriers.details.weight_hint"), x, y, TabletGui.TEXT_DISABLED,
                false);
        return y + font.lineHeight;
    }

    private static double parseWeight(String field) {
        try {
            int slash = field.indexOf('/');
            String head = slash > 0 ? field.substring(0, slash).trim() : field.trim();
            return Double.parseDouble(head);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int drawEndpoint(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value,
                                    Component coords) {
        g.drawString(font, label, x, y, TabletGui.TEXT_MUTED, false);
        y += font.lineHeight;
        drawWrapped(g, font, value, x, y, w, TabletGui.TEXT);
        y += font.lineHeight;
        if (!coords.getString().isEmpty()) {
            g.drawString(font, coords, x, y, TabletGui.TEXT_DISABLED, false);
            y += font.lineHeight;
        }
        return y;
    }

    private static void drawLine(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value) {
        drawLine(g, font, x, y, w, label, value, TabletGui.TEXT);
    }

    private static void drawLine(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value,
                                 int valueColor) {
        g.drawString(font, label, x, y, TabletGui.TEXT_MUTED, false);
        String vs = value.getString();
        g.drawString(font, vs, x + w - font.width(vs), y, valueColor, false);
    }

    private static void drawWrapped(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    private static void drawClipped(GuiGraphics g, Font font, String raw, int x, int y, int width, int color) {
        g.drawString(font, BoardRowRenderer.clip(font, raw, width), x, y, color, false);
    }
}
