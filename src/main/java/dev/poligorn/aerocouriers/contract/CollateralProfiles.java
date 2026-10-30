package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.admin.AdminActions;
import dev.poligorn.aerocouriers.config.CouriersConfigEditor;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.net.BoardPackets;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Migration and switching of {@link CollateralProfile}; players are never notified about a switch. */
public final class CollateralProfiles {
    private static final double LEGACY_DEFAULT_RATIO = 0.5;

    private CollateralProfiles() {
    }

    /** One-time: a world that tuned {@code p2p_min_collateral_ratio} keeps its behaviour under the nearest profile. */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ContractManager manager = ContractManager.get(server);
        if (manager.collateralMigrated() || !CouriersServerConfig.SPEC.isLoaded()) {
            return;
        }
        double legacy = CouriersServerConfig.P2P_MIN_COLLATERAL_RATIO.get();
        CollateralProfile current = CollateralProfile.active();
        if (current == CollateralProfile.NORMAL && Math.abs(legacy - LEGACY_DEFAULT_RATIO) > 1e-9) {
            CollateralProfile mapped = CollateralProfile.fromLegacyRatio(legacy);
            if (Math.abs(mapped.ratio() - legacy) > 1e-9 && legacy > 0.0) {
                AeronauticsCouriers.LOGGER.warn("[collateral] p2p_min_collateral_ratio={} has no exact profile, using {}",
                        legacy, mapped.key());
            }
            CouriersConfigEditor.setCollateralProfile(mapped);
            AeronauticsCouriers.LOGGER.info("[collateral] migrated p2p_min_collateral_ratio={} to collateral_profile={}",
                    legacy, mapped.key());
        }
        manager.markCollateralMigrated();
    }

    /**
     * Admin switch: saves the config, regenerates open village orders under the new rules and, for peace, zeroes the
     * collateral of open player orders. Accepted orders keep the collateral they were taken with.
     */
    public static ContractManager.Outcome apply(MinecraftServer server, CollateralProfile next) {
        CollateralProfile previous = CollateralProfile.active();
        CouriersConfigEditor.setCollateralProfile(next);
        int cleared = next.noCollateral() ? ContractManager.get(server).clearOpenCollateral() : 0;
        if (previous != next) {
            AdminActions.regenerateVillageBoards(server);
        }
        AeronauticsCouriers.LOGGER.info("[collateral] profile {} -> {} (open player orders zeroed: {})",
                previous.key(), next.key(), cleared);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BoardPackets.syncSnapshot(player);
        }
        return new ContractManager.Outcome(true,
                Component.translatable("admin.aerocouriers.economy.profile.applied",
                        Component.translatable(next.langKey())),
                Optional.empty());
    }
}
