package pow.crimson2.world;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages named world templates stored in plugins/VampireSMP/worlds/.
 *
 * Each world lives in its own subfolder:
 *   plugins/VampireSMP/worlds/WorldName/
 *     [Minecraft world files: region/, level.dat, …]
 *     Config.yml           — overrides config.yml keys for this world
 *     Beacons.json         — beacon definitions for this world
 *     OneshotConfig.json   — /gamestart wizard defaults (oneshot)
 *     SeriesConfig.json    — /gamestart wizard defaults (series)
 *
 * The plugin config files (Config.yml, Beacons.json, etc.) are skipped when
 * copying the template — only Minecraft world data is transferred.
 *
 * A _EXAMPLE/ folder is auto-generated on first run as a reference.
 *
 * /pow admin loadworld <name>  — full hot-swap pipeline:
 *   1. Creates a tiny flat staging world (_vsmp_lobby) if absent
 *   2. teleportAsync all players to staging; waits for ALL to complete
 *   3. Unloads the current "world" (no save)
 *   4. Async: deletes the "world" folder, copies the template into it
 *      (skipping plugin config files and uid.dat / session.lock)
 *   5. Main thread: loads "world" via WorldCreator
 *   6. teleportAsync all players to the new spawn
 *   7. Applies Config.yml overrides, loads Beacons.json, loads session defaults
 *   8. Updates server.properties level-name for next restart
 *
 * /pow admin listworlds — lists templates, marks the active one.
 */
public class WorldManager {

    private final VampireSMPPlugin plugin;

    /** plugins/VampireSMP/worlds/ — pristine templates, never modified. */
    private final File worldsFolder;

    /** Bukkit name of the currently active world (always "world" after a swap). */
    private String activeWorldName;

    /** Which template was last loaded (e.g. "Oakhurst"). */
    private String activeTemplateName;

    /** Small flat world players wait in during the file-swap operation. */
    private static final String STAGING_WORLD = "_vsmp_lobby";

    /** The server's primary world folder — always swapped into this name. */
    private static final String TARGET_WORLD = "world";

    /**
     * Files never copied from templates.
     * session.lock / uid.dat — avoid world-identity / locking conflicts.
     * The plugin config files — they live in the template folder for editing
     * but must not land in the live Minecraft world directory.
     */
    private static final Set<String> SKIP_FILES = Set.of(
            "session.lock", "uid.dat",
            "Config.yml", "Beacons.json",
            "OneshotConfig.json", "SeriesConfig.json"
    );

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public WorldManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.worldsFolder = new File(plugin.getDataFolder(), "worlds");
        if (!worldsFolder.exists()) worldsFolder.mkdirs();
        this.activeWorldName    = plugin.getConfig().getString("active-world",    TARGET_WORLD);
        this.activeTemplateName = plugin.getConfig().getString("active-template", "");
        generateExampleFiles();
        extractBundledWorlds();
        extractDropInZips();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public List<String> getAvailableWorlds() {
        List<String> names = new ArrayList<>();
        // A valid world entry is a non-'_' directory that contains an inner
        // subfolder of the same name (the actual Minecraft world data).
        File[] dirs = worldsFolder.listFiles(f ->
                f.isDirectory()
                && !f.getName().startsWith("_")
                && new File(f, f.getName()).isDirectory());
        if (dirs != null) for (File d : dirs) names.add(d.getName());
        Collections.sort(names);
        return names;
    }

    public String getActiveWorldName()    { return activeWorldName;    }
    public String getActiveTemplateName() { return activeTemplateName; }

    public World getActiveWorld() {
        World w = Bukkit.getWorld(activeWorldName);
        if (w == null) w = Bukkit.getWorld(TARGET_WORLD);
        // Final fallback: server may use a non-standard level-name (e.g. "Revhurst" instead of "world")
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        return w;
    }

    // =========================================================================
    // Load / hot-swap command
    // =========================================================================

