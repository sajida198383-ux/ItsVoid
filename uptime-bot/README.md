# ItsVoid Uptime Bot

This stationary Mineflayer client joins the Java server and reconnects after a disconnect. It does not move, send chat, or use cheat features.

Run it from this directory with `npm install` followed by `npm start`.

Connection defaults use the current Java Playit address and protocol 26.1, which the server's ViaVersion plugin can translate to 26.2. Override them with `MC_HOST`, `MC_PORT`, `MC_USERNAME`, and `MC_VERSION` if needed.

The server must be running before the bot can join. Running the bot in the same Codespace does not keep the Codespace or server process alive when the host stops it. For reliable 24/7 uptime, run both the server and bot on an always-on host.

The server currently uses offline-mode, so the bot uses offline authentication and does not require account credentials. Keep the bot name unique and do not give it operator permissions.

## Discord server controls

The Discord control bot is separate from the Mineflayer uptime bot. It adds the `/say`, `/ban`, `/stop`, `/restart`, and `/players` slash commands to one Discord server. Every command is limited to the Discord user IDs in `DISCORD_ADMIN_IDS`.

1. Create a Discord application and bot in the Discord Developer Portal. Invite it to your server with the `bot` and `applications.commands` scopes.
2. Copy `.env.example` to `.env`. Set the bot token, application ID (`DISCORD_CLIENT_ID`), Discord server ID (`DISCORD_GUILD_ID`), and one or more trusted Discord user IDs (`DISCORD_ADMIN_IDS`, comma-separated). Do not share or commit `.env`.
3. In the Minecraft server's `server.properties`, set `enable-rcon=true` and set `rcon.password` to a strong unique password. Put the same password in `RCON_PASSWORD` in `.env`. Keep the RCON port private; the default bot connection is to localhost.
4. Restart Minecraft after changing `server.properties`, then start the Discord bot from this directory with `npm run discord-control`.

`/restart` sends Minecraft's `restart` command. This server's `spigot.yml` points to `./start.sh`, which starts the Paper jar in the repository root. `/stop` stops the server and does not start it again. GitHub repositories do not run servers by themselves, and GitHub Codespaces may stop when idle, so these commands only work while both the Minecraft server and Discord bot are running and reachable.