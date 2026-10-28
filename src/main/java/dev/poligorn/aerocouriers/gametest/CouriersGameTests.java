package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.cargo.CargoSpawner;
import dev.poligorn.aerocouriers.cargo.CargoTracker;
import dev.poligorn.aerocouriers.contract.Destination;
import dev.poligorn.aerocouriers.contract.Pricing;
import dev.poligorn.aerocouriers.contract.RankRules;
import dev.poligorn.aerocouriers.license.CourierProfile;
import dev.poligorn.aerocouriers.license.LicenseOwner;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.license.Licenses;
import dev.poligorn.aerocouriers.registry.ModComponents;
import dev.poligorn.aerocouriers.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Server-side checks for iterations 0–1. Only registered when GameTests are enabled for the {@code aerocouriers}
 * namespace ({@code ./gradlew runGameTestServer}); inert in normal play.
 */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouriersGameTests {
    static final String EMPTY = "empty";

    private CouriersGameTests() {
    }

    /** Builds an 8×8×8 air template at startup so no binary structure file has to live in the repository. */
    public static void createEmptyTemplate(ServerStartedEvent event) {
        var level = event.getServer().overworld();
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(AeronauticsCouriers.id("empty"));
        BlockPos origin = new BlockPos(0, level.getMaxBuildHeight() - 16, 0);
        template.fillFromWorld(level, origin, new Vec3i(8, 8, 8), false, Blocks.STRUCTURE_VOID);
    }

    @GameTest(template = EMPTY)
    public static void licenseBindsToWearerAndFollowsRank(GameTestHelper helper) {
        // A FakePlayer instead of makeMockServerPlayerInLevel(): Simulated sends payloads on join that a mock
        // connection rejects.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "courier_test"));
        var curios = CuriosApi.getCuriosInventory(player).orElse(null);
        helper.assertTrue(curios != null, "Player has no Curios inventory");
        helper.assertTrue(curios.getStacksHandler(Licenses.CURIOS_SLOT).isPresent(), "Curios slot 'license' missing");

        curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, new ItemStack(ModItems.PILOT_LICENSE.get()));
        ItemStack worn = Licenses.findLicense(player).orElseThrow();
        helper.assertTrue(!Licenses.isLicensedCourier(player), "Unbound license must not count");

        Licenses.tickWorn(player, worn);
        LicenseOwner owner = worn.get(ModComponents.LICENSE_OWNER.get());
        helper.assertTrue(owner != null && owner.isOwnedBy(player.getUUID()), "License was not bound to the wearer");
        helper.assertTrue(owner.number() > 0, "License number was not issued");
        helper.assertTrue(Licenses.isLicensedCourier(player), "Bound license must count");
        helper.assertValueEqual(worn.get(ModComponents.LICENSE_RANK.get()), LicenseRank.RANK1, "initial rank");
        helper.assertValueEqual(Licenses.profile(player).licensesIssued(), 1, "licenses issued");

        Licenses.setProfile(player, Licenses.profile(player).withXp(1500));
        Licenses.tickWorn(player, worn);
        helper.assertValueEqual(worn.get(ModComponents.LICENSE_RANK.get()), LicenseRank.RANK3, "rank after 1500 XP");

        worn.set(ModComponents.LICENSE_RANK.get(), LicenseRank.ADMIN);
        Licenses.tickWorn(player, worn);
        helper.assertValueEqual(worn.get(ModComponents.LICENSE_RANK.get()), LicenseRank.ADMIN, "admin is kept");

        ItemStack foreign = new ItemStack(ModItems.PILOT_LICENSE.get());
        foreign.set(ModComponents.LICENSE_OWNER.get(), new LicenseOwner(UUID.randomUUID(), "Someone", 99));
        curios.setEquippedCurio(Licenses.CURIOS_SLOT, 0, foreign);
        helper.assertTrue(!Licenses.isLicensedCourier(player), "Foreign license must not count");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rankRulesFollowGdd(GameTestHelper helper) {
        helper.assertValueEqual(RankRules.rankFor(CourierProfile.EMPTY.withXp(299)), LicenseRank.RANK1, "299");
        helper.assertValueEqual(RankRules.rankFor(CourierProfile.EMPTY.withXp(300)), LicenseRank.RANK2, "300");
        helper.assertValueEqual(RankRules.rankFor(CourierProfile.EMPTY.withXp(5000)), LicenseRank.RANK4, "5000");
        CourierProfile ace = new CourierProfile(15000, 9, 1, 0, 0L, 1, false, 0, false, 0L,
                0, 0L, 0L, 0, 0, 0, 0);
        helper.assertValueEqual(RankRules.rankFor(ace), LicenseRank.RANK5, "ace at 90%");
        CourierProfile unreliable = new CourierProfile(15000, 8, 2, 0, 0L, 1, false, 0, false, 0L,
                0, 0L, 0L, 0, 0, 0, 0);
        helper.assertValueEqual(RankRules.rankFor(unreliable), LicenseRank.RANK4, "no ace below 90%");
        helper.assertValueEqual(LicenseRank.fromLegacySuffix("captain").orElseThrow(), LicenseRank.RANK4, "legacy");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void profileCodecRoundTrip(GameTestHelper helper) {
        CourierProfile profile = new CourierProfile(1234, 10, 2, 1, 98765L, 3, true, 2, true, 555L,
                4, 200L, 4100L, 1, 2, 1, 0);
        Tag encoded = CourierProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow();
        CourierProfile decoded = CourierProfile.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(decoded, profile, "profile");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void profileCodecMissingRewardSum(GameTestHelper helper) {
        Tag encoded = CourierProfile.CODEC.encodeStart(NbtOps.INSTANCE, CourierProfile.EMPTY.withDelivery(0, 0, false, 0))
                .getOrThrow();
        if (encoded instanceof net.minecraft.nbt.CompoundTag compound) {
            compound.remove("reward_sum");
        }
        CourierProfile decoded = CourierProfile.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(decoded.rewardSum(), 0L, "default reward_sum");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void issuerFeeScalesWithContainerSize(GameTestHelper helper) {
        helper.assertValueEqual(Pricing.fee(100, CargoSize.S), 5L, "S ×1.0");
        helper.assertValueEqual(Pricing.fee(100, CargoSize.M), 8L, "M ×1.5");
        helper.assertValueEqual(Pricing.fee(100, CargoSize.L), 13L, "L ×2.5");
        helper.assertValueEqual(Pricing.fee(100, CargoSize.XL), 20L, "XL ×4.0");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pricingScalesWithCargoWeight(GameTestHelper helper) {
        double distance = 600.0;
        long lightNpc = Pricing.npcReward(distance, 48.0, RandomSource.create(7L));
        long heavyNpc = Pricing.npcReward(distance, 2048.0, RandomSource.create(7L));
        helper.assertTrue(heavyNpc >= lightNpc + 50,
                "full L should pay much more than light S at same distance: light=" + lightNpc + " heavy=" + heavyNpc);

        long minLight = Pricing.minReward(distance, 48.0);
        long minHeavy = Pricing.minReward(distance, 2048.0);
        helper.assertTrue(minHeavy >= minLight + 70,
                "P2P minimum should scale with weight: light=" + minLight + " heavy=" + minHeavy);

        Destination origin = new Destination(Level.OVERWORLD, BlockPos.ZERO, 8);
        Destination dest = new Destination(Level.OVERWORLD, new BlockPos(600, 0, 0), 8);
        Pricing.NpcMoney balanced = Pricing.balanceNpcMoney(heavyNpc, 5L, CargoSize.L, origin, dest);
        helper.assertTrue(balanced.collateral() >= balanced.reward(),
                "NPC collateral must stay at least the reward after weight raises payout");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cargoContainerReportsWorldPosition(GameTestHelper helper) {
        UUID contractId = UUID.randomUUID();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        var result = CargoSpawner.spawn(helper.getLevel(), pos, CargoSize.S, 8, contractId, true, Direction.NORTH);
        helper.assertTrue(result instanceof CargoSpawner.Result.Spawned, "Container was not assembled: " + result);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 4, 4));
        helper.succeedWhen(() -> {
            var snapshot = CargoTracker.all().stream()
                    .filter(s -> s.contractId().equals(contractId))
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(snapshot != null, "Block entity in the Sable plot has not reported yet");
            double dx = snapshot.position().x() - (pos.getX() + 0.5);
            double dz = snapshot.position().z() - (pos.getZ() + 0.5);
            // GameTest places structures around |x|,|z| ~ 1e7 where the physics pose is float-precise to ~1 block;
            // an unprojected plot coordinate would be ~2e7 blocks away.
            helper.assertTrue(Math.abs(dx) <= 2 && Math.abs(dz) <= 2,
                    "World position is not projected out of the plot: dx=" + dx + " dz=" + dz);
        });
    }
}
