package com.jellypudding.discordRelay;

import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * API class for other plugins to interact with DiscordRelay.
 */
public class DiscordRelayAPI {

    private static DiscordRelay pluginInstance = null;

    static void initialize(DiscordRelay plugin) {
        pluginInstance = plugin;
    }

    static void shutdown() {
        pluginInstance = null;
    }

    public static void sendPlayerJoin(String playerName) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerJoin(playerName);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerJoin for " + playerName + ")");
        }
    }

    public static void sendPlayerLeave(String playerName) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerLeave(playerName);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerLeave for " + playerName + ")");
        }
    }

    public static void sendPlayerMessage(String playerName, String message) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerMessage(playerName, message);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerMessage for " + playerName + ")");
        }
    }

    public static void sendPlayerDeath(String playerName, String deathMessage) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayPlayerDeath(playerName, deathMessage);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendPlayerDeath for " + playerName + ")");
        }
    }

    public static void sendCustomMessage(String message) {
        if (pluginInstance != null && pluginInstance.isPluginConfigured()) {
            pluginInstance.relayCustomMessage(message);
        } else {
            logWarning("DiscordRelayAPI called while plugin not ready (sendCustomMessage: " + message + ")");
        }
    }

    private static void logWarning(String message) {
        if (pluginInstance != null) {
            pluginInstance.getLogger().warning(message);
        } else {
            Bukkit.getLogger().log(Level.WARNING, "[DiscordRelayAPI] " + message);
        }
    }

    public static boolean isReady() {
        return pluginInstance != null && pluginInstance.isPluginConfigured();
    }
} 