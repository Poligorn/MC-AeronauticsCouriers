package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletButton;
import dev.poligorn.aerocouriers.client.TabletGui;
import dev.poligorn.aerocouriers.client.XaeroOrderBridge;
import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** In-tablet accept confirmation overlay (mockup 2_accept). */
public final class BoardAcceptOverlay {
    private static final int PAD = 10;
    private static final int RISK_PAD = 6;

    private final Host host;
    private final BoardSnapshotPayload.BoardEntry entry;
    private final BoardSnapshotPayload.BoardProfile profile;
    private final int activeHauls;
    private final long balance;

    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    public interface Host {
        Font font();

        void addOverlayWidget(AbstractWidget widget);

        void removeOverlayWidgets(List<AbstractWidget> widgets);

        void closeOverlay();

        void onAccepted();
    }

    public BoardAcceptOverlay(
            Host host,
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            int activeHauls,
            long balance
    ) {
        this.host = host;
        this.entry = entry;
        this.profile = profile;
        this.activeHauls = activeHauls;
        this.balance = balance;
    }

    public List<AbstractWidget> widgets() {
        return widgets;
    }

    public void init(int cavityX, int cavityY, int cavityW, int cavityH) {
        widgets.clear();
        relayout(cavityW, cavityH);
        boxX = cavityX + (cavityW - boxW) / 2;
        boxY = cavityY + (cavityH - boxH) / 2;

        int btnW = Math.min(200, boxW - PAD * 2);
        int btnX = boxX + (boxW - btnW) / 2;
        int btnY = boxY + boxH - PAD - 20;
        TabletButton accept = TabletButton.create(btnX, btnY, btnW, 20, acceptLabel(), b -> confirm());
        Optional<AcceptEligibility.Denial> blocked = acceptBlocked();
        long due = collateralDue();
        boolean broke = balance < due;
        accept.active = blocked.isEmpty() && !broke && host.font() != null;
        widgets.add(accept);
        host.addOverlayWidget(accept);

        int cancelW = 72;
        TabletButton cancel = TabletButton.create(boxX + boxW - PAD - cancelW, boxY + PAD, cancelW, 14,
                Component.translatable("gui.cancel"), b -> host.closeOverlay());
        widgets.add(cancel);
        host.addOverlayWidget(cancel);
    }

    public void dispose() {
        host.removeOverlayWidgets(widgets);
        widgets.clear();
    }

