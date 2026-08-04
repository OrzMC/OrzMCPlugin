# OrzMC

[![Pull Request Build Check](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/OrzMC/OrzMCPlugin/branch/main/graph/badge.svg?token=QV5RJRNKW0)](https://codecov.io/gh/OrzMC/OrzMCPlugin)
[![Test Count](https://img.shields.io/badge/tests-800+-blue.svg)](https://github.com/OrzMC/OrzMCPlugin/actions)
[![Coverage](https://img.shields.io/badge/coverage-64%25-green.svg)](https://github.com/OrzMC/OrzMCPlugin/actions)
[![Dependabot Updates](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates)
[![Publish](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml)

A Paper server management plugin that unifies QQ, Telegram, Discord, Feishu and WeChat bots through the EasyBot gateway.

> 🌐 **English** | [简体中文](./README.zh-CN.md)
> 
> This plugin is built for [PaperMC](https://papermc.io/) servers. Since the
> `PaperAPI` is compatible with the `BukkitAPI` and `SpigotAPI`, it is also
> friendly to developers with Bukkit and Spigot plugin development experience.
>
> It currently runs on my [private server](https://orzmc.jokerhub.cn) to assist
> with administration, and is published on [Hangar](https://hangar.papermc.io/OrzMC/OrzMC)
> and [Modrinth](https://modrinth.com/plugin/orzmc).

## Features

| Feature | Description |
|---------|-------------|
| Whitelist management | Control server access. Admins add/remove players via bot commands (`$a` / `$r` / `$w`); inactive players are cleaned up automatically; kicked non-whitelisted players receive a helpful notice. |
| Multi-platform bot system | Unify QQ, Telegram, Discord, Feishu and WeChat through the EasyBot gateway. 9 bot commands for player management, queries and interaction; 30+ customizable message templates push server events to the matching group or channel. |
| Cross-server portals | Admins create or remove portals; players stepping on a portal are transferred across servers. Optional LoginSecurity verification before transfer. |
| TNT protection | Restrict where TNT can be placed, with per-area whitelist exemption; explosion notifications to the group chat; control respawn anchor explosion behavior. |
| Security controls | Restrict joins by GeoIP country; three blacklist modes (exact IP / CIDR / wildcard); optional LoginSecurity secondary verification. |
| Teleport bow | Shoot an arrow to teleport to its landing spot with automatic safe-landing detection; searches a nearby safe spot when needed; configurable entity teleport policy. |
| World maintenance | One-click world backup or optimization with real-time progress reports; the server list MOTD switches automatically during maintenance. |
| Player notifications | Push join/quit/kick details (role name, reason) to the group chat; show role prefixes before player messages. |
| Guide book | First-join players automatically receive a guide book; content is YAML-configurable so server owners can tailor the onboarding. |
| Runtime configuration | Manage 24 configuration options in-game with `/config`; changes hot-reload without restarting the server. |
| OrzMC menu | Open an in-game feature menu with quick access to every operation (in development). |

For the full feature list, see [docs/features.md](./docs/features.md).

## Installation

Download the plugin, drop it into the `plugins/` directory of your PaperMC
server, and start the server. The plugin creates a data directory with the same
name on first run. During operation, configuration is loaded into memory and
written back to the config files when the server stops.

## Bot setup (EasyBot gateway)

All OrzMC bot features connect through the external EasyBot IM gateway.

[EasyBot](https://github.com/easyIndie/EasyBot) unifies QQ / Telegram / Discord / Feishu / WeChat:

1. Deploy the EasyBot gateway service
2. Fill in the EasyBot connection address in the plugin's `easybot.yml`
3. Obtain the `api_key` by creating a **customer-service API Key** in the EasyBot console
4. Target values such as `admin_group` are not native platform IDs — get the **session key** from the EasyBot console's **Session Management** (e.g. `qq:conv_xxxxxxxx`)

> Detailed routing rules: [EasyBot configuration guide](./docs/features.md#25-easybot-网关配置指南)

### Migrating from the old setup

The legacy `bot.yml` is no longer loaded. Before upgrading, migrate any values
you still need — `cmd_prompt_char`, `discord_server_link`, `qq_group_id` and
`log_throttle_ms` — into `easybot.yml`, and finish the per-platform session
setup in the EasyBot console. The old NapCatQQ, Discord JDA and Feishu Webhook
direct-connection parameters are no longer needed and can be removed.

## Updating

PaperMC provides an `update/` directory inside the plugin directory. Put the
new plugin JAR there; on the next server restart it is moved into `plugins/`
automatically, completing the update.

## Feedback

If you run into any issues, we'd love to hear from you — please open an
[issue](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose).

You can also join our QQ channel for feedback:<br/>
![OrzMC feedback group QR code](https://raw.githubusercontent.com/OrzMC/OrzMCPlugin/main/images/lark_issue_feedback.png)

## Contributing

- [Contribution guide](CONTRIBUTING.md) (development notes and iteration conventions)
- [Plugin architecture](./docs/architecture.md)
- [Changelog](./CHANGELOG.md)
