package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.AdminOrdersFilterState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Admin panel sub-tabs and widgets for {@link CourierBoardScreen}. */
final class BoardAdminUi {
    enum SubTab {
        ORDERS, PLAYERS, ECONOMY, SERVER
    }

    private final CourierBoardScreen screen;
    private SubTab subTab = SubTab.ORDERS;
    private final List<TabletTabButton> subToggles = new ArrayList<>();
    private @Nullable UUID selectedPlayer;
    private int playerStationIndex;
    private int configKeyIndex;
    private int adminScroll;
    private @Nullable UUID pendingLicenseResetId;
    private @Nullable String pendingLicenseResetName;
    private final AdminEconomyUi economyUi;

    BoardAdminUi(CourierBoardScreen screen) {
        this.screen = screen;
        this.economyUi = new AdminEconomyUi(screen);
    }

    SubTab subTab() {
        return subTab;
    }

    List<BoardSnapshotPayload.AdminActiveEntry> filteredOrders(@Nullable BoardSnapshotPayload snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        return ClientBoard.adminOrderFilters().apply(snapshot.adminActive());
    }

    void clearSubToggles() {
        for (AbstractWidget widget : subToggles) {
            screen.adminRemoveWidget(widget);
        }
        subToggles.clear();
    }

    void initSubToggles(int sx, int y, int sw) {
        clearSubToggles();
        int gap = 4;
        int eachW = (sw - gap * 3) / 4;
        int x = sx;
        for (SubTab tab : SubTab.values()) {
            Component label = Component.translatable("ui.aerocouriers.admin.sub." + tab.name().toLowerCase(Locale.ROOT));
            TabletTabButton button = new TabletTabButton(x, y, eachW, 18, label, () -> selectSub(tab));
            button.selected(subTab == tab);
            subToggles.add(button);
            screen.adminAddWidget(button);
            x += eachW + gap;
        }
    }

    private void selectSub(SubTab tab) {
        subTab = tab;
        adminScroll = 0;
        pendingLicenseResetId = null;
        pendingLicenseResetName = null;
        economyUi.reset();
        screen.refreshAdminContent();
    }

    void initContent(BoardSnapshotPayload snapshot, int sx, int rowY, int sw, @Nullable UUID selectedContract) {
        if (subTab == SubTab.ORDERS) {
            initOrders(snapshot, sx, rowY, sw, selectedContract);
        } else if (subTab == SubTab.PLAYERS) {
            initPlayers(snapshot, sx, rowY, sw);
        } else if (subTab == SubTab.ECONOMY) {
            economyUi.init(snapshot, sx, rowY, sw);
        } else {
            initServer(snapshot, sx, rowY, sw);
        }
    }

    private void initOrders(BoardSnapshotPayload snapshot, int sx, int rowY, int sw, @Nullable UUID selectedContract) {
        int rowH = 14;
        List<BoardSnapshotPayload.AdminActiveEntry> all = filteredOrders(snapshot);
        int visible = Math.max(1, (screen.adminContentBottom() - rowY - 80) / rowH);
        adminScroll = Mth.clamp(adminScroll, 0, Math.max(0, all.size() - visible));
        for (int i = 0; i < visible && adminScroll + i < all.size(); i++) {
            BoardSnapshotPayload.AdminActiveEntry entry = all.get(adminScroll + i);
            int lineY = rowY + i * rowH;
            screen.adminAddWidget(TabletButton.create(sx + sw - 22, lineY - 2, 18, 14,
                    Component.literal("▸"), b -> screen.selectAdminContract(entry.id())));
        }
        if (selectedContract != null) {
            int actionY = rowY + visible * rowH + 8;
            Optional<UUID> id = Optional.of(selectedContract);
            screen.adminAddWidget(btn(sx, actionY, sw / 2 - 2, "ui.aerocouriers.admin.action.spawn",
                    OperatorBoardPayload.Action.FORCE_SPAWN, id, false));
            screen.adminAddWidget(btn(sx + sw / 2 + 2, actionY, sw / 2 - 2, "ui.aerocouriers.admin.action.deliver",
                    OperatorBoardPayload.Action.FORCE_DELIVER, id, false));
            screen.adminAddWidget(btn(sx, actionY + 22, sw / 2 - 2, "ui.aerocouriers.admin.action.fail",
                    OperatorBoardPayload.Action.FORCE_FAIL, id, true));
            screen.adminAddWidget(btn(sx + sw / 2 + 2, actionY + 22, sw / 2 - 2, "ui.aerocouriers.admin.action.cancel",
                    OperatorBoardPayload.Action.ADMIN_CANCEL, id, true));
            screen.adminAddWidget(btn(sx, actionY + 44, sw / 2 - 2, "ui.aerocouriers.admin.action.extend",
                    OperatorBoardPayload.Action.EXTEND_DEADLINE, id, false));
            screen.adminAddWidget(btn(sx + sw / 2 + 2, actionY + 44, sw / 2 - 2, "ui.aerocouriers.admin.action.tp",
                    OperatorBoardPayload.Action.TELEPORT_TO_CONTRACT, id, false));
        }
    }

