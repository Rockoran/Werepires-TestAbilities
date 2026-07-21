package pow.crimson2.managers;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import pow.crimson2.VampireSMPPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player stage skin registrations.
 *
 * Players use the SkinShuffle Fabric mod (or any method) to switch to the skin
 * they want for a given stage, then run:
 *   /skin register human     — saves their current skin as the "human" skin
 *   /skin register stage1    — saves as Stage 1 vampire skin
 *   /skin register stage2
 *   /skin register stage3
 *
 * When a player tiers up or down, the plugin automatically applies the
 * registered skin for the new stage. When cured, the "human" skin is restored.
 *
 * Data is stored per-player at:
 *   plugins/VampireSMP/player_skins/{uuid}.json
 *
 * The feature is gated by config key  vampire-skins.enabled  (default false).
 */
public class SkinShuffleManager implements PluginMessageListener {

    /** The plugin-message channel the SkinShuffle Fabric mod uses to push skin changes (C2S). */
    public static final String CHANNEL = "skinshuffle:skin_refresh";

    /**
     * Handshake channel (S2C). The SkinShuffle mod treats a server that sends this as
     * "SkinShuffle-aware" and stops prompting the player to reconnect after every skin
     * change. The body is empty (matches the mod's {@code HandshakePayload}).
     */
    public static final String HANDSHAKE_CHANNEL = "skinshuffle:handshake";

    /**
     * Force-skin channel (S2C). Added by our SkinShuffle compatibility patch. Lets the
     * server push a texture directly onto the local client so it renders the new skin on
     * its OWN model instantly, without a respawn/reconnect.
     *
     * Body layout (matches {@code ForceSkinPayload} in the patched mod):
     *   boolean clear
     *   if (!clear) {
     *       boolean hasSignature
     *       varstr  name           ("textures")
     *       varstr  value          (base64)
     *       varstr  signature      (only when hasSignature)
     *   }
     */
    public static final String FORCE_SKIN_CHANNEL = "skinshuffle:force_skin";

    /** Valid stage keys in normalized (lowercase) form. */
    public static final Set<String> VALID_STAGES =
            Set.of("human", "stage1", "stage2", "stage3", "cured", "ghost");

    private final VampireSMPPlugin plugin;
    private final File skinsFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * In-memory cache:  UUID → (stage → ProfileProperty).
     * Populated when a player joins or registers a skin.
     */
    private final Map<UUID, Map<String, ProfileProperty>> cache = new ConcurrentHashMap<>();

