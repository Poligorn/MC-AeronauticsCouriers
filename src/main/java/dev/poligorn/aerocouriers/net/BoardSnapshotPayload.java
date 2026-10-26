package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * S2C snapshot of the job board (GDD §14). The client never computes eligibility; it just displays what the
 * server sent. {@code board} is one page of open contracts (20); {@code mine} is the viewer's own.
 */
public record BoardSnapshotPayload(
        boolean openScreen,
        int page,
        int boardTotal,
        long balance,
        int mailCount,
        boolean operator,
        boolean licensed,
        boolean allowSelfAccept,
        boolean requireTablet,
        Optional<Component> notice,
        List<BoardEntry> board,
        List<BoardEntry> mine,
        List<StationHint> stations,
        BoardProfile profile
) implements CustomPacketPayload {
    public static final Type<BoardSnapshotPayload> TYPE = new Type<>(AeronauticsCouriers.id("board_snapshot"));
    public static final int PAGE_SIZE = 20;

    public record CargoLine(int count, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CargoLine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CargoLine::count,
                ByteBufCodecs.STRING_UTF8, CargoLine::name,
                CargoLine::new);

        public static CargoLine of(ItemStack stack) {
            return new CargoLine(stack.getCount(), stack.getHoverName().getString());
        }
    }

    public record StationHint(String name, BlockPos pos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StationHint> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StationHint::name,
                BlockPos.STREAM_CODEC, StationHint::pos,
                StationHint::new);
    }

    /** Viewer courier stats for the board «Профиль» tab (server is source of truth). */
    public record BoardProfile(
            String playerName,
            LicenseRank rank,
            int delivered,
            int failed,
            long distance,
            long rewardSum
    ) {
        public static final BoardProfile EMPTY = new BoardProfile("", LicenseRank.RANK1, 0, 0, 0L, 0L);

        public static final StreamCodec<RegistryFriendlyByteBuf, BoardProfile> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, BoardProfile::playerName,
                LicenseRank.STREAM_CODEC, BoardProfile::rank,
                ByteBufCodecs.VAR_INT, BoardProfile::delivered,
                ByteBufCodecs.VAR_INT, BoardProfile::failed,
                ByteBufCodecs.VAR_LONG, BoardProfile::distance,
                ByteBufCodecs.VAR_LONG, BoardProfile::rewardSum,
                BoardProfile::new);
    }

    public record BoardEntry(
            UUID id,
            String shortId,
            ContractState state,
            String role,
            String originLabel,
            String destinationLabel,
            int distance,
            String size,
            String weight,
            long reward,
            long collateral,
            LicenseRank requiredRank,
            long deadlineMinutes,
            long minutesLeft,
            List<CargoLine> manifest
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardEntry> STREAM_CODEC =
                StreamCodec.of(BoardEntry::write, BoardEntry::read);

        private static void write(RegistryFriendlyByteBuf buf, BoardEntry e) {
            UUIDUtil.STREAM_CODEC.encode(buf, e.id);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.shortId);
            ContractState.STREAM_CODEC.encode(buf, e.state);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.role);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.originLabel);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.destinationLabel);
            ByteBufCodecs.VAR_INT.encode(buf, e.distance);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.size);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.weight);
            ByteBufCodecs.VAR_LONG.encode(buf, e.reward);
            ByteBufCodecs.VAR_LONG.encode(buf, e.collateral);
            LicenseRank.STREAM_CODEC.encode(buf, e.requiredRank);
            ByteBufCodecs.VAR_LONG.encode(buf, e.deadlineMinutes);
            ByteBufCodecs.VAR_LONG.encode(buf, e.minutesLeft);
            CargoLine.STREAM_CODEC.apply(ByteBufCodecs.list(27)).encode(buf, e.manifest);
        }

        private static BoardEntry read(RegistryFriendlyByteBuf buf) {
            return new BoardEntry(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ContractState.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    LicenseRank.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    CargoLine.STREAM_CODEC.apply(ByteBufCodecs.list(27)).decode(buf));
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(BoardSnapshotPayload::write, BoardSnapshotPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, BoardSnapshotPayload p) {
        buf.writeBoolean(p.openScreen);
        buf.writeVarInt(p.page);
        buf.writeVarInt(p.boardTotal);
        buf.writeVarLong(p.balance);
        buf.writeVarInt(p.mailCount);
        buf.writeBoolean(p.operator);
        buf.writeBoolean(p.licensed);
        buf.writeBoolean(p.allowSelfAccept);
        buf.writeBoolean(p.requireTablet);
        buf.writeBoolean(p.notice.isPresent());
        p.notice.ifPresent(component -> ComponentSerialization.STREAM_CODEC.encode(buf, component));
        BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)).encode(buf, p.board);
        BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, p.mine);
        StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(buf, p.stations);
        BoardProfile.STREAM_CODEC.encode(buf, p.profile);
    }

    private static BoardSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        boolean openScreen = buf.readBoolean();
        int page = buf.readVarInt();
        int boardTotal = buf.readVarInt();
        long balance = buf.readVarLong();
        int mailCount = buf.readVarInt();
        boolean operator = buf.readBoolean();
        boolean licensed = buf.readBoolean();
        boolean allowSelfAccept = buf.readBoolean();
        boolean requireTablet = buf.readBoolean();
        Optional<Component> notice = buf.readBoolean()
                ? Optional.of(ComponentSerialization.STREAM_CODEC.decode(buf))
                : Optional.empty();
        return new BoardSnapshotPayload(openScreen, page, boardTotal, balance, mailCount, operator, licensed,
                allowSelfAccept, requireTablet, notice,
                BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)).decode(buf),
                BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf),
                StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(buf),
                BoardProfile.STREAM_CODEC.decode(buf));
    }

    public Optional<BoardEntry> find(UUID id) {
        for (BoardEntry entry : board) {
            if (entry.id.equals(id)) {
                return Optional.of(entry);
            }
        }
        for (BoardEntry entry : mine) {
            if (entry.id.equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static void handle(BoardSnapshotPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientBoard.apply(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
