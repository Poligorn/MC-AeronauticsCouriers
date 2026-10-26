package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import net.minecraft.client.gui.components.Renderable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Monitoring board: open orders and the viewer's own. Creation is the stamp item, not a tab.
 * Chrome: brass order-tablet monitor ({@link TabletGui}); stamp screen unchanged.
 */
public final class CourierBoardScreen extends Screen {
    /** Width reserved on the right of each list row for action buttons (px from screen right edge). */
    private static final int ROW_ACTION_RESERVE = 158;
    private static final int PROFILE_ICON_SIZE = 16;
    private static final int PROFILE_LINE_HEIGHT = 11;
    private static final int TAB_Y = 2;
    private static final int TAB_H = 20;
    /** Gap between tab row and per-tab heading (px). */
    private static final int HEADING_GAP = 7;
    private static final int SCREEN_BOTTOM_PAD = 12;
    private static final int SCROLL_COUNTER_H = 12;
    private static final int DETAILS_BLOCK_H = 56;
    private static final int BALANCE_MAIL_GAP = 8;

    /** Left edge of the balance strip (right of last tab). */
    private int balanceStripLeft;

    enum Tab {
        BOARD, MINE, PROFILE, TEST
    }

    private @Nullable BoardSnapshotPayload snapshot;
    private Tab tab = Tab.BOARD;
    private int scroll;
    private @Nullable UUID selected;
    private @Nullable UUID pendingAccept;
    private @Nullable Component notice;
    private @Nullable Component hoverTip;
    /** Visible list rows whose action buttons depend on {@link #initListButtons}; avoids rebuild on timer-only updates. */
    private @Nullable String listRowButtonKey;

    public CourierBoardScreen(@Nullable BoardSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.board.title"));
        applySnapshot(snapshot);
    }

    void apply(BoardSnapshotPayload payload) {
        BoardSnapshotPayload previous = snapshot;
        applySnapshot(payload);
        if (selected != null && payload.find(selected).isEmpty()) {
            selected = null;
            pendingAccept = null;
        }
        if (previous != null && previous.operator() != payload.operator()) {
            listRowButtonKey = listRowButtonKey(payload);
            rebuildWidgets();
        } else if (needsContentWidgetRefresh(previous, payload)) {
            listRowButtonKey = listRowButtonKey(payload);
            refreshContentWidgets();
        }
    }

    private boolean needsContentWidgetRefresh(@Nullable BoardSnapshotPayload previous, BoardSnapshotPayload payload) {
        if (previous == null) {
            return true;
        }
        if (tab == Tab.BOARD && previous.page() != payload.page()) {
            return true;
        }
        String nextKey = listRowButtonKey(payload);
        return nextKey != null && !nextKey.equals(listRowButtonKey);
    }

    private void selectTab(Tab value) {
        tab = value;
        scroll = 0;
        pendingAccept = null;
        listRowButtonKey = null;
        rebuildWidgets();
    }

    /** Drops and rebuilds row/test {@link TabletButton}s only; tabs and mail stay mounted. */
    private void refreshContentWidgets() {
        removeTabletButtons();
        if (snapshot == null) {
            return;
        }
        if (tab == Tab.TEST) {
            initTest();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            initListButtons(tab == Tab.BOARD ? snapshot.board() : snapshot.mine(), tab == Tab.BOARD);
        }
    }

    private void removeTabletButtons() {
        List<TabletButton> remove = new ArrayList<>();
        for (Renderable widget : renderables) {
            if (widget instanceof TabletButton button) {
                remove.add(button);
            }
        }
        for (TabletButton button : remove) {
            removeWidget(button);
        }
    }

