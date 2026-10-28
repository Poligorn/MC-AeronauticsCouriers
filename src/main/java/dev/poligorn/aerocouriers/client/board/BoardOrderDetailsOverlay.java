package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.AerocouriersGuiSprites;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.TabletMockupPalette;
import dev.poligorn.aerocouriers.client.TabletMockupUi;
import dev.poligorn.aerocouriers.client.XaeroOrderBridge;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Order details overlay — layout from mockup panel3() (540×248 GUI-px). */
public final class BoardOrderDetailsOverlay {
    private static final int MOCK_W = 540;
    private static final int MOCK_H = 248;
    private static final int BTN_H = 20;
    private static final int ROW_ADV = 13;

    private final Host host;
    private final BoardSnapshotPayload.BoardEntry entry;
    private final BoardSnapshotPayload.BoardProfile profile;
    private final long snapshotContractTick;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int valueColX;
    private int closeX;
    private int closeY;
    private int closeW;
    private int closeH;
    private int mapBtnX;
    private int mapBtnY;
    private int mapBtnW;
    private int acceptBtnX;
    private int acceptBtnY;
    private int acceptBtnW;
    private int backBtnX;
    private int backBtnY;
    private int backBtnW;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private @Nullable Integer hoverCustomerX;
    private @Nullable Integer hoverCustomerY;

    public interface Host {
        Font font();

        void addOverlayWidget(AbstractWidget widget);

        void removeOverlayWidgets(List<AbstractWidget> widgets);

        void closeOverlay();

        void openAccept(BoardSnapshotPayload.BoardEntry entry);
    }

    public BoardOrderDetailsOverlay(
            Host host,
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            long balance,
            long snapshotContractTick
    ) {
        this.host = host;
        this.entry = entry;
        this.profile = profile;
        this.snapshotContractTick = snapshotContractTick;
    }

    public List<AbstractWidget> widgets() {
        return widgets;
    }

    public void init(int cavityX, int cavityY, int cavityW, int cavityH) {
        widgets.clear();
        panelW = Math.min(MOCK_W, cavityW - 16);
        panelH = Math.min(MOCK_H, cavityH - 16);
        panelX = cavityX + (cavityW - panelW) / 2;
        panelY = cavityY + (cavityH - panelH) / 2;
        layoutButtons();
    }

    public void dispose() {
        host.removeOverlayWidgets(widgets);
        widgets.clear();
    }

