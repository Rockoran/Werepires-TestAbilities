package pow.crimson2.world;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Manages remote world pack discovery and installation.
 *
 * On start:
 *   1. Queries the GitHub Releases API for the configured worlds-repo
 *   2. Registers any new .zip assets into Supabase via type=register_worlds
 *      so they appear on the website automatically — no manual entry needed
 *
 * Every 60 s:
 *   3. Polls for pending installs (type=get_pending_worlds)
 *   4. Downloads, verifies, extracts, and hot-swaps — fully automatic
 *   5. Reports result back (type=world_status)
 */
public class WorldPackManager {

    private static final String EDGE_URL  = "https://iktbixeivylotgzspexl.supabase.co/functions/v1/report";
    private static final String GH_API    = "https://api.github.com/repos/";
    private static final int    POLL_TICKS = 20 * 60; // 60 s

    private final VampireSMPPlugin plugin;
    private final HttpClient http;
    private final String serverKey;
    private final String serverName;
    private final String worldsRepo; // e.g. "Rockoran/werepires-world1"
    private int taskId = -1;

    private volatile boolean installing = false;

    public WorldPackManager(VampireSMPPlugin plugin, String serverKey, String serverName) {
        this.plugin     = plugin;
        this.serverKey  = serverKey;
        this.serverName = serverName;
        this.worldsRepo = plugin.getConfig().getString("werepires-network.worlds-repo", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        // Discover + register worlds from GitHub immediately on startup (async)
        if (!worldsRepo.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::syncGithubReleases);
        }
        // Poll for pending deploys every 60 s (initial delay: 15 s)
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::pollForPacks, 20L * 15, POLL_TICKS).getTaskId();
        plugin.logInfo("[WorldPacks] Started. Repo: " + (worldsRepo.isEmpty() ? "none" : worldsRepo));
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    // -------------------------------------------------------------------------
    // GitHub release sync — auto-populates the website with available worlds
    // -------------------------------------------------------------------------

