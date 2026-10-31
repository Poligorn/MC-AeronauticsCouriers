package dev.poligorn.aerocouriers.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationContractSource;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /couriers station list|rename|generate} (GDD §6.0–6.2). */
final class StationCommands {
    private static final int OP = Commands.LEVEL_GAMEMASTERS;
    private static final int MAX_NAME_LENGTH = 48;

    private StationCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> station() {
        return Commands.literal("station")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("generate")
                        .requires(src -> src.hasPermission(OP))
                        .executes(StationCommands::generate))
                .then(Commands.literal("rename")
                        .requires(src -> src.hasPermission(OP))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(StationCommands::rename))));
    }

    /** Players see the stations they discovered; operators and the console see all of them. */
    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        boolean all = player == null || source.hasPermission(OP);
        List<StationAtlas.Station> shown = StationAtlas.get(source.getServer()).all().stream()
                .filter(s -> all || s.discoveredBy(player.getUUID()))
                .toList();
        source.sendSuccess(() -> Component.translatable("station.aerocouriers.list.header", shown.size()), false);
        for (StationAtlas.Station s : shown) {
            source.sendSuccess(() -> Component.translatable("station.aerocouriers.list.line", s.name(),
                    s.dimension().location().toString(), s.pos().toShortString(), s.discoveredBy().size()), false);
        }
        return shown.size();
    }

    private static int generate(CommandContext<CommandSourceStack> ctx) {
        int created = StationContractSource.INSTANCE.generate(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("station.aerocouriers.generated", created), true);
        return created;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String name = StringArgumentType.getString(ctx, "name").strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            source.sendFailure(Component.translatable("station.aerocouriers.error.name", MAX_NAME_LENGTH));
            return 0;
        }
        PostStationBlockEntity station = PostStationBlock.stationEntity(source.getLevel(), pos);
        if (station == null) {
            source.sendFailure(Component.translatable("station.aerocouriers.error.not_station", pos.toShortString()));
            return 0;
        }
        String old = station.name();
        station.rename(name);
        source.sendSuccess(() -> Component.translatable("station.aerocouriers.renamed", old, name), true);
        return 1;
    }
}
