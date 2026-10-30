package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.MoneyText;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** «Принять заказ?» with the risk block over the dimmed board (mockup 2_accept). */
public final class BoardAcceptOverlay extends BoardConfirmOverlay {
    public static final int W = 304;
    private static final int HEADER_H = 14;
    private static final int LABEL_X = 10;
    private static final int VALUE_X = 86;
    private static final int RISK_Y = 88;
    private static final int RISK_TEXT_W = W - 16 - 26 - 6;
    private static final int CONFIRM_X = 10;
    /** "Cancel" / "Отмена" stay on the right; the rest of the row is the tracked collateral label. */
    private static final int CANCEL_W = 78;
    private static final int CANCEL_X = W - 10 - CANCEL_W;
    private static final int CONFIRM_W = CANCEL_X - 8 - CONFIRM_X;
    private static final int CLOSE_X = W - 17;
    private static final int CLOSE_Y = 1;

    private final BoardOverlayHost host;
    private final UUID orderId;
    private int height = 184;
    private int buttonsY = 140;
    private @Nullable List<Component> hoverLines;

    public BoardAcceptOverlay(BoardOverlayHost host, UUID orderId) {
        this.host = host;
        this.orderId = orderId;
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

    @Override
    public void measure() {
        host.entry(orderId).ifPresent(e -> {
            Font font = host.font();
            int riskLines = MoneyText.lineCount(font, riskText(e), RISK_TEXT_W);
            int noteLines = MoneyText.lineCount(font, damageNote(), W - 20);
            int riskH = riskLines * 10 + 15;
            buttonsY = RISK_Y + riskH + 4 + noteLines * 10 + 6;
            height = buttonsY + 38;
        });
    }

    private long shortfall(BoardSnapshotPayload.BoardEntry e) {
        return Math.max(0L, host.collateralDue(e) - host.balance());
    }

    private boolean canConfirm(BoardSnapshotPayload.BoardEntry e) {
        return host.acceptDenial(e).isEmpty() && shortfall(e) == 0L;
    }

    private Component riskText(BoardSnapshotPayload.BoardEntry e) {
        if (CollateralUi.hidden(e.collateral())) {
            return Component.translatable(e.villageCustomer() ? "contract.aerocouriers.risk.npc.no_collateral"
                    : "contract.aerocouriers.risk.no_collateral");
        }
        return Component.translatable(e.villageCustomer() ? "contract.aerocouriers.risk.npc"
                : "contract.aerocouriers.risk",
                dev.poligorn.aerocouriers.economy.MoneyFormat.text(host.collateralDue(e)),
                dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.reward()));
    }

    private static Component damageNote() {
        return Component.translatable("ui.aerocouriers.accept.confirm.cargo_damage");
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

        String subtitle = Component.translatable("ui.aerocouriers.accept.subtitle", e.shortId(),
                BoardCardRenderer.roman(e.requiredRank())).getString();
        if (e.originRevealed()) {
            subtitle = Component.translatable(e.originStation()
                    ? "ui.aerocouriers.details.kind.station" : "ui.aerocouriers.details.kind.point").getString()
                    + " · " + subtitle;
        }
        TabletDraw.text(graphics, font, subtitle, 10, 19, TabletPalette.LOCKC);
        TabletDraw.hline(graphics, 8, W - 9, 31, TabletPalette.DIVIDER);

        int ry = 37;
        label(graphics, font, "ui.aerocouriers.details.reward", ry);
        TabletDraw.bold(graphics, font, "+" + dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.reward()),
                VALUE_X, ry, TabletPalette.GOLD);
        ry += 12;

        long due = host.collateralDue(e);
        if (!CollateralUi.hidden(e.collateral())) {
            label(graphics, font, "ui.aerocouriers.details.collateral", ry);
            String amount = due > 0 ? "-" + dev.poligorn.aerocouriers.economy.MoneyFormat.text(due) : "0";
            int color = due > 0 ? TabletPalette.CORAL : TabletPalette.TEAL;
            TabletDraw.bold(graphics, font, amount, VALUE_X, ry, color);
            String note = " · " + Component.translatable(due > 0 ? "ui.aerocouriers.accept.collateral_now"
                    : host.traineeFree(e) ? "ui.aerocouriers.details.collateral_trainee"
                    : "ui.aerocouriers.card.collateral_none").getString();
            int noteX = VALUE_X + TabletDraw.boldWidth(font, amount);
            TabletDraw.text(graphics, font, TabletDraw.trunc(font, note, W - 10 - noteX), noteX, ry, color);
            ry += 12;
        }

