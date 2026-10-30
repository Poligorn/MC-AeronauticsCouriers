package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** «Мои» sub-list: active orders vs finished history. */
public enum MineListSection {
    CURRENT,
    HISTORY;

    public static MineListSection forState(ContractState state) {
        return state.isFinal() ? HISTORY : CURRENT;
    }

    public static boolean matches(BoardSnapshotPayload.BoardEntry entry, MineListSection section) {
        return section == CURRENT ? isCurrent(entry) : isHistory(entry);
    }

    public static boolean isCurrent(BoardSnapshotPayload.BoardEntry entry) {
        ContractState state = entry.state();
        return state == ContractState.OPEN || state.isActive();
    }

    public static boolean isHistory(BoardSnapshotPayload.BoardEntry entry) {
        return entry.state().isFinal();
    }

    private static List<BoardSnapshotPayload.BoardEntry> currentSource;
    private static List<BoardSnapshotPayload.BoardEntry> currentOut = List.of();
    private static List<BoardSnapshotPayload.BoardEntry> historySource;
    private static List<BoardSnapshotPayload.BoardEntry> historyOut = List.of();

    public static List<BoardSnapshotPayload.BoardEntry> entries(
            List<BoardSnapshotPayload.BoardEntry> mine, MineListSection section) {
        if (section == CURRENT) {
            if (mine == currentSource) {
                return currentOut;
            }
            List<BoardSnapshotPayload.BoardEntry> out = collect(mine, section);
            currentSource = mine;
            currentOut = out;
            return out;
        }
        if (mine == historySource) {
            return historyOut;
        }
        List<BoardSnapshotPayload.BoardEntry> out = collect(mine, section);
        historySource = mine;
        historyOut = out;
        return out;
    }

    private static List<BoardSnapshotPayload.BoardEntry> collect(
            List<BoardSnapshotPayload.BoardEntry> mine, MineListSection section) {
        List<BoardSnapshotPayload.BoardEntry> out = new ArrayList<>();
        for (BoardSnapshotPayload.BoardEntry entry : mine) {
            if (matches(entry, section)) {
                out.add(entry);
            }
        }
        if (section == HISTORY) {
            Collections.reverse(out);
        }
        return out;
    }

    public static int count(List<BoardSnapshotPayload.BoardEntry> mine, MineListSection section) {
        int n = 0;
        for (BoardSnapshotPayload.BoardEntry entry : mine) {
            if (matches(entry, section)) {
                n++;
            }
        }
        return n;
    }
}