    public boolean clickCustomer(int mouseX, int mouseY) {
        if (entry.villageCustomer() || hoverCustomerX == null || hoverCustomerY == null) {
            return false;
        }
        int nx = hoverCustomerX;
        int ny = hoverCustomerY;
        Font font = host.font();
        int w = font.width(entry.customerName()) + 4;
        if (mouseX >= nx && mouseX < nx + w && mouseY >= ny && mouseY < ny + font.lineHeight) {
            entry.customerId().ifPresent(ClientBoard::requestPlayerProfile);
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (mx >= closeX && mx < closeX + closeW && my >= closeY && my < closeY + closeH) {
            host.closeOverlay();
            return true;
        }
        if (ModList.get().isLoaded("xaerominimap")
                && mx >= mapBtnX && mx < mapBtnX + mapBtnW && my >= mapBtnY && my < mapBtnY + BTN_H) {
            showOnMap();
            return true;
        }
        if (mx >= acceptBtnX && mx < acceptBtnX + acceptBtnW && my >= acceptBtnY && my < acceptBtnY + BTN_H) {
            host.openAccept(entry);
            return true;
        }
        if (mx >= backBtnX && mx < backBtnX + backBtnW && my >= backBtnY && my < backBtnY + BTN_H) {
            host.closeOverlay();
            return true;
        }
        return clickCustomer(mx, my);
    }

    public void render(GuiGraphics graphics, int cavityX, int cavityY, int cavityW, int cavityH, int mouseX, int mouseY) {
        panelW = Math.min(MOCK_W, cavityW - 16);
        panelH = Math.min(MOCK_H, cavityH - 16);
        panelX = cavityX + (cavityW - panelW) / 2;
        panelY = cavityY + (cavityH - panelH) / 2;
        layoutButtons();

        graphics.fill(cavityX, cavityY, cavityX + cavityW, cavityY + cavityH, 0xB0000000);
        TabletMockupUi.drawBevelPanel(graphics, panelX, panelY, panelW, panelH);

        Font font = host.font();
        float sx = panelW / (float) MOCK_W;
        float sy = panelH / (float) MOCK_H;

        int hx0 = panelX + Math.round(1 * sx);
        int hy0 = panelY + Math.round(1 * sy);
        int hx1 = panelX + panelW - Math.round(2 * sx);
        int hy1 = panelY + Math.round(15 * sy);
        graphics.fill(hx0, hy0, hx1, hy1, TabletMockupPalette.HEADER_BG);

        int tx = panelX + Math.round(10 * sx);
        int ty = panelY + Math.round(4 * sy);
        drawRouteHeader(graphics, font, tx, ty, sx);

        String id = "#" + entry.shortId();
        this.closeX = panelX + panelW - Math.round(17 * sx);
        this.closeY = panelY + Math.round(2 * sy);
        int idX = this.closeX - Math.round(8 * sx) - font.width(id);
        graphics.drawString(font, id, Math.max(panelX + 10, idX), ty, TabletMockupPalette.LOCKC, false);
        closeW = Math.max(12, Math.round(12 * sx));
        closeH = Math.max(11, Math.round(11 * sy));
        drawCloseButton(graphics, this.closeX, this.closeY, sx, sy);

        int px0 = panelX + Math.round(10 * sx);
        int py0 = panelY + Math.round(22 * sy);
        int pw = Math.round(180 * sx);
        int ph = Math.round(176 * sy);
        drawCargoPanel(graphics, font, px0, py0, pw, ph, sx, sy);

        int divX = panelX + Math.round(196 * sx);
        graphics.vLine(divX, panelY + Math.round(22 * sy), panelY + Math.round(198 * sy), TabletMockupPalette.SEP);

        int lx = panelX + Math.round(202 * sx);
        valueColX = lx + labelValueOffset(font);
        int ry = panelY + Math.round(22 * sy);
        ry = drawDetailsTable(graphics, font, lx, valueColX, ry, sx, mouseX, mouseY);

        int by = panelY + panelH - Math.round(42 * sy);
        graphics.hLine(panelX + Math.round(10 * sx), panelX + panelW - Math.round(11 * sx),
                by - Math.round(6 * sy), TabletMockupPalette.SEP);

        long due = collateralDue();
        String mapLabel = Component.translatable("ui.aerocouriers.details.map").getString();
        String acceptLabel = Component.translatable("ui.aerocouriers.accept.overlay.action", due).getString();
        String backLabel = Component.translatable("ui.aerocouriers.details.back").getString();
        boolean mapEnabled = ModList.get().isLoaded("xaerominimap");
        if (mapEnabled) {
            TabletMockupUi.drawBtn3(graphics, font, mapBtnX, mapBtnY, mapBtnW, mapLabel, TabletMockupUi.Btn3Kind.MAP);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.details.map_hint"), mapBtnX, by + 23,
                    TabletMockupPalette.LOCKC, false);
        }
        TabletMockupUi.drawBtn3(graphics, font, acceptBtnX, acceptBtnY, acceptBtnW, acceptLabel,
                TabletMockupUi.Btn3Kind.PRIMARY);
        TabletMockupUi.drawBtn3(graphics, font, backBtnX, backBtnY, backBtnW, backLabel, TabletMockupUi.Btn3Kind.BACK);
        String acceptHint = Component.translatable("ui.aerocouriers.details.accept_hint").getString();
        int hintX = acceptBtnX + (acceptBtnW - font.width(acceptHint)) / 2;
        graphics.drawString(font, acceptHint, hintX, by + 23, TabletMockupPalette.LOCKC, false);
    }

    private void layoutButtons() {
        float sx = panelW / (float) MOCK_W;
        float sy = panelH / (float) MOCK_H;
        int by = panelY + panelH - Math.round(42 * sy);
        backBtnW = Math.round(70 * sx);
        acceptBtnW = Math.round(160 * sx);
        backBtnX = panelX + panelW - Math.round(10 * sx) - backBtnW;
        acceptBtnX = backBtnX - Math.round(8 * sx) - acceptBtnW;
        mapBtnW = Math.round(150 * sx);
        mapBtnX = panelX + Math.round(10 * sx);
        mapBtnY = acceptBtnY = backBtnY = by;
        closeX = panelX + panelW - Math.round(17 * sx);
        closeY = panelY + Math.round(2 * sy);
        host.removeOverlayWidgets(widgets);
        widgets.clear();
    }

