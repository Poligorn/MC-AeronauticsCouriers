package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.client.MoneyText;
import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Hover lines for a cork-board note. Vanilla {@code renderTooltip} paints the purple
 * fill at z=400 and submits text into {@code bufferSource} at the same z — the fill
 * wins the depth test, so the box looks empty. We blit our 9-slice at z=400 and draw
 * cream strings at z=410 after a flush.
 */
public final class CorkNoteTooltip {
    public static final int PAD_X = 6;
    public static final int PAD_Y = 5;
    public static final int LINE = 10;
    private static final int GAP = 8;
    private static final int BG_Z = 400;
    private static final int TEXT_Z = 410;

    /** Last GUI-space box, for corkqa PIL crops. */
    private static int lastX;
    private static int lastY;
    private static int lastW;
    private static int lastH;

    private CorkNoteTooltip() {
    }

    public static int lastX() {
        return lastX;
    }

    public static int lastY() {
        return lastY;
    }

    public static int lastW() {
        return lastW;
    }

    public static int lastH() {
        return lastH;
    }

    public static List<Component> lines(
            BoardSnapshotPayload.BoardEntry entry,
            CorkNoteStyle style,
            boolean readOnly
    ) {
        List<Component> out = new ArrayList<>();
        String dest = BoardCardRenderer.destinationName(entry);
        if (dest != null && !dest.isBlank()) {
            out.add(Component.translatable("ui.aerocouriers.cork.hover.dest", dest,
                    MoneyFormat.grouped(entry.distance())));
        }
        String cargo = entry.size().toUpperCase() + " · " + weight(entry);
        out.add(Component.translatable("ui.aerocouriers.cork.hover.cargo", cargo));
        out.add(Component.translatable("ui.aerocouriers.cork.hover.deadline", clock(entry)));
        if (!style.hideReward()) {
            out.add(Component.translatable("ui.aerocouriers.cork.hover.reward", MoneyFormat.text(entry.reward())));
            out.add(Component.translatable("ui.aerocouriers.cork.hover.deposit", MoneyFormat.text(entry.collateral())));
        }
        if (style.locked()) {
            out.add(Component.translatable("ui.aerocouriers.cork.hover.rank",
                    BoardCardRenderer.roman(entry.requiredRank())));
        }
        if (style.inProgress()) {
            String nick = style.courierName().isBlank() ? "—" : style.courierName();
            out.add(Component.translatable("ui.aerocouriers.cork.hover.status", nick));
        }
        out.add(Component.translatable(readOnly
                ? "ui.aerocouriers.cork.need_tablet"
                : "ui.aerocouriers.cork.hover.click"));
        out.removeIf(line -> line == null || line.getString().isBlank());
        return List.copyOf(out);
    }

    public static int[] size(Font font, List<Component> lines) {
        int textW = 0;
        for (Component line : lines) {
            String plain = line.getString();
            textW = Math.max(textW, MoneyText.iconUnits() && MoneyText.containsUnit(plain)
                    ? MoneyText.width(font, plain)
                    : font.width(plain));
        }
        return new int[] {Math.max(24, textW + PAD_X * 2), Math.max(LINE + PAD_Y * 2, lines.size() * LINE + PAD_Y * 2)};
    }

    /**
     * Sit beside the note (right, then left, then below, then above). Prefer a
     * candidate that does not overlap the note.
     */
    public static CorkBoardLayout.Rect place(
            int screenW,
            int screenH,
            CorkBoardLayout.Rect note,
            int boxW,
            int boxH
    ) {
        int[][] candidates = {
                {note.x() + note.w() + GAP, note.y()},
                {note.x() - GAP - boxW, note.y()},
                {note.x(), note.y() + note.h() + GAP},
                {note.x(), note.y() - GAP - boxH}
        };
        CorkBoardLayout.Rect fallback = clamp(candidates[0][0], candidates[0][1], boxW, boxH, screenW, screenH);
        for (int[] xy : candidates) {
            CorkBoardLayout.Rect tip = clamp(xy[0], xy[1], boxW, boxH, screenW, screenH);
            if (!overlaps(tip, note)) {
                return tip;
            }
            fallback = tip;
        }
        return fallback;
    }

    private static CorkBoardLayout.Rect clamp(int x, int y, int boxW, int boxH, int screenW, int screenH) {
        int cx = Mth.clamp(x, 4, Math.max(4, screenW - boxW - 4));
        int cy = Mth.clamp(y, 4, Math.max(4, screenH - boxH - 4));
        return new CorkBoardLayout.Rect(cx, cy, boxW, boxH);
    }

    public static boolean overlaps(CorkBoardLayout.Rect a, CorkBoardLayout.Rect b) {
        return a.x() < b.x() + b.w() && a.x() + a.w() > b.x()
                && a.y() < b.y() + b.h() && a.y() + a.h() > b.y();
    }

    /** 9-slice at z=400, then cream text at z=410 so it cannot lose the depth test. */
    public static void render(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            lastW = 0;
            lastH = 0;
            return;
        }
        int[] box = size(font, lines);
        lastX = x;
        lastY = y;
        lastW = box[0];
        lastH = box[1];
        CorkBoardGui.fullBright();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        graphics.flush();
        graphics.blitSprite(CorkSprites.TOOLTIP, x, y, BG_Z, box[0], box[1]);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TEXT_Z);
        RenderSystem.disableDepthTest();
        CorkBoardGui.fullBright();
        int ty = y + PAD_Y;
        int last = lines.size() - 1;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == last ? CorkPalette.HEADER_MUTED : CorkPalette.HEADER_TEXT;
            String plain = lines.get(i).getString();
            if (MoneyText.iconUnits() && MoneyText.containsUnit(plain)) {
                MoneyText.draw(graphics, font, plain, x + PAD_X, ty, color, MoneyText.Face.DEFAULT);
            } else {
                graphics.drawString(font, plain, x + PAD_X, ty, color, false);
            }
            ty += LINE;
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    static String clock(BoardSnapshotPayload.BoardEntry entry) {
        long minutes = Math.max(0, entry.minutesLeft());
        if (minutes >= 60) {
            return (minutes / 60) + Component.translatable("ui.aerocouriers.cork.hours").getString();
        }
        return minutes + Component.translatable("ui.aerocouriers.cork.minutes_short").getString();
    }

    private static String weight(BoardSnapshotPayload.BoardEntry entry) {
        String raw = entry.weight();
        if (raw == null || raw.isBlank()) {
            return MoneyFormat.grouped((int) Math.round(entry.weightValue()))
                    + Component.translatable("ui.aerocouriers.cork.kg").getString();
        }
        return raw.replace(" ", "");
    }
}
