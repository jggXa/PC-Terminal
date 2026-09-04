package com.yourmord;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public class TerminalClientMod implements ClientModInitializer {
    private static boolean warned = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!warned && client.player != null && TerminalMod.isAndroid() && !TerminalMod.isAndroidWarningIgnored()) {
                warned = true; // 仅发送一次
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
                client.player.sendSystemMessage(mainMsg);
                client.player.sendSystemMessage(clickableHint);
            }
        });
    }
}