    public void render(GuiGraphics graphics, int cavityX, int cavityY, int cavityW, int cavityH, int mouseX, int mouseY) {
        relayout(cavityW, cavityH);
        boxX = cavityX + (cavityW - boxW) / 2;
        boxY = cavityY + (cavityH - boxH) / 2;
        graphics.fill(cavityX, cavityY, cavityX + cavityW, cavityY + cavityH, 0xB0000000);
        TabletGui.drawListPanel(graphics, boxX, boxY, boxW, boxH);

        Font font = host.font();
        int x = boxX + PAD;
        int y = boxY + PAD;
        int innerW = boxW - PAD * 2;

        String route = BoardEntryPickup.routeOriginLabel(entry).getString() + " → " + entry.destinationLabel();
        drawClipped(graphics, font, route, x, y, innerW, TabletGui.TEXT_HEADER);
        y += font.lineHeight + 2;
        Component sub = Component.translatable("ui.aerocouriers.accept.overlay.subtitle",
                entry.villageCustomer()
                        ? Component.translatable("ui.aerocouriers.details.origin.station")
                        : Component.translatable("ui.aerocouriers.details.type.player"),
                entry.shortId(),
                entry.requiredRank().displayName());
        drawWrapped(graphics, font, sub, x, y, innerW, TabletGui.TEXT_MUTED);
        y += font.lineHeight + 6;

        y = drawPair(graphics, font, Component.translatable("ui.aerocouriers.accept.confirm.reward.label").getString(),
                "+" + entry.reward(), x, y, innerW, TabletGui.TEXT, TabletGui.TEXT_REWARD);
        long due = collateralDue();
        y = drawPair(graphics, font,
                Component.translatable("ui.aerocouriers.accept.overlay.collateral_now").getString(),
                "-" + due, x, y, innerW, TabletGui.TEXT, TabletGui.TEXT_RISK);
        y = drawPair(graphics, font, Component.translatable("ui.aerocouriers.details.deadline").getString(),
                Component.translatable("ui.aerocouriers.row.time_compact", entry.deadlineMinutes()).getString(),
                x, y, innerW, TabletGui.TEXT_MUTED, TabletGui.TEXT);
        y = drawPair(graphics, font, Component.translatable("ui.aerocouriers.details.distance").getString(),
                Component.translatable("ui.aerocouriers.row.distance_blocks", entry.distance()).getString(),
                x, y, innerW, TabletGui.TEXT_MUTED, TabletGui.TEXT);
        y += 4;

        int riskTop = y;
        Component risk = riskLine();
        int riskH = wrappedHeight(font, risk, innerW - RISK_PAD * 2) + RISK_PAD * 2 + 2;
        graphics.renderOutline(x, riskTop, innerW, riskH, TabletGui.TEXT_RISK);
        drawWrapped(graphics, font, risk, x + RISK_PAD, riskTop + RISK_PAD, innerW - RISK_PAD * 2,
                TabletGui.TEXT_RISK);
        y = riskTop + riskH + 6;

        long due2 = collateralDue();
        if (balance < due2) {
            long missing = due2 - balance;
            Component err = Component.translatable("ui.aerocouriers.accept.overlay.shortfall", missing, balance, due2);
            drawWrapped(graphics, font, err, x, boxY + boxH - PAD - 28, innerW, TabletGui.TEXT_RISK);
        } else {
            Component footer = Component.translatable("ui.aerocouriers.accept.overlay.balance_remain",
                    balance, Math.max(0, balance - due2));
            drawWrapped(graphics, font, footer, x, boxY + boxH - PAD - 28, innerW, TabletGui.TEXT_REWARD);
        }

        for (AbstractWidget widget : widgets) {
            if (widget instanceof TabletButton button && button.getMessage().equals(acceptLabel())) {
                button.setY(boxY + boxH - PAD - 20);
                button.setX(boxX + (boxW - button.getWidth()) / 2);
            }
        }
    }

    private Component acceptLabel() {
        return Component.translatable("ui.aerocouriers.accept.overlay.action", collateralDue());
    }

    private void confirm() {
        if (acceptBlocked().isPresent() || balance < collateralDue()) {
            return;
        }
        PacketDistributor.sendToServer(new ContractIntentPayload(ContractIntentPayload.Kind.ACCEPT, entry.id()));
        host.onAccepted();
        host.closeOverlay();
    }

    private Optional<AcceptEligibility.Denial> acceptBlocked() {
        return BoardAcceptUi.denial(entry, profile, activeHauls);
    }

    private long collateralDue() {
        return TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                profile.villageDelivered(), profile.traineeFreeContracts());
    }

    private Component riskLine() {
        long due = collateralDue();
        return entry.villageCustomer()
                ? Component.translatable("contract.aerocouriers.risk.npc", due, entry.reward())
                : Component.translatable("contract.aerocouriers.risk", due, entry.reward());
    }

    private void relayout(int cavityW, int cavityH) {
        boxW = Math.min(320, cavityW - 16);
        boxH = Math.min(220, cavityH - 16);
    }

    private static int drawPair(GuiGraphics g, Font font, String label, String value, int x, int y, int w,
                                int labelColor, int valueColor) {
        g.drawString(font, label, x, y, labelColor, false);
        int vx = x + w - font.width(value);
        g.drawString(font, value, vx, y, valueColor, false);
        return y + font.lineHeight + 3;
    }

    private static int wrappedHeight(Font font, Component text, int maxWidth) {
        return font.split(text, maxWidth).size() * font.lineHeight;
    }

    private static void drawWrapped(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    private static void drawClipped(GuiGraphics g, Font font, String raw, int x, int y, int width, int color) {
        String shown = BoardRowRenderer.clip(font, raw, width);
        g.drawString(font, shown, x, y, color, false);
    }
}
