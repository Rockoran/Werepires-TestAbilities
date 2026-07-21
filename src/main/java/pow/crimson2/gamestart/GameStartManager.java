package pow.crimson2.gamestart;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pow.crimson2.VampireSMPPlugin;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages all server-side game-start state:
 *  - Oneshot break countdown  (fires /breaktimer at T-5 min before intended pause)
 *  - Series phase tracking    (half1 → break_wait → half2 → building)
 *  - Build countdown BossBar  (auto-fires end-of-session or resumes next session)
 *  - Stored series config     (for /gamestart resumesession)
 *
 * External break-timer integration: call setBreakTimerActive(true/false) from
 * another plugin to signal break start/end. If no external signal arrives, the
 * internal fallback timer (breakDur minutes) handles the transition automatically.
 */
public class GameStartManager {

    private final VampireSMPPlugin plugin;

    // ── Oneshot break countdown ───────────────────────────────────────────────
    /** Seconds until /breaktimer fires. -1 = not scheduled. */
    private int     breakCountdown    = -1;
    private int     breakDurMins      = 0;
    private String  breakMode         = "resume";
    private boolean breakLoop         = false;
    private int     breakLoopLeadSecs = 0;
    private boolean waitingForBreak   = false;

    // ── Internal break timer ──────────────────────────────────────────────────
    /** Set externally or by internal fallback. */
    private boolean breakTimerActive = false;
    private int     breakTimerRemain = 0;   // seconds left in current break

    // ── Series state ─────────────────────────────────────────────────────────
    private boolean seriesActive         = false;
    private String  seriesPhase          = null;  // half1 / break_wait / half2 / building
    private int     seriesCountdown      = 0;
    private boolean seriesBreakStarted   = false;
    private int     seriesBreakCountdown = 0;     // fallback for break_wait phase

    // ── Last stored series config (for /gamestart resumesession) ─────────────
    private int    lastHalfMins  = 0;
    private int    lastBreakDur  = 0;
    private int    lastBuildMins = 0;
    private String lastSession   = "resume";
    private String lastBreakMode = "resume";
    private final Map<String, Integer> lastRoleCounts = new HashMap<>();

    // ── Per-world wizard defaults (loaded from _OneshotConfig.json / _SeriesConfig.json) ──
    private String  defaultSession    = "resume";
    private int     defaultBreakMins  = 0;
    private int     defaultBreakDur   = 15;
    private boolean defaultBreakLoop  = false;
    private int     defaultSeriesHalf = 60;
    private int     defaultSeriesBrk  = 15;
    private int     defaultSeriesBld  = 45;
    private final Map<String, Integer> defaultRoleCounts = new HashMap<>();

    // ── Build countdown ───────────────────────────────────────────────────────
    private int     buildCountdown = -1;
    private String  buildAction    = null;   // "end" or "start"
    private BossBar buildBar       = null;

    // ── Break boss bar ────────────────────────────────────────────────────────
    /** Shown while a break is in progress; turns green for the final 5 minutes. */
    private BossBar breakBar = null;

    private BukkitRunnable tickTask;

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    public GameStartManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        tickTask = new BukkitRunnable() {
            @Override public void run() { tick(); }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (buildBar != null) { buildBar.removeAll(); buildBar = null; }
        if (breakBar != null) { breakBar.removeAll(); breakBar = null; }
    }

    // =========================================================================
    // PER-SECOND TICK
    // =========================================================================

    private void tick() {
        tickBreak();
        tickSeries();
        tickBuildCountdown();
    }

