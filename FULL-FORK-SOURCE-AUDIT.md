# WerePires full-fork source audit

Audited: 2026-08-16

Baseline: VampireSMP 1.0.6 / repository baseline at `origin/breaking`

Audited branch: `agent/1.15-cellphone-major` (the branch name does not limit this audit)

This is the source-level companion to `CHANGELOG-FROM-VAMPIRESMP-1.0.6.md`. It records the callable command surface, registrations, permissions, managers, listeners, systems, and every source file changed on this fork branch.

## Audit totals

- 185 Java source files inspected.
- 84 files differ from the repository baseline: 59 Java files, 2 runtime resource files, `pom.xml`, 20 release/changelog records, this audit document, and the repeatable source-audit script.
- 35 Bukkit command roots are declared in `plugin.yml`.
- 4 Paper Brigadier roots are registered at runtime: `/pow` plus the three Latin ritual commands.
- 7 permissions are declared and every literal permission checked by Java is declared.
- 58 listener-capable classes were inspected, including commands, managers, tome abilities, and voice-chat event subscribers; 2 are intentional compatibility placeholders.
- 50 manager-named classes were inspected, plus services and hooks outside the manager package.

## Complete command surface

### Paper Brigadier commands

- `/pow` (aliases `/vampiresmp`, `/vsmp`)
  - Information/use: `help`; `vability <list|all|ability>`; `wability <list|all|feralcharge>`; `tome <list|ability>`; `beaconstatus` (aliases `holysites`, `holy`); `texture [all|force|vampire|human]` (aliases `texturepack`, `resourcepack`); `permadeath <on|off|absolute>` (aliases `toggle-permadeath`, `toggle_permadeath`, `togglepermadeath`); `toggle-turning` (alias `turning`); `permakill`; `sendmessage` (alias `sendpendingmessage`); `reopen` (alias `forcedcure-reopen`); `ability` (alias `abilities`); and `faedeal` (alias `fae`).
  - Fae deal controls: `bargains`; `vampire <player> <stage> [breakOnFaePermadeath]`; `release <player>`; `canturn`; `canbeturned`; `turnlocks`; `deaths`; and `hearts`.
  - Admin session/faction controls: `init`; `session <start|pause|end|prime|resume|building>`; `vampire <player> <human|1|2|3|turn|clearcap|clearban>`; `werewolf <player> <human|1|2|3|clearcap|clearban>`; `onehumanleft`; and `setupplayer <player>`.
  - Admin ability/combat controls: `vampirecooldowns <reset|clear> [player]`; `resettomecooldowns`; `vampirehealthcheck <get|set>`; `damagesuppression <get|set>`; `fixattributes [all|player]`; `removeendermen <all|toggle|status>`; and `clearbloodmoonbuffs <all|player>`.
  - Admin world/event controls: `bloodmoon <start|stop|status>`; `break_warning`; `spawnanimals`; `barrier <lower|raise|status|exempt|normal|unexempt>`; `loadworld <name>`; `listworlds`; `set_vampire_spawn [x y z]`; and `fadestatus`.
  - Admin beacon controls: `beacon <add|remove|delete|list|info|stats|reload|validate|holy|desecrated|desecrate|neutral|fix|repair|refresh|cleanup|clearcooldowns|debug>`.
  - Admin tome/vault controls: `givetome`; `select_tomes`; `give_cure_book`; `give_revival_book`; `distributetomes`; `addtomechest`; `removetomechest`; `listtomechests`; `addtomevault`; `removetomevault`; `listtomevault` (alias `listtomevaults`); `addominouscurevault`; `removeominouscurevault`; and `listominouscurevaults` (alias `listominouscurevault`).
  - Admin identity/state controls: `resetplayer`; `permakill`; `sire`; `fae`; `canturn`; `canbeturned`; `turnlocks`; `deaths`; and `hearts`.
  - Admin configuration: `reloadconfig`; `config [gui|reload|list|get|set]`.
- `/voluntate-mea-hoc-nefandum-vinculum-abicio`: self-cure ritual.
- `/hoc-vinculum-tibi-dirumpo-mala-creatura <player>`: forced cure ritual.
- `/sanguine-et-nocte-te-ex-umbra-revoco <player>`: Rite of Return ritual.

### Bukkit-declared commands

