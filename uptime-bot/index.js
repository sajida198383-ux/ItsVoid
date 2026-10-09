const mineflayer = require("mineflayer");

const host = process.env.MC_HOST || "cynthia-appearances.tun.ply.gg";
const port = Number(process.env.MC_PORT || 32170);
const username = process.env.MC_USERNAME || "ItsVoidAFK";
const version = process.env.MC_VERSION || "26.1";
const reconnectDelay = Number(process.env.RECONNECT_DELAY_SECONDS || 15) * 1000;

let stopping = false;
let reconnectTimer;
let currentBot;

function connect() {
  if (stopping) return;

  console.log(`Connecting ${username} to ${host}:${port}`);
  currentBot = mineflayer.createBot({
    host,
    port,
    username,
    auth: "offline",
    version,
  });

  if (process.env.MC_DEBUG === "1") {
    currentBot._client.on("state", (nextState, previousState) => {
      console.log(`Protocol state: ${previousState} -> ${nextState}`);
    });
  }

  currentBot.once("spawn", () => {
    console.log(`Connected as ${username}. Staying idle.`);
  });

  currentBot.on("kicked", (reason) => {
    console.warn(`Disconnected by server: ${JSON.stringify(reason)}`);
  });

  currentBot.on("error", (error) => {
    console.error(`Connection error: ${error.message}`);
  });

  currentBot.once("end", (reason) => {
    console.warn(`Connection ended: ${reason || "unknown reason"}`);
    if (stopping) return;

    console.log(`Retrying in ${reconnectDelay / 1000} seconds.`);
    reconnectTimer = setTimeout(connect, reconnectDelay);
  });
}

function shutdown() {
  stopping = true;
  clearTimeout(reconnectTimer);
  currentBot?.quit("Bot process stopping");
}

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);

connect();