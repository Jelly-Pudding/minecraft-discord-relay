package com.jellypudding.discordRelay.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerUtil {

    public static UUID getPlayerUUIDExactMatch(String playerName) {
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null && onlinePlayer.getName().equals(playerName)) {
            return onlinePlayer.getUniqueId();
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                String actualName = offlinePlayer.getName();
                if (actualName != null && actualName.equals(playerName)) {
                    return offlinePlayer.getUniqueId();
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }
}
