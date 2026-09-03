# MiraSellWands

MiraSellWands provides container sell-wands for the Mira Paper server suite. Players can right-click supported containers to sell eligible contents using current MiraShop prices and receive Vault economy payouts.

## Download

[**Download MiraSellWands v0.1.0**](https://github.com/FiveSOCE/Mira-Sellwands/releases/download/v0.1.0/MiraSellWands-0.1.0.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- Vault
- A Vault-compatible economy provider
- MiraShop recommended/required for live MiraShop pricing integration

## How MiraSellWands Works

Sell wands have a unique persistent identity, a configured number of uses or unlimited uses, and an optional payout multiplier. A player with use permission right-clicks a supported Bukkit container with the wand. Eligible contents are matched against current MiraShop sell prices, including temporary sale-event pricing where applicable, and the calculated total is paid through Vault.

Custom/template item matching is evaluated before generic material matching so special items are not accidentally sold at the wrong generic price. Successful sales can also feed MiraShop item analytics.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/sellwand give <player> <uses|-1> [multiplier]` | `mirasellwands.admin` | Gives a player a sell wand. Use `-1` for unlimited uses and optionally specify a payout multiplier. |

Normal wand use is performed by right-clicking a supported container rather than through a command.

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `mirasellwands.use` | Everyone | Allows using sell wands on supported containers. |
| `mirasellwands.admin` | OP | Allows sell-wand administration and giving wands. |
