package com.yourmord;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class TerminalMod implements ModInitializer {
    public static final String MODID = "terminal";
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalMod.class);
    private static boolean isAndroid = false;
    private static boolean androidWarningIgnored = false;
    @Override
    public void onInitialize() {
        try {
            Class.forName("android.os.Build");
            isAndroid = true;
            LOGGER.warn("Running on Android device - this mod may not work properly.");
        } catch (ClassNotFoundException ignored) {
            LOGGER.info("Not an Android environment.");
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CommandHandler.registerCommands(dispatcher));
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
}