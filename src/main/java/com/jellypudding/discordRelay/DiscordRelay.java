package com.jellypudding.discordRelay;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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

import com.jellypudding.discordRelay.utils.ChromaTagUtil;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DiscordRelay extends JavaPlugin implements Listener {

    private JDA jda;
    private String discordChannelId;
    private boolean isConfigured = false;
    private long startTime;
    private ChromaTagUtil chromaTagUtil;

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

        chromaTagUtil = new ChromaTagUtil(getLogger());

        // Start cache cleanup task (runs every 20 minutes)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            chromaTagUtil.cleanupCache();
        }, 24000L, 24000L);

        if (isConfigured) {
            initializePlugin(false);
            DiscordRelayAPI.initialize(this);
        } else {
            getLogger().warning("The Discord bot is not yet configured. Please check your DiscordRelay/config.yml file and then use the /discordrelay reload command.");
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
                    Commands.slash("uptime", "Get the server's uptime"),
                    Commands.slash("tps", "Get the server's TPS (ticks per second)"),
                    Commands.slash("firstseen", "Check when a player first joined the server")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("lastseen", "Check when a player was last seen on the server")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("timeplayed", "Check how long a player has played on the server")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("chatter", "Check how many chat messages a player has sent")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("kills", "Check how many kills a player has")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("deaths", "Check how many times a player has died")
                            .addOption(OptionType.STRING, "name", "Player name", true)
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
                    sender.sendMessage("You don't have permission to reload DiscordRelay.");
                    return true;
                }
            } else {
                sender.sendMessage("Usage: /discordrelay reload");
                return true;
            }
        }
        return false;
    }

    private void reloadPlugin() {
        loadConfig();
        chromaTagUtil.refresh();
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
                event.deferReply().queue();
                sendPlayerList(event);
            } else if (event.getName().equals("uptime")) {
                event.deferReply().queue();
                sendUptime(event);
            } else if (event.getName().equals("tps")) {
                event.deferReply().queue();
                sendTPS(event);
            } else if (event.getName().equals("firstseen") || event.getName().equals("lastseen") ||
                       event.getName().equals("timeplayed") || event.getName().equals("chatter") ||
                       event.getName().equals("kills") || event.getName().equals("deaths")) {
                event.deferReply().queue();
                sendPlayerStat(event);
            }
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getChannel().getId().equals(discordChannelId) && !event.getAuthor().isBot()) {
                Member member = event.getMember();
                String name = (member != null && member.getNickname() != null) ? member.getNickname() : event.getAuthor().getName();
                String discordMessageContent = event.getMessage().getContentDisplay();

                net.kyori.adventure.text.Component discordPrefix = net.kyori.adventure.text.Component.text("[Discord] ")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY);

                net.kyori.adventure.text.Component playerNameComponent = chromaTagUtil.getColoredPlayerNameComponent(name);

                net.kyori.adventure.text.Component messageComponent = net.kyori.adventure.text.Component.text(": " + discordMessageContent)
                        .color(net.kyori.adventure.text.format.NamedTextColor.WHITE);

                net.kyori.adventure.text.Component fullMessage = discordPrefix.append(playerNameComponent).append(messageComponent);

                if (Bukkit.getPluginManager().isPluginEnabled("BedrockSupport")) {
                    try {
                        String plainMessageForAPI = String.format("[Discord] %s: %s", name, discordMessageContent);
                        com.jellypudding.fakePlayers.FakePlayersAPI.addExternalMessage(plainMessageForAPI);
                    } catch (NoClassDefFoundError e) {
                        getLogger().warning("Could not forward Discord message to FakePlayers. Is it installed and enabled correctly?");
                    } catch (Exception e) {
                        getLogger().warning("Error forwarding Discord message to FakePlayers: " + e.getMessage());
                    }
                }

                Bukkit.getScheduler().runTask(DiscordRelay.this, () ->
                        Bukkit.broadcast(fullMessage)
                );
            }
        }

        private void sendPlayerList(SlashCommandInteractionEvent event) {
            List<String> realPlayerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            List<String> fakePlayerNames = new ArrayList<>();
            if (Bukkit.getPluginManager().isPluginEnabled("BedrockSupport")) {
                try {
                    Set<String> currentFakes = com.jellypudding.fakePlayers.FakePlayersAPI.getCurrentFakePlayerNames();
                    if (currentFakes != null) {
                        fakePlayerNames.addAll(currentFakes);
                    }
                } catch (NoClassDefFoundError e) {
                    getLogger().warning("Could not get fake player list for /list command. Is FakePlayers (BedrockSupport) installed and enabled correctly?");
                } catch (Exception e) {
                    getLogger().warning("Error getting fake player list for /list command: " + e.getMessage());
                }
            }

            List<String> allPlayerNames = new ArrayList<>(realPlayerNames);
            allPlayerNames.addAll(fakePlayerNames);
            Collections.sort(allPlayerNames, String.CASE_INSENSITIVE_ORDER);

            int totalPlayerCount = allPlayerNames.size();
            String playerListString = totalPlayerCount == 0 ? "No players online." : String.join(", ", allPlayerNames);
            String message = String.format("Online players (%d): %s", totalPlayerCount, playerListString);

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

        private void sendTPS(SlashCommandInteractionEvent event) {
            double[] tps = Bukkit.getTPS();

            double tps1min = tps.length > 0 ? Math.min(Math.round(tps[0] * 100.0) / 100.0, 20.0) : 0.0;
            double tps5min = tps.length > 1 ? Math.min(Math.round(tps[1] * 100.0) / 100.0, 20.0) : 0.0;
            double tps15min = tps.length > 2 ? Math.min(Math.round(tps[2] * 100.0) / 100.0, 20.0) : 0.0;
            
            String description = String.format("**1 minute:** %.2f TPS\n**5 minutes:** %.2f TPS\n**15 minutes:** %.2f TPS", 
                    tps1min, tps5min, tps15min);
            
            // Choose colour based on worst TPS.
            double worstTPS = Math.min(Math.min(tps1min, tps5min), tps15min);
            Color color = worstTPS >= 18.0 ? Color.GREEN : worstTPS >= 15.0 ? Color.YELLOW : Color.RED;

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Server TPS")
                    .setDescription(description)
                    .setColor(color);

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }

        private void sendPlayerStat(SlashCommandInteractionEvent event) {
            String playerName = event.getOption("name").getAsString();
            String commandName = event.getName();
            String statType = getStatTypeFromCommand(commandName);

            // Use FakePlayers pattern for OfflineStats integration
            if (Bukkit.getPluginManager().isPluginEnabled("OfflineStats")) {
                try {
                    com.jellypudding.offlineStats.OfflineStats offlineStatsPlugin =
                        (com.jellypudding.offlineStats.OfflineStats) Bukkit.getPluginManager().getPlugin("OfflineStats");
                    com.jellypudding.offlineStats.api.OfflineStatsAPI api = offlineStatsPlugin.getAPI();

                    getServer().getScheduler().runTaskAsynchronously(DiscordRelay.this, () -> {
                        try {
                            String formattedStat = api.getFormattedStat(playerName, statType);

                            EmbedBuilder embed = new EmbedBuilder();
                            if (formattedStat == null || formattedStat.isEmpty()) {
                                embed.setTitle("No Data Found")
                                        .setDescription("No data found for player: " + playerName)
                                        .setColor(Color.RED);
                            } else {
                                embed.setTitle("Player Statistics")
                                        .setDescription(formattedStat)
                                        .setColor(Color.GREEN);
                            }

                            event.getHook().sendMessageEmbeds(embed.build()).queue();
                        } catch (Exception e) {
                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle("Error")
                                    .setDescription("Error retrieving statistics for " + playerName + ": " + e.getMessage())
                                    .setColor(Color.RED);
                            event.getHook().sendMessageEmbeds(embed.build()).queue();
                        }
                    });
                } catch (NoClassDefFoundError e) {
                    getLogger().warning("Could not get player statistics. Is OfflineStats installed and enabled correctly?");
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Error")
                            .setDescription("OfflineStats plugin is not available. Statistics commands cannot be used.")
                            .setColor(Color.RED);
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                } catch (Exception e) {
                    getLogger().warning("Error getting player statistics: " + e.getMessage());
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Error")
                            .setDescription("Error accessing OfflineStats: " + e.getMessage())
                            .setColor(Color.RED);
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                }
            } else {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Error")
                        .setDescription("OfflineStats plugin is not enabled. Statistics commands cannot be used.")
                        .setColor(Color.RED);
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
        }

        private String getStatTypeFromCommand(String commandName) {
            switch (commandName.toLowerCase()) {
                case "firstseen": return "firstseen";
                case "lastseen": return "lastseen";
                case "timeplayed": return "timeplayed";
                case "chatter": return "chatter";
                case "kills": return "kills";
                case "deaths": return "deaths";
                default: return "";
            }
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

    /** For DiscordRelayAPI: Sends a custom message to Discord */
    public void relayCustomMessage(String message) {
        if (!isConfigured) return;
        sendToDiscord(message);
    }

    // --- End Public API Methods ---
}