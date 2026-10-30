package dev.poligorn.aerocouriers.license;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.contract.Mailbox;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.registry.ModAttachments;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;

/**
 * License lookup and server-side bookkeeping (GDD §3, §14.4).
 */
public final class Licenses {
    public static final String CURIOS_SLOT = "license";

    private Licenses() {
    }

    public static Optional<ItemStack> findLicense(Player player) {
        // The Item overload of findFirstCurio caches its result for the whole game tick; the predicate overload
        // does not, so a license swapped earlier in the same tick is seen.
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.findFirstCurio(stack -> stack.is(ModItems.PILOT_LICENSE.get())))
                .map(SlotResult::stack);
    }

    /** True if the player wears a license bound to themselves (GDD §3.3: someone else's license grants nothing). */
    public static boolean isLicensedCourier(Player player) {
        return findLicense(player)
                .map(stack -> stack.get(ModComponents.LICENSE_OWNER.get()))
                .filter(owner -> owner.isOwnedBy(player.getUUID()))
                .filter(owner -> !(player instanceof ServerPlayer sp)
                        || LicenseRegistry.get(sp.server).isValid(owner.uuid(), owner.number()))
                .isPresent();
    }

    /** Lost license: the player had one before and holds none now (Curios, inventory, mailbox). */
    public static boolean canRestore(ServerPlayer player) {
        return profile(player).licensesIssued() > 0 && !holdsValidLicense(player);
    }

    private static boolean holdsValidLicense(ServerPlayer player) {
        LicenseRegistry registry = LicenseRegistry.get(player.server);
        List<ItemStack> stacks = new ArrayList<>(licenseStacks(player));
        stacks.addAll(Mailbox.get(player.server).contents(player.getUUID()).stream()
                .filter(stack -> stack.is(ModItems.PILOT_LICENSE.get())).toList());
        for (ItemStack stack : stacks) {
            LicenseOwner owner = stack.get(ModComponents.LICENSE_OWNER.get());
            if (owner == null || (owner.isOwnedBy(player.getUUID()) && registry.isValid(owner.uuid(), owner.number()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Issues a fresh license of the current rank with a new number and annuls every older one. Payment is the
     * caller's job; this does not count towards {@code licensesIssued}.
     */
    public static ItemStack restore(ServerPlayer player) {
        ItemStack stack = issueLicense(player, rankOf(player), false);
        LicenseOwner owner = stack.get(ModComponents.LICENSE_OWNER.get());
        if (owner != null) {
            LicenseRegistry.get(player.server).setCurrent(player.getUUID(), owner.number());
        }
        deliver(player, stack);
        return stack;
    }

    public static CourierProfile profile(Player player) {
        return player.getData(ModAttachments.COURIER_PROFILE);
    }

    public static void setProfile(Player player, CourierProfile profile) {
        player.setData(ModAttachments.COURIER_PROFILE, profile);
    }

    /** Removes every pilot license from Curios and inventory (admin reset). */
    public static void removeAllPilotLicenses(Player player) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (SlotResult slot : handler.findCurios(stack -> stack.is(ModItems.PILOT_LICENSE.get()))) {
                SlotContext ctx = slot.slotContext();
                handler.setEquippedCurio(ctx.identifier(), ctx.index(), ItemStack.EMPTY);
            }
        });
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.PILOT_LICENSE.get())) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public static LicenseRank rankOf(Player player) {
        return RankRules.rankFor(profile(player));
    }

    /**
     * Profile rank, raised to a higher rank written on a pilot license in Curios or the inventory. An admin license
     * wins. {@link #tickWorn} still copies the profile rank onto a worn non-admin license, so an admin grant that
     * also sets XP stays in agreement once the license is equipped.
     */
    public static LicenseRank effectiveRank(Player player) {
        LicenseRank rank = rankOf(player);
        for (ItemStack stack : licenseStacks(player)) {
            LicenseRank onItem = stack.get(ModComponents.LICENSE_RANK.get());
            if (onItem == null) {
                continue;
            }
            if (onItem.isAdmin()) {
                return onItem;
            }
            if (!rank.isAdmin() && onItem.level() > rank.level()) {
                rank = onItem;
            }
        }
        return rank;
    }

    public static boolean holdsPilotLicense(Player player) {
        if (!licenseStacks(player).isEmpty()) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            for (ItemStack stack : Mailbox.get(serverPlayer.server).contents(serverPlayer.getUUID())) {
                if (stack.is(ModItems.PILOT_LICENSE.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Empty Curios {@code license} slot, then the inventory, then the mailbox (claim row, not a ground drop).
     */
    public static void deliver(ServerPlayer player, ItemStack stack) {
        if (tryEquipLicense(player, stack)) {
            return;
        }
        if (player.getInventory().add(stack)) {
            return;
        }
        if (!stack.isEmpty()) {
            Mailbox.get(player.server).post(player.getUUID(), List.of(stack),
                    Component.translatable("ui.aerocouriers.inbox.license"));
        }
    }

    private static boolean tryEquipLicense(ServerPlayer player, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player).map(handler -> {
            var found = handler.getStacksHandler(CURIOS_SLOT);
            if (found.isEmpty()) {
                return false;
            }
            var stacks = found.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).isEmpty()) {
                    handler.setEquippedCurio(CURIOS_SLOT, i, stack);
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private static List<ItemStack> licenseStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (SlotResult slot : handler.findCurios(stack -> stack.is(ModItems.PILOT_LICENSE.get()))) {
                stacks.add(slot.stack());
            }
        });
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.PILOT_LICENSE.get())) {
                stacks.add(stack);
            }
        }
        return stacks;
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
