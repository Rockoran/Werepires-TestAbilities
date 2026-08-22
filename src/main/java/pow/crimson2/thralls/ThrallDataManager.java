package pow.crimson2.thralls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles persistence for thrall bond data and profiles.
 *
 * <p><b>Bonds</b> are stored as one small file per player in
 * {@code plugins/WerePires/thralls/<username>.yml}. The filename is the player's name
 * (for readability), but the authoritative key is the {@code uuid} stored <i>inside</i>
 * the file — usernames can change, so a rename is detected and the stale file removed.</p>
 *
 * <p>Writes are incremental: gameplay code calls {@link #markBondDirty(UUID)} on change, and
 * {@link #flushDirtyBonds()} (driven by the manager tick) writes only the changed files,
 * off the main thread. Only {@link #saveAllBondsSync()} / {@link #saveOneBondSync(UUID)} are
 * synchronous, used at safe exit points (quit / shutdown / admin commands).</p>
 *
 * <p><b>Profiles</b> still use a single {@code thralls_profiles.yml} (they change rarely).</p>
 */
public class ThrallDataManager {

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;

    private final File bondsDir;
    private final File legacyDataFile;
    private final File profilesFile;

    /** UUIDs whose bond file needs (re)writing or deleting. */
    private final Set<UUID> dirtyBonds = ConcurrentHashMap.newKeySet();
    /** UUID -> the filename base currently on disk (to detect renames / handle deletes). */
    private final Map<UUID, String> fileNames = new HashMap<>();
    /** Guards against overlapping async writes corrupting files. */
    private final AtomicBoolean savingNow = new AtomicBoolean(false);

    public ThrallDataManager(VampireSMPPlugin plugin, ThrallManager thrallManager) {
        this.plugin = plugin;
        this.thrallManager = thrallManager;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.bondsDir       = new File(plugin.getDataFolder(), "thralls");
        if (!bondsDir.exists()) bondsDir.mkdirs();
        this.legacyDataFile = new File(plugin.getDataFolder(), "thralls_data.yml");
        this.profilesFile   = new File(plugin.getDataFolder(), "thralls_profiles.yml");
    }

    // =========================================================================
    // Load
    // =========================================================================

    public void loadAll() {
        loadBondData();
        loadProfileData();
    }

    private void loadBondData() {
        File[] files = bondsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null && files.length > 0) {
            int loaded = 0;
            for (File f : files) if (loadOneBondFile(f)) loaded++;
            plugin.logInfo("ThrallDataManager: loaded " + loaded + " bonds (per-player files).");
            return;
        }
        // No per-player files yet — migrate from the legacy single file if present.
        if (legacyDataFile.exists()) migrateLegacy();
    }

    private boolean loadOneBondFile(File f) {
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String uuidStr = y.getString("uuid", null);
            if (uuidStr == null) {
                plugin.getLogger().warning("ThrallDataManager: " + f.getName() + " has no uuid field, skipping.");
                return false;
            }
            UUID uuid = UUID.fromString(uuidStr);
            String owner = y.getString("owner", null);
            if (owner == null) return false;

            ThrallBondData d = new ThrallBondData();
            d.setUsername(y.getString("username", null));
            d.setPrimaryMaster(UUID.fromString(owner));
            String owner2 = y.getString("owner2", null);
            if (owner2 != null) d.setSecondaryMaster(UUID.fromString(owner2));
            d.setStageM1(y.getInt("stagem1", 0));
            d.setStageM2(y.getInt("stagem2", 0));
            d.setBottlesM1(y.getInt("bottlesm1", 0));
            d.setBottlesM2(y.getInt("bottlesm2", 0));
            d.setWithdrawLeft(y.getInt("withdraw", thrallManager.getThrallConfig().getThrallWithdrawSeconds()));
            d.setTagOfferPending(y.getBoolean("tagoffer", false));

            // If two files somehow describe the same UUID, keep the first and drop the dupe.
            if (thrallManager.getBonds().containsKey(uuid)) {
                plugin.getLogger().warning("ThrallDataManager: duplicate bond for " + uuid + " in "
                        + f.getName() + ", ignoring.");
                return false;
            }
            thrallManager.getBonds().put(uuid, d);
            fileNames.put(uuid, baseName(f.getName()));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("ThrallDataManager: failed loading bond file " + f.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** One-time migration of the old single {@code thralls_data.yml} to per-player files. */
    private void migrateLegacy() {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyDataFile);
            if (yaml.contains("players")) {
                for (String uuidStr : yaml.getConfigurationSection("players").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String path = "players." + uuidStr + ".";
                        String owner = yaml.getString(path + "owner", null);
                        if (owner == null) continue;
                        ThrallBondData d = new ThrallBondData();
                        d.setUsername(yaml.getString(path + "username", null));
                        d.setPrimaryMaster(UUID.fromString(owner));
                        String owner2 = yaml.getString(path + "owner2", null);
                        if (owner2 != null) d.setSecondaryMaster(UUID.fromString(owner2));
                        d.setStageM1(yaml.getInt(path + "stagem1", 0));
                        d.setStageM2(yaml.getInt(path + "stagem2", 0));
                        d.setBottlesM1(yaml.getInt(path + "bottlesm1", 0));
                        d.setBottlesM2(yaml.getInt(path + "bottlesm2", 0));
                        d.setWithdrawLeft(yaml.getInt(path + "withdraw", thrallManager.getThrallConfig().getThrallWithdrawSeconds()));
                        d.setTagOfferPending(yaml.getBoolean(path + "tagoffer", false));
                        thrallManager.getBonds().put(uuid, d);
                    } catch (Exception ignored) {}
                }
            }
            saveAllBondsSync(); // write everyone out to per-player files
            File backup = new File(plugin.getDataFolder(), "thralls_data.yml.migrated");
            if (backup.exists()) backup.delete();
            legacyDataFile.renameTo(backup);
            plugin.logInfo("ThrallDataManager: migrated " + thrallManager.getBonds().size()
                    + " bonds to per-player files (legacy backed up as thralls_data.yml.migrated).");
        } catch (Exception e) {
            plugin.getLogger().warning("ThrallDataManager: legacy migration failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Dirty tracking + incremental flush
    // =========================================================================

    /** Mark a single bond as needing a write (or delete, if it was removed). */
    public void markBondDirty(UUID id) {
        if (id != null) dirtyBonds.add(id);
    }

    /** Mark every known bond dirty (used by the periodic full snapshot). */
    public void markAllBondsDirty() {
        dirtyBonds.addAll(thrallManager.getBonds().keySet());
        dirtyBonds.addAll(fileNames.keySet()); // include removed bonds so their files get deleted
    }

    /**
     * Write only the changed bonds. YAML is built on the calling (main) thread for a
     * consistent snapshot, then the file I/O runs asynchronously. Skipped if a previous
     * async write is still in flight (those bonds simply flush next tick).
     */
    /**
     * Blocks until any in-flight async flush has finished.
     *
     * <p>flushDirtyBonds hands file I/O to an async task and only clears {@code savingNow} in
     * that task's finally block. The synchronous paths did not consult it, so a player quitting
     * (or the server shutting down) mid-flush could put two threads on the same bond file -
     * YamlConfiguration.save truncates before writing, so the loser leaves a short or
     * interleaved file. Shutdown is the likeliest trigger, since saveAllBondsSync runs exactly
     * when a flush is most likely still pending.
     *
     * <p>Bounded so a wedged write can never hang the main thread or block shutdown: after the
     * timeout we log and proceed, which is no worse than the old unsynchronised behaviour.
     */
    private void awaitAsyncWrites() {
        final long deadline = System.currentTimeMillis() + 2000L;
        while (savingNow.get()) {
            if (System.currentTimeMillis() > deadline) {
                plugin.getLogger().warning("ThrallDataManager: async bond write still running after 2s; "
                        + "writing anyway");
                return;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void flushDirtyBonds() {
        if (dirtyBonds.isEmpty()) return;
        if (!savingNow.compareAndSet(false, true)) return;

        List<UUID> batch = new ArrayList<>(dirtyBonds);
        dirtyBonds.removeAll(batch);

        final List<WriteOp> writes = new ArrayList<>();
        final List<File> deletes  = new ArrayList<>();
        for (UUID id : batch) {
            ThrallBondData d = thrallManager.getBonds().get(id);
            String oldBase = fileNames.get(id);
            if (d == null || d.getPrimaryMaster() == null) {
                if (oldBase != null) { deletes.add(fileFor(oldBase)); fileNames.remove(id); }
                continue;
            }
            String newBase = baseFor(id, d);
            if (oldBase != null && !oldBase.equalsIgnoreCase(newBase)) deletes.add(fileFor(oldBase));
            writes.add(new WriteOp(fileFor(newBase), buildBondYaml(id, d)));
            fileNames.put(id, newBase);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (File f : deletes) { try { if (f.exists()) f.delete(); } catch (Exception ignored) {} }
                for (WriteOp w : writes) {
                    try { w.yaml.save(w.file); }
                    catch (Exception e) {
                        plugin.getLogger().warning("ThrallDataManager: failed saving "
                                + w.file.getName() + ": " + e.getMessage());
                    }
                }
            } finally {
                savingNow.set(false);
            }
        });
    }

    /** Synchronous write/delete of a single bond (used on quit). */
    public void saveOneBondSync(UUID id) {
        if (id == null) return;
        awaitAsyncWrites();
        ThrallBondData d = thrallManager.getBonds().get(id);
        String oldBase = fileNames.get(id);
        if (d == null || d.getPrimaryMaster() == null) {
            if (oldBase != null) { deleteQuiet(fileFor(oldBase)); fileNames.remove(id); }
            dirtyBonds.remove(id);
            return;
        }
        String newBase = baseFor(id, d);
        if (oldBase != null && !oldBase.equalsIgnoreCase(newBase)) deleteQuiet(fileFor(oldBase));
        try { buildBondYaml(id, d).save(fileFor(newBase)); }
        catch (Exception e) {
            plugin.getLogger().warning("ThrallDataManager: failed saving " + newBase + ".yml: " + e.getMessage());
        }
        fileNames.put(id, newBase);
        dirtyBonds.remove(id);
    }

    /** Synchronous full write of all bonds + cleanup of orphaned files (shutdown / admin). */
    public void saveAllBondsSync() {
        awaitAsyncWrites();
        Set<UUID> present = new HashSet<>();
        for (Map.Entry<UUID, ThrallBondData> e : thrallManager.getBonds().entrySet()) {
            ThrallBondData d = e.getValue();
            if (d.getPrimaryMaster() == null) continue;
            UUID id = e.getKey();
            String newBase = baseFor(id, d);
            String oldBase = fileNames.get(id);
            if (oldBase != null && !oldBase.equalsIgnoreCase(newBase)) deleteQuiet(fileFor(oldBase));
            try { buildBondYaml(id, d).save(fileFor(newBase)); }
            catch (Exception ex) {
                plugin.getLogger().warning("ThrallDataManager: failed saving " + newBase + ".yml: " + ex.getMessage());
            }
            fileNames.put(id, newBase);
            present.add(id);
        }
        // Delete files for bonds that no longer exist.
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, String> e : fileNames.entrySet()) {
            if (!present.contains(e.getKey())) { deleteQuiet(fileFor(e.getValue())); gone.add(e.getKey()); }
        }
        gone.forEach(fileNames::remove);
        dirtyBonds.clear();
    }

    /** Legacy-compatible alias kept for existing callers (admin commands, shutdown). */
    public void saveAll() {
        saveAllBondsSync();
    }

    /** Delete every per-player bond file and clear all tracking (full game-init wipe). */
    public void deleteAllBondFiles() {
        File[] files = bondsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) for (File f : files) deleteQuiet(f);
        fileNames.clear();
        dirtyBonds.clear();
    }

    private YamlConfiguration buildBondYaml(UUID id, ThrallBondData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("uuid", id.toString());
        if (d.getUsername() != null) y.set("username", d.getUsername());
        y.set("owner", d.getPrimaryMaster().toString());
        if (d.getSecondaryMaster() != null) y.set("owner2", d.getSecondaryMaster().toString());
        y.set("stagem1",   d.getStageM1());
        y.set("stagem2",   d.getStageM2());
        y.set("bottlesm1", d.getBottlesM1());
        y.set("bottlesm2", d.getBottlesM2());
        y.set("withdraw",  d.getWithdrawLeft());
        if (d.isTagOfferPending()) y.set("tagoffer", true);
        return y;
    }

    private String baseFor(UUID id, ThrallBondData d) {
        String name = d.getUsername();
        return (name != null && !name.isBlank()) ? sanitize(name) : id.toString();
    }

    private static String sanitize(String s) {
        String cleaned = s.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private File fileFor(String base) { return new File(bondsDir, base + ".yml"); }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void deleteQuiet(File f) {
        try { if (f.exists()) f.delete(); } catch (Exception ignored) {}
    }

    private static final class WriteOp {
        final File file;
        final YamlConfiguration yaml;
        WriteOp(File file, YamlConfiguration yaml) { this.file = file; this.yaml = yaml; }
    }

    // =========================================================================
    // Profiles (single file — unchanged)
    // =========================================================================

    private void loadProfileData() {
        if (!profilesFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(profilesFile);
        if (!yaml.contains("players")) return;

        for (String uuidStr : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "players." + uuidStr + ".";

                ThrallProfile profile = new ThrallProfile();
                profile.setActiveIndex(yaml.getInt(path + "active", 0));

                int count = yaml.getInt(path + "count", 0);
                for (int i = 1; i <= count; i++) {
                    String name  = yaml.getString(path + "profiles." + i + ".name",  "");
                    String taste = yaml.getString(path + "profiles." + i + ".taste", "§8Warm. Metallic. Familiar.");
                    String pref  = yaml.getString(path + "profiles." + i + ".thrallpref", null);
                    profile.addProfile(name, taste);
                    if (pref != null) profile.setThrallPref(i, pref);
                }

                thrallManager.getProfiles().put(uuid, profile);
            } catch (Exception e) {
                plugin.getLogger().warning("ThrallDataManager: failed loading profile for " + uuidStr + ": " + e.getMessage());
            }
        }
        plugin.logInfo("ThrallDataManager: loaded " + thrallManager.getProfiles().size() + " profiles.");
    }

    public void saveAllProfiles() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, ThrallProfile> entry : thrallManager.getProfiles().entrySet()) {
            String uuidStr = entry.getKey().toString();
            ThrallProfile profile = entry.getValue();
            if (profile.getCount() == 0) continue;

            String path = "players." + uuidStr + ".";
            yaml.set(path + "count",  profile.getCount());
            yaml.set(path + "active", profile.getActiveIndex());

            List<ThrallProfile.ProfileEntry> entries = profile.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                ThrallProfile.ProfileEntry e = entries.get(i);
                yaml.set(path + "profiles." + (i+1) + ".name",  e.getName());
                yaml.set(path + "profiles." + (i+1) + ".taste", e.getTaste());
                String pref = profile.getThrallPref(i + 1);
                if (pref != null) yaml.set(path + "profiles." + (i+1) + ".thrallpref", pref);
            }
        }

        try { yaml.save(profilesFile); }
        catch (Exception e) { plugin.getLogger().warning("ThrallDataManager: failed saving profiles: " + e.getMessage()); }
    }
}
