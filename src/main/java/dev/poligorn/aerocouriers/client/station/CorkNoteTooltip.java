package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * Hover lines for a cork-board note. Drawn with the 9-slice tooltip sprite and header
 * palette — vanilla {@code renderTooltip} puts the purple fill at z=400 and left the
 * text underneath, so the box looked empty.
 */
public final class CorkNoteTooltip {
    public static final int PAD_X = 6;
    public static final int PAD_Y = 5;
    public static final int LINE = 10;
    private static final int GAP = 8;

    private CorkNoteTooltip() {
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
            textW = Math.max(textW, font.width(line));
        }
        return new int[] {textW + PAD_X * 2, lines.size() * LINE + PAD_Y * 2};
    }

    /**
     * Sit beside the note (right, then left, then below/above). Never overlap the note
     * when the screen is large enough for both.
     */
    public static CorkBoardLayout.Rect place(
            int screenW,
            int screenH,
            CorkBoardLayout.Rect note,
            int boxW,
            int boxH
    ) {
        int x = note.x() + note.w() + GAP;
        int y = note.y();
        if (x + boxW > screenW - 4) {
            x = note.x() - GAP - boxW;
        }
        if (x < 4) {
            x = Mth.clamp(note.x(), 4, Math.max(4, screenW - boxW - 4));
            y = note.y() + note.h() + GAP;
            if (y + boxH > screenH - 4) {
                y = note.y() - GAP - boxH;
            }
        }
        x = Mth.clamp(x, 4, Math.max(4, screenW - boxW - 4));
        y = Mth.clamp(y, 4, Math.max(4, screenH - boxH - 4));
        return new CorkBoardLayout.Rect(x, y, boxW, boxH);
    }

    public static boolean overlaps(CorkBoardLayout.Rect a, CorkBoardLayout.Rect b) {
        return a.x() < b.x() + b.w() && a.x() + a.w() > b.x()
                && a.y() < b.y() + b.h() && a.y() + a.h() > b.y();
    }

    /** Background first at z=400, then cream text on top. */
    public static void render(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int[] box = size(font, lines);
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        CorkBoardGui.fullBright();
        CorkBoardGui.blit(graphics, CorkSprites.TOOLTIP, x, y, box[0], box[1]);
        int ty = y + PAD_Y;
        int last = lines.size() - 1;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == last ? CorkPalette.HEADER_MUTED : CorkPalette.HEADER_TEXT;
            CorkBoardGui.text(graphics, font, lines.get(i), x + PAD_X, ty, color);
            ty += LINE;
        }
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
