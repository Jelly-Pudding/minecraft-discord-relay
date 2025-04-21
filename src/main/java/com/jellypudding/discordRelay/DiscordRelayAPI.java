package com.jellypudding.discordRelay;

import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * API class for other plugins to interact with DiscordRelay.
 */
public class DiscordRelayAPI {

    private static DiscordRelay pluginInstance = null;

    // Called by DiscordRelay onEnable
    static void initialize(DiscordRelay plugin) {
        pluginInstance = plugin;
    }

    // Called by DiscordRelay onDisable
    static void shutdown() {
        pluginInstance = null;
    }

    /**
     * Sends a player join message to Discord.
     *
     * @param playerName The name of the player who joined.
     */
    public static void sendPlayerJoin(String playerName) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            // Run on Bukkit's main thread if needed, although JDA calls are likely async already.
            // For safety, let's ensure it runs async if not on main thread, or sync if called from main.
            // The send methods queue internally, so direct call should be fine.
            pluginInstance.relayPlayerJoin(playerName);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerJoin for " + playerName + ")");
        }
    }

    /**
     * Sends a player leave message to Discord.
     *
     * @param playerName The name of the player who left.
     */
    public static void sendPlayerLeave(String playerName) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerLeave(playerName);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerLeave for " + playerName + ")");
        }
    }

    /**
     * Sends a player chat message to Discord.
     *
     * @param playerName The name of the player who chatted.
     * @param message    The chat message content.
     */
    public static void sendPlayerMessage(String playerName, String message) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerMessage(playerName, message);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerMessage for " + playerName + ")");
        }
    }

    /**
     * Sends a player death message to Discord.
     *
     * @param playerName   The name of the player who died.
     * @param deathMessage The death message.
     */
    public static void sendPlayerDeath(String playerName, String deathMessage) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerDeath(playerName, deathMessage);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerDeath for " + playerName + ")");
        }
    }

    private static void logWarning(String message) {
        // Avoid logging if plugin instance is null during shutdown sequence
        if (pluginInstance != null) {
            pluginInstance.getLogger().warning(message);
        } else {
            // Fallback logger if needed, though less ideal
            Bukkit.getLogger().log(Level.WARNING, "[DiscordRelayAPI] " + message);
        }
    }

    /**
     * Checks if the DiscordRelay plugin is loaded and configured.
     * @return true if the API is ready to be used, false otherwise.
     */
    public static boolean isReady() {
        return pluginInstance != null && pluginInstance.isPluginConfigured();
    }
} 