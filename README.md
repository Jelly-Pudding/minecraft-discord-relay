# DiscordRelay Plugin

**DiscordRelay** is a Minecraft Paper 1.21.1 plugin that creates a bidirectional chat bridge between your Minecraft server and a Discord channel.

## Features
- Relay chat messages from Minecraft to Discord and vice versa.
- Display player join and leave events in Discord.
- Show player death messages in Discord.
- Show player avatars in Discord messages.
- `/list` command in Discord to see online Minecraft players.
- `/uptime` command in Discord to check server uptime.
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
`/discordrelay reload`: Reloads the plugin configuration.

## Discord Commands
`/list`: Shows the list of online Minecraft players.
`/uptime`: Displays the current uptime of the Minecraft server.

## Support Me
Donations to my [Patreon](https://www.patreon.com/lolwhatyesme) will help with the development of this project.
