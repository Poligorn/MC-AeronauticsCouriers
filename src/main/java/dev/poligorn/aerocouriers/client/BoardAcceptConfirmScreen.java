package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tablet-style confirmation before accepting a contract (GDD §7.2 risk transparency). */
public final class BoardAcceptConfirmScreen extends Screen {
    private static final int PAD = 10;
    private static final int FOOTER = 36;
    private static final int LINE_GAP = 4;

    private final Screen parent;
    private final BoardSnapshotPayload.BoardEntry entry;
    private final BoardSnapshotPayload.BoardProfile profile;

    private int boxW;
    private int boxH;
    private int boxX;
    private int boxY;
    private int btnY;

    public BoardAcceptConfirmScreen(Screen parent, BoardSnapshotPayload.BoardEntry entry,
                                    BoardSnapshotPayload.BoardProfile profile) {
        super(Component.translatable("ui.aerocouriers.accept.confirm.title"));
        this.parent = parent;
        this.entry = entry;
        this.profile = profile;
    }

    @Override
    protected void init() {
        super.init();
        relayout();
        int btnW = (boxW - 24) / 2;
        clearWidgets();
        addRenderableWidget(TabletButton.create(boxX + 8, btnY, btnW, 20,
                Component.translatable("ui.aerocouriers.accept.confirm.action"), b -> confirm()));
        addRenderableWidget(TabletButton.create(boxX + 16 + btnW, btnY, btnW, 20,
                Component.translatable("gui.cancel"), b -> onClose()));
    }

    private void relayout() {
        boxW = Math.min(360, width - 48);
        int textW = boxW - PAD * 2;
        int contentH = PAD + font.lineHeight + 6;
        for (Component line : bodyLines()) {
            contentH += wrappedHeight(line, textW) + LINE_GAP;
        }
        contentH += LINE_GAP;
        contentH += wrappedHeight(riskLine(), textW) + LINE_GAP;
        contentH += wrappedHeight(Component.translatable("ui.aerocouriers.accept.confirm.cargo_damage"), textW);
        contentH += PAD;
        boxH = Math.min(Math.max(contentH + FOOTER, 140), height - 48);
        boxY = (height - boxH) / 2;
        boxX = (width - boxW) / 2;
        btnY = boxY + boxH - 28;
    }

    private int wrappedHeight(Component text, int maxWidth) {
        return font.split(text, maxWidth).size() * font.lineHeight;
    }

    private void confirm() {
        PacketDistributor.sendToServer(new ContractIntentPayload(ContractIntentPayload.Kind.ACCEPT, entry.id()));
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        relayout();
        graphics.fill(0, 0, width, height, 0xC0000000);
        TabletGui.drawListPanel(graphics, boxX, boxY, boxW, boxH);
        graphics.drawString(font, title, boxX + PAD, boxY + PAD, TabletGui.TEXT_HEADER, false);

        int textX = boxX + PAD;
        int textW = boxW - PAD * 2;
        int y = boxY + PAD + font.lineHeight + 6;
        for (Component line : bodyLines()) {
            y = drawWrapped(graphics, line, textX, y, textW, TabletGui.TEXT) + LINE_GAP;
        }
        y = drawWrapped(graphics, riskLine(), textX, y + LINE_GAP, textW, TabletGui.TEXT_WARN) + LINE_GAP;
        drawWrapped(graphics, Component.translatable("ui.aerocouriers.accept.confirm.cargo_damage"),
                textX, y, textW, TabletGui.TEXT_WARN);

        for (Renderable widget : renderables) {
            if (widget instanceof TabletButton button) {
                button.setY(btnY);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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

    private List<Component> bodyLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.id", entry.shortId()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.customer", entry.customerName()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.route",
                entry.originLabel(), entry.destinationLabel(), entry.distance()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.reward", entry.reward()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.collateral", collateralDue()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.deadline",
                entry.deadlineMinutes(), entry.minutesLeft()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.rank", entry.requiredRank().displayName()));
        return lines;
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int drawY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, drawY, color, false);
            drawY += font.lineHeight;
        }
        return drawY;
    }
}
