package dev.poligorn.aerocouriers.license;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.registry.ModAttachments;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * License lookup and server-side bookkeeping (GDD §3, §14.4).
 */
public final class Licenses {
    public static final String CURIOS_SLOT = "license";

    private Licenses() {
    }

    public static Optional<ItemStack> findLicense(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.findFirstCurio(ModItems.PILOT_LICENSE.get()))
                .map(SlotResult::stack);
    }

    /** True if the player wears a license bound to themselves (GDD §3.3: someone else's license grants nothing). */
    public static boolean isLicensedCourier(Player player) {
        return findLicense(player)
                .map(stack -> stack.get(ModComponents.LICENSE_OWNER.get()))
                .filter(owner -> owner.isOwnedBy(player.getUUID()))
                .isPresent();
    }

    public static CourierProfile profile(Player player) {
        return player.getData(ModAttachments.COURIER_PROFILE);
    }

    public static void setProfile(Player player, CourierProfile profile) {
        player.setData(ModAttachments.COURIER_PROFILE, profile);
    }

    public static LicenseRank rankOf(Player player) {
        return RankRules.rankFor(profile(player));
    }

    /**
     * Creates a license already bound to {@code owner}.
     *
     * @param countAsIssued whether it counts towards the reissue price (migration and operator grants do not)
     */
    public static ItemStack issueLicense(ServerPlayer owner, LicenseRank rank, boolean countAsIssued) {
        ItemStack stack = new ItemStack(ModItems.PILOT_LICENSE.get());
        bind(owner, stack, countAsIssued);
        stack.set(ModComponents.LICENSE_RANK.get(), rank);
        return stack;
    }

    /**
     * Called every second while the license is worn: binds an unowned license to the wearer and keeps the rank
     * component in sync with the owner's profile. Curios syncs the changed stack to all tracking clients.
     */
    public static void tickWorn(ServerPlayer wearer, ItemStack stack) {
        LicenseOwner owner = stack.get(ModComponents.LICENSE_OWNER.get());
        if (owner == null) {
            owner = bind(wearer, stack, true);
            AeronauticsCouriers.LOGGER.info("Bound pilot license #{} to {}", owner.number(), wearer.getGameProfile().getName());
        }
        if (!owner.isOwnedBy(wearer.getUUID())) {
            return;
        }
        LicenseRank current = stack.get(ModComponents.LICENSE_RANK.get());
        if (current != null && current.isAdmin()) {
            return;
        }
        LicenseRank expected = rankOf(wearer);
        if (current != expected) {
            stack.set(ModComponents.LICENSE_RANK.get(), expected);
        }
    }

    private static LicenseOwner bind(ServerPlayer player, ItemStack stack, boolean countAsIssued) {
        int number = LicenseRegistry.get(player.server).issueNumber();
        LicenseOwner owner = new LicenseOwner(player.getUUID(), player.getGameProfile().getName(), number);
        stack.set(ModComponents.LICENSE_OWNER.get(), owner);
        if (countAsIssued) {
            CourierProfile profile = profile(player);
            setProfile(player, profile.withLicensesIssued(profile.licensesIssued() + 1));
        }
        return owner;
    }
}
