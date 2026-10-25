package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ClaimMailPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Testing job board (iteration 4): vanilla widgets, no extra UI libraries. All actions are C2S intents.
 */
public final class CourierBoardScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 248;
    private static final int ROWS = 4;

    enum Tab {
        BOARD, MINE, CREATE, TEST
    }

    private @Nullable BoardSnapshotPayload snapshot;
    private Tab tab = Tab.BOARD;
    private int listPage;
    private @Nullable UUID selected;
    private @Nullable UUID pendingAccept;
    private @Nullable Component notice;

    private String fromX = "";
    private String fromY = "";
    private String fromZ = "";
    private String toX = "";
    private String toY = "";
    private String toZ = "";
    private String reward = Long.toString(CreateContractPayload.DEFAULT_REWARD);
    private String collateral = Long.toString(CreateContractPayload.DEFAULT_COLLATERAL);
    private String minutes = Integer.toString(Pricing.MIN_DEADLINE_MINUTES);
    private String radius = Integer.toString(CreateContractPayload.DEFAULT_RADIUS);

    private @Nullable EditBox fromXBox;
    private @Nullable EditBox fromYBox;
    private @Nullable EditBox fromZBox;
    private @Nullable EditBox toXBox;
    private @Nullable EditBox toYBox;
    private @Nullable EditBox toZBox;
    private @Nullable EditBox rewardBox;
    private @Nullable EditBox collateralBox;
    private @Nullable EditBox minutesBox;
    private @Nullable EditBox radiusBox;

    public CourierBoardScreen(@Nullable BoardSnapshotPayload snapshot) {
        super(Component.translatable("ui.aerocouriers.board.title"));
        applySnapshot(snapshot);
    }

    void apply(BoardSnapshotPayload payload) {
        applySnapshot(payload);
        if (selected != null && payload.find(selected).isEmpty()) {
            selected = null;
            pendingAccept = null;
        }
        rebuildWidgets();
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
        int left = left();
        int top = top();
        addTab(left + 8, top + 6, 70, Tab.BOARD, "ui.aerocouriers.tab.board");
        addTab(left + 80, top + 6, 62, Tab.MINE, "ui.aerocouriers.tab.mine");
        addTab(left + 144, top + 6, 70, Tab.CREATE, "ui.aerocouriers.tab.create");
        if (snapshot != null && snapshot.operator()) {
            addTab(left + 216, top + 6, 50, Tab.TEST, "ui.aerocouriers.tab.test");
        }

        int mail = snapshot == null ? 0 : snapshot.mailCount();
        addRenderableWidget(Button.builder(
                        Component.translatable("ui.aerocouriers.mail.claim", mail),
                        b -> PacketDistributor.sendToServer(ClaimMailPayload.INSTANCE))
                .bounds(left + PANEL_W - 118, top + 28, 110, 20)
                .build());

        if (snapshot == null) {
            return;
        }
        switch (tab) {
            case BOARD -> initList(snapshot.board(), snapshot.boardTotal(), true);
            case MINE -> initList(snapshot.mine(), snapshot.mine().size(), false);
            case CREATE -> initCreate();
            case TEST -> initTest();
        }
    }

    private void addTab(int x, int y, int w, Tab value, String key) {
        Button button = Button.builder(Component.translatable(key), b -> {
                    tab = value;
                    listPage = 0;
                    pendingAccept = null;
                    rebuildWidgets();
                })
                .bounds(x, y, w, 20)
                .build();
        button.active = tab != value;
        addRenderableWidget(button);
    }

    private void initList(List<BoardSnapshotPayload.BoardEntry> all, int totalForServer, boolean boardTab) {
        int left = left();
        int top = top();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) ROWS));
        listPage = Math.min(listPage, pages - 1);
        int start = listPage * ROWS;
        int rowTop = top + 52;
        for (int i = 0; i < ROWS && start + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(start + i);
            int y = rowTop + i * 28;
            boolean selectedRow = entry.id().equals(selected);
            addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.details"), b -> {
                        selected = entry.id();
                        pendingAccept = null;
                        rebuildWidgets();
                    })
                    .bounds(left + PANEL_W - 148, y, 68, 20)
                    .build());
            if (boardTab && entry.state() == ContractState.OPEN && !"issuer".equals(entry.role())) {
                boolean confirm = entry.id().equals(pendingAccept);
                Button accept = Button.builder(
                                Component.translatable(confirm ? "ui.aerocouriers.accept.confirm" : "ui.aerocouriers.accept",
                                        entry.collateral()),
                                b -> onAccept(entry))
                        .bounds(left + PANEL_W - 78, y, 70, 20)
                        .build();
                accept.active = snapshot != null && snapshot.licensed();
                if (!accept.active) {
                    accept.setTooltip(Tooltip.create(Component.translatable("contract.aerocouriers.error.no_license")));
                }
                addRenderableWidget(accept);
            } else if (!boardTab && "issuer".equals(entry.role()) && entry.state() == ContractState.OPEN) {
                addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.cancel"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.CANCEL, entry.id())))
                        .bounds(left + PANEL_W - 78, y, 70, 20)
                        .build());
            } else if (!boardTab && "courier".equals(entry.role()) && entry.state().isActive()) {
                addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.abandon"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.ABANDON, entry.id())))
                        .bounds(left + PANEL_W - 78, y, 70, 20)
                        .build());
            }
            if (selectedRow) {
                // selection is drawn in render(); the buttons sit on the row
            }
        }

        if (boardTab) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                        if (snapshot != null && snapshot.page() > 0) {
                            PacketDistributor.sendToServer(new OpenBoardPayload(snapshot.page() - 1));
                        } else if (listPage > 0) {
                            listPage--;
                            rebuildWidgets();
                        }
                    })
                    .bounds(left + 8, top + PANEL_H - 24, 20, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                        if (snapshot != null && (snapshot.page() + 1) * BoardSnapshotPayload.PAGE_SIZE < totalForServer) {
                            PacketDistributor.sendToServer(new OpenBoardPayload(snapshot.page() + 1));
                        } else if (listPage + 1 < pages) {
                            listPage++;
                            rebuildWidgets();
                        }
                    })
                    .bounds(left + 30, top + PANEL_H - 24, 20, 20)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                        if (listPage > 0) {
                            listPage--;
                            rebuildWidgets();
                        }
                    })
                    .bounds(left + 8, top + PANEL_H - 24, 20, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                        if (listPage + 1 < pages) {
                            listPage++;
                            rebuildWidgets();
                        }
                    })
                    .bounds(left + 30, top + PANEL_H - 24, 20, 20)
                    .build());
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

    private void initCreate() {
        int left = left();
        int top = top();
        int y = top + 54;
        fromXBox = coordBox(left + 8, y, 48, fromX, v -> fromX = v);
        fromYBox = coordBox(left + 58, y, 40, fromY, v -> fromY = v);
        fromZBox = coordBox(left + 100, y, 48, fromZ, v -> fromZ = v);
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.create.here"), b -> {
                    fillFrom(currentPos());
                    rebuildWidgets();
                })
                .bounds(left + 152, y, 56, 20)
                .build());
        addStationCycle(left + 212, y, 140, true);

        y = top + 96;
        toXBox = coordBox(left + 8, y, 48, toX, v -> toX = v);
        toYBox = coordBox(left + 58, y, 40, toY, v -> toY = v);
        toZBox = coordBox(left + 100, y, 48, toZ, v -> toZ = v);
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.create.here"), b -> {
                    fillTo(currentPos());
                    rebuildWidgets();
                })
                .bounds(left + 152, y, 56, 20)
                .build());
        addStationCycle(left + 212, y, 140, false);

        y = top + 138;
        rewardBox = numberBox(left + 8, y, 56, reward, v -> reward = v);
        collateralBox = numberBox(left + 96, y, 56, collateral, v -> collateral = v);
        minutesBox = numberBox(left + 184, y, 48, minutes, v -> minutes = v);
        radiusBox = numberBox(left + 264, y, 40, radius, v -> radius = v);

        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.create.submit"), b -> submitCreate())
                .bounds(left + PANEL_W - 118, top + PANEL_H - 48, 110, 20)
                .build());
    }

    private void addStationCycle(int x, int y, int w, boolean origin) {
        List<BoardSnapshotPayload.StationHint> stations = snapshot == null ? List.of() : snapshot.stations();
        List<String> values = new ArrayList<>();
        values.add("");
        stations.forEach(s -> values.add(s.name() + " @" + s.pos().toShortString()));
        addRenderableWidget(CycleButton.<String>builder(value -> value.isEmpty()
                        ? Component.translatable("ui.aerocouriers.create.no_station")
                        : Component.literal(value.contains(" @") ? value.substring(0, value.indexOf(" @")) : value))
                .withValues(values)
                .withInitialValue("")
                .create(x, y, w, 20, Component.translatable("ui.aerocouriers.create.station"), (button, value) -> {
                    if (value.isEmpty()) {
                        return;
                    }
                    stations.stream()
                            .filter(s -> (s.name() + " @" + s.pos().toShortString()).equals(value))
                            .findFirst()
                            .ifPresent(s -> {
                                if (origin) {
                                    fillFrom(s.pos());
                                } else {
                                    fillTo(s.pos());
                                }
                                rebuildWidgets();
                            });
                }));
    }

    private void initTest() {
        int left = left();
        int top = top();
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.test.license"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_LICENSE, Optional.empty())))
                .bounds(left + 8, top + 54, 200, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.test.money",
                        OperatorBoardPayload.TEST_MONEY), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.ADD_MONEY, Optional.empty())))
                .bounds(left + 8, top + 78, 200, 20)
                .build());
        Optional<UUID> id = Optional.ofNullable(selected);
        Button spawn = Button.builder(Component.translatable("ui.aerocouriers.test.forcespawn"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_SPAWN, id)))
                .bounds(left + 8, top + 122, 120, 20)
                .build();
        Button deliver = Button.builder(Component.translatable("ui.aerocouriers.test.forcedeliver"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_DELIVER, id)))
                .bounds(left + 132, top + 122, 120, 20)
                .build();
        spawn.active = selected != null;
        deliver.active = selected != null;
        addRenderableWidget(spawn);
        addRenderableWidget(deliver);
    }

    private EditBox coordBox(int x, int y, int w, String value, java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(9);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
        box.setValue(value);
        box.setResponder(sink);
        return addRenderableWidget(box);
    }

    private EditBox numberBox(int x, int y, int w, String value, java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(12);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,12}"));
        box.setValue(value);
        box.setResponder(sink);
        return addRenderableWidget(box);
    }

    private void fillFrom(BlockPos pos) {
        fromX = Integer.toString(pos.getX());
        fromY = Integer.toString(pos.getY());
        fromZ = Integer.toString(pos.getZ());
    }

    private void fillTo(BlockPos pos) {
        toX = Integer.toString(pos.getX());
        toY = Integer.toString(pos.getY());
        toZ = Integer.toString(pos.getZ());
    }

    private BlockPos currentPos() {
        Player player = minecraft == null ? null : minecraft.player;
        return player == null ? BlockPos.ZERO : player.blockPosition();
    }

    private void submitCreate() {
        Optional<BlockPos> from = parsePos(fromX, fromY, fromZ);
        Optional<BlockPos> to = parsePos(toX, toY, toZ);
        if (from.isEmpty() || to.isEmpty()) {
            notice = Component.translatable("ui.aerocouriers.create.bad_coords");
            return;
        }
        long rewardValue = parseLong(reward, CreateContractPayload.DEFAULT_REWARD);
        long collateralValue = parseLong(collateral, CreateContractPayload.DEFAULT_COLLATERAL);
        int minutesValue = (int) parseLong(minutes, Pricing.MIN_DEADLINE_MINUTES);
        int radiusValue = (int) parseLong(radius, CreateContractPayload.DEFAULT_RADIUS);
        PacketDistributor.sendToServer(new CreateContractPayload(
                from.get(), to.get(), rewardValue, collateralValue, minutesValue, radiusValue));
    }

    private static Optional<BlockPos> parsePos(String x, String y, String z) {
        try {
            if (x.isEmpty() || y.isEmpty() || z.isEmpty() || x.equals("-") || y.equals("-") || z.equals("-")) {
                return Optional.empty();
            }
            return Optional.of(new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xC0101010);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + PANEL_H - 1, 0xFF202020);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.drawString(font, title, left + 8, top + 32, 0xFFFFFF, false);
        long balance = snapshot == null ? 0 : snapshot.balance();
        graphics.drawString(font, Component.translatable("ui.aerocouriers.balance", balance),
                left + 150, top + 32, 0xFFE082, false);

        if (snapshot == null) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.loading"),
                    left + 8, top + 60, 0xAAAAAA, false);
            return;
        }

        if (tab == Tab.BOARD || tab == Tab.MINE) {
            renderList(graphics, tab == Tab.BOARD ? snapshot.board() : snapshot.mine(), left, top);
        } else if (tab == Tab.CREATE) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.create.from"),
                    left + 8, top + 44, 0xCCCCCC, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.create.to"),
                    left + 8, top + 86, 0xCCCCCC, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.create.reward"),
                    left + 8, top + 128, 0xCCCCCC, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.create.collateral"),
                    left + 96, top + 128, 0xCCCCCC, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.create.minutes"),
                    left + 184, top + 128, 0xCCCCCC, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.create.radius"),
                    left + 264, top + 128, 0xCCCCCC, false);
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.create.hint",
                            (int) ContractManager.ISSUER_REACH),
                    left + 8, top + 162, PANEL_W - 16, 0xAAAAAA);
            Optional<BlockPos> from = parsePos(fromX, fromY, fromZ);
            Optional<BlockPos> to = parsePos(toX, toY, toZ);
            if (from.isPresent() && to.isPresent()) {
                long min = Pricing.minReward(Math.sqrt(from.get().distSqr(to.get())));
                graphics.drawString(font, Component.translatable("ui.aerocouriers.create.min_reward", min),
                        left + 8, top + PANEL_H - 44, 0x8BC34A, false);
            }
        } else if (tab == Tab.TEST) {
            String id = selected == null ? "—" : snapshot.find(selected).map(BoardSnapshotPayload.BoardEntry::shortId)
                    .orElse(selected.toString().substring(0, 8));
            graphics.drawString(font, Component.translatable("ui.aerocouriers.test.selected", id),
                    left + 8, top + 106, 0xCCCCCC, false);
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.test.hint"),
                    left + 8, top + 148, PANEL_W - 16, 0xAAAAAA);
        }

        if (notice != null) {
            drawWrapped(graphics, notice, left + 56, top + PANEL_H - 22, PANEL_W - 64, 0xFFCC66);
        }
    }

    private void renderList(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> all, int left, int top) {
        if (all.isEmpty()) {
            graphics.drawString(font, Component.translatable("contract.aerocouriers.list.none"),
                    left + 8, top + 56, 0xAAAAAA, false);
        }
        int start = listPage * ROWS;
        for (int i = 0; i < ROWS && start + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(start + i);
            int y = top + 52 + i * 28;
            if (entry.id().equals(selected)) {
                graphics.fill(left + 6, y - 2, left + PANEL_W - 150, y + 24, 0x403D7EAF);
            }
            graphics.drawString(font, Component.translatable("ui.aerocouriers.row.main",
                            entry.shortId(), entry.originLabel(), entry.destinationLabel(), entry.distance()),
                    left + 8, y, 0xFFFFFF, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.row.meta",
                            entry.state().displayName(),
                            entry.reward(),
                            entry.collateral(),
                            entry.minutesLeft(),
                            entry.weight(),
                            entry.size(),
                            Component.translatable("contract.aerocouriers.role." + entry.role())),
                    left + 8, y + 10, 0xBBBBBB, false);
        }

        Optional<BoardSnapshotPayload.BoardEntry> shown = selected == null ? Optional.empty() : snapshot.find(selected);
        shown.ifPresent(entry -> {
            int y = top + 168;
            String cargo = entry.manifest().isEmpty()
                    ? "—"
                    : entry.manifest().stream()
                    .map(line -> line.count() + " × " + line.name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("—");
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.details.line",
                            entry.requiredRank().displayName(), entry.deadlineMinutes(), cargo),
                    left + 8, y, PANEL_W - 16, 0xDDDDDD);
            if (entry.state() == ContractState.OPEN) {
                drawWrapped(graphics, Component.translatable("contract.aerocouriers.risk",
                                entry.collateral(), entry.reward()),
                        left + 8, y + 20, PANEL_W - 16, 0xFFCC66);
            }
        });
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int drawY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, drawY, color, false);
            drawY += 9;
        }
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