    private void initPlayers(BoardSnapshotPayload snapshot, int sx, int y, int sw) {
        int rowH = 14;
        List<BoardSnapshotPayload.PlayerHint> players = snapshot.onlinePlayers();
        if (selectedPlayer == null && !players.isEmpty()) {
            selectedPlayer = players.getFirst().id();
        }
        int resetW = Math.min(108, sw / 3);
        int nameW = Math.max(48, sw - 4 - resetW - 4);
        for (int i = 0; i < players.size() && i < 8; i++) {
            BoardSnapshotPayload.PlayerHint p = players.get(i);
            int rowY = y + i * rowH;
            boolean sel = p.id().equals(selectedPlayer);
            screen.adminAddWidget(TabletButton.create(sx, rowY, nameW, 14,
                    Component.literal((sel ? "• " : "  ") + p.name()), b -> {
                        selectedPlayer = p.id();
                        pendingLicenseResetId = null;
                        pendingLicenseResetName = null;
                        screen.refreshAdminContent();
                    }));
            screen.adminAddWidget(TabletButton.create(sx + nameW + 4, rowY, resetW, 14,
                    Component.translatable("ui.aerocouriers.admin.player.reset_license"), b -> {
                        pendingLicenseResetId = p.id();
                        pendingLicenseResetName = p.name();
                        screen.refreshAdminContent();
                    }));
        }
        int listBottom = y + Math.min(players.size(), 8) * rowH + 4;
        if (pendingLicenseResetId != null && pendingLicenseResetName != null) {
            int confirmY = listBottom;
            int yesW = 44;
            int noW = 44;
            int textW = Math.max(48, sw - yesW - noW - 12);
            screen.adminAddWidget(TabletButton.create(sx + textW + 8, confirmY, yesW, 14,
                    Component.translatable("ui.aerocouriers.admin.player.reset_license.yes"), b -> {
                        UUID targetId = pendingLicenseResetId;
                        pendingLicenseResetId = null;
                        pendingLicenseResetName = null;
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.PLAYER_RESET_LICENSE, Optional.empty(),
                                Optional.ofNullable(targetId), Optional.empty(), Optional.empty(), 0L, true));
                        screen.refreshAdminContent();
                    }));
            screen.adminAddWidget(TabletButton.create(sx + textW + yesW + 12, confirmY, noW, 14,
                    Component.translatable("ui.aerocouriers.admin.player.reset_license.no"), b -> {
                        pendingLicenseResetId = null;
                        pendingLicenseResetName = null;
                        screen.refreshAdminContent();
                    }));
            listBottom = confirmY + 18;
        }
        Optional<UUID> target = Optional.ofNullable(selectedPlayer);
        int by = listBottom + 4;
        int half = sw / 2 - 2;
        int col = 0;
        for (LicenseRank rank : LicenseRank.values()) {
            String key = rank.getSerializedName();
            int bx = sx + col * (half + 4);
            screen.adminAddWidget(playerBtn(bx, by, half,
                    Component.translatable("ui.aerocouriers.admin.player.rank_short", rank.displayName()),
                    OperatorBoardPayload.Action.PLAYER_RANK, target, key, 0));
            col++;
            if (col >= 2) {
                col = 0;
                by += 18;
            }
        }
        by += 22;
        screen.adminAddWidget(playerBtn(sx, by, half, Component.translatable("ui.aerocouriers.admin.player.xp_add"),
                OperatorBoardPayload.Action.PLAYER_XP_ADD, target, "", 100));
        screen.adminAddWidget(playerBtn(sx + half + 4, by, half, Component.translatable("ui.aerocouriers.admin.player.xp_set"),
                OperatorBoardPayload.Action.PLAYER_XP_SET, target, "", 500));
        by += 22;
        screen.adminAddWidget(playerBtn(sx, by, half, Component.translatable("ui.aerocouriers.admin.player.waybill"),
                OperatorBoardPayload.Action.PLAYER_WAYBILL, target, "", 0));
        screen.adminAddWidget(playerBtn(sx + half + 4, by, half,
                Component.translatable("ui.aerocouriers.admin.player.money",
                        dev.poligorn.aerocouriers.economy.MoneyFormat.component(OperatorBoardPayload.TEST_MONEY)),
                OperatorBoardPayload.Action.PLAYER_MONEY, target, "", OperatorBoardPayload.TEST_MONEY));
        by += 22;
        if (!snapshot.stations().isEmpty()) {
            playerStationIndex = Mth.clamp(playerStationIndex, 0, snapshot.stations().size() - 1);
            String station = snapshot.stations().get(playerStationIndex).name();
            screen.adminAddWidget(TabletButton.create(sx, by, sw - 4, 18,
                    Component.translatable("ui.aerocouriers.admin.player.rep", station, 10), b -> {
                        PacketDistributor.sendToServer(new OperatorBoardPayload(OperatorBoardPayload.Action.PLAYER_REP,
                                Optional.empty(), target, Optional.of(station), Optional.empty(), 10L, false));
                    }));
            screen.adminAddWidget(TabletButton.create(sx + sw - 60, by - 18, 28, 14, Component.literal("‹"), b -> {
                playerStationIndex = (playerStationIndex + snapshot.stations().size() - 1) % snapshot.stations().size();
                screen.refreshAdminContent();
            }));
            screen.adminAddWidget(TabletButton.create(sx + sw - 30, by - 18, 28, 14, Component.literal("›"), b -> {
                playerStationIndex = (playerStationIndex + 1) % snapshot.stations().size();
                screen.refreshAdminContent();
            }));
        }
    }

    private void initServer(BoardSnapshotPayload snapshot, int sx, int y, int sw) {
        screen.adminAddWidget(TabletButton.create(sx, y, sw - 4, 20,
                Component.translatable("ui.aerocouriers.admin.server.regen"), b ->
                        send(OperatorBoardPayload.Action.SERVER_REGEN, Optional.empty(), Optional.empty(), "", "", 0, false)));
        List<String> keys = snapshot.adminConfigLines();
        if (!keys.isEmpty()) {
            configKeyIndex = Mth.clamp(configKeyIndex, 0, keys.size() - 1);
            String line = keys.get(configKeyIndex);
            int eq = line.indexOf('=');
            String path = eq >= 0 ? line.substring(0, eq) : line;
            String cur = eq >= 0 ? line.substring(eq + 1) : "";
            screen.adminAddWidget(TabletButton.create(sx, y + 28, sw - 64, 18,
                    Component.literal(path + " = " + cur), b -> {}));
            screen.adminAddWidget(TabletButton.create(sx + sw - 58, y + 28, 26, 18, Component.literal("‹"), b -> {
                configKeyIndex = (configKeyIndex + keys.size() - 1) % keys.size();
                screen.refreshAdminContent();
            }));
            screen.adminAddWidget(TabletButton.create(sx + sw - 30, y + 28, 26, 18, Component.literal("›"), b -> {
                configKeyIndex = (configKeyIndex + 1) % keys.size();
                screen.refreshAdminContent();
            }));
            screen.adminAddWidget(TabletButton.create(sx, y + 50, sw - 4, 18,
                    Component.translatable("ui.aerocouriers.admin.server.config_inc"), b -> {
                        String next = toggleConfigValue(cur);
                        send(OperatorBoardPayload.Action.CONFIG_SET, Optional.empty(), Optional.empty(), path, next,
                                0, false);
                    }));
        }
        screen.adminAddWidget(TabletButton.create(sx, y + 76, sw - 4, 20,
                Component.translatable("ui.aerocouriers.admin.server.debug_self_accept"), b ->
                        send(OperatorBoardPayload.Action.DEBUG_TOGGLE_SELF_ACCEPT, Optional.empty(), Optional.empty(),
                                "", "", 0, false)));
        boolean tutorialOn = snapshot.extras().tutorialEnabled();
        screen.adminAddWidget(TabletButton.create(sx, y + 100, sw - 4, 20,
                Component.translatable("ui.aerocouriers.admin.server.tutorial",
                        Component.translatable(tutorialOn ? "ui.aerocouriers.common.on" : "ui.aerocouriers.common.off")),
                b -> send(OperatorBoardPayload.Action.SERVER_TUTORIAL_TOGGLE, Optional.empty(), Optional.empty(),
                        "", "", tutorialOn ? 0 : 1, false)));
    }

    private static String toggleConfigValue(String cur) {
        if ("true".equalsIgnoreCase(cur)) {
            return "false";
        }
        if ("false".equalsIgnoreCase(cur)) {
            return "true";
        }
        try {
            double v = Double.parseDouble(cur);
            return String.valueOf(v + 1);
        } catch (NumberFormatException e) {
            return cur;
        }
    }

    void render(GuiGraphics graphics, BoardSnapshotPayload snapshot, int sx, int headerY, int rowY, int sw,
                @Nullable UUID selectedContract) {
        if (subTab == SubTab.PLAYERS && pendingLicenseResetId != null && pendingLicenseResetName != null) {
            int rowH = 14;
            List<BoardSnapshotPayload.PlayerHint> players = snapshot.onlinePlayers();
            int confirmY = rowY + Math.min(players.size(), 8) * rowH + 4;
            int yesW = 44;
            int noW = 44;
            int textW = Math.max(48, sw - yesW - noW - 12);
            Component question = Component.translatable("ui.aerocouriers.admin.player.reset_license.confirm",
                    pendingLicenseResetName);
            graphics.drawString(screen.adminFont(), question, sx, confirmY + 3, TabletGui.TEXT_WARN, false);
            return;
        }
        if (subTab == SubTab.ECONOMY) {
            economyUi.render(graphics, snapshot, sx, rowY, sw);
            return;
        }
        if (subTab != SubTab.ORDERS) {
            return;
        }
        int rowH = 14;
        List<BoardSnapshotPayload.AdminActiveEntry> all = filteredOrders(snapshot);
        graphics.drawString(screen.adminFont(), Component.translatable("ui.aerocouriers.admin.orders.header", all.size()),
                sx, headerY, TabletGui.TEXT, false);
        int visible = Math.max(1, (screen.adminContentBottom() - rowY - 80) / rowH);
        for (int i = 0; i < visible && adminScroll + i < all.size(); i++) {
            BoardSnapshotPayload.AdminActiveEntry entry = all.get(adminScroll + i);
            int lineY = rowY + i * rowH;
            boolean sel = selectedContract != null && selectedContract.equals(entry.id());
            int color = sel ? TabletGui.TEXT : TabletGui.TEXT_MUTED;
            String line = "#" + entry.shortId() + " " + entry.originLabel() + "→" + entry.destinationLabel()
                    + "  " + entry.size() + "  " + entry.state().getSerializedName()
                    + "  " + entry.courierName();
            graphics.drawString(screen.adminFont(), clip(line, sw - 28), sx, lineY, color, false);
        }
        if (selectedContract != null) {
            snapshot.findAdmin(selectedContract).ifPresent(entry -> {
                int actionY = rowY + visible * rowH + 4;
                graphics.drawString(screen.adminFont(),
                        Component.translatable("ui.aerocouriers.admin.selected", entry.shortId()),
                        sx, actionY, TabletGui.TEXT, false);
            });
        }
    }

    private String clip(String text, int maxWidth) {
        var font = screen.adminFont();
        if (font.width(text) <= maxWidth) {
            return text;
        }
        while (text.length() > 1 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    private TabletButton btn(int x, int y, int w, String labelKey, OperatorBoardPayload.Action action,
                           Optional<UUID> contractId, boolean destructive) {
        return TabletButton.create(x, y, w, 20, Component.translatable(labelKey), b -> {
            if (destructive && contractId.isPresent() && screen.adminMc() != null) {
                Component body = Component.translatable("ui.aerocouriers.admin.confirm.body",
                        contractId.get().toString().substring(0, 8));
                screen.adminMc().setScreen(new AdminActionConfirmScreen(screen, action, contractId.get(), body));
            } else {
                send(action, contractId, Optional.empty(), "", "", 0, false);
            }
        });
    }

    private TabletButton playerBtn(int x, int y, int w, Component label, OperatorBoardPayload.Action action,
                                   Optional<UUID> target, String arg1, long longArg) {
        return TabletButton.create(x, y, w, 18, label, b ->
                send(action, Optional.empty(), target, arg1, "", longArg, false));
    }

    private void send(OperatorBoardPayload.Action action, Optional<UUID> contractId, Optional<UUID> target,
                      String arg1, String arg2, long longArg, boolean confirmed) {
        PacketDistributor.sendToServer(new OperatorBoardPayload(action, contractId, target,
                arg1.isEmpty() ? Optional.empty() : Optional.of(arg1),
                arg2.isEmpty() ? Optional.empty() : Optional.of(arg2), longArg, confirmed));
    }

    int subToggleY(int headingY, int fontHeight) {
        return headingY + fontHeight + CourierBoardScreen.ADMIN_SUB_GAP;
    }

    int contentTop(int subY) {
        return subY + CourierBoardScreen.ADMIN_SUB_TOGGLE_H + CourierBoardScreen.ADMIN_SUB_GAP;
    }

    boolean scrollEconomy(double delta, BoardSnapshotPayload snapshot, int top) {
        return subTab == SubTab.ECONOMY && economyUi.scroll(delta, snapshot, top);
    }

    void scrollAdmin(int delta) {
        adminScroll = Math.max(0, adminScroll + delta);
    }
}
