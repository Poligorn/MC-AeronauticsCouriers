package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletMockupPalette;
import dev.poligorn.aerocouriers.client.TabletMockupUi;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Two-line open-order row layout — mockup card() (640×360). */
public final class BoardOpenRowRenderer {
    private static final int SOURCE_ICON = 8;
    private static final int ICON_BTN = 18;
    private static final int REWARD_INSET = 44;
    private static final int EFF_INSET = 90;

    private BoardOpenRowRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            int textLeft,
            int textRight,
            int rowTop,
            boolean rankLocked,
            boolean showNewBadge,
            boolean urgentDeadline
    ) {
        int line1Y = rowTop + 4;
        int line2Y = rowTop + 14;
        int cardRight = textRight + REWARD_INSET;
        int rewardRight = textRight;
        int effRight = rewardRight - EFF_INSET;

        int muted = rankLocked ? TabletMockupPalette.LOCKC : TabletMockupPalette.SEC;
        int text = rankLocked ? TabletMockupPalette.LOCKC : TabletMockupPalette.TXT;

        int x = textLeft;
        if (entry.villageCustomer()) {
            graphics.blitSprite(AerocouriersGuiSprites.ICON_VILLAGE, x, line1Y, SOURCE_ICON, SOURCE_ICON);
        } else {
            BoardCustomerFace.draw(graphics, x, line1Y, entry.customerId());
        }
        x += SOURCE_ICON + 4;

        String routeA = BoardEntryPickup.routeOriginLabel(entry).getString();
        if (!entry.originRevealed() && routeA.isEmpty()) {
            routeA = Component.translatable("ui.aerocouriers.row.pickup_hidden").getString();
            text = TabletMockupPalette.LOCKC;
        }
        int routeMax = Math.max(8, effRight - 80 - x);
        drawClipped(graphics, font, routeA, x, line1Y, Math.min(150, routeMax), text);
        x += font.width(TabletMockupUi.clip(font, routeA, Math.min(150, routeMax))) + 4;
        graphics.drawString(font, "→", x, line1Y, muted, false);
        x += font.width("→") + 4;
        String routeB = entry.destinationLabel();
        drawClipped(graphics, font, routeB, x, line1Y, Math.max(8, effRight - 80 - x), text);

        int badgeX = x + font.width(TabletMockupUi.clip(font, routeB, Math.max(8, effRight - 80 - x))) + 4;
        if (showNewBadge && !rankLocked) {
            badgeX = drawBadge(graphics, font, Component.translatable("ui.aerocouriers.row.badge.new"),
                    badgeX, line1Y, TabletMockupPalette.TEAL);
            badgeX += 4;
        }
        if (urgentDeadline && !rankLocked) {
            drawBadge(graphics, font, Component.translatable("ui.aerocouriers.row.badge.urgent"),
                    badgeX, line1Y, TabletMockupPalette.CORAL);
        }

        if (rankLocked) {
            renderLockedReward(graphics, font, entry, rewardRight, line1Y);
        } else {
            String rewardText = "+" + entry.reward();
            TabletMockupUi.drawRight(graphics, font, rewardText, rewardRight, line1Y, TabletMockupPalette.GOLD, true);
            long due = TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                    profile.villageDelivered(), profile.traineeFreeContracts());
            String dep;
            int depColor;
            if (due <= 0 && entry.villageCustomer()
                    && TraineeRules.onTraineeVillageContracts(profile.villageDelivered(),
                    profile.traineeFreeContracts())) {
                dep = Component.translatable("ui.aerocouriers.row.deposit_trainee").getString();
                depColor = TabletMockupPalette.TEAL;
            } else {
                dep = Component.translatable("ui.aerocouriers.row.deposit", due).getString();
                depColor = TabletMockupPalette.CORAL;
            }
            TabletMockupUi.drawRight(graphics, font, dep, rewardRight, line2Y, depColor, false);
            String effText = BoardRewardEfficiency.perThousandBlocks(entry) + "/1000";
            TabletMockupUi.drawRight(graphics, font, effText, effRight, line2Y, muted, false);
        }

        x = textLeft + 10;
        String distance = Component.translatable("ui.aerocouriers.row.distance_blocks", entry.distance()).getString();
        x = drawStat(graphics, font, null, distance, x, line2Y, muted, effRight - 8);

        CargoSize cargoSize = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        String cargo = cargoSize.name() + " " + weightShort(entry.weight());
        x = drawStat(graphics, font, AerocouriersGuiSprites.ICON_CRATE, cargo, x, line2Y, muted, effRight - 8);

        String time = Component.translatable("ui.aerocouriers.row.time_compact", entry.minutesLeft()).getString();
        drawStat(graphics, font, AerocouriersGuiSprites.ICON_CLOCK, time, x, line2Y, muted, effRight - 8);
    }

    private static void renderLockedReward(
            GuiGraphics graphics,
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            int rewardRight,
            int line1Y
    ) {
        String need = BoardRankLabels.rankRequiredShort(entry.requiredRank()).getString();
        int blockW = 12 + 2 + font.width(need);
        int bx = rewardRight - blockW;
        graphics.blitSprite(AerocouriersGuiSprites.ICON_LOCK, bx, line1Y, 12, 12);
        graphics.drawString(font, need, bx + 14, line1Y + 2, TabletMockupPalette.LOCKC, false);
    }

    private static String weightShort(String weightField) {
        int slash = weightField.indexOf('/');
        if (slash > 0) {
            return weightField.substring(0, slash).trim() + "кг";
        }
        return weightField;
    }

    private static int drawStat(
            GuiGraphics graphics,
            Font font,
            net.minecraft.resources.ResourceLocation icon,
            String value,
            int x,
            int y,
            int color,
            int maxX
    ) {
        if (x >= maxX) {
            return x;
        }
        if (icon != null) {
            graphics.blitSprite(icon, x, y, 8, 8);
            x += 11;
        }
        value = TabletMockupUi.clip(font, value, Math.max(4, maxX - x));
        graphics.drawString(font, value, x, y + 1, color, false);
        return x + font.width(value) + 4;
    }

    private static int drawBadge(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        FormattedCharSequence line = text.getVisualOrderText();
        int w = font.width(line);
        int h = font.lineHeight + 2;
        graphics.fill(x, y - 1, x + w + 6, y - 1 + h, color == TabletMockupPalette.TEAL ? 0xFF1A3C36 : 0xFF462016);
        graphics.renderOutline(x, y - 1, w + 6, h, color);
        graphics.drawString(font, line, x + 3, y, color, false);
        return x + w + 6;
    }

    private static void drawClipped(GuiGraphics graphics, Font font, String raw, int x, int y, int width, int color) {
        graphics.drawString(font, TabletMockupUi.clip(font, raw, width), x, y, color, false);
    }

    public static boolean isUrgent(BoardSnapshotPayload.BoardEntry entry) {
        if (entry.deadlineMinutes() <= 0) {
            return false;
        }
        double ratio = entry.minutesLeft() / (double) entry.deadlineMinutes();
        return ratio < 0.2;
    }
}
