package dev.poligorn.aerocouriers.client.navigation;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Client-only focus for the navigation HUD when multiple active hauls exist. */
public final class ClientNavigation {
    private static volatile @Nullable UUID focusedOrderId;

    private ClientNavigation() {
    }

    public static void setFocusedOrder(@Nullable UUID orderId) {
        focusedOrderId = orderId;
    }

    public static @Nullable UUID focusedOrder() {
        return focusedOrderId;
    }

    public static void clearFocus() {
        focusedOrderId = null;
    }
}
