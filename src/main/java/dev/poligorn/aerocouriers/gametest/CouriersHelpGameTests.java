package dev.poligorn.aerocouriers.gametest;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.command.CouriersCommands;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@code /couriers} and {@code /couriers help} list only the subcommands the source may run. */
@GameTestHolder(AeronauticsCouriers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouriersHelpGameTests {
    private CouriersHelpGameTests() {
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.commands.help")
    public static void helpFiltersOperatorCommands(GameTestHelper helper) {
        ServerPlayer courier = TestPlayers.courier(helper, "help_player");
        CommandSourceStack player = courier.createCommandSourceStack().withPermission(0);
        List<String> visible = CouriersCommands.visibleSubcommands(player);
        helper.assertTrue(visible.contains("profile"), "player sees profile");
        helper.assertTrue(visible.contains("ui"), "player sees ui");
        helper.assertTrue(visible.contains("help"), "player sees help");
        helper.assertFalse(visible.contains("admin"), "player does not see admin");
        helper.assertFalse(visible.contains("license"), "player does not see license");
        helper.assertFalse(visible.contains("money"), "player does not see money");

        CommandSourceStack op = courier.createCommandSourceStack().withPermission(2);
        List<String> opVisible = CouriersCommands.visibleSubcommands(op);
        helper.assertTrue(opVisible.contains("profile"), "op still sees profile");
        helper.assertTrue(opVisible.contains("admin"), "op sees admin");
        helper.assertTrue(opVisible.contains("license"), "op sees license");
        helper.succeed();
    }

    @GameTest(template = CouriersGameTests.EMPTY, batch = "aerocouriers.commands.help")
    public static void couriersAndHelpRun(GameTestHelper helper) {
        ServerPlayer courier = TestPlayers.courier(helper, "help_run");
        CommandSourceStack source = courier.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        var commands = helper.getLevel().getServer().getCommands();
        helper.assertTrue(commands.performPrefixedCommand(source, "couriers") > 0, "/couriers with no args");
        helper.assertTrue(commands.performPrefixedCommand(source, "couriers help") > 0, "/couriers help");
        helper.succeed();
    }
}
