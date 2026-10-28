package dev.poligorn.aerocouriers.economy;

import java.util.UUID;

/** Runs {@link EconomyMigration} before delegating to Numismatics (or any external backend). */
final class MigratingEconomy implements Economy {
    private final Economy delegate;
    private final InternalEconomy internal;
    private final TransactionLog log;

    MigratingEconomy(Economy delegate, InternalEconomy internal, TransactionLog log) {
        this.delegate = delegate;
        this.internal = internal;
        this.log = log;
    }

    @Override
    public long balance(UUID player) {
        EconomyMigration.migratePlayer(player, internal, delegate, log);
        return delegate.balance(player);
    }

    @Override
    public boolean withdraw(UUID player, long spurs, String reason) {
        EconomyMigration.migratePlayer(player, internal, delegate, log);
        return delegate.withdraw(player, spurs, reason);
    }

    @Override
    public void deposit(UUID player, long spurs, String reason) {
        EconomyMigration.migratePlayer(player, internal, delegate, log);
        delegate.deposit(player, spurs, reason);
    }
}
