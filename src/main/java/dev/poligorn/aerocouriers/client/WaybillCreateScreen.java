package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.client.board.PixelGlyphs;
import dev.poligorn.aerocouriers.client.board.TabletDraw;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.contract.ContractDistances;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.ItemValues;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.CreateContractPayload;
import dev.poligorn.aerocouriers.net.OpenWaybillCreatePayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Player-facing order creation as a pressed-cardboard clipboard (approved 640×360 mockup).
 */
public final class WaybillCreateScreen extends Screen {
    private static final int[] DEADLINES = {30, 60, 180, 1440};
    private static final int[] ZONES = {4, 8, 16};
    private static final long SEAL_IMPACT_MS = 120L;
    private static final long SEAL_SETTLE_MS = 300L;
    private static final long SEAL_CLOSE_MS = 720L;
    private static final double AIRSHIP_BLOCKS_PER_SEC = 8.0;

    private final OpenWaybillCreatePayload preview;
    private String reward = Long.toString(CreateContractPayload.DEFAULT_REWARD);
    private String collateral = Long.toString(CreateContractPayload.DEFAULT_COLLATERAL);
    private String minutes = "60";
    private String radius = Integer.toString(CreateContractPayload.DEFAULT_RADIUS);
    private String toX = "";
    private String toY = "";
    private String toZ = "";
    private ResourceKey<Level> destinationDimension = Level.OVERWORLD;
    private DestKind destKind = DestKind.NONE;
    private String destName = "";
    private int rankFloorLevel;
    private boolean rewardTouched;
    private boolean collateralTouched;
    private boolean submitting;
    private boolean sealing;
    private boolean stampSoundPlayed;
    private long sealStartMs;
    private @Nullable BoardSnapshotPayload pendingBoard;
    private WaybillCreateLayout layout = WaybillCreateLayout.compute(640, 360);
    private final WaybillDestinationPicker picker;
    private final WaybillCargoOverlay cargoOverlay = new WaybillCargoOverlay();
    private int cargoLinkX;
    private int cargoLinkY;
    private int cargoLinkW;
    private boolean cargoLinkVisible;
    private @Nullable PaperEditBox rewardBox;
    private @Nullable PaperEditBox collateralBox;
    private @Nullable PaperEditBox minutesBox;
    private final PaperEditBox[] coordBoxes = new PaperEditBox[3];
    private @Nullable String[] hoverLines;
    private int hoverTipX;
    private int hoverTipY;
    private @Nullable SpurBreakdownTooltip hoverSpur;
    private @Nullable Component hoverSpurHeader;
    private @Nullable ItemStack hoveredStack;
    private int raiseX;
    private int raiseY;
    private int raiseW;
    private boolean raiseVisible;
    private boolean syncing;

    private enum DestKind {
        NONE, STATION, XAERO, COORDS
    }

