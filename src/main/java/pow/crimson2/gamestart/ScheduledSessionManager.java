package pow.crimson2.gamestart;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

import java.io.File;
import java.io.IOException;

/** Persistent five-phase scheduler used by /pow admin sessionsetup. */
public final class ScheduledSessionManager implements Listener {
    public enum Phase { IDLE, INITIAL_BUILDING, PRE_BREAK_SESSION, BREAK, POST_BREAK_SESSION, FINAL_BUILDING, COMPLETE }

    private static final int MAX_MINUTES = 10080;
    private final VampireSMPPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private BukkitTask task;
    private BossBar bar;
    private Phase phase = Phase.IDLE;
    private int remaining;
    private int initialBuild;
    private int preBreak;
    private int breakDuration;
    private int postBreak;
    private int finalBuild;
    private int saveTicker;
    private boolean restoringBreak;
    private boolean restoringPhase;

    public ScheduledSessionManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "scheduled-session.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public static boolean validMinutes(int value) { return value >= 1 && value <= MAX_MINUTES; }

    public boolean isActive() {
        return phase != Phase.IDLE && phase != Phase.COMPLETE;
    }

    public void start(int initialBuild, int preBreak, int breakDuration, int postBreak, int finalBuild) {
        cancelInternal(false);
        plugin.getGameStartManager().cancelAutomationTimers();
        this.initialBuild = initialBuild;
        this.preBreak = preBreak;
        this.breakDuration = breakDuration;
        this.postBreak = postBreak;
        this.finalBuild = finalBuild;
        prepareBuilding();
        enter(Phase.INITIAL_BUILDING, initialBuild * 60);
        Bukkit.broadcastMessage("§b§lSESSION PLAN §8| §fBuilding for §b" + initialBuild
                + "m§f, then session §e" + preBreak + "m§f, break §6" + breakDuration
                + "m§f, session §e" + postBreak + "m§f, final building §b" + finalBuild + "m§f.");
    }

    public boolean cancel(CommandSender sender) {
        if (!isActive()) return false;
        cancelInternal(true);
        Bukkit.broadcastMessage("§c§lSESSION PLAN CANCELLED §8| §7Cancelled by §f" + sender.getName() + "§7.");
        return true;
    }

    public boolean extendSession(int minutes) {
        if (!validMinutes(minutes)) return false;
        if (phase != Phase.PRE_BREAK_SESSION && phase != Phase.POST_BREAK_SESSION) return false;
        remaining += minutes * 60;
        if (phase == Phase.PRE_BREAK_SESSION) preBreak += minutes;
        else postBreak += minutes;
        save();
        updateBar();
        Bukkit.broadcastMessage("§a§lSESSION EXTENDED §8| §fAdded §a" + minutes + " minute"
                + (minutes == 1 ? "" : "s") + "§f. Remaining: §a" + format(remaining));
        return true;
    }

    public boolean extendBreak(int minutes) {
        if (!validMinutes(minutes) || phase != Phase.BREAK) return false;
        if (!plugin.getGameStartManager().extendScheduledBreak(minutes)) return false;
        breakDuration += minutes;
        remaining = plugin.getGameStartManager().getBreakTimeRemaining();
        save();
        Bukkit.broadcastMessage("§6§lBREAK EXTENDED §8| §fAdded §e" + minutes + " minute"
                + (minutes == 1 ? "" : "s") + "§f. Remaining: §e" + format(remaining));
        return true;
    }

    public void status(CommandSender sender) {
        sender.sendMessage("§6§l=== Scheduled Session ===");
        sender.sendMessage("§7Phase: §f" + phaseName());
        sender.sendMessage("§7Remaining: §f" + (isActive() ? format(currentRemaining()) : "none"));
        sender.sendMessage("§7Plan: §b" + initialBuild + "m build §8→ §e" + preBreak
                + "m session §8→ §6" + breakDuration + "m break §8→ §e" + postBreak
                + "m session §8→ §b" + finalBuild + "m build");
    }

    public void onPlayerJoin(Player player) {
        refreshPlayerBar(player);
    }

