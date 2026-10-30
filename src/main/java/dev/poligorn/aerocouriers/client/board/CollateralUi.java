package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.ClientBoard;
import java.util.Arrays;

/**
 * Player-side collateral visibility. Under the peace profile a zero collateral element is hidden. Switching to peace
 * also zeroes open and accepted orders, so a non-zero amount does not stay on the board.
 */
public final class CollateralUi {
    private CollateralUi() {
    }

    public static boolean serverHasNoCollateral() {
        return ClientBoard.collateralProfile().noCollateral();
    }

    public static boolean hidden(long collateral) {
        return serverHasNoCollateral() && collateral <= 0;
    }

    public static BoardSortKey[] playerSortKeys(BoardSortKey... keys) {
        if (!serverHasNoCollateral()) {
            return keys;
        }
        return Arrays.stream(keys).filter(k -> k != BoardSortKey.COLLATERAL).toArray(BoardSortKey[]::new);
    }

    public static BoardSortKey visibleSortKey(BoardSortKey key) {
        return key == BoardSortKey.COLLATERAL && serverHasNoCollateral() ? BoardSortKey.REWARD : key;
    }
}