    private void drawRouteHeader(GuiGraphics g, Font font, int tx, int ty, float sx) {
        String from = BoardEntryPickup.routeOriginLabel(entry).getString();
        String to = entry.destinationLabel();
        g.blitSprite(AerocouriersGuiSprites.ICON_VILLAGE, tx, ty + 1, 8, 8);
        tx += Math.round(10 * sx);
        int fromColor = entry.originRevealed() || !from.isEmpty() ? TabletMockupPalette.TXT : TabletMockupPalette.LOCKC;
        TabletMockupUi.drawBold(g, font, TabletMockupUi.clip(font, from, Math.round(120 * sx)), tx, ty, fromColor);
        tx += font.width(TabletMockupUi.clip(font, from, Math.round(120 * sx))) + Math.round(6 * sx);
        g.drawString(font, "→", tx, ty, TabletMockupPalette.TXT, false);
        tx += font.width("→") + Math.round(4 * sx);
        g.blitSprite(AerocouriersGuiSprites.ICON_VILLAGE, tx, ty + 1, 8, 8);
        tx += Math.round(10 * sx);
        TabletMockupUi.drawBold(g, font, TabletMockupUi.clip(font, to, Math.round(140 * sx)), tx, ty,
                TabletMockupPalette.TXT);
    }

