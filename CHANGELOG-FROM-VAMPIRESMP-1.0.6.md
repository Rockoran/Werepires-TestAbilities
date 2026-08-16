# Complete WerePires fork changelog from VampireSMP 1.0.6

Last audited: 2026-08-16
Current branch version: WerePires 1.15.11 TESTBUILD

This is the consolidated change inventory for the **entire fork**. The cellphone branch name does not limit its scope.

For the exhaustive source-level inventory—including every command/subcommand/alias, permission, manager, listener, service, registration path, and every Java file changed on the branch—see `FULL-FORK-SOURCE-AUDIT.md`.

## Baseline used for the audit

- Baseline artifact: `VampireSMP-1.0.6.jar` supplied by the project owner.
- The JAR's `config.yml` and `plugin.yml` match the repository's original decompiled baseline after normalizing line endings.
- The baseline is used only to identify inherited behavior. Entries below describe behavior added, changed, fixed, migrated, or made configurable by WerePires.

## Platform, packaging, and compatibility

- Renamed the plugin from VampireSMP to WerePires. Bukkit now uses `plugins/WerePires/`; on startup a copy-only migration brings across files missing from `plugins/VampireSMP/` and preserves the original folder as a backup.
- Moved the project to Paper 1.21.10 APIs and declared 1.21.5 as the minimum plugin API.
- Kept Java 17 bytecode compatibility and added a reproducible Maven shade build.
- Replaced decompiled embedded Gson sources with the declared Gson dependency and shaded it into the output JAR.
- Added optional Simple Voice Chat integration for ghosts and cellphone calls without making voice chat mandatory for server startup.
- Added a compatibility-mod handshake for ghost noclip, per-stage skin changes, Fading opacity, and related client features.
- Added the `vampiresmp.modgate.bypass` permission and a configurable compatibility-mod requirement.
- Added the WerePires network protocol and world-repository configuration. The TESTBUILD protocol switch remains disabled.
- Added development-only `WerePires-<version>-TESTBUILD.jar` naming and copies completed builds into `testbuilds/`.
- Excluded nested historical build output from resource packaging so `target/classes` is not recursively embedded in the JAR.

## Configuration and persistent state

- Expanded the original configuration with hundreds of settings covering abilities, factions, combat, beacons, worlds, kits, roles, ghosts, revival, thralls, packs, and phones.
- Preserved the original defaults when hardcoded values were moved into configuration, so an unchanged server keeps the old behavior.
- Added `/pow admin config` with an in-game editor plus `get`, `set`, `list`, and `reload` operations.
- Added `/pow admin reloadconfig` for explicit configuration reloads.
- Moved mutable session flags out of `config.yml` and into `state.yml`, with first-start migration from existing configs.
- Stopped routine state saves from stripping comments out of `config.yml`.
- Made the town name, town center, playable border, passive mob spawning, combat caps, iron weakness, cure rules, tome distribution, vaults, and beacon timing configurable.
- Added per-stage tuning for Bat Form, Lunge, Vanish, Shadow Travel, and Werewolf Feral Charge.
- Added full tuning for all classic tome abilities and every new ability.
- Added warning output for invalid configurable tome names instead of silently creating empty rewards.

## Vampire gameplay

- Added seven vampire abilities:
  - **Bloodscent** reveals wounded humans within its configured radius.
  - **Mist Form** blinks through obstructions and briefly protects the vampire.
  - **Sanguine Bite** damages a nearby human, heals the vampire, and restores thirst.
  - **Call the Swarm** surrounds nearby humans with bats, blindness, and chip damage.
  - **Hypnotic Gaze** clouds a targeted human; garlic blocks the effect.
  - **Crimson Veil** heals vampires and weakens humans in an area.
  - **Sire's Command** strengthens and hastens the caster's bonded thralls.
