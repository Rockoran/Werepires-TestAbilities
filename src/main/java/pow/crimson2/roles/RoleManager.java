package pow.crimson2.roles;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.YamlConfiguration;

import pow.crimson2.VampireSMPPlugin;

/**
 * Runtime state for the vampiric roles system (vampire_hunter, medic, tracker).
 * Persists role pools, game admins, vampire pool, and config values to
 * plugins/WerePires/roles_data.yml.
 *
 * Note: /findvampires applies the GLOWING potion effect to nearby vampires for the
 * scan duration. Unlike the original SkBee version this glow is visible to all
 * players (no per-player packet trick is available without NMS/ProtocolLib).
 */
public class RoleManager {

    public static final List<String> KNOWN_ROLES =
            Collections.unmodifiableList(Arrays.asList("vampire_hunter", "medic", "tracker"));

    private final VampireSMPPlugin plugin;
    private File dataFile;

    // Role state
    private final Map<String, Boolean>       roleEnabled = new HashMap<>();
    private final Map<String, List<String>>  rolePools   = new HashMap<>();

    // Shared lists
    private final List<String> gameAdmins   = new ArrayList<>();
    private final List<String> vampirePool  = new ArrayList<>();

    // Configurable values (persisted)
    private int fvCooldownMinutes   = 5;
    private int fvDurationSeconds   = 10;
    private int trackerCooldownMins = 5;
    private int trackerTrackMins    = 4;
    private int vampireCount        = 2;

    // Per-player runtime state (in-memory only, reset on restart)
    private final Map<UUID, Integer> fvCooldownTimer    = new HashMap<>();
    private final Map<UUID, Boolean> fvOnCooldown       = new HashMap<>();
    private final Map<UUID, Integer> trackerActiveTimer  = new HashMap<>();
    private final Map<UUID, Boolean> trackerActive       = new HashMap<>();
    private final Map<UUID, Integer> trackerCdTimer      = new HashMap<>();
    private final Map<UUID, Boolean> trackerOnCooldown   = new HashMap<>();
    private final Map<UUID, UUID>    trackerTarget       = new HashMap<>();

    private BukkitRunnable tickTask;

    public RoleManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.dataFile = new File(plugin.getDataFolder(), "roles_data.yml");

        for (String role : KNOWN_ROLES) {
            roleEnabled.put(role, true);
            rolePools.put(role, new ArrayList<>());
        }

