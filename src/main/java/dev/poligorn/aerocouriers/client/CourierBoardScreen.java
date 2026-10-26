package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
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
        if (needsWidgetRebuild(previous, payload)) {
            listRowButtonKey = listRowButtonKey(payload);
            rebuildWidgets();
        }
    }

    private boolean needsWidgetRebuild(@Nullable BoardSnapshotPayload previous, BoardSnapshotPayload payload) {
        if (previous == null) {
            return true;
        }
        if (previous.operator() != payload.operator()) {
            return true;
        }
        if (tab == Tab.BOARD && previous.page() != payload.page()) {
            return true;
        }
        String nextKey = listRowButtonKey(payload);
        if (nextKey != null && !nextKey.equals(listRowButtonKey)) {
            return true;
        }
        return false;
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
        key.append(scroll).append('|');
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
        tabX = addTab(tabX, sy + 2, tabW, Tab.BOARD, "ui.aerocouriers.tab.board") + 4;
        tabX = addTab(tabX, sy + 2, tabW, Tab.MINE, "ui.aerocouriers.tab.mine") + 4;
        tabX = addTab(tabX, sy + 2, tabW, Tab.PROFILE, "ui.aerocouriers.tab.profile") + 4;
        if (snapshot != null && snapshot.operator()) {
            addTab(tabX, sy + 2, tabW, Tab.TEST, "ui.aerocouriers.tab.test");
        }

        int mail = snapshot == null ? 0 : snapshot.mailCount();
        addRenderableWidget(new MailIconButton(sx + screenW() - 24, sy + 2, mail, true));

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
        addRenderableWidget(new TabletTabButton(x, y, w, 20, Component.translatable(key), () -> {
            tab = value;
            scroll = 0;
            pendingAccept = null;
            listRowButtonKey = snapshot == null ? null : listRowButtonKey(snapshot);
            rebuildWidgets();
        }).selected(on));
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
            rebuildWidgets();
        }
    }

    private void initTest() {
        addRenderableWidget(TabletButton.create(screenLeft() + 8, screenTop() + 48, 220, 20,
                Component.translatable("ui.aerocouriers.test.license"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_LICENSE, Optional.empty()))));
        addRenderableWidget(TabletButton.create(screenLeft() + 8, screenTop() + 72, 220, 20,
                Component.translatable("ui.aerocouriers.test.money", OperatorBoardPayload.TEST_MONEY), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.ADD_MONEY, Optional.empty()))));
        addRenderableWidget(TabletButton.create(screenLeft() + 8, screenTop() + 96, 220, 20,
                Component.translatable("ui.aerocouriers.test.stamp"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_STAMP, Optional.empty()))));
        Optional<UUID> id = Optional.ofNullable(selected);
        TabletButton spawn = TabletButton.create(screenLeft() + 8, screenTop() + 140, 120, 20,
                Component.translatable("ui.aerocouriers.test.forcespawn"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_SPAWN, id)));
        TabletButton deliver = TabletButton.create(screenLeft() + 132, screenTop() + 140, 120, 20,
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
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        TabletGui.drawScreenBackground(graphics, screenLeft(), screenTop(), screenW(), screenH());
        super.render(graphics, mouseX, mouseY, partialTick);
        TabletGui.drawFrameChrome(graphics, left(), top(), panelW(), panelH());
        int sx = screenLeft();
        int sy = screenTop();
        graphics.drawString(font, title, sx + 6, sy + 22, TabletGui.TEXT, false);
        long balance = snapshot == null ? 0 : snapshot.balance();
        Component balanceLine = Component.translatable("ui.aerocouriers.balance", balance);
        graphics.drawString(font, balanceLine, sx + screenW() - 6 - font.width(balanceLine), sy + 22, TabletGui.TEXT,
                false);

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
            String id = selected == null ? "—" : snapshot.find(selected).map(BoardSnapshotPayload.BoardEntry::shortId)
                    .orElse(selected.toString().substring(0, 8));
            graphics.drawString(font, Component.translatable("ui.aerocouriers.test.selected", id),
                    sx + 8, screenTop() + 124, TabletGui.TEXT, false);
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.test.hint"),
                    sx + 8, screenTop() + 170, screenW() - 16, TabletGui.TEXT_MUTED);
        }

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
        int boxH = bottom - top;
        int boxW = Math.max(72, screenW() / 2 - 20);
        int boxX = sx + 8;
        TabletGui.drawListPanel(graphics, boxX, top, boxW, boxH);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    boxX,
                    top,
                    boxX + boxW,
                    top + boxH,
                    30,
                    0.0625F,
                    mouseX,
                    mouseY,
                    minecraft.player);
        }

        int textX = boxX + boxW + 12;
        int textW = sx + screenW() - 8 - textX;
        int y = top + 4;
        graphics.drawString(font, profile.playerName(), textX, y, TabletGui.TEXT_BRIGHT, false);
        y += 12;

        LicenseRank rank = profile.rank();
        Component rankLine = Component.translatable("ui.aerocouriers.profile.rank", rank.displayName());
        graphics.drawString(font, rankLine, textX + 14, y, TabletGui.TEXT, false);
        ItemStack licenseIcon = new ItemStack(ModItems.PILOT_LICENSE.get());
        licenseIcon.set(ModComponents.LICENSE_RANK.get(), rank);
        graphics.renderItem(licenseIcon, textX, y - 2);
        y += 12;

        y = drawProfileStat(graphics, textX, y, textW,
                Component.translatable("ui.aerocouriers.profile.delivered", profile.delivered()));
        y = drawProfileStat(graphics, textX, y, textW,
                Component.translatable("ui.aerocouriers.profile.failed", profile.failed()));
        y = drawProfileStat(graphics, textX, y, textW,
                Component.translatable("ui.aerocouriers.profile.avg_reward", formatAverage(profile.rewardSum(), profile.delivered())));
        y = drawProfileStat(graphics, textX, y, textW,
                Component.translatable("ui.aerocouriers.profile.avg_distance",
                        formatAverage(profile.distance(), profile.delivered())));
        int completed = profile.delivered() + profile.failed();
        String rate = completed == 0
                ? String.format(Locale.ROOT, "%.0f%%", 100.0)
                : String.format(Locale.ROOT, "%.0f%%", profile.delivered() * 100.0 / completed);
        drawProfileStat(graphics, textX, y, textW,
                Component.translatable("ui.aerocouriers.profile.success_rate", rate));
    }

    private int drawProfileStat(GuiGraphics graphics, int x, int y, int width, Component line) {
        drawWrapped(graphics, line, x, y, width, TabletGui.TEXT);
        return y + 11;
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
                    sx + 8, detailsTop() - 12, TabletGui.TEXT_MUTED, false);
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
                rebuildWidgets();
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

    private int listTop() {
        return screenTop() + 44;
    }

    private int detailsTop() {
        return screenTop() + screenH() - 72;
    }

    private int visibleRows() {
        return Math.max(3, (detailsTop() - listTop() - 8) / rowH());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