    /**
     * Signature of scroll-visible rows that affect which per-row buttons exist (not minutes/reward text).
     */
    private @Nullable String listRowButtonKey(BoardSnapshotPayload payload) {
        if (tab != Tab.BOARD && tab != Tab.MINE) {
            return null;
        }
        List<BoardSnapshotPayload.BoardEntry> all = tab == Tab.BOARD ? payload.board() : payload.mine();
        int visible = visibleRows();
        StringBuilder key = new StringBuilder(visible * 48);
        key.append(scroll).append('|').append(pendingAccept).append('|');
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            key.append(entry.id()).append(':').append(entry.state()).append(':').append(entry.role()).append(';');
        }
        return key.toString();
    }

    private void applySnapshot(@Nullable BoardSnapshotPayload payload) {
        this.snapshot = payload;
        if (payload != null) {
            payload.notice().ifPresent(component -> notice = component);
            if (!payload.operator() && tab == Tab.TEST) {
                tab = Tab.BOARD;
                rebuildWidgets();
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        int sx = screenLeft();
        int sy = screenTop();
        int tabW = Math.max(64, screenW() / 7);
        int tabX = sx + 4;
        tabX = addTab(tabX, sy + TAB_Y, tabW, Tab.BOARD, "ui.aerocouriers.tab.board") + 4;
        tabX = addTab(tabX, sy + TAB_Y, tabW, Tab.MINE, "ui.aerocouriers.tab.mine") + 4;
        tabX = addTab(tabX, sy + TAB_Y, tabW, Tab.PROFILE, "ui.aerocouriers.tab.profile") + 4;
        if (snapshot != null && snapshot.operator()) {
            tabX = addTab(tabX, sy + TAB_Y, tabW, Tab.TEST, "ui.aerocouriers.tab.test") + 4;
        }
        balanceStripLeft = tabX + 4;

        int mail = snapshot == null ? 0 : snapshot.mailCount();
        addRenderableWidget(new MailIconButton(sx + screenW() - 24, sy + TAB_Y, mail, true));

        if (snapshot == null) {
            return;
        }
        if (tab == Tab.TEST) {
            initTest();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            initListButtons(tab == Tab.BOARD ? snapshot.board() : snapshot.mine(), tab == Tab.BOARD);
        }
        if (tab == Tab.BOARD || tab == Tab.MINE) {
            listRowButtonKey = listRowButtonKey(snapshot);
        }
    }

    /** Returns x position after this tab (for laying out the next one). */
    private int addTab(int x, int y, int w, Tab value, String key) {
        boolean on = tab == value;
        addRenderableWidget(new TabletTabButton(x, y, w, 20, Component.translatable(key), () -> selectTab(value))
                .selected(on));
        return x + w;
    }

    private void initListButtons(List<BoardSnapshotPayload.BoardEntry> all, boolean boardTab) {
        int sx = screenLeft();
        int sw = screenW();
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int rowTop = listTop();
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int y = rowTop + i * rowH();
            addRenderableWidget(TabletButton.create(
                    sx + sw - 156, y, 68, 18, Component.translatable("ui.aerocouriers.details"), b -> {
                        selected = entry.id();
                        pendingAccept = null;
                    }));
            if (boardTab && entry.state() == ContractState.OPEN
                    && (!"issuer".equals(entry.role()) || snapshot.allowSelfAccept())) {
                boolean confirm = entry.id().equals(pendingAccept);
                TabletButton accept = TabletButton.create(
                                sx + sw - 84, y, 76, 18,
                                Component.translatable(confirm ? "ui.aerocouriers.accept.confirm" : "ui.aerocouriers.accept",
                                        entry.collateral()),
                                b -> onAccept(entry));
                accept.active = snapshot != null && snapshot.licensed();
                if (!accept.active) {
                    accept.hint(Component.translatable("contract.aerocouriers.error.no_license"));
                }
                addRenderableWidget(accept);
            } else if (!boardTab && "issuer".equals(entry.role()) && entry.state() == ContractState.OPEN) {
                addRenderableWidget(TabletButton.create(
                        sx + sw - 84, y, 76, 18, Component.translatable("ui.aerocouriers.cancel"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.CANCEL, entry.id()))));
            } else if (!boardTab && "courier".equals(entry.role()) && entry.state().isActive()) {
                addRenderableWidget(TabletButton.create(
                        sx + sw - 84, y, 76, 18, Component.translatable("ui.aerocouriers.abandon"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.ABANDON, entry.id()))));
            }
        }
    }

    private void onAccept(BoardSnapshotPayload.BoardEntry entry) {
        selected = entry.id();
        if (entry.id().equals(pendingAccept)) {
            PacketDistributor.sendToServer(new ContractIntentPayload(ContractIntentPayload.Kind.ACCEPT, entry.id()));
            pendingAccept = null;
        } else {
            pendingAccept = entry.id();
            listRowButtonKey = snapshot == null ? null : listRowButtonKey(snapshot);
            refreshContentWidgets();
        }
    }

    private void initTest() {
        int sx = screenLeft();
        int y = listTop();
        addRenderableWidget(TabletButton.create(sx + 8, y, 220, 20,
                Component.translatable("ui.aerocouriers.test.license"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_LICENSE, Optional.empty()))));
        addRenderableWidget(TabletButton.create(sx + 8, y + 24, 220, 20,
                Component.translatable("ui.aerocouriers.test.money", OperatorBoardPayload.TEST_MONEY), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.ADD_MONEY, Optional.empty()))));
        addRenderableWidget(TabletButton.create(sx + 8, y + 48, 220, 20,
                Component.translatable("ui.aerocouriers.test.stamp"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_STAMP, Optional.empty()))));
        Optional<UUID> id = Optional.ofNullable(selected);
        int actionY = y + 88;
        TabletButton spawn = TabletButton.create(sx + 8, actionY, 120, 20,
                Component.translatable("ui.aerocouriers.test.forcespawn"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_SPAWN, id)));
        TabletButton deliver = TabletButton.create(sx + 132, actionY, 120, 20,
                Component.translatable("ui.aerocouriers.test.forcedeliver"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_DELIVER, id)));
        spawn.active = selected != null;
        deliver.active = selected != null;
        addRenderableWidget(spawn);
        addRenderableWidget(deliver);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
        TabletGui.drawScreenBackground(graphics, screenLeft(), screenTop(), screenW(), screenH());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        super.render(graphics, mouseX, mouseY, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int sx = screenLeft();
        int sy = screenTop();
        int headingY = headingY();
        graphics.drawString(font, tabHeading(), sx + 6, headingY, TabletGui.TEXT, false);
        if (snapshot != null) {
            long balance = snapshot.balance();
            Component balanceLine = Component.translatable("ui.aerocouriers.balance", balance);
            int mailX = sx + screenW() - 24;
            int balanceRight = mailX - BALANCE_MAIL_GAP;
            int maxW = Math.max(8, balanceRight - balanceStripLeft);
            drawClipped(graphics, balanceLine, balanceStripLeft, sy + 8, maxW, TabletGui.TEXT, mouseX, mouseY);
        }

        if (snapshot == null) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.loading"),
                    sx + 8, sy + 48, TabletGui.TEXT_MUTED, false);
            drawHoverTip(graphics, mouseX, mouseY);
            return;
        }

        if (tab == Tab.BOARD || tab == Tab.MINE) {
            renderList(graphics, tab == Tab.BOARD ? snapshot.board() : snapshot.mine(), sx, mouseX, mouseY);
        } else if (tab == Tab.PROFILE) {
            renderProfile(graphics, snapshot.profile(), sx, mouseX, mouseY);
        } else if (tab == Tab.TEST) {
            int testY = listTop();
            String id = selected == null ? "—" : snapshot.find(selected).map(BoardSnapshotPayload.BoardEntry::shortId)
                    .orElse(selected.toString().substring(0, 8));
            graphics.drawString(font, Component.translatable("ui.aerocouriers.test.selected", id),
                    sx + 8, testY + 72, TabletGui.TEXT, false);
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.test.hint"),
                    sx + 8, testY + 118, screenW() - 16, TabletGui.TEXT_MUTED);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        TabletGui.drawFrameChrome(graphics, left(), top(), panelW(), panelH());

        if (notice != null) {
            int noticeY = screenTop() + screenH() - 28;
            TabletGui.drawListPanel(graphics, sx + 6, noticeY - 2, Math.min(screenW() - 12, font.width(notice) + 24), 18);
            drawWrapped(graphics, notice, sx + 12, noticeY, screenW() - 24, TabletGui.TEXT_WARN);
        }
        captureButtonHint(mouseX, mouseY);
        drawHoverTip(graphics, mouseX, mouseY);
    }

    private void renderProfile(GuiGraphics graphics, BoardSnapshotPayload.BoardProfile profile, int sx,
                               int mouseX, int mouseY) {
        int top = listTop();
        int bottom = screenTop() + screenH() - 12;
        int boxH = Math.max(48, bottom - top);
        int innerW = screenW() - 16;
        int boxW = Math.max(96, innerW * 42 / 100);
        int boxX = sx + 8;
        int statsX = boxX + boxW + 12;
        int statsW = Math.max(48, sx + screenW() - 8 - statsX);

        TabletGui.drawListPanel(graphics, boxX, top, boxW, boxH);
        if (minecraft != null && minecraft.player != null) {
            int entityScale = Mth.clamp((int) (boxH * 0.42F), 28, 140);
            float yOffset = 0.0625F - Math.min(0.52F, boxH / 180.0F);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    boxX,
                    top,
                    boxX + boxW,
                    top + boxH,
                    entityScale,
                    yOffset,
                    mouseX,
                    mouseY,
                    minecraft.player);
        }

        int y = top + 4;
        y = drawProfileStatBlock(graphics, Component.literal(profile.playerName()), statsX, y, statsW,
                TabletGui.TEXT_BRIGHT);
        y += 2;

        LicenseRank rank = profile.rank();
        ItemStack licenseIcon = new ItemStack(ModItems.PILOT_LICENSE.get());
        licenseIcon.set(ModComponents.LICENSE_RANK.get(), rank);
        graphics.renderItem(licenseIcon, statsX, y);
        Component rankLine = Component.translatable("ui.aerocouriers.profile.rank", rank.displayName());
        int rankTextX = statsX + PROFILE_ICON_SIZE + 4;
        drawClipped(graphics, rankLine, rankTextX, y + 4, statsW - PROFILE_ICON_SIZE - 4, TabletGui.TEXT, mouseX,
                mouseY);
        y += PROFILE_ICON_SIZE + 6;

        y = drawProfileStatBlock(graphics,
                Component.translatable("ui.aerocouriers.profile.delivered", profile.delivered()), statsX, y, statsW,
                TabletGui.TEXT);
        y = drawProfileStatBlock(graphics,
                Component.translatable("ui.aerocouriers.profile.failed", profile.failed()), statsX, y, statsW,
                TabletGui.TEXT);
        y = drawProfileStatBlock(graphics,
                Component.translatable("ui.aerocouriers.profile.avg_reward",
                        formatAverage(profile.rewardSum(), profile.delivered())),
                statsX, y, statsW, TabletGui.TEXT);
        y = drawProfileStatBlock(graphics,
                Component.translatable("ui.aerocouriers.profile.avg_distance",
                        formatAverage(profile.distance(), profile.delivered())),
                statsX, y, statsW, TabletGui.TEXT);
        int completed = profile.delivered() + profile.failed();
        String rate = completed == 0
                ? String.format(Locale.ROOT, "%.0f%%", 100.0)
                : String.format(Locale.ROOT, "%.0f%%", profile.delivered() * 100.0 / completed);
        drawProfileStatBlock(graphics, Component.translatable("ui.aerocouriers.profile.success_rate", rate), statsX, y,
                statsW, TabletGui.TEXT);
    }

    private int drawProfileStatBlock(GuiGraphics graphics, Component line, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(line, width);
        for (FormattedCharSequence row : lines) {
            graphics.drawString(font, row, x, y, color, false);
            y += PROFILE_LINE_HEIGHT;
        }
        return y + 2;
    }

    private static Component formatAverage(long sum, int count) {
        if (count <= 0) {
            return Component.translatable("ui.aerocouriers.profile.dash");
        }
        return Component.literal(String.format(Locale.ROOT, "%d", sum / count));
    }

    private void renderList(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> all, int sx,
                            int mouseX, int mouseY) {
        if (all.isEmpty()) {
            graphics.drawString(font, Component.translatable("contract.aerocouriers.list.none"),
                    sx + 8, listTop() + 4, TabletGui.TEXT_MUTED, false);
            return;
        }
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int sw = screenW();
        int textLeft = sx + 8;
        int textRight = sx + sw - ROW_ACTION_RESERVE;
        int textW = Math.max(8, textRight - textLeft);
        int listY = listTop();
        int listH = visible * rowH();
        TabletGui.drawListPanel(graphics, sx + 4, listY - 2, sw - 8, listH + 2);
        graphics.enableScissor(sx + 4, listY - 2, sx + sw - 4, listY + listH);
        try {
            for (int i = 0; i < visible && scroll + i < all.size(); i++) {
                BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
                int y = listY + i * rowH();
                TabletGui.drawRow(graphics, sx + 6, y - 1, sw - ROW_ACTION_RESERVE - 6, rowH() - 2,
                        entry.id().equals(selected));
                Component main = Component.translatable("ui.aerocouriers.row.main",
                        entry.shortId(), entry.originLabel(), entry.destinationLabel(), entry.distance());
                Component meta = Component.translatable("ui.aerocouriers.row.meta",
                        entry.state().displayName(),
                        entry.reward(),
                        entry.collateral(),
                        entry.minutesLeft(),
                        entry.weight(),
                        entry.size(),
                        Component.translatable("contract.aerocouriers.role." + entry.role()));
                drawClipped(graphics, main, textLeft, y, textW, TabletGui.TEXT, mouseX, mouseY);
                drawClipped(graphics, meta, textLeft, y + 10, textW, TabletGui.TEXT_MUTED, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }

        if (all.size() > visible) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.scroll",
                            scroll + 1, Math.min(scroll + visible, all.size()), all.size()),
                    sx + 8, scrollCounterY(), TabletGui.TEXT_MUTED, false);
            float thumb = visible / (float) all.size();
            float progress = scroll / (float) Math.max(1, all.size() - visible);
            TabletGui.drawScrollbar(graphics, sx + screenW() - 10, listTop(), visible * rowH(), progress, thumb);
        }

        if (selected == null) {
            return;
        }
        snapshot.find(selected).ifPresent(entry -> {
            int y = detailsTop();
            String cargo = entry.manifest().isEmpty()
                    ? "—"
                    : entry.manifest().stream()
                    .map(line -> line.count() + " × " + line.name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("—");
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.details.line",
                            entry.requiredRank().displayName(), entry.deadlineMinutes(), cargo),
                    sx + 8, y, screenW() - 16, TabletGui.TEXT);
            if (entry.state() == ContractState.OPEN) {
                drawWrapped(graphics, Component.translatable("contract.aerocouriers.risk",
                                entry.collateral(), entry.reward()),
                        sx + 8, y + 22, screenW() - 16, TabletGui.TEXT_WARN);
            }
        });
    }

    private void drawClipped(GuiGraphics graphics, Component text, int x, int y, int width, int color,
                             int mouseX, int mouseY) {
        String raw = text.getString();
        String shown = font.plainSubstrByWidth(raw, width);
        if (!shown.equals(raw)) {
            shown = font.plainSubstrByWidth(raw, Math.max(8, width - font.width("…"))) + "…";
        }
        graphics.drawString(font, shown, x, y, color, false);
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 9 && !shown.equals(raw)) {
            hoverTip = text;
        }
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int drawY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, drawY, color, false);
            drawY += 9;
        }
    }

    private void captureButtonHint(int mouseX, int mouseY) {
        if (hoverTip != null) {
            return;
        }
        getChildAt(mouseX, mouseY).ifPresent(widget -> {
            if (widget instanceof TabletButton button && button.hint() != null) {
                hoverTip = button.hint();
            }
        });
    }

    private void drawHoverTip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoverTip != null) {
            TabletGui.drawSpeechTooltip(graphics, font, hoverTip, mouseX, mouseY, 240);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            List<BoardSnapshotPayload.BoardEntry> all = tab == Tab.BOARD ? snapshot.board() : snapshot.mine();
            int before = scroll;
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, all.size() - visibleRows()));
            if (scroll != before) {
                if (tab == Tab.BOARD && snapshot.page() > 0 && scroll == 0 && scrollY > 0) {
                    PacketDistributor.sendToServer(new OpenBoardPayload(snapshot.page() - 1));
                } else if (tab == Tab.BOARD && (snapshot.page() + 1) * BoardSnapshotPayload.PAGE_SIZE < snapshot.boardTotal()
                        && scroll >= Math.max(0, all.size() - visibleRows()) && scrollY < 0) {
                    PacketDistributor.sendToServer(new OpenBoardPayload(snapshot.page() + 1));
                }
                listRowButtonKey = listRowButtonKey(snapshot);
                refreshContentWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int panelW() {
        int margin = 24 + TabletGui.GEAR_OVERHANG_LEFT;
        return Mth.clamp((int) (width * 0.72), 400, Math.max(400, width - margin));
    }

    private int panelH() {
        int margin = 24 + TabletGui.ANTENNA_OVERHANG_TOP;
        return Mth.clamp((int) (height * 0.80), 280, Math.max(280, height - margin));
    }

    private int left() {
        return TabletGui.frameLeft(width, panelW());
    }

    private int top() {
        return TabletGui.frameTop(height, panelH());
    }

    private int screenLeft() {
        return left() + TabletGui.FRAME_BORDER;
    }

    private int screenTop() {
        return top() + TabletGui.FRAME_BORDER;
    }

    private int screenW() {
        return panelW() - TabletGui.FRAME_BORDER * 2;
    }

    private int screenH() {
        return panelH() - TabletGui.FRAME_BORDER * 2;
    }

    private int rowH() {
        return 24;
    }

    private Component tabHeading() {
        return switch (tab) {
            case BOARD -> Component.translatable("ui.aerocouriers.board.heading.board");
            case MINE -> Component.translatable("ui.aerocouriers.board.heading.mine");
            case PROFILE -> Component.translatable("ui.aerocouriers.board.heading.profile");
            case TEST -> Component.translatable("ui.aerocouriers.board.heading.test");
        };
    }

    private int headingY() {
        return screenTop() + TAB_Y + TAB_H + HEADING_GAP;
    }

    private int listTop() {
        return headingY() + 10;
    }

    private int contentBottom() {
        return screenTop() + screenH() - SCREEN_BOTTOM_PAD;
    }

    private int scrollCounterY() {
        return contentBottom() - SCROLL_COUNTER_H;
    }

    private int detailsTop() {
        int bottom = contentBottom();
        if (selected != null && snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            return bottom - DETAILS_BLOCK_H;
        }
        return bottom;
    }

    private int listBottom() {
        int bottom = detailsTop() - 4;
        if (snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            List<BoardSnapshotPayload.BoardEntry> all = tab == Tab.BOARD ? snapshot.board() : snapshot.mine();
            int rowsWithoutCounter = Math.max(3, (bottom - listTop()) / rowH());
            if (all.size() > rowsWithoutCounter) {
                bottom -= SCROLL_COUNTER_H;
            }
        }
        return bottom;
    }

    private int visibleRows() {
        return Math.max(3, (listBottom() - listTop()) / rowH());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
