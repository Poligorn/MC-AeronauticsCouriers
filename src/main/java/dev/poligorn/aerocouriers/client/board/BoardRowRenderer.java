package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Renders the text/icons inside one board list row (buttons are separate widgets). */
public final class BoardRowRenderer {
    private static final int STATUS_MAX_WIDTH = 88;

    private BoardRowRenderer() {
    }

    public record Line1Hover(@Nullable Component tooltip) {
    }

    public static Line1Hover renderLine1(
            GuiGraphics graphics,
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            boolean captureHover,
            BiConsumer<Component, Integer> clippedHover
    ) {
        int cursor = x;
        String idText = "#" + entry.shortId();
        graphics.drawString(font, idText, cursor, y, TabletGui.TEXT, false);
        cursor += font.width(idText) + BoardRowMetrics.ID_STATUS_GAP;

        Component status = entry.state().displayName();
        int statusWidth = Math.max(8, Math.min(STATUS_MAX_WIDTH, x + width - cursor - BoardRowMetrics.CUSTOMER_FACE - 48));
        String statusShown = clip(font, status.getString(), statusWidth);
        graphics.drawString(font, statusShown, cursor, y, TabletGui.TEXT_MUTED, false);
        if (captureHover && !statusShown.equals(status.getString())
                && mouseOver(mouseX, mouseY, cursor, y, statusWidth)) {
            clippedHover.accept(status, statusWidth);
        }
        cursor += font.width(statusShown) + 4;

        int faceX = cursor;
        if (entry.villageCustomer()) {
            BoardCustomerFace.drawVillageIcon(graphics, faceX, y);
        } else {
            BoardCustomerFace.draw(graphics, faceX, y, entry.customerId());
        }
        cursor += BoardRowMetrics.CUSTOMER_FACE + BoardRowMetrics.FACE_NAME_GAP;

        int nameW = Math.max(8, x + width - cursor);
        String nameShown = clip(font, entry.customerName(), nameW);
        graphics.drawString(font, nameShown, cursor, y, TabletGui.TEXT, false);
        Component customerTip = null;
        if (captureHover) {
            customerTip = line1HoverTooltip(
                    entry, faceX, cursor, y, nameW, nameShown, mouseX, mouseY, clippedHover);
        }
        return new Line1Hover(customerTip);
    }

    /** Hover hit-test for line 1 without drawing (matches {@link #renderLine1} geometry). */
    public static Line1Hover hitLine1Hover(
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            BiConsumer<Component, Integer> clippedHover
    ) {
        if (mouseY < y || mouseY >= y + BoardRowMetrics.LINE1_HEIGHT) {
            return new Line1Hover(null);
        }
        int cursor = x;
        String idText = "#" + entry.shortId();
        cursor += font.width(idText) + BoardRowMetrics.ID_STATUS_GAP;
        Component status = entry.state().displayName();
        int statusWidth = Math.max(8, Math.min(STATUS_MAX_WIDTH, x + width - cursor - BoardRowMetrics.CUSTOMER_FACE - 48));
        String statusShown = clip(font, status.getString(), statusWidth);
        if (!statusShown.equals(status.getString()) && mouseOver(mouseX, mouseY, cursor, y, statusWidth)) {
            clippedHover.accept(status, statusWidth);
            return new Line1Hover(null);
        }
        cursor += font.width(statusShown) + 4;
        int faceX = cursor;
        cursor += BoardRowMetrics.CUSTOMER_FACE + BoardRowMetrics.FACE_NAME_GAP;
        int nameW = Math.max(8, x + width - cursor);
        String nameShown = clip(font, entry.customerName(), nameW);
        Component tip = line1HoverTooltip(
                entry, faceX, cursor, y, nameW, nameShown, mouseX, mouseY, clippedHover);
        return new Line1Hover(tip);
    }

