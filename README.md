# DiscordRelay Plugin

**DiscordRelay** is a Minecraft Paper 1.21.10 plugin that creates a bidirectional chat bridge between your Minecraft server and a Discord channel.

## Features
- Relay chat messages from Minecraft to Discord and vice versa.
- Display player join and leave events in Discord.
- Show player death messages in Discord.
- Show player avatars in Discord messages.
- ChromaTag Integration (optional): Display player name colours in in-game Discord relay messages.
- `/list` command in Discord to see online Minecraft players.
- `/uptime` command in Discord to check server uptime.
- `/tps` command in Discord to check server TPS (ticks per second).
- Server start-up and shutdown notifications in Discord.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/minecraft-discord-relay/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server to generate the default configuration file.
4. Set up your Discord bot (see Discord Bot Setup below).
5. Configure the plugin (see Configuration below).
6. Run `/discordrelay reload` to reload the plugin.

## Discord Bot Setup
1. Go to the [Discord Developer Portal](https://discord.com/developers/applications).
2. Click "New Application" and give it any suitable name. You could call it "MC Bot" for example.
3. Go to the "Bot" tab and click "Reset Token".
4. You may have to carry out two-factor authentication. Once you see the new token, copy it to a notepad file.
5. In the "Bot" tab, scroll down to the "Privileged Gateway Intents" section. Enable "MESSAGE CONTENT INTENT" and "SERVER MEMBERS INTENT".
6. Go to the "OAuth2" tab, then "OAuth2 URL Generator".
7. In "Scopes", select "applications.commands" and "bot". This will make a "Bot Permissions" section appear below.
8. In "Bot Permissions", select:
   - Read Message History
   - View Channels
   - Send Messages
9. Copy the generated URL at the bottom of the page. Paste it into the server you want to add your bot to. You should see a message - click it and follow the steps to add your bot to your Discord server.
10. If your bot does not appear in your Discord server, carry out the steps again but with another browser. There are currently issues with Google Chrome.
11. At the bottom of Discord, you should see your avatar. A bit to the right of this, there is a cog wheel which says, "User Settings" if you hover over it. Click the cog wheel.
12. In `App Settings`, scroll down to `Advanced`. In the `Advanced` section, enable `Developer Mode`.
13. In your Discord server, right click on the channel where you want messages to be relayed. Copy the channel ID and put it into a notepad file.
14. Enter the values you obtained for your Discord's `Bot Token` and `Channel ID` into `plugins/DiscordRelay/config.yml`.

## Configuration
1. Open the `plugins/DiscordRelay/config.yml` file.
2. Set `discord-bot-token` to your bot's token.
3. Set `discord-channel-id` to the ID of the Discord channel you want to use for the relay.
   (To get the channel ID, enable Developer Mode in Discord settings, then right-click the channel and select "Copy ID")
4. Save the file.

Example `config.yml`:
```yaml
discord-bot-token: 'YOUR_BOT_TOKEN_HERE'
discord-channel-id: 'YOUR_CHANNEL_ID_HERE'
```

## In-game Commands
- `/discordrelay reload`: Reloads the plugin configuration. Requires `discordrelay.reload` permission (default: op).
- `/discordrelay send <colour> <message>`: Sends a formatted message to Discord with the specified colour. Requires `discordrelay.send` permission (default: op).
  - Available colours: red, green, blue, yellow, orange, purple, pink, grey, white, black
  - Examples:
    - `/discordrelay send red Server will restart in 5 minutes!` (uses default title "Server Message")
    - `/discordrelay send red Alert: Server will restart in 5 minutes!` (uses "Alert" as title)

## Discord Commands
- `/list`: Shows the list of online Minecraft players.
- `/uptime`: Displays the current uptime of the Minecraft server.
- `/tps`: Shows the server's TPS (ticks per second) for the last 1, 5, and 15 minutes.

## Permissions
- `discordrelay.use`: Allows using DiscordRelay commands (default: op).
- `discordrelay.reload`: Allows reloading the plugin configuration (default: op).
- `discordrelay.send`: Allows sending formatted messages to Discord (default: op).

## API for Developers

### Setup Dependencies
1. Download the latest `DiscordRelay.jar` and place it in a `libs` directory - and then add this to your `build.gradle` file:
    ```gradle
    dependencies {
        compileOnly files('libs/DiscordRelay-1.1.3.jar')
    }
    ```

2. If DiscordRelay is absolutely required by your plugin, then add this to your `plugin.yml` file - and this means if DiscordRelay is not found then your plugin will not load:
    ```yaml
    depend: [DiscordRelay]
    ```

### Getting DiscordRelay Instance
You can import DiscordRelay into your project through using the below code:
```java
import org.bukkit.Bukkit;
import com.jellypudding.discordRelay.DiscordRelayAPI;

// Check if DiscordRelay is available and ready
if (DiscordRelayAPI.isReady()) {
    // Plugin is loaded and configured - safe to use API
    DiscordRelayAPI.sendCustomMessage("Hello from my plugin!");
}
```

### Available API Methods
```java
// Check if DiscordRelay is loaded and properly configured
boolean isReady = DiscordRelayAPI.isReady();

// Send a custom message to Discord (plain text)
DiscordRelayAPI.sendCustomMessage("Custom message goes here :)");

// Send a formatted message to Discord with colour and title
DiscordRelayAPI.sendFormattedMessage("Alert", "I spilt my milk!", Color.RED);

// Send player join event (if needed)
DiscordRelayAPI.sendPlayerJoin("PlayerName");

// Send player leave event (if needed)
DiscordRelayAPI.sendPlayerLeave("PlayerName");

// Send player chat message (if needed)
DiscordRelayAPI.sendPlayerMessage("PlayerName", "Hello Discord!");

// Send player death message (if needed)
DiscordRelayAPI.sendPlayerDeath("PlayerName", "PlayerName was slain by a zombie");
```

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
