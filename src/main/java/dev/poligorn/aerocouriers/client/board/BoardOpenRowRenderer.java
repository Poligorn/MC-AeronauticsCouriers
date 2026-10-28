package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Two-line open-order row layout for the «Заказы» tab (mockup 1_board). */
public final class BoardOpenRowRenderer {
    private static final int SOURCE_ICON = 8;
    private static final int BADGE_PAD_H = 3;
    private static final int BADGE_PAD_V = 1;

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
        int line1Y = BoardRowMetrics.line1Y(rowTop);
        int line2Y = BoardRowMetrics.line2Y(rowTop);
        int muted = rankLocked ? TabletGui.TEXT_DISABLED : TabletGui.TEXT_MUTED;
        int text = rankLocked ? TabletGui.TEXT_DISABLED : TabletGui.TEXT;

        int rewardReserve = rewardColumnWidth(font, entry, rankLocked);
        int routeRight = textRight - rewardReserve - 4;
        int x = textLeft;

        if (entry.villageCustomer()) {
            graphics.blitSprite(AerocouriersGuiSprites.ICON_VILLAGE, x, line1Y, SOURCE_ICON, SOURCE_ICON);
        } else {
            BoardCustomerFace.draw(graphics, x, line1Y, entry.customerId());
        }
        x += SOURCE_ICON + 4;

        String route = routeText(entry);
        int routeMax = Math.max(8, routeRight - x);
        drawClipped(graphics, font, route, x, line1Y, routeMax, text);

        int badgeX = x + font.width(font.plainSubstrByWidth(route, routeMax)) + 4;
        if (showNewBadge && !rankLocked) {
            badgeX = drawBadge(graphics, font, Component.translatable("ui.aerocouriers.row.badge.new"),
                    badgeX, line1Y, TabletGui.TEXT_ACCENT);
            badgeX += 4;
        }
        if (urgentDeadline && !rankLocked) {
            drawBadge(graphics, font, Component.translatable("ui.aerocouriers.row.badge.urgent"),
                    badgeX, line1Y, TabletGui.TEXT_RISK);
        }

        if (rankLocked) {
            renderLockedReward(graphics, font, entry, textRight, line1Y);
        } else {
            renderRewardColumn(graphics, font, entry, profile, textRight, line1Y, line2Y);
        }

        x = textLeft;
        String distance = Component.translatable("ui.aerocouriers.row.distance_blocks", entry.distance()).getString();
        x = drawStat(graphics, font, null, distance, x, line2Y, muted);

        CargoSize cargoSize = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        String cargo = cargoSize.name() + " " + weightShort(entry.weight());
        x = drawStat(graphics, font, AerocouriersGuiSprites.ICON_CRATE, cargo, x, line2Y, muted);

        String time = Component.translatable("ui.aerocouriers.row.time_compact", entry.minutesLeft()).getString();
        x = drawStat(graphics, font, AerocouriersGuiSprites.ICON_CLOCK, time, x, line2Y, muted);

        long eff = BoardRewardEfficiency.perThousandBlocks(entry);
        String effText = eff + "/1000";
        int effX = textRight - rewardReserve + 4;
        if (effX > x + 8) {
            graphics.drawString(font, effText, effX, line2Y + 4, muted, false);
        }
    }

    private static void renderRewardColumn(
            GuiGraphics graphics,
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            int textRight,
            int line1Y,
            int line2Y
    ) {
        String rewardText = "+" + entry.reward();
        int rewardW = font.width(rewardText);
        int rewardX = textRight - rewardW;
        TabletGui.drawBoldString(graphics, font, rewardText, rewardX, line1Y, TabletGui.TEXT_REWARD);

        long due = TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                profile.villageDelivered(), profile.traineeFreeContracts());
        if (due <= 0 && entry.villageCustomer()
                && TraineeRules.onTraineeVillageContracts(profile.villageDelivered(), profile.traineeFreeContracts())) {
            String trainee = Component.translatable("ui.aerocouriers.row.deposit_trainee").getString();
            int tx = textRight - font.width(trainee);
            graphics.drawString(font, trainee, tx, line2Y + 4, TabletGui.TEXT_ACCENT, false);
        } else {
            String dep = Component.translatable("ui.aerocouriers.row.deposit", due).getString();
            int dx = textRight - font.width(dep);
            graphics.drawString(font, dep, dx, line2Y + 4, TabletGui.TEXT_RISK, false);
        }
    }

    private static void renderLockedReward(
            GuiGraphics graphics,
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            int textRight,
            int line1Y
    ) {
        int icon = 12;
        String need = BoardRankLabels.rankRequiredShort(entry.requiredRank()).getString();
        int blockW = icon + 2 + font.width(need);
        int bx = textRight - blockW;
        graphics.blitSprite(AerocouriersGuiSprites.ICON_LOCK, bx, line1Y, icon, icon);
        graphics.drawString(font, need, bx + icon + 2, line1Y + 2, TabletGui.TEXT_DISABLED, false);
    }

    private static int rewardColumnWidth(Font font, BoardSnapshotPayload.BoardEntry entry, boolean rankLocked) {
        if (rankLocked) {
            String need = BoardRankLabels.rankRequiredShort(entry.requiredRank()).getString();
            return 14 + font.width(need);
        }
        return Math.max(font.width("+" + entry.reward()), font.width("залог 9999"));
    }

    private static String routeText(BoardSnapshotPayload.BoardEntry entry) {
        String from = BoardEntryPickup.routeOriginLabel(entry).getString();
        String to = entry.destinationLabel();
        return from + " → " + to;
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
            int color
    ) {
        if (icon != null) {
            int iconW = BoardRowMetrics.LINE2_ICON;
            graphics.blitSprite(icon, x, y, iconW, iconW);
            x += iconW + BoardRowMetrics.ICON_TEXT_GAP;
        }
        graphics.drawString(font, value, x, y + Math.max(0, BoardRowMetrics.LINE2_ICON - 8), color, false);
        return x + font.width(value) + BoardRowMetrics.PAIR_GAP;
    }

    private static int drawBadge(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        FormattedCharSequence line = text.getVisualOrderText();
        int w = font.width(line);
        int h = font.lineHeight + BADGE_PAD_V * 2;
        graphics.fill(x, y - 1, x + w + BADGE_PAD_H * 2, y - 1 + h, 0x60000000);
        graphics.renderOutline(x, y - 1, w + BADGE_PAD_H * 2, h, color);
        graphics.drawString(font, line, x + BADGE_PAD_H, y + BADGE_PAD_V - 1, color, false);
        return x + w + BADGE_PAD_H * 2;
    }

    private static void drawClipped(GuiGraphics graphics, Font font, String raw, int x, int y, int width, int color) {
        String shown = BoardRowRenderer.clip(font, raw, width);
        graphics.drawString(font, shown, x, y, color, false);
    }

    public static boolean isUrgent(BoardSnapshotPayload.BoardEntry entry) {
        if (entry.deadlineMinutes() <= 0) {
            return false;
        }
        double ratio = entry.minutesLeft() / (double) entry.deadlineMinutes();
        return ratio < 0.2;
    }
}
