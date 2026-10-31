package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.board.BoardSortKey;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Size / rarity / reward filters for the cork board (same keys as the tablet board tab). */
public final class CorkBoardFilters {
    private final EnumSet<CargoSize> sizes = EnumSet.allOf(CargoSize.class);
    private boolean rareOnly;
    private BoardSortKey sortKey = BoardSortKey.REWARD;
    private boolean sortDescending = true;

    public EnumSet<CargoSize> sizes() {
        return EnumSet.copyOf(sizes);
    }

    public void toggleSize(CargoSize size) {
        if (sizes.contains(size)) {
            if (sizes.size() > 1) {
                sizes.remove(size);
            }
        } else {
            sizes.add(size);
        }
    }

    public boolean allSizes() {
        return sizes.size() == CargoSize.values().length;
    }

    public boolean rareOnly() {
        return rareOnly;
    }

    public void cycleRarity() {
        rareOnly = !rareOnly;
    }

    public BoardSortKey sortKey() {
        return sortKey;
    }

    public boolean sortDescending() {
        return sortDescending;
    }

    public void cycleSort() {
        BoardSortKey[] keys = {BoardSortKey.REWARD, BoardSortKey.DISTANCE, BoardSortKey.DEADLINE};
        int idx = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == sortKey) {
                idx = i;
                break;
            }
        }
        sortKey = keys[(idx + 1) % keys.length];
        sortDescending = sortKey == BoardSortKey.REWARD;
    }

    public void toggleSortDirection() {
        sortDescending = !sortDescending;
    }

    public Component sizeLabel() {
        if (allSizes()) {
            return Component.translatable("ui.aerocouriers.cork.filter.size_all");
        }
        StringBuilder out = new StringBuilder();
        for (CargoSize size : CargoSize.values()) {
            if (sizes.contains(size)) {
                if (!out.isEmpty()) {
                    out.append('/');
                }
                out.append(size.name());
            }
        }
        return Component.literal(out.toString());
    }

    public Component rarityLabel() {
        return Component.translatable(rareOnly
                ? "ui.aerocouriers.cork.filter.rarity_rare"
                : "ui.aerocouriers.cork.filter.rarity_all");
    }

    public Component sortLabel() {
        return Component.translatable("ui.aerocouriers.filters.sort." + sortKey.name().toLowerCase(Locale.ROOT));
    }

    public List<BoardSnapshotPayload.BoardEntry> apply(List<BoardSnapshotPayload.BoardEntry> source) {
        List<BoardSnapshotPayload.BoardEntry> out = new ArrayList<>();
        for (BoardSnapshotPayload.BoardEntry entry : source) {
            CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
            if (!sizes.contains(size)) {
                continue;
            }
            if (rareOnly && !entry.villageRare()) {
                continue;
            }
            out.add(entry);
        }
        Map<java.util.UUID, Integer> index = BoardSortKey.indexMap(source);
        Comparator<BoardSnapshotPayload.BoardEntry> cmp = BoardSortKey.comparator(sortKey, index);
        out.sort(sortDescending ? cmp.reversed() : cmp);
        return out;
    }
}
