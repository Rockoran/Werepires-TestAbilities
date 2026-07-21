package pow.crimson2.gamestart;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.roles.RoleManager;

import java.util.*;

/**
 * Handles /gamestart and the session setup wizard.
 *
 * The wizard uses Paper's native Dialog API — no chest GUIs, no chat capture,
 * no per-player state maps.  All wizard state is carried forward through
 * WizardState objects captured in dialog-button closures.
 *
 * Wizard flow (ONESHOT):
 *   Intro → Mode → Roles → Role counts (per role) →
 *   Break Schedule → [Break Duration → Loop Breaks?] → Session → Confirm
 *
 * Wizard flow (SERIES):
 *   Intro → Mode → Half Length → Series Break → Build Phase →
 *   Roles → Role counts (per role) → Session → Confirm
 */
@SuppressWarnings("UnstableApiUsage")
public class GameStartCommand implements CommandExecutor, TabCompleter, Listener {

    private final VampireSMPPlugin plugin;
    private final GameStartManager gsm;
    private final RoleManager      rm;

    public GameStartCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.gsm    = plugin.getGameStartManager();
        this.rm     = plugin.getRoleManager();
    }

    // =========================================================================
    // Per-wizard state (one instance per /gamestart run)
    // =========================================================================

    private static final class WizardState {
        String  mode       = "oneshot";
        String  session    = "resume";
        int     breakMins  = 0;
        int     breakDur   = 0;
        boolean breakLoop  = false;
        int     seriesHalf = 60;
        int     seriesBrk  = 15;
        int     seriesBld  = 45;
        final Map<String, Integer> roleCounts = new LinkedHashMap<>();
    }

    // =========================================================================
    // CommandExecutor
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command is player-only.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.isOp()) { p.sendMessage("§cYou need OP to use this command."); return true; }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "timer":
                    sendTimerStatus(p);
                    return true;

                case "cancel":
                    p.sendMessage("§7(No wizard running — dialogs are self-contained.)");
                    return true;

                case "sessionend":
                    gsm.sessionEnd();
                    return true;

                case "resumesession":
                    if (!gsm.resumeSession()) {
                        if (gsm.hasBuildCountdown())
                            p.sendMessage("§cA build countdown is already running.");
                        else
                            p.sendMessage("§cNo series session on record. Run the wizard first.");
                    }
                    return true;

                case "breaktimer":
                    if (args.length > 1) {
                        boolean active = "start".equalsIgnoreCase(args[1]);
                        gsm.setBreakTimerActive(active);
                        p.sendMessage("§7Break timer active = §f" + active);
                    }
                    return true;

                default:
                    break;
            }
        }

        openIntro(p);
        return true;
    }

    private void sendTimerStatus(Player p) {
        if (gsm.isSeriesActive()) {
            String phase = gsm.getSeriesPhase();
            int    cd    = gsm.getSeriesCountdown();
            if (cd > 0) {
                int m = cd / 60, s = cd % 60;
                p.sendMessage("§e[Series] Phase: §f" + phase + " §8| §eTime left: §f" + m + "m " + s + "s");
            } else {
                p.sendMessage("§e[Series] Phase: §f" + phase);
            }
        } else if (gsm.getBreakCountdown() >= 0) {
            int cd = gsm.getBreakCountdown();
            int m = cd / 60, s = cd % 60;
            String sf = s < 10 ? "0" + s : String.valueOf(s);
            p.sendMessage("§eNext break in §f" + m + "m " + sf + "s §8| Duration: §f" + gsm.getBreakDurMins() + "m");
        } else if (gsm.isWaitingForBreak()) {
            int cd = gsm.getBreakTimeRemaining();
            int m = cd / 60, s = cd % 60;
            String sf = s < 10 ? "0" + s : String.valueOf(s);
            p.sendMessage("§eBreak in progress §8— §esession resumes in §f" + m + "m " + sf + "s§e.");
        } else {
            p.sendMessage("§7No break timer scheduled.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1)
            return Arrays.asList("timer", "cancel", "sessionend", "resumesession", "breaktimer");
        if (args.length == 2 && "breaktimer".equalsIgnoreCase(args[0]))
            return Arrays.asList("start", "end");
        return Collections.emptyList();
    }

    /**
     * Creates a WizardState pre-populated with per-world defaults loaded by
     * WorldManager (from _OneshotConfig.json / _SeriesConfig.json).
     * Falls back to hardcoded values when no world defaults are loaded.
     */
    private WizardState newWizardState() {
        WizardState s = new WizardState();
        s.session    = gsm.getDefaultSession();
        s.breakMins  = gsm.getDefaultBreakMins();
        s.breakDur   = gsm.getDefaultBreakDur();
        s.breakLoop  = gsm.isDefaultBreakLoop();
        s.seriesHalf = gsm.getDefaultSeriesHalf();
        s.seriesBrk  = gsm.getDefaultSeriesBrk();
        s.seriesBld  = gsm.getDefaultSeriesBld();
        return s;
    }

    // =========================================================================
    // Wizard — Screen 1: Intro
    // =========================================================================

    public void openIntro(Player p) {
        boolean kitAvail = plugin.getStarterKitManager() != null
                && plugin.getStarterKitManager().isEnabled();
        String kitLine = kitAvail ? "§a[OK] §fStarter Kit" : "§8[--] §7Starter Kit";

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Setup", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure and launch a new game session."))))
                .inputs(List.of())
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Start Wizard"),
                    Component.text("§7Systems: §aRole System, §aBreak Timer, " + kitLine),
                    200,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> showMode(p, newWizardState())), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(Component.text("Setup cancelled.", NamedTextColor.RED))), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 2: Mode
    // =========================================================================

    private void showMode(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Mode", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Choose between a standard single session or a two-half series."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Oneshot"),
                    Component.text("Standard single session. Break schedule optional."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.mode = "oneshot";
                        showRoles(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Series", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Two halves + break + building phase."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.mode = "series";
                        showSeriesHalf(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(Component.text("Setup cancelled.", NamedTextColor.RED))), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Series screens (3 screens: half length, break, build)
    // =========================================================================

    private void showSeriesHalf(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Half Length", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Set how long each half of the series lasts."))))
                .inputs(List.of(
                    DialogInput.text("half_mins", Component.text("Minutes per half (e.g. 60)"))
                        .initial(String.valueOf(state.seriesHalf))
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String raw = view.getText("half_mins");
                        sync(() -> {
                            int v = parseIntSafe(raw, -1);
                            if (v <= 5) {
                                p.sendMessage(Component.text("Must be > 5 minutes.", NamedTextColor.RED));
                                showSeriesHalf(p, state);
                            } else {
                                state.seriesHalf = v;
                                showSeriesBrk(p, state);
                            }
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showSeriesBrk(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Series Break", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Set how long the break between halves lasts."))))
                .inputs(List.of(
                    DialogInput.text("brk_mins", Component.text("Break duration (minutes)"))
                        .initial(String.valueOf(state.seriesBrk))
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String raw = view.getText("brk_mins");
                        sync(() -> {
                            int v = parseIntSafe(raw, -1);
                            if (v < 1) {
                                p.sendMessage(Component.text("Must be ≥ 1 minute.", NamedTextColor.RED));
                                showSeriesBrk(p, state);
                            } else {
                                state.seriesBrk = v;
                                showSeriesBld(p, state);
                            }
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showSeriesBld(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Build Phase", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Set how long the building phase lasts before the session begins."))))
                .inputs(List.of(
                    DialogInput.text("bld_mins", Component.text("Build phase length (minutes)"))
                        .initial(String.valueOf(state.seriesBld))
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String raw = view.getText("bld_mins");
                        sync(() -> {
                            int v = parseIntSafe(raw, -1);
                            if (v < 1) {
                                p.sendMessage(Component.text("Must be ≥ 1 minute.", NamedTextColor.RED));
                                showSeriesBld(p, state);
                            } else {
                                state.seriesBld = v;
                                showRoles(p, state);
                            }
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen: Roles (shared by both paths)
    // =========================================================================

    private void showRoles(Player p, WizardState state) {
        boolean vhOn = rm.isRoleEnabled("vampire_hunter");
        boolean mdOn = rm.isRoleEnabled("medic");
        boolean trOn = rm.isRoleEnabled("tracker");

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Roles", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Select which special roles are active this session."))))
                .inputs(List.of(
                    DialogInput.bool("vampire_hunter", Component.text("Vampire Hunter"))
                        .initial(vhOn)
                        .build(),
                    DialogInput.bool("medic", Component.text("Medic"))
                        .initial(mdOn)
                        .build(),
                    DialogInput.bool("tracker", Component.text("Tracker"))
                        .initial(trOn)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Proceed →"),
                    Component.text("Selected roles will have players randomly assigned."),
                    200,
                    DialogAction.customClick((view, aud) -> {
                        Boolean vh = view.getBoolean("vampire_hunter");
                        Boolean md = view.getBoolean("medic");
                        Boolean tr = view.getBoolean("tracker");
                        sync(() -> {
                            if (vh != null) rm.setRoleEnabled("vampire_hunter", vh);
                            if (md != null) rm.setRoleEnabled("medic",           md);
                            if (tr != null) rm.setRoleEnabled("tracker",         tr);
                            Deque<String> q = new ArrayDeque<>();
                            for (String r : RoleManager.KNOWN_ROLES)
                                if (rm.isRoleEnabled(r)) q.add(r);
                            state.roleCounts.clear();
                            showRoleCounts(p, state, q);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen: Role count (one dialog per enabled role)
    // =========================================================================

    private void showRoleCounts(Player p, WizardState state, Deque<String> remaining) {
        String role = remaining.poll();
        if (role == null) {
            afterRoleCounts(p, state);
            return;
        }
        String label = RoleManager.getRoleLabel(role);
        float defaultCount = (float) gsm.getDefaultRoleCount(role);
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Role Count: " + label, NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Set how many players to assign as " + label + " this session."))))
                .inputs(List.of(
                    DialogInput.numberRange("count",
                            Component.text("Number of " + label + "s to assign"),
                            0f, 10f)
                        .step(1f)
                        .initial(defaultCount)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Next →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        Float rawF = view.getFloat("count");
                        sync(() -> {
                            int cnt = rawF != null ? Math.round(rawF) : 0;
                            if (cnt > 0) state.roleCounts.put(role, cnt);
                            showRoleCounts(p, state, remaining);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    /** Called after all role count dialogs; routes to the right next step. */
    private void afterRoleCounts(Player p, WizardState state) {
        if ("series".equals(state.mode)) {
            showSession(p, state);
        } else {
            showBreakSchedule(p, state);
        }
    }

    // =========================================================================
    // Oneshot screens: break schedule, duration, loop
    // =========================================================================

    private void showBreakSchedule(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Break Schedule", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Schedule a break during the session. Enter 0 in both fields to skip."))))
                .inputs(List.of(
                    DialogInput.text("break_hrs",
                            Component.text("Hours until first break"))
                        .initial("0")
                        .maxLength(3)
                        .build(),
                    DialogInput.text("break_mins",
                            Component.text("Minutes until first break"))
                        .initial("0")
                        .maxLength(2)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String rawHrs  = view.getText("break_hrs");
                        String rawMins = view.getText("break_mins");
                        sync(() -> {
                            int hrs  = parseIntSafe(rawHrs,  0);
                            int mins = parseIntSafe(rawMins, 0);
                            if (hrs < 0 || mins < 0) {
                                p.sendMessage(Component.text("Values must be ≥ 0.", NamedTextColor.RED));
                                showBreakSchedule(p, state);
                                return;
                            }
                            int total = hrs * 60 + mins;
                            state.breakMins = total;
                            if (total > 0) showBreakDuration(p, state);
                            else           showSession(p, state);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showBreakDuration(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Break Duration", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Set how long the break lasts."))))
                .inputs(List.of(
                    DialogInput.text("break_dur",
                            Component.text("Break duration (minutes)"))
                        .initial("15")
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String raw = view.getText("break_dur");
                        sync(() -> {
                            int v = parseIntSafe(raw, -1);
                            if (v < 1) {
                                p.sendMessage(Component.text("Must be ≥ 1 minute.", NamedTextColor.RED));
                                showBreakDuration(p, state);
                            } else {
                                state.breakDur = v;
                                showBreakLoop(p, state);
                            }
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showBreakLoop(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Loop Breaks?", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Choose whether breaks repeat throughout the session."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Yes — Loop Breaks", NamedTextColor.GREEN),
                    Component.text("Break timer restarts after each break ends."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.breakLoop = true;
                        showSession(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("No — Single Break", NamedTextColor.RED),
                    Component.text("Only one break will occur."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.breakLoop = false;
                        showSession(p, state);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Common screens: session type, confirm, execute
    // =========================================================================

    private void showSession(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Session Type", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Choose how the session state is handled at start."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Resume", NamedTextColor.GREEN),
                    Component.text("Continue the existing session."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.session = "resume";
                        showConfirm(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Restart", NamedTextColor.YELLOW),
                    Component.text("End current session, then start fresh."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.session = "restart";
                        showConfirm(p, state);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showConfirm(Player p, WizardState state) {
        // Build a tooltip-style summary for the confirm button
        StringBuilder sb = new StringBuilder("§7Mode: §f").append(state.mode)
            .append("\n§7Session: §f").append(state.session);
        if ("series".equals(state.mode)) {
            sb.append("\n§7Half: §f").append(state.seriesHalf).append(" min")
              .append("\n§7Break: §f").append(state.seriesBrk).append(" min")
              .append("\n§7Build: §f").append(state.seriesBld).append(" min");
        } else {
            if (state.breakMins > 0) {
                int bh = state.breakMins / 60, bm = state.breakMins % 60;
                String bStr = (bh > 0 ? bh + "h " : "") + bm + "m";
                sb.append("\n§7Break: T+§f").append(bStr)
                  .append("§7, lasts §f").append(state.breakDur).append("§7 min")
                  .append("\n§7Loop: §f").append(state.breakLoop ? "Yes" : "No");
            } else {
                sb.append("\n§7Break: §8not scheduled");
            }
        }
        if (!state.roleCounts.isEmpty()) {
            sb.append("\n§7Roles:");
            state.roleCounts.forEach((r, c) ->
                sb.append(" §f").append(RoleManager.getRoleLabel(r)).append("×").append(c));
        }

        Component summary = Component.text(sb.toString());

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("GameStart — Confirm", NamedTextColor.GREEN))
                .body(List.of(DialogBody.plainMessage(Component.text("Review your settings and confirm to start the session."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("✓ Start Session", NamedTextColor.GREEN),
                    summary, 200,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> executeWizard(p, state)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("✗ Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(
                            Component.text("Setup cancelled.", NamedTextColor.RED))), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void executeWizard(Player p, WizardState state) {
        Map<String, Integer> rc = new LinkedHashMap<>(state.roleCounts);
        rc.entrySet().removeIf(e -> e.getValue() <= 0);

        if ("series".equals(state.mode)) {
            gsm.executeSeries(state.session, state.seriesHalf,
                              state.seriesBrk, state.seriesBld, rc, p);
        } else {
            gsm.executeOneshot(state.session, state.breakMins,
                               state.breakDur, state.breakLoop, rc, p);
        }
    }

    // =========================================================================
    // Listeners (keep for join/quit only)
    // =========================================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        gsm.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // No wizard state maps to clean up — dialogs are closure-based.
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
