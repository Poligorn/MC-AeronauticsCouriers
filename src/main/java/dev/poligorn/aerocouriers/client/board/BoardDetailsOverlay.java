package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.MoneyText;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Order details over the dimmed board: cargo preview on the left, route and terms on the right (mockup 3). */
public final class BoardDetailsOverlay extends TabletOverlay {
    public static final int W = 540;
    public static final int H = 248;
    private static final int HEADER_H = 15;
    private static final int PREVIEW_X = 10;
    private static final int PREVIEW_Y = 22;
    private static final int PREVIEW_W = 180;
    private static final int PREVIEW_H = 177;
    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int LABEL_X = 202;
    private static final int VALUE_X = LABEL_X + 66;
    private static final int RIGHT_EDGE = W - 12;
    private static final int BUTTONS_Y = H - 42;
    private static final int MAP_X = 10;
    private static final int MAP_W = 150;
    private static final int BACK_W = 70;
    private static final int BACK_X = W - 10 - BACK_W;
    /** Wide enough for "Accept · collateral 100 000 Spurs" with currency-word tracking. */
    private static final int ACCEPT_W = 236;
    private static final int ACCEPT_X = BACK_X - 8 - ACCEPT_W;
    private static final int CLOSE_X = W - 17;
    private static final int CLOSE_Y = 2;
    private boolean suppressCargoItems;

    public void setSuppressCargoItems(boolean suppress) {
        suppressCargoItems = suppress;
    }

    private enum Action {
        ACCEPT,
        ABANDON,
        CANCEL,
        NONE
    }

    private final BoardOverlayHost host;
    private final UUID orderId;
    private boolean mapShown;
    private @Nullable ItemStack hoveredStack;
    private @Nullable List<Component> hoverLines;
    private int customerX;
    private int customerY;
    private int customerW;

    public BoardDetailsOverlay(BoardOverlayHost host, UUID orderId) {
        this.host = host;
        this.orderId = orderId;
    }

    public UUID orderId() {
        return orderId;
    }

    @Override
    protected int width() {
        return W;
    }

    @Override
    protected int height() {
        return H;
    }

    public @Nullable ItemStack hoveredStack() {
        return hoveredStack;
    }

    public @Nullable List<Component> hoverLines() {
        return hoverLines;
    }

    @Override
    protected void renderLocal(GuiGraphics graphics, int mx, int my, float partialTick) {
        hoveredStack = null;
        hoverLines = null;
        Optional<BoardSnapshotPayload.BoardEntry> found = host.entry(orderId);
        if (found.isEmpty()) {
            return;
        }
        BoardSnapshotPayload.BoardEntry e = found.get();
        Font font = host.font();
        TabletDraw.panel(graphics, 0, 0, W, H, HEADER_H);
        renderHeader(graphics, font, e, mx, my);
        renderPreview(graphics, font, e, mx, my);
        TabletDraw.vline(graphics, 196, 22, 198, TabletPalette.DIVIDER);
        renderTerms(graphics, font, e, mx, my);
        renderButtons(graphics, font, e, mx, my);
    }

    private void renderHeader(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int mx, int my) {
        boolean closeHover = TabletDraw.inside(mx, my, CLOSE_X, CLOSE_Y, 13, 12);
        TabletDraw.box(graphics, CLOSE_X, CLOSE_Y, CLOSE_X + 12, CLOSE_Y + 11,
                closeHover ? TabletPalette.hoverBrighten(TabletPalette.CLOSE_BG) : TabletPalette.CLOSE_BG,
                closeHover ? TabletPalette.TXT : TabletPalette.CORAL);
        for (int k = 0; k < 5; k++) {
            graphics.fill(CLOSE_X + 4 + k, CLOSE_Y + 2 + k + 1, CLOSE_X + 5 + k, CLOSE_Y + 3 + k + 1,
                    TabletPalette.CORAL);
            graphics.fill(CLOSE_X + 8 - k, CLOSE_Y + 2 + k + 1, CLOSE_X + 9 - k, CLOSE_Y + 3 + k + 1,
                    TabletPalette.CORAL);
        }
        String id = "#" + e.shortId();
        int idX = TabletDraw.textRight(graphics, font, id, CLOSE_X - 8, 4, TabletPalette.LOCKC, false);
        int routeRight = idX - 10;
        if (!host.offersAccept(e)) {
            String status = BoardCardRenderer.statusLabel(e);
            int color = BoardCardRenderer.statusColor(e);
            int chipX = idX - 8 - font.width(status) - 5;
            TabletDraw.box(graphics, chipX, 2, chipX + font.width(status) + 5, 12, BoardCardRenderer.dim(color), color);
            TabletDraw.text(graphics, font, status, chipX + 3, 3, color);
            routeRight = chipX - 8;
        }
        drawRoute(graphics, font, e, 10, 4, routeRight - 10, TabletPalette.TXT);
    }

