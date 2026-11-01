package dev.poligorn.aerocouriers.gametest;

import com.mojang.authlib.GameProfile;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.net.NameStationPayload;
import dev.poligorn.aerocouriers.net.OpenStationNamePayload;
import dev.poligorn.aerocouriers.net.StationPackets;
import dev.poligorn.aerocouriers.registry.ModBlocks;
import dev.poligorn.aerocouriers.registry.ModItems;
import dev.poligorn.aerocouriers.village.PostStationBlock;
import dev.poligorn.aerocouriers.village.PostStationBlockEntity;
import dev.poligorn.aerocouriers.village.StationAtlas;
import dev.poligorn.aerocouriers.village.StationNamePending;
import dev.poligorn.aerocouriers.village.VillageAdmin;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationNameGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos B = new BlockPos(5, 1, 5);

    private StationNameGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.station.name")
    public static void namePayloadRoundTrip(GameTestHelper helper) {
        OpenStationNamePayload open = new OpenStationNamePayload(new BlockPos(3, 4, 5),
                List.of("Ясная Поляна", "Тихая Гавань"));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        OpenStationNamePayload.STREAM_CODEC.encode(buf, open);
        buf.readerIndex(0);
        OpenStationNamePayload back = OpenStationNamePayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(back.pos(), open.pos(), "pos");
        helper.assertTrue(back.nameTaken("ясная поляна"), "taken ignore case");
        helper.assertFalse(back.nameTaken("Лунная Поляна"), "free");
        NameStationPayload name = new NameStationPayload(new BlockPos(1, 2, 3), "Сосновый Бор");
        var raw = Unpooled.buffer();
        NameStationPayload.STREAM_CODEC.encode(raw, name);
        NameStationPayload named = NameStationPayload.STREAM_CODEC.decode(raw);
        helper.assertValueEqual(named.name(), "Сосновый Бор", "name");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.station.name")
    public static void nameCharsEnforcedOnBothPaths(GameTestHelper helper) {
        helper.assertTrue(VillageBoardPolicy.hasValidNameChars("Ясная Поляна"), "cyrillic+space");
        helper.assertTrue(VillageBoardPolicy.hasValidNameChars("North-Dock 2"), "latin hyphen digit");
        helper.assertFalse(VillageBoardPolicy.hasValidNameChars("Hello!"), "punctuation");
        helper.assertFalse(VillageBoardPolicy.hasValidNameChars("a_b"), "underscore");
        helper.assertFalse(VillageBoardPolicy.hasValidNameChars(""), "empty");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.station.name.place", timeoutTicks = 80)
    public static void placerCanNameOtherPlayerCannot(GameTestHelper helper) {
        StationNamePending.clear();
        stoneFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(A);
        ServerPlayer placer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "placer"));
        ServerPlayer other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "other"));
        helper.assertTrue(placeStation(helper, abs, placer), "placed");
        helper.assertTrue(StationNamePending.isPending(level, abs), "window after place");
        helper.assertTrue(StationNamePending.canName(placer, abs), "placer accepted");
        helper.assertFalse(StationNamePending.canName(other, abs), "other rejected");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockEntity(abs) instanceof PostStationBlockEntity be && be.stationId() != null,
                        "station loaded"))
                .thenExecute(() -> {
                    PostStationBlockEntity be = (PostStationBlockEntity) level.getBlockEntity(abs);
                    String before = be.name();
                    StationPackets.handleName(other, new NameStationPayload(abs, "Чужая Гавань"));
                    helper.assertValueEqual(((PostStationBlockEntity) level.getBlockEntity(abs)).name(), before,
                            "other cannot rename");
                    StationPackets.handleName(placer, new NameStationPayload(abs, "Hello!"));
                    helper.assertValueEqual(((PostStationBlockEntity) level.getBlockEntity(abs)).name(), before,
                            "invalid charset rejected");
                    StationPackets.handleName(placer, new NameStationPayload(abs, "Лунная Поляна"));
                    helper.assertValueEqual(((PostStationBlockEntity) level.getBlockEntity(abs)).name(),
                            "Лунная Поляна", "placer rename");
                    helper.assertFalse(StationNamePending.isPending(level, abs), "window closed after save");
                    StationNamePending.clear();
                    StationAtlas.get(level.getServer()).byPos(level.dimension(), abs)
                            .ifPresent(s -> StationAtlas.get(level.getServer()).remove(s.id()));
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.station.name.village", timeoutTicks = 80)
    public static void villageStationGetsNoNamingWindow(GameTestHelper helper) {
        StationNamePending.clear();
        stoneFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(A);
        PostStationBlockEntity.placingVillage(() -> {
            BlockState state = ModBlocks.POST_STATION.get().defaultBlockState();
            level.setBlock(abs, state, 3);
            ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "villager"));
            ModBlocks.POST_STATION.get().setPlacedBy(level, abs, state, player, ItemStack.EMPTY);
        });
        helper.assertFalse(StationNamePending.isPending(level, abs), "village place does not open a window");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockEntity(abs) instanceof PostStationBlockEntity be && be.stationId() != null,
                        "village station loaded"))
                .thenExecute(() -> {
                    helper.assertTrue(!((PostStationBlockEntity) level.getBlockEntity(abs)).name().isEmpty(),
                            "random name assigned");
                    helper.assertFalse(StationNamePending.isPending(level, abs), "still no window");
                    StationNamePending.clear();
                    StationAtlas.get(level.getServer()).byPos(level.dimension(), abs)
                            .ifPresent(s -> StationAtlas.get(level.getServer()).remove(s.id()));
                })
                .thenSucceed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.station.name.setblock")
    public static void setBlockGetsNoNamingWindow(GameTestHelper helper) {
        StationNamePending.clear();
        stoneFloor(helper);
        helper.setBlock(A, ModBlocks.POST_STATION.get());
        helper.assertFalse(StationNamePending.isPending(helper.getLevel(), helper.absolutePos(A)),
                "/setblock does not call setPlacedBy");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.station.name")
    public static void adminRenameAlsoRejectsBadChars(GameTestHelper helper) {
        stoneFloor(helper);
        helper.setBlock(A, ModBlocks.POST_STATION.get());
        helper.setBlock(B, ModBlocks.POST_STATION.get());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(StationAtlas.get(helper.getLevel().getServer())
                            .byPos(helper.getLevel().dimension(), helper.absolutePos(A)).isPresent(), "A ready");
                    helper.assertTrue(StationAtlas.get(helper.getLevel().getServer())
                            .byPos(helper.getLevel().dimension(), helper.absolutePos(B)).isPresent(), "B ready");
                })
                .thenExecute(() -> {
                    StationAtlas.Station first = StationAtlas.get(helper.getLevel().getServer())
                            .byPos(helper.getLevel().dimension(), helper.absolutePos(A)).orElseThrow();
                    String original = first.name();
                    VillageAdmin.rename(helper.getLevel().getServer(), first, "Nope!");
                    helper.assertValueEqual(StationAtlas.get(helper.getLevel().getServer()).byId(first.id())
                            .orElseThrow().name(), original, "charset rejected on admin path");
                    StationAtlas.get(helper.getLevel().getServer()).remove(first.id());
                    StationAtlas.get(helper.getLevel().getServer())
                            .byPos(helper.getLevel().dimension(), helper.absolutePos(B))
                            .ifPresent(s -> StationAtlas.get(helper.getLevel().getServer()).remove(s.id()));
                })
                .thenSucceed();
    }

    private static void stoneFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static boolean placeStation(GameTestHelper helper, BlockPos absLower, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        player.setYRot(180.0F);
        player.setPos(Vec3.atCenterOf(absLower.relative(Direction.NORTH, 2)));
        ItemStack stack = new ItemStack(ModItems.POST_STATION.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absLower.below()).add(0, 0.51, 0),
                Direction.UP, absLower.below(), false);
        BlockPlaceContext ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        BlockState state = ModBlocks.POST_STATION.get().getStateForPlacement(ctx);
        if (state == null || !level.setBlock(absLower, state, 3)) {
            return false;
        }
        ModBlocks.POST_STATION.get().setPlacedBy(level, absLower, state, player, stack);
        return PostStationBlock.isController(level.getBlockState(absLower));
    }
}
