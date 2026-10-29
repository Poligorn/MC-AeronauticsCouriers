package dev.poligorn.aerocouriers.client.board;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Second-level confirmation over the details window (accept, abandon, cancel). */
public abstract class BoardConfirmOverlay extends TabletOverlay {
    public abstract UUID orderId();

    /** Recomputes a text-dependent height before {@link #layout}. */
    public void measure() {
    }

    public abstract @Nullable List<Component> hoverLines();
}