    private void syncGithubReleases() {
        List<GhAsset> assets = fetchGithubAssets();
        if (assets.isEmpty()) {
            plugin.logInfo("[WorldPacks] No .zip assets found in GitHub releases.");
            return;
        }

        // Build a JSON array of {world_name, download_url} for each zip
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < assets.size(); i++) {
            GhAsset a = assets.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"world_name\":").append(q(a.worldName))
              .append(",\"download_url\":").append(q(a.downloadUrl))
              .append(",\"size_bytes\":").append(a.sizeBytes)
              .append("}");
        }
        sb.append("]");

        String payload = "{\"worlds\":" + sb + "}";
        String result = post("register_worlds", payload);
        if (result != null && result.contains("\"ok\"")) {
            plugin.logInfo("[WorldPacks] Registered " + assets.size() + " world(s) from GitHub.");
        } else {
            plugin.logInfo("[WorldPacks] register_worlds response: " + result);
        }
    }

    private List<GhAsset> fetchGithubAssets() {
        List<GhAsset> out = new ArrayList<>();
        try {
            // GET /repos/{owner}/{repo}/releases — returns array of releases
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GH_API + worldsRepo + "/releases"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WerePires-Plugin")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                plugin.getLogger().warning("[WorldPacks] GitHub API returned " + res.statusCode());
                return out;
            }

            // Parse all assets from all releases — extract name + browser_download_url + size
            String body = res.body();
            int pos = 0;
            while (true) {
                int nameIdx = body.indexOf("\"browser_download_url\":", pos);
                if (nameIdx < 0) break;

                // Find the url value
                int urlStart = body.indexOf("\"", nameIdx + 23) + 1;
                int urlEnd   = body.indexOf("\"", urlStart);
                String url   = body.substring(urlStart, urlEnd);
                pos = urlEnd;

                if (!url.endsWith(".zip")) continue;

                // Derive world name from filename: "Frostvein.zip" -> "Frostvein"
                String fileName  = url.substring(url.lastIndexOf('/') + 1);
                String worldName = fileName.substring(0, fileName.length() - 4);

                // Find size (appears as "size":<number> somewhere near this asset block)
                long size = 0;
                int sizeIdx = body.lastIndexOf("\"size\":", urlEnd);
                if (sizeIdx > 0 && sizeIdx > nameIdx - 500) {
                    int sStart = sizeIdx + 7;
                    int sEnd   = body.indexOf(",", sStart);
                    if (sEnd > sStart) {
                        try { size = Long.parseLong(body.substring(sStart, sEnd).trim()); } catch (NumberFormatException ignored) {}
                    }
                }

                out.add(new GhAsset(worldName, url, size));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldPacks] GitHub API error: " + e.getMessage());
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Poll for pending installs
    // -------------------------------------------------------------------------

    private void pollForPacks() {
        if (installing) return;
        String response = post("get_pending_worlds", "{}");
        if (response == null || !response.contains("\"world_name\"")) return;

        WorldPackEntry pack = parseFirstPack(response);
        if (pack == null) return;

        installing = true;
        plugin.getLogger().info("[WorldPacks] Downloading: " + pack.worldName);
        broadcastOps("§e[WorldPacks] §7Downloading §e" + pack.worldName + "§7...");

        try {
            File zipFile = downloadZip(pack);
            if (pack.sha256 != null && !pack.sha256.isEmpty()) {
                verifyChecksum(zipFile, pack.sha256);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    plugin.getWorldManager().installAndLoadPack(pack.worldName, zipFile,
                            msg -> broadcastOps("§e[WorldPacks] §7" + msg));
                    reportStatus(pack.id, "installed", null);
                } catch (Exception e) {
                    plugin.getLogger().severe("[WorldPacks] Install failed: " + e.getMessage());
                    broadcastOps("§c[WorldPacks] §cInstall failed: " + e.getMessage());
                    reportStatus(pack.id, "error", e.getMessage());
                } finally {
                    installing = false;
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[WorldPacks] Download failed: " + e.getMessage());
            broadcastOps("§c[WorldPacks] §cDownload failed: " + e.getMessage());
            reportStatus(pack.id, "error", e.getMessage());
            installing = false;
        }
    }

    // -------------------------------------------------------------------------
    // Download + verify
    // -------------------------------------------------------------------------

    private File downloadZip(WorldPackEntry pack) throws IOException {
        File dest = new File(new File(plugin.getDataFolder(), "worlds"), pack.worldName + ".zip");
        dest.getParentFile().mkdirs();
        plugin.getLogger().info("[WorldPacks] Fetching: " + pack.downloadUrl);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(pack.downloadUrl))
                .header("User-Agent", "WerePires-Plugin")
                .timeout(Duration.ofMinutes(10))
                .GET().build();
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            Files.copy(res.body(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
        plugin.getLogger().info("[WorldPacks] Downloaded " + dest.length() / 1024 + " KB → " + dest.getName());
        return dest;
    }

    private void verifyChecksum(File zipFile, String expected) throws IOException {
        try {
            byte[] bytes  = Files.readAllBytes(zipFile.toPath());
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equalsIgnoreCase(expected)) {
                zipFile.delete();
                throw new IOException("SHA-256 mismatch — expected " + expected + " got " + actual);
            }
            plugin.logInfo("[WorldPacks] Checksum OK.");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    // -------------------------------------------------------------------------
    // Status report
    // -------------------------------------------------------------------------

    private void reportStatus(String packId, String status, String errorMsg) {
        String payload = "{\"pack_id\":" + q(packId)
                + ",\"status\":" + q(status)
                + ",\"error_message\":" + (errorMsg != null ? q(errorMsg) : "null") + "}";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> post("world_status", payload));
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private WorldPackEntry parseFirstPack(String json) {
        int start = json.indexOf("[{");
        if (start < 0) return null;
        int end = json.indexOf("}", start);
        if (end < 0) return null;
        String obj = json.substring(start + 1, end + 1);
        WorldPackEntry e = new WorldPackEntry();
        e.id          = extractString(obj, "id");
        e.worldName   = extractString(obj, "world_name");
        e.downloadUrl = extractString(obj, "download_url");
        e.sha256      = extractString(obj, "sha256");
        if (e.id == null || e.worldName == null || e.downloadUrl == null) return null;
        return e;
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int s = json.indexOf(search);
        if (s < 0) return null;
        s += search.length();
        int e = json.indexOf("\"", s);
        return e < 0 ? null : json.substring(s, e);
    }

    // -------------------------------------------------------------------------
    // HTTP
    // -------------------------------------------------------------------------

    private String post(String type, String payload) {
        try {
            String body = "{\"key\":" + q(serverKey)
                    + ",\"server_name\":" + q(serverName)
                    + ",\"type\":" + q(type)
                    + ",\"payload\":" + payload + "}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(EDGE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            plugin.logInfo("[WorldPacks] HTTP error: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void broadcastOps(String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("vampiresmp.admin")) p.sendMessage(msg);
            }
            plugin.getLogger().info(msg.replaceAll("§.", ""));
        });
    }

    private static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static class WorldPackEntry { String id, worldName, downloadUrl, sha256; }
    private static class GhAsset { String worldName, downloadUrl; long sizeBytes;
        GhAsset(String n, String u, long s) { worldName = n; downloadUrl = u; sizeBytes = s; } }
}
