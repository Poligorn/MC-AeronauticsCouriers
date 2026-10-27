package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side sort fields for board / mine lists. */
public enum BoardSortKey {
    NEWEST,
    REWARD,
    DISTANCE,
    DEADLINE,
    COLLATERAL;

    public static Comparator<BoardSnapshotPayload.BoardEntry> comparator(
            BoardSortKey key, Map<UUID, Integer> sourceIndex) {
        return switch (key) {
            case NEWEST -> Comparator.comparingInt(e -> sourceIndex.getOrDefault(e.id(), 0));
            case REWARD -> Comparator.comparingLong(BoardSnapshotPayload.BoardEntry::reward);
            case DISTANCE -> Comparator.comparingInt(BoardSnapshotPayload.BoardEntry::distance);
            case DEADLINE -> Comparator.comparingLong(BoardSnapshotPayload.BoardEntry::minutesLeft);
            case COLLATERAL -> Comparator.comparingLong(BoardSnapshotPayload.BoardEntry::collateral);
        };
    }

    public static Map<UUID, Integer> indexMap(List<BoardSnapshotPayload.BoardEntry> source) {
        Map<UUID, Integer> map = new HashMap<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            map.put(source.get(i).id(), i);
        }
        return map;
    }

    public String translationKey() {
        return "ui.aerocouriers.filters.sort." + name().toLowerCase();
    }
}