- Changed Bat Form to support per-stage duration and a three-minute Stage 3 duration.
- Changed Lunge to support per-stage launch power and cooldowns.
- Changed Vanish to support per-stage duration and attack limits, report remaining attacks, and reject use while in bat form.
- Changed Storm Call to expose duration/cooldown/weather-lock settings and warn one minute before the storm ends.
- Changed Shadow Travel to expose channel time and per-stage cooldowns and optionally require full health.
- Added a configurable new-vampire indicator with duration, range, tracker, and minimum-stage controls.
- Added global turning-disable behavior and automatic turning shutdown after a successful turn.
- Added persistent per-player turn locks and commands for who may turn or be turned.
- Made vampire respawn, thirst depletion, and per-session feeding limits configurable.
- Prevented vampires from receiving Strength from any source.
- Cancelled active feeding when the target leaves Survival and prevented feeding on non-Survival targets.
- Added per-stage automatic vampire skins with restoration after curing.

## Fae bargains and bloodlines

- Added staff-managed Fae status and `/pow faedeal` bargains.
- Added vampire-stage bargains that pin a target to Stage 1, 2, or 3.
- Added optional life-bound bargains that break when the creating Fae permanently dies.
- Added configurable human reversion, cured marking, turn restrictions, staking rules, and break broadcasts.
- Added Fae controls for deaths/hearts, turning permission, target turning permission, and turn-lock inspection.
- Prevented Fae-created vampires from promoting or demoting outside their bargained stage and retained a safe minimum blood value.
- Added sire inspection, assignment, and clearing commands.
- Changed curing to walk a configurable number of links up the sire chain.
- Added configurable support for cured sires, daytime requirements, and cure distance.

## Human tomes and abilities

- Added eleven registered tome abilities:
  - **Consecrate Ground** heals humans and sears vampires in an area.
  - **Blessed Blade** increases damage against vampires and weakens them.
  - **Daybreak** creates damaging artificial daylight.
  - **Sanctuary** grants strong resistance to nearby humans.
  - **Cleansing Smoke** shields humans and repels/weakens vampires.
  - **Hunter's Mark** makes a targeted vampire glow for everyone.
  - **Last Vigil** grants resistance, regeneration, and absorption.
  - **Fire Breath** creates a configurable cone of fire.
  - **Magic Fire Breath** is the longer, tighter, stronger fire variant and accepts the legacy `BlueFireBreath` identity.
  - **Scrying** temporarily points the vampire-indicator arrow at a named target.
  - **Fading** eases a compatibility-mod player's opacity to a chosen level or toggles full fading.
- Added event hooks for on-hit tome abilities such as Blessed Blade.
- Added separate enabled and loot-pool controls so an ability can work without spawning naturally.
- Added an allow-list for tome abilities and physical tome items that may survive a vampire turn.
- Corrected Holy Word's victim message and prevented paralysed vampires from escaping by mounting entities.
- Tagged real tome books with hidden identities so renamed books cannot grant arbitrary abilities.
- Prevented tome books from being consumed in crafting recipes.
- Added proper hidden identity data to the fourth cure book.
- Corrected Fire Breath/Magic Fire Breath to share a cooldown family.
- Added Fire Breath and Magic Fire Breath to staff give-tome completion.

## Werewolves

- Added Werewolves as a full third faction with three stages.
- Added sneaking bite conversion with distance cancellation, warning timing, and configurable completion time.
- Added nonlethal bare-hand killing blows that convert the victim into a Stage 1 Werewolf.
- Added the experience-bar hunger system, meat diet, promotion/demotion, and post-stage-change immunity.
- Added Stage 2 and Stage 3 claw multipliers, sweep effects, and growls.
- Added nearby pack bonuses with configurable radius and refresh timing.
- Added iron-as-silver weapon weakness.
- Added the Stage 2+ **Feral Charge** ability with per-stage launch power.
- Added Primal beacon ownership and three-faction beacon-majority calculation.
- Added staff stage, cap, promotion-ban, and status controls.

## Thralls and blood

