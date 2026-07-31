package pow.crimson2.roles;

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
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

import java.util.List;

/**
 * Dialog-based GUI for /rolecfg (no-arg usage).
 *
 * Screens:
 *   Role Config        — main: Humans | Vampires
 *   Humans — Roles     — VH, Medic, Tracker (with toggle + save)
 *   Vampires — Config  — edit default vampire count
 *   Vampire Hunter     — toggle enable, set FV cooldown/duration
 *   Medic              — toggle enable
 *   Tracker            — toggle enable, set track duration/cooldown
 *
 * No chest inventory, no chat capture, no Listener needed.
 */
@SuppressWarnings("UnstableApiUsage")
public class RoleCfgGui {

    private final VampireSMPPlugin plugin;
    private final RoleManager      rm;

    public RoleCfgGui(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.rm     = plugin.getRoleManager();
    }

    // =========================================================================
    // Main
    // =========================================================================

    public void openMain(Player p) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Role Config", NamedTextColor.GOLD))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure human role settings and default vampire count."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Humans"),
                    Component.text("Configure human roles."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> openHumans(p)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Vampires"),
                    Component.text("Configure vampire settings."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> openVampires(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Humans
    // =========================================================================

    private void openHumans(Player p) {
        boolean vhOn = rm.isRoleEnabled("vampire_hunter");
        boolean mdOn = rm.isRoleEnabled("medic");
        boolean trOn = rm.isRoleEnabled("tracker");
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Humans — Roles", NamedTextColor.GOLD))
                .body(List.of(DialogBody.plainMessage(Component.text("Enable or disable human roles and configure their settings."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Vampire Hunter  " + onOff(vhOn)),
                    Component.text("Click to configure."), 200,
                    DialogAction.customClick((view, aud) -> sync(() -> openVH(p)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Medic  " + onOff(mdOn)),
                    Component.text("Click to configure."), 200,
                    DialogAction.customClick((view, aud) -> sync(() -> openMedic(p)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Tracker  " + onOff(trOn)),
                    Component.text("Click to configure."), 200,
                    DialogAction.customClick((view, aud) -> sync(() -> openTracker(p)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("← Back"), null, 150,
                    DialogAction.customClick((view, aud) -> sync(() -> openMain(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Vampires
    // =========================================================================

    private void openVampires(Player p) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Vampires — Config", NamedTextColor.GOLD))
                .body(List.of(DialogBody.plainMessage(Component.text("Set the default number of vampires assigned each session."))))
                .inputs(List.of(
                    DialogInput.text("vampire_count",
                            Component.text("Default Vampire Count"))
                        .initial(String.valueOf(rm.getVampireCount()))
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Save"), null, 100,
                    DialogAction.customClick((view, aud) -> {
                        String raw = view.getText("vampire_count");
                        sync(() -> applyInt(p, raw, "vampire_count", 1, () -> openVampires(p)));
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("← Back"), null, 100,
                    DialogAction.customClick((view, aud) -> sync(() -> openMain(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Vampire Hunter
    // =========================================================================

    private void openVH(Player p) {
        boolean on = rm.isRoleEnabled("vampire_hunter");
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Vampire Hunter", NamedTextColor.GOLD))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure the Vampire Hunter role — enable/disable and set ability cooldowns."))))
                .inputs(List.of(
                    DialogInput.text("fv_cooldown",
                            Component.text("Find Vampires Cooldown (minutes)"))
                        .initial(String.valueOf(rm.getFvCooldownMinutes()))
                        .maxLength(4)
                        .build(),
                    DialogInput.text("fv_duration",
                            Component.text("Scan Duration (seconds)"))
                        .initial(String.valueOf(rm.getFvDurationSeconds()))
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    on ? Component.text("✓ Enabled — click to disable", NamedTextColor.GREEN)
                       : Component.text("✗ Disabled — click to enable", NamedTextColor.RED),
                    null, 220,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        rm.setRoleEnabled("vampire_hunter", !rm.isRoleEnabled("vampire_hunter"));
                        openVH(p);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("Save Values"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String rawCd  = view.getText("fv_cooldown");
                        String rawDur = view.getText("fv_duration");
                        sync(() -> {
                            applyInt(p, rawCd,  "fv_cooldown",  1, null);
                            applyInt(p, rawDur, "fv_duration",  1, null);
                            openVH(p);
                        });
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("← Back"), null, 100,
                    DialogAction.customClick((view, aud) -> sync(() -> openHumans(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Medic
    // =========================================================================

    private void openMedic(Player p) {
        boolean on = rm.isRoleEnabled("medic");
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Medic", NamedTextColor.GOLD))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure the Medic role — enable or disable."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    on ? Component.text("✓ Enabled — click to disable", NamedTextColor.GREEN)
                       : Component.text("✗ Disabled — click to enable", NamedTextColor.RED),
                    null, 220,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        rm.setRoleEnabled("medic", !rm.isRoleEnabled("medic"));
                        openMedic(p);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("← Back"), null, 100,
                    DialogAction.customClick((view, aud) -> sync(() -> openHumans(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Tracker
    // =========================================================================

    private void openTracker(Player p) {
        boolean on = rm.isRoleEnabled("tracker");
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Tracker", NamedTextColor.GOLD))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure the Tracker role — enable/disable and set compass timings."))))
                .inputs(List.of(
                    DialogInput.text("tracker_track",
                            Component.text("Track Duration (minutes)"))
                        .initial(String.valueOf(rm.getTrackerTrackMins()))
                        .maxLength(4)
                        .build(),
                    DialogInput.text("tracker_cooldown",
                            Component.text("Compass Cooldown (minutes)"))
                        .initial(String.valueOf(rm.getTrackerCooldownMins()))
                        .maxLength(4)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    on ? Component.text("✓ Enabled — click to disable", NamedTextColor.GREEN)
                       : Component.text("✗ Disabled — click to enable", NamedTextColor.RED),
                    null, 220,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        rm.setRoleEnabled("tracker", !rm.isRoleEnabled("tracker"));
                        openTracker(p);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("Save Values"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String rawTrack = view.getText("tracker_track");
                        String rawCd    = view.getText("tracker_cooldown");
                        sync(() -> {
                            applyInt(p, rawTrack, "tracker_track",    1, null);
                            applyInt(p, rawCd,    "tracker_cooldown", 1, null);
                            openTracker(p);
                        });
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("← Back"), null, 100,
                    DialogAction.customClick((view, aud) -> sync(() -> openHumans(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Parse, validate, and apply an integer config value. Runs {@code then} when done (even on error). */
    private void applyInt(Player p, String raw, String key, int min, Runnable then) {
        if (raw != null) {
            try {
                int val = Integer.parseInt(raw.trim());
                if (val < min) {
                    p.sendMessage(Component.text("Value must be ≥ " + min + ".", NamedTextColor.RED));
                } else {
                    switch (key) {
                        case "fv_cooldown":
                            rm.setFvCooldownMinutes(val);
                            p.sendMessage(Component.text("FV cooldown → " + val + " min.", NamedTextColor.GREEN));
                            break;
                        case "fv_duration":
                            rm.setFvDurationSeconds(val);
                            p.sendMessage(Component.text("FV scan duration → " + val + " sec.", NamedTextColor.GREEN));
                            break;
                        case "tracker_track":
                            rm.setTrackerTrackMins(val);
                            p.sendMessage(Component.text("Track duration → " + val + " min.", NamedTextColor.GREEN));
                            break;
                        case "tracker_cooldown":
                            rm.setTrackerCooldownMins(val);
                            p.sendMessage(Component.text("Tracker cooldown → " + val + " min.", NamedTextColor.GREEN));
                            break;
                        case "vampire_count":
                            rm.setVampireCount(val);
                            p.sendMessage(Component.text("Default vampire count → " + val + ".", NamedTextColor.GREEN));
                            break;
                    }
                }
            } catch (NumberFormatException e) {
                p.sendMessage(Component.text("Invalid number: \"" + raw + "\"", NamedTextColor.RED));
            }
        }
        if (then != null) then.run();
    }

    /** Schedule {@code r} on the main server thread (safe to call from dialog callbacks). */
    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static String onOff(boolean on) { return on ? "[ON]" : "[OFF]"; }
}