    public SkinShuffleManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.skinsFolder = new File(plugin.getDataFolder(), "player_skins");
        if (!skinsFolder.exists()) skinsFolder.mkdirs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vampire-skins.enabled", false);
    }

    /**
     * Capture the player's CURRENT skin (as active via SkinShuffle or otherwise)
     * and save it as their registered skin for {@code stage}.
     *
     * @param player    the player running /skin register
     * @param stage     normalised stage key ("human", "stage1", "stage2", "stage3")
     */
    public void registerCurrentSkin(Player player, String stage) {
        ProfileProperty textures = getTexturesProperty(player.getPlayerProfile());
        if (textures == null) {
            player.sendMessage("§cCould not read your current skin texture. Try re-equipping it.");
            return;
        }

        // Save to in-memory cache
        cache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(stage, textures);

        // Persist to disk
        saveSkins(player.getUniqueId());

        player.sendMessage("§a[SkinShuffle] Registered your current skin for stage §e"
                + displayName(stage) + "§a.");
        plugin.logInfo("[SkinShuffle] " + player.getName()
                + " registered skin for stage '" + stage + "'");
    }

    /**
     * Apply the registered skin for this vampire stage (1, 2, or 3).
     * Does nothing if the player has no skin registered for that stage.
     */
    public void applyVampireSkin(Player player, int stage) {
        if (!isEnabled()) return;
        applyStageSkin(player, stageToKey(stage));
    }

    /**
     * Apply the player's registered "human" skin (called on cure / setHuman).
     * Does nothing if no human skin is registered.
     */
    public void restoreHumanSkin(Player player) {
        if (!isEnabled()) return;
        applyStageSkin(player, "human");
    }

    /**
     * Apply the registered "Cured" skin (CuredVampire tag). Falls back to the registered
     * "human" skin if no cured skin is set, so a cured player never keeps their vampire skin.
     */
    public void applyCuredSkin(Player player) {
        if (!isEnabled()) return;
        if (hasStageRegistered(player, "cured")) {
            applyStageSkin(player, "cured");
        } else {
            applyStageSkin(player, "human");
        }
    }

    /**
     * Apply the registered "Ghost" skin (used when a player enters ghost/haunt mode). If no
     * ghost skin is registered this is a no-op, leaving their current skin in place.
     */
    public void applyGhostSkin(Player player) {
        if (!isEnabled()) return;
        applyStageSkin(player, "ghost");
    }

    /**
     * Re-apply the skin matching the player's current state. Used on login for returning
     * vampires. Priority: ghost &gt; cured &gt; vampire stage &gt; human.
     */
    public void applyExistingVampireSkin(Player player) {
        if (!isEnabled()) return;
        if (plugin.getGhostModeManager() != null && plugin.getGhostModeManager().isGhost(player)) {
            applyGhostSkin(player);
            return;
        }
        if (player.getScoreboardTags().contains("CuredVampire")) {
            applyCuredSkin(player);
            return;
        }
        int stage = currentVampireStage(player);
        applyStageSkin(player, stageToKey(stage));
    }

    /**
     * Manually apply a specific registered skin (used by /skin apply).
     */
    public void applyRegisteredSkin(Player player, String stage) {
        applyStageSkin(player, stage);
    }

    /**
     * Remove the registered skin for a specific stage.
     */
    public void clearRegisteredSkin(Player player, String stage) {
        Map<String, ProfileProperty> skins = cache.get(player.getUniqueId());
        if (skins != null) skins.remove(stage);
        saveSkins(player.getUniqueId());
        player.sendMessage("§a[SkinShuffle] Cleared registered skin for stage §e"
                + displayName(stage) + "§a.");
    }

    /**
     * Returns a summary of which stages have skins registered for the player.
     */
    public String getRegistrationStatus(Player player) {
        Map<String, ProfileProperty> skins = cache.getOrDefault(player.getUniqueId(), Map.of());
        StringBuilder sb = new StringBuilder("§7Registered skins:");
        for (String stage : new String[]{"human", "stage1", "stage2", "stage3", "cured", "ghost"}) {
            sb.append("\n  ").append(displayName(stage)).append(": ");
            sb.append(skins.containsKey(stage) ? "§a✔ registered" : "§8none");
        }
        return sb.toString();
    }

    /** Load skins for a player from disk into the in-memory cache. */
    public void loadSkins(Player player) {
        File file = playerFile(player.getUniqueId());
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null) return;
            Map<String, ProfileProperty> map = new HashMap<>();
            for (String stage : VALID_STAGES) {
                if (!root.has(stage)) continue;
                JsonObject entry = root.getAsJsonObject(stage);
                String value = entry.get("value").getAsString();
                String sig   = entry.has("signature") && !entry.get("signature").isJsonNull()
                        ? entry.get("signature").getAsString() : null;
                map.put(stage, new ProfileProperty("textures", value, sig));
            }
            if (!map.isEmpty()) {
                cache.put(player.getUniqueId(), map);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SkinShuffle] Failed to load skins for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /** Returns true if the player has at least one stage skin registered. */
    public boolean hasAnySkins(Player player) {
        Map<String, ProfileProperty> skins = cache.get(player.getUniqueId());
        return skins != null && !skins.isEmpty();
    }

    /** Returns true if the player has a skin registered for the given stage key. */
    public boolean hasStageRegistered(Player player, String stage) {
        Map<String, ProfileProperty> skins = cache.get(player.getUniqueId());
        return skins != null && skins.containsKey(stage);
    }

    /**
     * Returns the raw texture ProfileProperty for a given stage, or null.
     * Used by SkinPreviewGui to build player-head items.
     */
    public ProfileProperty getRegisteredProperty(Player player, String stage) {
        Map<String, ProfileProperty> skins = cache.get(player.getUniqueId());
        return (skins != null) ? skins.get(stage) : null;
    }

    /**
     * Copy the registered skin from {@code fromStage} to {@code toStage}.
     * Used by the "Use Same as Human" button in the setup wizard.
     */
    public void copySkin(Player player, String fromStage, String toStage) {
        Map<String, ProfileProperty> skins = cache.get(player.getUniqueId());
        if (skins == null || !skins.containsKey(fromStage)) return;
        ProfileProperty copy = skins.get(fromStage);
        cache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(toStage, copy);
        saveSkins(player.getUniqueId());
        player.sendMessage("§a[SkinShuffle] Copied §e" + displayName(fromStage)
                + "§a skin to §e" + displayName(toStage) + "§a.");
    }

    /** Remove the player's data from the in-memory cache on quit. */
    public void clearCache(Player player) {
        cache.remove(player.getUniqueId());
    }

    public void shutdown() {
        cache.clear();
    }

    // ── SkinShuffle mod bridge (plugin messages) ──────────────────────────────

    /**
     * Receives the skin change the SkinShuffle Fabric mod sends whenever the
     * player applies a new skin through its in-game menu.
     *
     * Without this listener the server's GameProfile never updates when the mod
     * changes a skin — so the tab list, other players' views, and our own stage
     * skin logic all see the stale login-time texture instead of whatever the
     * player currently has active.
     *
     * SkinShuffle has used two packet formats across versions; we try both:
     *
     *   Format A (older / bridge plugin era):
     *     byte        hasSignature
     *     varstr      textureValue   (base64)
     *     varstr      modelType      ("slim" | "default")
     *     varstr?     signature      (only when hasSignature == 1)
     *
     *   Format B (newer, uses Mojang Property stream codec):
     *     varstr      name           (always "textures")
     *     varstr      textureValue   (base64)
     *     byte        hasSignature
     *     varstr?     signature
     *
     * Both are tried; if neither parses cleanly the message is silently ignored.
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        ProfileProperty prop = parseSkinRefresh(message);
        if (prop == null) {
            plugin.getLogger().fine("[SkinShuffle] Could not parse skin_refresh packet from "
                    + player.getName() + " (length=" + message.length + ")");
            return;
        }

        // Apply on the main thread — setPlayerProfile is not thread-safe
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            // The PLAYER changed their own skin via the mod GUI, so release any server-forced
            // skin and let their own selection render. The server profile is still updated so
            // the tab list / other players stay in sync.
            applySkinSync(player, prop, false);
            plugin.logInfo("[SkinShuffle] Applied mod skin change for " + player.getName());
        });
    }

    /** Try the known packet formats; returns {@code null} if none parse cleanly. */
    private static ProfileProperty parseSkinRefresh(byte[] msg) {
        // Format A — the current SkinShuffle SkinRefreshPayload codec:
        //   boolean hasSignature, varstr name ("textures"), varstr value (base64), varstr? signature
        try {
            int[] pos = {0};
            boolean hasSig = msg[pos[0]++] != 0;
            String name = readVarStr(msg, pos);
            String value = readVarStr(msg, pos);
            String signature = hasSig ? readVarStr(msg, pos) : null;
            if (isValidTextureValue(value)) {
                return new ProfileProperty(
                        (name == null || name.isEmpty()) ? "textures" : name, value, signature);
            }
        } catch (Exception ignored) {}

        // Format B — older bridge format:
        //   varstr name, varstr value, byte hasSignature, varstr? signature
        try {
            int[] pos = {0};
            readVarStr(msg, pos); // name ("textures")
            String value = readVarStr(msg, pos);
            boolean hasSig = pos[0] < msg.length && msg[pos[0]++] != 0;
            String signature = hasSig ? readVarStr(msg, pos) : null;
            if (isValidTextureValue(value)) {
                return new ProfileProperty("textures", value, signature);
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * A real skin texture property value is base64-encoded JSON describing the skin URL.
     * This guards against mis-parsing a length-prefixed field (e.g. the literal name
     * {@code "textures"}) as the value, which would otherwise apply a garbage property and
     * render the default Steve/Alex skin.
     */
    private static boolean isValidTextureValue(String value) {
        if (value == null || value.length() < 32) return false;
        try {
            String decoded = new String(
                    java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            return decoded.contains("textures") || decoded.contains("SKIN")
                    || decoded.contains("http");
        } catch (Exception e) {
            return false;
        }
    }

    /** Read a Minecraft-style VarInt-prefixed UTF-8 string. */
    private static String readVarStr(byte[] data, int[] pos) {
        int len = 0, shift = 0;
        byte b;
        do {
            b = data[pos[0]++];
            len |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        String s = new String(data, pos[0], len, StandardCharsets.UTF_8);
        pos[0] += len;
        return s;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void applyStageSkin(Player player, String stage) {
        if (stage == null) return;
        Map<String, ProfileProperty> skins = cache.get(player.getUniqueId());
        if (skins == null || !skins.containsKey(stage)) return;

        ProfileProperty prop = skins.get(stage);
        // Server-driven stage change: force the skin onto the client so its own model
        // updates instantly, without the SkinShuffle reconnect prompt.
        applySkinSync(player, prop, true);
    }

    /**
     * Apply a texture property to the player and force all clients — including
     * the player's own — to render the new skin.  Must be called on the main thread.
     *
     * Two separate update paths:
     *  1. Other players: hidePlayer + showPlayer forces their client to drop and
     *     re-request the player's model, which picks up the new GameProfile texture.
     *  2. The player themselves: setPlayerProfile() calls Paper's refreshPlayer()
     *     internally, but we also call it explicitly (via reflection) as a safety net
     *     so the player sees their own skin in F5 / inventory / armour stand mirrors.
     */
    private void applySkinSync(Player player, ProfileProperty textures, boolean forceClient) {
        if (!player.isOnline()) return;

        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");
        profile.setProperty(textures);
        player.setPlayerProfile(profile);

        // Tell the patched SkinShuffle mod what the client should render on its OWN model.
        //  • forceClient == true  → server decided this skin (stage change): push it so the
        //    client renders it instantly without a reconnect.
        //  • forceClient == false → the player chose this skin themselves via the mod GUI:
        //    release any previous server-forced skin so their own preset renders.
        if (forceClient) {
            sendForceSkin(player, textures);
        } else {
            sendClearForceSkin(player);
        }

        // Ensure the player's OWN client refreshes (F5 view, inventory, etc.)
        forceSelfRefresh(player);

        // Force every OTHER player's client to re-render this player's skin
        pow.crimson2.ghost.GhostModeManager ghosts = plugin.getGhostModeManager();
        boolean playerIsGhost = ghosts != null && ghosts.isGhost(player);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) continue;
            viewer.hidePlayer(plugin, player);
            // A ghost must stay hidden from anyone they haven't revealed themselves to — the
            // showPlayer below would otherwise un-hide them on every skin refresh. Keep them
            // listed in tab, though, so the refresh doesn't drop a haunting ghost off the list.
            if (playerIsGhost && !ghosts.getRevealedTo(player).contains(viewer.getUniqueId())) {
                try {
                    viewer.listPlayer(player);
                } catch (Throwable ignored) {
                }
                continue;
            }
            viewer.showPlayer(plugin, player);
        }

        plugin.logInfo("[SkinShuffle] Applied skin to " + player.getName());
    }

    // ── SkinShuffle mod outbound bridge (handshake + force_skin) ───────────────

    /**
     * Sends the SkinShuffle handshake to a player. The patched mod (and the stock mod) treat
     * this as proof the server speaks the SkinShuffle protocol and stop showing the
     * "reconnect required" prompt after skin changes. Safe to call even if the player isn't
     * running the mod — the message is simply dropped.
     */
    public void sendHandshake(Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            player.sendPluginMessage(plugin, HANDSHAKE_CHANNEL, new byte[0]);
        } catch (Exception e) {
            plugin.getLogger().fine("[SkinShuffle] handshake send failed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /** Push a server-forced skin to the client (renders without reconnect). */
    public void sendForceSkin(Player player, ProfileProperty textures) {
        if (player == null || !player.isOnline()) return;
        if (textures == null) {
            sendClearForceSkin(player);
            return;
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            out.write(0); // clear = false
            boolean hasSig = textures.getSignature() != null && !textures.getSignature().isEmpty();
            out.write(hasSig ? 1 : 0);
            String name = textures.getName() == null ? "textures" : textures.getName();
            writeVarStr(out, name);
            writeVarStr(out, textures.getValue() == null ? "" : textures.getValue());
            if (hasSig) writeVarStr(out, textures.getSignature());
            player.sendPluginMessage(plugin, FORCE_SKIN_CHANNEL, out.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().fine("[SkinShuffle] force_skin send failed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /** Tell the client to release any server-forced skin and render its own preset again. */
    public void sendClearForceSkin(Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            player.sendPluginMessage(plugin, FORCE_SKIN_CHANNEL, new byte[]{1}); // clear = true
        } catch (Exception e) {
            plugin.getLogger().fine("[SkinShuffle] force_skin (clear) send failed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /** Write a Minecraft-style VarInt-prefixed UTF-8 string. */
    private static void writeVarStr(java.io.ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    /** Write a Minecraft-style VarInt. */
    private static void writeVarInt(java.io.ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    /**
     * Calls {@code CraftPlayer.refreshPlayer()} via reflection.
     *
     * What this does under the hood (Paper internals):
     *  • Sends {@code ClientboundPlayerInfoUpdatePacket} with the new texture to all
     *    connected clients so the tab list / player models update.
     *  • Sends a {@code ClientboundRespawnPacket} (keepAllPlayerData = true variant)
     *    to the player themselves — this is the packet that makes the player's OWN
     *    client reload the skin without actually respawning them in the world.
     *  • Re-teleports the player to their current position so the respawn packet
     *    does not displace them.
     *
     * Paper's {@code setPlayerProfile()} already calls this internally in most builds,
     * but the explicit call here is a reliable safety net across all 1.21.x Paper builds
     * in case the internal call path changes.
     *
     * Failure is silent — if the method is not available the skin still updates for
     * all OTHER players via the hidePlayer/showPlayer loop; only the player's own
     * first-person view may lag until their next chunk reload or relog.
     */
    private void forceSelfRefresh(Player player) {
        try {
            java.lang.reflect.Method m = player.getClass().getDeclaredMethod("refreshPlayer");
            m.setAccessible(true);
            m.invoke(player);
        } catch (NoSuchMethodException ignored) {
            // Not exposed in this Paper build — setPlayerProfile() handles it internally
        } catch (Exception e) {
            plugin.getLogger().fine("[SkinShuffle] forceSelfRefresh unavailable: " + e.getMessage());
        }
    }

    private void saveSkins(UUID uuid) {
        Map<String, ProfileProperty> skins = cache.get(uuid);
        JsonObject root = new JsonObject();
        if (skins != null) {
            for (Map.Entry<String, ProfileProperty> entry : skins.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("value", entry.getValue().getValue());
                if (entry.getValue().getSignature() != null) {
                    obj.addProperty("signature", entry.getValue().getSignature());
                }
                root.add(entry.getKey(), obj);
            }
        }
        File file = playerFile(uuid);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(root, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[SkinShuffle] Failed to save skins for "
                    + uuid + ": " + e.getMessage());
        }
    }

    private File playerFile(UUID uuid) {
        return new File(skinsFolder, uuid.toString() + ".json");
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Normalise user input to a stage key.
     * Accepts: "human"/"0", "stage1"/"1", "stage2"/"2", "stage3"/"3"
     * Returns null if unrecognised.
     */
    public static String normaliseStage(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase().trim()) {
            case "human", "0"  -> "human";
            case "stage1", "1" -> "stage1";
            case "stage2", "2" -> "stage2";
            case "stage3", "3" -> "stage3";
            case "cured"       -> "cured";
            case "ghost"       -> "ghost";
            default            -> null;
        };
    }

    private static String stageToKey(int stage) {
        return switch (stage) {
            case 1 -> "stage1";
            case 2 -> "stage2";
            case 3 -> "stage3";
            default -> "human";
        };
    }

    private static int currentVampireStage(Player player) {
        if (player.getScoreboardTags().contains("vampire_stage3")) return 3;
        if (player.getScoreboardTags().contains("vampire_stage2")) return 2;
        if (player.getScoreboardTags().contains("vampire_stage1")) return 1;
        return 0;
    }

    private static ProfileProperty getTexturesProperty(PlayerProfile profile) {
        for (ProfileProperty p : profile.getProperties()) {
            if ("textures".equals(p.getName())) return p;
        }
        return null;
    }

    public static String displayName(String stage) {
        return switch (stage) {
            case "human"  -> "Human";
            case "stage1" -> "Stage 1";
            case "stage2" -> "Stage 2";
            case "stage3" -> "Stage 3";
            case "cured"  -> "Cured";
            case "ghost"  -> "Ghost";
            default       -> stage;
        };
    }
}