    /** Bold «Откуда → Куда» with house/face glyphs, clipped to {@code maxW}. */
    static int drawRoute(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int x, int y, int maxW,
                         int color) {
        int limit = x + maxW;
        String from = BoardCardRenderer.originName(e);
        String to = BoardCardRenderer.destinationName(e);
        int half = Math.max(30, (maxW - 40) / 2);
        PixelGlyphs.draw(graphics, x, y + 1, BoardCardRenderer.originIsPlayer(e) ? PixelGlyphs.FACE : PixelGlyphs.HOUSE,
                color);
        x += 10;
        String fromShown = TabletDraw.trunc(font, from, Math.max(half, maxW - 40 - font.width(to)));
        TabletDraw.bold(graphics, font, fromShown, x, y, color);
        x += TabletDraw.boldWidth(font, fromShown) + 5;
        PixelGlyphs.draw(graphics, x, y + 2, PixelGlyphs.ARROW, color);
        x += 10;
        PixelGlyphs.draw(graphics, x, y + 1,
                BoardCardRenderer.destinationIsPlayer(e) ? PixelGlyphs.FACE : PixelGlyphs.HOUSE, color);
        x += 10;
        String toShown = TabletDraw.trunc(font, to, Math.max(16, limit - x));
        TabletDraw.bold(graphics, font, toShown, x, y, color);
        return x + TabletDraw.boldWidth(font, toShown);
    }