- Added persistent primary and optional secondary vampire blood bonds.
- Added bottle-count or timer-based three-stage progression.
- Added configurable owner/global caps, dual masters, and automatic unthralling on vampire conversion.
- Added self-bottled vampire blood with stage restrictions, health cost, cooldown, and character-profile lore.
- Added optional human self-draw and animal blood collection controls.
- Added role-aware drinking behavior for vampire, thrall, and plain blood.
- Added probabilistic commands by bond stage, forced offers, stay, punish, locate, find-master, and blood-taste mechanics.
- Added withdrawal, separation anxiety, boss bars, holy-water bond reduction, and the Stage 3 vampirism choice.
- Added up to five character profiles and used the active character identity throughout phone contacts and social features.
- Added consent preferences for being a thrall, owning a thrall, both, neither, or attempted interaction.
- Fixed Vampire Blood consumption to use a drink interaction, consume one vial, return glass, and enter the correct bond/thirst path.
- Kept ordinary Crimson Blood unavailable to humans.

## Ghosts, permanent death, and revival

- Added a post-permadeath choice between normal spectator mode and interactive ghost mode.
- Added ghost flight/noclip, invisibility, interaction restrictions, and limited world interaction.
- Added per-player reveal/hide controls and staff release controls.
- Added private voice haunting through Simple Voice Chat.
- Added per-ghost view/simulation distances to limit chunk load.
- Added the four-book Rite of Return, nighttime/blood/beacon requirements, and configurable returned faction.
- Added Ghoul revival with reduced maximum health, no tome use, and a blood bond to the ritual source.
- Added configurable anonymous permanent-death broadcasts.
- Added confirmed player self-permadeath and staff forced-permadeath messages.
- Added configurable vampire-kill death thresholds and staking-stage rules.
- Fixed ghost state cleanup during session resets.
- Added clear warnings when permanent-death data cannot be read or written.

## Human roles, player setup, kits, and sessions

- Added Vampire Hunter, Tracker, and Medic human roles.
- Added opt-in role pools, random assignment, mutual-exclusion rules, cooldowns, and role removal on vampire conversion.
- Added `/playersetup` for character identity, starting-vampire entry, role preferences, thrall preferences, and skin setup.
- Added the multi-step `/pow admin init` wizard with destructive-action warning, vampire selection modes, role counts, session timing, build/break phases, starter-kit options, and final confirmation.
- Added `/gamestart` session timers, cancellation, ending, and resuming.
- Added configurable starter kits, per-character items, delayed join delivery, builder limits, food-kit records, and kit administration commands.
- Restricted the immersion chat warning to active sessions instead of downtime/build phases.

## Beacons, worlds, vaults, and map flow

- Added contested beacon capture: an enemy faction inside the capture radius stops conversion.
- Added the Primal Werewolf beacon state and updated majority bonuses for three sides.
- Added configurable conversion radius, timing, cooldowns, human speed, final-stand speed, announcement delay, and corrupted-beacon rules.
- Added beacon validation, repair, refresh, cleanup, debug, and statistics tools.
- Added Trial Key vaults for tome rewards and ominous cure/revival rewards with once-per-player tracking.
- Added hot-swappable world templates with per-world settings, beacon data, session defaults, and automatic example files.
- Fixed commands that assumed the main world was literally named `world`.
- Made blood moons follow the active world after a map swap.
- Rebuilt barrier behavior for ghosts/spectators, mounts, per-player exemptions, and staff raise/lower controls.

## Resource packs and client visuals

- Added plugin-managed WerePires resource-pack delivery with configurable URL, SHA-1, prompt, required state, and delay.
- Routed pack delivery through the existing vampire texture-pack manager to prevent racing duplicate requests.
- Changed the WerePires pack to a stacked request with a stable UUID so the `server.properties` pack remains loaded underneath it.
- Added phone item models/colors and updated legacy `phone_*` items when colors change.
- Added source texture packs, shield/item assets, phone assets, compatibility-mod patches, server test scripts, and reference mod/plugin JARs used by the development environment.

## Cellphone system

