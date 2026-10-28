package dev.poligorn.aerocouriers.economy;

import java.util.UUID;

/** Writes every successful money movement to {@code transactions.log} (GDD §14.2). */
final class LoggedEconomy implements Economy {
    private final Economy delegate;
    private final TransactionLog log;

    LoggedEconomy(Economy delegate, TransactionLog log) {
        this.delegate = delegate;
        this.log = log;
    }

    @Override
    public long balance(UUID player) {
        return delegate.balance(player);
    }

    @Override
    public boolean withdraw(UUID player, long spurs, String reason) {
        boolean ok = delegate.withdraw(player, spurs, reason);
        if (ok && spurs > 0) {
            log.write("withdraw", player, spurs, reason, delegate.balance(player));
        }
        return ok;
    }

    @Override
    public void deposit(UUID player, long spurs, String reason) {
        delegate.deposit(player, spurs, reason);
        if (spurs > 0) {
            log.write("deposit", player, spurs, reason, delegate.balance(player));
        }
    }
}