- Thralls: `/thrall <bondinfo|findmaster|findthrall|list|command|punish|bar|stay|check|thrallcount|force|profile|preferences|checkconfig|admin>`. Profile operations are `create`, `select`, `list`, and `delete`; admin operations are `cooldownreset`, `reset`, `setowner`, `bondset`, and `forcesave`.
- Roles: `/role`, `/rolecfg`, `/rolestart`, `/vampire`, `/gameadmin`, and `/findvampires`. These cover opt-in/out, role list maintenance, cooldown reset, random assignment, vampire selection, game-admin list maintenance, and hunter scans.
- Starter kits: `/kitstart`, `/kitstop`, `/kitall`, `/starterkit [edit|player]`, `/starterkitgive`, `/foodkitadd`, `/foodkitremove`, and `/foodkitgive`.
- Player/session setup: `/playersetup`; `/gamestart [timer|cancel|sessionend|resumesession|breaktimer]`.
- Skins/ghosts: `/skin <register|apply|clear|list|reload>`; `/ghost <show|hide|showall|hideall|list|haunt|unhaunt|stop|setfree>`.
- Cure-book placement: admin-only `/stash_third_book` and `/stash_fourth_book <x> <y> <z>`.
- Phone administration: `/cellphone [give|open|reload]`, `/givephone`, `/phonegive <all|player>`, `/checkphonestatus`, and `/phonereset confirm` (alias `/cpreset`).
- Phone calls: `/answer` (alias `/pickup`), `/decline` (alias `/reject`), `/hangup` (alias `/cphangup`), `/cpacceptcall`, `/cpdeclinecall`, legacy `/cphanghup`, `/callmute`, `/calldeafen`, and `/speaker`.

## Permission audit

- `vampiresmp.admin`: `/pow admin`, staff skin/ghost/world/config operations, and other core administration.
- `vampiresmp.player`: ordinary player-facing declared commands.
- `vampiresmp.modgate.bypass`: compatibility-mod gate exemption.
- `vampiresmp.phone.admin`: phone give/open/reload/reset administration.
- `thralls.admin`: thrall administration.
- `starterkit.admin`: starter-kit administration.
- `starterkit.use`: player access to their own starter-kit settings.

No Java permission literal is absent from `plugin.yml`.

## Listener and event audit

Directly registered Bukkit listeners:

- Core/player: `DamageSuppressionListener`, `DeathHandler`, `CombatListener`, `PlayerJoinListener`, `BlockListener`, `InteractionListener`, `MovementBoundaryListener`, `PlayerChatManager`, and `InitGameListener`.
- Vampire: `VampireCraftBlocker`, `IronWeaknessListener`, `FeedingListener`, `ThirstEffectsListener`, `NoSleepListener`, `VampireFallDamageListener`, `BatTransformationListener`, `VampireStrengthImmunityListener`, and `VampireFeedingManager`.
- Beacons/items/world: `BeaconConversionListener`, `BeaconTeleportListener`, `BeetrootListener`, `BeetrootHarvestListener`, `ExperienceBottleListener`, `EndermanRemovalListener`, `SilverArrowManager`, `VaultLootListener`, and `VaultChunkListener`.
- Tomes/cure/revival: `CureBookReadingListener`, `TomeListener`, `TomeVampireRestrictionListener`, `FourthBookRevealListener`, `ForcedCureChoiceListener`, and `RevivalBookManager`.
- Werewolf/thrall/roles: `WerewolfBitingListener`, `WerewolfDietListener`, `BloodDrawListener`, `BloodConsumeListener`, `ThrallHolyWaterListener`, `ThrallStayListener`, `ThrallInventoryListener`, `ThrallJoinQuitListener`, and `TrackerListener`.
- Systems: `GameStartCommand`, `StarterKitCommand`, `PlayerSetupManager`, `GhostModeManager`, `GhoulManager`, and `PhoneManager`.

Self-registered or manager-registered listeners:

- `HolyWordTomeAbility`, `WayOfTheProspectorTomeAbility`, `WayOfTheLumberjackTomeAbility`, `WayOfTheLandTomeAbility`, `BlessedBladeTomeAbility`, and any future `TomeAbility` that also implements `Listener` are registered exactly once by `TomeManager`.
- `BloodMoonAttributeListener` is registered once by the plugin; `HolyWaterEffectManager` and `SkinPreviewGui` self-register during construction.
- `WerewolfAbilityListener` is intentionally an empty placeholder; Feral Charge is command-driven and registered by `WerewolfAbilityManager`.
- `ThrallInventoryListener` is intentionally a registered compatibility stub; inventory offers were replaced by Paper Dialog callbacks.
- Simple Voice Chat event subscribers are `GhostVoicechatPlugin` and `PhoneVoicechatPlugin`; the phone plugin also registers the call volume category after voice-chat server startup.