    private static @Nullable Component line1HoverTooltip(
            BoardSnapshotPayload.BoardEntry entry,
            int faceX,
            int nameX,
            int y,
            int nameW,
            String nameShown,
            int mouseX,
            int mouseY,
            BiConsumer<Component, Integer> clippedHover
    ) {
        if (mouseOver(mouseX, mouseY, faceX, y, BoardRowMetrics.CUSTOMER_FACE + BoardRowMetrics.FACE_NAME_GAP + nameW)) {
            if (entry.villageCustomer()) {
                Component next = entry.customerReputationNext() < 0
                        ? Component.translatable("ui.aerocouriers.reputation.max_tier")
                        : Component.translatable("ui.aerocouriers.row.reputation_next_inline",
                                entry.customerReputationNext());
                return Component.translatable("ui.aerocouriers.row.customer_tooltip_village",
                        entry.customerName(), entry.customerStationReputation(), entry.customerReputationTier(),
                        entry.customerReputationBonusPercent(), next);
            }
            return Component.translatable("ui.aerocouriers.row.customer_tooltip", entry.customerName());
        }
        if (!nameShown.equals(entry.customerName()) && mouseOver(mouseX, mouseY, nameX, y, nameW)) {
            clippedHover.accept(Component.literal(entry.customerName()), nameW);
        }
        return null;
    }

    public static void renderLine2(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry entry, int x, int y,
                                   int width) {
        int statX = x;
        statX = drawStat(graphics, font, AerocouriersGuiSprites.ICON_COIN, Long.toString(entry.reward()), statX, y);
        statX = drawStat(graphics, font, AerocouriersGuiSprites.ICON_LOCK, Long.toString(entry.collateral()), statX, y);
        String time = Component.translatable("ui.aerocouriers.row.time_compact", entry.minutesLeft()).getString();
        statX = drawStat(graphics, font, AerocouriersGuiSprites.ICON_CLOCK, time, statX, y);
        if ("issuer".equals(entry.role())) {
            graphics.blitSprite(AerocouriersGuiSprites.ICON_STAR, statX + 2, y, BoardRowMetrics.LINE2_ICON,
                    BoardRowMetrics.LINE2_ICON);
        }
        if (entry.villageRare()) {
            int icon = BoardRowMetrics.LINE2_ICON;
            int rareX = x + Math.max(statX - x, width - icon);
            graphics.blitSprite(AerocouriersGuiSprites.ICON_RARE_CARGO, rareX, y, icon, icon);
        }
    }

    public static @Nullable Component hitRareCargoHover(BoardSnapshotPayload.BoardEntry entry, int x, int y, int width,
                                                        int mouseX, int mouseY) {
        if (!entry.villageRare()) {
            return null;
        }
        int icon = BoardRowMetrics.LINE2_ICON;
        int rareX = x + Math.max(0, width - icon);
        if (mouseOver(mouseX, mouseY, rareX, y, icon)) {
            return Component.translatable("ui.aerocouriers.row.rare_cargo");
        }
        return null;
    }

    private static int drawStat(GuiGraphics graphics, Font font, ResourceLocation icon, String value, int x, int y) {
        int iconW = BoardRowMetrics.LINE2_ICON;
        graphics.blitSprite(icon, x, y, iconW, iconW);
        int textX = x + iconW + BoardRowMetrics.ICON_TEXT_GAP;
        int textY = y + Math.max(0, iconW - 8);
        graphics.drawString(font, value, textX, textY, TabletGui.TEXT_MUTED, false);
        return textX + font.width(value) + BoardRowMetrics.PAIR_GAP;
    }

    public static String clip(Font font, String raw, int width) {
        if (width <= 0) {
            return "";
        }
        String shown = font.plainSubstrByWidth(raw, width);
        if (!shown.equals(raw)) {
            shown = font.plainSubstrByWidth(raw, Math.max(8, width - font.width("…"))) + "…";
        }
        return shown;
    }

    private static boolean mouseOver(int mouseX, int mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 9;
    }
}
