package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.TabletGui;
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
    private static final int REWARD_EFF_GAP = 8;

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

        int rewardColW = rewardColumnWidth(font, entry, profile, rankLocked);
        String effText = BoardRewardEfficiency.perThousandBlocks(entry) + "/1000";
        int effW = font.width(effText);
        int effColRight = textRight - rewardColW - REWARD_EFF_GAP;
        int effX = effColRight - effW;

        int routeRight = textRight - rewardColW - 4;
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
            graphics.drawString(font, effText, effX, line2Y + 4, muted, false);
        }

        x = textLeft;
        int statsMaxX = Math.max(x + 40, effX - 6);
        String distance = Component.translatable("ui.aerocouriers.row.distance_blocks", entry.distance()).getString();
        x = drawStat(graphics, font, null, distance, x, line2Y, muted, statsMaxX);

        CargoSize cargoSize = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        String cargo = cargoSize.name() + " " + weightShort(entry.weight());
        x = drawStat(graphics, font, AerocouriersGuiSprites.ICON_CRATE, cargo, x, line2Y, muted, statsMaxX);

        String time = Component.translatable("ui.aerocouriers.row.time_compact", entry.minutesLeft()).getString();
        drawStat(graphics, font, AerocouriersGuiSprites.ICON_CLOCK, time, x, line2Y, muted, statsMaxX);
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

    private static int rewardColumnWidth(
            Font font,
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            boolean rankLocked
    ) {
        if (rankLocked) {
            String need = BoardRankLabels.rankRequiredShort(entry.requiredRank()).getString();
            return 14 + font.width(need);
        }
        String reward = "+" + entry.reward();
        long due = TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                profile.villageDelivered(), profile.traineeFreeContracts());
        String deposit;
        if (due <= 0 && entry.villageCustomer()
                && TraineeRules.onTraineeVillageContracts(profile.villageDelivered(), profile.traineeFreeContracts())) {
            deposit = Component.translatable("ui.aerocouriers.row.deposit_trainee").getString();
        } else {
            deposit = Component.translatable("ui.aerocouriers.row.deposit", due).getString();
        }
        return Math.max(font.width(reward), font.width(deposit));
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
            int color,
            int maxX
    ) {
        if (x >= maxX) {
            return x;
        }
        if (icon != null) {
            int iconW = BoardRowMetrics.LINE2_ICON;
            graphics.blitSprite(icon, x, y, iconW, iconW);
            x += iconW + BoardRowMetrics.ICON_TEXT_GAP;
        }
        int valueW = font.width(value);
        if (x + valueW > maxX) {
            value = font.plainSubstrByWidth(value, Math.max(4, maxX - x - font.width("…"))) + "…";
            valueW = font.width(value);
        }
        graphics.drawString(font, value, x, y + Math.max(0, BoardRowMetrics.LINE2_ICON - 8), color, false);
        return x + valueW + BoardRowMetrics.PAIR_GAP;
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