Plugin messaging lifecycle was checked as a pair: WerePires registers incoming/outgoing compatibility channels on enable and unregisters them on disable.

## Manager and service audit

- Core/session/config: `ConfigManager`, `SessionManager`, `InitGameManager`, `GameStartManager`, `PlayerSetupManager`, and `RoleManager`.
- Factions/state: `VampireManager`, `VampireSireManager`, `WerewolfAbilityManager`, `WerewolfHungerManager`, `WerewolfPackManager`, `FaeManager`, `TurnLockManager`, `ThrallManager`, `ThrallDataManager`, and `GhoulManager`.
- Abilities/effects: `VampireAbilityManager`, `TomeManager`, `TomeDistributionManager`, `EffectManager`, `FadeManager`, `HolyWaterEffectManager`, `VampireFeedingManager`, `ThirstManager`, `BloodMoonManager`, `BloodBottleManager`, and `ForcedCureChoiceManager`.
- World/beacons/loot: `WorldManager`, `WorldPackManager`, `BeaconManager`, `BeaconMajorityManager`, `VaultManager`, `PassiveMobSpawningManager`, `MobTeamManager`, and `BeetrootManager`.
- Player presentation/state: `ArmorStorageManager`, `BatTransformationManager`, `SkinShuffleManager`, `VampireTexturePackManager`, `VampireTrackingManager`, `VampireTurningManager`, `StarterKitManager`, and `PermadeathManager`.
- Phone: `PhoneManager`, `PhoneDataStore`, `PhoneUi`, `PhoneGameManager`, `PhoneCallService`, `PhoneVoicechatHook`, `PhoneVoicechatPlugin`, and `LegacyPhoneImporter`.
- Network/mod gate: `WerePiresNetwork`, `ModGateManager`, and the ghost/phone voice-chat integrations. The TESTBUILD custom-network protocol remains deliberately disabled.

## Every branch-changed source file

### Build, entry point, and resources

- `pom.xml`: Paper/voice-chat dependencies, Java/shading/build naming, resource exclusions, and test-build copy.
- `VampireSMPPlugin.java`: new manager/service construction, listener and command registration, messaging channels, voice-chat hooks, and disable cleanup.
- `config.yml`: new configurable gameplay, world, faction, ability, Fae, phone, resource-pack, and persistence settings.
- `plugin.yml`: WerePires metadata, soft dependencies, commands, aliases, and permissions.

### Commands

- `AbilityCommand.java`: survive-turn ability allow-list administration.
- `BrigadierCommands.java`: complete `/pow` tree, suggestions, aliases, admin nodes, and Latin ritual roots.
- `CommandHandler.java`: Fae/turn-lock/death/world/config/sire and other admin dispatch.
- `DeathCounterCommand.java`: death/heart get/set/add/remove/give/take operations.
- `FaeCommand.java`: staff Fae status/list/add/remove operations.
- `FaeDealCommand.java`: bargains, release, stage/life binding, turn locks, and counters.
- `PowCommand.java`: routing, help, permissions, aliases, and completion.
- `StashThirdBookCommand.java`: restored legacy placement command plus the missing Cure Book 3 hidden identity.
- `TomeAbilityCommand.java`: new tome names, aliases, enablement, and invocation.
- `TurnLockCommand.java`: turn/can-be-turned controls and status.
- `GameStartCommand.java`: permission-based administration instead of an undocumented operator-only gate.
- `RoleCommand.java` and `ThrallCommand.java`: aligned permission checks and help visibility with declared permissions.

### Managers

- `ConfigManager.java`: new settings accessors and defaults.
- `FadeManager.java`: opacity transitions, restoration, persistence, and client synchronization.
- `FaeManager.java`: persistent Fae records and bargain enforcement/lifecycle.
- `SessionManager.java`: mutable session-state separation.
- `ThirstManager.java`: configurable thirst timing and safe floors.
- `TomeManager.java`: new ability registration and listener-aware registration.
- `TurnLockManager.java`: persistent per-player/per-species lock state.
- `VampireFeedingManager.java`: session feed limits and survival-state cancellation.
- `VampireManager.java`: stage cap/promotion-ban and bargain integration.
- `VampireTexturePackManager.java`: managed, stacked, stable-ID resource-pack delivery.
- `RevivalBookManager.java` and `WorldManager.java`: moved mutable unlock/world selection into `state.yml` and corrected WerePires data paths.
- `SkinShuffleManager.java`, `VampireSireManager.java`, `RoleManager.java`, and `ThrallDataManager.java`: corrected paths, notifications, and permission-based staff visibility.
- `StarterKitManager.java`: corrected its player-facing permission and retained explicit shutdown persistence.

