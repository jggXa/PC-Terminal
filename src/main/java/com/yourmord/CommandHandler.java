package com.yourmord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CommandHandler {

    // 固定线程池，避免每次创建新池导致泄漏
    private static final ExecutorService COMMAND_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pc-terminal-cmd");
        t.setDaemon(true);
        return t;
    });

    // 单条命令最长执行时间（秒），与描述一致
    private static final int TIMEOUT_SECONDS = 10;

    // 聊天输出最大行数，防止刷屏
    private static final int MAX_OUTPUT_LINES = 50;

    // 单条消息最大字符数
    private static final int MAX_CHARS_PER_MESSAGE = 10000;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var argumentBuilder = Commands.argument("command", StringArgumentType.greedyString())
                .requires(source -> source.hasPermission(2)) // 仅 OP（权限等级2）可执行
                .executes(this::executeCommand);

        dispatcher.register(Commands.literal("cmd").then(argumentBuilder));
        dispatcher.register(Commands.literal("tty").then(argumentBuilder));
        dispatcher.register(Commands.literal("shell").then(argumentBuilder));
        dispatcher.register(Commands.literal("terminal").then(argumentBuilder));
    }

    private int executeCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String fullCommand = StringArgumentType.getString(context, "command");
        MinecraftServer server = player.server;

        // Android 环境检测与警告（可忽略）
        if (isAndroid()) {
            sendMessage(server, player,
                    Component.literal("[PC Terminal] 检测到 Android 环境（如 PojavLauncher），系统命令可能受限，继续执行...")
                            .withStyle(ChatFormatting.YELLOW));
        }

        // 提交到线程池执行，避免阻塞主线程
        COMMAND_EXECUTOR.submit(() -> {
            Process process = null;
            Future<?> stdoutFuture = null;
            Future<?> stderrFuture = null;
            try {
                String os = System.getProperty("os.name").toLowerCase();
                String[] command;
                if (os.contains("win")) {
                    command = new String[]{"cmd", "/c", fullCommand};
                } else {
                    command = new String[]{"bash", "-c", fullCommand};
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(false);
                process = pb.start();

                // 同时读取 stdout 和 stderr，避免缓冲区满导致死锁
                StringBuilder stdout = new StringBuilder();
                StringBuilder stderr = new StringBuilder();

                InputStream inStream = process.getInputStream();
                InputStream errStream = process.getErrorStream();

                stdoutFuture = COMMAND_EXECUTOR.submit(() -> readStream(inStream, stdout));
                stderrFuture = COMMAND_EXECUTOR.submit(() -> readStream(errStream, stderr));

                // 超时保护：最多等待 TIMEOUT_SECONDS 秒
                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!finished) {
                    // 超时，强制销毁进程
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                    sendMessage(server, player,
                            Component.literal("[PC Terminal] 命令执行超时（>" + TIMEOUT_SECONDS + "秒），已强制终止。")
                                    .withStyle(ChatFormatting.RED));
                    return;
                }

                // 等待输出读取完成
                if (stdoutFuture != null) stdoutFuture.get(2, TimeUnit.SECONDS);
                if (stderrFuture != null) stderrFuture.get(2, TimeUnit.SECONDS);

                int exitCode = process.exitValue();
                String out = stdout.toString().trim();
                String err = stderr.toString().trim();

                if (exitCode == 0) {
                    if (out.isEmpty()) {
                        sendMessage(server, player,
                                Component.literal("[PC Terminal] 命令执行成功，无输出。")
                                        .withStyle(ChatFormatting.GRAY));
                    } else {
                        sendOutput(server, player, out, ChatFormatting.GRAY);
                    }
                } else {
                    if (!err.isEmpty()) {
                        sendMessage(server, player,
                                Component.literal("[PC Terminal] 命令退出码: " + exitCode)
                                        .withStyle(ChatFormatting.RED));
                        sendOutput(server, player, err, ChatFormatting.WHITE);
                    } else if (!out.isEmpty()) {
                        sendMessage(server, player,
                                Component.literal("[PC Terminal] 命令退出码: " + exitCode)
                                        .withStyle(ChatFormatting.RED));
                        sendOutput(server, player, out, ChatFormatting.GRAY);
                    } else {
                        sendMessage(server, player,
                                Component.literal("[PC Terminal] 命令执行失败（退出码: " + exitCode + "），无输出。")
                                        .withStyle(ChatFormatting.RED));
                    }
                }

            } catch (Exception e) {
                sendMessage(server, player,
                        Component.literal("[PC Terminal] 执行异常: " + e.getMessage())
                                .withStyle(ChatFormatting.RED));
                e.printStackTrace();
            } finally {
                // 清理资源
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });

        return 1;
    }

    /**
     * 读取输入流到 StringBuilder
     */
    private void readStream(InputStream stream, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                if (lineCount < MAX_OUTPUT_LINES + 20) { // 多读一点，后面再截断
                    target.append(line).append("\n");
                }
                lineCount++;
            }
        } catch (Exception e) {
            target.append("[读取输出时发生错误: ").append(e.getMessage()).append("]\n");
        }
    }

    /**
     * 发送命令输出，自动截断过长内容并分条发送
     */
    private void sendOutput(MinecraftServer server, ServerPlayer player, String output, ChatFormatting color) {
        String[] lines = output.split("\n", -1);
        StringBuilder current = new StringBuilder();
        int sentLines = 0;

        for (String line : lines) {
            if (sentLines >= MAX_OUTPUT_LINES) {
                current.append("\n... 输出过长，已截断（共 ").append(lines.length).append(" 行） ...");
                break;
            }
            // 单条消息过长时先发送
            if (current.length() + line.length() + 1 > MAX_CHARS_PER_MESSAGE) {
                sendMessage(server, player, Component.literal(current.toString()).withStyle(color));
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append("\n");
            current.append(line);
            sentLines++;
        }

        if (current.length() > 0) {
            sendMessage(server, player, Component.literal(current.toString()).withStyle(color));
        }
    }

    /**
     * 回到主线程发送消息，保证线程安全
     */
    private void sendMessage(MinecraftServer server, ServerPlayer player, Component message) {
        server.execute(() -> player.sendSystemMessage(message));
    }

    /**
     * 检测是否运行在 Android 环境
     */
    private boolean isAndroid() {
        try {
            // Android 系统属性检测
            String javaVendor = System.getProperty("java.vendor", "");
            String osName = System.getProperty("os.name", "");
            String javaVmName = System.getProperty("java.vm.name", "");
            return javaVendor.toLowerCase().contains("android")
                    || osName.toLowerCase().contains("android")
                    || javaVmName.toLowerCase().contains("dalvik")
                    || javaVmName.toLowerCase().contains("art");
        } catch (Exception e) {
            return false;
        }
    }
}