    public void refreshPlayerBar(Player player) {
        if (bar == null) return;
        if (SessionBossBarPreference.isEnabled(player)) bar.addPlayer(player);
        else bar.removePlayer(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onPlayerJoin(event.getPlayer());
    }

    private void tick() {
        if (!isActive()) return;
        if (restoringPhase) {
            restoringPhase = false;
            restoreSessionState();
        }
        if (phase == Phase.BREAK) {
            if (restoringBreak) {
                restoringBreak = false;
                plugin.getGameStartManager().restoreScheduledBreak(remaining, breakDuration);
            }
            if (plugin.getGameStartManager().isWaitingForBreak()) {
                remaining = plugin.getGameStartManager().getBreakTimeRemaining();
            } else {
                if (plugin.getSessionManager().getSessionState() != 1) {
                    plugin.getSessionManager().resumeSession();
                }
                enter(Phase.POST_BREAK_SESSION, postBreak * 60);
            }
        } else {
            if (remaining > 0) remaining--;
            warn();
            updateBar();
            if (remaining <= 0) advance();
        }
        if (++saveTicker >= 30) { saveTicker = 0; save(); }
    }

    private void advance() {
        switch (phase) {
            case INITIAL_BUILDING -> {
                removeBar();
                plugin.getSessionManager().startSession();
                enter(Phase.PRE_BREAK_SESSION, preBreak * 60);
            }
            case PRE_BREAK_SESSION -> {
                removeBar();
                phase = Phase.BREAK;
                remaining = breakDuration * 60;
                save();
                plugin.getGameStartManager().beginScheduledBreak(breakDuration);
            }
            case POST_BREAK_SESSION -> {
                removeBar();
                plugin.getSessionManager().endSession();
                plugin.getSessionManager().primeNewSession();
                plugin.getSessionManager().preStartSession();
                enter(Phase.FINAL_BUILDING, finalBuild * 60);
            }
            case FINAL_BUILDING -> {
                removeBar();
                phase = Phase.COMPLETE;
                remaining = 0;
                save();
                Bukkit.broadcastMessage("§b§lBUILDING COMPLETE §8| §fThe scheduled session timeline is complete.");
            }
            default -> { }
        }
    }

    private void prepareBuilding() {
        int state = plugin.getSessionManager().getSessionState();
        if (state == 1 || state == 2) plugin.getSessionManager().endSession();
        if (plugin.getSessionManager().getSessionState() != 0) plugin.getSessionManager().primeNewSession();
        plugin.getSessionManager().preStartSession();
    }

    private void enter(Phase next, int seconds) {
        phase = next;
        remaining = seconds;
        createBar();
        save();
    }

    private void warn() {
        if (remaining != 300 && remaining != 120 && remaining != 60) return;
        Bukkit.broadcastMessage("§6§lSESSION PLAN §8| §e" + phaseName() + " ends in §f" + format(remaining) + "§e.");
    }

    private void createBar() {
        removeBar();
        // The active-session phases are intentionally bar-free. Breaks use the
        // GameStartManager's break bar, while this manager owns building bars.
        if (phase != Phase.INITIAL_BUILDING && phase != Phase.FINAL_BUILDING) return;
        bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (SessionBossBarPreference.isEnabled(player)) bar.addPlayer(player);
        }
        updateBar();
    }

    private void updateBar() {
        if (bar == null) return;
        int total = switch (phase) {
            case INITIAL_BUILDING -> initialBuild * 60;
            case PRE_BREAK_SESSION -> preBreak * 60;
            case POST_BREAK_SESSION -> postBreak * 60;
            case FINAL_BUILDING -> finalBuild * 60;
            default -> Math.max(1, remaining);
        };
        bar.setTitle("§f" + phaseName() + " §8— §f" + format(remaining));
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / Math.max(1, total))));
    }

    private int currentRemaining() {
        if (phase == Phase.BREAK && plugin.getGameStartManager().isWaitingForBreak()) {
            return plugin.getGameStartManager().getBreakTimeRemaining();
        }
        return remaining;
    }

    private String phaseName() {
        return switch (phase) {
            case INITIAL_BUILDING -> "Initial building";
            case PRE_BREAK_SESSION -> "Session before break";
            case BREAK -> "Break";
            case POST_BREAK_SESSION -> "Session after break";
            case FINAL_BUILDING -> "Final building";
            case COMPLETE -> "Complete";
            default -> "Idle";
        };
    }

    private static String format(int seconds) {
        int safe = Math.max(0, seconds);
        return (safe / 60) + ":" + String.format("%02d", safe % 60);
    }

    private void cancelInternal(boolean saveNow) {
        if (phase == Phase.BREAK && plugin.getGameStartManager().cancelScheduledBreak()
                && plugin.getSessionManager().getSessionState() == 2) {
            plugin.getSessionManager().resumeSession();
        }
        removeBar();
        phase = Phase.IDLE;
        remaining = 0;
        restoringBreak = false;
        if (saveNow) save();
    }

    private void removeBar() {
        if (bar != null) { bar.removeAll(); bar = null; }
    }

    private void load() {
        try { phase = Phase.valueOf(data.getString("phase", "IDLE")); }
        catch (IllegalArgumentException ex) { phase = Phase.IDLE; }
        remaining = data.getInt("remaining-seconds", 0);
        initialBuild = data.getInt("initial-building-minutes", 15);
        preBreak = data.getInt("pre-break-session-minutes", 60);
        breakDuration = data.getInt("break-minutes", 15);
        postBreak = data.getInt("post-break-session-minutes", 60);
        finalBuild = data.getInt("final-building-minutes", 15);
        if (isActive()) {
            if (phase == Phase.BREAK) restoringBreak = true;
            else createBar();
            restoringPhase = true;
        }
    }

    private void restoreSessionState() {
        int state = plugin.getSessionManager().getSessionState();
        if (phase == Phase.PRE_BREAK_SESSION || phase == Phase.POST_BREAK_SESSION) {
            if (state == 2) plugin.getSessionManager().resumeSession();
            else if (state != 1) plugin.getSessionManager().startSession();
        } else if (phase == Phase.INITIAL_BUILDING || phase == Phase.FINAL_BUILDING) {
            if (state != 4) prepareBuilding();
        }
    }

    private void save() {
        data.set("phase", phase.name());
        data.set("remaining-seconds", remaining);
        data.set("initial-building-minutes", initialBuild);
        data.set("pre-break-session-minutes", preBreak);
        data.set("break-minutes", breakDuration);
        data.set("post-break-session-minutes", postBreak);
        data.set("final-building-minutes", finalBuild);
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save scheduled-session.yml: " + ex.getMessage()); }
    }

    public void shutdown() {
        save();
        removeBar();
        if (task != null) { task.cancel(); task = null; }
    }
}
