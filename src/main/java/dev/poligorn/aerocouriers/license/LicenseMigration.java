package dev.poligorn.aerocouriers.license;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Replaces legacy {@code kubejs:pilot_license_<rank>} items of the MC-Ikaria pack on login (GDD §3.1.2).
 * Looks in every Curios slot (the pack used {@code license}) and in the main inventory, because Curios moves
 * items that are no longer valid for a slot out into the inventory.
 */
public final class LicenseMigration {
    private static final String LEGACY_NAMESPACE = "kubejs";
    private static final String LEGACY_PREFIX = "pilot_license_";

    private LicenseMigration() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            migrate(player);
        }
    }

    public static int migrate(ServerPlayer player) {
        int migrated = 0;
        LicenseRank best = null;

        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            for (Map.Entry<String, ICurioStacksHandler> entry : curios.get().getCurios().entrySet()) {
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    Optional<LicenseRank> rank = legacyRank(stacks.getStackInSlot(slot));
                    if (rank.isPresent()) {
                        stacks.setStackInSlot(slot, Licenses.issueLicense(player, rank.get(), false));
                        best = higher(best, rank.get());
                        migrated++;
                    }
                }
            }
        }

        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            Optional<LicenseRank> rank = legacyRank(inventory.getItem(slot));
            if (rank.isPresent()) {
                inventory.setItem(slot, Licenses.issueLicense(player, rank.get(), false));
                best = higher(best, rank.get());
                migrated++;
            }
        }

        if (best != null) {
            CourierProfile profile = Licenses.profile(player);
            int startXp = Math.max(profile.xp(), migrationXp(best));
            Licenses.setProfile(player, profile.withXp(startXp).withMigrated(true));
            AeronauticsCouriers.LOGGER.info("Migrated {} legacy pilot license(s) of {} (rank {}, xp {})",
                    migrated, player.getGameProfile().getName(), best.getSerializedName(), startXp);
        }
        return migrated;
    }

    private static Optional<LicenseRank> legacyRank(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!LEGACY_NAMESPACE.equals(id.getNamespace()) || !id.getPath().startsWith(LEGACY_PREFIX)) {
            return Optional.empty();
        }
        return LicenseRank.fromLegacySuffix(id.getPath().substring(LEGACY_PREFIX.length()));
    }

    private static LicenseRank higher(LicenseRank current, LicenseRank candidate) {
        if (current == null || candidate.isAdmin()) {
            return candidate;
        }
        if (current.isAdmin()) {
            return current;
        }
        return candidate.level() > current.level() ? candidate : current;
    }

    private static int migrationXp(LicenseRank rank) {
        if (!CouriersServerConfig.SPEC.isLoaded()) {
            return 0;
        }
        return switch (rank) {
            case RANK2 -> CouriersServerConfig.MIGRATION_XP_SECOND.get();
            case RANK3 -> CouriersServerConfig.MIGRATION_XP_FIRST.get();
            case RANK4, RANK5 -> CouriersServerConfig.MIGRATION_XP_CAPTAIN.get();
            case RANK1 -> CouriersServerConfig.MIGRATION_XP_CADET.get();
            case ADMIN -> 0;
        };
    }
}