- Replaced the CellPhone Skript with a native Paper dialog phone and tagged colored phone item.
- Added atomic UUID-keyed `phone-data.json` persistence and one-time import of all eight legacy Skript YAML files without deleting the originals.
- Added contact favorites, private nicknames, blocking, character-name identity, shift-right-click contact creation, and offline users.
- Added persistent direct messages, unread state, eight-message paging, offline delivery, notifications, and recent/full click-to-copy exports.
- Added GPS save, rename, share, delete, 50-pin limit, and live distance/direction tracking.
- Added private notes with edit, pin, delete, and 100-note limit.
- Added permanent per-character social handles, discovery, follow feeds, 200-character posts, reactions, deletion, and six-post pages.
- Added persistent group creation, ownership transfer, ten-member limit, messages, per-member read watermarks, paging, exports, notifications, and group calls.
- Added Blackjack, Higher or Lower, Memory Sequence, Wordle, Connect Four, Tic-Tac-Toe, and Rock Paper Scissors.
- Added chips, wagers, escrow/refunds, wins/losses, streaks, best scores, leaderboards, multiplayer challenges, turn notifications, and live match-screen refreshes.
- Expanded Wordle to 112 answers and changed feedback to correctly scored colored letters.
- Added native private/group calls, ringing timeouts, missed calls, DND/busy/offline checks, answer/decline/hangup aliases, call-only mute/deafen, and two-way speakerphone.
- Replaced the incorrect voice listener with microphone packet capture and prevented recursive retransmission/echo.
- Preserved normal proximity voice while routing phone audio through private channels.
- Added phone color, DND, silent, vibrate, and disable-refresh preferences.
- Added `/phonereset confirm`/`/cpreset`, preserving identity/settings/handles while clearing session data and resetting counters.
- Fixed contact lookup for multiplayer game challenges and made main-hand air clicks open reliably even after another plugin cancels the event.
- In 1.15.9, fixed scoped unread totals, group unread aggregation, call blocking, direct-call labels, and missed-call labels.

## Other inherited-system fixes

- Fixed off-hand feeding consuming or checking the wrong item and duplicate firing when both hands held items.
- Fixed vampire turning state being forgotten across restart.
- Fixed stored armor serialization by using Paper's item serialization.
- Fixed normal spectators being pushed by the world barrier.
- Fixed the existing source typo identified during decompilation cleanup.
- Added death/hearts counter administration and configurable final-stand/death penalties.
- Added Silver Arrows as craftable non-glowing spectral arrows.
- Added configurable passive-animal morning spawning and weighted species selection.
- Added nonessential-log suppression.

## Version checkpoints after the broad fork

- **1.14.1–1.14.2:** Vampire Blood drinking, Fae bargain life binding/stage floor, Fire Breath naming/cooldowns, permadeath messages, and missing command completion.
- **1.15:** native cellphone foundation, social/groups, voice calls, games, migration/reset, and final CellPhone parity.
- **1.15.1:** private phone channels, mute/deafen, two-way speakerphone, and packaging cleanup.
- **1.15.2:** phone-audio recursion protection.
- **1.15.3–1.15.5:** resource-pack delivery, character contact shortcut, microphone routing, and removal of duplicate pack requests.
- **1.15.6:** Wordle scoring, reliable air-click opening, live multiplayer screen refresh, and contact-key fix.
- **1.15.7:** 112-word Wordle pool.
- **1.15.8:** stable stacked resource-pack delivery.
- **1.15.9:** unread/call privacy fixes and complete fork documentation.
- **1.15.10:** source-level completeness audit; restored the unreachable Blood Moon admin command and every handler-supported minor alias omitted from the Brigadier tree.
- **1.15.11:** extended the audit across every system: restored additional ability/Fae/stage aliases, corrected command manifests and permissions, centralized duplicate listener registration, completed mutable-state migration, added safe legacy data-folder migration, corrected admin paths and branding, and added a repeatable whole-source audit script.