    private void renderPreview(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int mx, int my) {
        int px0 = PREVIEW_X;
        int py0 = PREVIEW_Y;
        TabletDraw.box(graphics, px0, py0, px0 + PREVIEW_W - 1, py0 + PREVIEW_H - 1, TabletPalette.PREVIEW_BG,
                TabletPalette.BRASS);
        TabletDraw.text(graphics, font, Component.translatable("ui.aerocouriers.details.cargo").getString(),
                px0 + 6, py0 + 4, TabletPalette.SEC);
        CargoSize size = parseSize(e.size());
        int chx = px0 + PREVIEW_W - 4;
        CargoSize[] order = {CargoSize.XL, CargoSize.L, CargoSize.M, CargoSize.S};
        for (CargoSize s : order) {
            String lab = s.name();
            int w = font.width(lab) + 7;
            chx -= w + 2;
            boolean sel = s == size;
            TabletDraw.box(graphics, chx, py0 + 4, chx + w - 1, py0 + 13, sel ? TabletPalette.CARD_SEL
                    : TabletPalette.PANEL_BG, sel ? TabletPalette.GOLD : TabletPalette.DIVIDER);
            TabletDraw.text(graphics, font, lab, chx + 4, py0 + 5, sel ? TabletPalette.GOLD : TabletPalette.LOCKC);
        }

        int cx = px0 + 6;
        int cy = py0 + 16;
        drawCrate(graphics, font, cx, cy, size.name());
        TabletDraw.text(graphics, font,
                Component.translatable("ui.aerocouriers.details.container", size.name()).getString(),
                cx + 32, cy + 1, TabletPalette.TXT);
        TabletDraw.text(graphics, font,
                TabletDraw.plural("ui.aerocouriers.details.slots", e.slots(), e.slots()).getString(),
                cx + 32, cy + 11, TabletPalette.SEC);

        int slots = Math.max(1, e.slots());
        int rows = (slots + COLS - 1) / COLS;
        int gx = px0 + 9;
        int gy = py0 + 42;
        List<ItemStack> stacks = e.cargoStacks();
        int usedSlots = Math.max(stacks.size(), e.manifest().size());
        for (int k = 0; k < rows * COLS; k++) {
            int sx = gx + (k % COLS) * SLOT;
            int sy = gy + (k / COLS) * SLOT;
            if (k >= slots) {
                break;
            }
            TabletDraw.box(graphics, sx, sy, sx + SLOT - 1, sy + SLOT - 1, TabletPalette.SLOT_BG,
                    TabletPalette.SLOT_BORDER);
            TabletDraw.hline(graphics, sx + 1, sx + SLOT - 2, sy + 1, TabletPalette.SLOT_SHADOW);
            TabletDraw.vline(graphics, sx + 1, sy + 1, sy + SLOT - 2, TabletPalette.SLOT_SHADOW);
            if (!suppressCargoItems && k < stacks.size() && !stacks.get(k).isEmpty()) {
                ItemStack stack = stacks.get(k);
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(font, stack, sx + 1, sy + 1, "");
                drawStackCount(graphics, font, stack, sx, sy);
                if (TabletDraw.inside(mx, my, sx, sy, SLOT, SLOT)) {
                    hoveredStack = stack;
                    graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x40FFFFFF);
                }
            }
        }
        int occY = gy + rows * SLOT + 4;
        TabletDraw.text(graphics, font,
                Component.translatable("ui.aerocouriers.details.occupied", usedSlots, slots).getString(),
                px0 + 6, occY, TabletPalette.SEC);
        int wy = occY + 14;
        TabletDraw.text(graphics, font, Component.translatable("ui.aerocouriers.details.weight").getString(),
                px0 + 6, wy, TabletPalette.SEC);
        String weight = Component.translatable("ui.aerocouriers.details.weight_value",
                TabletDraw.weight(e.weightValue()), TabletDraw.num(e.maxWeight())).getString();
        TabletDraw.textRight(graphics, font, weight, px0 + PREVIEW_W - 6, wy, TabletPalette.TXT, false);
        TabletDraw.box(graphics, px0 + 6, wy + 12, px0 + PREVIEW_W - 7, wy + 18, TabletPalette.SLOT_SHADOW,
                TabletPalette.BRASS_DIM);
        double fraction = e.maxWeight() <= 0 ? 0 : Math.min(1.0, e.weightValue() / e.maxWeight());
        int fw = (int) Math.round((PREVIEW_W - 14) * fraction);
        if (fw > 0) {
            graphics.fill(px0 + 7, wy + 13, px0 + 7 + fw, wy + 18, TabletPalette.SEC);
        }
        if (wy + 22 + 9 <= py0 + PREVIEW_H) {
            TabletDraw.text(graphics, font, Component.translatable("ui.aerocouriers.details.weight_hint").getString(),
                    px0 + 6, wy + 22, TabletPalette.LOCKC);
        }
    }

    /** Vanilla places the count one pixel too far right/down for an 18 px slot, so its shadow covers the border. */
    private static void drawStackCount(GuiGraphics graphics, Font font, ItemStack stack, int sx, int sy) {
        if (stack.getCount() <= 1) {
            return;
        }
        String count = String.valueOf(stack.getCount());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(font, count, sx + SLOT - 1 - font.width(count), sy + SLOT - 9, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private static void drawCrate(GuiGraphics graphics, Font font, int x, int y, String letter) {
        TabletDraw.box(graphics, x, y, x + 25, y + 19, 0xFF462A12, TabletPalette.BEVEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 25, y + 8, 0xFF6E461E);
        TabletDraw.hline(graphics, x + 1, x + 24, y + 8, TabletPalette.BEVEL_LIGHT);
        TabletDraw.box(graphics, x + 10, y + 6, x + 15, y + 10, 0xFF969AAA, 0xFF3C3E48);
        int lw = TabletDraw.boldWidth(font, letter);
        TabletDraw.bold(graphics, font, letter, x + 13 - lw / 2, y + 11, TabletPalette.GOLD);
    }

    private void renderTerms(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int mx, int my) {
        int ry = 22;
        ry = place(graphics, font, "ui.aerocouriers.details.from", BoardCardRenderer.originName(e),
                BoardCardRenderer.originIsPlayer(e), e.originStation(), e.originRevealed(), e.originPos(), ry);
        ry = place(graphics, font, "ui.aerocouriers.details.to", BoardCardRenderer.destinationName(e),
                BoardCardRenderer.destinationIsPlayer(e), e.destinationStation(), true, e.destinationPos(), ry);

        label(graphics, font, "ui.aerocouriers.details.distance", ry);
        String dist = Component.translatable("ui.aerocouriers.card.blocks", TabletDraw.num(e.distance())).getString();
        TabletDraw.text(graphics, font, dist, VALUE_X, ry, TabletPalette.TXT);
        int perX = VALUE_X + MoneyText.width(font, dist) + 4;
        String per = "· " + Component.translatable("ui.aerocouriers.details.per_1000",
                dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.rewardPer1000Blocks())).getString();
        TabletDraw.text(graphics, font, MoneyText.trunc(font, per, Math.max(8, RIGHT_EDGE - perX)), perX, ry,
                TabletPalette.SEC);
        ry += 13;
        ry = separator(graphics, ry);

        label(graphics, font, "ui.aerocouriers.details.reward", ry);
        TabletDraw.bold(graphics, font, "+" + dev.poligorn.aerocouriers.economy.MoneyFormat.text(e.reward()),
                VALUE_X, ry, TabletPalette.GOLD);
        ry += 13;

        if (!CollateralUi.hidden(e.collateral())) {
            ry = collateralRow(graphics, font, e, ry);
        }
        ry = separator(graphics, ry);

        label(graphics, font, "ui.aerocouriers.details.deadline", ry);
        String deadline = Component.translatable("ui.aerocouriers.details.minutes", e.deadlineMinutes()).getString();
        TabletDraw.text(graphics, font, deadline, VALUE_X, ry, TabletPalette.TXT);
        if (e.state().isActive()) {
            String left = "· " + Component.translatable("ui.aerocouriers.details.minutes_left", e.minutesLeft())
                    .getString();
            TabletDraw.text(graphics, font, left, VALUE_X + font.width(deadline) + 4, ry,
                    BoardCardRenderer.deadlineWarn(e) ? TabletPalette.CORAL : TabletPalette.SEC);
        }
        ry += 13;

        label(graphics, font, "ui.aerocouriers.details.rank", ry);
        String need = Component.translatable("ui.aerocouriers.details.rank_needed",
                BoardCardRenderer.roman(e.requiredRank())).getString();
        TabletDraw.text(graphics, font, need, VALUE_X, ry, TabletPalette.TXT);
        int rx = VALUE_X + font.width(need) + 6;
        boolean rankOk = host.viewerRank().isAdmin() || host.viewerRank().level() >= e.requiredRank().level();
        PixelGlyphs.draw(graphics, rx, ry + 1, rankOk ? PixelGlyphs.CHECK : PixelGlyphs.CROSS,
                rankOk ? TabletPalette.TEAL : TabletPalette.CORAL);
        TabletDraw.text(graphics, font, Component.translatable("ui.aerocouriers.details.rank_yours",
                BoardCardRenderer.roman(host.viewerRank())).getString(), rx + 10, ry,
                rankOk ? TabletPalette.TEAL : TabletPalette.CORAL);
        ry += 13;
        ry = separator(graphics, ry);

        label(graphics, font, "ui.aerocouriers.details.customer", ry);
        renderCustomer(graphics, font, e, ry, mx, my);
        ry += 13;

        label(graphics, font, "ui.aerocouriers.details.type", ry);
        String type = Component.translatable(e.villageCustomer()
                ? "ui.aerocouriers.details.type.village" : "ui.aerocouriers.details.type.player").getString();
        if (e.originRevealed()) {
            type = Component.translatable(e.originStation()
                    ? "ui.aerocouriers.details.kind.station" : "ui.aerocouriers.details.kind.point").getString()
                    + " · " + type;
        }
        TabletDraw.text(graphics, font, TabletDraw.trunc(font, type, RIGHT_EDGE - VALUE_X), VALUE_X, ry,
                TabletPalette.TXT);
        ry += 13;

        label(graphics, font, "ui.aerocouriers.details.created", ry);
        String created = e.ageMinutes() <= 0
                ? Component.translatable("ui.aerocouriers.details.created_now").getString()
                : Component.translatable("ui.aerocouriers.details.created_ago", e.ageMinutes()).getString();
        TabletDraw.text(graphics, font, created, VALUE_X, ry, TabletPalette.TXT);
    }

    private void renderCustomer(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int ry, int mx,
                                int my) {
        String name = TabletDraw.trunc(font, e.customerName(), 150);
        customerX = VALUE_X;
        customerY = ry - 1;
        customerW = 10 + font.width(name);
        boolean hover = TabletDraw.inside(mx, my, customerX, customerY, customerW, 11);
        if (e.villageCustomer()) {
            PixelGlyphs.draw(graphics, VALUE_X, ry, PixelGlyphs.HOUSE, TabletPalette.TXT);
            TabletDraw.text(graphics, font, name, VALUE_X + 10, ry, TabletPalette.TXT);
            String rep = "· " + Component.translatable("ui.aerocouriers.details.reputation",
                    e.customerStationReputation()).getString();
            TabletDraw.text(graphics, font, rep, VALUE_X + 14 + font.width(name), ry, TabletPalette.SEC);
            if (hover) {
                Component next = e.customerReputationNext() < 0
                        ? Component.translatable("ui.aerocouriers.reputation.max_tier")
                        : Component.translatable("ui.aerocouriers.row.reputation_next_inline",
                        e.customerReputationNext());
                hoverLines = List.of(Component.translatable("ui.aerocouriers.row.customer_tooltip_village",
                        e.customerName(), e.customerStationReputation(), e.customerReputationTier(),
                        e.customerReputationBonusPercent(), next));
            }
            return;
        }
        int color = hover ? TabletPalette.TXT : TabletPalette.BLUE;
        PixelGlyphs.draw(graphics, VALUE_X, ry, PixelGlyphs.FACE, color);
        TabletDraw.text(graphics, font, name, VALUE_X + 10, ry, color);
        TabletDraw.hline(graphics, VALUE_X + 10, VALUE_X + 10 + font.width(name) - 1, ry + 8, color);
        if (hover) {
            String tip = Component.translatable(host.customerOnline(e) ? "ui.aerocouriers.details.customer_profile"
                    : "ui.aerocouriers.details.customer_offline").getString();
            int tx = VALUE_X + 10 + font.width(name) + 8;
            int ty = ry - 2;
            TabletDraw.box(graphics, tx, ty, tx + font.width(tip) + 9, ty + 12, TabletPalette.TOOLTIP_BG,
                    TabletPalette.TOOLTIP_BORDER);
            TabletDraw.text(graphics, font, tip, tx + 5, ty + 3, TabletPalette.TXT);
        }
    }

    private int place(GuiGraphics graphics, Font font, String labelKey, String name, boolean player, boolean station,
                      boolean revealed, BlockPos pos, int ry) {
        label(graphics, font, labelKey, ry);
        PixelGlyphs.draw(graphics, VALUE_X, ry, player ? PixelGlyphs.FACE : PixelGlyphs.HOUSE, TabletPalette.TXT);
        String kind = "· " + Component.translatable(station
                ? "ui.aerocouriers.details.kind.station" : "ui.aerocouriers.details.kind.point").getString();
        int nameMax = RIGHT_EDGE - VALUE_X - 14 - font.width(kind);
        String shown = TabletDraw.trunc(font, name, nameMax);
        TabletDraw.text(graphics, font, shown, VALUE_X + 10, ry, TabletPalette.TXT);
        if (revealed) {
            TabletDraw.text(graphics, font, kind, VALUE_X + 14 + font.width(shown), ry, TabletPalette.SEC);
        }
        ry += 11;
        String coords = revealed
                ? Component.translatable("ui.aerocouriers.details.coords", TabletDraw.num(pos.getX()),
                TabletDraw.num(pos.getZ())).getString()
                : Component.translatable("ui.aerocouriers.details.coords_hidden").getString();
        TabletDraw.text(graphics, font, coords, VALUE_X + 10, ry, TabletPalette.LOCKC);
        return ry + 13;
    }

    private static void label(GuiGraphics graphics, Font font, String key, int ry) {
        TabletDraw.text(graphics, font, Component.translatable(key).getString(), LABEL_X, ry, TabletPalette.SEC);
    }

    private static int separator(GuiGraphics graphics, int ry) {
        TabletDraw.hline(graphics, LABEL_X, RIGHT_EDGE, ry - 2, TabletPalette.DIVIDER_SOFT);
        return ry + 3;
    }

    private Action action(BoardSnapshotPayload.BoardEntry e) {
        if (host.offersAccept(e)) {
            return Action.ACCEPT;
        }
        if ("courier".equals(e.role()) && e.state().isActive()) {
            return Action.ABANDON;
        }
        if ("issuer".equals(e.role()) && e.state() == ContractState.OPEN) {
            return Action.CANCEL;
        }
        return Action.NONE;
    }

    private String mapCaption(BoardSnapshotPayload.BoardEntry e, boolean mapOk) {
        if (action(e) == Action.ABANDON) {
            String key = !e.cargoPickedUp() ? "ui.aerocouriers.details.nav_pickup"
                    : "ui.aerocouriers.details.nav_delivery";
            return Component.translatable(key).getString();
        }
        return !mapOk
                ? Component.translatable("ui.aerocouriers.details.map_needs_xaero").getString()
                : mapShown ? Component.translatable("ui.aerocouriers.details.map_shown").getString()
                : Component.translatable("ui.aerocouriers.details.map_hint").getString();
    }

    private void renderButtons(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int mx, int my) {
        int by = BUTTONS_Y;
        TabletDraw.hline(graphics, 10, W - 11, by - 6, TabletPalette.DIVIDER);
        boolean mapOk = host.mapAvailable(e);
        TabletDraw.button(graphics, font, MAP_X, by, MAP_W,
                Component.translatable("ui.aerocouriers.details.show_on_map").getString(),
                mapOk ? TabletDraw.ButtonKind.MAP : TabletDraw.ButtonKind.DISABLED, PixelGlyphs.PIN,
                mapOk && TabletDraw.inside(mx, my, MAP_X, by, MAP_W, TabletDraw.BUTTON_H));
        int mapCaptionColor = mapShown ? TabletPalette.TEAL
                : action(e) == Action.ABANDON ? BoardCardRenderer.statusColor(e) : TabletPalette.LOCKC;
        TabletDraw.text(graphics, font, TabletDraw.trunc(font, mapCaption(e, mapOk), ACCEPT_X - MAP_X - 8), MAP_X,
                by + 23, mapCaptionColor);

        TabletDraw.button(graphics, font, BACK_X, by, BACK_W,
                Component.translatable("ui.aerocouriers.details.back").getString(), TabletDraw.ButtonKind.SECONDARY,
                null, TabletDraw.inside(mx, my, BACK_X, by, BACK_W, TabletDraw.BUTTON_H));

        Action action = action(e);
        if (action == Action.NONE) {
            return;
        }
        if (action != Action.ACCEPT) {
            String label = Component.translatable(action == Action.ABANDON ? "ui.aerocouriers.abandon"
                    : "ui.aerocouriers.cancel_confirm.button").getString();
            TabletDraw.button(graphics, font, ACCEPT_X, by, ACCEPT_W, label, TabletDraw.ButtonKind.DANGER, null,
                    TabletDraw.inside(mx, my, ACCEPT_X, by, ACCEPT_W, TabletDraw.BUTTON_H));
            String caption = Component.translatable("ui.aerocouriers.details.accept_hint").getString();
            TabletDraw.text(graphics, font, caption, ACCEPT_X + ACCEPT_W / 2 - font.width(caption) / 2, by + 23,
                    TabletPalette.LOCKC);
            return;
        }
        Optional<Component> denial = host.acceptDenial(e);
        boolean enabled = denial.isEmpty();
        TabletDraw.button(graphics, font, ACCEPT_X, by, ACCEPT_W, acceptLabel(e),
                enabled ? TabletDraw.ButtonKind.PRIMARY : TabletDraw.ButtonKind.DISABLED, null,
                enabled && TabletDraw.inside(mx, my, ACCEPT_X, by, ACCEPT_W, TabletDraw.BUTTON_H));
        String caption = denial.map(Component::getString)
                .orElseGet(() -> Component.translatable("ui.aerocouriers.details.accept_hint").getString());
        int capW = BACK_X + BACK_W - ACCEPT_X;
        String shown = TabletDraw.trunc(font, caption, capW + 40);
        int cx = Math.min(ACCEPT_X + ACCEPT_W / 2 - font.width(shown) / 2, W - 10 - font.width(shown));
        TabletDraw.text(graphics, font, shown, cx, by + 23, enabled ? TabletPalette.LOCKC : TabletPalette.CORAL);
        if (!enabled && !shown.equals(caption) && TabletDraw.inside(mx, my, cx, by + 22, font.width(shown), 10)) {
            hoverLines = List.of(Component.literal(caption));
        }
    }

    private int collateralRow(GuiGraphics graphics, Font font, BoardSnapshotPayload.BoardEntry e, int ry) {
        label(graphics, font, "ui.aerocouriers.details.collateral", ry);
        long due = host.collateralDue(e);
        boolean trainee = host.traineeFree(e);
        int color = due > 0 ? TabletPalette.CORAL : TabletPalette.TEAL;
        String amount = dev.poligorn.aerocouriers.economy.MoneyFormat.text(due);
        TabletDraw.bold(graphics, font, amount, VALUE_X, ry, color);
        String note = !e.state().equals(ContractState.OPEN)
                ? Component.translatable("ui.aerocouriers.details.collateral_held").getString()
                : trainee ? Component.translatable("ui.aerocouriers.details.collateral_trainee").getString()
                : due <= 0 ? Component.translatable("ui.aerocouriers.card.collateral_none").getString()
                : Component.translatable("ui.aerocouriers.details.collateral_note").getString();
        int noteX = VALUE_X + TabletDraw.boldWidth(font, amount) + 5;
        TabletDraw.text(graphics, font, TabletDraw.trunc(font, note, RIGHT_EDGE - noteX), noteX, ry, color);
        return ry + 13;
    }

    private String acceptLabel(BoardSnapshotPayload.BoardEntry e) {
        long due = host.collateralDue(e);
        if (CollateralUi.hidden(e.collateral())) {
            return Component.translatable("ui.aerocouriers.details.accept_plain").getString();
        }
        return due > 0
                ? Component.translatable("ui.aerocouriers.details.accept",
                        dev.poligorn.aerocouriers.economy.MoneyFormat.text(due)).getString()
                : Component.translatable("ui.aerocouriers.details.accept_free").getString();
    }

    @Override
    protected boolean clickLocal(int mx, int my, int button) {
        Optional<BoardSnapshotPayload.BoardEntry> found = host.entry(orderId);
        if (found.isEmpty()) {
            host.closeDetails();
            return true;
        }
        BoardSnapshotPayload.BoardEntry e = found.get();
        if (button != 0) {
            return true;
        }
        if (TabletDraw.inside(mx, my, CLOSE_X, CLOSE_Y, 13, 12)
                || TabletDraw.inside(mx, my, BACK_X, BUTTONS_Y, BACK_W, TabletDraw.BUTTON_H)) {
            click();
            host.closeDetails();
            return true;
        }
        if (TabletDraw.inside(mx, my, MAP_X, BUTTONS_Y, MAP_W, TabletDraw.BUTTON_H) && host.mapAvailable(e)) {
            click();
            host.showOnMap(e);
            mapShown = true;
            return true;
        }
        boolean onAction = TabletDraw.inside(mx, my, ACCEPT_X, BUTTONS_Y, ACCEPT_W, TabletDraw.BUTTON_H);
        Action action = action(e);
        if (onAction && action == Action.ACCEPT && host.acceptDenial(e).isEmpty()) {
            click();
            host.openAcceptConfirm(e);
            return true;
        }
        if (onAction && (action == Action.ABANDON || action == Action.CANCEL)) {
            click();
            host.openActionConfirm(e, action == Action.ABANDON
                    ? ContractIntentPayload.Kind.ABANDON : ContractIntentPayload.Kind.CANCEL);
            return true;
        }
        if (!e.villageCustomer() && e.customerId().isPresent()
                && TabletDraw.inside(mx, my, customerX, customerY, customerW, 11)) {
            click();
            host.openCustomerProfile(e);
            return true;
        }
        return true;
    }

    private static CargoSize parseSize(String raw) {
        try {
            return CargoSize.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CargoSize.M;
        }
    }
}
