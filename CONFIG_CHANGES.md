# Config Extraction — `breaking` Branch

This branch extracts hardcoded gameplay values from Java source files into `config.yml`,
making them tunable by server admins without recompiling the plugin.

---

## What Was Done

### Pattern used throughout
Every value follows the same pattern:
1. A new key is added to `config.yml` with a sensible default matching the old hardcoded value.
2. A typed getter is added to `ConfigManager.java`.
3. The Java class reads the value in its constructor (instance field), or reads it inline where needed.
4. The hardcoded literal is removed.

No gameplay defaults changed — servers upgrading to this build will behave identically until
an admin edits `config.yml`.

---

## Config Keys Added

### Per-stage Vampire Ability Tuning
Allows each ability to be tuned differently per vampire stage.

| Key | Default | Controls |
|-----|---------|---------|
| `abilities.vampire.bat-cooldown-stage1/2/3` | `900` | Bat transformation cooldown per stage (seconds) |
| `abilities.vampire.bat-duration-seconds-stage1/2/3` | `120/120/180` | Bat form duration per stage (seconds) |
| `abilities.vampire.lunge-cooldown-stage2/3` | `45` | Lunge cooldown per stage (seconds) |
| `abilities.vampire.lunge-strength-stage2/3` | `2.0/2.5` | Lunge velocity multiplier per stage |
| `abilities.vampire.vanish-cooldown-stage2/3` | `420` | Vanish cooldown per stage (seconds) |
| `abilities.vampire.vanish-duration-seconds-stage2/3` | `120/240` | Vanish duration per stage (seconds) |
| `abilities.vampire.vanish-attack-limit-stage2/3` | `3` | Attacks before invisibility breaks per stage |
| `abilities.vampire.beacon-teleport-cooldown-stage2/3` | `300` | Shadow Travel cooldown per stage (seconds) |

**Files changed:** `BatAbility.java`, `LungeAbility.java`, `InvisibilityAbility.java`, `BeaconTeleportAbility.java`, `BatTransformationManager.java`, `VampireAbility.java`

---

### Beacon System
| Key | Default | Controls |
|-----|---------|---------|
| `beacons.capture-radius` | `10` | Block radius a player must stand within to capture a beacon |
| `abilities.vampire.beacon-teleport-channel-ticks` | `100` | Channel time before Shadow Travel teleport fires (ticks) |

**Files changed:** `BeaconManager.java`, `BeaconTeleportListener.java`

---

### Combat — Human Death & Final Stand
| Key | Default | Controls |
|-----|---------|---------|
| `combat.humans-final-stand-health-cap` | `6.0` | Max vampire HP during the "one human left" final stand phase |
| `combat.human-death-score-cap` | `5` | Max deaths recorded on scoreboard (excess clamped) |
| `combat.human-death-hp-penalty` | `2.0` | HP penalty per death under beacon majority bonus |

**Files changed:** `EffectManager.java`, `DeathHandler.java`, `BeaconMajorityManager.java`

---

### Werewolf — Hunger Immunity
| Key | Default | Controls |
|-----|---------|---------|
| `werewolf.hunger.immunity-minutes` | `15` | Minutes of hunger immunity after a stage promotion or demotion |

**Files changed:** `WerewolfHungerManager.java`

---

### Iron Weakness (Vampire)
| Key | Default | Controls |
|-----|---------|---------|
| `iron-weakness.repel-distance` | `2.0` | Blocks from iron block before physical repel kicks in |
| `iron-weakness.repel-strength` | `0.5` | Velocity applied when repelling |
| `iron-weakness.proximity-radius` | `5.0` | Radius checked for iron proximity weakness |
| `iron-weakness.weakness-duration-ticks` | `40` | Duration of Weakness effect (ticks) |
| `iron-weakness.weakness-amplifier` | `2` | Weakness amplifier (0 = Weakness I) |

**Files changed:** `IronWeaknessListener.java`

---

### Passive Mob Spawning Weights
| Key | Default | Controls |
|-----|---------|---------|
| `passive-mob-spawning.mob-weights.COW/PIG/SHEEP/CHICKEN` | `30/30/25/15` | Weighted spawn table for morning passive mob spawns |

**Files changed:** `PassiveMobSpawningManager.java`

---

### Storm Call
| Key | Default | Controls |
|-----|---------|---------|
| `abilities.vampire.storm-clear-weather-ticks` | `6000` | Ticks of clear weather locked in after the storm ends (prevents immediate re-rain) |

**Files changed:** `StormCallAbility.java`

---

### Tome Abilities — Cooldowns
All tome cooldowns were already in config. No change here.

---

### Tome Abilities — Tuning Parameters

