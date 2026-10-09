# RankKits

`RankKits` is a small Paper plugin that provides daily, permission-gated kits and
does not add any other player features.

## Install

The included `build.sh` compiles against this server's local Paper API and places
`RankKits-1.0.0.jar` in the server's `plugins` directory. Run it from this
directory with `sh build.sh`, then restart the server.

## Configure ranks

After the restart, run the commands in the repository's `rank-permissions.txt`
in the server console, one line at a time. These commands preserve existing
permissions on LuckPerms' `default` group while denying the SMP command and kit
permissions to the ordinary Player rank. `Member` adds `/spawn`, `/msg`,
`/home`, and `/sethome`. Higher donor groups inherit their preceding donor rank.
Assign a player with `lp user <username> parent set <group>`; for example,
`lp user Example parent set vipplus`.

The kit permissions are only granted by the donor groups in that command list.
Kits can be edited in `plugins/RankKits/config.yml`; each kit has a 24-hour
cooldown by default, configurable with `cooldown-hours`. Cooldowns are persisted
in `plugins/RankKits/cooldowns.yml`.

The Owner group does not grant operator status to everyone in the group. Use
`op <owner-username>` in the server console only for the chosen owner account.
