package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.contract.ContractState;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.contract.ManifestLimits;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.OpenWaybillCreatePayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Network payload codecs must tolerate oversized cargo lists without disconnecting players. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PayloadCodecGameTests {
    private PayloadCodecGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void boardEntryEncodesHugeManifestWithoutThrow(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        List<BoardSnapshotPayload.CargoLine> manifest = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            manifest.add(new BoardSnapshotPayload.CargoLine(1, "stack-" + i));
            stacks.add(new ItemStack(Items.COBBLESTONE, 1));
        }
        BoardSnapshotPayload.BoardEntry entry = sampleEntry("XL", manifest, stacks);
        BoardSnapshotPayload.BoardEntry.STREAM_CODEC.encode(buf, entry);
        buf.readerIndex(0);
        BoardSnapshotPayload.BoardEntry decoded = BoardSnapshotPayload.BoardEntry.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.manifest().size(), BoardSnapshotPayload.CARGO_STACK_LIMIT,
                "manifest clamped on wire");
        helper.assertValueEqual(decoded.cargoStacks().size(), BoardSnapshotPayload.CARGO_STACK_LIMIT,
                "stacks clamped on wire");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void manifestLimitsRejectHugeCargo(GameTestHelper helper) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            stacks.add(new ItemStack(Items.STONE));
        }
        helper.assertTrue(ManifestLimits.rejectContract(stacks).isPresent(), "200 stacks rejected");
        helper.assertTrue(ManifestLimits.validateMessage(stacks).isPresent(), "validation message");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void boardEntryEncodesXlManifest(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        List<BoardSnapshotPayload.CargoLine> manifest = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            manifest.add(new BoardSnapshotPayload.CargoLine(1, "stack-" + i));
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            stacks.add(new ItemStack(Items.COBBLESTONE, i + 1));
        }
        BoardSnapshotPayload.BoardEntry entry = sampleEntry("XL", manifest, stacks);
        BoardSnapshotPayload.BoardEntry.STREAM_CODEC.encode(buf, entry);
        buf.readerIndex(0);
        BoardSnapshotPayload.BoardEntry decoded = BoardSnapshotPayload.BoardEntry.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.manifest().size(), 40, "manifest round-trip");
        helper.assertValueEqual(decoded.size(), "XL", "size");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void boardProfileEncodesIssuerXlStats(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        BoardSnapshotPayload.BoardProfile profile = new BoardSnapshotPayload.BoardProfile(
                "issuer", LicenseRank.RANK4, 0, 0, 0L, 0L, 0, 3, 800,
                100, 0, 300, 1500, 5000, 15000, 90,
                true, 1, 5, 500L, 120L, 0, 0, 1, 4);
        BoardSnapshotPayload.BoardProfile.STREAM_CODEC.encode(buf, profile);
        buf.readerIndex(0);
        BoardSnapshotPayload.BoardProfile decoded = BoardSnapshotPayload.BoardProfile.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.issuerCountXl(), 4, "issuer xl count");
        helper.assertValueEqual(decoded.issuerMostCommonSize().orElseThrow(), CargoSize.XL, "mode size");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void waybillPreviewEncodesXlCargo(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        List<ItemStack> cargo = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            cargo.add(new ItemStack(Items.IRON_INGOT, 1));
        }
        OpenWaybillCreatePayload payload = new OpenWaybillCreatePayload(
                BlockPos.ZERO, cargo, 128.0, 1000L, false, List.of());
        OpenWaybillCreatePayload.STREAM_CODEC.encode(buf, payload);
        buf.readerIndex(0);
        OpenWaybillCreatePayload decoded = OpenWaybillCreatePayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.cargo().size(), 45, "cargo round-trip");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY)
    public static void waybillPreviewEncodesLargeCargoList(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        List<ItemStack> cargo = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            cargo.add(new ItemStack(Items.IRON_INGOT, 1));
        }
        OpenWaybillCreatePayload payload = new OpenWaybillCreatePayload(
                BlockPos.ZERO, cargo, 200.0, 1000L, false, List.of());
        OpenWaybillCreatePayload.STREAM_CODEC.encode(buf, payload);
        buf.readerIndex(0);
        OpenWaybillCreatePayload decoded = OpenWaybillCreatePayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.cargo().size(), 200, "large cargo round-trip");
        helper.succeed();
    }

    private static BoardSnapshotPayload.BoardEntry sampleEntry(
            String size,
            List<BoardSnapshotPayload.CargoLine> manifest,
            List<ItemStack> stacks
    ) {
        UUID id = UUID.randomUUID();
        return new BoardSnapshotPayload.BoardEntry(
                id,
                "abc123",
                ContractState.OPEN,
                "issuer",
                "Origin",
                "Dest",
                100,
                size,
                "10.0 / 4096",
                50L,
                10L,
                LicenseRank.RANK4,
                60L,
                55L,
                manifest,
                Optional.empty(),
                "issuer",
                false,
                Level.OVERWORLD,
                BlockPos.ZERO,
                Level.OVERWORLD,
                new BlockPos(10, 64, 10),
                stacks,
                0,
                false,
                0,
                -1,
                0,
                true,
                false,
                true,
                false,
                45,
                4096,
                10.0,
                3L,
                false);
    }
}
