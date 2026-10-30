package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.client.ClientBoard;
import java.util.Arrays;

/**
 * Player-side collateral visibility. Under the peace profile every zero collateral element disappears; an order taken
 * before the switch still shows the collateral it holds.
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