    public void loadWorld(Player sender, String templateName) {
        // worlds/{name}/{name}/ is the actual Minecraft world data folder
        File templateDir = new File(new File(worldsFolder, templateName), templateName);
        if (!templateDir.exists() || !templateDir.isDirectory()) {
            sender.sendMessage("§cWorld template '" + templateName + "' not found.");
            sender.sendMessage("§7Expected world data at: §eworlds/" + templateName + "/" + templateName + "/");
            List<String> available = getAvailableWorlds();
            if (available.isEmpty()) {
                sender.sendMessage("§7No templates found in: §eplugins/VampireSMP/worlds/");
            } else {
                sender.sendMessage("§7Available: §e" + String.join(", ", available));
            }
            return;
        }

        File destDir = new File(Bukkit.getWorldContainer(), TARGET_WORLD);
        plugin.getLogger().info("[WorldSwap] Template: " + templateDir.getAbsolutePath());
        plugin.getLogger().info("[WorldSwap] Target:   " + destDir.getAbsolutePath());

        // ── Step 1: get / create the staging world ────────────────────────────
        sender.sendMessage("§e[WorldSwap] §7Preparing staging world...");
        World staging = getOrCreateStagingWorld();
        if (staging == null) {
            sender.sendMessage("§c[WorldSwap] Could not create staging world. Aborting.");
            return;
        }

        // ── Step 2: teleportAsync ALL players to staging; wait for completion ─
        Location stagingSpawn = staging.getSpawnLocation();
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (Player p : online) {
            p.sendMessage("§e[WorldSwap] §7Swapping to §e" + templateName + "§7, please wait...");
        }

        List<CompletableFuture<Boolean>> moves = online.stream()
                .map(p -> p.teleportAsync(stagingSpawn))
                .collect(Collectors.toList());

        sender.sendMessage("§7Moving §e" + online.size() + "§7 player(s) to staging...");

        CompletableFuture.allOf(moves.toArray(new CompletableFuture[0]))
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                        doUnloadAndSwap(sender, templateName, templateDir, destDir)));
    }

    // Called on main thread once every player is confirmed in the staging world
    private void doUnloadAndSwap(Player sender, String templateName, File templateDir, File destDir) {

        // ── Step 3: unload "world" (no save) ──────────────────────────────────
        World current = Bukkit.getWorld(TARGET_WORLD);
        if (current != null) {
            plugin.getLogger().info("[WorldSwap] Unloading '" + TARGET_WORLD + "'...");
            if (!Bukkit.unloadWorld(current, false)) {
                sender.sendMessage("§c[WorldSwap] Failed to unload '" + TARGET_WORLD + "'.");
                sender.sendMessage("§7Falling back to named-world load instead.");
                plugin.getLogger().warning("[WorldSwap] unloadWorld failed — falling back");
                doNamedWorldLoad(sender, templateName, new File(Bukkit.getWorldContainer(), templateName));
                return;
            }
            sender.sendMessage("§7Unloaded '" + TARGET_WORLD + "'.");
        }

        // ── Steps 4 + 5: async delete+copy, then main-thread load ─────────────
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (destDir.exists()) {
                    sender.sendMessage("§7Deleting old world files...");
                    deleteDirectory(destDir.toPath());
                }

                sender.sendMessage("§7Copying §e" + templateName + "§7 → §e" + TARGET_WORLD + "§7...");
                copyDirectory(templateDir.toPath(), destDir.toPath());
                sender.sendMessage("§7Copy complete.");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§7Loading world...");
                    plugin.getLogger().info("[WorldSwap] WorldCreator(\"" + TARGET_WORLD + "\")");

                    World newWorld = new WorldCreator(TARGET_WORLD)
                            .environment(World.Environment.NORMAL)
                            .createWorld();

                    if (newWorld == null) {
                        sender.sendMessage("§c[WorldSwap] World failed to load! Check console.");
                        plugin.getLogger().severe("[WorldSwap] WorldCreator returned null");
                        return;
                    }

                    plugin.getLogger().info("[WorldSwap] Loaded: " + newWorld.getName()
                            + " spawn=" + newWorld.getSpawnLocation());

                    // ── Step 6: teleportAsync all players back ─────────────────
                    teleportAll(newWorld.getSpawnLocation(), templateName);
                    finalizeLoad(sender, TARGET_WORLD, templateName);
                });

            } catch (IOException e) {
                plugin.getLogger().severe("[WorldSwap] IO error: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§c[WorldSwap] IO error: " + e.getMessage()));
            }
        });
    }

    /**
     * Fallback: loads the template as a named world (not replacing "world").
     * Used when unloadWorld("world") fails.
     * Always does a fresh copy — if the world is already loaded it is unloaded
     * first (players are in staging so this is safe).
     */
    private void doNamedWorldLoad(Player sender, String templateName, File destDir) {
        // Unload existing named world so we can copy a fresh template over it
        World existing = Bukkit.getWorld(templateName);
        if (existing != null) {
            plugin.getLogger().info("[WorldSwap] Fallback: unloading existing '" + templateName + "' for fresh copy");
            if (!Bukkit.unloadWorld(existing, false)) {
                // Can't unload even the named world — just teleport to it as-is
                sender.sendMessage("§e[WorldSwap] §7Could not refresh §e" + templateName
                        + "§7 — teleporting to existing copy.");
                teleportAll(existing.getSpawnLocation(), templateName);
                finalizeLoad(sender, templateName, templateName);
                return;
            }
        }

        // Fresh copy from template then load
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (destDir.exists()) {
                    sender.sendMessage("§7Removing old §e" + templateName + "§7 data...");
                    deleteDirectory(destDir.toPath());
                }
                sender.sendMessage("§7Copying template files...");
                // worlds/{name}/{name}/ is the actual MC world data
                copyDirectory(new File(new File(worldsFolder, templateName), templateName).toPath(), destDir.toPath());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("[WorldSwap] Fallback WorldCreator(\"" + templateName + "\")");
                    World world = new WorldCreator(templateName)
                            .environment(World.Environment.NORMAL)
                            .createWorld();
                    if (world == null) {
                        sender.sendMessage("§c[WorldSwap] Fallback world load failed!");
                        return;
                    }
                    plugin.getLogger().info("[WorldSwap] Fallback loaded: " + world.getName());
                    teleportAll(world.getSpawnLocation(), templateName);
                    finalizeLoad(sender, templateName, templateName);
                });
            } catch (IOException e) {
                plugin.getLogger().severe("[WorldSwap] Fallback IO error: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§c[WorldSwap] IO error: " + e.getMessage()));
            }
        });
    }

    // =========================================================================
    // World config + beacon helpers
    // =========================================================================

    /**
     * The container folder for a template: worlds/{name}/
     * Config files (Config.yml, Beacons.json, etc.) sit directly inside here.
     * The actual Minecraft world data is one level deeper: worlds/{name}/{name}/
     */
    private File worldDir(String templateName) {
        return new File(worldsFolder, templateName);
    }

    /**
     * Locates Config.yml for a template.
     * Priority:
     *   1. worlds/{name}/Config.yml           (preferred — inside world subfolder)
     *   2. worlds/{name}_Config.yml           (legacy flat layout)
     *   3. plugins/VampireSMP/{name}_Config.yml (oldest legacy)
     */
    private File findWorldConfigFile(String templateName) {
        File inDir = new File(worldDir(templateName), "Config.yml");
        if (inDir.exists()) return inDir;
        File flat = new File(worldsFolder, templateName + "_Config.yml");
        if (flat.exists()) return flat;
        File legacy = new File(plugin.getDataFolder(), templateName + "_Config.yml");
        if (legacy.exists()) return legacy;
        return null;
    }

    /**
     * Locates Beacons.json for a template.
     * Priority: worlds/{name}/Beacons.json → worlds/{name}_Beacons.json → null
     */
    private File findWorldBeaconsFile(String templateName) {
        File inDir = new File(worldDir(templateName), "Beacons.json");
        if (inDir.exists()) return inDir;
        File flat = new File(worldsFolder, templateName + "_Beacons.json");
        return flat.exists() ? flat : null;
    }

    /**
     * Locates OneshotConfig.json for a template.
     * Priority: worlds/{name}/OneshotConfig.json → worlds/{name}_OneshotConfig.json → null
     */
    private File findWorldOneshotConfigFile(String templateName) {
        File inDir = new File(worldDir(templateName), "OneshotConfig.json");
        if (inDir.exists()) return inDir;
        File flat = new File(worldsFolder, templateName + "_OneshotConfig.json");
        return flat.exists() ? flat : null;
    }

    /**
     * Locates SeriesConfig.json for a template.
     * Priority: worlds/{name}/SeriesConfig.json → worlds/{name}_SeriesConfig.json → null
     */
    private File findWorldSeriesConfigFile(String templateName) {
        File inDir = new File(worldDir(templateName), "SeriesConfig.json");
        if (inDir.exists()) return inDir;
        File flat = new File(worldsFolder, templateName + "_SeriesConfig.json");
        return flat.exists() ? flat : null;
    }

    public int applyWorldConfig(String templateName) {
        File cfgFile = findWorldConfigFile(templateName);
        if (cfgFile == null) return 0;

        YamlConfiguration worldCfg = YamlConfiguration.loadConfiguration(cfgFile);
        int applied = 0;

        for (Map.Entry<String, Object> entry : worldCfg.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) continue;
            plugin.getConfig().set(entry.getKey(), entry.getValue());
            applied++;
        }

        plugin.saveConfig();
        plugin.getConfigManager().loadConfig();
        plugin.getTomeDistributionManager().reloadConfig();
        plugin.getLogger().info("[WorldSwap] Applied " + applied + " config values from "
                + cfgFile.getParentFile().getName() + "/" + cfgFile.getName());
        return applied;
    }

    public YamlConfiguration getWorldConfig(String templateName) {
        File cfgFile = findWorldConfigFile(templateName);
        return cfgFile != null ? YamlConfiguration.loadConfiguration(cfgFile) : null;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void finalizeLoad(Player sender, String worldName, String templateName) {
        // Apply config (worlds/{name}_Config.yml preferred, legacy fallback)
        int applied = applyWorldConfig(templateName);

        activeWorldName    = worldName;
        activeTemplateName = templateName;
        plugin.getConfig().set("active-world",    worldName);
        plugin.getConfig().set("active-template", templateName);
        plugin.saveConfig();

        updateServerProperties(worldName);

        // Reload vampire respawn location for the new world
        plugin.reloadVampireRespawnLocation();

        // Load world-specific beacons if present, otherwise reload from main file
        File worldBeaconsFile = findWorldBeaconsFile(templateName);
        if (worldBeaconsFile != null) {
            plugin.getBeaconManager().loadBeaconsFromWorldFile(worldBeaconsFile, worldName);
        } else {
            plugin.getBeaconManager().reloadBeacons();
        }

        // Load per-world /gamestart wizard defaults (oneshot / series session config)
        plugin.getGameStartManager().loadWorldDefaults(
                findWorldOneshotConfigFile(templateName),
                findWorldSeriesConfigFile(templateName));

        sender.sendMessage("");
        sender.sendMessage("§a§l========================================");
        sender.sendMessage("§a§lWorld loaded: §e" + templateName);
        sender.sendMessage("§a§l========================================");
        if (applied > 0) {
            File cfgFile = findWorldConfigFile(templateName);
            String cfgLoc = cfgFile != null
                    ? "worlds/" + templateName + "/" + cfgFile.getName()
                    : "worlds/" + templateName + "/Config.yml";
            sender.sendMessage("§7Config applied: §e" + applied + " §7values from §e" + cfgLoc + "§7.");
        } else {
            sender.sendMessage("§7No config found — add §eConfig.yml §7to §eworlds/" + templateName + "/§7.");
        }
        if (worldBeaconsFile != null) {
            sender.sendMessage("§7Beacons loaded from §eworlds/" + templateName + "/Beacons.json§7.");
        } else {
            sender.sendMessage("§7No beacons file — add §eBeacons.json §7to §eworlds/" + templateName + "/§7.");
        }
        sender.sendMessage("§7Active world → §e" + worldName
                + (worldName.equals(templateName) ? "" : " §7(template: §e" + templateName + "§7)"));
        sender.sendMessage("§7server.properties updated for next restart.");
        sender.sendMessage("§7Run §f/pow admin init §7to start a game.");
    }

    /** teleportAsync each player; on failure logs a warning. */
    private void teleportAll(Location spawn, String templateName) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleportAsync(spawn).thenAccept(success -> {
                if (success) {
                    p.sendMessage("§a§lWelcome to §6§l" + templateName + "§a§l!");
                } else {
                    p.sendMessage("§c[WorldSwap] Teleport failed — try /spawn.");
                    plugin.getLogger().warning("[WorldSwap] teleportAsync failed for " + p.getName());
                }
            });
        }
    }

    /** Returns (or lazily creates) the tiny flat staging world. */
    private World getOrCreateStagingWorld() {
        World existing = Bukkit.getWorld(STAGING_WORLD);
        if (existing != null) return existing;
        try {
            return new WorldCreator(STAGING_WORLD)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .createWorld();
        } catch (Exception e) {
            plugin.getLogger().severe("[WorldSwap] Could not create staging world: " + e.getMessage());
            return null;
        }
    }

    /** Updates level-name in server.properties so the world is default on restart. */
    private void updateServerProperties(String levelName) {
        File propsFile = new File("server.properties");
        if (!propsFile.exists()) {
            plugin.getLogger().warning("[WorldSwap] server.properties not found at: "
                    + propsFile.getAbsolutePath());
            return;
        }
        try {
            List<String> lines   = Files.readAllLines(propsFile.toPath());
            List<String> updated = new ArrayList<>();
            boolean found = false;
            for (String line : lines) {
                if (line.startsWith("level-name=")) {
                    updated.add("level-name=" + levelName);
                    found = true;
                } else {
                    updated.add(line);
                }
            }
            if (!found) updated.add("level-name=" + levelName);
            Files.write(propsFile.toPath(), updated);
            plugin.getLogger().info("[WorldSwap] server.properties level-name=" + levelName);
        } catch (IOException e) {
            plugin.getLogger().warning("[WorldSwap] Could not update server.properties: " + e.getMessage());
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyDirectory(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIP_FILES.contains(file.getFileName().toString())) {
                    Files.copy(file, dest.resolve(src.relativize(file)),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // =========================================================================
    // Bundled world extraction
    // =========================================================================

    /**
     * Reads worlds/_bundled.txt from the jar and extracts any world whose
     * worlds/{name}/{name}/level.dat does not yet exist on disk.
     * Checking level.dat (not just the directory) means incomplete extractions
     * are detected and retried automatically on the next boot.
     * Config files (Config.yml, Beacons.json, etc.) are also copied if missing.
     * Safe to call every startup — existing files are never overwritten.
     */
    private void extractBundledWorlds() {
        try (InputStream is = plugin.getResource("worlds/_bundled.txt")) {
            if (is == null) return;
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (String rawLine : content.split("[\r\n]+")) {
                String worldName = rawLine.trim();
                if (worldName.isEmpty() || worldName.startsWith("#")) continue;

                File worldDataDir = new File(new File(worldsFolder, worldName), worldName);
                // Use level.dat as the completeness marker — a directory alone
                // could be a leftover from a failed previous extraction.
                boolean complete = new File(worldDataDir, "level.dat").exists();
                if (!complete) {
                    // Clean up any partial extraction before starting fresh
                    if (worldDataDir.exists()) {
                        plugin.getLogger().info("[WorldManager] Incomplete extraction detected for '"
                                + worldName + "' — cleaning up and retrying...");
                        try { deleteDirectory(worldDataDir.toPath()); } catch (IOException ignored) {}
                    }
                    plugin.getLogger().info("[WorldManager] Extracting bundled world '"
                            + worldName + "' — this may take a moment...");
                    extractBundledWorldZip(worldName, worldDataDir);
                    plugin.getLogger().info("[WorldManager] Finished extracting '" + worldName + "'.");
                }

                // Always fill in missing config files (never overwrites)
                extractBundledConfigIfMissing(worldName, "Config.yml");
                extractBundledConfigIfMissing(worldName, "Beacons.json");
                extractBundledConfigIfMissing(worldName, "OneshotConfig.json");
                extractBundledConfigIfMissing(worldName, "SeriesConfig.json");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[WorldManager] Could not read _bundled.txt: " + e.getMessage());
        }
    }

    /**
     * Extracts worlds/{name}.zip from the jar into worlds/{name}/{name}/ on disk.
     * Skips uid.dat, session.lock, and other SKIP_FILES entries so Paper generates
     * a fresh world identity rather than seeing a duplicate UUID.
     *
     * Handles zips that have a common top-level folder prefix (e.g. "world/" from
     * some export tools) by auto-detecting and stripping it so all files land
     * directly in worldDataDir.
     *
     * Each entry is extracted independently — a bad entry is logged and skipped
     * rather than aborting the entire extraction.
     */
    private void extractBundledWorldZip(String worldName, File worldDataDir) {
        String zipResource = "worlds/" + worldName + ".zip";

        // First pass: detect a common top-level folder prefix (e.g. "world/").
        // If every entry in the zip starts with the same folder name we strip it
        // so files land directly in worldDataDir instead of a sub-folder.
        String stripPrefix = detectZipTopLevelPrefix(zipResource);
        if (!stripPrefix.isEmpty()) {
            plugin.getLogger().info("[WorldManager] Detected zip prefix '" + stripPrefix
                    + "' for '" + worldName + "' — will strip during extraction.");
        }

        try (InputStream raw = plugin.getResource(zipResource)) {
            if (raw == null) {
                plugin.getLogger().warning("[WorldManager] No bundled zip found for '" + worldName
                        + "' (looked for " + zipResource + " in jar).");
                return;
            }
            worldDataDir.mkdirs();
            int extracted = 0, skipped = 0;
            try (ZipInputStream zis = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    // Normalise separators — some zip tools use backslash
                    String entryName = entry.getName().replace('\\', '/');

                    // Strip common top-level prefix if detected
                    if (!stripPrefix.isEmpty() && entryName.startsWith(stripPrefix)) {
                        entryName = entryName.substring(stripPrefix.length());
                    }
                    // Skip the prefix directory entry itself (now empty string)
                    if (entryName.isEmpty()) {
                        zis.closeEntry();
                        continue;
                    }

                    // Strip the file-name component to check SKIP_FILES
                    int slash = entryName.lastIndexOf('/');
                    String fileName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
                    if (SKIP_FILES.contains(fileName)) {
                        zis.closeEntry();
                        skipped++;
                        continue;
                    }

                    File dest = new File(worldDataDir, entryName);
                    try {
                        if (entry.isDirectory()) {
                            // If a prior bad entry wrote a FILE here, remove it
                            if (dest.exists() && !dest.isDirectory()) dest.delete();
                            dest.mkdirs();
                        } else {
                            // Skip: some zips (made by Windows) contain a file entry for a path
                            // that is also a parent directory of later entries.  If we already
                            // created that path as a directory, don't overwrite it with a file
                            // (this is what causes "Invalid directory entry: sparkles" in Minecraft).
                            if (dest.exists() && dest.isDirectory()) {
                                zis.closeEntry();
                                skipped++;
                                continue;
                            }
                            File parent = dest.getParentFile();
                            // If parent somehow ended up as a file, fix it
                            if (parent.exists() && !parent.isDirectory()) parent.delete();
                            parent.mkdirs();
                            Files.copy(zis, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            extracted++;
                        }
                    } catch (IOException entryEx) {
                        plugin.getLogger().warning("[WorldManager] Skipping zip entry '"
                                + entryName + "': " + entryEx.getMessage());
                        skipped++;
                    }
                    zis.closeEntry();
                }
            }
            plugin.getLogger().info("[WorldManager] Extracted " + extracted
                    + " files (" + skipped + " skipped) for world '" + worldName + "'.");
        } catch (IOException e) {
            plugin.getLogger().severe("[WorldManager] Failed to extract bundled world '"
                    + worldName + "': " + e.getMessage());
        }
    }

    /**
     * Scans the zip resource for a common top-level folder shared by ALL entries.
     * For example, if every entry starts with "world/" this returns "world/".
     * Returns "" if entries have mixed prefixes or any top-level file exists.
     */
    private String detectZipTopLevelPrefix(String zipResource) {
        try (InputStream raw = plugin.getResource(zipResource)) {
            if (raw == null) return "";
            String candidate = null;
            try (ZipInputStream zis = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    int slash = name.indexOf('/');
                    if (slash < 0) {
                        // Top-level file — no consistent folder prefix
                        zis.closeEntry();
                        return "";
                    }
                    String top = name.substring(0, slash + 1); // e.g. "world/"
                    if (candidate == null) {
                        candidate = top;
                    } else if (!candidate.equals(top)) {
                        zis.closeEntry();
                        return ""; // mixed top-level folders
                    }
                    zis.closeEntry();
                }
            }
            return candidate != null ? candidate : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Copies worlds/{name}_{fileName} from the jar to worlds/{name}/{fileName} on disk.
     * Does nothing if the destination file already exists.
     */
    private void extractBundledConfigIfMissing(String worldName, String fileName) {
        File dest = new File(new File(worldsFolder, worldName), fileName);
        if (dest.exists()) return;

        String resourcePath = "worlds/" + worldName + "_" + fileName;
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) return; // Not bundled for this world — skip silently
            dest.getParentFile().mkdirs();
            Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[WorldManager] Extracted bundled config: worlds/"
                    + worldName + "/" + fileName);
        } catch (IOException e) {
            plugin.getLogger().warning("[WorldManager] Could not extract "
                    + resourcePath + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // Drop-in zip extraction (worlds/*.zip placed by the server admin)
    // =========================================================================

    /**
     * Scans the worlds folder for any *.zip files placed there by the server admin
     * and auto-extracts them on startup.
     *
     * Expected zip format:
     *   MyWorld.zip
     *   ├── Config.yml        (optional — bundled defaults used if absent)
     *   ├── Beacons.json      (optional — bundled defaults used if absent)
     *   └── world/            (or any single top-level folder — auto-detected)
     *       ├── level.dat
     *       ├── region/
     *       └── ...
     *
     * The zip file is kept after extraction so the admin can verify it; it is
     * ignored on subsequent startups because level.dat already exists.
     */
    private void extractDropInZips() {
        File[] zips = worldsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        if (zips == null || zips.length == 0) return;

        for (File zipFile : zips) {
            String zipName = zipFile.getName();
            // Derive world name from filename: "Revhurst.zip" -> "Revhurst"
            String worldName = zipName.substring(0, zipName.length() - 4);

            File worldDir     = new File(worldsFolder, worldName);
            File worldDataDir = new File(worldDir, worldName);

            if (new File(worldDataDir, "level.dat").exists()) continue; // already done

            plugin.getLogger().info("[WorldManager] Found drop-in zip: " + zipName
                    + " — extracting world '" + worldName + "'...");
            try {
                if (worldDataDir.exists()) {
                    try { deleteDirectory(worldDataDir.toPath()); } catch (IOException ignored) {}
                }
                extractDropInZip(zipFile, worldName, worldDir, worldDataDir);
                plugin.getLogger().info("[WorldManager] Finished extracting '" + worldName
                        + "' from drop-in zip.");
            } catch (Exception e) {
                plugin.getLogger().severe("[WorldManager] Failed to extract drop-in zip '"
                        + zipName + "': " + e.getMessage());
                continue;
            }

            // Fill in any missing config files from bundled jar resources
            extractBundledConfigIfMissing(worldName, "Config.yml");
            extractBundledConfigIfMissing(worldName, "Beacons.json");
            extractBundledConfigIfMissing(worldName, "OneshotConfig.json");
            extractBundledConfigIfMissing(worldName, "SeriesConfig.json");
        }
    }

    /**
     * Extracts a single drop-in zip into worlds/{worldName}/.
     *
     * Root-level *.yml / *.json entries go to worlds/{worldName}/ (config files).
     * Everything else is treated as Minecraft world data: a common top-level
     * prefix (e.g. "world/") is detected and stripped, then files land in
     * worlds/{worldName}/{worldName}/.
     */
    private void extractDropInZip(File zipFile, String worldName,
                                   File worldDir, File worldDataDir) throws IOException {
        String dataPrefix = detectDropInDataPrefix(zipFile);
        if (!dataPrefix.isEmpty()) {
            plugin.getLogger().info("[WorldManager] Drop-in zip '" + zipFile.getName()
                    + "' has data prefix '" + dataPrefix + "' — stripping.");
        }

        worldDir.mkdirs();
        worldDataDir.mkdirs();

        int extracted = 0, skipped = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');

                // Determine where this entry belongs
                File dest;
                boolean isRootLevel = !entryName.contains("/");
                boolean isRootConfig = isRootLevel
                        && (entryName.endsWith(".yml") || entryName.endsWith(".json"));

                if (isRootConfig) {
                    // Config files at zip root → worldDir (don't overwrite existing)
                    dest = new File(worldDir, entryName);
                    if (dest.exists()) { zis.closeEntry(); skipped++; continue; }
                } else if (!dataPrefix.isEmpty() && entryName.startsWith(dataPrefix)) {
                    // World data with a prefix → worldDataDir, prefix stripped
                    String rel = entryName.substring(dataPrefix.length());
                    if (rel.isEmpty()) { zis.closeEntry(); continue; } // the prefix dir itself
                    dest = new File(worldDataDir, rel);
                } else if (dataPrefix.isEmpty() && !isRootConfig) {
                    // No prefix detected (level.dat at zip root) — all non-config → worldDataDir
                    dest = new File(worldDataDir, entryName);
                } else {
                    // Root-level non-config when a prefix was detected — skip
                    zis.closeEntry(); skipped++; continue;
                }

                int slash = entryName.lastIndexOf('/');
                String fileName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
                if (SKIP_FILES.contains(fileName)) { zis.closeEntry(); skipped++; continue; }

                try {
                    if (entry.isDirectory()) {
                        if (dest.exists() && !dest.isDirectory()) dest.delete();
                        dest.mkdirs();
                    } else {
                        if (dest.exists() && dest.isDirectory()) { zis.closeEntry(); skipped++; continue; }
                        File parent = dest.getParentFile();
                        if (parent.exists() && !parent.isDirectory()) parent.delete();
                        parent.mkdirs();
                        Files.copy(zis, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        extracted++;
                    }
                } catch (IOException entryEx) {
                    plugin.getLogger().warning("[WorldManager] Skipping drop-in entry '"
                            + entryName + "': " + entryEx.getMessage());
                    skipped++;
                }
                zis.closeEntry();
            }
        }
        plugin.getLogger().info("[WorldManager] Drop-in extracted " + extracted
                + " files (" + skipped + " skipped) for '" + worldName + "'.");
    }

    /**
     * Finds the common top-level folder in a zip that contains level.dat.
     * E.g. "world/" or "Revhurst/". Returns "" if level.dat is at the zip root.
     */
    private String detectDropInDataPrefix(File zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                zis.closeEntry();
                if (name.endsWith("level.dat")) {
                    int lastSlash = name.lastIndexOf('/');
                    return lastSlash < 0 ? "" : name.substring(0, lastSlash + 1);
                }
            }
        }
        return "";
    }

    // =========================================================================
    // Example / template file generation
    // =========================================================================

    /**
     * Creates a _EXAMPLE/ folder in the worlds directory showing the expected layout.
     * Runs once on first boot; never overwrites existing files.
     *
     * Structure produced:
     *   worlds/_EXAMPLE/
     *     _EXAMPLE/          ← placeholder for the Minecraft world data folder
     *     Config.yml         ← copy of the bundled config.yml (every available key)
     *     Beacons.json
     *     OneshotConfig.json
     *     SeriesConfig.json
     *
     * To add a real world, create:
     *   worlds/WorldName/WorldName/  ← put Minecraft world files here
     *   worlds/WorldName/Config.yml  ← optional config overrides
     *   etc.
     */
    private void generateExampleFiles() {
        File exampleContainer = new File(worldsFolder, "_EXAMPLE");
        if (!exampleContainer.exists()) exampleContainer.mkdirs();

        // Inner placeholder folder representing where world data goes
        File exampleWorldData = new File(exampleContainer, "_EXAMPLE");
        if (!exampleWorldData.exists()) exampleWorldData.mkdirs();

        // Config files sit next to the world data folder, inside the container
        writeExampleFile(new File(exampleContainer, "Config.yml"),         buildExampleConfig());
        writeExampleFile(new File(exampleContainer, "Beacons.json"),       buildExampleBeacons());
        writeExampleFile(new File(exampleContainer, "OneshotConfig.json"), buildExampleOneshot());
        writeExampleFile(new File(exampleContainer, "SeriesConfig.json"),  buildExampleSeries());
    }

    private void writeExampleFile(File f, String content) {
        if (f.exists()) return;
        try {
            Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
            plugin.getLogger().info("[WorldManager] Created example file: worlds/_EXAMPLE/" + f.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("[WorldManager] Could not write " + f.getName() + ": " + e.getMessage());
        }
    }

    /** Reads the bundled config.yml from the jar — stays in sync automatically. */
    private String buildExampleConfig() {
        try (java.io.InputStream is = plugin.getResource("config.yml")) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("[WorldManager] Could not read bundled config.yml: " + e.getMessage());
        }
        return "# Error: could not load config.yml from plugin jar\n";
    }

    private static String buildExampleBeacons() {
        // NOTE: must be valid JSON — no trailing comments.
        // Keys are beacon names in lowercase.  worldName is overwritten to the
        // active world on load.  Valid state values:
        //   NEUTRAL, HOLY, DESECRATED, PERMANENTLY_DESECRATED, PRIMAL
        return "{\n"
             + "  \"townhall\": {\n"
             + "    \"name\": \"TownHall\",\n"
             + "    \"worldName\": \"world\",\n"
             + "    \"x\": 0.0,\n"
             + "    \"y\": 64.0,\n"
             + "    \"z\": 0.0,\n"
             + "    \"state\": \"NEUTRAL\",\n"
             + "    \"captureRadius\": 10,\n"
             + "    \"lastStateChangeTime\": 0,\n"
             + "    \"lastChangedBy\": null,\n"
             + "    \"conversionCooldownUntil\": 0\n"
             + "  },\n"
             + "  \"church\": {\n"
             + "    \"name\": \"Church\",\n"
             + "    \"worldName\": \"world\",\n"
             + "    \"x\": 100.0,\n"
             + "    \"y\": 70.0,\n"
             + "    \"z\": 200.0,\n"
             + "    \"state\": \"NEUTRAL\",\n"
             + "    \"captureRadius\": 10,\n"
             + "    \"lastStateChangeTime\": 0,\n"
             + "    \"lastChangedBy\": null,\n"
             + "    \"conversionCooldownUntil\": 0\n"
             + "  }\n"
             + "}\n";
    }

    private static String buildExampleOneshot() {
        return "{\n"
             + "  \"_comment\": \"Oneshot session defaults — pre-fills the /gamestart wizard for this world.\",\n"
             + "\n"
             + "  \"session\":    \"resume\",\n"
             + "\n"
             + "  \"break_mins\": 90,\n"
             + "  \"break_dur\":  15,\n"
             + "  \"break_loop\": true,\n"
             + "\n"
             + "  \"role_counts\": {\n"
             + "    \"vampire_hunter\": 1,\n"
             + "    \"medic\":          0,\n"
             + "    \"tracker\":        0\n"
             + "  }\n"
             + "}\n";
    }

    private static String buildExampleSeries() {
        return "{\n"
             + "  \"_comment\": \"Series session defaults — pre-fills the /gamestart wizard for this world.\",\n"
             + "\n"
             + "  \"session\":    \"resume\",\n"
             + "\n"
             + "  \"half_mins\":  60,\n"
             + "  \"break_dur\":  15,\n"
             + "  \"build_mins\": 45,\n"
             + "\n"
             + "  \"role_counts\": {\n"
             + "    \"vampire_hunter\": 1,\n"
             + "    \"medic\":          0,\n"
             + "    \"tracker\":        0\n"
             + "  }\n"
             + "}\n";
    }
}
