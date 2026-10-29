package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.board.BoardCargoTooltip;
import dev.poligorn.aerocouriers.client.board.BoardRowDebug;
import dev.poligorn.aerocouriers.client.board.BoardRowLayout;
import dev.poligorn.aerocouriers.client.board.BoardRowMetrics;
import dev.poligorn.aerocouriers.client.board.BoardAcceptOverlay;
import dev.poligorn.aerocouriers.client.board.BoardActionConfirmOverlay;
import dev.poligorn.aerocouriers.client.board.BoardConfirmOverlay;
import dev.poligorn.aerocouriers.client.board.BoardAcceptUi;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.client.board.BoardDetailsOverlay;
import dev.poligorn.aerocouriers.client.board.BoardOverlayHost;
import dev.poligorn.aerocouriers.client.board.TabletDraw;
import dev.poligorn.aerocouriers.client.board.TabletPalette;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.client.board.BoardEntryPickup;
import dev.poligorn.aerocouriers.client.board.BoardRowRenderer;
import dev.poligorn.aerocouriers.client.board.MineListSection;
import dev.poligorn.aerocouriers.client.board.ProfileRankBar;
import dev.poligorn.aerocouriers.client.board.ProfileRankTooltip;
import dev.poligorn.aerocouriers.client.ProfileSkinPreviewLayout;
import dev.poligorn.aerocouriers.client.ProfileSkinRenderer;
import dev.poligorn.aerocouriers.client.board.TabletFilterBar;
import dev.poligorn.aerocouriers.client.board.TabletFilterChip;
import dev.poligorn.aerocouriers.client.board.TabletFilterDropdown;
import dev.poligorn.aerocouriers.contract.RankProgress;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.contract.AcceptEligibility;
import dev.poligorn.aerocouriers.client.board.TabletRowIconButton;
import dev.poligorn.aerocouriers.client.board.VillageRefreshUi;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.contract.TraineeRules;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.InboxClaimPayload;
import dev.poligorn.aerocouriers.net.InboxClearPlainPayload;
import dev.poligorn.aerocouriers.net.InboxDismissPayload;
import dev.poligorn.aerocouriers.net.MarkNotificationsReadPayload;
import dev.poligorn.aerocouriers.net.NotificationSyncPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import dev.poligorn.aerocouriers.net.PlayerProfilePayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.components.Renderable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
    private static final int SCREEN_BOTTOM_PAD = 4;
    private static final int SCROLL_COUNTER_H = 12;
    private static final int BALANCE_RIGHT_PAD = 8;
    private static final int INBOX_ROW_MIN_H = 22;
    private static final int INBOX_ROW_PAD_V = 4;
    private static final int INBOX_ICON = 14;
    private static final int MINE_SUB_TOGGLE_H = 18;
    private static final int MINE_SUB_GAP = 4;
    static final int ADMIN_SUB_TOGGLE_H = 18;
    static final int ADMIN_SUB_GAP = 4;
    private static final int FILTER_SUMMARY_H = 11;
    private static final int VILLAGE_REFRESH_HEADER_H = 11;
    private static final int STATUS_LINE_H = 11;
    private static final int LIST_HEADING_H = 11;
    private static final int COUNT_LABEL_H = 13;

    /** Left edge of the balance strip (right of last tab). */
    private int balanceStripLeft;
    private final List<AbstractWidget> mineSubToggles = new ArrayList<>();
    private final List<AbstractWidget> filterWidgets = new ArrayList<>();
    private final List<AbstractWidget> adminFilterWidgets = new ArrayList<>();
    private int filterBarHeight;
    private int adminFilterBarHeight;
    private final Map<Tab, TabletTabButton> mainTabs = new EnumMap<>(Tab.class);
    private final BoardAdminUi adminUi = new BoardAdminUi(this);

    enum Tab {
        BOARD, MINE, PROFILE, MAIL, ADMIN
    }

    private @Nullable BoardSnapshotPayload snapshot;
    private Tab tab = Tab.BOARD;
    private int scroll;
    private @Nullable UUID selected;
    private @Nullable Component hoverTip;
    private @Nullable List<Component> hoverTipLines;
    private @Nullable BoardCargoTooltip hoverCargoComponent;
    private @Nullable SpurBreakdownTooltip hoverSpurBreakdown;
    private @Nullable Component hoverSpurHeader;
    private @Nullable ItemStack hoverItem;
    /** Visible list rows whose action buttons depend on {@link #initListButtons}; avoids rebuild on timer-only updates. */
    private @Nullable String listRowButtonKey;
    private @Nullable ProfileRankBar.Layout profileRankBarLayout;
    private final TabletScreenChrome tabletChrome = new TabletScreenChrome(false);
    private ScreenFit fit = ScreenFit.of(ScreenFit.DESIGN_W, ScreenFit.DESIGN_H);
    private final TabletTabSwitchEffects tabEffects = new TabletTabSwitchEffects();
    private final TabletListRowEffects listRowEffects = new TabletListRowEffects();
    private final TabletListRowEffects mailRowEffects = new TabletListRowEffects();
    private final ProfileXpBarEffects profileXpEffects = new ProfileXpBarEffects();
    private int mailScroll;
    private @Nullable String mailRowButtonKey;
    private final BoardOverlayHost overlayHost = new OverlayHost();
    private @Nullable BoardDetailsOverlay detailsOverlay;
    private @Nullable BoardConfirmOverlay confirmOverlay;

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
        if (previous == null || previous.operator() != payload.operator()) {
            rebuildMainTabs();
            listRowButtonKey = listRowButtonKey(payload);
            refreshBoardContent();
        } else if (needsContentWidgetRefresh(previous, payload)) {
            listRowButtonKey = listRowButtonKey(payload);
            refreshContentWidgets();
        }
        if (previous == null || previous.mailCount() != payload.mailCount()
                || previous.notificationUnread() != payload.notificationUnread()) {
            onInboxCountsChanged();
        }
        if (tab == Tab.PROFILE) {
            removeTabletButtons();
            initProfileButtons();
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
        if (value != tab) {
            tabEffects.onTabSwitch(mainTabs.get(tab), mainTabs.get(value));
            if (value == Tab.PROFILE) {
                ClientBoard.clearViewedProfile();
                pulseProfileXpBar();
            }
            if (value == Tab.MAIL) {
                PacketDistributor.sendToServer(MarkNotificationsReadPayload.INSTANCE);
            }
        }
        tab = value;
        scroll = 0;
        mailScroll = 0;
        listRowButtonKey = null;
        mailRowButtonKey = null;
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
        removeFilterWidgets();
        removeAdminFilterWidgets();
        removeTabletButtons();
        if (snapshot == null) {
            return;
        }
        if (tab == Tab.ADMIN) {
            initAdmin();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            initFilterBar(screenLeft());
            if (tab == Tab.MINE) {
                initMineSubToggles(screenLeft());
            }
            initListButtons(listEntries(), tab == Tab.BOARD);
        }
        if (tab == Tab.BOARD || tab == Tab.MINE) {
            listRowButtonKey = listRowButtonKey(snapshot);
        } else if (tab == Tab.PROFILE) {
            initProfileButtons();
        } else if (tab == Tab.MAIL) {
            initMailTab();
        }
    }

    void openViewedProfile(PlayerProfilePayload payload) {
        closeOverlays();
        Tab previous = tab;
        if (previous != Tab.PROFILE) {
            tabEffects.onTabSwitch(mainTabs.get(previous), mainTabs.get(Tab.PROFILE));
        }
        tab = Tab.PROFILE;
        scroll = 0;
        listRowButtonKey = null;
        syncMainTabSelection();
        pulseProfileXpBar();
        refreshBoardContent();
        init();
    }

    /** Drops and rebuilds row/test {@link TabletButton}s only; tabs and mail stay mounted. */
    private void refreshContentWidgets() {
        removeMineSubToggles();
        removeFilterWidgets();
        removeAdminFilterWidgets();
        removeTabletButtons();
        if (snapshot == null) {
            return;
        }
        if (tab == Tab.ADMIN) {
            initAdmin();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            initFilterBar(screenLeft());
            if (tab == Tab.MINE) {
                initMineSubToggles(screenLeft());
            }
            initListButtons(listEntries(), tab == Tab.BOARD);
        }
    }

    private void onFiltersChanged() {
        scroll = 0;
        listRowButtonKey = null;
        refreshBoardContent();
    }

    private void initFilterBar(int sx) {
        filterBarHeight = 0;
        if (snapshot == null || (tab != Tab.BOARD && tab != Tab.MINE)) {
            return;
        }
        int top = filterBarTop();
        TabletFilterBar.Built built = tab == Tab.BOARD
                ? TabletFilterBar.buildBoard(sx, top, screenW(), this::onFiltersChanged)
                : TabletFilterBar.buildMine(sx, top, screenW(), ClientBoard.mineSection(), this::onFiltersChanged);
        filterBarHeight = built.height();
        for (AbstractWidget widget : built.widgets()) {
            filterWidgets.add(widget);
            addRenderableWidget(widget);
        }
    }

    private void removeFilterWidgets() {
        for (AbstractWidget widget : filterWidgets) {
            removeWidget(widget);
        }
        filterWidgets.clear();
        filterBarHeight = 0;
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
            if (!payload.operator() && tab == Tab.ADMIN) {
                tab = Tab.BOARD;
                refreshBoardContent();
            }
        }
    }

    private void initProfileButtons() {
        if (snapshot == null) {
            return;
        }
        BoardSnapshotPayload.BoardProfile profile = ClientBoard.activeProfile(snapshot.profile());
        int sx = screenLeft();
        int top = listTop();
        int panelW = screenW();
        if (ClientBoard.viewingOtherProfile()) {
            addRenderableWidget(profileCloseButton(sx, panelW));
        }
        if (!ClientBoard.viewingOtherProfile() && profile.neverHadPilotLicense()) {
            int btnW = Math.min(profileStatsWidth(sx), 220);
            addRenderableWidget(TabletButton.create(profileStatsX(sx), profileTraineeButtonY(profile), btnW, 18,
                    Component.translatable("ui.aerocouriers.profile.claim_trainee"),
                    b -> ClientBoard.claimTraineeLicense()));
        }
    }

    private void pulseProfileXpBar() {
        if (snapshot == null) {
            return;
        }
        BoardSnapshotPayload.BoardProfile profile = ClientBoard.activeProfile(snapshot.profile());
        if (profile.neverHadPilotLicense()) {
            return;
        }
        LicenseRank rank = profile.rank();
        RankProgress.State progress = RankProgress.state(profile.xp(), rank, profile.rankXpThresholds());
        profileXpEffects.onProfileTabShown(profile.xp(), progress.fraction());
    }

    @Override
    protected void init() {
        refreshFit();
        super.init();
        rebuildMainTabs();
        if (snapshot == null) {
            return;
        }
        if (tab == Tab.ADMIN) {
            initAdmin();
        } else if (tab == Tab.BOARD || tab == Tab.MINE) {
            int sx = screenLeft();
            initFilterBar(sx);
            if (tab == Tab.MINE) {
                initMineSubToggles(sx);
            }
            initListButtons(listEntries(), tab == Tab.BOARD);
        } else if (tab == Tab.PROFILE) {
            initProfileButtons();
        } else if (tab == Tab.MAIL) {
            initMailTab();
        }
        if (tab == Tab.BOARD || tab == Tab.MINE) {
            listRowButtonKey = listRowButtonKey(snapshot);
        }
    }

    private void rebuildMainTabs() {
        for (TabletTabButton button : mainTabs.values()) {
            removeWidget(button);
        }
        mainTabs.clear();
        int sx = screenLeft();
        int tabRowY = mainTabRowY();
        int tabW = Math.max(64, screenW() / 7);
        int tabX = sx;
        tabX = addTab(tabX, tabRowY, tabW, Tab.BOARD, "ui.aerocouriers.tab.board", 0) + 4;
        tabX = addTab(tabX, tabRowY, tabW, Tab.MINE, "ui.aerocouriers.tab.mine", 0) + 4;
        tabX = addTab(tabX, tabRowY, tabW, Tab.PROFILE, "ui.aerocouriers.tab.profile", 0) + 4;
        tabX = addTab(tabX, tabRowY, tabW, Tab.MAIL, "ui.aerocouriers.tab.mail", inboxUnreadCount()) + 4;
        if (snapshot != null && snapshot.operator()) {
            tabX = addTab(tabX, tabRowY, tabW, Tab.ADMIN, "ui.aerocouriers.tab.admin", 0) + 4;
        }
        balanceStripLeft = tabX + 4;
        syncMainTabSelection();
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
        adminUi.clearSubToggles();
    }

    void onNotificationsChanged() {
        onInboxCountsChanged();
    }

    private void onInboxCountsChanged() {
        TabletTabButton mail = mainTabs.get(Tab.MAIL);
        if (mail != null) {
            mail.badge(inboxUnreadCount());
        }
        if (tab == Tab.MAIL) {
            mailRowButtonKey = null;
            removeTabletButtons();
            initMailTab();
        }
    }

    private int inboxUnreadCount() {
        if (snapshot != null) {
            return Math.max(snapshot.notificationUnread(), ClientNotifications.unreadCount());
        }
        return ClientNotifications.unreadCount();
    }

    /** Returns x position after this tab (for laying out the next one). */
    private int addTab(int x, int y, int w, Tab value, String key, int badge) {
        TabletTabButton button = new TabletTabButton(x, y, w, TAB_H, Component.translatable(key), () -> selectTab(value))
                .badge(badge);
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
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visibleRows()));
    }

    private boolean cardLocked(BoardSnapshotPayload.BoardEntry entry) {
        return tab == Tab.BOARD && snapshot != null && entry.state() == ContractState.OPEN
                && BoardAcceptUi.rowRankLocked(entry, snapshot.profile().rank());
    }

    private boolean customerOnline(BoardSnapshotPayload.BoardEntry entry) {
        if (minecraft == null || minecraft.getConnection() == null || entry.customerId().isEmpty()) {
            return false;
        }
        return minecraft.getConnection().getPlayerInfo(entry.customerId().get()) != null;
    }

    private void openDetails(UUID orderId) {
        if (snapshot == null || snapshot.find(orderId).isEmpty()) {
            return;
        }
        selected = orderId;
        confirmOverlay = null;
        detailsOverlay = new BoardDetailsOverlay(overlayHost, orderId);
    }

    private static boolean activeHaul(BoardSnapshotPayload.BoardEntry entry) {
        return "courier".equals(entry.role()) && entry.state().isActive();
    }

    private boolean overlayOpen() {
        return detailsOverlay != null || confirmOverlay != null;
    }

    private void closeTopOverlay() {
        if (confirmOverlay != null) {
            confirmOverlay = null;
        } else {
            detailsOverlay = null;
        }
    }

    private void closeOverlays() {
        confirmOverlay = null;
        detailsOverlay = null;
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private long collateralDue(BoardSnapshotPayload.BoardEntry entry) {
        if (snapshot == null) {
            return entry.collateral();
        }
        return TraineeRules.collateralDueAtAccept(snapshot.profile().rank(), entry.collateral(),
                entry.villageCustomer(), snapshot.profile().villageDelivered(),
                snapshot.profile().traineeFreeContracts());
    }

    void refreshAdminContent() {
        refreshBoardContent();
    }

    void selectAdminContract(UUID id) {
        selected = id;
        refreshAdminContent();
    }

    int adminContentBottom() {
        return screenTop() + screenH() - 12;
    }

    void adminAddWidget(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    void adminRemoveWidget(AbstractWidget widget) {
        removeWidget(widget);
    }

    net.minecraft.client.gui.Font adminFont() {
        return font;
    }

    net.minecraft.client.Minecraft adminMc() {
        return minecraft;
    }

    private void initAdmin() {
        if (snapshot == null) {
            return;
        }
        int sx = screenLeft();
        int sw = screenW();
        int subY = adminUi.subToggleY(headingY(), font.lineHeight);
        adminUi.initSubToggles(sx, subY, sw);
        removeAdminFilterWidgets();
        adminFilterBarHeight = 0;
        int rowY;
        if (adminUi.subTab() == BoardAdminUi.SubTab.ORDERS) {
            initAdminFilterBar(sx, adminUi.contentTop(subY));
            rowY = adminOrdersRowY(subY);
        } else {
            rowY = adminUi.contentTop(subY);
        }
        adminUi.initContent(snapshot, sx, rowY, sw, selected);
    }

    private void initAdminFilterBar(int sx, int y) {
        adminFilterBarHeight = 0;
        if (adminUi.subTab() != BoardAdminUi.SubTab.ORDERS) {
            return;
        }
        TabletFilterBar.Built built = TabletFilterBar.buildAdmin(sx, y, screenW(), this::onAdminFiltersChanged);
        adminFilterBarHeight = built.height();
        for (AbstractWidget widget : built.widgets()) {
            adminFilterWidgets.add(widget);
            addRenderableWidget(widget);
        }
    }

    private void removeAdminFilterWidgets() {
        for (AbstractWidget widget : adminFilterWidgets) {
            removeWidget(widget);
        }
        adminFilterWidgets.clear();
        adminFilterBarHeight = 0;
    }

    private void onAdminFiltersChanged() {
        refreshAdminContent();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshFit();
        tabletChrome.layout(guiH(), top(), panelH());
        tabletChrome.renderBackground(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        hoverTipLines = null;
        hoverCargoComponent = null;
        hoverSpurBreakdown = null;
        hoverSpurHeader = null;
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        refreshFit();
        int mx = fit.localXi(mouseX);
        int my = fit.localYi(mouseY);
        tabletChrome.tick();
        graphics.pose().pushPose();
        fit.apply(graphics);
        tabletChrome.layout(guiH(), top(), panelH());
        int overlayMouseY = (int) tabletChrome.adjustMouseY(my, partialTick);
        syncOverlays();
        boolean overlay = overlayOpen();
        int contentMouseX = overlay ? -1 : mx;
        int contentMouseY = overlay ? -1 : overlayMouseY;
        boolean tabLoading = tabEffects.isTabLoadingActive();
        float cavityAlpha = tabletChrome.cavityContentAlpha(snapshot == null, tabLoading, partialTick);
        float contentAlpha = tabEffects.contentAlpha() * cavityAlpha;
        tabletChrome.drawTabletBody(graphics, partialTick, () -> {
            TabletGui.drawScreenBackground(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH(), partialTick);
            if (cavityAlpha > 0.001F) {
                TabletAnim.withAlpha(graphics, contentAlpha, () -> {
                    tabEffects.drawHighlight(graphics, mainTabRowY(), TAB_H);
                    renderChromeWidgets(graphics, contentMouseX, contentMouseY, partialTick);
                    renderContentWidgets(graphics, contentMouseX, contentMouseY, partialTick);
                    drawTabPanelContent(graphics, contentMouseX, contentMouseY, partialTick);
                    if (tab == Tab.PROFILE) {
                        renderProfileWidgetsOnTop(graphics, contentMouseX, contentMouseY, partialTick);
                    }
                });
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (overlay && cavityAlpha > 0.001F) {
                renderOverlays(graphics, mx, overlayMouseY, partialTick);
            }
            TabletGui.drawFrameChrome(graphics, left(), top(), panelW(), panelH());
            tabletChrome.drawLoadingGear(graphics, cavityLeft(), cavityTop(), cavityW(), cavityH(),
                    snapshot == null, tabLoading, partialTick);
        });
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (!tabEffects.isTabLoadingActive()) {
            captureCardHover(contentMouseX, contentMouseY);
            if (hoverTip == null && hoverTipLines == null && hoverCargoComponent == null && hoverSpurBreakdown == null) {
                captureProfileRankHover(contentMouseX, contentMouseY);
            }
            if (hoverTip == null && hoverTipLines == null && hoverCargoComponent == null && hoverSpurBreakdown == null) {
                captureButtonHint(contentMouseX, contentMouseY);
            }
        }
        if (overlay) {
            captureOverlayHover();
        }
        graphics.pose().popPose();
        renderTopLayerTooltips(graphics, mouseX, mouseY);
    }

    /** Drops overlays whose order left the snapshot (accepted elsewhere, expired, cancelled). */
    private void renderStatusLine(GuiGraphics graphics, int sx) {
        BoardSnapshotPayload.BoardProfile profile = snapshot.profile();
        LicenseRank rank = profile.rank();
        String roman = BoardCardRenderer.roman(rank);
        String title = rank.displayName().getString();
        String left = title.startsWith(roman + " ") ? roman + " · " + title.substring(roman.length() + 1) : title;
        int y = statusLineY();
        graphics.drawString(font, left, sx + 1, y, TabletPalette.SEC, false);

        int active = BoardAcceptUi.countActiveHauls(snapshot.mine());
        int maxActive = RankRules.limits(rank).maxActive();
        long atStake = 0;
        for (BoardSnapshotPayload.BoardEntry entry : snapshot.mine()) {
            if ("courier".equals(entry.role()) && entry.state().isActive()) {
                atStake += entry.collateral();
            }
        }
        String head = Component.translatable("ui.aerocouriers.status.active", active, maxActive).getString();
        String amount = dev.poligorn.aerocouriers.economy.MoneyFormat.text(atStake);
        if (atStake > 0) {
            head += " · " + Component.translatable("ui.aerocouriers.status.collateral").getString() + " ";
        }
        int right = sx + screenW();
        int amountW = atStake > 0 ? MoneyText.width(font, amount) : 0;
        int x = right - font.width(head) - amountW;
        x = Math.max(x, sx + font.width(left) + 12);
        graphics.drawString(font, head, x, y, TabletPalette.SEC, false);
        if (atStake > 0) {
            MoneyText.draw(graphics, font, amount, x + font.width(head), y, TabletPalette.CORAL, MoneyText.Face.DEFAULT);
        }
    }

    private void syncOverlays() {
        if (snapshot == null) {
            closeOverlays();
            return;
        }
        if (confirmOverlay != null && snapshot.find(confirmOverlay.orderId()).isEmpty()) {
            confirmOverlay = null;
        }
        if (detailsOverlay != null && snapshot.find(detailsOverlay.orderId()).isEmpty()) {
            detailsOverlay = null;
        }
    }

    private void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = cavityLeft();
        int cy = cavityTop();
        int cw = cavityW();
        int ch = cavityH();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 150.0F);
        graphics.fill(cx, cy, cx + cw, cy + ch, TabletPalette.SCRIM);
        graphics.pose().popPose();
        if (detailsOverlay != null) {
            detailsOverlay.setSuppressCargoItems(confirmOverlay != null);
            detailsOverlay.layout(cx + 4, cy + 4, cw - 8, ch - 8);
            detailsOverlay.render(graphics, confirmOverlay == null ? mouseX : -1, confirmOverlay == null ? mouseY : -1,
                    partialTick);
        }
        if (confirmOverlay != null) {
            if (detailsOverlay != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 350.0F);
                graphics.fill(cx, cy, cx + cw, cy + ch, TabletPalette.SCRIM);
                graphics.pose().popPose();
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 450.0F);
            }
            confirmOverlay.measure();
            confirmOverlay.layout(cx + 4, cy + 4, cw - 8, ch - 8);
            confirmOverlay.render(graphics, mouseX, mouseY, partialTick);
            if (detailsOverlay != null) {
                graphics.pose().popPose();
            }
        }
    }

    private void captureOverlayHover() {
        hoverTip = null;
        hoverTipLines = null;
        hoverCargoComponent = null;
        hoverSpurBreakdown = null;
        hoverSpurHeader = null;
        hoverItem = null;
        if (confirmOverlay != null) {
            hoverTipLines = confirmOverlay.hoverLines();
            return;
        }
        if (detailsOverlay != null) {
            hoverItem = detailsOverlay.hoveredStack();
            hoverTipLines = detailsOverlay.hoverLines();
        }
    }

    private void renderChromeWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable widget : renderables) {
            if (isMainChromeWidget(widget)) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderContentWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable widget : renderables) {
            if (isMainChromeWidget(widget) || isMailRowButton(widget)) {
                continue;
            }
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** Mail × / «Забрать» are drawn with the list scissor, not in the unclipped content pass. */
    private boolean isMailRowButton(Renderable widget) {
        return tab == Tab.MAIL && widget instanceof TabletButton button && button.getY() >= mailListTop();
    }

    private boolean isMainChromeWidget(Renderable widget) {
        if (widget instanceof TabletTabButton tabButton && mainTabs.containsValue(tabButton)) {
            return true;
        }
        return false;
    }

    private void drawTabPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int sx = screenLeft();
        int sy = screenTop();
        if (listTab()) {
            graphics.drawString(font, tabHeading(), sx + 1, listHeadingY(), TabletPalette.TXT, false);
        } else if (tab != Tab.PROFILE) {
            graphics.drawString(font, tabHeading(), sx, headingY(), TabletGui.TEXT, false);
        }
        if (snapshot != null && listTab()) {
            renderStatusLine(graphics, sx);
        }
        if (snapshot != null && tab == Tab.BOARD && showsTraineeHint(snapshot.profile())) {
            Component hint = Component.translatable("ui.aerocouriers.trainee.hint",
                    snapshot.profile().traineeFreeContracts() - snapshot.profile().villageDelivered(),
                    snapshot.profile().traineeMaxDistance());
            drawWrapped(graphics, hint, sx + 1, statusLineY() + STATUS_LINE_H, screenW(), TabletPalette.TEAL);
        }
        if (snapshot != null) {
            long balance = snapshot.balance();
            int right = sx + screenW() - 1;
            int w = SpurAmountUi.width(font, balance, false);
            int x = Math.max(balanceStripLeft, right - w);
            SpurAmountUi.Bounds balanceBounds = SpurAmountUi.drawCenteredInRow(
                    graphics, font, balance, x, mainTabRowY(), TAB_H, TabletPalette.GOLD);
            offerSpurTooltip(balance, balanceBounds, mouseX, mouseY);
        }

        if (snapshot == null) {
            return;
        }

        if (tab == Tab.BOARD || tab == Tab.MINE) {
            renderVillageRefreshHeader(graphics, sx);
            renderList(graphics, listEntries(), sx, mouseX, mouseY);
            renderListActionWidgetsOnTop(graphics, mouseX, mouseY, partialTick);
        } else if (tab == Tab.PROFILE) {
            renderProfile(graphics, ClientBoard.activeProfile(snapshot.profile()), sx, mouseX, mouseY);
        } else if (tab == Tab.MAIL) {
            renderInboxList(graphics, sx, mouseX, mouseY);
            renderMailActionWidgetsOnTop(graphics, mouseX, mouseY, partialTick);
        } else if (tab == Tab.ADMIN) {
            int subY = adminUi.subToggleY(headingY(), font.lineHeight);
            if (adminUi.subTab() == BoardAdminUi.SubTab.ORDERS) {
                renderAdminFilterChrome(graphics, sx, adminUi.contentTop(subY));
                int headerY = adminOrdersHeaderY(subY);
                int rowY = adminOrdersRowY(subY);
                adminUi.render(graphics, snapshot, sx, headerY, rowY, screenW(), selected);
            } else {
                adminUi.render(graphics, snapshot, sx, adminUi.contentTop(subY), adminUi.contentTop(subY),
                        screenW(), selected);
            }
        }
    }

    @Override
    public void onClose() {
        if (tabletChrome.openClose().isClosing()) {
            return;
        }
        ClientCouriersSounds.playTabletClose();
        tabletChrome.requestClose(super::onClose);
    }

    private boolean blocksTabPanelInput() {
        return tabletChrome.blocksTabContentInput(snapshot == null, tabEffects.isTabLoadingActive(), 0.0F);
    }

    private boolean isOverMainTab(double mouseX, double mouseY) {
        for (TabletTabButton button : mainTabs.values()) {
            if (button.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabletChrome.blocksInput(snapshot == null, 0.0F)) {
            return true;
        }
        if (overlayOpen()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                closeTopOverlay();
                return true;
            }
            if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (blocksTabPanelInput()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderProfile(GuiGraphics graphics, BoardSnapshotPayload.BoardProfile profile, int sx,
                               int mouseX, int mouseY) {
        profileRankBarLayout = null;
        int top = listTop();
        int bottom = screenTop() + screenH() - 12;
        int innerW = screenW() - 16;
        int innerX = sx + 8;
        int titleY = top + 6;
        Component title = Component.translatable("ui.aerocouriers.profile.heading", profile.playerName());
        drawClipped(graphics, title, innerX, titleY, innerW - 22, TabletGui.TEXT_BRIGHT, mouseX, mouseY);

        int contentTop = titleY + font.lineHeight + 6;
        int boxH = Math.max(48, bottom - contentTop);
        int boxW = profileSkinPanelWidth();
        int boxX = sx;
        int statsX = profileStatsX(sx);
        int statsW = profileStatsWidth(sx);

        TabletGui.drawListPanel(graphics, boxX, contentTop, boxW, boxH);
        LivingEntity previewEntity = resolveProfilePreviewEntity();
        if (previewEntity != null) {
            renderProfileSkin(graphics, boxX, contentTop, boxW, boxH, mouseX, mouseY, previewEntity);
        } else {
            renderProfileHeadFallback(graphics, boxX, contentTop, boxW, boxH, ClientBoard.viewedProfileId());
        }

        int y = contentTop + 4;
        LicenseRank rank = profile.rank();
        ItemStack licenseIcon = new ItemStack(ModItems.PILOT_LICENSE.get());
        licenseIcon.set(ModComponents.LICENSE_RANK.get(), rank);
        graphics.renderItem(licenseIcon, statsX, y);
        int rankTextX = statsX + PROFILE_ICON_SIZE + 4;
        boolean viewingOther = ClientBoard.viewingOtherProfile();
        if (profile.neverHadPilotLicense()) {
            drawClipped(graphics, rank.displayName(), rankTextX, y + 4, statsW - PROFILE_ICON_SIZE - 4,
                    TabletGui.TEXT_BRIGHT, mouseX, mouseY);
        } else {
            Component licenseLine = profile.licensedCourier()
                    ? Component.translatable("ui.aerocouriers.profile.rank_license",
                    rank.displayName(), Component.translatable("ui.aerocouriers.profile.license.pilot"))
                    : Component.translatable("ui.aerocouriers.profile.rank_license",
                    rank.displayName(), Component.translatable("ui.aerocouriers.profile.license.none_worn"));
            drawClipped(graphics, licenseLine, rankTextX, y + 4, statsW - PROFILE_ICON_SIZE - 4, TabletGui.TEXT_BRIGHT,
                    mouseX, mouseY);
        }
        y += PROFILE_ICON_SIZE + 4;

        y = drawProfileIssuerBlock(graphics, profile, statsX, y, statsW);
        y += 4;

        if (profile.neverHadPilotLicense()) {
            if (viewingOther) {
                drawProfileStatBlock(graphics,
                        Component.translatable("ui.aerocouriers.profile.license.none_other"),
                        statsX, y, statsW, TabletGui.TEXT_MUTED);
            }
        } else {
            RankProgress.State progress = RankProgress.state(profile.xp(), rank, profile.rankXpThresholds());
            profileXpEffects.onProfile(rank, progress.fraction(), profile.xp());
            float xpFraction = profileXpEffects.displayedFraction(progress.fraction());
            float rankFlash = profileXpEffects.rankFlashStrength();
            if (progress.maxRank()) {
                y = drawProfileStatBlock(graphics,
                        Component.translatable("ui.aerocouriers.profile.max_rank"), statsX, y, statsW, TabletGui.TEXT);
            } else {
                profileRankBarLayout = ProfileRankBar.draw(
                        graphics, font, statsX, y, statsW, xpFraction, progress.xpInRank(), progress.span(), rankFlash);
                y += profileRankBarLayout.blockH() + 2;
                LicenseRank next = RankProgress.nextRank(rank);
                y = drawProfileStatBlock(graphics,
                        Component.translatable("ui.aerocouriers.profile.xp_to_next", next.displayName(),
                                progress.remainingToNext()),
                        statsX, y, statsW, TabletGui.TEXT_MUTED);
                Optional<Component> gate = RankProgress.promotionRequirement(
                        profile.xp(), profile.delivered(), profile.failed(), rank, profile.rankXpThresholds(),
                        profile.aceMinSuccessPercent());
                if (gate.isPresent()) {
                    y = drawProfileStatBlock(graphics, gate.get(), statsX, y, statsW, TabletGui.TEXT_WARN);
                }
            }
            y += 4;
            y = drawProfileStatBlock(graphics,
                    Component.translatable("ui.aerocouriers.profile.delivered", profile.delivered()), statsX, y, statsW,
                    TabletGui.TEXT);
            y = drawProfileStatBlock(graphics,
                    Component.translatable("ui.aerocouriers.profile.failed", profile.failed()), statsX, y, statsW,
                    TabletGui.TEXT);
            if (profile.delivered() <= 0) {
                y = drawProfileStatBlock(graphics,
                        Component.translatable("ui.aerocouriers.profile.avg_reward",
                                Component.translatable("ui.aerocouriers.profile.dash")),
                        statsX, y, statsW, TabletGui.TEXT);
            } else {
                long avgReward = profile.rewardSum() / profile.delivered();
                String avgLabel = Component.translatable("ui.aerocouriers.profile.avg_reward.label").getString();
                SpurAmountUi.Bounds avgBounds = SpurAmountUi.drawLabeled(
                        graphics, font, avgLabel, avgReward, statsX, y, statsW, TabletGui.TEXT, TabletGui.TEXT);
                offerSpurTooltip(avgReward, avgBounds, mouseX, mouseY);
                y += Math.max(PROFILE_LINE_HEIGHT, avgBounds.height()) + 2;
            }
            y = drawProfileStatBlock(graphics,
                    Component.translatable("ui.aerocouriers.profile.avg_distance",
                            formatAverage(profile.distance(), profile.delivered())),
                    statsX, y, statsW, TabletGui.TEXT);
            int completed = profile.delivered() + profile.failed();
            String rate = completed == 0
                    ? String.format(Locale.ROOT, "%.0f%%", 100.0)
                    : String.format(Locale.ROOT, "%.0f%%", profile.delivered() * 100.0 / completed);
            drawProfileStatBlock(graphics,
                    Component.translatable("ui.aerocouriers.profile.success_rate", rate), statsX, y, statsW,
                    TabletGui.TEXT);
        }
    }

    private int drawProfileIssuerBlock(GuiGraphics graphics, BoardSnapshotPayload.BoardProfile profile, int statsX,
                                       int y, int statsW) {
        y = drawProfileStatBlock(graphics, Component.translatable("ui.aerocouriers.profile.issuer.heading"),
                statsX, y, statsW, TabletGui.TEXT_MUTED);
        if (profile.issuerOrders() <= 0) {
            return drawProfileStatBlock(graphics, Component.translatable("ui.aerocouriers.profile.issuer.empty"),
                    statsX, y, statsW, TabletGui.TEXT_MUTED);
        }
        dev.poligorn.aerocouriers.license.IssuerStats issuer = profile.issuerStats();
        Component line1 = Component.translatable("ui.aerocouriers.profile.issuer.line1",
                issuer.orders(), dev.poligorn.aerocouriers.economy.MoneyFormat.component(issuer.averageReward()));
        y = drawProfileStatBlock(graphics, line1, statsX, y, statsW, TabletGui.TEXT);
        CargoSize common = profile.issuerMostCommonSize().orElse(CargoSize.M);
        Component sizeLabel = AcceptEligibility.sizeLabel(common);
        Component line2 = Component.translatable("ui.aerocouriers.profile.issuer.line2",
                issuer.averageWeight(), sizeLabel);
        return drawProfileStatBlock(graphics, line2, statsX, y, statsW, TabletGui.TEXT);
    }

    private int profileSkinPanelWidth() {
        return Math.max(96, screenW() * 42 / 100);
    }

    private int profileStatsX(int sx) {
        return sx + profileSkinPanelWidth() + 12;
    }

    private int profileStatsWidth(int sx) {
        return Math.max(48, sx + screenW() - profileStatsX(sx));
    }

    private int profileContentTop() {
        return listTop() + 6 + font.lineHeight + 6;
    }

    private int profileTraineeButtonY(BoardSnapshotPayload.BoardProfile profile) {
        int statsW = profileStatsWidth(screenLeft());
        int y = profileContentTop() + 4 + PROFILE_ICON_SIZE + 4;
        return measureProfileIssuerBlock(profile, statsW, y) + 4;
    }

    private int measureProfileIssuerBlock(BoardSnapshotPayload.BoardProfile profile, int statsW, int y) {
        y = measureProfileStatBlock(Component.translatable("ui.aerocouriers.profile.issuer.heading"), statsW, y);
        if (profile.issuerOrders() <= 0) {
            return measureProfileStatBlock(Component.translatable("ui.aerocouriers.profile.issuer.empty"), statsW, y);
        }
        dev.poligorn.aerocouriers.license.IssuerStats issuer = profile.issuerStats();
        Component line1 = Component.translatable("ui.aerocouriers.profile.issuer.line1",
                issuer.orders(), dev.poligorn.aerocouriers.economy.MoneyFormat.component(issuer.averageReward()));
        y = measureProfileStatBlock(line1, statsW, y);
        CargoSize common = profile.issuerMostCommonSize().orElse(CargoSize.M);
        Component sizeLabel = AcceptEligibility.sizeLabel(common);
        Component line2 = Component.translatable("ui.aerocouriers.profile.issuer.line2",
                issuer.averageWeight(), sizeLabel);
        return measureProfileStatBlock(line2, statsW, y);
    }

    private int measureProfileStatBlock(Component line, int width, int y) {
        int lines = MoneyText.lineCount(font, line, width);
        return y + lines * PROFILE_LINE_HEIGHT + 2;
    }

    private @Nullable LivingEntity resolveProfilePreviewEntity() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        if (!ClientBoard.viewingOtherProfile()) {
            return minecraft.player;
        }
        UUID id = ClientBoard.viewedProfileId();
        if (id == null) {
            return null;
        }
        Player player = minecraft.level.getPlayerByUUID(id);
        return player != null ? player : null;
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

    private void renderProfileHeadFallback(GuiGraphics graphics, int boxX, int top, int boxW, int boxH,
                                           @Nullable UUID playerId) {
        int size = Math.min(boxW - 16, boxH - 16);
        size = Math.min(size, 64);
        int x = boxX + (boxW - size) / 2;
        int y = top + (boxH - size) / 2;
        ResourceLocation skin = profileSkinTexture(playerId);
        graphics.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    private static ResourceLocation profileSkinTexture(@Nullable UUID playerId) {
        if (playerId == null) {
            return DefaultPlayerSkin.getDefaultTexture();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerId);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        return minecraft.getSkinManager().getInsecureSkin(new GameProfile(playerId, "")).texture();
    }

    private int drawProfileStatBlock(GuiGraphics graphics, Component line, int x, int y, int width, int color) {
        MoneyText.drawWrapped(graphics, font, line, x, y, width, color, PROFILE_LINE_HEIGHT);
        return y + MoneyText.lineCount(font, line, width) * PROFILE_LINE_HEIGHT + 2;
    }

    private static Component formatAverage(long sum, int count) {
        if (count <= 0) {
            return Component.translatable("ui.aerocouriers.profile.dash");
        }
        return Component.literal(String.format(Locale.ROOT, "%d", sum / count));
    }

    /** Row widgets are drawn under list chrome in {@link #render}; paint them again on top. */
    private void renderProfileWidgetsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = listTop();
        for (Renderable widget : renderables) {
            if (widget instanceof TabletButton button && button.getY() >= top) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderListActionWidgetsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listY = listTop();
        int listBottom = listPanelBottom();
        for (Renderable widget : renderables) {
            if (widget instanceof TabletButton button && button.getY() >= listY && button.getY() < listBottom) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }
    private void renderList(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> all, int sx,
                            int mouseX, int mouseY) {
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int listY = listTop();
        int cx1 = sx + screenW() - 1;
        int ly1 = listPanelBottom();
        TabletDraw.box(graphics, sx, listY, cx1, ly1, TabletPalette.LIST_BG, TabletPalette.BRASS);
        if (all.isEmpty()) {
            Component message = rawListEntries().isEmpty()
                    ? listEmptyMessage()
                    : Component.translatable("ui.aerocouriers.filters.empty");
            graphics.drawString(font, message, sx + 8, listY + 8, TabletPalette.SEC, false);
            return;
        }
        String listKey = (listRowButtonKey != null ? listRowButtonKey : "") + "|" + tab + "|" + scroll;
        listRowEffects.onListPopulated(listKey, visible);
        int x0 = cardLeft();
        int x1 = cardRight();
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int y = cardY(i);
            boolean hovered = !overlayOpen() && mouseX >= x0 && mouseX <= x1 && mouseY >= y
                    && mouseY < y + BoardCardRenderer.CARD_H;
            boolean boardTab = tab == Tab.BOARD;
            BoardCardRenderer.Style style = new BoardCardRenderer.Style(
                    boardTab,
                    entry.id().equals(selected),
                    hovered,
                    cardLocked(entry),
                    collateralDue(entry),
                    boardTab && entry.collateral() > 0 && collateralDue(entry) <= 0);
            float slideX = listRowEffects.rowSlideX(i);
            float rowAlpha = listRowEffects.rowAlpha(i);
            graphics.pose().pushPose();
            graphics.pose().translate(slideX, 0.0F, 0.0F);
            TabletAnim.withAlpha(graphics, rowAlpha,
                    () -> BoardCardRenderer.render(graphics, font, entry, x0, x1, y, style));
            graphics.pose().popPose();
        }

        if (all.size() > visible) {
            int trackTop = listY + 2;
            int trackBottom = ly1 - 2;
            graphics.fill(cx1 - 7, trackTop, cx1 - 1, trackBottom + 1, TabletPalette.SCROLL_TRACK);
            int trackH = trackBottom - trackTop - 1;
            int thumbH = Math.max(9, Math.round(trackH * (visible / (float) all.size())));
            float progress = scroll / (float) Math.max(1, all.size() - visible);
            int thumbY = trackTop + 1 + Math.round((trackH - thumbH) * progress);
            graphics.fill(cx1 - 6, thumbY, cx1 - 2, thumbY + thumbH, TabletPalette.GOLD);
        }
        int total = rawListEntries().size();
        Component count = all.size() == total
                ? TabletDraw.plural("ui.aerocouriers.board.count", total, total)
                : Component.translatable("ui.aerocouriers.filters.shown", all.size(), total);
        graphics.drawString(font, count, sx + 1, ly1 + 4, TabletPalette.SEC, false);
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
        MoneyText.drawWrapped(graphics, font, text, x, y, width, color, 9);
    }

    private void captureButtonHint(int mouseX, int mouseY) {
        if (hoverTip != null || hoverTipLines != null || hoverCargoComponent != null) {
            return;
        }
        getChildAt(mouseX, mouseY).ifPresent(widget -> {
            if (widget instanceof TabletButton button && button.hint() != null) {
                hoverTip = button.hint();
            } else if (widget instanceof TabletFilterChip chip && chip.hint() != null) {
                hoverTip = chip.hint();
            } else if (widget instanceof TabletFilterDropdown dropdown && dropdown.hint() != null) {
                hoverTip = dropdown.hint();
            }
        });
    }

    private void captureProfileRankHover(int mouseX, int mouseY) {
        if (tab != Tab.PROFILE || snapshot == null || profileRankBarLayout == null) {
            return;
        }
        BoardSnapshotPayload.BoardProfile profile =
                ClientBoard.activeProfile(snapshot.profile());
        if (profile.rank().isAdmin() || profile.rank().level() >= 5) {
            return;
        }
        if (!profileRankBarLayout.contains(mouseX, mouseY)) {
            return;
        }
        List<Component> lines = ProfileRankTooltip.lines(profile.rank());
        if (!lines.isEmpty()) {
            hoverTipLines = lines;
        }
    }

    private void renderVillageRefreshHeader(GuiGraphics graphics, int sx) {
        if (!showsVillageRefreshHeader()) {
            return;
        }
        int remaining = VillageRefreshUi.remainingTicks(minecraft);
        if (remaining < 0) {
            return;
        }
        String label = VillageRefreshUi.countdownLabel(remaining).getString();
        int right = sx + screenW();
        int x = right - font.width(label) - 11;
        int y = listHeadingY();
        dev.poligorn.aerocouriers.client.board.PixelGlyphs.draw(graphics, x, y,
                dev.poligorn.aerocouriers.client.board.PixelGlyphs.CLOCK, TabletPalette.BLUE);
        graphics.drawString(font, label, x + 11, y, TabletPalette.BLUE, false);
    }
    private boolean showsVillageRefreshHeader() {
        if (snapshot == null || tab != Tab.BOARD || tabEffects.isTabLoadingActive()) {
            return false;
        }
        if (!VillageRefreshUi.isEnabled(snapshot.nextVillageRefreshGameTime())) {
            return false;
        }
        return VillageRefreshUi.relevantForBoardFilters(ClientBoard.boardFilters());
    }

    private void renderAdminFilterChrome(GuiGraphics graphics, int sx, int y) {
        if (adminFilterBarHeight <= 0) {
            return;
        }
        int summaryY = y + adminFilterBarHeight + 2;
        if (showsAdminFilterSummary()) {
            int total = snapshot == null ? 0 : snapshot.adminActive().size();
            int shown = adminUi.filteredOrders(snapshot).size();
            graphics.drawString(font,
                    Component.translatable("ui.aerocouriers.filters.shown", shown, total),
                    sx, summaryY, TabletGui.TEXT_MUTED, false);
        }
    }

    private boolean showsAdminFilterSummary() {
        if (snapshot == null || tab != Tab.ADMIN || adminUi.subTab() != BoardAdminUi.SubTab.ORDERS) {
            return false;
        }
        int total = snapshot.adminActive().size();
        if (total == 0) {
            return false;
        }
        int shown = adminUi.filteredOrders(snapshot).size();
        return shown != total || !ClientBoard.adminOrderFilters().isDefault();
    }

    private void renderTopLayerTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoverItem != null && !hoverItem.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 500.0F);
            graphics.renderTooltip(font, hoverItem, mouseX, mouseY);
            graphics.pose().popPose();
            return;
        }
        if (hoverSpurBreakdown != null && hoverSpurHeader != null) {
            TooltipDraw.render(graphics, font, List.of(), Optional.of(hoverSpurBreakdown), mouseX, mouseY);
            return;
        }
        if (hoverCargoComponent != null) {
            List<Component> lines = hoverTip == null ? List.of() : List.of(hoverTip);
            TooltipDraw.render(graphics, font, lines, Optional.of(hoverCargoComponent), mouseX, mouseY);
            return;
        }
        if (hoverTipLines != null && !hoverTipLines.isEmpty()) {
            TooltipDraw.render(graphics, font, hoverTipLines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoverTip != null) {
            TooltipDraw.render(graphics, font, List.of(hoverTip), Optional.empty(), mouseX, mouseY);
        }
    }

    private void offerSpurTooltip(long spurs, SpurAmountUi.Bounds bounds, int mouseX, int mouseY) {
        if (hoverSpurBreakdown != null || bounds == null || snapshot == null || !snapshot.numismaticsEconomy()) {
            return;
        }
        if (bounds.contains(mouseX, mouseY)) {
            hoverSpurBreakdown = new SpurBreakdownTooltip(spurs);
            hoverSpurHeader = Component.translatable("ui.aerocouriers.spur.tooltip.header",
                    dev.poligorn.aerocouriers.economy.MoneyFormat.component(spurs));
        }
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

    /** A rank-locked card explains why it is grey. */
    private void captureCardHover(int mouseX, int mouseY) {
        if (snapshot == null || !listTab() || hoverCargoComponent != null) {
            return;
        }
        if (isOverRowAction(mouseX, mouseY)) {
            return;
        }
        BoardSnapshotPayload.BoardEntry entry = hitCard(mouseX, mouseY);
        if (entry != null && cardLocked(entry)) {
            hoverTip = Component.translatable("ui.aerocouriers.card.locked_tooltip",
                    BoardCardRenderer.roman(entry.requiredRank()));
        }
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabletChrome.blocksInput(snapshot == null, 0.0F)) {
            return true;
        }
        refreshFit();
        mouseX = fit.localX(mouseX);
        mouseY = fit.localY(mouseY);
        double adjustedY = tabletChrome.adjustMouseY(mouseY, 0.0F);
        if (confirmOverlay != null) {
            return confirmOverlay.mouseClicked(mouseX, adjustedY, button);
        }
        if (detailsOverlay != null) {
            return detailsOverlay.mouseClicked(mouseX, adjustedY, button);
        }
        if (blocksTabPanelInput()) {
            if (!tabEffects.isTabLoadingActive() && isOverMainTab(mouseX, adjustedY)) {
                return super.mouseClicked(mouseX, adjustedY, button);
            }
            return true;
        }
        if (button == 0 && tab == Tab.PROFILE && ClientBoard.viewingOtherProfile()
                && isOverProfileCloseButton((int) mouseX, (int) adjustedY)) {
            closeViewedProfile();
            return true;
        }
        if (button == 0 && tab == Tab.MAIL) {
            int mx = (int) mouseX;
            int my = (int) adjustedY;
            if (isOverRowAction(mx, my) && isMailRowButtonUnder(mx, my)
                    && (my < mailListTop() || my >= mailListBottom())) {
                return true;
            }
            if (!isOverRowAction(mx, my)) {
                int index = mailRowAt(mouseX, adjustedY);
                if (index >= 0) {
                    List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
                    if (index < entries.size()) {
                        entries.get(index).contractId().ifPresent(ClientBoard::focusContract);
                        return true;
                    }
                }
            }
        }
        if (button == 0 && snapshot != null && (tab == Tab.BOARD || tab == Tab.MINE)) {
            int mx = (int) mouseX;
            int my = (int) adjustedY;
            if (!isOverRowAction(mx, my)) {
                BoardSnapshotPayload.BoardEntry row = hitCard(mx, my);
                if (row != null) {
                    playClick();
                    openDetails(row.id());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, adjustedY, button);
    }

    private void onCustomerClick(BoardSnapshotPayload.BoardEntry entry) {
        if (entry.villageCustomer()) {
            return;
        }
        entry.customerId().ifPresent(ClientBoard::requestPlayerProfile);
    }

    private @Nullable BoardSnapshotPayload.BoardEntry hitCard(int mouseX, int mouseY) {
        List<BoardSnapshotPayload.BoardEntry> all = listEntries();
        int visible = visibleRows();
        if (mouseX < cardLeft() || mouseX > cardRight()) {
            return null;
        }
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            int y = cardY(i);
            if (mouseY >= y && mouseY < y + BoardCardRenderer.CARD_H) {
                return all.get(scroll + i);
            }
        }
        return null;
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tabletChrome.blocksInput(snapshot == null, 0.0F)) {
            return true;
        }
        if (blocksTabPanelInput()) {
            return true;
        }
        if (overlayOpen()) {
            return true;
        }
        refreshFit();
        mouseX = fit.localX(mouseX);
        mouseY = fit.localY(mouseY);
        double adjustedY = tabletChrome.adjustMouseY(mouseY, 0.0F);
        if (tab == Tab.MAIL) {
            List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
            int before = mailScroll;
            mailScroll = Mth.clamp(mailScroll - (int) Math.signum(scrollY), 0, mailMaxScrollIndex(entries));
            if (mailScroll != before) {
                mailRowButtonKey = null;
                initMailTab();
                return true;
            }
        }
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
        return super.mouseScrolled(mouseX, adjustedY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        refreshFit();
        return super.mouseReleased(fit.localX(mouseX), fit.localY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        refreshFit();
        double s = fit.scale;
        return super.mouseDragged(fit.localX(mouseX), fit.localY(mouseY), button, dragX / s, dragY / s);
    }

    private static final int PROFILE_CLOSE_W = 18;
    private static final int PROFILE_CLOSE_H = 14;

    private TabletButton profileCloseButton(int sx, int panelW) {
        int x = profileCloseButtonX(sx, panelW);
        int y = profileCloseButtonY();
        return TabletButton.create(x, y, PROFILE_CLOSE_W, PROFILE_CLOSE_H, Component.literal("\u2715"),
                b -> closeViewedProfile());
    }

    private int profileCloseButtonX(int sx, int panelW) {
        return sx + panelW - PROFILE_CLOSE_W - 4;
    }

    private int profileCloseButtonY() {
        return listTop() + 6;
    }

    private boolean isOverProfileCloseButton(int mouseX, int mouseY) {
        if (!ClientBoard.viewingOtherProfile()) {
            return false;
        }
        int sx = screenLeft();
        int x = profileCloseButtonX(sx, screenW());
        int y = profileCloseButtonY();
        return mouseX >= x && mouseX < x + PROFILE_CLOSE_W && mouseY >= y && mouseY < y + PROFILE_CLOSE_H;
    }

    private void closeViewedProfile() {
        if (!ClientBoard.viewingOtherProfile()) {
            return;
        }
        ClientBoard.clearViewedProfile();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        refreshBoardContent();
    }

    /** At GUI scale 3 (640×360) the frame is 620 wide. Smaller screens reuse that layout and pose-scale it. */
    private int panelW() {
        int w = guiW();
        return Math.max(428, Math.min(w - 20, Math.max(620, (int) (w * 0.74))));
    }

    private int panelH() {
        int h = guiH();
        int maxH = Math.max(200, h - TabletGui.antennaOverhangTop() - 8);
        return Math.min(maxH, Math.max(309, (int) (h * 0.80)));
    }

    private int guiW() {
        return fit.shrinking() ? fit.layoutW : width;
    }

    private int guiH() {
        return fit.shrinking() ? fit.layoutH : height;
    }

    private void refreshFit() {
        fit = ScreenFit.of(width, height);
    }

    private int left() {
        return TabletGui.frameLeft(guiW(), panelW());
    }

    private int top() {
        return TabletGui.frameTop(guiH(), panelH());
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

    private Component tabHeading() {
        return switch (tab) {
            case BOARD -> Component.translatable("ui.aerocouriers.board.heading.board");
            case MINE -> Component.translatable("ui.aerocouriers.board.heading.mine");
            case PROFILE -> Component.translatable("ui.aerocouriers.board.heading.profile");
            case MAIL -> Component.translatable("ui.aerocouriers.board.heading.mail");
            case ADMIN -> Component.translatable("ui.aerocouriers.board.heading.admin");
        };
    }

    private int headingY() {
        return mainTabRowY() + TAB_H + HEADING_GAP;
    }

    private static boolean showsTraineeHint(BoardSnapshotPayload.BoardProfile profile) {
        return TraineeRules.applies(profile.rank(), profile.villageDelivered(), profile.traineeFreeContracts());
    }

    private boolean listTab() {
        return tab == Tab.BOARD || tab == Tab.MINE;
    }

    /** Rank on the left, active hauls and collateral at stake on the right (board and «Мои»). */
    private int statusLineY() {
        return mainTabRowY() + TAB_H + 4;
    }

    private int filterBarTop() {
        if (listTab()) {
            return statusLineY() + STATUS_LINE_H + traineeHintBlockHeight();
        }
        return headingY() + font.lineHeight + 6;
    }

    private int listHeadingY() {
        return filterBarTop() + filterBarHeight + 4;
    }

    private int listTop() {
        if (!listTab()) {
            return filterBarTop();
        }
        int y = listHeadingY() + LIST_HEADING_H;
        if (tab == Tab.MINE) {
            y += MINE_SUB_TOGGLE_H + MINE_SUB_GAP;
        }
        return y;
    }

    private int cardY(int index) {
        return listTop() + 3 + index * BoardCardRenderer.PITCH;
    }

    private int cardLeft() {
        return screenLeft() + BoardCardRenderer.LEFT_INSET;
    }

    private int cardRight() {
        return screenLeft() + screenW() - 1 - BoardCardRenderer.RIGHT_INSET;
    }

    private int listPanelBottom() {
        return listTop() + 3 + BoardCardRenderer.PITCH * visibleRows();
    }

    private int adminOrdersHeaderY(int subY) {
        int y = adminUi.contentTop(subY);
        y += adminFilterBarHeight + 4;
        if (showsAdminFilterSummary()) {
            y += FILTER_SUMMARY_H;
        }
        return y;
    }

    private int adminOrdersRowY(int subY) {
        return adminOrdersHeaderY(subY) + font.lineHeight + 4;
    }

    private int traineeHintBlockHeight() {
        if (snapshot == null || tab != Tab.BOARD || !showsTraineeHint(snapshot.profile())) {
            return 0;
        }
        Component hint = Component.translatable("ui.aerocouriers.trainee.hint",
                snapshot.profile().traineeFreeContracts() - snapshot.profile().villageDelivered(),
                snapshot.profile().traineeMaxDistance());
        int lines = MoneyText.lineCount(font, hint, screenW());
        return lines * font.lineHeight + 4;
    }

    private int mineSubToggleY() {
        return listHeadingY() + LIST_HEADING_H;
    }

    private List<BoardSnapshotPayload.BoardEntry> listEntries() {
        if (snapshot == null) {
            return List.of();
        }
        return listEntriesForTab(snapshot);
    }

    private List<BoardSnapshotPayload.BoardEntry> rawListEntries() {
        if (snapshot == null) {
            return List.of();
        }
        return rawListEntriesForTab(snapshot);
    }

    private List<BoardSnapshotPayload.BoardEntry> rawListEntriesForTab(BoardSnapshotPayload payload) {
        if (tab == Tab.BOARD) {
            return payload.board();
        }
        if (tab == Tab.MINE) {
            return MineListSection.entries(payload.mine(), ClientBoard.mineSection());
        }
        return List.of();
    }

    private List<BoardSnapshotPayload.BoardEntry> listEntriesForTab(BoardSnapshotPayload payload) {
        List<BoardSnapshotPayload.BoardEntry> raw = rawListEntriesForTab(payload);
        if (tab == Tab.BOARD) {
            int activeHauls = BoardAcceptUi.countActiveHauls(payload.mine());
            return ClientBoard.boardFilters().apply(raw, payload.profile(), activeHauls);
        }
        if (tab == Tab.MINE) {
            return ClientBoard.mineFilters(ClientBoard.mineSection()).apply(raw, ClientBoard.mineSection());
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

    private int visibleRows() {
        return Math.max(1, (contentBottom() - COUNT_LABEL_H - listTop() - 3) / BoardCardRenderer.PITCH);
    }

    private void initMailTab() {
        removeTabletButtons();
        if (snapshot == null) {
            return;
        }
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        int sx = screenLeft();
        int sw = screenW();
        int clearW = Math.min(96, sw / 3);
        addRenderableWidget(TabletButton.create(sx + sw - clearW, headingY(), clearW, 14,
                Component.translatable("ui.aerocouriers.inbox.clear_all"),
                b -> PacketDistributor.sendToServer(InboxClearPlainPayload.INSTANCE)));

        int visible = mailVisibleRows();
        mailScroll = Mth.clamp(mailScroll, 0, mailMaxScrollIndex(entries));
        int rowTop = mailListTop();
        for (int i = 0; i < visible && mailScroll + i < entries.size(); i++) {
            NotificationSyncPayload.Line line = entries.get(mailScroll + i);
            int rowY = mailRowTop(entries, mailScroll + i);
            int rowH = inboxRowHeight(line, sw);
            int btnY = rowY + (rowH - 14) / 2;
            int claimW = 54;
            int dismissW = 14;
            int dismissX = sx + sw - dismissW - 4;
            if (line.hasItems()) {
                addRenderableWidget(TabletButton.create(dismissX - claimW - 4, btnY, claimW, 14,
                        Component.translatable("ui.aerocouriers.inbox.claim"),
                        b -> PacketDistributor.sendToServer(new InboxClaimPayload(line.id()))));
            } else {
                addRenderableWidget(TabletButton.create(dismissX, btnY, dismissW, 14,
                        Component.literal("\u2715"),
                        b -> PacketDistributor.sendToServer(new InboxDismissPayload(line.id()))));
            }
        }
        mailRowButtonKey = mailScroll + "|" + entries.size();
    }

    private void renderInboxList(GuiGraphics graphics, int sx, int mouseX, int mouseY) {
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        int listW = screenW();
        int top = mailListTop();
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.inbox.empty"),
                    sx, top, TabletGui.TEXT_MUTED, false);
            return;
        }
        int visible = mailVisibleRows();
        String listKey = mailScroll + "|" + entries.size();
        mailRowEffects.onListPopulated(listKey, visible);
        for (int i = 0; i < visible && mailScroll + i < entries.size(); i++) {
            NotificationSyncPayload.Line line = entries.get(mailScroll + i);
            int listIndex = mailScroll + i;
            int rowY = mailRowTop(entries, listIndex);
            int rowH = inboxRowHeight(line, listW);
            boolean hover = mouseX >= sx && mouseX < sx + listW
                    && mouseY >= rowY && mouseY < rowY + rowH - 2;
            float hoverStrength = mailRowEffects.hoverStrength(i, hover);
            float slideX = mailRowEffects.rowSlideX(i);
            float rowAlpha = mailRowEffects.rowAlpha(i);
            TabletGui.drawRow(graphics, sx, rowY, listW, rowH - 2, false, hoverStrength, rowAlpha, slideX);
            graphics.pose().pushPose();
            graphics.pose().translate(slideX, 0.0F, 0.0F);
            TabletAnim.withAlpha(graphics, rowAlpha, () -> {
                int iconY = rowY + (rowH - INBOX_ICON) / 2 - 1;
                var icon = line.hasItems() ? AerocouriersGuiSprites.ICON_CRATE : AerocouriersGuiSprites.BELL_TABLET;
                graphics.blitSprite(icon, sx + 4, iconY, INBOX_ICON, INBOX_ICON);
                int textX = sx + 4 + INBOX_ICON + 4;
                int claimReserve = line.hasItems() ? 62 : 18;
                int textW = listW - (textX - sx) - claimReserve - 4;
                int wrapped = MoneyText.lineCount(font, line.text(), textW);
                int textBlockH = wrapped * font.lineHeight;
                int ty = rowY + (rowH - textBlockH) / 2;
                MoneyText.drawWrapped(graphics, font, line.text(), textX, ty, textW, TabletGui.TEXT, font.lineHeight);
            });
            graphics.pose().popPose();
        }
    }

    private void renderMailActionWidgetsOnTop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listY = mailListTop();
        int listBottom = mailListBottom();
        int sx = screenLeft();
        int x0 = layoutToScreenX(sx);
        int y0 = layoutToScreenY(listY);
        int x1 = layoutToScreenX(sx + screenW());
        int y1 = layoutToScreenY(listBottom);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        graphics.enableScissor(x0, y0, x1, y1);
        for (Renderable widget : renderables) {
            if (widget instanceof TabletButton button && button.getY() >= listY && button.getY() < listBottom) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();
    }

    private int layoutToScreenX(int layoutX) {
        return (int) Math.floor(fit.fitX + layoutX * fit.scale);
    }

    private int layoutToScreenY(int layoutY) {
        return (int) Math.floor(fit.fitY + layoutY * fit.scale);
    }

    private boolean isMailRowButtonUnder(int mouseX, int mouseY) {
        for (Renderable widget : renderables) {
            if (isMailRowButton(widget) && widget instanceof AbstractWidget button && button.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private int mailRowAt(double mouseX, double mouseY) {
        int left = screenLeft();
        int listW = screenW();
        if (mouseX < left || mouseX >= left + listW) {
            return -1;
        }
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        int visible = mailVisibleRows();
        for (int i = 0; i < visible && mailScroll + i < entries.size(); i++) {
            int listIndex = mailScroll + i;
            int rowY = mailRowTop(entries, listIndex);
            int rowH = inboxRowHeight(entries.get(listIndex), listW);
            if (mouseY >= rowY && mouseY < rowY + rowH - 2) {
                return listIndex;
            }
        }
        return -1;
    }

    private int mailListTop() {
        return headingY() + font.lineHeight + 6;
    }

    private int mailListHeight() {
        return contentBottom() - mailListTop();
    }

    private int mailListBottom() {
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        int visible = mailVisibleRows();
        if (visible == 0 || entries.isEmpty()) {
            return mailListTop();
        }
        int last = Math.min(entries.size() - 1, mailScroll + visible - 1);
        return mailRowTop(entries, last) + inboxRowHeight(entries.get(last), screenW());
    }

    private int inboxActionReserve(boolean hasItems) {
        return hasItems ? 62 : 18;
    }

    private int inboxTextWidth(int listW, boolean hasItems) {
        int textX = 4 + INBOX_ICON + 4;
        return listW - textX - inboxActionReserve(hasItems) - 4;
    }

    private int inboxRowHeight(NotificationSyncPayload.Line line, int listW) {
        int lines = MoneyText.lineCount(font, line.text(), inboxTextWidth(listW, line.hasItems()));
        int textH = Math.max(font.lineHeight, lines * font.lineHeight);
        return Math.max(INBOX_ROW_MIN_H, INBOX_ROW_PAD_V * 2 + textH);
    }

    private int mailRowTop(List<NotificationSyncPayload.Line> entries, int listIndex) {
        int y = mailListTop();
        for (int i = mailScroll; i < listIndex; i++) {
            y += inboxRowHeight(entries.get(i), screenW());
        }
        return y;
    }

    private int mailVisibleRows() {
        List<NotificationSyncPayload.Line> entries = ClientNotifications.entries();
        int listH = mailListHeight();
        if (listH <= 0 || entries.isEmpty()) {
            return 0;
        }
        int used = 0;
        int count = 0;
        for (int i = mailScroll; i < entries.size(); i++) {
            int rowH = inboxRowHeight(entries.get(i), screenW());
            if (count > 0 && used + rowH > listH) {
                break;
            }
            used += rowH;
            count++;
            if (used >= listH) {
                break;
            }
        }
        return count;
    }

    private int mailMaxScrollIndex(List<NotificationSyncPayload.Line> entries) {
        if (entries.isEmpty()) {
            return 0;
        }
        int last = entries.size() - 1;
        int listH = mailListHeight();
        for (int start = last; start >= 0; start--) {
            int height = 0;
            for (int i = start; i <= last; i++) {
                height += inboxRowHeight(entries.get(i), screenW());
            }
            if (height >= listH || start == last) {
                return start;
            }
        }
        return 0;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private final class OverlayHost implements BoardOverlayHost {
        @Override
        public net.minecraft.client.gui.Font font() {
            return font;
        }

        @Override
        public Optional<BoardSnapshotPayload.BoardEntry> entry(UUID id) {
            return snapshot == null ? Optional.empty() : snapshot.find(id);
        }

        @Override
        public long balance() {
            return snapshot == null ? 0L : snapshot.balance();
        }

        @Override
        public LicenseRank viewerRank() {
            return snapshot == null ? LicenseRank.RANK1 : snapshot.profile().rank();
        }

        @Override
        public long collateralDue(BoardSnapshotPayload.BoardEntry entry) {
            return CourierBoardScreen.this.collateralDue(entry);
        }

        @Override
        public boolean traineeFree(BoardSnapshotPayload.BoardEntry entry) {
            return entry.collateral() > 0 && CourierBoardScreen.this.collateralDue(entry) <= 0;
        }

        @Override
        public boolean offersAccept(BoardSnapshotPayload.BoardEntry entry) {
            return snapshot != null && entry.state() == ContractState.OPEN
                    && snapshot.board().stream().anyMatch(e -> e.id().equals(entry.id()))
                    && (!"issuer".equals(entry.role()) || snapshot.allowSelfAccept());
        }

        @Override
        public Optional<Component> acceptDenial(BoardSnapshotPayload.BoardEntry entry) {
            if (snapshot == null || !snapshot.licensed()) {
                return Optional.of(Component.translatable("contract.aerocouriers.error.no_license"));
            }
            int activeHauls = BoardAcceptUi.countActiveHauls(snapshot.mine());
            return BoardAcceptUi.denial(entry, snapshot.profile(), activeHauls).map(AcceptEligibility.Denial::message);
        }

        @Override
        public boolean mapAvailable(BoardSnapshotPayload.BoardEntry entry) {
            return activeHaul(entry) || XaeroOrderBridge.xaeroMinimapPresent()
                    || XaeroOrderBridge.xaeroWorldMapPresent();
        }

        @Override
        public void showOnMap(BoardSnapshotPayload.BoardEntry entry) {
            if (activeHaul(entry)) {
                return;
            }
            XaeroOrderBridge.onPreviewDestination(
                    entry.id(), entry.destinationDimension(), entry.destinationPos(), entry.shortId());
        }

        @Override
        public void openCustomerProfile(BoardSnapshotPayload.BoardEntry entry) {
            onCustomerClick(entry);
        }

        @Override
        public boolean customerOnline(BoardSnapshotPayload.BoardEntry entry) {
            return CourierBoardScreen.this.customerOnline(entry);
        }

        @Override
        public void openAcceptConfirm(BoardSnapshotPayload.BoardEntry entry) {
            confirmOverlay = new BoardAcceptOverlay(this, entry.id());
        }

        @Override
        public void closeDetails() {
            detailsOverlay = null;
            confirmOverlay = null;
        }

        @Override
        public void closeConfirm() {
            confirmOverlay = null;
        }

        @Override
        public void openActionConfirm(BoardSnapshotPayload.BoardEntry entry, ContractIntentPayload.Kind action) {
            confirmOverlay = new BoardActionConfirmOverlay(this, entry.id(), action);
        }

        @Override
        public void confirmAction(BoardSnapshotPayload.BoardEntry entry, ContractIntentPayload.Kind action) {
            closeOverlays();
            PacketDistributor.sendToServer(new ContractIntentPayload(action, entry.id()));
        }

        @Override
        public void confirmAccept(BoardSnapshotPayload.BoardEntry entry) {
            selected = entry.id();
            closeOverlays();
            PacketDistributor.sendToServer(new ContractIntentPayload(ContractIntentPayload.Kind.ACCEPT, entry.id()));
        }
    }
}
