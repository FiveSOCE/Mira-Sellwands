# MiraSellWands

MiraShop-aware container sell wands for Paper 1.21.11 / Java 21.

## Current release

**v0.1.0**

Direct download:
https://github.com/FiveSOCE/Mira-Sellwands/releases/download/v0.1.0/MiraSellWands-0.1.0.jar

All releases:
https://github.com/FiveSOCE/Mira-Sellwands/releases

## Features

- Limited or unlimited-use sell wands
- Configurable payout multiplier per wand
- Unique serial PDC identity
- Right-click Bukkit containers to sell eligible contents
- Uses current MiraShop sell prices, including temporary sale events
- Exact custom-item/template matching before generic material matching
- Vault payouts
- Successful sales feed MiraShop item analytics

## Commands

- `/sellwand give <player> <uses|-1> [multiplier]`

## Requirements

- Vault + economy provider
- MiraShop for pricing

## Build

`./gradlew build`

Output: `build/libs/MiraSellWands-0.1.0.jar`
