package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.station.CorkHudGuard;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.config.NoticeChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Own 190×30 toast stack (vanilla {@code ToastComponent} is 160 wide and has no priorities). Drawn on the HUD and on
 * top of the tablet; hidden with F1, F3, chat and pause, where incoming toasts only add to one summary.
 */
public final class ClientToasts {
    public static final int WIDTH = 190;
    public static final int HEIGHT = 30;
    public static final int GAP = 2;
    public static final int MARGIN = 4;
    private static final long COLLAPSE_MS = 10_000L;
    private static final long SLIDE_MS = 200L;

    public static final int GOLD = 0xFFE0A63A;
    public static final int BLUE = 0xFF5AB4E8;
    public static final int TEAL = 0xFF2EC4B6;
    public static final int CORAL = 0xFFFF6F61;
    public static final int GREY = 0xFF9AA0A6;

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final Deque<Entry> QUEUE = new ArrayDeque<>();
    private static int hiddenMissed;
    private static boolean wasHidden;

    private ClientToasts() {
    }

    private static final class Entry {
        final NoticeChannel.Row row;
        final Component text;
        final String plain;
        int count = 1;
        long shownAt = -1;
        long lastAt;

        Entry(NoticeChannel.Row row, Component text, long now) {
            this.row = row;
            this.text = text;
            this.plain = text.getString();
            this.lastAt = now;
        }

        boolean urgent() {
            return row == NoticeChannel.Row.CRITICAL;
        }

        long duration() {
            long base = CouriersClientConfig.get(CouriersClientConfig.TOAST_SECONDS, 5) * 1000L;
            return urgent() ? base * 2 : base;
        }
    }

    public static void push(NoticeChannel.Row row, Component text) {
        long now = Util.getMillis();
        if (hidden()) {
            hiddenMissed++;
            return;
        }
        for (Entry e : ACTIVE) {
            if (e.row == row && e.plain.equals(text.getString()) && now - e.lastAt <= COLLAPSE_MS) {
                e.count++;
                e.lastAt = now;
                e.shownAt = now - SLIDE_MS;
                return;
            }
        }
        for (Entry e : QUEUE) {
            if (e.row == row && e.plain.equals(text.getString()) && now - e.lastAt <= COLLAPSE_MS) {
                e.count++;
                e.lastAt = now;
                return;
            }
        }
        Entry entry = new Entry(row, text, now);
        if (entry.urgent()) {
            if (ACTIVE.size() >= maxVisible()) {
                for (int i = 0; i < ACTIVE.size(); i++) {
                    if (!ACTIVE.get(i).urgent()) {
                        Entry evicted = ACTIVE.remove(i);
                        evicted.shownAt = -1;
                        QUEUE.addFirst(evicted);
                        break;
                    }
                }
            }
            if (ACTIVE.size() < maxVisible()) {
                entry.shownAt = now;
                ACTIVE.add(0, entry);
            } else {
                QUEUE.addFirst(entry);
            }
            return;
        }
        QUEUE.addLast(entry);
        promote(now);
    }

    public static void clear() {
        ACTIVE.clear();
        QUEUE.clear();
        hiddenMissed = 0;
    }

    private static int maxVisible() {
        return Mth.clamp(CouriersClientConfig.get(CouriersClientConfig.TOAST_MAX_VISIBLE, 3), 1, 5);
    }

    /** Height of the reserved toast zone; the task panel starts below it. */
    public static int zoneHeight() {
        return MARGIN + maxVisible() * (HEIGHT + GAP) + 10;
    }

    private static void promote(long now) {
        while (ACTIVE.size() < maxVisible() && !QUEUE.isEmpty()) {
            Entry next = QUEUE.pollFirst();
            next.shownAt = now;
            ACTIVE.add(next);
        }
    }