    public WaybillCreateScreen(OpenWaybillCreatePayload preview) {
        super(Component.translatable("ui.aerocouriers.create.title"));
        this.preview = preview;
        this.picker = new WaybillDestinationPicker(this);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            destinationDimension = minecraft.level.dimension();
        }
    }

    public boolean isSealing() {
        return sealing;
    }

    public void beginSeal(BoardSnapshotPayload board) {
        pendingBoard = board;
        submitting = false;
        sealing = true;
        stampSoundPlayed = false;
        sealStartMs = Util.getMillis();
        picker.close();
        cargoOverlay.close();
        setBoxesActive(false);
    }

    public void onPublishDenied() {
        submitting = false;
        sealing = false;
        setBoxesActive(true);
    }

    void applyPicked(XaeroPickedWaypoint picked) {
        toX = Integer.toString(picked.x());
        toY = Integer.toString(picked.y());
        toZ = Integer.toString(picked.z());
        destKind = DestKind.XAERO;
        destName = picked.name();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            destinationDimension = minecraft.level.dimension();
        }
        onDestinationSet();
    }

    void applyStation(BoardSnapshotPayload.StationHint station) {
        toX = Integer.toString(station.pos().getX());
        toY = Integer.toString(station.pos().getY());
        toZ = Integer.toString(station.pos().getZ());
        destinationDimension = station.dimension();
        destKind = DestKind.STATION;
        destName = station.name();
        onDestinationSet();
    }

    void commitCoords() {
        Optional<BlockPos> pos = parsePos(toX, toY, toZ);
        if (pos.isEmpty()) {
            picker.setCoordsInvalid(true);
            ClientCouriersSounds.playOrderDeny();
            return;
        }
        applyCoords(pos.get());
        picker.close();
        syncBoxes();
        setFocused(null);
    }

    void applyCoords(BlockPos pos) {
        toX = Integer.toString(pos.getX());
        toY = Integer.toString(pos.getY());
        toZ = Integer.toString(pos.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            destinationDimension = minecraft.level.dimension();
        }
        destKind = DestKind.COORDS;
        destName = "";
        onDestinationSet();
    }

    void refreshAfterPick() {
        refreshForm();
    }

    void refreshForm() {
        syncBoxes();
    }

    Font waybillFont() {
        return font;
    }

    BlockPos previewOrigin() {
        return preview.origin();
    }

    List<BoardSnapshotPayload.StationHint> stations() {
        return preview.stations();
    }

    @Override
    protected void init() {
        super.init();
        layout = WaybillCreateLayout.compute(width, height);
        picker.close();
        cargoOverlay.close();
        rewardBox = moneyBox(layout.fieldX0, layout.rewardY, layout.fieldRight(), reward, v -> {
            reward = v;
            if (!syncing) {
                rewardTouched = true;
            }
        }, Component.translatable("ui.aerocouriers.create.reward"));
        addRenderableWidget(rewardBox);
        collateralBox = moneyBox(layout.fieldX0, layout.collateralY, layout.fieldRight(), collateral, v -> {
            collateral = v;
            if (!syncing) {
                collateralTouched = true;
            }
        }, Component.translatable("ui.aerocouriers.create.courier_collateral"));
        addRenderableWidget(collateralBox);
        minutesBox = moneyBox(layout.customMinutesX(), layout.deadlineY, layout.customMinutesX() + layout.customMinutesW(),
                minutes, v -> minutes = v, Component.translatable("ui.aerocouriers.create.minutes"));
        addRenderableWidget(minutesBox);
        for (int i = 0; i < 3; i++) {
            int column = i;
            String value = switch (i) {
                case 0 -> toX;
                case 1 -> toY;
                default -> toZ;
            };
            PaperEditBox box = coordBox(i, value, v -> {
                if (syncing) {
                    return;
                }
                switch (column) {
                    case 0 -> toX = v;
                    case 1 -> toY = v;
                    default -> toZ = v;
                }
                picker.setCoordsInvalid(false);
            });
            coordBoxes[i] = box;
            addRenderableWidget(box);
        }
        syncBoxes();
    }

    private PaperEditBox moneyBox(int x0, int y, int x1, String value, java.util.function.Consumer<String> sink,
                                  Component label) {
        int unitW = font.width(moneyUnit()) + 6;
        PaperEditBox box = new PaperEditBox(font, x0 + 5, y + 3, Math.max(12, x1 - x0 - 8 - unitW), 9, label);
        box.setMaxLength(12);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,12}"));
        box.setValue(value);
        box.setResponder(sink);
        return box;
    }

    private PaperEditBox coordBox(int column, String value, java.util.function.Consumer<String> sink) {
        Component label = switch (column) {
            case 0 -> Component.translatable("ui.aerocouriers.create.coord.x");
            case 1 -> Component.translatable("ui.aerocouriers.create.coord.y");
            default -> Component.translatable("ui.aerocouriers.create.coord.z");
        };
        PaperEditBox box = new PaperEditBox(font, 0, 0, 40, 9, label);
        box.setMaxLength(9);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
        box.setValue(value);
        box.setResponder(sink);
        box.visible = false;
        box.active = false;
        return box;
    }

    private void syncBoxes() {
        syncing = true;
        boolean live = !sealing && !submitting;
        if (rewardBox != null) {
            rewardBox.setValue(reward);
            rewardBox.active = live;
            rewardBox.visible = true;
            rewardBox.setX(layout.fieldX0 + 5);
            rewardBox.setY(layout.rewardY + 3);
        }
        if (collateralBox != null) {
            collateralBox.setValue(collateral);
            collateralBox.active = live;
            collateralBox.setX(layout.fieldX0 + 5);
            collateralBox.setY(layout.collateralY + 3);
        }
        boolean customMinutes = deadlineIndex() == 4;
        if (minutesBox != null) {
            minutesBox.setValue(minutes);
            minutesBox.visible = customMinutes;
            minutesBox.active = customMinutes && live;
            minutesBox.setX(layout.customMinutesX() + 5);
            minutesBox.setY(layout.deadlineY + 3);
            minutesBox.setWidth(Math.max(12, layout.customMinutesW() - 16));
        }
        boolean coords = picker.isOpen() && picker.tab() == WaybillDestinationPicker.Tab.COORDS;
        for (int i = 0; i < 3; i++) {
            PaperEditBox box = coordBoxes[i];
            if (box == null) {
                continue;
            }
            box.visible = coords;
            box.active = coords && live;
            if (coords) {
                box.setX(picker.coordFieldX(i) + 4);
                box.setY(picker.coordFieldY() + 3);
                box.setWidth(Math.max(12, picker.coordFieldW() - 8));
            }
            String value = switch (i) {
                case 0 -> toX;
                case 1 -> toY;
                default -> toZ;
            };
            if (!box.getValue().equals(value)) {
                box.setValue(value);
            }
        }
        setBoxesActive(live);
        syncing = false;
    }

    private void setBoxesActive(boolean live) {
        if (rewardBox != null) {
            rewardBox.active = live;
        }
        if (collateralBox != null) {
            collateralBox.active = live;
        }
        if (minutesBox != null) {
            minutesBox.active = live && minutesBox.visible;
        }
        for (PaperEditBox box : coordBoxes) {
            if (box != null) {
                box.active = live && box.visible;
            }
        }
    }

    private void onDestinationSet() {
        Optional<BlockPos> to = parsePos(toX, toY, toZ);
        if (to.isEmpty()) {
            return;
        }
        long minReward = minRewardFor(to.get());
        long minCollateral = Pricing.minP2pCollateral(parseLong(reward, minReward), preview.cargo());
        if (!rewardTouched || reward.isEmpty() || parseLong(reward, 0) == CreateContractPayload.DEFAULT_REWARD) {
            reward = Long.toString(minReward);
            rewardTouched = false;
        }
        minCollateral = Pricing.minP2pCollateral(parseLong(reward, minReward), preview.cargo());
        if (!collateralTouched || collateral.isEmpty()
                || parseLong(collateral, 0) == CreateContractPayload.DEFAULT_COLLATERAL) {
            collateral = Long.toString(minCollateral);
            collateralTouched = false;
        }
        syncBoxes();
    }

    @Override
    public void tick() {
        super.tick();
        if (sealing && pendingBoard != null && Util.getMillis() - sealStartMs >= SEAL_CLOSE_MS) {
            Minecraft.getInstance().setScreen(new CourierBoardScreen(pendingBoard));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InGamePauseBackground.render(this, graphics, mouseX, mouseY, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverLines = null;
        hoverSpur = null;
        hoverSpurHeader = null;
        hoveredStack = null;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int lx = layout.localXi(mouseX);
        int ly = layout.localYi(mouseY);
        graphics.pose().pushPose();
        if (layout.fitScale < 1.0) {
            float fit = (float) layout.fitScale;
            graphics.pose().translate(layout.fitX, layout.fitY, 0.0);
            graphics.pose().scale(fit, fit, fit);
        }
        try {
            int shake = paperShake();
            WaybillCreateGui.drawBoard(graphics, layout);
            WaybillCreateGui.drawPaper(graphics, layout, shake);
            drawSheet(graphics, lx, ly, shake);
            WaybillCreateGui.drawClip(graphics, layout);
            for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
                widget.render(graphics, lx, ly, partialTick);
            }
            picker.render(graphics, font, lx, ly);
            if (picker.isOpen() && picker.tab() == WaybillDestinationPicker.Tab.COORDS) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 420.0F);
                for (PaperEditBox box : coordBoxes) {
                    if (box != null) {
                        box.render(graphics, lx, ly, partialTick);
                    }
                }
                graphics.pose().popPose();
            }
            ItemStack overlayStack = cargoOverlay.render(graphics, font, lx, ly, preview.cargo(), cargoSize(),
                    preview.weight());
            if (cargoOverlay.isOpen() && cargoOverlay.contains(lx, ly)) {
                hoveredStack = overlayStack;
            }
            drawStamp(graphics);
        } finally {
            graphics.pose().popPose();
        }
        if (!picker.isOpen() && !sealing) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0, 0.0, 800.0);
            try {
                if (hoveredStack != null) {
                    graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
                } else if (hoverSpur != null && hoverSpurHeader != null) {
                    graphics.renderTooltip(font, List.of(hoverSpurHeader), Optional.of(hoverSpur), mouseX, mouseY);
                } else if (hoverLines != null && !cargoOverlay.isOpen()) {
                    WaybillCreateGui.tooltip(graphics, font, toScreenX(hoverTipX), toScreenY(hoverTipY), hoverLines);
                }
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    /** Layout pixel → screen pixel. Identity when the clipboard is not pose-scaled. */
    private int toScreenX(int localX) {
        return (int) Math.round(layout.fitX + localX * layout.fitScale);
    }

    private int toScreenY(int localY) {
        return (int) Math.round(layout.fitY + localY * layout.fitScale);
    }

    private void drawSheet(GuiGraphics g, int mouseX, int mouseY, int shake) {
        int x0 = layout.contentX0;
        int x1 = layout.contentX1;
        int yOff = shake;
        WaybillCreateGui.tracked(g, font, tr("ui.aerocouriers.create.title"), x0, layout.headerY + yOff,
                WaybillPalette.INK, 1);
        String customer = minecraft != null && minecraft.player != null
                ? minecraft.player.getGameProfile().getName() : "";
        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.customer", customer),
                x0, layout.headerSubY + yOff, WaybillPalette.INK2);
        long pay = chargeNow();
        String bal = MoneyFormat.text(preview.balance());
        int bx = x1 - font.width(bal);
        WaybillCreateGui.spur(g, bx - 11, layout.headerY + yOff);
        WaybillCreateGui.bold(g, font, bal, bx, layout.headerY + yOff, WaybillPalette.GOLD);
        offerMoneyTooltip(preview.balance(), bx - 11, layout.headerY + yOff, 11 + font.width(bal), 10, mouseX, mouseY);
        String after = tr("ui.aerocouriers.create.after_post",
                MoneyFormat.text(preview.balance() - pay));
        WaybillCreateGui.textRight(g, font, after, x1, layout.headerSubY + yOff, WaybillPalette.INK2, false);
        WaybillCreateGui.hline(g, x0, x1, layout.headerRuleY + yOff, WaybillPalette.RULE);

        WaybillCreateGui.section(g, font, x0, layout.leftColX1, layout.section1Y + yOff, 1,
                tr("ui.aerocouriers.create.section.cargo"));
        WaybillCreateGui.section(g, font, layout.rightColX0, x1, layout.section1Y + yOff, 2,
                tr("ui.aerocouriers.create.section.terms"));
        drawCargo(g, mouseX, mouseY, yOff);
        drawTerms(g, mouseX, mouseY, yOff);
        drawRoute(g, mouseX, mouseY, yOff);
        drawTotal(g, mouseX, mouseY, yOff);
        drawFooter(g, mouseX, mouseY, yOff);
    }

    private void drawCargo(GuiGraphics g, int mouseX, int mouseY, int yOff) {
        int x0 = layout.contentX0;
        List<ItemStack> cargo = preview.cargo();
        CargoSize size = cargoSize();
        int slots = size.slots();
        int shown = Math.min(9, slots);
        int ninthX = x0;
        int ninthY = layout.slotY + yOff;
        for (int i = 0; i < shown; i++) {
            int sx = x0 + i * WaybillCreateLayout.SLOT;
            int sy = layout.slotY + yOff;
            if (i == 8) {
                ninthX = sx;
                ninthY = sy;
            }
            WaybillCreateGui.slot(g, sx, sy);
            if (i < cargo.size() && !cargo.get(i).isEmpty()) {
                ItemStack stack = cargo.get(i);
                g.renderItem(stack, sx + 1, sy + 1);
                g.renderItemDecorations(font, stack, sx + 1, sy + 1, "");
                WaybillCreateGui.stackCount(g, font, stack, sx, sy);
                if (!cargoOverlay.isOpen()
                        && TabletDraw.inside(mouseX, mouseY, sx, sy, WaybillCreateLayout.SLOT, WaybillCreateLayout.SLOT)) {
                    hoveredStack = stack;
                }
            }
        }
        int hidden = Math.max(0, cargo.size() - 9);
        if (hidden > 0 && shown == 9) {
            WaybillCreateGui.hiddenStacksBadge(g, font, ninthX, ninthY, hidden);
        }
        cargoLinkVisible = slots > 9;
        if (cargoLinkVisible) {
            String link = tr("ui.aerocouriers.create.shown_link", slots);
            cargoLinkX = x0;
            cargoLinkY = layout.occupiedY + yOff;
            int textX = x0 + 12;
            cargoLinkW = 12 + font.width(WaybillFonts.ink(link));
            boolean hover = !cargoOverlay.isOpen() && !picker.isOpen()
                    && TabletDraw.inside(mouseX, mouseY, cargoLinkX, cargoLinkY, cargoLinkW, 10);
            int col = hover ? WaybillPalette.BLUE : WaybillPalette.BLUE;
            PixelGlyphs.draw(g, x0, cargoLinkY + 1, PixelGlyphs.SHEET_GRID, col);
            WaybillCreateGui.text(g, font, link, textX, cargoLinkY, col);
            WaybillCreateGui.hline(g, textX, textX + font.width(WaybillFonts.ink(link)) - 1, cargoLinkY + 8, col);
        } else {
            WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.occupied", cargo.size(), slots),
                    x0, layout.occupiedY + yOff, WaybillPalette.INK2);
        }
        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.weight"), x0, layout.weightY + yOff, WaybillPalette.INK);
        String weight = tr("ui.aerocouriers.create.weight_value",
                TabletDraw.weight(preview.weight()), TabletDraw.num(size.maxWeight()));
        WaybillCreateGui.textRight(g, font, weight, layout.leftColX1, layout.weightY + yOff, WaybillPalette.INK, false);
        double frac = size.maxWeight() <= 0 ? 0 : preview.weight() / size.maxWeight();
        WaybillCreateGui.weightBar(g, x0, layout.weightBarY + yOff, layout.leftColX1, frac);
        String sizeReason = tr(sizeBySlots(size) ? "ui.aerocouriers.create.container.by_slots"
                : "ui.aerocouriers.create.container.by_weight", size.name());
        WaybillCreateGui.metaRow(g, font, x0, layout.leftColX1, layout.cargoMetaY + yOff,
                tr("ui.aerocouriers.create.container"), sizeReason, null, 0);
        long value = ItemValues.of(cargo);
        String valueText = tr("ui.aerocouriers.create.cargo_value_amount", MoneyFormat.text(value));
        WaybillCreateGui.metaRow(g, font, x0, layout.leftColX1, layout.cargoMetaY + 12 + yOff,
                tr("ui.aerocouriers.create.cargo_value"), valueText, null, 0);
        WaybillCreateGui.spur(g, layout.leftColX1 - font.width(valueText) - 9, layout.cargoMetaY + 12 + yOff);
        BlockPos origin = preview.origin();
        String from = origin.getX() + ", " + origin.getY() + ", " + origin.getZ();
        WaybillCreateGui.metaRow(g, font, x0, layout.leftColX1, layout.cargoMetaY + 24 + yOff,
                tr("ui.aerocouriers.create.from_chest"), from, PixelGlyphs.CHEST, WaybillPalette.BROWN);
    }

    private void drawTerms(GuiGraphics g, int mouseX, int mouseY, int yOff) {
        int rx = layout.rightColX0;
        int x1 = layout.contentX1;
        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.reward"), rx, layout.rewardY + 3 + yOff,
                WaybillPalette.INK2);
        WaybillCreateGui.field(g, layout.fieldX0, layout.rewardY + yOff, x1, false);
        drawUnit(g, x1, layout.rewardY + 3 + yOff, moneyUnit());
        offerMoneyTooltip(parseLong(reward, 0), layout.fieldX0, layout.rewardY + yOff,
                x1 - layout.fieldX0, WaybillCreateLayout.FIELD_H, mouseX, mouseY);

        String collLabel = tr("ui.aerocouriers.create.courier_collateral");
        WaybillCreateGui.text(g, font, collLabel, rx, layout.collateralY + 3 + yOff, WaybillPalette.INK2);
        int helpX = rx + font.width(collLabel) + 5;
        int helpY = layout.collateralY + 2 + yOff;
        WaybillCreateGui.helpIcon(g, font, helpX, helpY);
        long minColl = minCollateralNow();
        boolean bad = dest().isPresent() && parseLong(collateral, 0) < minColl;
        WaybillCreateGui.field(g, layout.fieldX0, layout.collateralY + yOff, x1, bad);
        drawUnit(g, x1, layout.collateralY + 3 + yOff, moneyUnit());
        offerMoneyTooltip(parseLong(collateral, 0), layout.fieldX0, layout.collateralY + yOff,
                x1 - layout.fieldX0, WaybillCreateLayout.FIELD_H, mouseX, mouseY);
        if (bad) {
            WaybillCreateGui.textRight(g, font, tr("ui.aerocouriers.create.collateral_low"), x1,
                    layout.collateralErrY + yOff, WaybillPalette.RED, false);
        }
        if (!picker.isOpen() && TabletDraw.inside(mouseX, mouseY, helpX, helpY, 9, 9)) {
            hoverLines = new String[] {
                    tr("ui.aerocouriers.create.collateral_hint.1", MoneyFormat.text(minColl)),
                    tr("ui.aerocouriers.create.collateral_hint.2", MoneyFormat.text(ItemValues.of(preview.cargo()))),
                    tr("ui.aerocouriers.create.collateral_hint.3")
            };
            hoverTipX = helpX + 12;
            hoverTipY = helpY + 12;
        }

        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.deadline"), rx, layout.deadlineY + 2 + yOff,
                WaybillPalette.INK2);
        int selectedDeadline = deadlineIndex();
        String[] labs = {
                tr("ui.aerocouriers.create.deadline.30m"),
                tr("ui.aerocouriers.create.deadline.1h"),
                tr("ui.aerocouriers.create.deadline.3h"),
                tr("ui.aerocouriers.create.deadline.1d"),
                tr("ui.aerocouriers.create.deadline.custom")
        };
        for (int i = 0; i < 5; i++) {
            int cx = layout.durationChipX(i);
            int cy = layout.deadlineY + yOff;
            boolean hover = TabletDraw.inside(mouseX, mouseY, cx, cy, 22, WaybillCreateLayout.CHIP_H);
            WaybillCreateGui.chip(g, font, cx, cy, 22, labs[i], i == selectedDeadline, hover);
        }
        if (selectedDeadline == 4) {
            WaybillCreateGui.field(g, layout.customMinutesX(), layout.deadlineY + yOff,
                    layout.customMinutesX() + layout.customMinutesW(), false);
            drawUnit(g, layout.customMinutesX() + layout.customMinutesW(), layout.deadlineY + 3 + yOff,
                    tr("ui.aerocouriers.create.minutes_unit"));
        }

        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.rank"), rx, layout.rankY + yOff, WaybillPalette.INK2);
        LicenseRank required = requiredRank();
        String rankText = rankLabel(required);
        WaybillCreateGui.textRight(g, font, rankText, x1, layout.rankY + yOff, WaybillPalette.INK, false);
        boolean canRaise = required.level() < 5 && dest().isPresent();
        boolean canReset = rankFloorLevel > autoRank().level();
        raiseVisible = canRaise || canReset;
        if (raiseVisible) {
            String raise = tr(canRaise ? "ui.aerocouriers.create.rank.raise" : "ui.aerocouriers.create.rank.reset");
            raiseW = font.width(raise);
            raiseX = x1 - raiseW;
            raiseY = layout.rankRaiseY + yOff;
            boolean hover = TabletDraw.inside(mouseX, mouseY, raiseX - 10, raiseY, raiseW + 10, 10);
            int col = WaybillPalette.BLUE;
            if (canRaise) {
                PixelGlyphs.draw(g, raiseX - 9, raiseY + 1, PixelGlyphs.UP, col);
            }
            WaybillCreateGui.text(g, font, raise, raiseX, raiseY, col);
            WaybillCreateGui.hline(g, raiseX, x1 - 1, raiseY + 8, col);
            if (hover) {
                hoverLines = new String[]{tr("ui.aerocouriers.create.rank.reset_hint")};
                hoverTipX = raiseX;
                hoverTipY = raiseY + 12;
            }
        }
    }

    private void drawRoute(GuiGraphics g, int mouseX, int mouseY, int yOff) {
        int x0 = layout.contentX0;
        int x1 = layout.contentX1;
        WaybillCreateGui.section(g, font, x0, x1, layout.section3Y + yOff, 3, tr("ui.aerocouriers.create.section.route"));
        int ry = layout.routeY + yOff;
        WaybillCreateGui.fill(g, x0, ry + 1, x0 + 6, ry + 7, WaybillPalette.ORIGIN_DOT);
        WaybillCreateGui.outline(g, x0, ry + 1, x0 + 6, ry + 7, WaybillPalette.INK);
        WaybillCreateGui.bold(g, font, "A", x0 + 10, ry, WaybillPalette.INK);
        BlockPos origin = preview.origin();
        WaybillCreateGui.text(g, font, origin.getX() + ", " + origin.getY() + ", " + origin.getZ(),
                x0 + 20, ry, WaybillPalette.INK);
        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.here_at_chest"), x0 + 10, ry + 11, WaybillPalette.INK2);
        int ax = x0 + 104;
        WaybillCreateGui.dashArrow(g, ax, ax + 30, ry + 4);
        int bx0 = x0 + 148;
        WaybillCreateGui.fill(g, bx0, ry + 1, bx0 + 6, ry + 7, WaybillPalette.DEST_DOT);
        WaybillCreateGui.outline(g, bx0, ry + 1, bx0 + 6, ry + 7, WaybillPalette.INK);
        WaybillCreateGui.bold(g, font, "B", bx0 + 10, ry, WaybillPalette.INK);
        WaybillCreateGui.text(g, font, destMain(), bx0 + 20, ry, WaybillPalette.INK);
        WaybillCreateGui.text(g, font, destSub(), bx0 + 10, ry + 11, WaybillPalette.INK2);

        int px = layout.pickX;
        int py = layout.pickY + yOff;
        boolean pickHover = !picker.isOpen() && TabletDraw.inside(mouseX, mouseY, px, py, WaybillCreateLayout.PICK_W,
                WaybillCreateLayout.PICK_H);
        WaybillCreateGui.button(g, font, px, py, WaybillCreateLayout.PICK_W, WaybillCreateLayout.PICK_H,
                tr("ui.aerocouriers.create.pick"), WaybillCreateGui.ButtonKind.PICK, PixelGlyphs.PIN, pickHover);
        if (pickHover) {
            hoverLines = new String[] {
                    tr("ui.aerocouriers.create.pick.hint.1"),
                    tr("ui.aerocouriers.create.pick.hint.2")
            };
            hoverTipX = px - 30;
            hoverTipY = py + 22;
        }

        WaybillCreateGui.textRight(g, font, tr("ui.aerocouriers.create.zone"), x1, ry - 1, WaybillPalette.INK2, false);
        int zoneSel = zoneIndex();
        for (int i = 0; i < 3; i++) {
            int cx = layout.zoneChipX(i);
            int cy = layout.zoneChipY + yOff;
            boolean hover = TabletDraw.inside(mouseX, mouseY, cx, cy, 26, WaybillCreateLayout.CHIP_H);
            WaybillCreateGui.chip(g, font, cx, cy, 26, Integer.toString(ZONES[i]), i == zoneSel, hover);
        }

        int sy = layout.summaryY + yOff;
        WaybillCreateGui.fill(g, x0, sy, x1, sy + 12, WaybillPalette.PAPER2);
        WaybillCreateGui.text(g, font, summaryLine(), x0 + 5, sy + 2, WaybillPalette.INK);
    }

    private void drawTotal(GuiGraphics g, int mouseX, int mouseY, int yOff) {
        int x0 = layout.contentX0;
        int x1 = layout.contentX1;
        WaybillCreateGui.section(g, font, x0, x1, layout.section4Y + yOff, 4, tr("ui.aerocouriers.create.section.total"));
        WaybillCreateGui.text(g, font, tr("ui.aerocouriers.create.pay_now"), x0, layout.payY + yOff, WaybillPalette.INK);
        long rewardValue = parseLong(reward, 0);
        long fee = Pricing.fee(rewardValue, cargoSize());
        String tot = MoneyFormat.text(rewardValue + fee);
        int totX = WaybillCreateGui.textRight(g, font, tot, x1, layout.payY + yOff, WaybillPalette.GOLD, true);
        offerMoneyTooltip(rewardValue + fee, totX, layout.payY + yOff, x1 - totX, 10, mouseX, mouseY);
        String br = tr("ui.aerocouriers.create.pay_breakdown", MoneyFormat.text(rewardValue), MoneyFormat.text(fee));
        WaybillCreateGui.textRight(g, font, br, totX - 4, layout.payY + yOff, WaybillPalette.INK, false);
        String risk = tr("ui.aerocouriers.create.risk_issuer",
                MoneyFormat.text(parseLong(collateral, 0)), MoneyFormat.text(rewardValue));
        String shown = TabletDraw.trunc(font, risk, Math.max(16, x1 - x0 - 22));
        WaybillCreateGui.riskBanner(g, font, x0, layout.riskY + yOff, x1, shown);
        if (!shown.equals(risk) && TabletDraw.inside(mouseX, mouseY, x0, layout.riskY + yOff, x1 - x0, 14)) {
            hoverLines = new String[] {risk};
            hoverTipX = mouseX + 8;
            hoverTipY = mouseY + 12;
        }
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY, int yOff) {
        int x0 = layout.contentX0;
        int x1 = layout.contentX1;
        int by = layout.buttonY + yOff;
        WaybillCreateGui.hline(g, x0, x1, layout.footerRuleY + yOff, WaybillPalette.RULE);
        boolean cancelHover = TabletDraw.inside(mouseX, mouseY, layout.cancelX, by, layout.cancelW, WaybillCreateLayout.BTN_H)
                && !sealing;
        WaybillCreateGui.button(g, font, layout.cancelX, by, layout.cancelW, WaybillCreateLayout.BTN_H,
                tr("gui.cancel"), WaybillCreateGui.ButtonKind.CANCEL, null, cancelHover);
        String deny = denyReason();
        boolean can = deny == null && !submitting && !sealing;
        String submitLabel = sealing
                ? tr("ui.aerocouriers.create.posted")
                : tr("ui.aerocouriers.create.submit_amount", MoneyFormat.text(chargeNow()));
        boolean submitHover = can && TabletDraw.inside(mouseX, mouseY, layout.submitX, by, layout.submitW,
                WaybillCreateLayout.BTN_H);
        WaybillCreateGui.button(g, font, layout.submitX, by, layout.submitW, WaybillCreateLayout.BTN_H, submitLabel,
                can ? WaybillCreateGui.ButtonKind.PRIMARY : WaybillCreateGui.ButtonKind.DISABLED, null, submitHover);
        if (can) {
            int kw = WaybillCreateGui.keycapWidth(font, "Enter");
            WaybillCreateGui.keycap(g, font, layout.submitX - 8 - kw, by + 4, "Enter");
        } else if (deny != null && !sealing) {
            WaybillCreateGui.textRight(g, font, deny, layout.submitX - 10, by + 6, WaybillPalette.RED, false);
        }
    }

    private void drawStamp(GuiGraphics g) {
        if (!sealing) {
            return;
        }
        long t = Util.getMillis() - sealStartMs;
        float scale;
        float rot;
        float alpha;
        int ox;
        int oy;
        boolean burst = false;
        if (t < SEAL_IMPACT_MS) {
            float p = t / (float) SEAL_IMPACT_MS;
            scale = Mth.lerp(p, 3.0F, 2.0F);
            rot = Mth.lerp(p, -12.0F, -7.0F);
            alpha = Mth.lerp(p, 0.4F, 1.0F);
            ox = Math.round(Mth.lerp(p, -8.0F, 0.0F));
            oy = Math.round(Mth.lerp(p, -40.0F, 0.0F));
        } else {
            float p = Mth.clamp((t - SEAL_IMPACT_MS) / (float) (SEAL_SETTLE_MS - SEAL_IMPACT_MS), 0.0F, 1.0F);
            scale = 2.0F;
            rot = -7.0F;
            alpha = Mth.lerp(p, 1.0F, 0.82F);
            ox = 0;
            oy = 0;
            burst = t < SEAL_IMPACT_MS + 100;
            if (!stampSoundPlayed) {
                stampSoundPlayed = true;
                ClientCouriersSounds.playWaybillStamp();
            }
        }
        int cx = layout.sx(430) + ox;
        int cy = layout.sy(266) + oy;
        if (burst) {
            WaybillCreateGui.stampBurst(g, cx, cy, scale);
        }
        WaybillCreateGui.stamp(g, font, cx, cy, scale, rot, alpha);
    }

    private int paperShake() {
        if (!sealing) {
            return 0;
        }
        long t = Util.getMillis() - sealStartMs;
        return t >= SEAL_IMPACT_MS && t < SEAL_IMPACT_MS + 80 ? 1 : 0;
    }

    /** Same Numismatics breakdown the tablet uses. Without that economy the amounts stay plain text. */
    private void offerMoneyTooltip(long spurs, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (!preview.numismaticsEconomy() || hoverSpur != null || picker.isOpen() || cargoOverlay.isOpen()) {
            return;
        }
        if (TabletDraw.inside(mouseX, mouseY, x, y, w, h)) {
            hoverSpur = new SpurBreakdownTooltip(spurs);
            hoverSpurHeader = Component.translatable("ui.aerocouriers.spur.tooltip.header", MoneyFormat.component(spurs));
        }
    }

    private void drawUnit(GuiGraphics g, int fieldRight, int y, String unit) {
        WaybillCreateGui.textRight(g, font, unit, fieldRight - 4, y, WaybillPalette.INK2, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sealing) {
            return true;
        }
        mouseX = layout.localX(mouseX);
        mouseY = layout.localY(mouseY);
        if (cargoOverlay.isOpen()) {
            if (cargoOverlay.handleClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (picker.isOpen() && !picker.contains(mouseX, mouseY)) {
            picker.close();
            syncBoxes();
            return true;
        }
        if (picker.isOpen() && picker.contains(mouseX, mouseY)) {
            if (picker.handleClick(mouseX, mouseY, button)) {
                syncBoxes();
            } else {
                super.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (raiseVisible && TabletDraw.inside(mouseX, mouseY, raiseX - 10, raiseY, raiseW + 10, 10)) {
            if (button == 1) {
                rankFloorLevel = 0;
                return true;
            }
            if (button == 0) {
                LicenseRank required = requiredRank();
                if (required.level() >= 5) {
                    rankFloorLevel = 0;
                } else {
                    rankFloorLevel = required.level() + 1;
                }
                return true;
            }
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int selectedDeadline = deadlineIndex();
        for (int i = 0; i < 5; i++) {
            if (TabletDraw.inside(mouseX, mouseY, layout.durationChipX(i), layout.deadlineY, 22,
                    WaybillCreateLayout.CHIP_H)) {
                if (i < 4) {
                    minutes = Integer.toString(DEADLINES[i]);
                } else if (selectedDeadline != 4) {
                    minutes = "";
                }
                syncBoxes();
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (TabletDraw.inside(mouseX, mouseY, layout.zoneChipX(i), layout.zoneChipY, 26,
                    WaybillCreateLayout.CHIP_H)) {
                radius = Integer.toString(ZONES[i]);
                return true;
            }
        }
        if (cargoLinkVisible && TabletDraw.inside(mouseX, mouseY, cargoLinkX, cargoLinkY, cargoLinkW, 10)) {
            openCargoOverlay();
            return true;
        }
        if (TabletDraw.inside(mouseX, mouseY, layout.pickX, layout.pickY, WaybillCreateLayout.PICK_W,
                WaybillCreateLayout.PICK_H)) {
            cargoOverlay.close();
            picker.open(layout.pickX, layout.pickY, WaybillCreateLayout.PICK_W);
            syncBoxes();
            return true;
        }
        if (TabletDraw.inside(mouseX, mouseY, layout.cancelX, layout.buttonY, layout.cancelW, WaybillCreateLayout.BTN_H)) {
            onClose();
            return true;
        }
        if (denyReason() == null && !submitting
                && TabletDraw.inside(mouseX, mouseY, layout.submitX, layout.buttonY, layout.submitW,
                WaybillCreateLayout.BTN_H)) {
            submit();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(layout.localX(mouseX), layout.localY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double fit = layout.fitScale;
        return super.mouseDragged(layout.localX(mouseX), layout.localY(mouseY), button, dragX / fit, dragY / fit);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = layout.localX(mouseX);
        mouseY = layout.localY(mouseY);
        if (picker.isOpen() && picker.contains(mouseX, mouseY) && picker.handleScroll(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sealing) {
            return true;
        }
        if (cargoOverlay.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cargoOverlay.close();
            }
            return true;
        }
        if (picker.isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            picker.close();
            syncBoxes();
            return true;
        }
        if (picker.isOpen() && picker.tab() == WaybillDestinationPicker.Tab.COORDS) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitCoords();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (cargoLinkVisible && keyCode == GLFW.GLFW_KEY_G && !hasShiftDown() && !hasControlDown() && !hasAltDown()) {
            openCargoOverlay();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && denyReason() == null) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openCargoOverlay() {
        if (!cargoLinkVisible || sealing) {
            return;
        }
        if (cargoOverlay.isOpen()) {
            cargoOverlay.close();
            return;
        }
        picker.close();
        cargoOverlay.open(layout, cargoSize().slots(), cargoLinkX + 12);
        setFocused(null);
    }

    private void submit() {
        String deny = denyReason();
        if (deny != null || submitting || sealing) {
            ClientCouriersSounds.playOrderDeny();
            return;
        }
        Optional<BlockPos> to = dest();
        if (to.isEmpty()) {
            ClientCouriersSounds.playOrderDeny();
            return;
        }
        submitting = true;
        setBoxesActive(false);
        PacketDistributor.sendToServer(new CreateContractPayload(
                preview.origin(), to.get(), destinationDimension,
                parseLong(reward, CreateContractPayload.DEFAULT_REWARD),
                parseLong(collateral, CreateContractPayload.DEFAULT_COLLATERAL),
                (int) parseLong(minutes, 60),
                (int) parseLong(radius, CreateContractPayload.DEFAULT_RADIUS),
                rankFloorLevel));
    }

    private @Nullable String denyReason() {
        Optional<BlockPos> to = dest();
        if (to.isEmpty()) {
            return tr("ui.aerocouriers.create.need_dest");
        }
        int mins = (int) parseLong(minutes, 0);
        if (mins < Pricing.MIN_DEADLINE_MINUTES || mins > Pricing.MAX_DEADLINE_MINUTES) {
            return tr("ui.aerocouriers.create.need_deadline");
        }
        long rewardValue = parseLong(reward, 0);
        long minReward = minRewardFor(to.get());
        if (rewardValue < minReward) {
            return tr("ui.aerocouriers.create.reward_need", MoneyFormat.text(minReward));
        }
        long coll = parseLong(collateral, 0);
        long minColl = Pricing.minP2pCollateral(rewardValue, preview.cargo());
        if (coll < minColl) {
            return tr("ui.aerocouriers.create.collateral_need", MoneyFormat.text(minColl));
        }
        long fee = Pricing.fee(rewardValue, cargoSize());
        if (preview.balance() < rewardValue + fee) {
            return tr("ui.aerocouriers.create.need_money", MoneyFormat.text(rewardValue + fee));
        }
        if (preview.cargo().isEmpty()) {
            return tr("contract.aerocouriers.error.empty_cargo");
        }
        return null;
    }

    private String destMain() {
        return switch (destKind) {
            case NONE -> tr("ui.aerocouriers.create.dest.unset");
            case STATION -> destName;
            case XAERO -> {
                Optional<BlockPos> to = dest();
                yield to.map(p -> tr("ui.aerocouriers.create.dest.coords",
                        TabletDraw.num(p.getX()), TabletDraw.num(p.getY()), TabletDraw.num(p.getZ()))).orElse(destName);
            }
            case COORDS -> dest().map(p -> tr("ui.aerocouriers.create.dest.coords",
                    TabletDraw.num(p.getX()), TabletDraw.num(p.getY()), TabletDraw.num(p.getZ())))
                    .orElse(tr("ui.aerocouriers.create.dest.unset"));
        };
    }

    private String destSub() {
        return switch (destKind) {
            case NONE -> tr("ui.aerocouriers.create.pick.sub");
            case STATION -> tr("ui.aerocouriers.create.dest.station");
            case XAERO -> tr("ui.aerocouriers.create.dest.xaero");
            case COORDS -> tr("ui.aerocouriers.create.dest.custom");
        };
    }

    private String summaryLine() {
        Optional<BlockPos> to = dest();
        if (to.isEmpty()) {
            return tr("ui.aerocouriers.create.summary.need_dest");
        }
        double distance = distanceTo(to.get());
        long airMin = Math.max(1, Math.round(distance / AIRSHIP_BLOCKS_PER_SEC / 60.0));
        long minReward = minRewardFor(to.get());
        return tr("ui.aerocouriers.create.summary",
                TabletDraw.num(Math.round(distance)), TabletDraw.num(airMin), MoneyFormat.text(minReward));
    }

    private LicenseRank requiredRank() {
        LicenseRank auto = autoRank();
        if (rankFloorLevel > auto.level()) {
            return LicenseRank.byLevel(Math.min(5, rankFloorLevel));
        }
        return auto;
    }

    private LicenseRank autoRank() {
        Optional<BlockPos> to = dest();
        Destination origin = originDest();
        Destination destination = to.map(p -> new Destination(destinationDimension, p, zoneRadius())).orElse(origin);
        return ContractDistances.routeRequiredRank(parseLong(collateral, 0), cargoSize(), origin, destination);
    }

    private String rankLabel(LicenseRank rank) {
        String roman = BoardCardRenderer.roman(rank);
        if (rankFloorLevel > autoRank().level()) {
            return tr("ui.aerocouriers.create.rank.raised", roman);
        }
        Optional<BlockPos> to = dest();
        if (to.isEmpty()) {
            return tr("ui.aerocouriers.create.rank.pending", roman);
        }
        Destination origin = originDest();
        Destination destination = new Destination(destinationDimension, to.get(), zoneRadius());
        double distance = ContractDistances.pricingBlocks(origin, destination);
        LicenseRank byC = RankRules.requiredRank(parseLong(collateral, 0), CargoSize.S, 0);
        LicenseRank byS = RankRules.requiredRank(0, cargoSize(), 0);
        LicenseRank byD = RankRules.requiredRank(0, CargoSize.S, distance);
        if (ContractDistances.isCrossDimension(origin, destination)
                && rank.level() >= 4 && byC.level() < 4 && byS.level() < 4 && byD.level() < 4) {
            return tr("ui.aerocouriers.create.rank.by_dimension", roman);
        }
        if (byD.level() >= byC.level() && byD.level() >= byS.level()) {
            return tr("ui.aerocouriers.create.rank.by_distance", roman);
        }
        if (byC.level() >= byS.level()) {
            return tr("ui.aerocouriers.create.rank.by_collateral", roman);
        }
        return tr("ui.aerocouriers.create.rank.by_size", roman);
    }

    private Destination originDest() {
        ResourceKey<Level> dim = minecraft != null && minecraft.level != null
                ? minecraft.level.dimension() : Level.OVERWORLD;
        return new Destination(dim, preview.origin(), zoneRadius());
    }

    private CargoSize cargoSize() {
        return CargoSize.smallestFor(preview.cargo().size(), preview.weight()).orElse(CargoSize.S);
    }

    private boolean sizeBySlots(CargoSize actual) {
        Optional<CargoSize> byWeight = CargoSize.smallestFor(1, preview.weight());
        Optional<CargoSize> bySlots = CargoSize.smallestFor(preview.cargo().size(), 0);
        if (bySlots.isPresent() && byWeight.isPresent()) {
            return bySlots.get().ordinal() > byWeight.get().ordinal() && actual == bySlots.get();
        }
        return false;
    }

    private long minRewardFor(BlockPos to) {
        return Math.max(1, Pricing.minReward(distanceTo(to), preview.weight()));
    }

    private long minCollateralNow() {
        long rewardValue = parseLong(reward, dest().map(this::minRewardFor).orElse(1L));
        return Pricing.minP2pCollateral(rewardValue, preview.cargo());
    }

    private double distanceTo(BlockPos to) {
        return ContractDistances.pricingBlocks(originDest(), new Destination(destinationDimension, to, zoneRadius()));
    }

    private long chargeNow() {
        long rewardValue = parseLong(reward, 0);
        return rewardValue + Pricing.fee(rewardValue, cargoSize());
    }

    private int deadlineIndex() {
        int value = (int) parseLong(minutes, -1);
        for (int i = 0; i < DEADLINES.length; i++) {
            if (value == DEADLINES[i]) {
                return i;
            }
        }
        return 4;
    }

    private int zoneIndex() {
        int value = (int) parseLong(radius, CreateContractPayload.DEFAULT_RADIUS);
        for (int i = 0; i < ZONES.length; i++) {
            if (value == ZONES[i]) {
                return i;
            }
        }
        return 1;
    }

    private int zoneRadius() {
        return (int) parseLong(radius, CreateContractPayload.DEFAULT_RADIUS);
    }

    private Optional<BlockPos> dest() {
        return parsePos(toX, toY, toZ);
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

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String moneyUnit() {
        return MoneyFormat.unit(2).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
