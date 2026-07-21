package pow.crimson2.network;

import org.bukkit.Bukkit;
import pow.crimson2.VampireSMPPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class WerePiresNetwork {

    private static final String EDGE_FUNCTION_URL = "https://iktbixeivylotgzspexl.supabase.co/functions/v1/report";
    private static final int POLL_INTERVAL_TICKS = 20 * 60; // 60s between reactivation checks

    private final VampireSMPPlugin plugin;
    private final HttpClient http;
    private final String serverName;
    private final String serverKey;

    private int heartbeatTaskId = -1;
    private int pollTaskId = -1;
    private final AtomicBoolean suspended = new AtomicBoolean(false);

    public WerePiresNetwork(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String ip = Bukkit.getServer().getIp();
        int port = Bukkit.getServer().getPort();
        this.serverName = (ip.isEmpty() ? "0.0.0.0" : ip) + ":" + port;
        this.serverKey = plugin.getConfig().getString("werepires-network.server-key", "");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        if (serverKey.isEmpty()) {
            plugin.getLogger().severe("╔══════════════════════════════════════════════╗");
            plugin.getLogger().severe("║         WerePires — NO LICENSE KEY          ║");
            plugin.getLogger().severe("║  Set werepires-network.server-key in         ║");
            plugin.getLogger().severe("║  config.yml. Contact the plugin admin.       ║");
            plugin.getLogger().severe("╚══════════════════════════════════════════════╝");
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.getPluginManager().disablePlugin(plugin));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = postSync("validate", "{}");
            if (result == null || result.contains("\"error\"")) {
                String reason = result != null ? result : "No response from network";
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().severe("╔══════════════════════════════════════════════╗");
                    plugin.getLogger().severe("║       WerePires — INVALID LICENSE KEY        ║");
                    plugin.getLogger().severe("║  " + padReason(reason) + "  ║");
                    plugin.getLogger().severe("╚══════════════════════════════════════════════╝");
                    Bukkit.getPluginManager().disablePlugin(plugin);
                });
            } else {
                startHeartbeat();
                plugin.logInfo("[WerePiresNetwork] Licensed — connected as \"" + serverName + "\"");
            }
        });
    }

    public void stop() {
        cancelTask(heartbeatTaskId);
        cancelTask(pollTaskId);
        heartbeatTaskId = -1;
        pollTaskId = -1;
        if (!serverKey.isEmpty()) post("offline", "{}");
    }

    // -------------------------------------------------------------------------
    // Suspend / Resume (no restart required)
    // -------------------------------------------------------------------------

    private void suspendByNetwork(String reason) {
        if (suspended.getAndSet(true)) return; // already suspended

        cancelTask(heartbeatTaskId);
        heartbeatTaskId = -1;

        String cleanReason = stripJson(reason);
        String kickMsg = "§4§l[WerePires] §cThis server's WerePires license was deactivated.\n§7Contact the server admin for assistance.\n§8Reason: " + cleanReason;
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.kickPlayer(kickMsg);
        }
        plugin.getLogger().severe("╔══════════════════════════════════════════════╗");
        plugin.getLogger().severe("║     WerePires — REMOTELY DEACTIVATED         ║");
        plugin.getLogger().severe("║  Plugin suspended. Polling for reactivation. ║");
        plugin.getLogger().severe("╚══════════════════════════════════════════════╝");

        // Keep a lightweight poll alive so reactivation is auto-detected
        pollTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::pollForReactivation, 20L * 30, POLL_INTERVAL_TICKS).getTaskId();
    }

    private void pollForReactivation() {
        if (!suspended.get()) return;
        String result = postSync("validate", "{}");
        if (result != null && !result.contains("\"error\"")) {
            Bukkit.getScheduler().runTask(plugin, this::resumeByNetwork);
        }
    }

    private void resumeByNetwork() {
        if (!suspended.getAndSet(false)) return; // wasn't suspended

        cancelTask(pollTaskId);
        pollTaskId = -1;

        startHeartbeat();

        String msg = "§a§l[WerePires] §aThis server's WerePires license has been reactivated!";
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
        plugin.getLogger().info("╔══════════════════════════════════════════════╗");
        plugin.getLogger().info("║     WerePires — REACTIVATED                  ║");
        plugin.getLogger().info("║  Heartbeat resumed. No restart required.     ║");
        plugin.getLogger().info("╚══════════════════════════════════════════════╝");
    }

    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    private void startHeartbeat() {
        heartbeatTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::sendHeartbeat, 40L, 20L * 30).getTaskId();
    }

    private void sendHeartbeat() {
        int playerCount = Bukkit.getOnlinePlayers().size();
        boolean sessionActive = plugin.getSessionManager().isSessionActive();
        String version = plugin.getPluginMeta().getVersion();
        String payload = "{"
                + "\"player_count\":" + playerCount + ","
                + "\"session_active\":" + sessionActive + ","
                + "\"version\":" + q(version)
                + "}";
        post("heartbeat", payload);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isSuspended() {
        return suspended.get();
    }

    public boolean isEnabled() {
        return !serverKey.isEmpty();
    }

    public void logEvent(String eventType, String playerUuid, String playerName, String extraData) {
        if (serverKey.isEmpty() || suspended.get()) return;
        String payload = "{"
                + "\"event_type\":" + q(eventType) + ","
                + "\"player_uuid\":" + q(playerUuid) + ","
                + "\"player_name\":" + q(playerName) + ","
                + "\"extra_data\":" + (extraData != null ? q(extraData) : "null")
                + "}";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> post("event", payload));
    }

    public void updatePlayer(String uuid, String username, String role, String extraData) {
        if (serverKey.isEmpty() || suspended.get()) return;
        boolean isOp = extraData != null && extraData.contains("op=true");
        String payload = "{"
                + "\"minecraft_uuid\":" + q(uuid) + ","
                + "\"username\":" + q(username) + ","
                + "\"role\":" + q(role) + ","
                + "\"is_op\":" + isOp
                + "}";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> post("player", payload));
    }

    // -------------------------------------------------------------------------
    // HTTP
    // -------------------------------------------------------------------------

    private String postSync(String type, String payload) {
        try {
            HttpRequest req = buildRequest(type, payload);
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 403) return "{\"error\":\"" + stripJson(res.body()) + "\"}";
            return res.body();
        } catch (Exception e) {
            return null;
        }
    }

    private void post(String type, String payload) {
        try {
            HttpRequest req = buildRequest(type, payload);
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() == 403) {
                        Bukkit.getScheduler().runTask(plugin, () -> suspendByNetwork(res.body()));
                    }
                });
        } catch (Exception e) {
            plugin.logInfo("[WerePiresNetwork] Post failed: " + e.getMessage());
        }
    }

    private HttpRequest buildRequest(String type, String payload) {
        String body = "{"
                + "\"key\":" + q(serverKey) + ","
                + "\"server_name\":" + q(serverName) + ","
                + "\"type\":" + q(type) + ","
                + "\"payload\":" + payload
                + "}";
        return HttpRequest.newBuilder()
                .uri(URI.create(EDGE_FUNCTION_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void cancelTask(int id) {
        if (id != -1) Bukkit.getScheduler().cancelTask(id);
    }

    private static String stripJson(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("\\{\"error\":\"", "").replaceAll("\"\\}", "").trim();
    }

    private static String padReason(String reason) {
        reason = stripJson(reason);
        if (reason.length() > 44) reason = reason.substring(0, 41) + "...";
        return reason + " ".repeat(Math.max(0, 44 - reason.length()));
    }

    private static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
