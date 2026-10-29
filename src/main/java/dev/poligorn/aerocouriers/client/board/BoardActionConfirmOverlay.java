package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.client.MoneyText;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * «Отказаться от заказа?» (courier, active haul) and «Снять заказ?» (customer, still open): the same risk layout as
 * the accept confirmation, wording mirrors {@code ContractManager#fail} / {@code #expire}.
 */
public final class BoardActionConfirmOverlay extends BoardConfirmOverlay {
    public static final int W = 304;
    private static final int HEADER_H = 14;
    private static final int LABEL_X = 10;
    private static final int VALUE_X = 86;
    private static final int RISK_TEXT_W = W - 16 - 26 - 6;
    private static final int CONFIRM_X = 10;
    private static final int CONFIRM_W = 168;
    private static final int CANCEL_X = 186;
    private static final int CANCEL_W = W - 11 - CANCEL_X + 1;
    private static final int CLOSE_X = W - 17;
    private static final int CLOSE_Y = 1;

    private final BoardOverlayHost host;
    private final UUID orderId;
    private final ContractIntentPayload.Kind action;
    private int riskY = 76;
    private int height = 170;
    private int buttonsY = 130;
    private @Nullable List<Component> hoverLines;

    public BoardActionConfirmOverlay(BoardOverlayHost host, UUID orderId, ContractIntentPayload.Kind action) {
        this.host = host;
        this.orderId = orderId;
        this.action = action;
    }

    @Override
    public UUID orderId() {
        return orderId;
    }

    @Override
    public @Nullable List<Component> hoverLines() {
        return hoverLines;
    }

    @Override
    protected int width() {
        return W;
    }

    @Override
    protected int height() {
        return height;
    }

    private boolean abandon() {
        return action == ContractIntentPayload.Kind.ABANDON;
    }

    @Override
    public void measure() {
        host.entry(orderId).ifPresent(e -> {
            Font font = host.font();
            riskY = 37 + termRows() * 12 + 3;
            int riskLines = MoneyText.lineCount(font, riskText(e), RISK_TEXT_W);
            int noteLines = MoneyText.lineCount(font, note(e), W - 20);
            buttonsY = riskY + riskLines * 10 + 15 + 4 + noteLines * 10 + 6;
            height = buttonsY + 28;
        });
    }

    private int termRows() {
        return abandon() ? 3 : 2;
    }

    private Component riskText(BoardSnapshotPayload.BoardEntry e) {
        if (!abandon()) {
            return Component.translatable("ui.aerocouriers.cancel_confirm.risk");
        }
        return Component.translatable(e.villageCustomer() ? "ui.aerocouriers.abandon.risk.npc"
                : "ui.aerocouriers.abandon.risk",
                dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.collateral()),
                dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.reward()));
    }

    private Component note(BoardSnapshotPayload.BoardEntry e) {
        if (!abandon()) {
            return Component.translatable("ui.aerocouriers.cancel_confirm.cargo");
        }
        if (e.villageCustomer()) {
            return Component.translatable("ui.aerocouriers.abandon.cargo.npc");
        }
        return Component.translatable(e.state() == ContractState.IN_TRANSIT
                ? "ui.aerocouriers.abandon.cargo.in_transit" : "ui.aerocouriers.abandon.cargo.accepted");
    }

    @Override
    protected void renderLocal(GuiGraphics graphics, int mx, int my, float partialTick) {
        hoverLines = null;
        Optional<BoardSnapshotPayload.BoardEntry> found = host.entry(orderId);
        if (found.isEmpty()) {
            return;
        }
        BoardSnapshotPayload.BoardEntry e = found.get();
        Font font = host.font();
        TabletDraw.panel(graphics, 0, 0, W, height, HEADER_H);

        boolean closeHover = TabletDraw.inside(mx, my, CLOSE_X, CLOSE_Y, 13, 12);
        TabletDraw.box(graphics, CLOSE_X, CLOSE_Y, CLOSE_X + 12, CLOSE_Y + 11,
                closeHover ? TabletPalette.hoverBrighten(TabletPalette.CLOSE_BG) : TabletPalette.CLOSE_BG,
                closeHover ? TabletPalette.TXT : TabletPalette.CORAL);
        PixelGlyphs.draw(graphics, CLOSE_X + 3, CLOSE_Y + 3, PixelGlyphs.CROSS, TabletPalette.CORAL);
        BoardDetailsOverlay.drawRoute(graphics, font, e, 10, 4, CLOSE_X - 18, TabletPalette.TXT);

        String title = Component.translatable(abandon() ? "ui.aerocouriers.abandon.title"
                : "ui.aerocouriers.cancel_confirm.title", e.shortId()).getString();
        TabletDraw.text(graphics, font, title, 10, 19, TabletPalette.CORAL);
        String status = BoardCardRenderer.statusLabel(e);
        TabletDraw.textRight(graphics, font, status, W - 10, 19, BoardCardRenderer.statusColor(e), false);
        TabletDraw.hline(graphics, 8, W - 9, 31, TabletPalette.DIVIDER);

        int ry = 37;
        label(graphics, font, "ui.aerocouriers.details.reward", ry);
        String reward = "+" + dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.reward());
        TabletDraw.bold(graphics, font, reward, VALUE_X, ry, abandon() ? TabletPalette.LOCKC : TabletPalette.GOLD);
        String rewardNote = " · " + Component.translatable(abandon() ? "ui.aerocouriers.abandon.reward_lost"
                : "ui.aerocouriers.cancel_confirm.reward_back").getString();
        int rnX = VALUE_X + TabletDraw.boldWidth(font, reward);
        TabletDraw.text(graphics, font, TabletDraw.trunc(font, rewardNote, W - 10 - rnX), rnX, ry,
                abandon() ? TabletPalette.CORAL : TabletPalette.TEAL);
        ry += 12;

        if (abandon()) {
            label(graphics, font, "ui.aerocouriers.details.collateral", ry);
            String amount = e.collateral() > 0
                    ? "-" + dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.collateral()) : "0";
            TabletDraw.bold(graphics, font, amount, VALUE_X, ry, TabletPalette.CORAL);
            String note = " · " + Component.translatable(e.villageCustomer() ? "ui.aerocouriers.abandon.collateral_burns"
                    : "ui.aerocouriers.abandon.collateral_to_customer").getString();
            int noteX = VALUE_X + TabletDraw.boldWidth(font, amount);
            TabletDraw.text(graphics, font, TabletDraw.trunc(font, note, W - 10 - noteX), noteX, ry,
                    TabletPalette.CORAL);
            ry += 12;
            label(graphics, font, "ui.aerocouriers.details.deadline", ry);
            TabletDraw.text(graphics, font,
                    Component.translatable("ui.aerocouriers.details.minutes_left", e.minutesLeft()).getString(),
                    VALUE_X, ry, TabletPalette.TXT);
        } else {
            label(graphics, font, "ui.aerocouriers.details.distance", ry);
            TabletDraw.text(graphics, font,
                    Component.translatable("ui.aerocouriers.card.blocks", TabletDraw.num(e.distance())).getString(),
                    VALUE_X, ry, TabletPalette.TXT);
        }

        int riskLines = MoneyText.lineCount(font, riskText(e), RISK_TEXT_W);
        int riskBottom = riskY + riskLines * 10 + 14;
        TabletDraw.box(graphics, 8, riskY, W - 9, riskBottom, TabletPalette.RISK_BG, TabletPalette.CORAL);
        PixelGlyphs.draw(graphics, 14, riskY + 7, PixelGlyphs.WARN, TabletPalette.CORAL);
        MoneyText.drawWrapped(graphics, font, riskText(e), 26, riskY + 7, RISK_TEXT_W, TabletPalette.TXT, 10);
        MoneyText.drawWrapped(graphics, font, note(e), 10, riskBottom + 5, W - 20, TabletPalette.LOCKC, 10);

        String confirm = Component.translatable(abandon() ? "ui.aerocouriers.abandon"
                : "ui.aerocouriers.cancel_confirm.button").getString();
        TabletDraw.button(graphics, font, CONFIRM_X, buttonsY, CONFIRM_W, confirm, TabletDraw.ButtonKind.DANGER, null,
                TabletDraw.inside(mx, my, CONFIRM_X, buttonsY, CONFIRM_W, TabletDraw.BUTTON_H));
        TabletDraw.button(graphics, font, CANCEL_X, buttonsY, CANCEL_W,
                Component.translatable("ui.aerocouriers.accept.cancel").getString(), TabletDraw.ButtonKind.SECONDARY,
                null, TabletDraw.inside(mx, my, CANCEL_X, buttonsY, CANCEL_W, TabletDraw.BUTTON_H));
    }

    private static void label(GuiGraphics graphics, Font font, String key, int ry) {
        TabletDraw.text(graphics, font, Component.translatable(key).getString(), LABEL_X, ry, TabletPalette.SEC);
    }

    @Override
    protected boolean clickLocal(int mx, int my, int button) {
        Optional<BoardSnapshotPayload.BoardEntry> found = host.entry(orderId);
        if (found.isEmpty()) {
            host.closeConfirm();
            return true;
        }
        if (button != 0) {
            return true;
        }
        if (TabletDraw.inside(mx, my, CLOSE_X, CLOSE_Y, 13, 12)
                || TabletDraw.inside(mx, my, CANCEL_X, buttonsY, CANCEL_W, TabletDraw.BUTTON_H)) {
            click();
            host.closeConfirm();
            return true;
        }
        if (TabletDraw.inside(mx, my, CONFIRM_X, buttonsY, CONFIRM_W, TabletDraw.BUTTON_H)) {
            click();
            host.confirmAction(found.get(), action);
            return true;
        }
        return true;
    }
}
