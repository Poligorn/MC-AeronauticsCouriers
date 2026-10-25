package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.ClaimMailPayload;
import dev.poligorn.aerocouriers.net.ContractIntentPayload;
import dev.poligorn.aerocouriers.net.OpenBoardPayload;
import dev.poligorn.aerocouriers.net.OperatorBoardPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Monitoring board: open orders and the viewer's own. Creation is the stamp item, not a tab.
 */
public final class CourierBoardScreen extends Screen {
    enum Tab {
        BOARD, MINE, TEST
    }

    private @Nullable BoardSnapshotPayload snapshot;
    private Tab tab = Tab.BOARD;
    private int scroll;
    private @Nullable UUID selected;
    private @Nullable UUID pendingAccept;
    private @Nullable Component notice;
    private @Nullable Component hoverTip;

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
        int tabW = Math.max(70, panelW() / 6);
        addTab(left + 8, top + 6, tabW, Tab.BOARD, "ui.aerocouriers.tab.board");
        addTab(left + 12 + tabW, top + 6, tabW, Tab.MINE, "ui.aerocouriers.tab.mine");
        if (snapshot != null && snapshot.operator()) {
            addTab(left + 16 + tabW * 2, top + 6, tabW, Tab.TEST, "ui.aerocouriers.tab.test");
        }

        int mail = snapshot == null ? 0 : snapshot.mailCount();
        addRenderableWidget(Button.builder(
                        Component.translatable("ui.aerocouriers.mail.claim", mail),
                        b -> PacketDistributor.sendToServer(ClaimMailPayload.INSTANCE))
                .bounds(left + panelW() - 126, top + 6, 118, 20)
                .build());

