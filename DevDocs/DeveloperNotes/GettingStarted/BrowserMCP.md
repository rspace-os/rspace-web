# Browser MCP quick setup

Add Playwright for Chrome and Firefox, plus Chrome DevTools for performance,
console and network diagnostics. These commands target **Codex CLI** and
**Claude Code**, not Claude Desktop. Run them in a terminal on the host, not in
the RSpace Docker containers.

## Prerequisites

Have Node.js 24 LTS with npm/npx, Google Chrome stable, and your chosen agent CLI
installed and available on PATH.

```bash
node --version
npx --version
```

Install the Firefox build matching Playwright MCP's Playwright dependency.
Ordinary desktop Firefox and the repository's test-browser installation are not
necessarily the right build:

```bash
npx --yes "playwright@$(npm view @playwright/mcp@latest dependencies.playwright)" install firefox
```

On Linux, use `install --with-deps firefox` instead if browser system libraries
are missing; installing those libraries may require administrator privileges.
Repeat the matching-browser installation if an MCP update reports a missing
Firefox executable.

## Codex

These commands add user-level entries to `~/.codex/config.toml`:

```bash
codex mcp add playwright-chrome -- npx -y @playwright/mcp@latest --browser chrome --isolated
codex mcp add playwright-firefox -- npx -y @playwright/mcp@latest --browser firefox --isolated
codex mcp add chrome-devtools -- npx -y chrome-devtools-mcp@latest --isolated --no-usage-statistics

codex mcp list
```

The syntax and configuration location follow the [Codex MCP documentation](https://developers.openai.com/codex/mcp/).

## Claude Code

`--scope user` makes the servers available across your projects. Use
`--scope project` instead only if you want to share configuration through a
repository's `.mcp.json`.

```bash
claude mcp add --scope user --transport stdio playwright-chrome -- npx -y @playwright/mcp@latest --browser chrome --isolated
claude mcp add --scope user --transport stdio playwright-firefox -- npx -y @playwright/mcp@latest --browser firefox --isolated
claude mcp add --scope user --transport stdio chrome-devtools -- npx -y chrome-devtools-mcp@latest --isolated --no-usage-statistics

claude mcp list
```

The `--` separates Claude's options from the server command and its options.
See the [Claude Code MCP documentation](https://code.claude.com/docs/en/mcp).

## Check the connection

Start a new agent session after registration. In Codex CLI or Claude Code,
run `/mcp` to inspect server status. Then ask:

> Use playwright-chrome to open https://example.com and take a snapshot.
> Repeat with playwright-firefox. Use chrome-devtools to open https://example.com
> and inspect console messages.

For RSpace, substitute the URL printed by `./docker/dev/rspace-dev ps`.
Chrome DevTools opens a separate Chrome session; it does not inspect the
Playwright session automatically. Log in separately when testing authenticated
pages.

## Profiles and troubleshooting

`--isolated` avoids reusing a shared persistent profile, reducing "browser
already running" collisions when several agents run at once. Browser state,
including logins, is discarded when that session closes. See the
[Playwright MCP profile documentation](https://github.com/microsoft/playwright-mcp#browser-profile)
and [Chrome DevTools MCP configuration](https://github.com/ChromeDevTools/chrome-devtools-mcp/blob/main/docs/configuration.md).

Append `--headless` to a server command if the host has no graphical display.
If a name already exists, inspect it with `codex mcp get NAME` or
`claude mcp get NAME` before changing it. Do not delete another session's browser
profile or kill its browser to resolve a lock.

These servers can read browser content and perform actions with the accounts
you log into. Use development accounts, not a personal browser profile.
The DevTools examples disable its default usage-statistics collection, as
documented in the [Chrome DevTools MCP README](https://github.com/ChromeDevTools/chrome-devtools-mcp#usage-statistics).
`@latest` downloads current packages; pin package versions if you need
reproducible tooling.
