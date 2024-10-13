package com.jellypudding.discordRelay;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DiscordRelay extends JavaPlugin implements Listener, TabCompleter {

    private JDA jda;
    private String discordChannelId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        connectToDiscord(false);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("discordrelay")).setTabCompleter(this);
    }

    private void connectToDiscord(boolean isReload) {
        try {
            jda = JDABuilder.createDefault(getConfig().getString("discord-bot-token"))
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new DiscordListener())
                    .build();
            jda.awaitReady();
            getLogger().info("Discord bot connected successfully!");

            if (!isReload) {
                sendToDiscord("**Server is starting up!**");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to connect to Discord: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            try {
                // Send shutdown message synchronously
                TextChannel channel = jda.getTextChannelById(discordChannelId);
                if (channel != null) {
                    channel.sendMessage("**Server is shutting down!**").complete();
                }

                jda.removeEventListener(jda.getRegisteredListeners());
                jda.shutdownNow();
                try {
                    // Wait for shutdown with a shorter timeout
                    if (!jda.awaitShutdown(Duration.ofSeconds(2))) {
                        getLogger().warning("JDA did not shut down in time. Forcing shutdown.");
                        jda.shutdown();
                    }
                } catch (InterruptedException e) {
                    getLogger().warning("Interrupted while shutting down JDA. Forcing shutdown.");
                    jda.shutdown();
                    Thread.currentThread().interrupt();
                }
                getLogger().info("Discord bot disconnected successfully.");
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
            sendPlayerMessageToDiscord(playerName, message);
        } else {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                String playerName = event.getPlayer().getName();
                String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                sendPlayerMessageToDiscord(playerName, message);
            });
        }
    }

    private void sendPlayerMessageToDiscord(String playerName, String message) {
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(playerName, null, avatarUrl)
                        .setDescription(message)
                        .setColor(Color.YELLOW);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    private void sendPlayerEventToDiscord(String playerName, String action, Color color) {
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(playerName + " " + action, null, avatarUrl)
                        .setColor(color);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        sendPlayerEventToDiscord(playerName, "joined the game", Color.GREEN);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        sendPlayerEventToDiscord(playerName, "left the game", Color.RED);
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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("discordrelay")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("discordrelay.reload")) {
                    reloadPlugin();
                    sender.sendMessage("DiscordRelay plugin reloaded.");
                    return true;
                } else {
                    sender.sendMessage("You don't have permission to reload the plugin.");
                    return true;
                }
            }
        }
        return false;
    }

    private void reloadPlugin() {
        if (jda != null) {
            jda.shutdown();
        }
        reloadConfig();
        loadConfig();
        connectToDiscord(true);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("discordrelay")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("reload");
                return completions;
            }
        }
        return null;
    }

    private class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getChannel().getId().equals(discordChannelId) && !event.getAuthor().isBot()) {
                String content = event.getMessage().getContentRaw();
                if (content.equalsIgnoreCase("/list")) {
                    sendPlayerList(event.getChannel());
                } else {
                    Member member = event.getMember();
                    String name = (member != null && member.getNickname() != null) ? member.getNickname() : event.getAuthor().getName();
                    String message = String.format("§9[Discord] §6%s§f: %s", name, event.getMessage().getContentDisplay());
                    Bukkit.getScheduler().runTask(DiscordRelay.this, () ->
                            Bukkit.broadcast(net.kyori.adventure.text.Component.text(message))
                    );
                }
            }
        }

        private void sendPlayerList(MessageChannel channel) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            String playerListString = playerNames.isEmpty() ? "No players online." : String.join(", ", playerNames);
            String message = String.format("Online players (%d): %s", playerNames.size(), playerListString);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Player List")
                    .setDescription(message)
                    .setColor(Color.BLUE);

            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }
}