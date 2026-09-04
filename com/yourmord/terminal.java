package com.yourmord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("terminal")  // 直接写 "terminal"
public class terminal {
    public static final String MODID = "terminal";  // 改为 terminal
    private static final Logger LOGGER = LoggerFactory.getLogger(terminal.class);
    private static boolean isAndroid = false;
    private static boolean androidWarningIgnored = false;

    public terminal() {
        try {
            Class.forName("android.os.Build");
            isAndroid = true;
            LOGGER.warn("Running on Android device - this mod may not work properly.");
        } catch (ClassNotFoundException ignored) {
            LOGGER.info("Not an Android environment.");
        }

        NeoForge.EVENT_BUS.register(new CommandHandler());

        if (isAndroid) {
            NeoForge.EVENT_BUS.register(this);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(CommandHandler::shutdownExecutor));
    }

    public static boolean isAndroid() {
        return isAndroid;
    }

    public static void setAndroidWarningIgnored(boolean ignored) {
        androidWarningIgnored = ignored;
    }

    public static boolean isAndroidWarningIgnored() {
        return androidWarningIgnored;
    }

    @SubscribeEvent
    public void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (isAndroid && !androidWarningIgnored && event.getPlayer() != null) {
            Component mainMsg = Component.literal("此模组在 Android 设备上可能无法正常工作。")
                    .withStyle(ChatFormatting.RED);
            Component clickableHint = Component.literal("[点击此处忽略此警告]")
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ignoreandroid")
                    ))
                    .withStyle(style -> style.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击后不再显示此警告"))
                    ));
            event.getPlayer().sendSystemMessage(mainMsg);
            event.getPlayer().sendSystemMessage(clickableHint);
        }
    }
}