    private void drawCloseButton(GuiGraphics g, int x, int y, float sx, float sy) {
        int w = Math.max(12, Math.round(12 * sx));
        int h = Math.max(11, Math.round(11 * sy));
        g.fill(x, y, x + w, y + h, rgb(52, 24, 16));
        g.renderOutline(x, y, w, h, TabletMockupPalette.CORAL);
        int cx = x + w / 2;
        int cy = y + h / 2;
        for (int i = -2; i <= 2; i++) {
            g.fill(cx + i, cy + i - 2, cx + i + 1, cy + i - 1, TabletMockupPalette.CORAL);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, TabletMockupPalette.CORAL);
        }
    }

    private void drawCargoPanel(GuiGraphics g, Font font, int px0, int py0, int pw, int ph, float sx, float sy) {
        g.fill(px0, py0, px0 + pw, py0 + ph, TabletMockupPalette.PREVIEW_BG);
        g.renderOutline(px0, py0, pw, ph, TabletMockupPalette.BRASS);
        g.drawString(font, Component.translatable("ui.aerocouriers.row.cargo_heading"), px0 + 6, py0 + 4,
                TabletMockupPalette.SEC, false);

        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        drawSizeChips(g, font, px0 + pw - 4, py0 + 4, size, sx);

        int cx = px0 + 6;
        int cy = py0 + 16;
        g.fill(cx, cy, cx + 26, cy + 20, rgb(70, 42, 18));
        g.renderOutline(cx, cy, 26, 20, rgb(190, 140, 60));
        g.fill(cx + 1, cy + 1, cx + 25, cy + 8, rgb(110, 70, 30));
        g.hLine(cx + 1, cx + 24, cy + 8, rgb(190, 140, 60));
        TabletMockupUi.drawBold(g, font, size.name(), cx + 10, cy + 11, TabletMockupPalette.GOLD);
        g.drawString(font, Component.translatable("ui.aerocouriers.details.container_name", size.name()), cx + 32,
                cy + 1, TabletMockupPalette.TXT, false);
        g.drawString(font, Component.translatable("ui.aerocouriers.details.slots_label", size.slots()), cx + 32,
                cy + 11, TabletMockupPalette.SEC, false);

        int gx = px0 + 9;
        int gy = py0 + 42;
        List<ItemStack> stacks = entry.cargoStacks();
        int slots = size.slots();
        int rows = (slots + 8) / 9;
        for (int i = 0; i < slots; i++) {
            int col = i % 9;
            int row = i / 9;
            if (row >= 3) {
                break;
            }
            ItemStack stack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            TabletMockupUi.drawSlot(g, font, gx + col * TabletMockupUi.SLOT, gy + row * TabletMockupUi.SLOT, stack);
        }
        int used = (int) stacks.stream().filter(s -> !s.isEmpty()).count();
        g.drawString(font, Component.translatable("ui.aerocouriers.details.slots_used", used, slots), px0 + 6,
                gy + Math.min(3, rows) * TabletMockupUi.SLOT + 2, TabletMockupPalette.SEC, false);

        int wy = gy + Math.min(3, rows) * TabletMockupUi.SLOT + 14;
        drawWeight(g, font, px0 + 6, wy, pw - 12, entry.weight(), size);
    }

    private void drawSizeChips(GuiGraphics g, Font font, int right, int y, CargoSize selected, float sx) {
        int x = right;
        for (CargoSize size : new CargoSize[] {CargoSize.XL, CargoSize.L, CargoSize.M, CargoSize.S}) {
            String lab = size.name();
            int w = font.width(lab) + 7;
            x -= w + 2;
            boolean on = size == selected;
            g.fill(x, y, x + w, y + 10, on ? TabletMockupPalette.CARDSEL : rgb(30, 16, 8));
            g.renderOutline(x, y, w, 10, on ? TabletMockupPalette.GOLD : rgb(90, 56, 26));
            g.drawString(font, lab, x + 3, y + 1, on ? TabletMockupPalette.GOLD : TabletMockupPalette.LOCKC, false);
        }
    }

    private int drawDetailsTable(GuiGraphics g, Font font, int lx, int vx, int ry, float sx, int mouseX, int mouseY) {
        hoverCustomerX = null;
        hoverCustomerY = null;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.from"));
        drawPlaceLine(g, font, vx, ry, entry.originLabel(), entry.villageCustomer(), entry.originRevealed());
        ry += 11;
        g.drawString(font, formatCoords(entry.originPos(), entry.originRevealed()), vx + 10, ry,
                TabletMockupPalette.LOCKC, false);
        ry += ROW_ADV;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.to"));
        drawPlaceLine(g, font, vx, ry, entry.destinationLabel(), true, true);
        ry += 11;
        g.drawString(font, formatCoords(entry.destinationPos(), true), vx + 10, ry, TabletMockupPalette.LOCKC, false);
        ry += ROW_ADV;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.distance"));
        long eff = BoardRewardEfficiency.perThousandBlocks(entry);
        String dist = Component.translatable("ui.aerocouriers.row.distance_blocks", entry.distance()).getString();
        g.drawString(font, dist, vx, ry, TabletMockupPalette.TXT, false);
        g.drawString(font, " · " + eff + " сп/1000бл", vx + font.width(dist), ry, TabletMockupPalette.SEC, false);
        ry += ROW_ADV;
        TabletMockupUi.sep(g, lx, panelX + panelW - Math.round(12 * sx), ry - 2);
        ry += 3;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.accept.confirm.reward.label"));
        TabletMockupUi.drawBold(g, font, "+" + entry.reward(), vx, ry, TabletMockupPalette.GOLD);
        ry += ROW_ADV;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.deposit"));
        long due = collateralDue();
        String dueStr = Long.toString(due);
        TabletMockupUi.drawBold(g, font, dueStr, vx, ry, TabletMockupPalette.CORAL);
        String depHint = Component.translatable("ui.aerocouriers.details.deposit_hint").getString();
        g.drawString(font, depHint, vx + font.width(dueStr) + 5, ry, TabletMockupPalette.CORAL, false);
        ry += ROW_ADV;
        TabletMockupUi.sep(g, lx, panelX + panelW - Math.round(12 * sx), ry - 2);
        ry += 3;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.deadline"));
        g.drawString(font, Component.translatable("ui.aerocouriers.row.time_compact", entry.deadlineMinutes()).getString(),
                vx, ry, TabletMockupPalette.TXT, false);
        ry += ROW_ADV;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.rank"));
        String need = Component.translatable("ui.aerocouriers.details.rank_need_short",
                entry.requiredRank().displayName()).getString();
        g.drawString(font, need, vx, ry, TabletMockupPalette.TXT, false);
        if (RankRules.satisfies(profile.rank(), entry.requiredRank())) {
            int nx = vx + font.width(need) + 6;
            g.drawString(font, Component.translatable("ui.aerocouriers.details.rank_ok", profile.rank().displayName())
                    .getString(), nx, ry, TabletMockupPalette.TEAL, false);
        }
        ry += ROW_ADV;
        TabletMockupUi.sep(g, lx, panelX + panelW - Math.round(12 * sx), ry - 2);
        ry += 3;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.customer"));
        if (entry.villageCustomer()) {
            g.drawString(font, entry.customerName(), vx, ry, TabletMockupPalette.TXT, false);
        } else {
            BoardCustomerFace.draw(g, vx, ry, entry.customerId());
            hoverCustomerX = vx + 10;
            hoverCustomerY = ry;
            g.drawString(font, entry.customerName(), hoverCustomerX, hoverCustomerY, TabletMockupPalette.BLUE, false);
            g.hLine(hoverCustomerX, hoverCustomerX + font.width(entry.customerName()) - 1,
                    hoverCustomerY + font.lineHeight - 2, TabletMockupPalette.BLUE);
        }
        ry += ROW_ADV;

        drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.type"));
        Component type = entry.villageCustomer()
                ? Component.translatable("ui.aerocouriers.details.type.village")
                : Component.translatable("ui.aerocouriers.details.type.player");
        g.drawString(font, type, vx, ry, TabletMockupPalette.TXT, false);
        ry += ROW_ADV;

        if (entry.createdAt() > 0 && snapshotContractTick > entry.createdAt()) {
            drawLabelRow(g, font, lx, vx, ry, Component.translatable("ui.aerocouriers.details.created"));
            long minutesAgo = Math.max(1, (snapshotContractTick - entry.createdAt()) / Pricing.TICKS_PER_MINUTE);
            g.drawString(font, Component.translatable("ui.aerocouriers.details.created_ago", minutesAgo), vx, ry,
                    TabletMockupPalette.TXT, false);
            ry += ROW_ADV;
        }
        return ry;
    }

    private static void drawLabelRow(GuiGraphics g, Font font, int lx, int vx, int ry, Component label) {
        g.drawString(font, label, lx, ry, TabletMockupPalette.SEC, false);
    }

    private void drawPlaceLine(GuiGraphics g, Font font, int vx, int y, String name, boolean village,
                               boolean revealed) {
        if (!revealed && name.isEmpty()) {
            g.drawString(font, Component.translatable("ui.aerocouriers.row.pickup_hidden"), vx, y,
                    TabletMockupPalette.LOCKC, false);
            return;
        }
        g.blitSprite(AerocouriersGuiSprites.ICON_VILLAGE, vx, y, 8, 8);
        g.drawString(font, name, vx + 10, y, TabletMockupPalette.TXT, false);
        String kind = village
                ? Component.translatable("ui.aerocouriers.details.origin.station").getString()
                : Component.translatable("ui.aerocouriers.details.xaero_point").getString();
        g.drawString(font, " · " + kind, vx + 10 + font.width(name) + 4, y, TabletMockupPalette.SEC, false);
    }

    private static String formatCoords(BlockPos pos, boolean revealed) {
        if (!revealed || pos.equals(BlockPos.ZERO)) {
            return "";
        }
        return "X " + pos.getX() + " · Z " + pos.getZ();
    }

    private static void drawWeight(GuiGraphics g, Font font, int x, int y, int barW, String weightField, CargoSize size) {
        double current = parseWeight(weightField);
        int max = parseMaxWeight(weightField, size.maxWeight());
        String label = String.format(Locale.ROOT, "%.1f / %d кг", current, max);
        g.drawString(font, Component.translatable("ui.aerocouriers.details.weight"), x, y, TabletMockupPalette.SEC,
                false);
        g.drawString(font, label, x + barW - font.width(label), y, TabletMockupPalette.TXT, false);
        y += font.lineHeight + 2;
        g.fill(x, y, x + barW, y + 6, rgb(10, 5, 2));
        g.renderOutline(x, y, barW, 6, rgb(110, 70, 30));
        float ratio = max > 0 ? (float) Math.min(1, current / max) : 0;
        int fillW = Math.max(1, Math.round((barW - 2) * ratio));
        g.fill(x + 1, y + 1, x + 1 + fillW, y + 5, TabletMockupPalette.SEC);
        y += 8;
        g.drawString(font, Component.translatable("ui.aerocouriers.details.weight_hint"), x, y, TabletMockupPalette.LOCKC,
                false);
    }

    private static double parseWeight(String field) {
        if (field == null || field.isBlank()) {
            return 0;
        }
        String normalized = field.replace(',', '.');
        int slash = normalized.indexOf('/');
        String head = slash > 0 ? normalized.substring(0, slash).trim() : normalized.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(head);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int parseMaxWeight(String field, int fallback) {
        if (field == null) {
            return fallback;
        }
        int slash = field.indexOf('/');
        if (slash < 0) {
            return fallback;
        }
        String tail = field.substring(slash + 1).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+)").matcher(tail);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int labelValueOffset(Font font) {
        String[] labels = {"Откуда", "Куда", "Дистанция", "Награда", "Залог", "Срок", "Ранг", "Заказчик", "Тип", "Создан"};
        int max = 0;
        for (String l : labels) {
            max = Math.max(max, font.width(l));
        }
        return max + 6;
    }

    private void showOnMap() {
        XaeroOrderBridge.onPreviewDestination(
                entry.id(), entry.destinationDimension(), entry.destinationPos(), entry.shortId());
        XaeroOrderBridge.openWorldMapAt(entry.destinationPos());
    }

    private long collateralDue() {
        return TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                profile.villageDelivered(), profile.traineeFreeContracts());
    }

    private static int rgb(int r, int g, int b) {
        return TabletMockupPalette.rgb(r, g, b);
    }
}
