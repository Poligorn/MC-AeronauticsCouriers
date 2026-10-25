package dev.poligorn.aerocouriers.economy;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Append-only {@code <world>/aerocouriers/transactions.log}, one tab-separated line per movement. */
public final class TransactionLog {
    private final Path file;

    TransactionLog(Path file) {
        this.file = file;
    }

    static TransactionLog forServer(MinecraftServer server) {
        return new TransactionLog(server.getWorldPath(LevelResource.ROOT).resolve(AeronauticsCouriers.MOD_ID)
                .resolve("transactions.log"));
    }

    synchronized void write(String operation, UUID player, long spurs, String reason, long balanceAfter) {
        String line = String.join("\t", Instant.now().toString(), operation, player.toString(),
                Long.toString(spurs), reason, Long.toString(balanceAfter)) + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            AeronauticsCouriers.LOGGER.error("Could not write {}: {}", file, line.strip(), e);
        }
    }
}