    public static boolean hidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof ChatScreen
                || mc.isPaused() || mc.player == null || CorkHudGuard.stationUiOpen();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            render(event.getGuiGraphics());
        }
    }

    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof CourierBoardScreen) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0, 0, 400);
            render(event.getGuiGraphics());
            event.getGuiGraphics().pose().popPose();
        }
    }

    private static void render(GuiGraphics graphics) {
        boolean hide = hidden();
        if (hide) {
            wasHidden = true;
            return;
        }
        long now = Util.getMillis();
        if (wasHidden) {
            wasHidden = false;
            if (hiddenMissed > 0) {
                int missed = hiddenMissed;
                hiddenMissed = 0;
                push(NoticeChannel.Row.ORDERS, Component.translatable("ui.aerocouriers.toast.missed", missed));
            }
        }
        for (Iterator<Entry> it = ACTIVE.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (now - e.shownAt > e.duration()) {
                it.remove();
            }
        }
        promote(now);
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        CouriersClientConfig.ToastCorner corner =
                CouriersClientConfig.get(CouriersClientConfig.TOAST_CORNER, CouriersClientConfig.ToastCorner.TOP_RIGHT);
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int baseX = corner.right() ? screenW - WIDTH - MARGIN : MARGIN;
        for (int i = 0; i < ACTIVE.size(); i++) {
            Entry e = ACTIVE.get(i);
            float slide = Mth.clamp((now - e.shownAt) / (float) SLIDE_MS, 0F, 1F);
            int offset = Math.round((1F - slide) * (WIDTH + MARGIN));
            int x = corner.right() ? baseX + offset : baseX - offset;
            int y = corner.top() ? MARGIN + i * (HEIGHT + GAP) : screenH - MARGIN - (i + 1) * (HEIGHT + GAP);
            float left = 1F - Mth.clamp((now - e.shownAt) / (float) e.duration(), 0F, 1F);
            drawToast(graphics, font, e, x, y, left);
        }
        if (!QUEUE.isEmpty()) {
            Component chip = Component.translatable("ui.aerocouriers.toast.queued", QUEUE.size());
            int w = font.width(chip) + 8;
            int x = corner.right() ? screenW - MARGIN - w : MARGIN;
            int y = corner.top() ? MARGIN + ACTIVE.size() * (HEIGHT + GAP)
                    : screenH - MARGIN - ACTIVE.size() * (HEIGHT + GAP) - 11;
            graphics.fill(x, y, x + w, y + 10, 0xC0101418);
            graphics.drawString(font, chip, x + 4, y + 1, 0xFFB8C0C8, false);
        }
    }

    private static void drawToast(GuiGraphics graphics, Font font, Entry e, int x, int y, float timeLeft) {
        int accent = color(e.row);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, e.urgent() ? 0xE8301A18 : 0xE8141A20);
        graphics.fill(x, y, x + 3, y + HEIGHT, accent);
        graphics.fill(x + 6, y + 5, x + 26, y + 25, (accent & 0x00FFFFFF) | 0x30000000);
        drawIcon(graphics, e.row, x + 8, y + 7);
        Component title = Component.translatable("ui.aerocouriers.notify.row." + e.row.key());
        if (e.count > 1) {
            title = title.copy().append(" ×" + e.count);
        }
        graphics.drawString(font, title, x + 30, y + 4, accent, false);
        List<FormattedCharSequence> lines = font.split(e.text, WIDTH - 34);
        if (!lines.isEmpty()) {
            FormattedCharSequence line = lines.getFirst();
            graphics.drawString(font, line, x + 30, y + 15, 0xFFE6E8EA, false);
            if (lines.size() > 1) {
                graphics.drawString(font, "…", x + WIDTH - 8, y + 15, 0xFFE6E8EA, false);
            }
        }
        int barW = Math.round((WIDTH - 3) * timeLeft);
        graphics.fill(x + 3, y + HEIGHT - 1, x + 3 + barW, y + HEIGHT, accent);
    }

    public static int color(NoticeChannel.Row row) {
        return switch (row) {
            case CRITICAL -> CORAL;
            case ORDERS, MONEY -> GOLD;
            case CARGO, ADMIN -> BLUE;
            case TUTORIAL -> TEAL;
            case BOARD -> GREY;
        };
    }

    /** 16×16 category icon; money uses the active currency icon (Numismatics spur or vanilla emerald). */
    public static void drawIcon(GuiGraphics graphics, NoticeChannel.Row row, int x, int y) {
        switch (row) {
            case MONEY -> SpurCoinGraphics.drawGenericCoin(graphics, x, y, 16);
            case CRITICAL -> graphics.renderFakeItem(new ItemStack(Items.CLOCK), x, y);
            case CARGO -> graphics.renderFakeItem(new ItemStack(Items.BARREL), x, y);
            case TUTORIAL -> graphics.renderFakeItem(new ItemStack(Items.BOOK), x, y);
            case BOARD -> graphics.renderFakeItem(new ItemStack(Items.PAPER), x, y);
            case ADMIN -> graphics.renderFakeItem(new ItemStack(Items.COMMAND_BLOCK), x, y);
            case ORDERS -> graphics.renderFakeItem(
                    new ItemStack(dev.poligorn.aerocouriers.registry.ModItems.ORDER_TABLET.get()), x, y);
        }
    }
}
