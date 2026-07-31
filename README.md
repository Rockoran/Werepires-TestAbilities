# WerePires — VampireSMP Plugin

A Paper 1.21 plugin for a survival multiplayer server where players are assigned roles: **Vampires**, **Humans**, **Werewolves**, and **Thralls**. Each role has unique abilities, mechanics, and win conditions tied to a beacon capture system.

---

## Overview

WerePires is a roleplay-driven SMP plugin built around asymmetric gameplay. Vampires grow stronger as they stage up, humans use tome abilities to fight back, werewolves operate as a third faction, and thralls are bound servants to vampire masters.

### Roles

| Role | Summary |
|------|---------|
| **Vampire** | Stage 1–3 progression. Gains abilities (Bat, Lunge, Vanish, Shadow Travel, Storm Call) at each stage. Weakened by iron, garlic, and sunlight. |
| **Human** | Wields tome abilities (Holy Word, Shoulder Barge, Rallying Cry, Prayer of Faith, etc.). Goal is to survive and recapture beacons. |
| **Werewolf** | Third faction with its own ability set and pack mechanics. |
| **Thrall** | Bound to a vampire master via blood bond. Has access to blood bottle mechanics. |

---

## Systems

- **Beacon System** — Capturable beacons across multiple worlds (Frostvein, Revhurst, SilverHills). Majority beacon control gives vampires buffs and humans debuffs.
- **Thirst System** — Vampires must feed regularly or suffer escalating penalties.
- **Tome System** — Humans receive randomised tome books granting one-use or cooldown abilities.
- **Config System** — All gameplay values are tunable via `config.yml` with no recompile needed. Over 50 values extracted to config on the `breaking` branch.
- **Ghost Mode** — Spectator-style mode with voicechat integration for eliminated players.
- **Vault System** — World-embedded loot vaults for mid-game resource injection.
- **Starter Kit** — Configurable starting loadout distributed on game start.
- **Skin System** — Role-based skin assignment via SkinShuffle integration.
- **Revival System** — Mechanics for bringing eliminated players back under specific conditions.
- **Permakill** — Permanent elimination system for end-game scenarios.

---

## Branch: `breaking`

The `breaking` branch is the active development branch. It contains:

- Full config extraction (~50 hardcoded values moved to `config.yml`)
- New ability classes: Bloodscent, CallSwarm, CrimsonVeil, FeralCharge, HypnoticGaze, MistForm, SanguineBite, SiresCommand
- New tome abilities: BlessedBlade, BlueFireBreath, CleansingSmoke, ConsecrateGround, Daybreak, FireBreath, HuntersMark, LastVigil, Sanctuary
- New systems: Thralls, Ghost, Vault, Roles, GameStart, World pack management
- Config split planning docs (see `config-split-plan/`)

---

## Building

```bash
mvn -f pom.xml package -q
```

Output jar in `target/`. Requires Java 21 and Paper 1.21 API.

---

## Config

All tunable values live in `config.yml` (placed in the plugin data folder on first run). Reload in-game with:

```
/pow admin reload
```

No server restart required for config changes.

---

## Future Plans

- **Config split** — Break the single `config.yml` into per-system files (`vampire.yml`, `human.yml`, `combat.yml`, `beacons.yml`, etc.) for easier admin navigation. Full plan in `config-split-plan/`.
- **Finish new abilities** — Several new vampire and tome abilities are drafted but not yet wired into the ability manager or registered in `plugin.yml`.
- **Werewolf ability expansion** — Werewolf ability set is partially implemented; needs full parity with vampire stage system.
- **Thrall polish** — Blood bottle system, bond mechanics, and thrall-specific restrictions need end-to-end testing.
- **Vault loot tables** — Vault contents are hardcoded; move to configurable loot tables.
- **Game start flow** — `GameStartManager` and role assignment flow needs full integration testing with skin system and starter kit.
- **World pack system** — Multi-world support (Frostvein/Revhurst/SilverHills) needs beacon config validation and world-specific tuning support.
- **Permadeath integration** — Permakill and revival systems need consistent integration with the death handler and scoreboard.
- **Iron weakness tuning** — All 5 iron weakness params are now config-tunable; needs in-game balance testing.
- **Blood Moon events** — BloodMoonManager exists but event triggering and full effect pipeline needs testing.
