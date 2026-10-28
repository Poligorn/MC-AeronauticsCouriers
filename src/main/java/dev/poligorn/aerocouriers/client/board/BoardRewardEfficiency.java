package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;

/** Reward per 1000 blocks (sp/1000bl) for sorting and row display. */
public final class BoardRewardEfficiency {
    private BoardRewardEfficiency() {
    }

    public static long perThousandBlocks(BoardSnapshotPayload.BoardEntry entry) {
        int distance = Math.max(1, entry.distance());
        return entry.reward() * 1000L / distance;
    }
}
