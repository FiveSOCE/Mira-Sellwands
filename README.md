# MiraSellWands

MiraSellWands provides transaction-safe container sell wands for the Mira Paper server suite. Players can right-click supported Bukkit containers to sell eligible contents at current MiraShop prices, including active sale-event pricing, with Vault payouts and persistent wand identities.

## Download

[**Download MiraSellWands v0.1.2**](https://github.com/FiveSOCE/Mira-SellWands/releases/download/v0.1.2/MiraSellWands-0.1.2.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-SellWands/releases)

[View All Releases](https://github.com/FiveSOCE/Mira-Sellwands/releases)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.2.0 or newer
- MiraShop 0.1.8 or newer
- Vault
- A Vault-compatible economy provider

## How MiraSellWands Works

Each wand has persistent PDC identity data: a unique serial, remaining uses and payout multiplier. Use `-1` for unlimited uses.

v0.1.1 makes the sell operation transactional. MiraSellWands first scans and prices a snapshot of the container without changing it. Vault must successfully accept the complete payout before the container is mutated. If the inventory mutation unexpectedly fails after the deposit, MiraSellWands attempts an immediate Vault rollback and records the fault in the server log.

MiraShop remains the pricing authority. Current sale-event pricing is used, and successful sales feed MiraShop economy statistics using the actual multiplied payout.

## Custom Item Safety

Custom Mira items are not treated as ordinary material stock. MiraSellWands checks registered MiraShop custom templates before generic material entries. Generic selling only accepts a plain ItemStack matching a fresh vanilla stack of that material, preventing named, enchanted, damaged, PDC-backed, custom-model or otherwise modified items from being silently sold as generic stock.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/sellwand give <player> <uses|-1> [multiplier]` | `mirasellwands.admin` | Gives a uniquely serialized sell wand. `-1` means unlimited uses. |

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `mirasellwands.use` | Everyone | Allows using sell wands on physical Bukkit containers. |
| `mirasellwands.admin` | OP | Allows administrative sell-wand creation. |

## Mira Ecosystem Integration

`SellWandsApi` is registered through Bukkit ServicesManager and MiraCore. It exposes safe wand creation and read-only identity/use/multiplier inspection for other Mira plugins.

Every administratively issued wand is written to MiraCore audit history. Successful sales can also be audited, including the player, wand serial, unit count, base value, multiplier, final payout and container coordinates.

A typed `SellWandSaleEvent` fires after a successful payout and container mutation.

## Configuration

`config.yml` controls:

- minimum repeat-use spacing with `wand.use-cooldown-millis`
- the maximum accepted multiplier with `wand.max-multiplier`
- whether successful sales are written to Core audit history

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.

## MiraCosmetics Audio Integration (0.1.2)

Adds optional MiraCosmetics audio for successful container cash-outs. Each completed wand transaction emits one SellWand audio event.
