# FlipMoneyFormat

A compact money formatter for Vault economies, with PlaceholderAPI support. The default display is designed for SMP-style prices such as **$ 12.01k**.

## Features

- Formats Vault's Economy#format(double) output through a highest-priority transparent wrapper.
- Delegates balance checks, deposits, withdrawals, bank operations, and all other Vault methods to your existing economy provider.
- Adds PlaceholderAPI placeholders for your balance, another player's balance, and arbitrary amounts.
- Configurable symbol, spacing, decimal precision, trailing-zero trimming, separators, and suffixes.
- /flipmoneyformat reload (alias /fmf reload) reloads settings.
- Targets Java 21 bytecode and the Bukkit/Paper 1.21 API floor for compatibility across Paper 1.21.11 through 26.3. Paper 26.x servers themselves require Java 25.

## Requirements

- Paper (or compatible Bukkit server) 1.21.11–26.3
- Vault
- An economy plugin that provides Vault's Economy service (for example EssentialsX economy)
- PlaceholderAPI for placeholders

Vault format wrapping works without PlaceholderAPI. Placeholder expansion requires PlaceholderAPI.

## Install

1. Put FlipMoneyFormat.jar, Vault.jar, PlaceholderAPI.jar, and your Vault economy plugin in plugins/.
2. Start the server once to generate plugins/FlipMoneyFormat/config.yml.
3. Adjust the configuration and run /flipmoneyformat reload.

## Placeholders

| Placeholder | Result |
|---|---|
| %flipmoneyformat_balance% | Your compact balance, including symbol, e.g. $ 12.01k |
| %flipmoneyformat_balance_short% | Your compact balance without symbol, e.g. 12.01k |
| %flipmoneyformat_balance_full% | Your full balance with grouping, e.g. $ 12,010 |
| %flipmoneyformat_balance_raw% | Plain, ungrouped balance number, e.g. 12010 |
| %flipmoneyformat_balance_number% | Compact number without the symbol |
| %flipmoneyformat_balance_<player>% | Another player's compact balance with symbol |
| %flipmoneyformat_balance_short_<player>% | Another player's compact balance without symbol |
| %flipmoneyformat_balance_full_<player>% | Another player's full balance with symbol |
| %flipmoneyformat_balance_raw_<player>% | Another player's plain balance number |
| %flipmoneyformat_format_<amount>% | Formats a number with symbol, e.g. %flipmoneyformat_format_12010% returns $ 12.01k |
| %flipmoneyformat_formatshort_<amount>% | Formats a number without symbol |
| %flipmoneyformat_formatfull_<amount>% | Full number with grouping and symbol |
| %flipmoneyformat_formatraw_<amount>% | Plain number with no symbol or grouping |

Player-name placeholders use the Minecraft username. Amount placeholders accept a period as the decimal separator, such as %flipmoneyformat_format_12010.25%.

## Configuration

    vault-format-override: true

    format:
      symbol: '$'
      space-after-symbol: true
      decimal-places: 2
      trim-trailing-zeros: true
      abbreviate: true
      suffixes: [k, m, b, t, q, qq]
      grouping-separator: ','
      decimal-separator: '.'

- vault-format-override: true makes Vault plugins that call Economy#format(double) use this format. Disable it if you only want PlaceholderAPI formatting.
- This plugin does not replace the economy provider and does not change actual account balances; only rendered text changes.
- Abbreviations are display-only: 12010 may display as $ 12.01k, while the underlying amount stays 12010.
- A plugin that caches a Vault provider before this plugin wraps it may need a restart or a reload of its own economy integration to pick up the formatted provider.

## Commands and permission

- /flipmoneyformat reload
- /fmf reload
- Permission: flipmoneyformat.admin (operators by default)

## Build from source

Requires JDK 21+ and Maven 3.9+.

    mvn clean package

The output JAR is target/FlipMoneyFormat.jar.
