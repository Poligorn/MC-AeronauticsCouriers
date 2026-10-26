package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.board.BoardCargoTooltip;
import dev.poligorn.aerocouriers.client.board.BoardRowDebug;
import dev.poligorn.aerocouriers.client.board.BoardRowLayout;
import dev.poligorn.aerocouriers.client.board.BoardRowMetrics;
import dev.poligorn.aerocouriers.client.board.BoardRowRenderer;
import dev.poligorn.aerocouriers.client.board.MineListSection;
import dev.poligorn.aerocouriers.client.board.TabletRowIconButton;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.components.Renderable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Monitoring board: open orders and the viewer's own. Creation is the stamp item, not a tab.
 * Chrome: brass order-tablet monitor ({@link TabletGui}); stamp screen unchanged.
 */
public final class CourierBoardScreen extends Screen {
    private static final int PROFILE_ICON_SIZE = 16;
    private static final int PROFILE_LINE_HEIGHT = 11;
    private static final int TAB_Y = 2;
    private static final int TAB_H = 20;
    /** Gap between tab row and per-tab heading (px). */
    private static final int HEADING_GAP = 7;
    private static final int SCREEN_BOTTOM_PAD = 12;
    private static final int SCROLL_COUNTER_H = 12;
    private static final int BALANCE_MAIL_GAP = 8;
    private static final int MINE_SUB_TOGGLE_H = 18;
    private static final int MINE_SUB_GAP = 4;

    /** Left edge of the balance strip (right of last tab). */
    private int balanceStripLeft;
    private final List<AbstractWidget> mineSubToggles = new ArrayList<>();
    private final Map<Tab, TabletTabButton> mainTabs = new EnumMap<>(Tab.class);

    enum Tab {
        BOARD, MINE, PROFILE, TEST
    }

    private @Nullable BoardSnapshotPayload snapshot;
    private Tab tab = Tab.BOARD;
    private int scroll;
    private @Nullable UUID selected;
    private @Nullable Component notice;
    private @Nullable Component hoverTip;
    private @Nullable List<Component> hoverTipLines;
    private @Nullable BoardCargoTooltip hoverCargoComponent;
    private @Nullable UUID mapHoverOrder;
    private @Nullable UUID crateHoverOrder;
    /** Visible list rows whose action buttons depend on {@link #initListButtons}; avoids rebuild on timer-only updates. */
    private @Nullable String listRowButtonKey;

    public CourierBoardScreen(@Nullable BoardSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.board.title"));
        applySnapshot(snapshot);
    }

