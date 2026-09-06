## v0.1.6 MiraShop bridge fix

The MiraShop integration no longer hard-links MiraShop implementation classes through the SellWands classloader. The optional bridge resolves MiraShop dynamically and retries availability when a wand is used, fixing false `MiraShop is currently unavailable` responses while MiraShop is healthy.

## v0.1.6 MiraShop bootstrap fix

- MiraShop is now a soft bootstrap dependency.
- All MiraShop API linkage is isolated behind an optional bridge.
- MiraSellWands loads in a degraded state when MiraShop is absent instead of being rejected by Paper.
- Actual selling remains disabled until MiraShop is available.

# MiraSellWands

MiraSellWands provides transaction-safe container sell wands for the Mira Paper server suite. Players can right-click supported Bukkit containers to sell eligible contents at current MiraShop prices, including active sale-event pricing, with Vault payouts and persistent wand identities.

## Download

[**Download MiraSellWands v0.1.6**](https://github.com/FiveSOCE/Mira-Sellwands/releases/download/v0.1.6/MiraSellWands-0.1.6.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-Sellwands/releases)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.4.1 or newer
- MiraShop 0.1.12 or newer
- Vault is optional for plugin bootstrap, but required for actual selling
- A Vault-compatible economy provider is required for payouts

## Bootstrap compatibility fix (0.1.4)

v0.1.6 fixes a plugin-recognition/bootstrap issue.

- Vault is no longer a hard `plugin.yml` dependency.
- Vault API classes are isolated behind an optional economy bridge, so Paper can recognize and enable MiraSellWands even when Vault is absent or unavailable during dependency resolution.
- Without a compatible Vault economy provider, MiraSellWands loads in a degraded state and selling is disabled with a clear message.
- The build now targets MiraCore 0.4.1 and MiraShop 0.1.12.
- Release publishing is restricted to explicit `release/**` branches.

## How MiraSellWands Works

Each wand has persistent PDC identity data: a unique serial, remaining uses and payout multiplier. Use `-1` for unlimited uses.

v0.1.1 makes the sell operation transactional. MiraSellWands first scans and prices a snapshot of the container without changing it. Vault must successfully accept the complete payout before the container is mutated. If the inventory mutation unexpectedly fails after the deposit, MiraSellWands attempts an immediate Vault rollback and records the fault in the server log.

MiraShop remains the pricing authority. Current sale-event pricing is used, and successful sales feed MiraShop economy statistics using the actual multiplied payout.

## Custom Item Safety

Custom Mira items are not treated as ordinary material stock. MiraSellWands checks registered MiraShop custom templates before generic material entries. Generic selling only accepts a plain ItemStack matching a fresh vanilla stack of that material, preventing named, enchanted, damaged, PDC-backed, custom-model or otherwise modified items from being silently sold as generic stock.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/sellwand give <player> <tier|uses|-1> [multiplier]` | `mirasellwands.admin` | Gives a configured tier or a custom uniquely serialized wand. `-1` means unlimited uses. |

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
- configurable `wand.tiers` definitions for uses and multiplier
- whether successful sales are written to Core audit history

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.

## MiraCosmetics Audio Integration (0.1.2)

Adds optional MiraCosmetics audio for successful container cash-outs. Each completed wand transaction emits one SellWand audio event.

## SellWand Tiers and Charges (0.1.3)

v0.1.6 formalizes the existing charge system into configurable tiers while preserving custom numeric admin creation.

Default tiers:

| Tier | Default Uses | Default Multiplier |
| --- | ---: | ---: |
| `BASIC` | 50 | 1.00x |
| `ADVANCED` | 250 | 1.00x |
| `UNLIMITED` | -1 | 1.00x |

A use is consumed **only after** the Vault payout succeeds and the container mutation completes. Failed/empty sales do not burn charges.

Other Mira plugins can create configured tier wands through `SellWandsApi#createTier(String)`.
