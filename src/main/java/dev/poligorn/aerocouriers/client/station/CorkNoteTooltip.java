package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.client.board.BoardCardRenderer;
import dev.poligorn.aerocouriers.economy.MoneyFormat;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Hover lines for a cork-board note: fields that the 136×108 sheet cannot hold in full,
 * plus the click / tablet hint. Empty lines are never returned.
 */
public final class CorkNoteTooltip {
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
