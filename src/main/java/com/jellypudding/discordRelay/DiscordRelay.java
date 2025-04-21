package com.jellypudding.discordRelay;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
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

public class DiscordRelay extends JavaPlugin implements Listener {

    private JDA jda;
    private String discordChannelId;
    private boolean isConfigured = false;
    private long startTime;

    /**
     * Public getter for the configuration status.
     * @return true if the plugin's config seems valid, false otherwise.
     */
    public boolean isPluginConfigured() {
        return isConfigured;
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        String token = config.getString("discord-bot-token");
        discordChannelId = config.getString("discord-channel-id");

        isConfigured = token != null && !token.equals("YOUR_BOT_TOKEN_HERE") &&
                discordChannelId != null && !discordChannelId.equals("YOUR_CHANNEL_ID_HERE");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        startTime = System.currentTimeMillis();
        if (isConfigured) {
            initializePlugin(false);
            DiscordRelayAPI.initialize(this);
        } else {
            getLogger().warning("The Discord bot is not yet configured. Please check your DiscordRelay/config.yml file.");
        }
    }

    private void initializePlugin(boolean isReload) {
        connectToDiscord(isReload);
        if (isConfigured) {
            registerListeners();
            Objects.requireNonNull(getCommand("discordrelay")).setTabCompleter(this);
        }
    }

    private void registerListeners() {
        HandlerList.unregisterAll((JavaPlugin) this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void connectToDiscord(boolean isReload) {
        try {
            if (jda != null) {
                jda.shutdown();
                jda = null;
            }
            jda = JDABuilder.createDefault(getConfig().getString("discord-bot-token"))
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new DiscordListener())
                    .build();
            jda.awaitReady();

            // Register the slash commands
            jda.updateCommands().addCommands(
                    Commands.slash("list", "Get a list of online players"),
                    Commands.slash("uptime", "Get the server uptime")
            ).queue();

            getLogger().info("Discord bot connected successfully!");

            if (!isReload) {
                sendToDiscord("**Server is starting up!**");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to connect to Discord. Please check your bot token and try again.");
            isConfigured = false;
        }
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            try {
                sendToDiscord("**Server is shutting down!**");

                jda.removeEventListener(jda.getRegisteredListeners());
                jda.shutdownNow();
                try {
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
        DiscordRelayAPI.shutdown();
        HandlerList.unregisterAll((JavaPlugin) this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!isConfigured) return;

        if (event.isCancelled()) return;

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
        if (!isConfigured) return;

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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String playerName = event.getEntity().getName();
        String deathMessage = event.deathMessage() != null
                ? PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(event.deathMessage()))
                : playerName + " died";
        sendDeathMessageToDiscord(playerName, deathMessage);
    }

    private void sendDeathMessageToDiscord(String playerName, String deathMessage) {
        if (!isConfigured) return;

        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(deathMessage, null, avatarUrl)
                        .setColor(Color.GRAY);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    private void sendToDiscord(String message) {
        if (!isConfigured) return;

        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                channel.sendMessage(message).complete();
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
        loadConfig();
        if (isConfigured) {
            initializePlugin(true);
            if (jda != null) {
                getLogger().info("DiscordRelay plugin reloaded successfully.");
            } else {
                getLogger().warning("Failed to connect to Discord after reload. Please check your bot token and try again.");
            }
        } else {
            if (jda != null) {
                jda.shutdown();
                jda = null;
            }
            HandlerList.unregisterAll((JavaPlugin) this);
            getLogger().warning("Failed to reload: Discord bot is not configured properly. Please check your config.yml file.");
        }
    }

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
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (event.getName().equals("list")) {
                event.deferReply().queue(); // Acknowledge the command immediately
                sendPlayerList(event);
            } else if (event.getName().equals("uptime")) {
                event.deferReply().queue(); // Acknowledge the command immediately
                sendUptime(event);
            }
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getChannel().getId().equals(discordChannelId) && !event.getAuthor().isBot()) {
                Member member = event.getMember();
                String name = (member != null && member.getNickname() != null) ? member.getNickname() : event.getAuthor().getName();
                String message = String.format("§9[Discord] §6%s§f: %s", name, event.getMessage().getContentDisplay());

                // Forward to FakePlayers if enabled
                //if (Bukkit.getPluginManager().isPluginEnabled("FakePlayers")) {
                //    try {
                //        // Use the fully qualified name to avoid import if FakePlayers is optional
                //        com.jellypudding.fakePlayers.FakePlayersAPI.addExternalMessage(message);
                //    } catch (NoClassDefFoundError e) {
                //        // This might happen if FakePlayers is removed without restarting/reloading
                //        getLogger().warning("Could not forward Discord message to FakePlayers. Is it installed and enabled correctly?");
                //    } catch (Exception e) {
                //        getLogger().warning("Error forwarding Discord message to FakePlayers: " + e.getMessage());
                //        // Log the stack trace for detailed debugging if needed
                //        // e.printStackTrace();
                //    }
                //}

                // Broadcast to Minecraft server
                Bukkit.getScheduler().runTask(DiscordRelay.this, () ->
                        Bukkit.broadcast(net.kyori.adventure.text.Component.text(message))
                );
            }
        }

        private void sendPlayerList(SlashCommandInteractionEvent event) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            String playerListString = playerNames.isEmpty() ? "No players online." : String.join(", ", playerNames);
            String message = String.format("Online players (%d): %s", playerNames.size(), playerListString);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Player List")
                    .setDescription(message)
                    .setColor(Color.BLUE);

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }

        private void sendUptime(SlashCommandInteractionEvent event) {
            long uptime = System.currentTimeMillis() - startTime;
            long days = uptime / (1000 * 60 * 60 * 24);
            long hours = (uptime / (1000 * 60 * 60)) % 24;
            long minutes = (uptime / (1000 * 60)) % 60;
            long seconds = (uptime / 1000) % 60;

            String uptimeString = String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Server Uptime")
                    .setDescription(uptimeString)
                    .setColor(Color.GREEN);

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }
    }

    // --- Public API Methods ---

    /** For DiscordRelayAPI: Relays player join event */
    public void relayPlayerJoin(String playerName) {
        if (!isConfigured) return;
        sendPlayerEventToDiscord(playerName, "joined the game", Color.GREEN);
    }

    /** For DiscordRelayAPI: Relays player leave event */
    public void relayPlayerLeave(String playerName) {
        if (!isConfigured) return;
        sendPlayerEventToDiscord(playerName, "left the game", Color.RED);
    }

    /** For DiscordRelayAPI: Relays player chat message */
    public void relayPlayerMessage(String playerName, String message) {
        if (!isConfigured) return;
        sendPlayerMessageToDiscord(playerName, message);
    }

    /** For DiscordRelayAPI: Relays player death message */
    public void relayPlayerDeath(String playerName, String deathMessage) {
        if (!isConfigured) return;
        sendDeathMessageToDiscord(playerName, deathMessage);
    }

    // --- End Public API Methods ---
}