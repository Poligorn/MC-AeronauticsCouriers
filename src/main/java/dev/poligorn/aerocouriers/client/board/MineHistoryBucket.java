package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;

/** History status chips on «Мои · История». */
public enum MineHistoryBucket {
    DELIVERED,
    FAILED,
    REVOKED,
    RETURN,
    EXPIRED;

    public static MineHistoryBucket classify(BoardSnapshotPayload.BoardEntry entry) {
        return switch (entry.state()) {
            case DELIVERED -> DELIVERED;
            case FAILED -> "issuer".equals(entry.role()) ? RETURN : FAILED;
            case EXPIRED -> "issuer".equals(entry.role()) ? REVOKED : EXPIRED;
            default -> EXPIRED;
        };
    }

    public boolean enabled(MineTabFilterState state) {
        return switch (this) {
            case DELIVERED -> state.statusDelivered();
            case FAILED -> state.statusFailed();
            case REVOKED -> state.statusRevoked();
            case RETURN -> state.statusReturn();
            case EXPIRED -> state.statusExpired();
        };
    }
}