        label(graphics, font, "ui.aerocouriers.details.deadline", ry);
        TabletDraw.text(graphics, font,
                Component.translatable("ui.aerocouriers.details.minutes", e.deadlineMinutes()).getString(),
                VALUE_X, ry, TabletPalette.TXT);
        ry += 12;

        label(graphics, font, "ui.aerocouriers.details.distance", ry);
        TabletDraw.text(graphics, font,
                Component.translatable("ui.aerocouriers.card.blocks", TabletDraw.num(e.distance())).getString(),
                VALUE_X, ry, TabletPalette.TXT);

        int riskLines = MoneyText.lineCount(font, riskText(e), RISK_TEXT_W);
        int riskBottom = RISK_Y + riskLines * 10 + 14;
        TabletDraw.box(graphics, 8, RISK_Y, W - 9, riskBottom, TabletPalette.RISK_BG, TabletPalette.CORAL);
        PixelGlyphs.draw(graphics, 14, RISK_Y + 7, PixelGlyphs.WARN, TabletPalette.CORAL);
        MoneyText.drawWrapped(graphics, font, riskText(e), 26, RISK_Y + 7, RISK_TEXT_W, TabletPalette.TXT, 10);
        MoneyText.drawWrapped(graphics, font, damageNote(), 10, riskBottom + 5, W - 20, TabletPalette.LOCKC, 10);

        int yb = buttonsY;
        boolean ok = canConfirm(e);
        String confirm = due > 0
                ? Component.translatable("ui.aerocouriers.details.accept",
                        dev.poligorn.aerocouriers.economy.MoneyFormat.text(due)).getString()
                : Component.translatable(CollateralUi.hidden(e.collateral())
                        ? "ui.aerocouriers.details.accept_plain" : "ui.aerocouriers.details.accept_free").getString();
        TabletDraw.button(graphics, font, CONFIRM_X, yb, CONFIRM_W, confirm,
                ok ? TabletDraw.ButtonKind.PRIMARY : TabletDraw.ButtonKind.DISABLED, null,
                ok && TabletDraw.inside(mx, my, CONFIRM_X, yb, CONFIRM_W, TabletDraw.BUTTON_H));
        TabletDraw.button(graphics, font, CANCEL_X, yb, CANCEL_W,
                Component.translatable("ui.aerocouriers.accept.cancel").getString(), TabletDraw.ButtonKind.SECONDARY,
                null, TabletDraw.inside(mx, my, CANCEL_X, yb, CANCEL_W, TabletDraw.BUTTON_H));

        int hy = yb + 25;
        Optional<Component> denial = host.acceptDenial(e);
        long balance = host.balance();
        if (denial.isPresent()) {
            String text = denial.get().getString();
            String shown = TabletDraw.trunc(font, text, W - 20);
            TabletDraw.text(graphics, font, shown, 10, hy, TabletPalette.CORAL);
            if (!shown.equals(text) && TabletDraw.inside(mx, my, 10, hy - 1, W - 20, 10)) {
                hoverLines = List.of(denial.get());
            }
        } else if (shortfall(e) > 0) {
            String lack = Component.translatable("ui.aerocouriers.accept.short",
                    dev.poligorn.aerocouriers.economy.MoneyFormat.text(shortfall(e)))
                    .getString();
            TabletDraw.text(graphics, font, lack, 10, hy, TabletPalette.CORAL);
            String rest = " · " + Component.translatable("ui.aerocouriers.accept.short_detail",
                    dev.poligorn.aerocouriers.economy.MoneyFormat.text(balance),
                    dev.poligorn.aerocouriers.economy.MoneyFormat.text(due)).getString();
            int rx = 10 + MoneyText.width(font, lack);
            TabletDraw.text(graphics, font, TabletDraw.trunc(font, rest, W - 10 - rx), rx, hy, TabletPalette.SEC);
        } else {
            TabletDraw.text(graphics, font, Component.translatable("ui.aerocouriers.accept.balance_left",
                    dev.poligorn.aerocouriers.economy.MoneyFormat.text(balance),
                    dev.poligorn.aerocouriers.economy.MoneyFormat.text(balance - due)).getString(), 10, hy,
                    TabletPalette.SEC);
        }
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
        BoardSnapshotPayload.BoardEntry e = found.get();
        if (TabletDraw.inside(mx, my, CLOSE_X, CLOSE_Y, 13, 12)
                || TabletDraw.inside(mx, my, CANCEL_X, buttonsY, CANCEL_W, TabletDraw.BUTTON_H)) {
            click();
            host.closeConfirm();
            return true;
        }
        if (canConfirm(e) && TabletDraw.inside(mx, my, CONFIRM_X, buttonsY, CONFIRM_W, TabletDraw.BUTTON_H)) {
            click();
            host.confirmAccept(e);
            return true;
        }
        return true;
    }
}
