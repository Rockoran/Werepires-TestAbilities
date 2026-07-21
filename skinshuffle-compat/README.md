# SkinShuffle — VampireSMP compatibility patch

This folder holds a **patched build of the SkinShuffle Fabric mod** (based on the official
`2.10.0` release, targeting Minecraft **1.21.10**) plus the source files that were changed.

The patch lets the **server push a skin onto the client without forcing a reconnect**, so
vampire tier-up / tier-down skin changes apply instantly and the player's own first-person /
F5 view matches what everyone else sees.

---

## What players install

`testbuilds/VampireSMP-Compatibility-Mod-2.10.0+1.21.10.jar`  →  drop into the client's `.minecraft/mods/` folder
(alongside Fabric Loader + Fabric API). Players **without** the mod are unaffected — the
server's extra packets are simply ignored by vanilla clients.

This jar is functionally identical to stock SkinShuffle 2.10.0 for normal use. It only **adds**
one new server→client channel; nothing existing was removed.

---

## The problem this fixes

Stock SkinShuffle decides the local player's rendered skin **entirely client-side**: its
`getSkin()` mixin always returns the locally-chosen preset for your own model. So when the
**server** changes your GameProfile texture (what the VampireSMP plugin does on a tier change):

* other players + the tab list update correctly (they read the server profile), but
* **your own model keeps rendering the stale local preset** — the mismatch you saw
  ("tab shows human, screen shows S2"), and
* SkinShuffle keeps nagging you to **reconnect** because it thinks the server doesn't speak
  its protocol (no handshake was ever sent by the plugin).

## How the patch fixes it

Two new server→client plugin-message channels, both spoken by the VampireSMP plugin:

| Channel | Direction | Purpose |
|---|---|---|
| `skinshuffle:handshake` | S2C | Tells the mod the server is SkinShuffle-aware → **suppresses the reconnect prompt**. (Stock channel; the plugin now actually sends it.) |
| `skinshuffle:force_skin` | S2C | **New.** Pushes a texture `Property` the client applies to its **own** model immediately — no respawn/reconnect. A `clear` flag releases control back to the local preset. |

Priority rule on the client: a server-forced skin **overrides** the local preset for your own
model. When you change your skin yourself through the SkinShuffle GUI, the plugin sends a
`force_skin{clear}` so your own selection renders again.

---

## Mod source changes (vs. stock 2.10.0)

Snapshots of the edited files live in `patched-sources/` for reference. Changes:

1. **`networking/ForceSkinPayload.java`** *(new)* — S2C payload `skinshuffle:force_skin`
   carrying `boolean clear` + an optional texture `Property` (byte layout mirrors
   `SkinRefreshPayload`).
2. **`SkinShuffle.java`** — registers `ForceSkinPayload` in `PayloadTypeRegistry.playS2C()`.
3. **`networking/ClientSkinHandling.java`** — holds the server-forced `SkinTextures`; a
   global receiver resolves the incoming `Property` via the client skin provider, stores it,
   refreshes the local player, and marks the handshake as taken place. Cleared on disconnect.
4. **`mixin/PlayerEntityMixin.java`** — `getSkin()` returns the server-forced skin (when set)
   for the local player, taking priority over the chosen preset.

### Build-toolchain changes (needed for a clean build today)

* `versions/1.21.10/gradle.properties` — `runtime.minecraftcapes` bumped to
  `fabric-1.21.10-1.0.1` (the originally-pinned `fabric-1.21.9-12.4.0` was removed from
  Modrinth).
* `build.gradle` — `fabric-loom` bumped `1.11-SNAPSHOT` → `1.13-SNAPSHOT` (the newer
  minecraftcapes artifact requires Loom ≥ 1.13.6).

Neither affects runtime behavior — both deps are `modCompileOnly`.

---

## Rebuilding the mod

Mod source lives at `C:\Users\lande\Downloads\SkinShuffle-source\SkinShuffle-2.10.0`.

```
./gradlew :1.21.10:build -x test
```

Output: `versions/1.21.10/build/libs/VampireSMP-Compatibility-Mod-2.10.0+1.21.10.jar`
(copied into the repo as `testbuilds/VampireSMP-Compatibility-Mod-2.10.0+1.21.10.jar`).

---

## Plugin side (already in the VampireSMP jar)

`pow.crimson2.managers.SkinShuffleManager` registers the two outgoing channels and:

* sends `handshake` shortly after join (delayed so the client's channel registration has
  arrived),
* sends `force_skin` whenever it applies a **stage** skin (tier up/down, returning-vampire
  login, `/skin apply`),
* sends `force_skin{clear}` when the **player** changes their own skin via the mod GUI
  (received on `skinshuffle:skin_refresh`).
