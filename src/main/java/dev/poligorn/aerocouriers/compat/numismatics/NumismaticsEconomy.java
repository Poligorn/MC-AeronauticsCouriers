package dev.poligorn.aerocouriers.compat.numismatics;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.poligorn.aerocouriers.economy.Economy;
import dev.poligorn.aerocouriers.economy.EconomyMigration;
import java.util.UUID;

/**
 * Bank-account backend for Create: Numismatics. Loaded only when the mod is present
 * ({@link dev.poligorn.aerocouriers.economy.Economies} uses reflection so Numismatics types are not
 * initialized otherwise).
 */
public final class NumismaticsEconomy implements Economy {
    private NumismaticsEconomy() {
    }

    public static Economy create() {
        return new NumismaticsEconomy();
    }

    @Override
    public long balance(UUID player) {
        BankAccount account = Numismatics.BANK.getAccount(player);
        return account == null ? 0L : account.getBalance();
    }

    @Override
    public boolean withdraw(UUID player, long spurs, String reason) {
        if (spurs < 0) {
            throw new IllegalArgumentException("Negative withdrawal: " + spurs);
        }
        if (spurs == 0) {
            return true;
        }
        int amount = EconomyMigration.toSpurs(spurs);
        if (amount <= 0) {
            return false;
        }
        BankAccount account = Numismatics.BANK.getAccount(player);
        if (account == null) {
            return false;
        }
        return account.deduct(amount, false);
    }

    @Override
    public void deposit(UUID player, long spurs, String reason) {
        if (spurs < 0) {
            throw new IllegalArgumentException("Negative deposit: " + spurs);
        }
        if (spurs == 0) {
            return;
        }
        int amount = EconomyMigration.toSpurs(spurs);
        if (amount <= 0) {
            return;
        }
        Numismatics.BANK.getOrCreateAccount(player, BankAccount.Type.PLAYER).deposit(amount);
    }
}
