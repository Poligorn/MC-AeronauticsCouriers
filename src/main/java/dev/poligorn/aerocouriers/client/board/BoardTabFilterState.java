package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Remembered filters for the «Заказы» tab. */
public final class BoardTabFilterState {
    private EnumSet<CargoSize> sizes = EnumSet.allOf(CargoSize.class);
    private boolean onlyAvailable;
    private BoardContractKind contractKind = BoardContractKind.ALL;
    private BoardSortKey sortKey = BoardSortKey.REWARD;
    private boolean sortDescending = true;

    /** Identity of the last {@code source} list; not {@link List#equals}, which deep-compares BoardEntry records. */
    private List<BoardSnapshotPayload.BoardEntry> cachedSource;
    private BoardSnapshotPayload.BoardProfile cachedProfile;
    private int cachedHauls = Integer.MIN_VALUE;
    private boolean cachedOnlyAvailable;
    private BoardContractKind cachedKind;
    private BoardSortKey cachedSort;
    private boolean cachedDesc;
    private EnumSet<CargoSize> cachedSizes;
    private List<BoardSnapshotPayload.BoardEntry> cachedOut = List.of();

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

    public boolean onlyAvailable() {
        return onlyAvailable;
    }

    public void setOnlyAvailable(boolean onlyAvailable) {
        this.onlyAvailable = onlyAvailable;
    }

    public void toggleOnlyAvailable() {
        onlyAvailable = !onlyAvailable;
    }

    public BoardContractKind contractKind() {
        return contractKind;
    }

    public void cycleContractKind() {
        BoardContractKind[] values = BoardContractKind.values();
        contractKind = values[(contractKind.ordinal() + 1) % values.length];
    }

    public void setContractKind(BoardContractKind kind) {
        contractKind = kind == null ? BoardContractKind.ALL : kind;
    }

    public BoardSortKey sortKey() {
        return CollateralUi.visibleSortKey(sortKey);
    }

    public boolean sortDescending() {
        return sortDescending;
    }

    public void cycleSortKey() {
        BoardSortKey[] values = CollateralUi.playerSortKeys(BoardSortKey.REWARD, BoardSortKey.DISTANCE,
                BoardSortKey.DEADLINE, BoardSortKey.COLLATERAL);
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == sortKey()) {
                idx = i;
                break;
            }
        }
        sortKey = values[(idx + 1) % values.length];
        sortDescending = defaultDescending(sortKey);
    }

    public void toggleSortDirection() {
        sortDescending = !sortDescending;
    }

    public boolean isDefault() {
        return sizes.size() == CargoSize.values().length
                && !onlyAvailable
                && contractKind == BoardContractKind.ALL
                && sortKey == BoardSortKey.REWARD
                && sortDescending;
    }

    public void reset() {
        sizes = EnumSet.allOf(CargoSize.class);
        onlyAvailable = false;
        contractKind = BoardContractKind.ALL;
        sortKey = BoardSortKey.REWARD;
        sortDescending = true;
    }

    public List<BoardSnapshotPayload.BoardEntry> apply(
            List<BoardSnapshotPayload.BoardEntry> source,
            BoardSnapshotPayload.BoardProfile profile,
            int activeHauls) {
        BoardSortKey sort = sortKey();
        if (source == cachedSource
                && profile == cachedProfile
                && activeHauls == cachedHauls
                && onlyAvailable == cachedOnlyAvailable
                && contractKind == cachedKind
                && sort == cachedSort
                && sortDescending == cachedDesc
                && cachedSizes != null
                && sizes.equals(cachedSizes)) {
            return cachedOut;
        }
        List<BoardSnapshotPayload.BoardEntry> out = new ArrayList<>();
        for (BoardSnapshotPayload.BoardEntry entry : source) {
            if (!matches(entry, profile, activeHauls)) {
                continue;
            }
            out.add(entry);
        }
        Map<java.util.UUID, Integer> index = BoardSortKey.indexMap(source);
        Comparator<BoardSnapshotPayload.BoardEntry> cmp =
                BoardSortKey.comparator(sort, index);
        out.sort(sortDescending ? cmp.reversed() : cmp);
        cachedSource = source;
        cachedProfile = profile;
        cachedHauls = activeHauls;
        cachedOnlyAvailable = onlyAvailable;
        cachedKind = contractKind;
        cachedSort = sort;
        cachedDesc = sortDescending;
        cachedSizes = EnumSet.copyOf(sizes);
        cachedOut = out;
        return out;
    }

    private boolean matches(
            BoardSnapshotPayload.BoardEntry entry,
            BoardSnapshotPayload.BoardProfile profile,
            int activeHauls) {
        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        if (!sizes.contains(size)) {
            return false;
        }
        if (!contractKind.matches(entry)) {
            return false;
        }
        if (onlyAvailable) {
            if (BoardAcceptUi.rowRankLocked(entry, profile.rank())) {
                return false;
            }
            if (BoardAcceptUi.denial(entry, profile, activeHauls).isPresent()) {
                return false;
            }
        }
        return true;
    }

    private static boolean defaultDescending(BoardSortKey key) {
        return key != BoardSortKey.DISTANCE && key != BoardSortKey.DEADLINE;
    }
}
