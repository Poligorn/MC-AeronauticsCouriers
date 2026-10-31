package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the station-note contract visible on the tablet even when the board snapshot
 * omitted it (destination not discovered yet) or filters would hide it.
 */
public final class BoardFocus {
    private BoardFocus() {
    }

    public static List<BoardSnapshotPayload.BoardEntry> ensureVisible(
            List<BoardSnapshotPayload.BoardEntry> listed,
            @Nullable UUID id,
            @Nullable BoardSnapshotPayload.BoardEntry extra
    ) {
        if (id == null) {
            return listed;
        }
        for (BoardSnapshotPayload.BoardEntry entry : listed) {
            if (entry.id().equals(id)) {
                return listed;
            }
        }
        if (extra == null || !extra.id().equals(id)) {
            return listed;
        }
        List<BoardSnapshotPayload.BoardEntry> out = new ArrayList<>(listed.size() + 1);
        out.add(extra);
        out.addAll(listed);
        return List.copyOf(out);
    }

    public static Optional<BoardSnapshotPayload.BoardEntry> resolve(
            @Nullable BoardSnapshotPayload snapshot,
            UUID id,
            @Nullable BoardSnapshotPayload.BoardEntry extra
    ) {
        return resolve(snapshot, id, extra, List.of());
    }

    public static Optional<BoardSnapshotPayload.BoardEntry> resolve(
            @Nullable BoardSnapshotPayload snapshot,
            UUID id,
            @Nullable BoardSnapshotPayload.BoardEntry extra,
            @Nullable List<BoardSnapshotPayload.BoardEntry> extras
    ) {
        if (id == null) {
            return Optional.empty();
        }
        if (snapshot != null) {
            Optional<BoardSnapshotPayload.BoardEntry> hit = snapshot.find(id);
            if (hit.isPresent()) {
                return hit;
            }
        }
        if (extra != null && extra.id().equals(id)) {
            return Optional.of(extra);
        }
        if (extras != null) {
            for (BoardSnapshotPayload.BoardEntry entry : extras) {
                if (entry.id().equals(id)) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /** Append station-board rows the tablet snapshot omitted (destination not discovered). */
    public static List<BoardSnapshotPayload.BoardEntry> mergeMissing(
            List<BoardSnapshotPayload.BoardEntry> listed,
            @Nullable List<BoardSnapshotPayload.BoardEntry> extras
    ) {
        if (extras == null || extras.isEmpty()) {
            return listed;
        }
        HashSet<UUID> have = new HashSet<>();
        for (BoardSnapshotPayload.BoardEntry entry : listed) {
            have.add(entry.id());
        }
        List<BoardSnapshotPayload.BoardEntry> out = new ArrayList<>(listed);
        boolean added = false;
        for (BoardSnapshotPayload.BoardEntry extra : extras) {
            if (have.add(extra.id())) {
                out.add(extra);
                added = true;
            }
        }
        return added ? List.copyOf(out) : listed;
    }
}
