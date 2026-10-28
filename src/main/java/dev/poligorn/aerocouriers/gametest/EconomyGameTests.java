package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.economy.EconomyBackendResolver;
import dev.poligorn.aerocouriers.economy.EconomyMigration;
import dev.poligorn.aerocouriers.economy.InternalEconomy;
import dev.poligorn.aerocouriers.economy.TransactionLog;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private EconomyGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void backendResolver(GameTestHelper helper) {
        helper.assertValueEqual(EconomyBackendResolver.resolve("auto", false), EconomyBackendResolver.Choice.INTERNAL,
                "auto without mod");
        helper.assertValueEqual(EconomyBackendResolver.resolve("auto", true), EconomyBackendResolver.Choice.NUMISMATICS,
                "auto with mod");
        helper.assertValueEqual(EconomyBackendResolver.resolve("internal", true), EconomyBackendResolver.Choice.INTERNAL,
                "forced internal");
        helper.assertValueEqual(EconomyBackendResolver.resolve("numismatics", false),
                EconomyBackendResolver.Choice.INTERNAL, "forced numismatics missing mod");
        helper.assertTrue(EconomyBackendResolver.forcedNumismaticsMissing("numismatics", false), "warn case");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void migrationIdempotent(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        MemoryEconomy delegate = new MemoryEconomy();
        InternalEconomy internal = InternalEconomy.get(helper.getLevel().getServer());
        internal.deposit(player, 75, "test_seed");
        TransactionLog log = TransactionLog.open(helper.getLevel().getServer());

        EconomyMigration.migratePlayer(player, internal, delegate, log);
        helper.assertValueEqual(delegate.balance(player), 75L, "delegate holds funds");
        helper.assertValueEqual(internal.balance(player), 0L, "internal cleared");

        EconomyMigration.migratePlayer(player, internal, delegate, log);
        helper.assertValueEqual(delegate.balance(player), 75L, "second migrate does not double");
        helper.assertValueEqual(internal.balance(player), 0L, "internal still clear");
        helper.succeed();
    }

    private static final class MemoryEconomy implements Economy {
        private final java.util.Map<UUID, Long> balances = new java.util.HashMap<>();

        @Override
        public long balance(UUID player) {
            return balances.getOrDefault(player, 0L);
        }

        @Override
        public boolean withdraw(UUID player, long spurs, String reason) {
            long current = balance(player);
            if (current < spurs) {
                return false;
            }
            balances.put(player, current - spurs);
            return true;
        }

        @Override
        public void deposit(UUID player, long spurs, String reason) {
            balances.merge(player, spurs, Math::addExact);
        }
    }
}
