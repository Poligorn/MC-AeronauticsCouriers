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
            return;
        }
        InternalEconomy ledger = InternalEconomy.get(player.server);
        long spurs = ledger.balance(player.getUUID());
        if (spurs <= 0) {
            return;
        }
        ledger.set(player.getUUID(), 0);
        Economies.get(player.server).deposit(player.getUUID(), MoneyScale.fromSpurs(spurs), "ledger_to_emeralds");
    }
}
