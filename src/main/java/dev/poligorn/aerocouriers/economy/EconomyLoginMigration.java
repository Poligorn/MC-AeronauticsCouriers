package dev.poligorn.aerocouriers.economy;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class EconomyLoginMigration {
    private EconomyLoginMigration() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Economies.usesNumismatics(player.server)) {
            Economies.get(player.server).balance(player.getUUID());
        }
    }
}
