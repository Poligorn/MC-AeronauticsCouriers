package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Remembered filters for «Мои» (separate state for Текущие / История). */
public final class MineTabFilterState {
    private boolean statusOpen = true;
    private boolean statusInTransit = true;
    private boolean statusDelivered = true;
    private boolean statusFailed = true;
    private boolean statusRevoked = true;
    private boolean statusReturn = true;
    private boolean statusExpired = true;
    private boolean roleCourier = true;
    private boolean roleCustomer = true;
    private BoardSortKey sortKey = BoardSortKey.NEWEST;
    private boolean sortDescending = true;

    /** Identity of the last section list; not {@link List#equals} (deep BoardEntry compare). */
    private List<BoardSnapshotPayload.BoardEntry> cachedSource;
    private MineListSection cachedSection;
    private boolean cachedOpen;
    private boolean cachedInTransit;
    private boolean cachedDelivered;
    private boolean cachedFailed;
    private boolean cachedRevoked;
    private boolean cachedReturn;
    private boolean cachedExpired;
    private boolean cachedCourier;
    private boolean cachedCustomer;
    private BoardSortKey cachedSort;
    private boolean cachedDesc;
    private List<BoardSnapshotPayload.BoardEntry> cachedOut = List.of();

    public static MineTabFilterState defaults(MineListSection section) {
        MineTabFilterState state = new MineTabFilterState();
        if (section == MineListSection.HISTORY) {
            state.sortKey = BoardSortKey.NEWEST;
            state.sortDescending = true;
        } else {
            state.sortKey = BoardSortKey.DEADLINE;
            state.sortDescending = false;
        }
        return state;
    }

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

    public boolean statusDelivered() {
        return statusDelivered;
    }

    public boolean statusFailed() {
        return statusFailed;
    }

    public boolean statusRevoked() {
        return statusRevoked;
    }

    public boolean statusReturn() {
        return statusReturn;
    }

    public boolean statusExpired() {
        return statusExpired;
    }

    public void toggleHistoryStatus(MineHistoryBucket bucket) {
        switch (bucket) {
            case DELIVERED -> statusDelivered = flipAtLeastOne(statusDelivered, historyEnabledCount());
            case FAILED -> statusFailed = flipAtLeastOne(statusFailed, historyEnabledCount());
            case REVOKED -> statusRevoked = flipAtLeastOne(statusRevoked, historyEnabledCount());
            case RETURN -> statusReturn = flipAtLeastOne(statusReturn, historyEnabledCount());
            case EXPIRED -> statusExpired = flipAtLeastOne(statusExpired, historyEnabledCount());
        }
    }

    private int historyEnabledCount() {
        int n = 0;
        if (statusDelivered) n++;
        if (statusFailed) n++;
        if (statusRevoked) n++;
        if (statusReturn) n++;
        if (statusExpired) n++;
        return n;
    }

    private static boolean flipAtLeastOne(boolean current, int enabledCount) {
        if (current && enabledCount <= 1) {
            return true;
        }
        return !current;
    }

    public boolean roleCourier() {
        return roleCourier;
    }

    public boolean roleCustomer() {
        return roleCustomer;
    }

    public void toggleRoleCourier() {
        if (!roleCourier || roleCustomer) {
            roleCourier = !roleCourier;
        }
    }

    public void toggleRoleCustomer() {
        if (!roleCustomer || roleCourier) {
            roleCustomer = !roleCustomer;
        }
    }

    public BoardSortKey sortKey() {
        return CollateralUi.visibleSortKey(sortKey);
    }

    public boolean sortDescending() {
        return sortDescending;
    }