### Listeners

- `CombatListener.java`: Fae/stage restrictions and new combat behavior.
- `DeathHandler.java`: Fae bargain breaking, counters, and permanent-death integration.
- `ExperienceBottleListener.java`: corrected hand/item processing.
- `PlayerJoinListener.java`: join-time state, phone/pack/mod synchronization.
- `BloodMoonAttributeListener.java`: single registration ownership, preventing duplicate event handling.
- `TomeVampireRestrictionListener.java`: preserved-ability rules.
- `WerewolfBitingListener.java`: turn-lock checks and conversion protections.
- `BloodConsumeListener.java`: corrected drink interaction, vial consumption, glass return, and bond/thirst routing.

### Tome abilities

- `TomeAbility.java`: shared metadata, enablement/loot controls, and hit hooks.
- `FireBreathTomeAbility.java`: shared cooldown family behavior.
- `BlueFireBreathTomeAbility.java`: Magic Fire Breath naming/legacy identity and balancing.
- `ScryingTomeAbility.java`: targeted temporary tracking.
- `FadingTomeAbility.java`: configurable opacity ability.
- `HolyWordTomeAbility.java`, `WayOfTheLandTomeAbility.java`, `WayOfTheLumberjackTomeAbility.java`, and `WayOfTheProspectorTomeAbility.java`: removed duplicate self-registration so `TomeManager` is the sole listener owner.

### Phone

- `LegacyPhoneImporter.java`: one-time import of eight legacy Skript data files.
- `PhoneCallCommand.java`: call answer/decline/hangup/mute/deafen/speaker commands.
- `PhoneCallService.java`: call state, blocking, participant routing, timeout, and labels.
- `PhoneCommand.java`: phone giving, opening, status, reload, and reset.
- `PhoneDataStore.java`: atomic persistent contacts/messages/groups/social/GPS/notes/game/settings state.
- `PhoneGameManager.java`: seven games, wagers, records, challenges, and live refresh.
- `PhoneManager.java`: phone item/event lifecycle, identity, notifications, and service wiring.
- `PhoneUi.java`: all dialog screens and user actions.
- `PhoneVoicechatHook.java`: optional voice-chat bridge.
- `PhoneVoicechatPlugin.java`: microphone capture, private/group routing, speakerphone, and echo prevention.

### Network

- `WerePiresNetwork.java`: compatibility protocol messages for skins, ghosts, Fading, and client features; disabled TESTBUILD switch retained.

## Confirmed audit repairs

- Restored the unreachable `/pow admin bloodmoon <start|stop|status>` tree.
- Restored Brigadier access to handler-supported aliases: `abilities`, `fae`, `toggle_permadeath`, `togglepermadeath`, plural/singular vault forms, and beacon `delete`, `desecrate`, and `repair`.
- Added the missing `StarterKitManager.shutdown()` call to the plugin-disable lifecycle.
- Registered the two orphaned stash command classes and repaired the third book's hidden cure-book identity.
- Restored handler-supported `ability status/help`, Fae `list/help/free`, and long-form vampire/werewolf stage-cap and promotion-ban aliases in Brigadier.
- Corrected `/pow admin` usage messages, Bukkit command usage metadata, starter-kit access, and role/session staff checks to match declared permissions.
- Moved active world/template and revival-unlock values into `state.yml`, including migration when an existing state file is already present.
- Added copy-only migration from legacy `plugins/VampireSMP/` data to `plugins/WerePires/` without overwriting current files or deleting the source.
- Removed duplicate tome and Blood Moon listener registration paths.
- Added `scripts/audit_source.py` so command, permission, listener, ability, manager, lifecycle, and literal config coverage can be rechecked on every build.
- Verified all `plugin.yml` command roots are assigned an executor, all Java permission literals are declared, and all non-placeholder listener classes are registered directly or through their owning manager.
