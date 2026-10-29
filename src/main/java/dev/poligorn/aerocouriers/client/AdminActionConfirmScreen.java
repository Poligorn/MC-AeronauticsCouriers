package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Confirms destructive admin contract actions (fail / cancel with refund). */
public final class AdminActionConfirmScreen extends Screen {
    private static final int PAD = TabletGui.CONTENT_INSET;

    private final Screen parent;
    private final OperatorBoardPayload.Action action;
    private final UUID contractId;
    private final Component body;

    private int boxW;
    private int boxH;
    private int boxX;
    private int boxY;
    private int btnY;
    private ScreenFit fit = ScreenFit.of(ScreenFit.DESIGN_W, ScreenFit.DESIGN_H);

    public AdminActionConfirmScreen(Screen parent, OperatorBoardPayload.Action action, UUID contractId,
                                    Component body) {
        super(Component.translatable("ui.aerocouriers.admin.confirm.title"));
        this.parent = parent;
        this.action = action;
        this.contractId = contractId;
        this.body = body;
    }

    @Override
    protected void init() {
        refreshFit();
        super.init();
        relayout();
        int btnW = (boxW - 24) / 2;
        clearWidgets();
        addRenderableWidget(TabletButton.create(boxX + 8, btnY, btnW, 20,
                Component.translatable("ui.aerocouriers.admin.confirm.yes"), b -> confirm()));
        addRenderableWidget(TabletButton.create(boxX + 16 + btnW, btnY, btnW, 20,
                Component.translatable("ui.aerocouriers.admin.confirm.no"), b -> onClose()));
    }

    private void relayout() {
        int spaceW = fit.shrinking() ? fit.layoutW : width;
        int spaceH = fit.shrinking() ? fit.layoutH : height;
        boxW = Math.min(300, spaceW - 48);
        boxH = 120;
        boxX = (spaceW - boxW) / 2;
        boxY = (spaceH - boxH) / 2;
        btnY = boxY + boxH - 28;
    }

    private void confirm() {
        PacketDistributor.sendToServer(new OperatorBoardPayload(action, Optional.of(contractId), Optional.empty(),
                Optional.empty(), Optional.empty(), 0L, true));
        onClose();
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
        refreshFit();
        relayout();
        for (var widget : renderables) {
            if (widget instanceof TabletButton button) {
                button.setY(btnY);
            }
        }
        if (fit.shrinking()) {
            int lx = fit.localXi(mouseX);
            int ly = fit.localYi(mouseY);
            InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
            graphics.fill(0, 0, width, height, 0xC0000000);
            graphics.pose().pushPose();
            fit.apply(graphics);
            try {
                drawScaledPanel(graphics);
                for (var widget : renderables) {
                    widget.render(graphics, lx, ly, partialTick);
                }
                drawBody(graphics);
            } finally {
                graphics.pose().popPose();
            }
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        drawBody(graphics);
    }

    /** Panel under the fit pose. Kept out of {@code render} so the chrome guard stays on {@code renderBackground}. */
    private void drawScaledPanel(GuiGraphics graphics) {
        TabletGui.drawListPanel(graphics, boxX, boxY, boxW, boxH);
    }

    private void drawBody(GuiGraphics graphics) {
        graphics.drawString(font, title, boxX + PAD, boxY + PAD, TabletGui.TEXT_HEADER, false);
        int y = boxY + PAD + font.lineHeight + 6;
        for (var line : font.split(body, boxW - PAD * 2)) {
            graphics.drawString(font, line, boxX + PAD, y, TabletGui.TEXT_MUTED, false);
            y += font.lineHeight;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        refreshFit();
        return super.mouseClicked(fit.localX(mouseX), fit.localY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        refreshFit();
        return super.mouseReleased(fit.localX(mouseX), fit.localY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        refreshFit();
        double s = fit.scale;
        return super.mouseDragged(fit.localX(mouseX), fit.localY(mouseY), button, dragX / s, dragY / s);
    }

    private void refreshFit() {
        fit = ScreenFit.of(width, height);
    }
}
