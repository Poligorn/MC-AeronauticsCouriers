package dev.poligorn.aerocouriers.client.tablet;

/**
 * Client hook for a future GeckoLib handheld model ({@code aerocouriers:order_tablet}).
 * <p>
 * TODO: register a GeckoLib item renderer here and swap off the flat {@code item/order_tablet} texture.
 * Keep this class as the only client entry point so the dependency stays optional until then.
 */
public final class OrderTabletVisuals {
    private OrderTabletVisuals() {
    }

    /** Called from client setup when a 3D model is added. */
    public static void register() {
        // flat item model only
    }
}
