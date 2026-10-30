package dev.poligorn.aerocouriers.net;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.contract.ContractType;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.license.IssuerStats;
import dev.poligorn.aerocouriers.license.LicenseRank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * S2C snapshot of the job board (GDD §14). The client never computes eligibility; it just displays what the
 * server sent. {@code board} is one page of open contracts (20); {@code mine} is the viewer's own.
 */
public record BoardSnapshotPayload(
        boolean openScreen,
        int page,
        int boardTotal,
        long balance,
        boolean numismaticsEconomy,
        int mailCount,
        int notificationUnread,
        boolean operator,
        boolean licensed,
        boolean allowSelfAccept,
        boolean requireTablet,
        Optional<Component> notice,
        List<BoardEntry> board,
        List<BoardEntry> mine,
        List<StationHint> stations,
        BoardProfile profile,
        List<AdminActiveEntry> adminActive,
        List<PlayerHint> onlinePlayers,
        List<String> adminConfigLines,
        /** Absolute overworld game time of the next NPC board generation; 0 if disabled. */
        long nextVillageRefreshGameTime,
        /** Overworld game time when this snapshot was built (client countdown baseline). */
        long snapshotOverworldGameTime,
        Extras extras
) implements CustomPacketPayload {
    public static final Type<BoardSnapshotPayload> TYPE = new Type<>(AeronauticsCouriers.id("board_snapshot"));
    public static final int PAGE_SIZE = PayloadLimits.BOARD_PAGE;
    /** Max item stacks synced per board row for crate hover tooltips. */
    public static final int CARGO_STACK_LIMIT = PayloadLimits.CARGO_STACK_SYNC;

    public record CargoLine(int count, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CargoLine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CargoLine::count,
                ByteBufCodecs.STRING_UTF8, CargoLine::name,
                CargoLine::new);

        public static CargoLine of(ItemStack stack) {
            return new CargoLine(stack.getCount(), stack.getHoverName().getString());
        }
    }

    public record StationHint(String name, BlockPos pos, ResourceKey<Level> dimension) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StationHint> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StationHint::name,
                BlockPos.STREAM_CODEC, StationHint::pos,
                ResourceKey.streamCodec(Registries.DIMENSION), StationHint::dimension,
                StationHint::new);
    }

    /** All active/open contracts for the admin «Заказы» sub-tab. */
    public record AdminActiveEntry(
            UUID id,
            String shortId,
            ContractState state,
            String originLabel,
            String destinationLabel,
            String size,
            String courierName,
            ContractType contractType,
            long reward,
            int distance,
            long minutesLeft,
            long collateral,
            long createdAt
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AdminActiveEntry> STREAM_CODEC =
                StreamCodec.of(AdminActiveEntry::write, AdminActiveEntry::read);

        private static void write(RegistryFriendlyByteBuf buf, AdminActiveEntry e) {
            UUIDUtil.STREAM_CODEC.encode(buf, e.id);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.shortId);
            ContractState.STREAM_CODEC.encode(buf, e.state);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.originLabel);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.destinationLabel);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.size);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.courierName);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.contractType.getSerializedName());
            ByteBufCodecs.VAR_LONG.encode(buf, e.reward);
            ByteBufCodecs.VAR_INT.encode(buf, e.distance);
            ByteBufCodecs.VAR_LONG.encode(buf, e.minutesLeft);
            ByteBufCodecs.VAR_LONG.encode(buf, e.collateral);
            ByteBufCodecs.VAR_LONG.encode(buf, e.createdAt);
        }

        private static ContractType decodeContractType(String name) {
            for (ContractType type : ContractType.values()) {
                if (type.getSerializedName().equals(name)) {
                    return type;
                }
            }
            return ContractType.VILLAGE;
        }

        private static AdminActiveEntry read(RegistryFriendlyByteBuf buf) {
            return new AdminActiveEntry(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ContractState.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    decodeContractType(ByteBufCodecs.STRING_UTF8.decode(buf)),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf));
        }
    }

    /**
     * 0.2.0 additions. {@code collateralProfile} drives which collateral elements the client shows; its name is only
     * rendered in the operator panel. {@code economyLines} ({@code key=value}) are filled for operators only.
     */
    public record Extras(
            String collateralProfile,
            List<String> economyLines,
            boolean tutorialEnabled,
            int tutorialState,
            Optional<UUID> tutorialOrderId,
            boolean tutorialNoStation,
            boolean tutorialCompletionPending,
            int tutorialXp,
            int tutorialHintsSeen,
            long licenseRestorePrice,
            boolean licenseRestorable
    ) {
        public static final Extras EMPTY = new Extras(dev.poligorn.aerocouriers.contract.CollateralProfile.NORMAL.key(),
                List.of(), false, 0, Optional.empty(), false, false, 0, 0, 0L, false);

        public static final StreamCodec<RegistryFriendlyByteBuf, Extras> STREAM_CODEC =
                StreamCodec.of(Extras::write, Extras::read);

        public dev.poligorn.aerocouriers.contract.CollateralProfile profile() {
            return dev.poligorn.aerocouriers.contract.CollateralProfile.byKey(collateralProfile);
        }

        public boolean isTutorialOrder(UUID id) {
            return tutorialOrderId.filter(id::equals).isPresent();
        }

        private static void write(RegistryFriendlyByteBuf buf, Extras e) {
            ByteBufCodecs.STRING_UTF8.encode(buf, e.collateralProfile);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(PayloadLimits.ADMIN_CONFIG_LINES))
                    .encode(buf, PayloadLimits.cap(e.economyLines, PayloadLimits.ADMIN_CONFIG_LINES));
            buf.writeBoolean(e.tutorialEnabled);
            buf.writeVarInt(e.tutorialState);
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, e.tutorialOrderId);
            buf.writeBoolean(e.tutorialNoStation);
            buf.writeBoolean(e.tutorialCompletionPending);
            buf.writeVarInt(e.tutorialXp);
            buf.writeVarInt(e.tutorialHintsSeen);
            buf.writeVarLong(e.licenseRestorePrice);
            buf.writeBoolean(e.licenseRestorable);
        }

        private static Extras read(RegistryFriendlyByteBuf buf) {
            return new Extras(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(PayloadLimits.ADMIN_CONFIG_LINES)).decode(buf),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readBoolean());
        }
    }

    public record PlayerHint(UUID id, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerHint> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerHint::id,
                ByteBufCodecs.STRING_UTF8, PlayerHint::name,
                PlayerHint::new);
    }

    /** Courier + issuer stats for the board «Профиль» tab (server is source of truth). */
    public record BoardProfile(
            String playerName,
            LicenseRank rank,
            int delivered,
            int failed,
            long distance,
            long rewardSum,
            int villageDelivered,
            int traineeFreeContracts,
            int traineeMaxDistance,
            int xp,
            int rankXp0,
            int rankXp1,
            int rankXp2,
            int rankXp3,
            int rankXp4,
            int aceMinSuccessPercent,
            boolean licensedCourier,
            int licensesIssued,
            int issuerOrders,
            long issuerRewardSum,
            long issuerWeightTenths,
            int issuerCountS,
            int issuerCountM,
            int issuerCountL,
            int issuerCountXl,
            boolean pilotLicenseHeld
    ) {
        public static final BoardProfile EMPTY = new BoardProfile(
                "", LicenseRank.RANK1, 0, 0, 0L, 0L, 0, 3, 800,
                0, 0, 300, 1500, 5000, 15000, 90,
                false, 0, 0, 0L, 0L, 0, 0, 0, 0, false);

        public List<Integer> rankXpThresholds() {
            return List.of(rankXp0, rankXp1, rankXp2, rankXp3, rankXp4);
        }

        public IssuerStats issuerStats() {
            return new IssuerStats(issuerOrders, issuerRewardSum, issuerWeightTenths,
                    issuerCountS, issuerCountM, issuerCountL, issuerCountXl);
        }

        /** True when the player never received a pilot license (first issue is free from the profile tab). */
        public boolean neverHadPilotLicense() {
            return licensesIssued <= 0 && !licensedCourier && !pilotLicenseHeld;
        }

        public Optional<CargoSize> issuerMostCommonSize() {
            return issuerStats().mostCommonSize();
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, BoardProfile> STREAM_CODEC =
                StreamCodec.of(BoardProfile::write, BoardProfile::read);

        private static void write(RegistryFriendlyByteBuf buf, BoardProfile p) {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.playerName);
            LicenseRank.STREAM_CODEC.encode(buf, p.rank);
            ByteBufCodecs.VAR_INT.encode(buf, p.delivered);
            ByteBufCodecs.VAR_INT.encode(buf, p.failed);
            ByteBufCodecs.VAR_LONG.encode(buf, p.distance);
            ByteBufCodecs.VAR_LONG.encode(buf, p.rewardSum);
            ByteBufCodecs.VAR_INT.encode(buf, p.villageDelivered);
            ByteBufCodecs.VAR_INT.encode(buf, p.traineeFreeContracts);
            ByteBufCodecs.VAR_INT.encode(buf, p.traineeMaxDistance);
            ByteBufCodecs.VAR_INT.encode(buf, p.xp);
            ByteBufCodecs.VAR_INT.encode(buf, p.rankXp0);
            ByteBufCodecs.VAR_INT.encode(buf, p.rankXp1);
            ByteBufCodecs.VAR_INT.encode(buf, p.rankXp2);
            ByteBufCodecs.VAR_INT.encode(buf, p.rankXp3);
            ByteBufCodecs.VAR_INT.encode(buf, p.rankXp4);
            ByteBufCodecs.VAR_INT.encode(buf, p.aceMinSuccessPercent);
            buf.writeBoolean(p.licensedCourier);
            ByteBufCodecs.VAR_INT.encode(buf, p.licensesIssued);
            ByteBufCodecs.VAR_INT.encode(buf, p.issuerOrders);
            ByteBufCodecs.VAR_LONG.encode(buf, p.issuerRewardSum);
            ByteBufCodecs.VAR_LONG.encode(buf, p.issuerWeightTenths);
            ByteBufCodecs.VAR_INT.encode(buf, p.issuerCountS);
            ByteBufCodecs.VAR_INT.encode(buf, p.issuerCountM);
            ByteBufCodecs.VAR_INT.encode(buf, p.issuerCountL);
            ByteBufCodecs.VAR_INT.encode(buf, p.issuerCountXl);
            buf.writeBoolean(p.pilotLicenseHeld);
        }

        private static BoardProfile read(RegistryFriendlyByteBuf buf) {
            return new BoardProfile(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    LicenseRank.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readBoolean());
        }
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
            List<CargoLine> manifest,
            Optional<UUID> customerId,
            String customerName,
            boolean villageCustomer,
            ResourceKey<Level> originDimension,
            BlockPos originPos,
            ResourceKey<Level> destinationDimension,
            BlockPos destinationPos,
            List<ItemStack> cargoStacks,
            int customerStationReputation,
            boolean villageRare,
            int customerReputationTier,
            int customerReputationNext,
            int customerReputationBonusPercent,
            boolean originRevealed,
            boolean crossDimension,
            boolean originStation,
            boolean destinationStation,
            /** Container slot count and weight limit from the server config (client config may differ). */
            int slots,
            int maxWeight,
            double weightValue,
            /** Game minutes since the order was published. */
            long ageMinutes,
            /** Active haul whose container already left the pickup area (stamp/NPC crates are IN_TRANSIT at once). */
            boolean cargoPickedUp
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardEntry> STREAM_CODEC =
                StreamCodec.of(BoardEntry::write, BoardEntry::read);
        /** Wire value of {@link #customerName} for the tutorial order; localised on the client. */
        public static final String COURIER_SERVICE = "@courier_service";

        public boolean courierService() {
            return COURIER_SERVICE.equals(customerName);
        }

        public String customerName() {
            return courierService()
                    ? Component.translatable("ui.aerocouriers.tutorial.customer").getString()
                    : customerName;
        }

        public long rewardPer1000Blocks() {
            return distance <= 0 ? 0 : reward * 1000L / distance;
        }

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
            CargoLine.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.WIRE_ITEM_STACK_LIST))
                    .encode(buf, PayloadLimits.cap(e.manifest, CARGO_STACK_LIMIT));
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, e.customerId);
            ByteBufCodecs.STRING_UTF8.encode(buf, e.customerName);
            buf.writeBoolean(e.villageCustomer);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, e.originDimension);
            BlockPos.STREAM_CODEC.encode(buf, e.originPos);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, e.destinationDimension);
            BlockPos.STREAM_CODEC.encode(buf, e.destinationPos);
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.WIRE_ITEM_STACK_LIST))
                    .encode(buf, PayloadLimits.cap(e.cargoStacks, CARGO_STACK_LIMIT));
            ByteBufCodecs.VAR_INT.encode(buf, e.customerStationReputation);
            buf.writeBoolean(e.villageRare);
            ByteBufCodecs.VAR_INT.encode(buf, e.customerReputationTier);
            ByteBufCodecs.VAR_INT.encode(buf, e.customerReputationNext);
            ByteBufCodecs.VAR_INT.encode(buf, e.customerReputationBonusPercent);
            buf.writeBoolean(e.originRevealed);
            buf.writeBoolean(e.crossDimension);
            buf.writeBoolean(e.originStation);
            buf.writeBoolean(e.destinationStation);
            ByteBufCodecs.VAR_INT.encode(buf, e.slots);
            ByteBufCodecs.VAR_INT.encode(buf, e.maxWeight);
            buf.writeDouble(e.weightValue);
            ByteBufCodecs.VAR_LONG.encode(buf, e.ageMinutes);
            buf.writeBoolean(e.cargoPickedUp);
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
                    CargoLine.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.WIRE_ITEM_STACK_LIST)).decode(buf),
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readBoolean(),
                    ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf),
                    ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.WIRE_ITEM_STACK_LIST)).decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readDouble(),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    buf.readBoolean());
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(BoardSnapshotPayload::write, BoardSnapshotPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, BoardSnapshotPayload p) {
        buf.writeBoolean(p.openScreen);
        buf.writeVarInt(p.page);
        buf.writeVarInt(p.boardTotal);
        buf.writeVarLong(p.balance);
        buf.writeBoolean(p.numismaticsEconomy);
        buf.writeVarInt(p.mailCount);
        buf.writeVarInt(p.notificationUnread);
        buf.writeBoolean(p.operator);
        buf.writeBoolean(p.licensed);
        buf.writeBoolean(p.allowSelfAccept);
        buf.writeBoolean(p.requireTablet);
        buf.writeBoolean(p.notice.isPresent());
        p.notice.ifPresent(component -> ComponentSerialization.STREAM_CODEC.encode(buf, component));
        BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)).encode(buf, PayloadLimits.cap(p.board, PAGE_SIZE));
        BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.BOARD_MINE))
                .encode(buf, PayloadLimits.cap(p.mine, PayloadLimits.BOARD_MINE));
        StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.STATION_HINTS))
                .encode(buf, PayloadLimits.cap(p.stations, PayloadLimits.STATION_HINTS));
        BoardProfile.STREAM_CODEC.encode(buf, p.profile);
        AdminActiveEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.ADMIN_ACTIVE))
                .encode(buf, PayloadLimits.cap(p.adminActive, PayloadLimits.ADMIN_ACTIVE));
        PlayerHint.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.ONLINE_PLAYERS))
                .encode(buf, PayloadLimits.cap(p.onlinePlayers, PayloadLimits.ONLINE_PLAYERS));
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(PayloadLimits.ADMIN_CONFIG_LINES))
                .encode(buf, PayloadLimits.cap(p.adminConfigLines, PayloadLimits.ADMIN_CONFIG_LINES));
        ByteBufCodecs.VAR_LONG.encode(buf, p.nextVillageRefreshGameTime);
        ByteBufCodecs.VAR_LONG.encode(buf, p.snapshotOverworldGameTime);
        Extras.STREAM_CODEC.encode(buf, p.extras);
    }

    private static BoardSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        boolean openScreen = buf.readBoolean();
        int page = buf.readVarInt();
        int boardTotal = buf.readVarInt();
        long balance = buf.readVarLong();
        boolean numismaticsEconomy = buf.readBoolean();
        int mailCount = buf.readVarInt();
        int notificationUnread = buf.readVarInt();
        boolean operator = buf.readBoolean();
        boolean licensed = buf.readBoolean();
        boolean allowSelfAccept = buf.readBoolean();
        boolean requireTablet = buf.readBoolean();
        Optional<Component> notice = buf.readBoolean()
                ? Optional.of(ComponentSerialization.STREAM_CODEC.decode(buf))
                : Optional.empty();
        List<BoardEntry> board = BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)).decode(buf);
        List<BoardEntry> mine = BoardEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.BOARD_MINE)).decode(buf);
        List<StationHint> stations =
                StationHint.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.STATION_HINTS)).decode(buf);
        BoardProfile profile = BoardProfile.STREAM_CODEC.decode(buf);
        List<AdminActiveEntry> adminActive =
                AdminActiveEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.ADMIN_ACTIVE)).decode(buf);
        List<PlayerHint> onlinePlayers =
                PlayerHint.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadLimits.ONLINE_PLAYERS)).decode(buf);
        List<String> adminConfigLines =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(PayloadLimits.ADMIN_CONFIG_LINES)).decode(buf);
        long nextVillageRefreshGameTime = ByteBufCodecs.VAR_LONG.decode(buf);
        long snapshotOverworldGameTime = ByteBufCodecs.VAR_LONG.decode(buf);
        Extras extras = Extras.STREAM_CODEC.decode(buf);
        return new BoardSnapshotPayload(openScreen, page, boardTotal, balance, numismaticsEconomy, mailCount,
                notificationUnread, operator,
                licensed,
                allowSelfAccept, requireTablet, notice,
                board, mine, stations, profile, adminActive, onlinePlayers, adminConfigLines,
                nextVillageRefreshGameTime, snapshotOverworldGameTime, extras);
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

    public Optional<AdminActiveEntry> findAdmin(UUID id) {
        for (AdminActiveEntry entry : adminActive) {
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
