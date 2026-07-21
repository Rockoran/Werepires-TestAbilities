# Config Split Plan — WerePires Plugin

## Proposed file structure

```
src/main/resources/
├── config.yml       ← server, vampire, thrall, tomes, chat, state tracking
├── abilities.yml    ← all ability cooldowns and tuning (vampire + tome + werewolf)
├── combat.yml       ← beacons, iron weakness, garlic, cure, revival, permadeath
└── werewolf.yml     ← werewolf combat/biting/pack/hunger + spawning + oakhurst
```

The four `.yml` files in this folder are exactly what those files would look like. Copy them into `src/main/resources/` to replace the old `config.yml` + add the three new ones.

---

## How the code changes work

### 1. Register each file in `ConfigManager`

Bukkit only auto-loads `config.yml`. Every additional file needs a `FileConfiguration` field and a loader method.

```java
public class ConfigManager {

    private final FileConfiguration config;      // plugin.getConfig() — stays as-is
    private FileConfiguration abilitiesConfig;
    private FileConfiguration combatConfig;
    private FileConfiguration werewolfConfig;

    public ConfigManager(VampireSMPPlugin plugin) {
        this.config = plugin.getConfig();
        this.abilitiesConfig = loadExtra(plugin, "abilities.yml");
        this.combatConfig    = loadExtra(plugin, "combat.yml");
        this.werewolfConfig  = loadExtra(plugin, "werewolf.yml");
    }

    private FileConfiguration loadExtra(VampireSMPPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false); // copies from resources/ if missing
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
```

**Imports needed:**
```java
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
```

---

### 2. Update every getter to point at the right backing config

Each getter currently reads from `this.config`. After the split, point it at whichever file owns that key:

| Key prefix | Old call | New call |
|---|---|---|
| `abilities.*` | `this.config.getInt(...)` | `this.abilitiesConfig.getInt(...)` |
| `beacons.*` | `this.config.getInt(...)` | `this.combatConfig.getInt(...)` |
| `iron-weakness.*` | `this.config.getInt(...)` | `this.combatConfig.getDouble(...)` |
| `garlic.*` | `this.config.getInt(...)` | `this.combatConfig.getInt(...)` |
| `cure.*` | `this.config.getDouble(...)` | `this.combatConfig.getDouble(...)` |
| `combat.*` | `this.config.getDouble(...)` | `this.combatConfig.getDouble(...)` |
| `werewolf.*` | `this.config.getDouble(...)` | `this.werewolfConfig.getDouble(...)` |
| `passive-mob-spawning.*` | `this.config.*` | `this.werewolfConfig.*` |
| `oakhurst.*` | `this.config.*` | `this.werewolfConfig.*` |

Everything else (vampire settings, thrall, tomes, chat, state flags) stays on `this.config`.

---

### 3. Ability constructors that call `plugin.getConfig()` directly

Several tome abilities read from `plugin.getConfig()` rather than going through `ConfigManager`. These need updating too:

| File | Current call | Fix |
|---|---|---|
| `BanishUndeadTomeAbility.java` | `plugin.getConfig().getInt("abilities.tome.banishundead.radius", 40)` | `plugin.getConfigManager().getAbilitiesConfig().getInt(...)` |
| `LanternThrashTomeAbility.java` | `plugin.getConfig().getInt("abilities.tome.lanthrash.*")` | same pattern |
| `RallyingCryTomeAbility.java` | `plugin.getConfig().getDouble("abilities.tome.rallyingcry.*")` | same pattern |
| *(all other tome abilities)* | `plugin.getConfig().get*("abilities.tome.*")` | same pattern |

Two options:
- **Option A (simpler):** Add a public `getAbilitiesConfig()` getter on `ConfigManager` and call it directly in the ability constructors.
- **Option B (cleaner):** Add named getters to `ConfigManager` for every value (the pattern already used for most keys). Ability constructors never touch raw config objects.

Option B is preferred since it keeps all config access in one place, making future key changes a one-line fix.

---

### 4. Reload support

If `/pow admin reload` (or similar) exists, update it to re-load all three extra files:

```java
public void reload(VampireSMPPlugin plugin) {
    plugin.reloadConfig();
    this.abilitiesConfig = loadExtra(plugin, "abilities.yml");
    this.combatConfig    = loadExtra(plugin, "combat.yml");
    this.werewolfConfig  = loadExtra(plugin, "werewolf.yml");
}
```

---

### 5. `saveDefaultConfig` equivalent for extra files

`plugin.saveDefaultConfig()` only saves `config.yml`. The `loadExtra()` helper above (using `plugin.saveResource(name, false)`) handles this — it copies the bundled default from `resources/` to the data folder the first time the server starts, and never overwrites an existing file.

---

## What does NOT change

- All `ConfigManager` getter **signatures** stay identical — no call sites in managers or listeners need updating.
- The `config.yml` internal state flags (`first_beacon_converted`, etc.) remain in `config.yml` since the plugin writes them back via `plugin.saveConfig()`.
- Maven build: no `pom.xml` changes needed — resource files are included automatically.

---

## Migration steps (in order)

1. Copy all four `.yml` files from this folder into `src/main/resources/` (replacing the old `config.yml`).
2. Add the three `FileConfiguration` fields and `loadExtra()` to `ConfigManager`.
3. Update each getter in `ConfigManager` to read from the correct backing config.
4. Update the handful of tome ability constructors that call `plugin.getConfig()` directly for `abilities.*` keys.
5. Update any reload command to call `configManager.reload(plugin)`.
6. Run `mvn package` — fix any compile errors from step 3/4.
7. Drop the resulting jar on a test server and verify all abilities fire with correct values.
