package com.jellypudding.discordRelay.discord;

import com.jellypudding.discordRelay.DiscordRelay;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DiscordBotListener extends ListenerAdapter {

    private final DiscordRelay plugin;

    public DiscordBotListener(DiscordRelay plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "list"     -> { event.deferReply().queue(); sendPlayerList(event); }
            case "uptime"   -> { event.deferReply().queue(); sendUptime(event); }
            case "tps"      -> { event.deferReply().queue(); sendTPS(event); }
            case "firstseen", "lastseen", "timeplayed", "chatter", "kills", "deaths" -> {
                event.deferReply().queue();
                sendPlayerStat(event);
            }
            case "reputation" -> { event.deferReply().queue(); sendPlayerReputation(event); }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getChannel().getId().equals(plugin.getDiscordChannelId())) return;
        if (event.getAuthor().isBot() && (!plugin.getConfig().getBoolean("relay-bot-messages")
                || event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong())) return;

        Member member = event.getMember();
        String name = (member != null && member.getNickname() != null)
                ? member.getNickname()
                : event.getAuthor().getName();
        String content = event.getMessage().getContentDisplay();
        if (content.isBlank() && event.getAuthor().isBot()) {
            content = getBotMessageContent(event.getMessage());
            if (content.isEmpty()) return;
        }

        Component fullMessage = Component.text("[Discord] ").color(NamedTextColor.GRAY)
                .append(plugin.getChromaTagUtil().getColoredPlayerNameComponent(name))
                .append(Component.text(": " + content).color(NamedTextColor.WHITE));

        if (Bukkit.getPluginManager().isPluginEnabled("BedrockSupport")) {
            try {
                com.jellypudding.fakePlayers.FakePlayersAPI.addExternalMessage(
                        String.format("[Discord] %s: %s", name, content));
            } catch (NoClassDefFoundError e) {
                plugin.getLogger().warning("Could not forward Discord message to FakePlayers. Is it installed and enabled correctly?");
            } catch (Exception e) {
                plugin.getLogger().warning("Error forwarding Discord message to FakePlayers: " + e.getMessage());
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(fullMessage));
    }

    // Bots often post only embeds or attachments, which have no plain text content.
    // Convert the message content into plain text for in-game
    private static String getBotMessageContent(Message message) {
        List<String> parts = new ArrayList<>();
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embed.getAuthor() != null) parts.add(embed.getAuthor().getName());
            parts.add(embed.getTitle());
            parts.add(embed.getDescription());
            embed.getFields().forEach(field -> { parts.add(field.getName()); parts.add(field.getValue()); });
        }
        message.getAttachments().forEach(attachment -> parts.add("[" + attachment.getFileName() + "]"));
        return parts.stream().filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" | ")).replace('\n', ' ');
    }

    private void sendPlayerList(SlashCommandInteractionEvent event) {
        List<String> players = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());

        if (Bukkit.getPluginManager().isPluginEnabled("BedrockSupport")) {
            try {
                Set<String> fakes = com.jellypudding.fakePlayers.FakePlayersAPI.getCurrentFakePlayerNames();
                if (fakes != null) players.addAll(fakes);
            } catch (NoClassDefFoundError e) {
                plugin.getLogger().warning("Could not get fake player list for /list command.");
            } catch (Exception e) {
                plugin.getLogger().warning("Error getting fake player list: " + e.getMessage());
            }
        }

        Collections.sort(players, String.CASE_INSENSITIVE_ORDER);
        String list = players.isEmpty()
                ? "No players online."
                : players.stream().map(DiscordBotListener::escapeMarkdown).collect(Collectors.joining(", "));

        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Player List")
                .setDescription(String.format("Online players (%d): %s", players.size(), list))
                .setColor(Color.BLUE)
                .build()).queue();
    }

    private void sendUptime(SlashCommandInteractionEvent event) {
        long ms = System.currentTimeMillis() - plugin.getStartTime();
        long days    = ms / 86400000;
        long hours   = (ms / 3600000) % 24;
        long minutes = (ms / 60000) % 60;
        long seconds = (ms / 1000) % 60;

        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Server Uptime")
                .setDescription(String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds))
                .setColor(Color.GREEN)
                .build()).queue();
    }

    private void sendTPS(SlashCommandInteractionEvent event) {
        double[] tps = Bukkit.getTPS();
        double t1  = tps.length > 0 ? Math.min(Math.round(tps[0] * 100.0) / 100.0, 20.0) : 0.0;
        double t5  = tps.length > 1 ? Math.min(Math.round(tps[1] * 100.0) / 100.0, 20.0) : 0.0;
        double t15 = tps.length > 2 ? Math.min(Math.round(tps[2] * 100.0) / 100.0, 20.0) : 0.0;

        double worst = Math.min(Math.min(t1, t5), t15);
        Color color = worst >= 18.0 ? Color.GREEN : worst >= 15.0 ? Color.YELLOW : Color.RED;

        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Server TPS")
                .setDescription(String.format("**1 minute:** %.2f TPS\n**5 minutes:** %.2f TPS\n**15 minutes:** %.2f TPS", t1, t5, t15))
                .setColor(color)
                .build()).queue();
    }

    private void sendPlayerStat(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("name").getAsString();
        String statType = switch (event.getName().toLowerCase()) {
            case "firstseen"  -> "firstseen";
            case "lastseen"   -> "lastseen";
            case "timeplayed" -> "timeplayed";
            case "chatter"    -> "chatter";
            case "kills"      -> "kills";
            case "deaths"     -> "deaths";
            default           -> "";
        };

        withOfflineStatsAPI(event, playerName, api -> {
            String stat = api.getFormattedStat(playerName, statType);
            EmbedBuilder embed = new EmbedBuilder();
            if (stat == null || stat.isEmpty()) {
                embed.setTitle("No Data Found")
                        .setDescription("No data found for player: " + escapeMarkdown(playerName))
                        .setColor(Color.RED);
            } else {
                embed.setTitle("Player Statistics")
                        .setDescription(escapeMarkdown(stat))
                        .setColor(Color.GREEN);
            }
            return embed;
        });
    }

    private void sendPlayerReputation(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();

        withOfflineStatsAPI(event, playerName, api -> {
            var stats = api.getPlayerStats(playerName);
            EmbedBuilder embed = new EmbedBuilder();
            if (stats == null) {
                embed.setTitle("No Data Found")
                        .setDescription("No data found for player: " + escapeMarkdown(playerName))
                        .setColor(Color.RED);
            } else {
                int net = stats.getNetRep();
                Color color = net > 0 ? Color.GREEN : net < 0 ? Color.RED : Color.WHITE;
                embed.setTitle("Player Reputation")
                        .setDescription(String.format("%s has %d reputation (+%d/-%d)",
                                escapeMarkdown(stats.getUsername()), net, stats.getPositiveRep(), stats.getNegativeRep()))
                        .setColor(color);
            }
            return embed;
        });
    }

    @FunctionalInterface
    private interface OfflineStatsTask {
        EmbedBuilder execute(com.jellypudding.offlineStats.api.OfflineStatsAPI api) throws Exception;
    }

    private void withOfflineStatsAPI(SlashCommandInteractionEvent event, String playerName, OfflineStatsTask task) {
        if (!Bukkit.getPluginManager().isPluginEnabled("OfflineStats")) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Error").setDescription("OfflineStats plugin is not enabled.").setColor(Color.RED)
                    .build()).queue();
            return;
        }

        try {
            com.jellypudding.offlineStats.OfflineStats offlineStatsPlugin =
                    (com.jellypudding.offlineStats.OfflineStats) Bukkit.getPluginManager().getPlugin("OfflineStats");
            com.jellypudding.offlineStats.api.OfflineStatsAPI api = offlineStatsPlugin.getAPI();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    event.getHook().sendMessageEmbeds(task.execute(api).build()).queue();
                } catch (Exception e) {
                    event.getHook().sendMessageEmbeds(new EmbedBuilder()
                            .setTitle("Error")
                            .setDescription("Error retrieving data for " + escapeMarkdown(playerName) + ": " + e.getMessage())
                            .setColor(Color.RED).build()).queue();
                }
            });
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().warning("Could not access OfflineStats. Is it installed and enabled correctly?");
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Error").setDescription("OfflineStats plugin is not available.").setColor(Color.RED)
                    .build()).queue();
        } catch (Exception e) {
            plugin.getLogger().warning("Error accessing OfflineStats: " + e.getMessage());
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Error").setDescription("Error accessing OfflineStats: " + e.getMessage()).setColor(Color.RED)
                    .build()).queue();
        }
    }

    private static String escapeMarkdown(String text) {
        if (text == null) return null;
        return text.replace("\\", "\\\\")
                   .replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace("|", "\\|");
    }
}