    void apply(BoardSnapshotPayload payload) {
        BoardSnapshotPayload previous = snapshot;
        applySnapshot(payload);
        UUID focus = ClientBoard.takePendingContractFocus();
        if (focus != null && payload.find(focus).isPresent()) {
            selected = focus;
            Optional<BoardSnapshotPayload.BoardEntry> mineHit = payload.mine().stream()
                    .filter(e -> e.id().equals(focus))
                    .findFirst();
            if (mineHit.isPresent()) {
                tab = Tab.MINE;
                ClientBoard.setMineSection(MineListSection.forState(mineHit.get().state()));
            } else {
                tab = Tab.BOARD;
            }
            syncMainTabSelection();
            scroll = 0;
            listRowButtonKey = null;
            refreshBoardContent();
            focusScrollToSelected();
        }
        if (selected != null && payload.find(selected).isEmpty()) {
            selected = null;
        }
        if (previous != null && previous.operator() != payload.operator()) {
            listRowButtonKey = listRowButtonKey(payload);
            refreshBoardContent();
        } else if (needsContentWidgetRefresh(previous, payload)) {
            listRowButtonKey = listRowButtonKey(payload);
            refreshContentWidgets();
        }
        if (previous == null || previous.mailCount() != payload.mailCount()
                || previous.notificationUnread() != payload.notificationUnread()) {
            refreshHeaderIcons(screenLeft(), mainTabRowY());
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
        listRowButtonKey = null;
        syncMainTabSelection();
        refreshBoardContent();
    }

    private void syncMainTabSelection() {
        for (var entry : mainTabs.entrySet()) {
            entry.getValue().selected(tab == entry.getKey());
        }
    }

    private void selectMineSection(MineListSection section) {
        ClientBoard.setMineSection(section);
        scroll = 0;
        listRowButtonKey = null;
        if (selected != null && snapshot != null
                && listEntries().stream().noneMatch(e -> e.id().equals(selected))) {
            selected = null;
        }
        refreshBoardContent();
    }

    private void refreshBoardContent() {
        removeMineSubToggles();
        removeTabletButtons();
        if (snapshot == null) {
            return;
        }
        if (tab == Tab.TEST) {
            initTest();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            if (tab == Tab.MINE) {
                initMineSubToggles(screenLeft());
            }
            initListButtons(listEntries(), tab == Tab.BOARD);
        }
        if (tab == Tab.BOARD || tab == Tab.MINE) {
            listRowButtonKey = listRowButtonKey(snapshot);
        }
    }

    /** Drops and rebuilds row/test {@link TabletButton}s only; tabs and mail stay mounted. */
    private void refreshContentWidgets() {
        removeMineSubToggles();
        removeTabletButtons();
        if (snapshot == null) {
            return;
        }
        if (tab == Tab.TEST) {
            initTest();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            if (tab == Tab.MINE) {
                initMineSubToggles(screenLeft());
            }
            initListButtons(listEntries(), tab == Tab.BOARD);
        }
    }

    private void removeTabletButtons() {
        List<AbstractWidget> remove = new ArrayList<>();
        for (Renderable widget : renderables) {
            if (widget instanceof TabletButton || widget instanceof TabletRowIconButton) {
                remove.add((AbstractWidget) widget);
            }
        }
        for (AbstractWidget button : remove) {
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
        List<BoardSnapshotPayload.BoardEntry> all = listEntriesForTab(payload);
        int visible = visibleRows();
        StringBuilder key = new StringBuilder(visible * 48);
        if (tab == Tab.MINE) {
            key.append(ClientBoard.mineSection()).append(':')
                    .append(MineListSection.count(payload.mine(), MineListSection.CURRENT)).append('/')
                    .append(MineListSection.count(payload.mine(), MineListSection.HISTORY)).append('|');
        }
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
                refreshBoardContent();
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        mainTabs.clear();
        int sx = screenLeft();
        int tabRowY = mainTabRowY();
        int tabW = Math.max(64, screenW() / 7);
        int tabX = sx;
        tabX = addTab(tabX, tabRowY, tabW, Tab.BOARD, "ui.aerocouriers.tab.board") + 4;
        tabX = addTab(tabX, tabRowY, tabW, Tab.MINE, "ui.aerocouriers.tab.mine") + 4;
        tabX = addTab(tabX, tabRowY, tabW, Tab.PROFILE, "ui.aerocouriers.tab.profile") + 4;
        if (snapshot != null && snapshot.operator()) {
            tabX = addTab(tabX, tabRowY, tabW, Tab.TEST, "ui.aerocouriers.tab.test") + 4;
        }
        balanceStripLeft = tabX + 4;
        syncMainTabSelection();
        refreshHeaderIcons(sx, tabRowY);

        if (snapshot == null) {
            return;
        }
        if (tab == Tab.TEST) {
            initTest();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            if (tab == Tab.MINE) {
                initMineSubToggles(sx);
            }
            initListButtons(listEntries(), tab == Tab.BOARD);
        }
        if (tab == Tab.BOARD || tab == Tab.MINE) {
            listRowButtonKey = listRowButtonKey(snapshot);
        }
    }

    private void initMineSubToggles(int sx) {
        if (snapshot == null) {
            return;
        }
        int y = mineSubToggleY();
        int gap = MINE_SUB_GAP;
        int eachW = (screenW() - gap) / 2;
        MineListSection active = ClientBoard.mineSection();
        int currentN = MineListSection.count(snapshot.mine(), MineListSection.CURRENT);
        int historyN = MineListSection.count(snapshot.mine(), MineListSection.HISTORY);
        TabletTabButton current = new TabletTabButton(sx, y, eachW, MINE_SUB_TOGGLE_H,
                Component.translatable("ui.aerocouriers.mine.current", currentN),
                () -> selectMineSection(MineListSection.CURRENT)).selected(active == MineListSection.CURRENT);
        TabletTabButton history = new TabletTabButton(sx + eachW + gap, y, eachW, MINE_SUB_TOGGLE_H,
                Component.translatable("ui.aerocouriers.mine.history", historyN),
                () -> selectMineSection(MineListSection.HISTORY)).selected(active == MineListSection.HISTORY);
        mineSubToggles.add(current);
        mineSubToggles.add(history);
        addRenderableWidget(current);
        addRenderableWidget(history);
    }

    private void removeMineSubToggles() {
        for (AbstractWidget widget : mineSubToggles) {
            removeWidget(widget);
        }
        mineSubToggles.clear();
    }

    void onNotificationsChanged() {
        refreshHeaderIcons(screenLeft(), mainTabRowY());
    }

    private void refreshHeaderIcons(int sx, int tabRowY) {
        for (Renderable widget : new ArrayList<>(renderables)) {
            if (widget instanceof MailIconButton || widget instanceof NotificationsIconButton) {
                removeWidget((AbstractWidget) widget);
            }
        }
        int mailX = sx + screenW() - 24;
        int bellX = mailX - 24;
        int mail = snapshot == null ? 0 : snapshot.mailCount();
        int unread = snapshot == null
                ? ClientNotifications.unreadCount()
                : Math.max(snapshot.notificationUnread(), ClientNotifications.unreadCount());
        addRenderableWidget(new NotificationsIconButton(bellX, tabRowY + 4, unread, true));
        addRenderableWidget(new MailIconButton(mailX, tabRowY + 4, mail, true));
    }

    /** Returns x position after this tab (for laying out the next one). */
    private int addTab(int x, int y, int w, Tab value, String key) {
        TabletTabButton button = new TabletTabButton(x, y, w, TAB_H, Component.translatable(key), () -> selectTab(value));
        button.selected(tab == value);
        mainTabs.put(value, button);
        addRenderableWidget(button);
        return x + w;
    }

    /** Main tabs sit slightly above the content top so the selected tab can raise into the inset band. */
    private int mainTabRowY() {
        return screenTop() - 4;
    }

    private void initListButtons(List<BoardSnapshotPayload.BoardEntry> all, boolean boardTab) {
        int sx = screenLeft();
        int sw = screenW();
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int rowTop = listTop();
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int rowY = rowTop + i * rowH();
            int btnY = BoardRowMetrics.buttonY(rowY);
            boolean hasSecondary = hasSecondaryAction(entry, boardTab);
            BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, hasSecondary);
            addRenderableWidget(new TabletRowIconButton(
                    layout.mapX(), btnY, entry.id(), TabletRowIconButton.Kind.MAP, this::onMapIconClick));
            addRenderableWidget(new TabletRowIconButton(
                    layout.crateX(), btnY, entry.id(), TabletRowIconButton.Kind.CRATE, null));
            if (boardTab && entry.state() == ContractState.OPEN
                    && (!"issuer".equals(entry.role()) || snapshot.allowSelfAccept())) {
                TabletButton accept = TabletButton.create(
                        layout.secondaryX(), btnY, layout.secondaryW(), BoardRowMetrics.BUTTON_HEIGHT,
                        Component.translatable("ui.aerocouriers.accept", collateralDue(entry)),
                        b -> onAccept(entry));
                accept.active = snapshot != null && snapshot.licensed();
                if (!accept.active) {
                    accept.hint(Component.translatable("contract.aerocouriers.error.no_license"));
                }
                addRenderableWidget(accept);
            } else if (!boardTab && "issuer".equals(entry.role()) && entry.state() == ContractState.OPEN) {
                addRenderableWidget(TabletButton.create(
                        layout.secondaryX(), btnY, layout.secondaryW(), BoardRowMetrics.BUTTON_HEIGHT,
                        Component.translatable("ui.aerocouriers.cancel"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.CANCEL, entry.id()))));
            } else if (!boardTab && "courier".equals(entry.role()) && entry.state().isActive()) {
                addRenderableWidget(TabletButton.create(
                        layout.secondaryX(), btnY, layout.secondaryW(), BoardRowMetrics.BUTTON_HEIGHT,
                        Component.translatable("ui.aerocouriers.abandon"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.ABANDON, entry.id()))));
            }
        }
    }

    private boolean hasSecondaryAction(BoardSnapshotPayload.BoardEntry entry, boolean boardTab) {
        if (boardTab && entry.state() == ContractState.OPEN
                && (!"issuer".equals(entry.role()) || snapshot != null && snapshot.allowSelfAccept())) {
            return true;
        }
        if (!boardTab && "issuer".equals(entry.role()) && entry.state() == ContractState.OPEN) {
            return true;
        }
        return !boardTab && "courier".equals(entry.role()) && entry.state().isActive();
    }

    private void onMapIconClick(UUID orderId) {
        if (snapshot == null) {
            return;
        }
        snapshot.find(orderId).ifPresent(entry -> {
            XaeroOrderBridge.onPreviewDestination(
                    entry.id(), entry.destinationDimension(), entry.destinationPos(), entry.shortId());
            XaeroOrderBridge.openWorldMapAt(entry.destinationPos());
        });
    }

    private void onAccept(BoardSnapshotPayload.BoardEntry entry) {
        selected = entry.id();
        if (minecraft != null) {
            BoardSnapshotPayload.BoardProfile profile = snapshot != null
                    ? snapshot.profile()
                    : BoardSnapshotPayload.BoardProfile.EMPTY;
            minecraft.setScreen(new BoardAcceptConfirmScreen(this, entry, profile));
        }
    }

    private long collateralDue(BoardSnapshotPayload.BoardEntry entry) {
        if (snapshot == null) {
            return entry.collateral();
        }
        return TraineeRules.collateralDueAtAccept(entry.collateral(), entry.villageCustomer(),
                snapshot.profile().villageDelivered(), snapshot.profile().traineeFreeContracts());
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
                Component.translatable("ui.aerocouriers.test.waybill"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_WAYBILL, Optional.empty()))));
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
        TabletGui.drawScreenBackground(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        hoverTipLines = null;
        hoverCargoComponent = null;
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        super.render(graphics, mouseX, mouseY, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int sx = screenLeft();
        int sy = screenTop();
        int headingY = headingY();
        graphics.drawString(font, tabHeading(), sx, headingY, TabletGui.TEXT, false);
        if (snapshot != null && tab == Tab.BOARD && showsTraineeHint(snapshot.profile())) {
            Component hint = Component.translatable("ui.aerocouriers.trainee.hint",
                    snapshot.profile().traineeFreeContracts() - snapshot.profile().villageDelivered(),
                    snapshot.profile().traineeMaxDistance());
            drawWrapped(graphics, hint, sx, headingY + font.lineHeight + 2, screenW(), TabletGui.TEXT_MUTED);
        }
        if (snapshot != null) {
            long balance = snapshot.balance();
            Component balanceLine = Component.translatable("ui.aerocouriers.balance", balance);
            int mailX = sx + screenW() - 24;
            int bellX = mailX - 24;
            int balanceRight = bellX - BALANCE_MAIL_GAP;
            int maxW = Math.max(8, balanceRight - balanceStripLeft);
            drawClipped(graphics, balanceLine, balanceStripLeft, mainTabRowY() + 8, maxW, TabletGui.TEXT, mouseX, mouseY);
        }

        if (snapshot == null) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.loading"),
                    sx, sy + 40, TabletGui.TEXT_MUTED, false);
            renderTopLayerTooltips(graphics, mouseX, mouseY);
            return;
        }

        if (tab == Tab.BOARD || tab == Tab.MINE) {
            renderList(graphics, listEntries(), sx, mouseX, mouseY);
            renderListActionWidgetsOnTop(graphics, mouseX, mouseY, partialTick);
        } else if (tab == Tab.PROFILE) {
            renderProfile(graphics, snapshot.profile(), sx, mouseX, mouseY);
        } else if (tab == Tab.TEST) {
            int testY = listTop();
            String id = selected == null ? "—" : snapshot.find(selected).map(BoardSnapshotPayload.BoardEntry::shortId)
                    .orElse(selected.toString().substring(0, 8));
            graphics.drawString(font, Component.translatable("ui.aerocouriers.test.selected", id),
                    sx, testY + 72, TabletGui.TEXT, false);
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.test.hint"),
                    sx, testY + 118, screenW(), TabletGui.TEXT_MUTED);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        TabletGui.drawFrameChrome(graphics, left(), top(), panelW(), panelH());

        if (notice != null) {
            int noticeY = screenTop() + screenH() - 20;
            TabletGui.drawListPanel(graphics, sx, noticeY - 2, Math.min(screenW(), font.width(notice) + 16), 18);
            drawWrapped(graphics, notice, sx + 4, noticeY, screenW() - 8, TabletGui.TEXT_WARN);
        }
        updateRowIconHover(mouseX, mouseY);
        captureRowLine1HoverTips(mouseX, mouseY);
        captureRowHoverTips(mouseX, mouseY);
        if (hoverTip == null && hoverTipLines == null && hoverCargoComponent == null) {
            captureButtonHint(mouseX, mouseY);
        }
        renderTopLayerTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public void removed() {
        if (mapHoverOrder != null) {
            XaeroOrderBridge.onLeaveDestination(mapHoverOrder);
            mapHoverOrder = null;
        }
        super.removed();
    }

    private void renderProfile(GuiGraphics graphics, BoardSnapshotPayload.BoardProfile profile, int sx,
                               int mouseX, int mouseY) {
        int top = listTop();
        int bottom = screenTop() + screenH() - 12;
        int boxH = Math.max(48, bottom - top);
        int innerW = screenW();
        int boxW = Math.max(96, innerW * 42 / 100);
        int boxX = sx;
        int statsX = boxX + boxW + 12;
        int statsW = Math.max(48, sx + screenW() - statsX);

        TabletGui.drawListPanel(graphics, boxX, top, boxW, boxH);
        if (minecraft != null && minecraft.player != null) {
            renderProfileSkin(graphics, boxX, top, boxW, boxH, mouseX, mouseY, minecraft.player);
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

    private void renderProfileSkin(GuiGraphics graphics, int boxX, int top, int boxW, int boxH, int mouseX,
                                   int mouseY, LivingEntity entity) {
        ProfileSkinPreviewLayout.Layout layout =
                ProfileSkinPreviewLayout.forPanel(boxX, top, boxW, boxH, entity.getScale());
        if (Boolean.getBoolean("aerocouriers.logProfileLayout")) {
            AeronauticsCouriers.LOGGER.info(
                    "Profile skin panel top={} h={} headTop={} feetBottom={} scale={} marginsOk={}",
                    top, boxH, layout.estimatedHeadTop(), layout.estimatedFeetBottom(), layout.scale(),
                    ProfileSkinPreviewLayout.marginsOk(top, boxH, layout));
        }
        graphics.enableScissor(boxX, top, boxX + boxW, top + boxH);
        try {
            float body = entity.yBodyRot;
            float yaw = entity.getYRot();
            float pitch = entity.getXRot();
            float headO = entity.yHeadRotO;
            float head = entity.yHeadRot;

            float renderScale = layout.scale() / entity.getScale();
            float bodyPx = renderScale * ProfileSkinPreviewLayout.PLAYER_VISUAL_HEIGHT_BLOCKS;
            float anchorX = layout.screenX();
            float anchorY = layout.estimatedFeetBottom() - bodyPx * 0.82F;
            float angleX = ProfileSkinRenderer.angleXFromMouse(anchorX, mouseX);
            float angleY = ProfileSkinRenderer.angleYFromMouse(anchorY, mouseY);

            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf cameraRot = new Quaternionf().rotateX(angleY * 20.0F * (float) (Math.PI / 180.0));
            pose.mul(cameraRot);
            entity.yBodyRot = 180.0F + angleX * 20.0F;
            entity.setYRot(180.0F + angleX * 40.0F);
            entity.setXRot(-angleY * 20.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();

            float screenY = layout.estimatedFeetBottom() - bodyPx / 2.0F;
            Vector3f translate = new Vector3f(0.0F, entity.getBbHeight() / 2.0F, 0.0F);
            ProfileSkinRenderer.render(
                    graphics, layout.screenX(), screenY, renderScale, translate, pose, cameraRot, entity);

            entity.yBodyRot = body;
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.yHeadRotO = headO;
            entity.yHeadRot = head;
        } finally {
            graphics.disableScissor();
        }
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

    /** Row widgets are drawn under list chrome in {@link #render}; paint them again on top. */
    private void renderListActionWidgetsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listY = listTop();
        int listBottom = listY + visibleRows() * rowH();
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            } else if (widget instanceof TabletButton button && button.getY() >= listY && button.getY() < listBottom) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderList(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> all, int sx,
                            int mouseX, int mouseY) {
        if (all.isEmpty()) {
            graphics.drawString(font, listEmptyMessage(), sx, listTop() + 4, TabletGui.TEXT_MUTED, false);
            return;
        }
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int sw = screenW();
        int frameLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE;
        int textLeft = frameLeft + BoardRowMetrics.ROW_INSET_LEFT;
        BoardRowLayout reserve = BoardRowLayout.forRow(sx, sw, true);
        int textRight = reserve.textRight();
        int textW = Math.max(8, textRight - textLeft);
        int listY = listTop();
        int listH = visible * rowH();
        TabletGui.drawListPanel(graphics, sx + 2, listY - 2, sw - 4, listH + 2);
        graphics.enableScissor(sx + 2, listY - 2, sx + sw - 2, listY + listH);
        try {
            for (int i = 0; i < visible && scroll + i < all.size(); i++) {
                BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
                int rowY = listY + i * rowH();
                boolean hasSecondary = hasSecondaryAction(entry, tab == Tab.BOARD);
                BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, hasSecondary);
                int frameRight = layout.hasSecondary()
                        ? layout.secondaryX() + BoardRowLayout.SECONDARY_W
                        : layout.crateX() + BoardRowLayout.ICON;
                int frameW = Math.max(8, frameRight - frameLeft);
                TabletGui.drawRow(graphics, frameLeft, rowY, frameW, rowH(), entry.id().equals(selected));
                renderOrderRow(graphics, entry, rowY, textLeft, textW, mouseX, mouseY);
                BoardRowDebug.logRow(
                        i,
                        frameLeft,
                        rowY,
                        frameW,
                        rowH(),
                        BoardRowMetrics.line1Y(rowY),
                        BoardRowMetrics.line2Y(rowY),
                        textLeft,
                        textRight,
                        layout.mapX(),
                        layout.crateX(),
                        BoardRowMetrics.buttonY(rowY));
            }
        } finally {
            graphics.disableScissor();
        }

        if (all.size() > visible) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.scroll",
                            scroll + 1, Math.min(scroll + visible, all.size()), all.size()),
                    sx, scrollCounterY(), TabletGui.TEXT_MUTED, false);
            float thumb = visible / (float) all.size();
            float progress = scroll / (float) Math.max(1, all.size() - visible);
            TabletGui.drawScrollbar(graphics, sx + screenW() - 6, listTop(), visible * rowH(), progress, thumb);
        }

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
        if (hoverTip != null || hoverTipLines != null || hoverCargoComponent != null) {
            return;
        }
        getChildAt(mouseX, mouseY).ifPresent(widget -> {
            if (widget instanceof TabletButton button && button.hint() != null) {
                hoverTip = button.hint();
            }
        });
    }

    private void renderTopLayerTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoverCargoComponent != null) {
            List<Component> lines = hoverTip == null ? List.of() : List.of(hoverTip);
            graphics.renderTooltip(font, lines, Optional.of(hoverCargoComponent), mouseX, mouseY);
            return;
        }
        if (hoverTipLines != null && !hoverTipLines.isEmpty()) {
            graphics.renderTooltip(font, hoverTipLines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoverTip != null) {
            graphics.renderTooltip(font, List.of(hoverTip), Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderOrderRow(GuiGraphics graphics, BoardSnapshotPayload.BoardEntry entry, int rowTop, int x, int width,
                                int mouseX, int mouseY) {
        BoardRowRenderer.renderLine1(
                graphics, font, entry, x, BoardRowMetrics.line1Y(rowTop), width, mouseX, mouseY, false, null);
        BoardRowRenderer.renderLine2(graphics, font, entry, x, BoardRowMetrics.line2Y(rowTop));
    }

    private void captureRowHoverTips(int mouseX, int mouseY) {
        if (snapshot == null || tab != Tab.BOARD && tab != Tab.MINE) {
            return;
        }
        if (hoverTip != null || hoverTipLines != null || hoverCargoComponent != null || isOverRowIcon(mouseX, mouseY)) {
            return;
        }
        List<BoardSnapshotPayload.BoardEntry> all = listEntries();
        int visible = visibleRows();
        int listY = listTop();
        int sx = screenLeft();
        int sw = screenW();
        BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, true);
        int textRight = layout.textRight();
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int y = listY + i * rowH();
            if (mouseX < sx + 6 || mouseX >= textRight || mouseY < y || mouseY >= y + rowH()) {
                continue;
            }
            if (isOverRowAction(mouseX, mouseY) || isOverCustomerArea(entry, y, textRight, mouseX, mouseY)) {
                continue;
            }
            hoverTipLines = List.of(
                    Component.translatable("ui.aerocouriers.row.summary.reward", entry.reward()),
                    Component.translatable("ui.aerocouriers.row.summary.collateral", entry.collateral()),
                    Component.translatable("ui.aerocouriers.row.summary.rank", entry.requiredRank().displayName()),
                    Component.translatable("ui.aerocouriers.row.summary.deadline", entry.deadlineMinutes()),
                    Component.translatable("ui.aerocouriers.row.summary.time", entry.minutesLeft()),
                    Component.translatable("ui.aerocouriers.row.summary.cargo", entry.weight(), entry.size()),
                    Component.translatable("ui.aerocouriers.row.summary.distance", entry.distance()),
                    Component.translatable("ui.aerocouriers.row.summary.role",
                            Component.translatable("contract.aerocouriers.role." + entry.role())));
            return;
        }
    }

    private void captureRowLine1HoverTips(int mouseX, int mouseY) {
        if (snapshot == null || tab != Tab.BOARD && tab != Tab.MINE) {
            return;
        }
        if (hoverTip != null || hoverTipLines != null || hoverCargoComponent != null || isOverRowIcon(mouseX, mouseY)) {
            return;
        }
        if (isOverRowAction(mouseX, mouseY)) {
            return;
        }
        List<BoardSnapshotPayload.BoardEntry> all = listEntries();
        int visible = visibleRows();
        int listY = listTop();
        int sx = screenLeft();
        int sw = screenW();
        int textLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE + BoardRowMetrics.ROW_INSET_LEFT;
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int rowY = listY + i * rowH();
            if (mouseY < rowY || mouseY >= rowY + rowH()) {
                continue;
            }
            boolean hasSecondary = hasSecondaryAction(entry, tab == Tab.BOARD);
            BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, hasSecondary);
            if (mouseX < textLeft || mouseX >= layout.textRight()) {
                return;
            }
            int textW = Math.max(8, layout.textRight() - textLeft);
            BoardRowRenderer.Line1Hover line1 = BoardRowRenderer.hitLine1Hover(
                    font,
                    entry,
                    textLeft,
                    BoardRowMetrics.line1Y(rowY),
                    textW,
                    mouseX,
                    mouseY,
                    (tip, ignored) -> hoverTip = tip);
            if (line1.tooltip() != null) {
                hoverTip = line1.tooltip();
            }
            return;
        }
    }

    private boolean isOverRowIcon(int mouseX, int mouseY) {
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton icon && icon.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverRowAction(int mouseX, int mouseY) {
        for (Renderable widget : renderables) {
            if (widget instanceof AbstractWidget w && w.isMouseOver(mouseX, mouseY)) {
                if (w instanceof TabletButton || w instanceof TabletRowIconButton) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateRowIconHover(int mouseX, int mouseY) {
        UUID nextMap = null;
        UUID nextCrate = null;
        for (Renderable widget : renderables) {
            if (widget instanceof TabletRowIconButton icon && icon.isMouseOver(mouseX, mouseY)) {
                if (icon.kind() == TabletRowIconButton.Kind.MAP) {
                    nextMap = icon.orderId();
                } else {
                    nextCrate = icon.orderId();
                }
            }
        }
        final UUID hoveredMap = nextMap;
        if (hoveredMap != null && snapshot != null) {
            snapshot.find(hoveredMap).ifPresent(entry -> {
                hoverTip = buildMapTooltip(entry);
                if (!Objects.equals(mapHoverOrder, hoveredMap)) {
                    XaeroOrderBridge.onHoverDestination(entry.id(), entry.destinationDimension(), entry.destinationPos(),
                            entry.shortId());
                }
            });
        } else if (mapHoverOrder != null && snapshot != null) {
            snapshot.find(mapHoverOrder).ifPresent(e -> {
                if (!keepsPersistentWaypoint(e)) {
                    XaeroOrderBridge.onLeaveDestination(e.id());
                }
            });
            if (nextCrate == null) {
                hoverTip = null;
            }
        }
        mapHoverOrder = nextMap;

        if (nextCrate != null && snapshot != null) {
            snapshot.find(nextCrate).ifPresent(this::applyCrateHover);
        } else {
            hoverCargoComponent = null;
        }
        crateHoverOrder = nextCrate;
    }

    private void applyCrateHover(BoardSnapshotPayload.BoardEntry entry) {
        List<net.minecraft.world.item.ItemStack> stacks = entry.cargoStacks();
        int shown = Math.min(BoardCargoTooltip.MAX_SHOWN, stacks.size());
        int hidden = Math.max(0, entry.manifest().size() - shown);
        hoverCargoComponent = new BoardCargoTooltip(
                stacks.subList(0, shown),
                hidden,
                entry.weight());
        hoverTip = Component.translatable("ui.aerocouriers.row.cargo_heading");
    }

    private Component buildMapTooltip(BoardSnapshotPayload.BoardEntry entry) {
        int distance = XaeroOrderBridge.distanceBlocks(entry.destinationPos(), entry.destinationDimension());
        if (distance < 0) {
            distance = entry.distance();
        }
        Component pickup = formatPos(entry.originPos(), entry.originDimension());
        Component delivery = formatPos(entry.destinationPos(), entry.destinationDimension());
        if (XaeroOrderBridge.xaeroMinimapPresent()) {
            return Component.translatable("ui.aerocouriers.row.map_tooltip.xaero",
                    pickup, delivery, distance,
                    Component.translatable("ui.aerocouriers.row.map_open_hint"));
        }
        return Component.translatable("ui.aerocouriers.row.map_tooltip", pickup, delivery, distance);
    }

    private static Component formatPos(BlockPos pos, ResourceKey<Level> dimension) {
        return Component.translatable("ui.aerocouriers.row.coords",
                pos.getX(), pos.getY(), pos.getZ(), dimension.location().toString());
    }

    private boolean keepsPersistentWaypoint(BoardSnapshotPayload.BoardEntry entry) {
        return "courier".equals(entry.role()) && entry.state().isActive()
                && ClientZones.get().stream().anyMatch(z -> z.orderId().equals(entry.id()) && z.delivery());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (!isOverRowAction(mx, my)) {
                BoardSnapshotPayload.BoardEntry customer = hitCustomerRow(mx, my);
                if (customer != null) {
                    onCustomerClick(customer);
                    return true;
                }
                BoardSnapshotPayload.BoardEntry row = hitRowBody(mx, my);
                if (row != null) {
                    selected = row.id();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** TODO(profile): open the customer's profile when cross-player profiles exist. */
    private void onCustomerClick(BoardSnapshotPayload.BoardEntry entry) {
        entry.customerId();
    }

    private @Nullable BoardSnapshotPayload.BoardEntry hitCustomerRow(int mouseX, int mouseY) {
        List<BoardSnapshotPayload.BoardEntry> all = listEntries();
        int visible = visibleRows();
        int listY = listTop();
        int sx = screenLeft();
        BoardRowLayout layout = BoardRowLayout.forRow(sx, screenW(), true);
        int textRight = layout.textRight();
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int rowY = listY + i * rowH();
            if (isOverCustomerArea(entry, rowY, textRight, mouseX, mouseY)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isOverCustomerArea(BoardSnapshotPayload.BoardEntry entry, int rowY, int textRight, int mouseX,
                                       int mouseY) {
        int line1Y = BoardRowMetrics.line1Y(rowY);
        if (mouseY < line1Y || mouseY >= line1Y + 9) {
            return false;
        }
        int sx = screenLeft();
        int textLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE + BoardRowMetrics.ROW_INSET_LEFT;
        int faceStart = textLeft + font.width("#" + entry.shortId()) + BoardRowMetrics.ID_STATUS_GAP
                + Math.min(font.width(entry.state().displayName().getString()), 88) + 4;
        int faceEnd = faceStart + BoardRowMetrics.CUSTOMER_FACE + BoardRowMetrics.FACE_NAME_GAP
                + font.width(entry.customerName());
        return mouseX >= faceStart && mouseX < Math.min(faceEnd, textRight);
    }

    private @Nullable BoardSnapshotPayload.BoardEntry hitRowBody(int mouseX, int mouseY) {
        List<BoardSnapshotPayload.BoardEntry> all = listEntries();
        int visible = visibleRows();
        int listY = listTop();
        int sx = screenLeft();
        int sw = screenW();
        int frameLeft = sx + BoardRowMetrics.ROW_FRAME_SIDE;
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int rowY = listY + i * rowH();
            if (mouseY < rowY || mouseY >= rowY + rowH()) {
                continue;
            }
            if (isOverCustomerArea(entry, rowY, Integer.MAX_VALUE, mouseX, mouseY)) {
                continue;
            }
            boolean hasSecondary = hasSecondaryAction(entry, tab == Tab.BOARD);
            BoardRowLayout layout = BoardRowLayout.forRow(sx, sw, hasSecondary);
            if (mouseX >= layout.mapX()) {
                continue;
            }
            int frameRight = layout.hasSecondary()
                    ? layout.secondaryX() + BoardRowLayout.SECONDARY_W
                    : layout.crateX() + BoardRowLayout.ICON;
            if (mouseX >= frameLeft && mouseX < frameRight) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            List<BoardSnapshotPayload.BoardEntry> all = listEntries();
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
        return Mth.clamp((int) (width * 0.72), 400, Math.max(400, width - 48));
    }

    private int panelH() {
        int maxH = TabletGui.maxFrameHeight(height);
        int preferred = (int) (height * 0.80);
        return Math.min(maxH, Math.max(200, preferred));
    }

    private int left() {
        return TabletGui.frameLeft(width, panelW());
    }

    private int top() {
        return TabletGui.frameTop(height, panelH());
    }

    private int cavityLeft() {
        return TabletGui.cavityLeft(left());
    }

    private int cavityTop() {
        return TabletGui.cavityTop(top());
    }

    private int cavityW() {
        return TabletGui.cavityWidth(panelW());
    }

    private int cavityH() {
        return TabletGui.cavityHeight(panelH());
    }

    /** Padded content area inside the screen cavity (tabs, lists, profile). */
    private int screenLeft() {
        return TabletGui.contentLeft(left());
    }

    private int screenTop() {
        return TabletGui.contentTop(top());
    }

    private int screenW() {
        return TabletGui.contentWidth(panelW());
    }

    private int screenH() {
        return TabletGui.contentHeight(panelH());
    }

    private int rowH() {
        return BoardRowMetrics.rowHeight();
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
        return mainTabRowY() + TAB_H + HEADING_GAP;
    }

    private static boolean showsTraineeHint(BoardSnapshotPayload.BoardProfile profile) {
        return profile.traineeMaxDistance() > 0
                && profile.villageDelivered() < profile.traineeFreeContracts();
    }

    private int listTop() {
        int extra = traineeHintBlockHeight();
        if (tab == Tab.MINE) {
            extra += font.lineHeight + MINE_SUB_GAP + MINE_SUB_TOGGLE_H + MINE_SUB_GAP;
        }
        return headingY() + font.lineHeight + 6 + extra;
    }

    private int traineeHintBlockHeight() {
        if (snapshot == null || tab != Tab.BOARD || !showsTraineeHint(snapshot.profile())) {
            return 0;
        }
        Component hint = Component.translatable("ui.aerocouriers.trainee.hint",
                snapshot.profile().traineeFreeContracts() - snapshot.profile().villageDelivered(),
                snapshot.profile().traineeMaxDistance());
        int lines = font.split(hint, screenW()).size();
        return lines * font.lineHeight + 4;
    }

    private int mineSubToggleY() {
        return headingY() + font.lineHeight + MINE_SUB_GAP;
    }

    private List<BoardSnapshotPayload.BoardEntry> listEntries() {
        if (snapshot == null) {
            return List.of();
        }
        return listEntriesForTab(snapshot);
    }

    private List<BoardSnapshotPayload.BoardEntry> listEntriesForTab(BoardSnapshotPayload payload) {
        if (tab == Tab.BOARD) {
            return payload.board();
        }
        if (tab == Tab.MINE) {
            return MineListSection.entries(payload.mine(), ClientBoard.mineSection());
        }
        return List.of();
    }

    private Component listEmptyMessage() {
        if (tab == Tab.MINE) {
            return ClientBoard.mineSection() == MineListSection.CURRENT
                    ? Component.translatable("ui.aerocouriers.mine.empty.current")
                    : Component.translatable("ui.aerocouriers.mine.empty.history");
        }
        return Component.translatable("contract.aerocouriers.list.none");
    }

    private void focusScrollToSelected() {
        if (selected == null || snapshot == null) {
            return;
        }
        List<BoardSnapshotPayload.BoardEntry> all = listEntries();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(selected)) {
                int visible = visibleRows();
                scroll = Mth.clamp(i - visible / 2, 0, Math.max(0, all.size() - visible));
                return;
            }
        }
    }

    private int contentBottom() {
        return screenTop() + screenH() - SCREEN_BOTTOM_PAD;
    }

    private int scrollCounterY() {
        return contentBottom() - SCROLL_COUNTER_H;
    }

    private int listBottom() {
        int bottom = contentBottom() - 4;
        if (snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            List<BoardSnapshotPayload.BoardEntry> all = listEntries();
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
