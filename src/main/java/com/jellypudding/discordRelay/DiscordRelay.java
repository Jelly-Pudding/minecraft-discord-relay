package com.jellypudding.discordRelay;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

public class DiscordRelay extends JavaPlugin implements Listener {

    private JDA jda;
    private String discordChannelId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);

        try {
            jda = JDABuilder.createDefault(getConfig().getString("discord-bot-token"))
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new DiscordListener())
                    .build();
            jda.awaitReady();
            getLogger().info("Discord bot connected successfully!");

            // Send server start message
            sendToDiscord("**Server is starting up!**");
        } catch (Exception e) {
            getLogger().severe("Failed to connect to Discord: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            try {
                // Send server stop message synchronously
                TextChannel channel = jda.getTextChannelById(discordChannelId);
                if (channel != null) {
                    channel.sendMessage("**Server is shutting down!**").complete();
                }

                jda.removeEventListener(jda.getRegisteredListeners());
                jda.shutdownNow();
                if (!jda.awaitShutdown(Duration.ofSeconds(5))) {
                    getLogger().warning("JDA did not shut down in time. Forcing shutdown.");
                    jda.shutdown();
                }
                getLogger().info("Discord bot disconnected successfully.");
            } catch (InterruptedException e) {
                getLogger().warning("Interrupted while shutting down JDA: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getLogger().warning("Error during JDA shutdown: " + e.getMessage());
            }
        }
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        discordChannelId = config.getString("discord-channel-id");
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        if (event.isAsynchronous()) {
            String playerName = event.getPlayer().getName();
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            String formattedMessage = String.format("**%s**: %s", playerName, message);
            sendToDiscord(formattedMessage);
        } else {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                String playerName = event.getPlayer().getName();
                String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                String formattedMessage = String.format("**%s**: %s", playerName, message);
                sendToDiscord(formattedMessage);
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String joinMessage = String.format("**%s joined the game**", event.getPlayer().getName());
        sendToDiscord(joinMessage);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String quitMessage = String.format("**%s left the game**", event.getPlayer().getName());
        sendToDiscord(quitMessage);
    }

    private void sendToDiscord(String message) {
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                channel.sendMessage(message).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    private class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getChannel().getId().equals(discordChannelId) && !event.getAuthor().isBot()) {
                Member member = event.getMember();
                String name = (member != null && member.getNickname() != null) ? member.getNickname() : event.getAuthor().getName();
                String message = String.format("§9[Discord] §6%s§f: %s", name, event.getMessage().getContentDisplay());
                Bukkit.getScheduler().runTask(DiscordRelay.this, () ->
                        Bukkit.broadcast(net.kyori.adventure.text.Component.text(message))
                );
            }
        }
    }
}