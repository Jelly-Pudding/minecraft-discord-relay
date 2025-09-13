package com.jellypudding.discordRelay.utils;

import com.jellypudding.chromaTag.ChromaTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChromaTagUtil {

    private ChromaTag chromaTag;
    private final Logger logger;

    private final Map<String, CachedComponent> componentCache = new ConcurrentHashMap<>();
    // 20 minute cache.
    private static final long CACHE_DURATION = 1200000;

    public ChromaTagUtil(Logger logger) {
        this.logger = logger;
        setupChromaTag();
    }

    public void setupChromaTag() {
        Plugin chromaTagPlugin = Bukkit.getPluginManager().getPlugin("ChromaTag");
        if (chromaTagPlugin instanceof ChromaTag && chromaTagPlugin.isEnabled()) {
            this.chromaTag = (ChromaTag) chromaTagPlugin;
            logger.info("Successfully hooked into ChromaTag.");
        } else {
            logger.info("ChromaTag plugin not found or not enabled. Player colours will not be displayed in in-game Discord relay messages.");
            this.chromaTag = null;
        }
    }


    public Component getColoredPlayerNameComponent(String playerName) {
        // Check cache first
        CachedComponent cached = componentCache.get(playerName);
        if (cached != null && !cached.isExpired()) {
            return cached.component;
        }

        Component result = generateColoredPlayerNameComponent(playerName);
        
        // Cache the result
        componentCache.put(playerName, new CachedComponent(result, System.currentTimeMillis()));
        
        return result;
    }
    
    private Component generateColoredPlayerNameComponent(String playerName) {
        if (chromaTag == null) {
            return Component.text(playerName).color(NamedTextColor.GRAY);
        }

        UUID playerUUID = PlayerUtil.getPlayerUUIDExactMatch(playerName);
        if (playerUUID == null) {
            return Component.text(playerName).color(NamedTextColor.GRAY);
        }

        TextColor playerColor;
        try {
            playerColor = chromaTag.getPlayerColor(playerUUID);
        } catch (Exception e) {
            return Component.text(playerName).color(NamedTextColor.GRAY);
        }

        if (playerColor == null) {
            return Component.text(playerName).color(NamedTextColor.GRAY);
        }

        return Component.text(playerName).color(playerColor);
    }


    public boolean isChromaTagAvailable() {
        return chromaTag != null;
    }

    public void refresh() {
        setupChromaTag();
        componentCache.clear();
    }

    public void cleanupCache() {
        componentCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private static class CachedComponent {
        final Component component;
        final long timestamp;

        CachedComponent(Component component, long timestamp) {
            this.component = component;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }
}