    /**
     * Oneshot break state machine. Two phases:
     *
     *  1. Counting down to the break ({@code breakCountdown >= 0}). Warns 5 minutes before
     *     pausing, then pauses the session and starts the break timer.
     *  2. Break in progress ({@code waitingForBreak && breakTimerActive}). Warns 5 minutes
     *     before the break ends, then resumes (or end→start when mode is "restart"). In loop
     *     mode the countdown to the next break is re-armed.
     */
    private void tickBreak() {
        // Phase 2: a break is in progress — wait for it to end.
        if (waitingForBreak && breakTimerActive) {
            if (breakTimerRemain > 0) breakTimerRemain--;

            // 5-minute warning before the break ends / session resumes:
            //  - the boss bar turns green
            //  - the /pow admin break_warning sound sequence fires (warning_1 now, warning_2 at end)
            if (breakTimerRemain == 300 && breakDurMins * 60 > 300) {
                Bukkit.broadcastMessage("§6§lBREAK §8| §eSession resumes in §f5 minutes§e — get ready!");
                playBreakWarning();
            }

            updateBreakBar(breakTimerRemain, breakDurMins * 60);

            if (breakTimerRemain <= 0) {
                breakTimerActive = false;
                endBreak();
            }
            return;
        }

        // Phase 1: counting down to the break.
        if (breakCountdown < 0) return;
        if (breakCountdown > 0) {
            breakCountdown--;
            // 5 minutes before pausing: fire the break-warning sound sequence (warning_1 now,
            // warning_2 in 5 minutes — i.e. right when the break actually starts).
            if (breakCountdown == 300) {
                Bukkit.broadcastMessage("§6§lSESSION §8| §eBreak begins in §f5 minutes§e — the session will pause.");
                playBreakWarning();
            }
            return;
        }

        // breakCountdown hit 0 — start the break now.
        startBreak();
    }

    /** Pause the session and begin the break duration timer. */
    private void startBreak() {
        breakCountdown = -1;
        plugin.getSessionManager().pauseSession();
        int dur = Math.max(0, breakDurMins);
        Bukkit.broadcastMessage("§6§lBREAK §8| §eThe session is paused for §f" + dur + " §eminute" + (dur == 1 ? "" : "s") + ".");
        breakTimerActive = true;
        breakTimerRemain = dur * 60;
        waitingForBreak  = true;

        // Boss bar showing remaining break time (yellow until the final 5 minutes).
        createBreakBar();
        updateBreakBar(breakTimerRemain, dur * 60);

        // Very short break (<= 5 min): the 5-min warning point never arrives, so fire it now.
        if (dur * 60 <= 300) {
            playBreakWarning();
        }
    }

    /** End the break: resume (or restart) the session, and re-arm the loop if enabled. */
    private void endBreak() {
        waitingForBreak = false;
        removeBreakBar();

        if ("restart".equals(breakMode)) {
            // pow session end → wait → pow session start
            plugin.getSessionManager().endSession();
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> plugin.getSessionManager().startSession(), 60L);
        } else {
            plugin.getSessionManager().resumeSession();
        }

