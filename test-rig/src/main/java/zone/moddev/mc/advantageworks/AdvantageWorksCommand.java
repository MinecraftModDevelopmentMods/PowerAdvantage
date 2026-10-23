package zone.moddev.mc.advantageworks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Brigadier command surface used by both human operators and the runner. */
final class AdvantageWorksCommand {
    private static final Logger LOGGER = LogManager.getLogger(AdvantageWorksMod.MODID);

    private AdvantageWorksCommand() {}

    static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("advworks")
                .requires(source -> source.hasPermissionLevel(2))
                .then(targetCommand("build"))
                .then(targetCommand("reset"))
                .then(targetCommand("start"))
                .then(targetCommand("stop"))
                .then(targetCommand("check"))
                .then(targetCommand("status"))
                .then(Commands.literal("checkpoint")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> execute(context.getSource(), "checkpoint",
                                                StringArgumentType.getString(context, "target"),
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("sample-worldgen")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                .executes(context -> sampleWorldgen(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> targetCommand(
            String operation) {
        return Commands.literal(operation)
                .executes(context -> execute(context.getSource(), operation, "all", null))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(context -> execute(context.getSource(), operation,
                                StringArgumentType.getString(context, "target"), null)));
    }

    private static int execute(CommandSource source, String operation, String target, String name) {
        try {
            WorksController controller = WorksController.getInstance();
            controller.requireAuthorizedWorld(source.getServer());
            String message;
            switch (operation) {
                case "build": message = controller.build(source.getServer(), target); break;
                case "reset": message = controller.reset(source.getServer(), target); break;
                case "start": message = controller.start(source.getServer(), target); break;
                case "stop": message = controller.stop(source.getServer(), target); break;
                case "check": message = controller.check(source.getServer(), target); break;
                case "status": message = controller.status(target); break;
                case "checkpoint": message = controller.checkpoint(source.getServer(), target, name); break;
                default: throw new IllegalArgumentException("Unknown Advantage Works operation " + operation);
            }
            source.sendFeedback(new TextComponentString(message), false);
            return 1;
        } catch (RuntimeException ex) {
            LOGGER.error("Advantage Works command failed: {} {}", operation, target, ex);
            throw ex;
        }
    }

    private static int sampleWorldgen(CommandSource source, int radius) {
        WorksController controller = WorksController.getInstance();
        controller.requireAuthorizedWorld(source.getServer());
        source.sendFeedback(new TextComponentString(controller.sampleWorldgen(source.getServer(), radius)), false);
        return 1;
    }
}
