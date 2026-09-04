//neoforge1.21.1
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class CommandHandler {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 10000;
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var execBuilder = Commands.argument("command", StringArgumentType.greedyString())
                .executes(this::executeCommand);
        dispatcher.register(Commands.literal("cmd").then(execBuilder));
        dispatcher.register(Commands.literal("tty").then(execBuilder));
        dispatcher.register(Commands.literal("shell").then(execBuilder));
        dispatcher.register(Commands.literal("terminal").then(execBuilder));

        dispatcher.register(Commands.literal("ignoreandroid")
                .executes(ctx -> {
                    terminal.setAndroidWarningIgnored(true);
                    ctx.getSource().sendSuccess(() -> Component.literal("已忽略 Android 兼容性警告"), false);
                    return 1;
                })
        );
    }
    private int executeCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String fullCommand = StringArgumentType.getString(context, "command");

        if (terminal.isAndroid() && !terminal.isAndroidWarningIgnored()) {
            player.sendSystemMessage(Component.literal("⚠️ Android 环境下执行系统命令可能存在风险，请谨慎使用。输入 /ignoreandroid 可关闭此提醒。")
                    .withStyle(ChatFormatting.YELLOW));
        }
        String lower = fullCommand.toLowerCase();
        if (lower.contains("rm -rf") || lower.contains("del /f") || lower.contains("format") || lower.contains("shutdown")) {
            player.sendSystemMessage(Component.literal("该命令包含危险操作，已被禁止执行").withStyle(ChatFormatting.RED));
            return 1;
        }
        EXECUTOR.submit(() -> {
            Process process = null;
            try {
                String os = System.getProperty("os.name").toLowerCase();
                String[] command;
                if (os.contains("win")) {
                    command = new String[]{"cmd", "/c", fullCommand};
                } else {
                    String shell = detectShell();
                    if (shell == null) {
                        player.sendSystemMessage(Component.literal("未找到可用的 Shell（bash/sh）").withStyle(ChatFormatting.RED));
                        return;
                    }
                    command = new String[]{shell, "-c", fullCommand};
                }
                process = Runtime.getRuntime().exec(command);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    StringBuilder output = new StringBuilder();
                    StringBuilder error = new StringBuilder();
                    String line;

                    boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        player.sendSystemMessage(Component.literal("命令执行超时（" + TIMEOUT_SECONDS + " 秒）").withStyle(ChatFormatting.RED));
                        return;
                    }
                    while ((line = reader.readLine()) != null) {
                        if (output.length() + line.length() + 1 < MAX_OUTPUT_LENGTH) {
                            output.append(line).append("\n");
                        } else {
                            output.append("... (输出过长已截断)");
                            break;
                        }
                    }
                    while ((line = errorReader.readLine()) != null) {
                        if (error.length() + line.length() + 1 < MAX_OUTPUT_LENGTH) {
                            error.append(line).append("\n");
                        } else {
                            error.append("... (错误输出过长已截断)");
                            break;
                        }
                    }
                    int exitCode = process.exitValue();
                    if (exitCode == 0) {
                        String result = output.toString().trim();
                        if (result.isEmpty()) {
                            player.sendSystemMessage(Component.translatable("command.terminal.success.no_output"));
                        } else {
                            player.sendSystemMessage(Component.literal(result).withStyle(ChatFormatting.GRAY));
                        }
                    } else {
                        String errorMsg = error.toString().trim();
                        if (!errorMsg.isEmpty()) {
                            player.sendSystemMessage(Component.literal("错误: " + errorMsg).withStyle(ChatFormatting.WHITE));
                        } else {
                            player.sendSystemMessage(Component.translatable("command.terminal.error.general"));
                        }
                    }
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.translatable("command.terminal.error.general"));
                e.printStackTrace();
            } finally {
                if (process != null) process.destroy();
            }
        });

        return 1;
    }
    private String detectShell() {
        String[] candidates = {"/bin/bash", "/usr/bin/bash", "/system/bin/sh", "/bin/sh", "sh", "bash"};
        for (String shell : candidates) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{shell, "-c", "echo ok"});
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return shell;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
    public static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
    }
}