        if (breakLoop) {
            // Re-arm the countdown to the next break (keep mode/duration/loop for the cycle).
            breakCountdown = breakLoopLeadSecs;
            Bukkit.broadcastMessage("§6§lSESSION §8| §eNext break in §f" + (breakLoopLeadSecs / 60) + " §eminutes.");
        } else {
            // One-and-done: clear the break config.
            breakDurMins      = 0;
            breakMode         = "resume";
            breakLoop         = false;
            breakLoopLeadSecs = 0;
        }
    }

    // ── Break boss bar + warning sounds ───────────────────────────────────────

    /** Create the break boss bar (yellow) and add all online players. */
    private void createBreakBar() {
        if (breakBar != null) { breakBar.removeAll(); breakBar = null; }
        breakBar = Bukkit.createBossBar("§eBreak", BarColor.YELLOW, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) breakBar.addPlayer(p);
    }

    /** Refresh the break boss bar title/progress; turns green for the final 5 minutes. */
    private void updateBreakBar(int remainingSecs, int totalSecs) {
        if (breakBar == null) return;
        int total = Math.max(1, totalSecs);
        int s = Math.max(0, remainingSecs);
        int m = s / 60, sec = s % 60;
        String sf = sec < 10 ? "0" + sec : String.valueOf(sec);
        boolean finalStretch = s <= 300;
        breakBar.setColor(finalStretch ? BarColor.GREEN : BarColor.YELLOW);
        breakBar.setTitle((finalStretch ? "§aResuming in §f" : "§eBreak §8— §eresumes in §f") + m + ":" + sf);
        breakBar.setProgress(Math.max(0.0, Math.min(1.0, (double) s / total)));
    }

    private void removeBreakBar() {
        if (breakBar != null) { breakBar.removeAll(); breakBar = null; }
    }

    /**
     * Plays the break warning sound sequence — the same one used by
     * {@code /pow admin break_warning}: {@code crimson_warning_1} now, then
     * {@code crimson_warning_2} five minutes later (i.e. when the break ends).
     */
    public void playBreakWarning() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), "crimson:crimson.sound.crimson_warning_1",
                    org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), "crimson:crimson.sound.crimson_warning_2",
                        org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }, 6000L);
    }

    /** Drives series phase state machine. */
    private void tickSeries() {
        if (!seriesActive) return;

        if ("half1".equals(seriesPhase)) {
            if (seriesCountdown > 0) {
                seriesCountdown--;
                if (seriesCountdown == 900) {
                    Bukkit.broadcastMessage("§6§lSESSION §8| §eHalf 1 ends in §f15 minutes. Break incoming.");
                    playBell(1f);
                }
                if (seriesCountdown == 300) {
                    // Break-warning sound sequence: warning_1 now, warning_2 in 5 min (at pause).
                    playBreakWarning();
                    Bukkit.broadcastMessage("§6§lSESSION §8| §6Break in §f5 minutes§6 — the session will pause.");
                    seriesPhase          = "break_wait";
                    seriesBreakStarted   = false;
                    seriesBreakCountdown = 300 + lastBreakDur * 60;
                    breakTimerActive     = false;
                    breakTimerRemain     = 0;
                }
            }

        } else if ("half2".equals(seriesPhase)) {
            if (seriesCountdown > 0) {
                seriesCountdown--;
                if (seriesCountdown == 900) {
                    Bukkit.broadcastMessage("§6§lSESSION §8| §eHalf 2 ends in §f15 minutes.");
                    playBell(1f);
                }
                if (seriesCountdown == 300) {
                    Bukkit.broadcastMessage("§6§lSESSION §8| §eHalf 2 ends in §f5 minutes. Building phase incoming.");
                    playBell(1f);
                }
            } else {
                seriesPhase = "building";
                int bld = lastBuildMins;
                plugin.getSessionManager().endSession();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getSessionManager().preStartSession();
                    Bukkit.broadcastMessage("§6§lSESSION §8| §6Half 2 complete. Building phase started — §e"
                            + bld + " minutes.");
                    startBuildCountdown(bld, "end");
                }, 2L);
            }

        } else if ("break_wait".equals(seriesPhase)) {
            if (seriesBreakCountdown > 0) seriesBreakCountdown--;

            if (!seriesBreakStarted) {
                // Treat break as "started" when external flag fires OR 5-min warning lead has elapsed
                boolean externalStarted  = breakTimerActive;
                boolean fallbackStarted  = (seriesBreakCountdown <= lastBreakDur * 60);
                if (externalStarted || fallbackStarted) {
                    seriesBreakStarted = true;
                    plugin.getSessionManager().pauseSession();
                    Bukkit.broadcastMessage("§6§lBREAK §8| §eThe session is paused for §f" + lastBreakDur
                            + " §eminute" + (lastBreakDur == 1 ? "" : "s") + ".");
                    if (!breakTimerActive) {
                        breakTimerActive = true;
                        breakTimerRemain = lastBreakDur * 60;
                    }
                    // Boss bar for the break (mirrors the oneshot break).
                    createBreakBar();
                    updateBreakBar(Math.min(seriesBreakCountdown, lastBreakDur * 60), lastBreakDur * 60);
                    if (lastBreakDur * 60 <= 300) playBreakWarning();
                } else if (seriesBreakCountdown <= 0) {
                    transitionToHalf2();
                }
            } else {
                // 5-minute warning before the break ends / Half 2 resumes:
                // boss bar turns green + the break_warning sound sequence fires.
                if (seriesBreakCountdown == 300 && lastBreakDur * 60 > 300) {
                    Bukkit.broadcastMessage("§6§lBREAK §8| §eHalf 2 resumes in §f5 minutes§e — get ready!");
                    playBreakWarning();
                }
                updateBreakBar(seriesBreakCountdown, lastBreakDur * 60);
                // Waiting for break to end
                if (!breakTimerActive || seriesBreakCountdown <= 0) {
                    breakTimerActive = false;
                    transitionToHalf2();
                }
            }
        }
    }

    private void transitionToHalf2() {
        seriesBreakStarted   = false;
        seriesBreakCountdown = 0;
        removeBreakBar();
        seriesPhase          = "half2";
        seriesCountdown      = lastHalfMins * 60;
        Bukkit.broadcastMessage("§6§lSESSION §8| §aHalf 2 has begun!");
        playBell(2f);
        // Resume or restart based on how the series was configured
        if ("restart".equals(lastBreakMode)) {
            plugin.getSessionManager().endSession();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getSessionManager().startSession(), 2L);
        } else {
            plugin.getSessionManager().resumeSession();
        }
    }

    /** Drives build countdown and BossBar. */
    private void tickBuildCountdown() {
        if (buildCountdown < 0) return;

        if (buildCountdown > 0) {
            buildCountdown--;

            if (buildCountdown == 300) {
                if ("end".equals(buildAction))
                    Bukkit.broadcastMessage("§6§lBUILDING §8| §eSession ends in §f5 minutes.");
                else
                    Bukkit.broadcastMessage("§a§lBUILDING §8| §aSession starts in §f5 minutes!");
                playBell(1f);
            }
            if (buildCountdown == 120) {
                if ("end".equals(buildAction))
                    Bukkit.broadcastMessage("§6§lBUILDING §8| §eSession ends in §f2 minutes.");
                else
                    Bukkit.broadcastMessage("§a§lBUILDING §8| §aSession starts in §f2 minutes!");
                playBell(1f);
            }
            if (buildCountdown == 60) {
                if (buildBar == null) {
                    String title = "end".equals(buildAction)
                            ? "§eSession ending in §f1:00" : "§aSession starting in §f1:00";
                    BarColor color = "end".equals(buildAction) ? BarColor.YELLOW : BarColor.GREEN;
                    buildBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
                    for (Player p : Bukkit.getOnlinePlayers()) buildBar.addPlayer(p);
                }
                playBell(1f);
            }
            if (buildBar != null) {
                int s = buildCountdown;
                int bm = s / 60, bs = s % 60;
                String bsf = bs < 10 ? "0" + bs : String.valueOf(bs);
                buildBar.setTitle("end".equals(buildAction)
                        ? "§eSession ending in §f" + bm + ":" + bsf
                        : "§aSession starting in §f" + bm + ":" + bsf);
                buildBar.setProgress(Math.min(1.0, buildCountdown / 60.0));
            }
        } else {
            // Hit 0
            if (buildBar != null) { buildBar.removeAll(); buildBar = null; }
            String endAction = buildAction;
            buildCountdown = -1;
            buildAction    = null;

            if ("end".equals(endAction)) {
                seriesActive = false;
                seriesPhase  = null;
                plugin.getSessionManager().endSession();
                Bukkit.broadcastMessage("§c§lSession has ended. Thanks for playing!");
            } else {
                launchStoredSession();
            }
        }
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /** Full teardown — called by /gamestart sessionend. */
    public void sessionEnd() {
        plugin.getSessionManager().endSession();
        clearBreakState();
        seriesActive         = false;
        seriesPhase          = null;
        seriesCountdown      = 0;
        seriesBreakStarted   = false;
        seriesBreakCountdown = 0;
        if (buildBar != null) { buildBar.removeAll(); buildBar = null; }
        removeBreakBar();
        buildCountdown = -1;
        buildAction    = null;
        Bukkit.broadcastMessage("§c§lSession has ended.");
    }

    private void clearBreakState() {
        removeBreakBar();
        breakCountdown    = -1;
        breakDurMins      = 0;
        breakMode         = "resume";
        breakLoop         = false;
        breakLoopLeadSecs = 0;
        waitingForBreak   = false;
        breakTimerActive  = false;
        breakTimerRemain  = 0;
    }

    /**
     * Called by /gamestart resumesession.
     * @return false if no stored series config or build countdown already running.
     */
    public boolean resumeSession() {
        if (lastHalfMins == 0) return false;
        if (buildCountdown >= 0) return false;
        plugin.getSessionManager().preStartSession();
        Bukkit.broadcastMessage("§6§lBUILDING PHASE §8| §eNext session starts in §f"
                + lastBuildMins + " minutes.");
        startBuildCountdown(lastBuildMins, "start");
        return true;
    }

    /** Execute oneshot mode — called by GameStartCommand wizard on confirm. */
    public void executeOneshot(String session, int breakMins, int breakDurMins,
                               boolean loop, Map<String, Integer> roleCounts, Player sender) {
        sender.sendMessage("§8§m======================================");
        sender.sendMessage("§6§lEXECUTING SESSION SETUP...");
        sender.sendMessage("§8§m======================================");

        Runnable finish = () -> {
            if (plugin.getStarterKitManager() != null && plugin.getStarterKitManager().isEnabled()) {
                sender.sendMessage("§7Distributing kits...");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kitall");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kitstart");
            }
            if (!roleCounts.isEmpty()) {
                sender.sendMessage("§7Selecting roles...");
                plugin.getRoleManager().startAllRoles(sender, roleCounts);
            }
            if (breakMins > 0) {
                String bm2 = "restart".equals(session) ? "restart" : "resume";
                // Full time until the break; the 5-minute warning fires internally at T-5min.
                breakCountdown      = breakMins * 60;
                this.breakDurMins   = breakDurMins;
                breakMode           = bm2;
                breakLoop           = loop;
                breakLoopLeadSecs   = breakMins * 60;
                sender.sendMessage("§7Break in " + breakMins + " min (mode: " + bm2
                        + ", lasts " + breakDurMins + " min), warning 5 min before.");
            }
            sender.sendMessage("");
            sender.sendMessage("§a§lSession is live! §7Use §f/gamestart timer §7to track the break.");
            sender.sendMessage("§8§m======================================");
        };

        if ("resume".equals(session)) {
            sender.sendMessage("§7Resuming session...");
            plugin.getSessionManager().resumeSession();
            finish.run();
        } else {
            sender.sendMessage("§7Ending current session...");
            plugin.getSessionManager().endSession();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sender.sendMessage("§7Starting fresh session...");
                plugin.getSessionManager().startSession();
                finish.run();
            }, 2L);
        }
    }

    /** Execute series mode — called by GameStartCommand wizard on confirm. */
    public void executeSeries(String session, int halfMins, int breakDur, int buildMins,
                              Map<String, Integer> roleCounts, Player sender) {
        sender.sendMessage("§8§m======================================");
        sender.sendMessage("§6§lEXECUTING SERIES SESSION...");
        sender.sendMessage("§8§m======================================");

        Runnable finish = () -> {
            if (plugin.getStarterKitManager() != null && plugin.getStarterKitManager().isEnabled()) {
                sender.sendMessage("§7Distributing kits...");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kitall");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kitstart");
            }
            if (!roleCounts.isEmpty()) {
                sender.sendMessage("§7Selecting roles...");
                plugin.getRoleManager().startAllRoles(sender, roleCounts);
            }
            lastHalfMins  = halfMins;
            lastBreakDur  = breakDur;
            lastBuildMins = buildMins;
            lastSession   = session;
            lastBreakMode = "restart".equals(session) ? "restart" : "resume";
            lastRoleCounts.clear();
            lastRoleCounts.putAll(roleCounts);

            seriesActive    = true;
            seriesPhase     = "half1";
            seriesCountdown = halfMins * 60;
            Bukkit.broadcastMessage("§6§lSERIES SESSION §8| §eHalf 1 of 2 — §f"
                    + halfMins + "m §8| Break: §f" + breakDur + "m §8| Build: §f" + buildMins + "m");
            sender.sendMessage("");
            sender.sendMessage("§a§lSeries is live! §7Use §f/gamestart timer §7to check phase.");
            sender.sendMessage("§8§m======================================");
        };

        if ("resume".equals(session)) {
            sender.sendMessage("§7Resuming session...");
            plugin.getSessionManager().resumeSession();
            finish.run();
        } else {
            sender.sendMessage("§7Ending current session...");
            plugin.getSessionManager().endSession();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sender.sendMessage("§7Starting fresh session...");
                plugin.getSessionManager().startSession();
                finish.run();
            }, 2L);
        }
    }

    /**
     * Configure the oneshot break countdown without touching session state.
     * Called by InitGameManager after it has already started the session.
     *
     * @param breakMode "resume" to continue the session when the break ends, or "restart"
     *                  to end then start a fresh session (rolls tomes).
     */
    public void setupBreakOnly(int breakMins, int breakDurMins, boolean loop, String breakMode) {
        if (breakMins <= 0) return;
        // Full time until the break; the 5-minute warning fires internally at T-5min.
        this.breakCountdown    = breakMins * 60;
        this.breakDurMins      = breakDurMins;
        this.breakMode         = "restart".equals(breakMode) ? "restart" : "resume";
        this.breakLoop         = loop;
        this.breakLoopLeadSecs = breakMins * 60;
    }

    /**
     * Activate the series phase tracker without touching session state.
     * Called by InitGameManager after it has already started the session.
     */
    public void startSeriesOnly(int halfMins, int breakDur, int buildMins,
                                Map<String, Integer> roleCounts) {
        this.lastHalfMins  = halfMins;
        this.lastBreakDur  = breakDur;
        this.lastBuildMins = buildMins;
        this.lastBreakMode = "resume";
        this.lastRoleCounts.clear();
        this.lastRoleCounts.putAll(roleCounts);
        this.seriesActive    = true;
        this.seriesPhase     = "half1";
        this.seriesCountdown = halfMins * 60;
        Bukkit.broadcastMessage("§6§lSERIES SESSION §8| §eHalf 1 of 2 — §f"
                + halfMins + "m §8| Break: §f" + breakDur + "m §8| Build: §f" + buildMins + "m");
    }

    /** Start a build-phase countdown. action = "end" or "start". */
    public void startBuildCountdown(int mins, String action) {
        buildCountdown = mins * 60;
        buildAction    = action;
        if (buildBar != null) { buildBar.removeAll(); buildBar = null; }
    }

    private void launchStoredSession() {
        Bukkit.broadcastMessage("§a§lSession is starting!");
        plugin.getSessionManager().startSession();
        seriesActive    = true;
        seriesPhase     = "half1";
        seriesCountdown = lastHalfMins * 60;
        Bukkit.broadcastMessage("§6§lSERIES SESSION §8| §eHalf 1 of 2 — §f" + lastHalfMins + " minutes");
    }

    /** Add a newly-joined player to any active BossBar (build or break). */
    public void onPlayerJoin(Player p) {
        if (buildBar != null) buildBar.addPlayer(p);
        if (breakBar != null) breakBar.addPlayer(p);
    }

    // ── External break-timer integration ─────────────────────────────────────

    /** Signal from an external break-timer plugin. */
    public void setBreakTimerActive(boolean active) {
        this.breakTimerActive = active;
        if (!active) breakTimerRemain = 0;
    }

    public boolean isBreakTimerActive() { return breakTimerActive; }

    // ── Status getters ────────────────────────────────────────────────────────

    public boolean isSeriesActive()     { return seriesActive; }
    public String  getSeriesPhase()     { return seriesPhase; }
    public int     getSeriesCountdown() { return seriesCountdown; }
    public int     getBreakCountdown()  { return breakCountdown; }
    public int     getBreakDurMins()    { return breakDurMins; }
    public boolean isWaitingForBreak()  { return waitingForBreak; }
    public int     getBreakTimeRemaining() { return waitingForBreak ? breakTimerRemain : 0; }
    public int     getLastHalfMins()    { return lastHalfMins; }
    public int     getLastBuildMins()   { return lastBuildMins; }
    public boolean hasBuildCountdown()  { return buildCountdown >= 0; }

    // ── Per-world wizard defaults ─────────────────────────────────────────────

    /**
     * Called by WorldManager.finalizeLoad() after a world is loaded.
     * Pass null for either file to reset that mode's defaults to their
     * hardcoded values so previous-world settings don't bleed through.
     */
    public void loadWorldDefaults(File oneshotFile, File seriesFile) {
        // Always reset to hardcoded defaults first
        defaultSession    = "resume";
        defaultBreakMins  = 0;
        defaultBreakDur   = 15;
        defaultBreakLoop  = false;
        defaultSeriesHalf = 60;
        defaultSeriesBrk  = 15;
        defaultSeriesBld  = 45;
        defaultRoleCounts.clear();

        Gson gson = new Gson();

        if (oneshotFile != null) {
            try (FileReader fr = new FileReader(oneshotFile)) {
                JsonObject o = gson.fromJson(fr, JsonObject.class);
                if (o != null) {
                    if (o.has("session"))    defaultSession   = o.get("session").getAsString();
                    if (o.has("break_mins")) defaultBreakMins = o.get("break_mins").getAsInt();
                    if (o.has("break_dur"))  defaultBreakDur  = o.get("break_dur").getAsInt();
                    if (o.has("break_loop")) defaultBreakLoop = o.get("break_loop").getAsBoolean();
                    if (o.has("role_counts")) {
                        JsonObject rc = o.getAsJsonObject("role_counts");
                        for (String role : rc.keySet()) {
                            int cnt = rc.get(role).getAsInt();
                            if (cnt > 0) defaultRoleCounts.put(role, cnt);
                        }
                    }
                }
                plugin.getLogger().info("[WorldDefaults] Loaded oneshot defaults from " + oneshotFile.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[WorldDefaults] Failed to read " + oneshotFile.getName() + ": " + e.getMessage());
            }
        }

        if (seriesFile != null) {
            try (FileReader fr = new FileReader(seriesFile)) {
                JsonObject o = gson.fromJson(fr, JsonObject.class);
                if (o != null) {
                    if (o.has("session"))    defaultSession    = o.get("session").getAsString();
                    if (o.has("half_mins"))  defaultSeriesHalf = o.get("half_mins").getAsInt();
                    if (o.has("break_dur"))  defaultSeriesBrk  = o.get("break_dur").getAsInt();
                    if (o.has("build_mins")) defaultSeriesBld  = o.get("build_mins").getAsInt();
                    if (o.has("role_counts")) {
                        JsonObject rc = o.getAsJsonObject("role_counts");
                        for (String role : rc.keySet()) {
                            int cnt = rc.get(role).getAsInt();
                            if (cnt > 0) defaultRoleCounts.merge(role, cnt, Integer::max);
                        }
                    }
                }
                plugin.getLogger().info("[WorldDefaults] Loaded series defaults from " + seriesFile.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[WorldDefaults] Failed to read " + seriesFile.getName() + ": " + e.getMessage());
            }
        }
    }

    public String  getDefaultSession()            { return defaultSession;    }
    public int     getDefaultBreakMins()           { return defaultBreakMins;  }
    public int     getDefaultBreakDur()            { return defaultBreakDur;   }
    public boolean isDefaultBreakLoop()            { return defaultBreakLoop;  }
    public int     getDefaultSeriesHalf()          { return defaultSeriesHalf; }
    public int     getDefaultSeriesBrk()           { return defaultSeriesBrk;  }
    public int     getDefaultSeriesBld()           { return defaultSeriesBld;  }
    public int     getDefaultRoleCount(String role){ return defaultRoleCounts.getOrDefault(role, 0); }
    public Map<String, Integer> getDefaultRoleCounts() { return Collections.unmodifiableMap(defaultRoleCounts); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void playBell(float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch);
        }
    }
}
