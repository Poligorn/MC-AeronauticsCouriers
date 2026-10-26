package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Tablet-style confirmation before accepting a contract (GDD §7.2 risk transparency). */
public final class BoardAcceptConfirmScreen extends Screen {
    private final Screen parent;
    private final BoardSnapshotPayload.BoardEntry entry;

    private @Nullable Component hoverTip;

    public BoardAcceptConfirmScreen(Screen parent, BoardSnapshotPayload.BoardEntry entry) {
        super(Component.translatable("ui.aerocouriers.accept.confirm.title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();
        int boxW = Math.min(360, width - 48);
        int boxH = Math.min(280, height - 48);
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;
        int btnW = (boxW - 24) / 2;
        int btnY = boxY + boxH - 28;
        addRenderableWidget(TabletButton.create(boxX + 8, btnY, btnW, 20,
                Component.translatable("ui.aerocouriers.accept.confirm.action"), b -> confirm()));
        addRenderableWidget(TabletButton.create(boxX + 16 + btnW, btnY, btnW, 20,
                Component.translatable("gui.cancel"), b -> onClose()));
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
        hoverTip = null;
        graphics.fill(0, 0, width, height, 0xC0000000);
        int boxW = Math.min(360, width - 48);
        int boxH = Math.min(280, height - 48);
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;
        TabletGui.drawListPanel(graphics, boxX, boxY, boxW, boxH);
        graphics.drawString(font, title, boxX + 10, boxY + 8, TabletGui.TEXT_HEADER, false);

        int textX = boxX + 10;
        int textW = boxW - 20;
        int y = boxY + 24;
        for (Component line : bodyLines()) {
            y = drawWrappedClipped(graphics, line, textX, y, textW, TabletGui.TEXT, mouseX, mouseY) + 4;
        }

        Component risk = entry.villageCustomer()
                ? Component.translatable("contract.aerocouriers.risk.npc", entry.collateral(), entry.reward())
                : Component.translatable("contract.aerocouriers.risk", entry.collateral(), entry.reward());
        y = drawWrappedClipped(graphics, risk, textX, y + 4, textW, TabletGui.TEXT_WARN, mouseX, mouseY) + 4;

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoverTip != null) {
            TabletGui.drawSpeechTooltip(graphics, font, hoverTip, mouseX, mouseY, textW);
        }
    }

    private List<Component> bodyLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.id", entry.shortId()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.customer", entry.customerName()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.route",
                entry.originLabel(), entry.destinationLabel(), entry.distance()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.reward", entry.reward()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.collateral", entry.collateral()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.deadline",
                entry.deadlineMinutes(), entry.minutesLeft()));
        lines.add(Component.translatable("ui.aerocouriers.accept.confirm.rank", entry.requiredRank().displayName()));
        return lines;
    }

    private int drawWrappedClipped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color,
                                   int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int drawY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, drawY, color, false);
            drawY += 10;
        }
        String raw = text.getString();
        String oneLine = font.plainSubstrByWidth(raw, maxWidth);
        if (!oneLine.equals(raw) && mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < drawY) {
            hoverTip = text;
        }
        return drawY;
    }
}
