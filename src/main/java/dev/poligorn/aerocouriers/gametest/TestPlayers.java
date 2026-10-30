package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.contract.ContractManager;
import dev.poligorn.aerocouriers.item.Waybills;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * FakePlayers for GameTests. They are not in the server player list, so each one is registered with
 * {@link ContractManager#registerTestPlayer}: the emerald economy and contract notices can then find it.
 */
final class TestPlayers {
    private TestPlayers() {
    }

    /** Unlicensed player that can hold emeralds and waybills. */
    static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        ContractManager.registerTestPlayer(player);
        return player;
    }

    /** Player wearing a pilot license bound to them (rank from the current profile XP). */
    static ServerPlayer courier(GameTestHelper helper, String name) {
        ServerPlayer player = player(helper, name);
        license(player);
        return player;
    }

    static void license(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).orElseThrow()
                .setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        Licenses.tickWorn(player, Licenses.findLicense(player).orElseThrow());
    }

    /** Publishing an order from a container consumes one transport waybill. */
    static void giveWaybills(ServerPlayer player, int count) {
        player.getInventory().add(Waybills.stack(count));
    }
}
