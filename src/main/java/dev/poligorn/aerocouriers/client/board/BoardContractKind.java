package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;

/** Board tab «Тип» filter (client-only). */
public enum BoardContractKind {
    ALL,
    VILLAGE,
    PLAYER,
    RARE;

    public boolean matches(BoardSnapshotPayload.BoardEntry entry) {
        return switch (this) {
            case ALL -> true;
            case VILLAGE -> entry.villageCustomer() && !entry.villageRare();
            case PLAYER -> !entry.villageCustomer();
            case RARE -> entry.villageRare();
        };
    }
}
