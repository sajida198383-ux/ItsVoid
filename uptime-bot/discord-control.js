require("dotenv").config();

const {
  Client,
  Events,
  GatewayIntentBits,
  REST,
  Routes,
  SlashCommandBuilder,
} = require("discord.js");
const { Rcon } = require("rcon-client");

const requiredEnvironment = [
  "DISCORD_TOKEN",
  "DISCORD_CLIENT_ID",
  "DISCORD_GUILD_ID",
  "DISCORD_ADMIN_IDS",
  "RCON_PASSWORD",
];

for (const name of requiredEnvironment) {
  if (!process.env[name]?.trim()) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
}

const adminIds = new Set(
  process.env.DISCORD_ADMIN_IDS.split(",")
    .map((id) => id.trim())
    .filter(Boolean),
);

if (adminIds.size === 0 || [...adminIds].some((id) => !/^\d{17,20}$/.test(id))) {
  throw new Error("DISCORD_ADMIN_IDS must contain one or more Discord user IDs.");
}

const rconPort = Number(process.env.RCON_PORT || 25575);
if (!Number.isInteger(rconPort) || rconPort < 1 || rconPort > 65535) {
  throw new Error("RCON_PORT must be an integer between 1 and 65535.");
}

const commands = [
  new SlashCommandBuilder()
    .setName("say")
    .setDescription("Send a message to Minecraft chat.")
    .addStringOption((option) =>
      option
        .setName("message")
        .setDescription("Message to send")
        .setRequired(true)
        .setMaxLength(256),
    ),
  new SlashCommandBuilder()
    .setName("ban")
    .setDescription("Ban a Minecraft player.")
    .addStringOption((option) =>
      option
        .setName("player")
        .setDescription("Minecraft player name")
        .setRequired(true)
        .setMaxLength(16),
    )
    .addStringOption((option) =>
      option
        .setName("reason")
        .setDescription("Reason for the ban")
        .setMaxLength(200),
    ),
  new SlashCommandBuilder()
    .setName("stop")
    .setDescription("Stop the Minecraft server."),
  new SlashCommandBuilder()
    .setName("restart")
    .setDescription("Restart Minecraft using the host's restart configuration."),
  new SlashCommandBuilder()
    .setName("players")
    .setDescription("Show the players currently online."),
].map((command) => command.toJSON());

const rest = new REST({ version: "10" }).setToken(process.env.DISCORD_TOKEN);

async function registerCommands() {
  await rest.put(
    Routes.applicationGuildCommands(
      process.env.DISCORD_CLIENT_ID,
      process.env.DISCORD_GUILD_ID,
    ),
    { body: commands },
  );
  console.log("Registered Discord server-control commands.");
}

async function sendMinecraftCommand(command) {
  const rcon = await Rcon.connect({
    host: process.env.RCON_HOST || "127.0.0.1",
    port: rconPort,
    password: process.env.RCON_PASSWORD,
    timeout: 5000,
  });

  try {
    return await rcon.send(command);
  } finally {
    await rcon.end();
  }
}

function getPlayerName(interaction) {
  const player = interaction.options.getString("player", true).trim();
  if (!/^[A-Za-z0-9_]{1,16}$/.test(player)) {
    throw new Error("Player name must be 1-16 letters, numbers, or underscores.");
  }
  return player;
}

const client = new Client({ intents: [GatewayIntentBits.Guilds] });

client.once(Events.ClientReady, (readyClient) => {
  console.log(`Discord control bot ready as ${readyClient.user.tag}.`);
});

client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  if (!adminIds.has(interaction.user.id)) {
    await interaction.reply({
      content: "You are not authorized to control this server.",
      ephemeral: true,
    });
    return;
  }

  await interaction.deferReply({ ephemeral: true });

  try {
    let command;
    let confirmation;

    switch (interaction.commandName) {
      case "say": {
        const message = interaction.options
          .getString("message", true)
          .replace(/[\r\n]/g, " ")
          .trim();
        if (!message) throw new Error("Message cannot be empty.");
        command = `say ${message}`;
        confirmation = "Message sent to Minecraft chat.";
        break;
      }
      case "ban": {
        const player = getPlayerName(interaction);
        const reason = interaction.options
          .getString("reason")
          ?.replace(/[\r\n]/g, " ")
          .trim();
        command = `ban ${player}${reason ? ` ${reason}` : ""}`;
        confirmation = `Ban command sent for ${player}.`;
        break;
      }
      case "stop":
        command = "stop";
        confirmation = "Stop command sent to the Minecraft server.";
        break;
      case "restart":
        command = "restart";
        confirmation =
          "Restart command sent. The server will return only if the host has a working restart script or supervisor.";
        break;
      case "players":
        command = "list";
        break;
      default:
        throw new Error("Unsupported command.");
    }

    const result = await sendMinecraftCommand(command);
    await interaction.editReply(
      interaction.commandName === "players"
        ? result || "No player list was returned."
        : confirmation,
    );
  } catch (error) {
    console.error(`Discord command ${interaction.commandName} failed:`, error);
    await interaction.editReply(
      `Command failed: ${error.message || "Unknown error."}`,
    );
  }
});

async function main() {
  await registerCommands();
  await client.login(process.env.DISCORD_TOKEN);
}

main().catch((error) => {
  console.error("Failed to start Discord control bot:", error);
  process.exitCode = 1;
});