| Key | Default | Controls |
|-----|---------|---------|
| `abilities.tome.banishundead.radius` | `40` | Sweep radius for removing undead mobs |
| `abilities.tome.lanthrash.fire-radius` | `6` | Outer radius of fire ring |
| `abilities.tome.lanthrash.fire-inner-radius` | `2` | Inner exclusion radius of fire ring |
| `abilities.tome.lanthrash.fire-resistance-duration-ticks` | `6000` | Fire Resistance given to caster |
| `abilities.tome.rallyingcry.radius` | `20.0` | Strength buff radius |
| `abilities.tome.rallyingcry.strength-duration-ticks` | `600` | Buff duration |
| `abilities.tome.rallyingcry.strength-amplifier` | `0` | Strength amplifier (0 = Strength I) |
| `abilities.tome.shoulderbarge.charge-velocity` | `1.5` | Forward velocity on charge |
| `abilities.tome.shoulderbarge.knockback-strength` | `1.2` | Knockback on collision |
| `abilities.tome.shoulderbarge.slowness-duration-ticks` | `300` | Slowness applied to hit targets |
| `abilities.tome.shoulderbarge.damage-to-players` | `10.0` | Damage dealt to player targets |
| `abilities.tome.shoulderbarge.damage-to-mobs` | `20.0` | Damage dealt to mob targets |
| `abilities.tome.shoulderbarge-charge.charge-duration-ticks` | `20` | Ticks the collision window stays open |
| `abilities.tome.shoulderbarge-charge.upward-velocity` | `0.3` | Upward launch component on barge |
| `abilities.tome.shoulderbarge-charge.target-cooldown-ms` | `3000` | MS before the same target can be barged again |
| `abilities.tome.turnundead.disguise-duration-ticks` | `6000` | Duration of undead disguise |
| `abilities.tome.turnundead.darkness-ticks` | `200` | Darkness effect on cast |
| `abilities.tome.turnundead.slowness-ticks` | `100` | Slowness effect on cast |
| `abilities.tome.unnaturalhaste.haste-duration-ticks` | `6000` | Haste duration |
| `abilities.tome.unnaturalhaste.haste-amplifier` | `1` | Haste amplifier (1 = Haste II) |
| `abilities.tome.prayeroffaith.channel-seconds` | `60` | Motionless prayer duration |
| `abilities.tome.prayeroffaith.absorption-duration-ticks` | `12000` | Absorption duration on completion |
| `abilities.tome.prayeroffaith.absorption-amplifier` | `2` | Absorption amplifier |
| `abilities.tome.stopthebleeding.channel-ticks` | `1200` | Channel duration for healing |
| `abilities.tome.stopthebleeding.proximity-distance` | `2.0` | Max distance between healer and target |
| `abilities.tome.enlightenedeye.night-vision-duration-ticks` | `6000` | Night Vision duration |
| `abilities.tome.wayoftheland.double-drop-chance` | `0.75` | Chance for double crop drops |
| `abilities.tome.wayofthelumberjack.double-drop-chance` | `0.30` | Chance for double log drops |
| `abilities.tome.holyword.radius` | `20` | Paralysis radius (blocks) |
| `abilities.tome.holyword.paralysis-duration-ticks` | `300` | Paralysis duration (ticks) |
| `abilities.tome.uncannydirection.animation-ticks` | `140` | Compass HUD display duration (ticks) |

**Files changed:** `BanishUndeadTomeAbility.java`, `LanternThrashTomeAbility.java`, `RallyingCryTomeAbility.java`, `ShoulderBargeTomeAbility.java`, `TurnUndeadTomeAbility.java`, `UnnaturalHasteTomeAbility.java`, `PrayerOfFaithTomeAbility.java`, `StopTheBleedingTomeAbility.java`, `EnlightenedEyeTomeAbility.java`, `WayOfTheLandTomeAbility.java`, `WayOfTheLumberjackTomeAbility.java`, `HolyWordTomeAbility.java`, `UncannyDirectionTomeAbility.java`

---

## Files Changed Summary

| File | Change |
|------|--------|
| `src/main/resources/config.yml` | All new keys added with defaults and comments |
| `ConfigManager.java` | ~30 new typed getters added |
| `BatAbility.java` | Per-stage cooldown override |
| `LungeAbility.java` | Per-stage cooldown + strength from config |
| `InvisibilityAbility.java` | Per-stage cooldown + duration + attack limit from config |
| `BeaconTeleportAbility.java` | Per-stage cooldown override |
| `BatTransformationManager.java` | Per-stage bat duration from config |
| `BeaconManager.java` | Capture radius injected from config |
| `BeaconTeleportListener.java` | Channel ticks from config |
| `EffectManager.java` | Final stand health cap from config |
| `DeathHandler.java` | Death score cap from config |
| `BeaconMajorityManager.java` | HP penalty per death from config |
| `WerewolfHungerManager.java` | Hunger immunity minutes from config |
| `IronWeaknessListener.java` | All 5 iron weakness params from config |
| `PassiveMobSpawningManager.java` | Mob weights from config |
| `StormCallAbility.java` | Clear weather lock duration from config |
| `BanishUndeadTomeAbility.java` | Radius from config |
| `LanternThrashTomeAbility.java` | Fire radius, inner radius, resistance duration from config |
| `RallyingCryTomeAbility.java` | Radius, duration, amplifier from config |
| `ShoulderBargeTomeAbility.java` | All 7 params from config |
| `TurnUndeadTomeAbility.java` | 3 params from config |
| `UnnaturalHasteTomeAbility.java` | Duration + amplifier from config |
| `PrayerOfFaithTomeAbility.java` | Channel seconds + absorption params from config |
| `StopTheBleedingTomeAbility.java` | Channel ticks + proximity distance from config |
| `EnlightenedEyeTomeAbility.java` | Night vision duration from config |
| `WayOfTheLandTomeAbility.java` | Double drop chance from config |
| `WayOfTheLumberjackTomeAbility.java` | Double drop chance from config |
| `HolyWordTomeAbility.java` | Radius + paralysis duration from config |
| `UncannyDirectionTomeAbility.java` | Animation duration from config |

---

## How to Use

After updating to this build, all values use their previous hardcoded defaults automatically.
To tune them, edit `config.yml` in your server's plugin data folder and run `/pow admin reload`
(or restart the server).

No migration steps required — existing servers just gain the ability to configure these values.