        loadData();
        startTick();
    }

    // =========================================================================
    // Role enable / disable
    // =========================================================================

    public boolean isRoleEnabled(String role) {
        return roleEnabled.getOrDefault(role, false);
    }

    public void setRoleEnabled(String role, boolean enabled) {
        roleEnabled.put(role, enabled);
        saveData();
    }

    // =========================================================================
    // Role pools
    // =========================================================================

    public List<String> getRolePool(String role) {
        return rolePools.computeIfAbsent(role, k -> new ArrayList<>());
    }

    public boolean addToPool(String role, String playerName) {
        List<String> pool = getRolePool(role);
        if (pool.contains(playerName)) return false;
        pool.add(playerName);
        saveData();
        return true;
    }

    public boolean removeFromPool(String role, String playerName) {
        boolean removed = getRolePool(role).remove(playerName);
        if (removed) saveData();
        return removed;
    }

    public void clearPool(String role) {
        getRolePool(role).clear();
        saveData();
    }

    // =========================================================================
    // Game admins
    // =========================================================================

    public List<String> getGameAdmins() {
        return Collections.unmodifiableList(gameAdmins);
    }

    public boolean addGameAdmin(String name) {
        if (gameAdmins.contains(name)) return false;
        gameAdmins.add(name);
        saveData();
        return true;
    }

    public boolean removeGameAdmin(String name) {
        boolean removed = gameAdmins.remove(name);
        if (removed) saveData();
        return removed;
    }

    public void clearGameAdmins() {
        gameAdmins.clear();
        saveData();
    }

    // =========================================================================
    // Vampire pool
    // =========================================================================

    public List<String> getVampirePool() {
        return Collections.unmodifiableList(vampirePool);
    }

    public boolean addToVampirePool(String name) {
        if (vampirePool.contains(name)) return false;
        vampirePool.add(name);
        saveData();
        return true;
    }

    public boolean removeFromVampirePool(String name) {
        boolean removed = vampirePool.remove(name);
        if (removed) saveData();
        return removed;
    }

    public void clearVampirePool() {
        vampirePool.clear();
        saveData();
    }

    // =========================================================================
    // Config getters / setters
    // =========================================================================

    public int  getFvCooldownMinutes()  { return fvCooldownMinutes; }
    public void setFvCooldownMinutes(int v)  { fvCooldownMinutes = v;   saveData(); }

    public int  getFvDurationSeconds()  { return fvDurationSeconds; }
    public void setFvDurationSeconds(int v)  { fvDurationSeconds = v;   saveData(); }

    public int  getTrackerCooldownMins() { return trackerCooldownMins; }
    public void setTrackerCooldownMins(int v) { trackerCooldownMins = v; saveData(); }

    public int  getTrackerTrackMins() { return trackerTrackMins; }
    public void setTrackerTrackMins(int v) { trackerTrackMins = v; saveData(); }

    public int  getVampireCount() { return vampireCount; }
    public void setVampireCount(int v) { vampireCount = v; saveData(); }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns the list of all known role names. Equivalent to KNOWN_ROLES. */
    public List<String> getRoles() { return KNOWN_ROLES; }

    public static String getRoleLabel(String role) {
        switch (role) {
            case "vampire_hunter": return "Vampire Hunter";
            case "medic":          return "Medic";
            case "tracker":        return "Tracker";
            default:               return role;
        }
    }

    public static boolean isVampirePlayer(Player p) {
        Set<String> tags = p.getScoreboardTags();
        return tags.contains("vampire_stage1")
            || tags.contains("vampire_stage2")
            || tags.contains("vampire_stage3");
    }

    /**
     * Sends an announcement to game admins. Falls back to all ops if admin list empty.
     * Also sends to the named winner regardless.
     */
    public void announceRole(String winnerName, String role) {
        String msg = "§6§lROLE SELECTED §8| §f" + winnerName
                   + " §6has been chosen as §f" + getRoleLabel(role) + "§6!";
        broadcastToAdmins(msg);
        // Also deliver to the winner if they aren't an admin/op (they'd get it twice otherwise,
        // but that mirrors the Skript behaviour).
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equals(winnerName)) { p.sendMessage(msg); break; }
        }
    }

    /** Sends a message to game admins (or all ops as fallback). */
    public void broadcastToAdmins(String msg) {
        boolean sent = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (gameAdmins.contains(p.getName())) { p.sendMessage(msg); sent = true; }
        }
        if (!sent) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("vampiresmp.admin")) p.sendMessage(msg);
            }
        }
    }

    // =========================================================================
    // Role assignment
    // =========================================================================

    /**
     * Assigns all enabled roles atomically, respecting mutual exclusion between
     * vampire_hunter and tracker, and skipping any players who are vampires.
     *
     * @param sender     player who receives status messages
     * @param roleCounts desired count per role (null → default 1)
     */
    public void startAllRoles(Player sender, Map<String, Integer> roleCounts) {
        String[] order = {"vampire_hunter", "tracker", "medic"};
        Set<String> taken = new HashSet<>();

        for (String role : order) {
            if (!isRoleEnabled(role)) continue;

            List<String> pool = getRolePool(role);
            if (pool.isEmpty()) {
                sender.sendMessage("§7Skipping §f" + getRoleLabel(role) + "§7 — pool is empty.");
                continue;
            }

            int count = (roleCounts != null && roleCounts.containsKey(role))
                    ? roleCounts.get(role) : 1;
            if (count <= 0) continue;

            String conflict = "vampire_hunter".equals(role) ? "tracker"
                            : "tracker".equals(role)        ? "vampire_hunter"
                            : "";

            List<String> eligible = buildEligible(pool, taken, conflict);
            if (eligible.isEmpty()) {
                sender.sendMessage("§cNo eligible players for §f"
                        + getRoleLabel(role) + "§c. Skipping.");
                continue;
            }

            int picked = 0;
            for (int i = 0; i < count && !eligible.isEmpty(); i++) {
                String winner = eligible.remove(
                        ThreadLocalRandom.current().nextInt(eligible.size()));
                taken.add(winner);
                picked++;
                assignTag(winner, role);
                announceRole(winner, role);
            }
            if (picked < count) {
                sender.sendMessage("§cPool exhausted for §f" + getRoleLabel(role)
                        + "§c — assigned §f" + picked + "§c / §f" + count + "§c.");
            }
        }
    }

    /** Assigns a single role (for /rolestart <role>). */
    public void startSingleRole(Player sender, String role) {
        if (!isRoleEnabled(role)) {
            sender.sendMessage("§cThat role is currently disabled.");
            return;
        }
        List<String> pool = getRolePool(role);
        if (pool.isEmpty()) {
            sender.sendMessage("§cNo players are in the §f" + role + " §cpool.");
            return;
        }

        String conflict = "vampire_hunter".equals(role) ? "tracker"
                        : "tracker".equals(role)        ? "vampire_hunter"
                        : "";

        List<String> eligible = buildEligible(pool, Collections.emptySet(), conflict);
        if (eligible.isEmpty()) {
            sender.sendMessage("§cNo eligible players for §f" + role + "§c.");
            return;
        }

        String winner = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        assignTag(winner, role);
        announceRole(winner, role);
    }

    private List<String> buildEligible(List<String> pool, Set<String> taken, String conflict) {
        List<String> out = new ArrayList<>();
        for (String candidate : pool) {
            if (taken.contains(candidate)) continue;
            Player cp = Bukkit.getPlayerExact(candidate);
            if (cp == null || !cp.isOnline()) continue;
            if (isVampirePlayer(cp)) continue;
            if (!conflict.isEmpty() && cp.getScoreboardTags().contains(conflict)) continue;
            out.add(candidate);
        }
        return out;
    }

    private void assignTag(String playerName, String role) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equals(playerName)) { p.addScoreboardTag(role); break; }
        }
    }

    // =========================================================================
    // /findvampires
    // =========================================================================

    public void activateFindVampires(Player hunter) {
        UUID hid = hunter.getUniqueId();

        if (fvOnCooldown.getOrDefault(hid, false)) {
            hunter.sendMessage("§cFind Vampires is on cooldown! §7("
                    + fvCooldownMinutes + " minute cooldown)");
            return;
        }

        List<Player> found = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(hunter)) continue;
            if (!p.getWorld().equals(hunter.getWorld())) continue;
            if (p.getLocation().distance(hunter.getLocation()) > 50) continue;
            if (isVampirePlayer(p)) found.add(p);
        }

        if (found.isEmpty()) {
            hunter.sendMessage("§7No vampires found within 50 blocks.");
            return;
        }

        hunter.sendMessage("§cFound §f" + found.size()
                + " §cvampire(s) nearby! §7(Cooldown starts after scan ends)");

        int durationTicks = fvDurationSeconds * 20;
        for (Player vamp : found) {
            vamp.addPotionEffect(
                    new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, true, false));
        }

        // Start cooldown after the scan window expires
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fvOnCooldown.put(hid, true);
            fvCooldownTimer.put(hid, fvCooldownMinutes * 60);
        }, durationTicks);
    }

    public void resetFvCooldown(UUID uuid) {
        fvOnCooldown.remove(uuid);
        fvCooldownTimer.remove(uuid);
    }

    // =========================================================================
    // Tracker
    // =========================================================================

    public void setTrackerTarget(UUID trackerUUID, UUID targetUUID) {
        trackerTarget.put(trackerUUID, targetUUID);
    }

    public UUID getTrackerTarget(UUID trackerUUID) {
        return trackerTarget.get(trackerUUID);
    }

    public boolean isTrackerActive(UUID trackerUUID) {
        return trackerActive.getOrDefault(trackerUUID, false);
    }

    public boolean isTrackerOnCooldown(UUID uuid) {
        return trackerOnCooldown.getOrDefault(uuid, false);
    }

    public int getTrackerCooldownRemaining(UUID uuid) {
        return trackerCdTimer.getOrDefault(uuid, 0);
    }

    public int getTrackerActiveRemaining(UUID uuid) {
        return trackerActiveTimer.getOrDefault(uuid, 0);
    }

    /**
     * Updates the tracker's compass to point at the target and manages the
     * active-window timer.  Call this when the tracker right-clicks air/ground.
     */
    public void activateTrackerCompass(Player tracker, Player target) {
        UUID tid = tracker.getUniqueId();
        tracker.setCompassTarget(target.getLocation());
        int dist = (int) tracker.getLocation().distance(target.getLocation());

        if (!trackerActive.getOrDefault(tid, false)) {
            trackerActive.put(tid, true);
            trackerActiveTimer.put(tid, trackerTrackMins * 60);
            tracker.sendActionBar("§bTracking §f" + target.getName()
                    + " §8| §7" + trackerTrackMins + "min §8| §f" + dist + "m");
        } else {
            int rem = trackerActiveTimer.getOrDefault(tid, 0);
            int m = rem / 60, s = rem % 60;
            String sf = s < 10 ? "0" + s : String.valueOf(s);
            tracker.sendActionBar("§b" + target.getName()
                    + " §8| §7" + m + "m " + sf + "s §8| §f" + dist + "m");
        }
    }

    public void resetTrackerCooldown(UUID uuid) {
        trackerOnCooldown.remove(uuid);
        trackerCdTimer.remove(uuid);
    }

    // =========================================================================
    // Per-second tick
    // =========================================================================

    private void startTick() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID uuid = p.getUniqueId();

                    // Strip human roles from players who became vampires
                    if (isVampirePlayer(p)) {
                        Set<String> tags = p.getScoreboardTags();
                        if (tags.contains("vampire_hunter")) {
                            p.removeScoreboardTag("vampire_hunter");
                            p.sendMessage("§cYou became a vampire — your §fVampire Hunter §crole has been removed.");
                        }
                        if (tags.contains("tracker")) {
                            p.removeScoreboardTag("tracker");
                            p.sendMessage("§cYou became a vampire — your §fTracker §crole has been removed.");
                        }
                        if (tags.contains("medic")) {
                            p.removeScoreboardTag("medic");
                            p.sendMessage("§cYou became a vampire — your §fMedic §crole has been removed.");
                        }
                    }

                    // Find-vampires cooldown countdown
                    if (fvCooldownTimer.containsKey(uuid)) {
                        int t = fvCooldownTimer.get(uuid);
                        if (t > 0) {
                            fvCooldownTimer.put(uuid, t - 1);
                        } else {
                            fvCooldownTimer.remove(uuid);
                            fvOnCooldown.remove(uuid);
                        }
                    }

                    // Tracker active-window countdown
                    if (trackerActive.getOrDefault(uuid, false)) {
                        int t = trackerActiveTimer.getOrDefault(uuid, 0);
                        if (t > 0) {
                            trackerActiveTimer.put(uuid, t - 1);
                        } else {
                            trackerActive.remove(uuid);
                            trackerActiveTimer.remove(uuid);
                            trackerOnCooldown.put(uuid, true);
                            trackerCdTimer.put(uuid, trackerCooldownMins * 60);
                            p.sendActionBar("§7Tracking expired §8| §fCooldown: "
                                    + trackerCooldownMins + "min");
                        }
                    }

                    // Tracker cooldown countdown
                    if (trackerCdTimer.containsKey(uuid)) {
                        int t = trackerCdTimer.get(uuid);
                        if (t > 0) {
                            trackerCdTimer.put(uuid, t - 1);
                        } else {
                            trackerCdTimer.remove(uuid);
                            trackerOnCooldown.remove(uuid);
                        }
                    }
                }
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("config.fv-cooldown",       fvCooldownMinutes);
        yaml.set("config.fv-duration",       fvDurationSeconds);
        yaml.set("config.tracker-cooldown",  trackerCooldownMins);
        yaml.set("config.tracker-track",     trackerTrackMins);
        yaml.set("config.vampire-count",     vampireCount);

        for (String role : KNOWN_ROLES) {
            yaml.set("roles." + role + ".enabled", roleEnabled.getOrDefault(role, true));
            yaml.set("roles." + role + ".pool",    new ArrayList<>(getRolePool(role)));
        }

        yaml.set("vampire-pool", new ArrayList<>(vampirePool));
        yaml.set("game-admins",  new ArrayList<>(gameAdmins));

        try { yaml.save(dataFile); }
        catch (Exception e) {
            plugin.getLogger().warning("RoleManager: failed saving data: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);

        fvCooldownMinutes   = yaml.getInt("config.fv-cooldown",      5);
        fvDurationSeconds   = yaml.getInt("config.fv-duration",     10);
        trackerCooldownMins = yaml.getInt("config.tracker-cooldown",  5);
        trackerTrackMins    = yaml.getInt("config.tracker-track",     4);
        vampireCount        = yaml.getInt("config.vampire-count",     2);

        for (String role : KNOWN_ROLES) {
            roleEnabled.put(role, yaml.getBoolean("roles." + role + ".enabled", true));
            rolePools.put(role, new ArrayList<>(yaml.getStringList("roles." + role + ".pool")));
        }

        vampirePool.clear();
        vampirePool.addAll(yaml.getStringList("vampire-pool"));

        gameAdmins.clear();
        gameAdmins.addAll(yaml.getStringList("game-admins"));

        plugin.logInfo("RoleManager: data loaded.");
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        saveData();
    }
}
