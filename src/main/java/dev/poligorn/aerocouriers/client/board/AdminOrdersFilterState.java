package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Client filters for admin «Заказы». */
public final class AdminOrdersFilterState {
    private boolean statusOpen = true;
    private boolean statusInTransit = true;
    private EnumSet<CargoSize> sizes = EnumSet.allOf(CargoSize.class);
    private AdminContractKind contractKind = AdminContractKind.ALL;
    private BoardSortKey sortKey = BoardSortKey.REWARD;
    private boolean sortDescending = true;

    /** Identity of the last {@code source} list; not {@link List#equals}. */
    private List<BoardSnapshotPayload.AdminActiveEntry> cachedSource;
    private boolean cachedOpen;
    private boolean cachedInTransit;
    private EnumSet<CargoSize> cachedSizes;
    private AdminContractKind cachedKind;
    private BoardSortKey cachedSort;
    private boolean cachedDesc;
    private List<BoardSnapshotPayload.AdminActiveEntry> cachedOut = List.of();

    public boolean statusOpen() {
        return statusOpen;
    }

    public boolean statusInTransit() {
        return statusInTransit;
    }

    public void toggleStatusOpen() {
        if (!statusOpen || statusInTransit) {
            statusOpen = !statusOpen;
        }
    }

    public void toggleStatusInTransit() {
        if (!statusInTransit || statusOpen) {
            statusInTransit = !statusInTransit;
        }
    }

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

    public AdminContractKind contractKind() {
        return contractKind;
    }

    public void cycleContractKind() {
        AdminContractKind[] values = AdminContractKind.values();
        contractKind = values[(contractKind.ordinal() + 1) % values.length];
    }

    public BoardSortKey sortKey() {
        return sortKey;
    }

    public boolean sortDescending() {
        return sortDescending;
    }

    public void cycleSortKey() {
        BoardSortKey[] values = {BoardSortKey.REWARD, BoardSortKey.DISTANCE, BoardSortKey.DEADLINE,
                BoardSortKey.COLLATERAL, BoardSortKey.NEWEST};
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == sortKey) {
                idx = i;
                break;
            }
        }
        sortKey = values[(idx + 1) % values.length];
        sortDescending = sortKey != BoardSortKey.DISTANCE && sortKey != BoardSortKey.DEADLINE;
    }

    public void toggleSortDirection() {
        sortDescending = !sortDescending;
    }

    public boolean isDefault() {
        return statusOpen
                && statusInTransit
                && sizes.size() == CargoSize.values().length
                && contractKind == AdminContractKind.ALL
                && sortKey == BoardSortKey.REWARD
                && sortDescending;
    }

    public void reset() {
        statusOpen = true;
        statusInTransit = true;
        sizes = EnumSet.allOf(CargoSize.class);
        contractKind = AdminContractKind.ALL;
        sortKey = BoardSortKey.REWARD;
        sortDescending = true;
    }

    public List<BoardSnapshotPayload.AdminActiveEntry> apply(List<BoardSnapshotPayload.AdminActiveEntry> source) {
        if (source == cachedSource
                && statusOpen == cachedOpen
                && statusInTransit == cachedInTransit
                && contractKind == cachedKind
                && sortKey == cachedSort
                && sortDescending == cachedDesc
                && cachedSizes != null
                && sizes.equals(cachedSizes)) {
            return cachedOut;
        }
        List<BoardSnapshotPayload.AdminActiveEntry> out = new ArrayList<>();
        for (BoardSnapshotPayload.AdminActiveEntry entry : source) {
            if (matches(entry)) {
                out.add(entry);
            }
        }
        Map<UUID, Integer> index = new HashMap<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            index.put(source.get(i).id(), i);
        }
        Comparator<BoardSnapshotPayload.AdminActiveEntry> cmp = comparator(sortKey, index);
        out.sort(sortDescending ? cmp.reversed() : cmp);
        cachedSource = source;
        cachedOpen = statusOpen;
        cachedInTransit = statusInTransit;
        cachedKind = contractKind;
        cachedSort = sortKey;
        cachedDesc = sortDescending;
        cachedSizes = EnumSet.copyOf(sizes);
        cachedOut = out;
        return out;
    }

    private boolean matches(BoardSnapshotPayload.AdminActiveEntry entry) {
        if (entry.state() == ContractState.OPEN) {
            if (!statusOpen) {
                return false;
            }
        } else if (entry.state() == ContractState.ACCEPTED || entry.state() == ContractState.IN_TRANSIT) {
            if (!statusInTransit) {
                return false;
            }
        }
        CargoSize size = CargoSize.valueOf(entry.size().toUpperCase(Locale.ROOT));
        if (!sizes.contains(size)) {
            return false;
        }
        return contractKind.matches(entry.contractType());
    }

    private static Comparator<BoardSnapshotPayload.AdminActiveEntry> comparator(
            BoardSortKey key, Map<UUID, Integer> sourceIndex) {
        return switch (key) {
            case NEWEST -> Comparator.comparingInt(e -> sourceIndex.getOrDefault(e.id(), 0));
            case REWARD -> Comparator.comparingLong(BoardSnapshotPayload.AdminActiveEntry::reward);
            case DISTANCE -> Comparator.comparingInt(BoardSnapshotPayload.AdminActiveEntry::distance);
            case DEADLINE -> Comparator.comparingLong(BoardSnapshotPayload.AdminActiveEntry::minutesLeft);
            case COLLATERAL -> Comparator.comparingLong(BoardSnapshotPayload.AdminActiveEntry::collateral);
        };
    }
}