    public void cycleSortKey(MineListSection section) {
        BoardSortKey[] values = section == MineListSection.HISTORY
                ? CollateralUi.playerSortKeys(BoardSortKey.NEWEST, BoardSortKey.REWARD, BoardSortKey.DISTANCE,
                BoardSortKey.DEADLINE, BoardSortKey.COLLATERAL)
                : CollateralUi.playerSortKeys(BoardSortKey.DEADLINE, BoardSortKey.NEWEST, BoardSortKey.REWARD,
                BoardSortKey.DISTANCE, BoardSortKey.COLLATERAL);
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == sortKey()) {
                idx = i;
                break;
            }
        }
        sortKey = values[(idx + 1) % values.length];
        sortDescending = defaultDescending(sortKey, section);
    }

    public void toggleSortDirection() {
        sortDescending = !sortDescending;
    }

    public boolean isDefault(MineListSection section) {
        MineTabFilterState defaults = defaults(section);
        return statusOpen == defaults.statusOpen
                && statusInTransit == defaults.statusInTransit
                && statusDelivered == defaults.statusDelivered
                && statusFailed == defaults.statusFailed
                && statusRevoked == defaults.statusRevoked
                && statusReturn == defaults.statusReturn
                && statusExpired == defaults.statusExpired
                && roleCourier == defaults.roleCourier
                && roleCustomer == defaults.roleCustomer
                && sortKey == defaults.sortKey
                && sortDescending == defaults.sortDescending;
    }

    public void reset(MineListSection section) {
        MineTabFilterState defaults = defaults(section);
        statusOpen = defaults.statusOpen;
        statusInTransit = defaults.statusInTransit;
        statusDelivered = defaults.statusDelivered;
        statusFailed = defaults.statusFailed;
        statusRevoked = defaults.statusRevoked;
        statusReturn = defaults.statusReturn;
        statusExpired = defaults.statusExpired;
        roleCourier = defaults.roleCourier;
        roleCustomer = defaults.roleCustomer;
        sortKey = defaults.sortKey;
        sortDescending = defaults.sortDescending;
    }

    public List<BoardSnapshotPayload.BoardEntry> apply(
            List<BoardSnapshotPayload.BoardEntry> sectionEntries, MineListSection section) {
        BoardSortKey sort = sortKey();
        if (sectionEntries == cachedSource
                && section == cachedSection
                && statusOpen == cachedOpen
                && statusInTransit == cachedInTransit
                && statusDelivered == cachedDelivered
                && statusFailed == cachedFailed
                && statusRevoked == cachedRevoked
                && statusReturn == cachedReturn
                && statusExpired == cachedExpired
                && roleCourier == cachedCourier
                && roleCustomer == cachedCustomer
                && sort == cachedSort
                && sortDescending == cachedDesc) {
            return cachedOut;
        }
        List<BoardSnapshotPayload.BoardEntry> out = new ArrayList<>();
        for (BoardSnapshotPayload.BoardEntry entry : sectionEntries) {
            if (!matches(entry, section)) {
                continue;
            }
            out.add(entry);
        }
        Map<java.util.UUID, Integer> index = BoardSortKey.indexMap(sectionEntries);
        Comparator<BoardSnapshotPayload.BoardEntry> cmp = BoardSortKey.comparator(sort, index);
        out.sort(sortDescending ? cmp.reversed() : cmp);
        cachedSource = sectionEntries;
        cachedSection = section;
        cachedOpen = statusOpen;
        cachedInTransit = statusInTransit;
        cachedDelivered = statusDelivered;
        cachedFailed = statusFailed;
        cachedRevoked = statusRevoked;
        cachedReturn = statusReturn;
        cachedExpired = statusExpired;
        cachedCourier = roleCourier;
        cachedCustomer = roleCustomer;
        cachedSort = sort;
        cachedDesc = sortDescending;
        cachedOut = out;
        return out;
    }

    private boolean matches(BoardSnapshotPayload.BoardEntry entry, MineListSection section) {
        if (!matchesRole(entry)) {
            return false;
        }
        if (section == MineListSection.CURRENT) {
            if (entry.state() == ContractState.OPEN) {
                return statusOpen;
            }
            if (entry.state().isActive()) {
                return statusInTransit;
            }
            return false;
        }
        MineHistoryBucket bucket = MineHistoryBucket.classify(entry);
        return bucket.enabled(this);
    }

    private boolean matchesRole(BoardSnapshotPayload.BoardEntry entry) {
        boolean courier = "courier".equals(entry.role());
        boolean customer = "issuer".equals(entry.role());
        return (courier && roleCourier) || (customer && roleCustomer);
    }

    private static boolean defaultDescending(BoardSortKey key, MineListSection section) {
        if (key == BoardSortKey.NEWEST) {
            return true;
        }
        if (key == BoardSortKey.DEADLINE) {
            return false;
        }
        return key != BoardSortKey.DISTANCE;
    }
}