        if (snapshot == null) {
            return;
        }
        if (tab == Tab.TEST) {
            initTest();
        } else {
            initListButtons(tab == Tab.BOARD ? snapshot.board() : snapshot.mine(), tab == Tab.BOARD);
        }
    }

    private void addTab(int x, int y, int w, Tab value, String key) {
        Button button = Button.builder(Component.translatable(key), b -> {
                    tab = value;
                    scroll = 0;
                    pendingAccept = null;
                    rebuildWidgets();
                })
                .bounds(x, y, w, 20)
                .build();
        button.active = tab != value;
        addRenderableWidget(button);
    }

    private void initListButtons(List<BoardSnapshotPayload.BoardEntry> all, boolean boardTab) {
        int left = left();
        int top = top();
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int rowTop = listTop();
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int y = rowTop + i * rowH();
            addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.details"), b -> {
                        selected = entry.id();
                        pendingAccept = null;
                        rebuildWidgets();
                    })
                    .bounds(left + panelW() - 156, y, 68, 18)
                    .build());
            if (boardTab && entry.state() == ContractState.OPEN
                    && (!"issuer".equals(entry.role()) || snapshot.allowSelfAccept())) {
                boolean confirm = entry.id().equals(pendingAccept);
                Button accept = Button.builder(
                                Component.translatable(confirm ? "ui.aerocouriers.accept.confirm" : "ui.aerocouriers.accept",
                                        entry.collateral()),
                                b -> onAccept(entry))
                        .bounds(left + panelW() - 84, y, 76, 18)
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
                        .bounds(left + panelW() - 84, y, 76, 18)
                        .build());
            } else if (!boardTab && "courier".equals(entry.role()) && entry.state().isActive()) {
                addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.abandon"), b ->
                                PacketDistributor.sendToServer(
                                        new ContractIntentPayload(ContractIntentPayload.Kind.ABANDON, entry.id())))
                        .bounds(left + panelW() - 84, y, 76, 18)
                        .build());
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
        int left = left();
        int top = top();
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.test.license"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_LICENSE, Optional.empty())))
                .bounds(left + 8, top + 54, 220, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.test.money",
                        OperatorBoardPayload.TEST_MONEY), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.ADD_MONEY, Optional.empty())))
                .bounds(left + 8, top + 78, 220, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("ui.aerocouriers.test.stamp"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.GIVE_STAMP, Optional.empty())))
                .bounds(left + 8, top + 102, 220, 20)
                .build());
        Optional<UUID> id = Optional.ofNullable(selected);
        Button spawn = Button.builder(Component.translatable("ui.aerocouriers.test.forcespawn"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_SPAWN, id)))
                .bounds(left + 8, top + 146, 120, 20)
                .build();
        Button deliver = Button.builder(Component.translatable("ui.aerocouriers.test.forcedeliver"), b ->
                        PacketDistributor.sendToServer(new OperatorBoardPayload(
                                OperatorBoardPayload.Action.FORCE_DELIVER, id)))
                .bounds(left + 132, top + 146, 120, 20)
                .build();
        spawn.active = selected != null;
        deliver.active = selected != null;
        addRenderableWidget(spawn);
        addRenderableWidget(deliver);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.fill(left, top, left + panelW(), top + panelH(), 0xC0101010);
        graphics.fill(left + 1, top + 1, left + panelW() - 1, top + panelH() - 1, 0xFF202020);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.drawString(font, title, left + 8, top + 32, 0xFFFFFF, false);
        long balance = snapshot == null ? 0 : snapshot.balance();
        graphics.drawString(font, Component.translatable("ui.aerocouriers.balance", balance),
                left + Math.min(panelW() - 200, 200), top + 32, 0xFFE082, false);

        if (snapshot == null) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.loading"),
                    left + 8, top + 60, 0xAAAAAA, false);
            return;
        }

        if (tab == Tab.BOARD || tab == Tab.MINE) {
            renderList(graphics, tab == Tab.BOARD ? snapshot.board() : snapshot.mine(), left, top, mouseX, mouseY);
        } else if (tab == Tab.TEST) {
            String id = selected == null ? "—" : snapshot.find(selected).map(BoardSnapshotPayload.BoardEntry::shortId)
                    .orElse(selected.toString().substring(0, 8));
            graphics.drawString(font, Component.translatable("ui.aerocouriers.test.selected", id),
                    left + 8, top + 130, 0xCCCCCC, false);
            drawWrapped(graphics, Component.translatable("ui.aerocouriers.test.hint"),
                    left + 8, top + 176, panelW() - 16, 0xAAAAAA);
        }

        if (notice != null) {
            drawWrapped(graphics, notice, left + 8, top + panelH() - 22, panelW() - 16, 0xFFCC66);
        }
        if (hoverTip != null) {
            graphics.renderTooltip(font, font.split(hoverTip, 240), mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphics graphics, List<BoardSnapshotPayload.BoardEntry> all, int left, int top,
                            int mouseX, int mouseY) {
        if (all.isEmpty()) {
            graphics.drawString(font, Component.translatable("contract.aerocouriers.list.none"),
                    left + 8, listTop() + 4, 0xAAAAAA, false);
            return;
        }
        int visible = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - visible));
        int textW = panelW() - 168;
        graphics.enableScissor(left + 6, listTop() - 2, left + panelW() - 6, listTop() + visible * rowH());
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            BoardSnapshotPayload.BoardEntry entry = all.get(scroll + i);
            int y = listTop() + i * rowH();
            if (entry.id().equals(selected)) {
                graphics.fill(left + 6, y - 1, left + panelW() - 158, y + rowH() - 3, 0x403D7EAF);
            }
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
            drawClipped(graphics, main, left + 8, y, textW, 0xFFFFFF, mouseX, mouseY);
            drawClipped(graphics, meta, left + 8, y + 10, textW, 0xBBBBBB, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (all.size() > visible) {
            graphics.drawString(font, Component.translatable("ui.aerocouriers.scroll",
                            scroll + 1, Math.min(scroll + visible, all.size()), all.size()),
                    left + 8, detailsTop() - 12, 0x888888, false);
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
                    left + 8, y, panelW() - 16, 0xDDDDDD);
            if (entry.state() == ContractState.OPEN) {
                drawWrapped(graphics, Component.translatable("contract.aerocouriers.risk",
                                entry.collateral(), entry.reward()),
                        left + 8, y + 22, panelW() - 16, 0xFFCC66);
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
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int panelW() {
        return Mth.clamp((int) (width * 0.72), 400, Math.max(400, width - 24));
    }

    private int panelH() {
        return Mth.clamp((int) (height * 0.80), 280, Math.max(280, height - 24));
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return (height - panelH()) / 2;
    }

    private int rowH() {
        return 24;
    }

    private int listTop() {
        return top() + 50;
    }

    private int detailsTop() {
        return top() + panelH() - 78;
    }

    private int visibleRows() {
        return Math.max(3, (detailsTop() - listTop() - 8) / rowH());
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
