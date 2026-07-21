package pow.crimson2.setup;

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
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.kit.StarterKitManager;
import pow.crimson2.roles.RoleManager;
import pow.crimson2.thralls.ThrallManager;
import pow.crimson2.thralls.ThrallProfile;

import java.util.*;

/**
 * Drives the /playersetup wizard using Paper's native Dialog API.
 *
 * Steps:
 *   0a. Character picker    — if thralls loaded + player has profiles
 *   0b. Character detail
 *   0c. Character edit      — name + taste in one dialog (replaces chat capture)
 *   0d. Thrall preferences
 *   1.  Intro               — shows detected systems
 *   2.  Vampire pool opt-in
 *   3+. Role opt-in         — one dialog per enabled role
 *   4.  Starter kit prompt  — opens /starterkit GUI when confirmed
 *
 * No InventoryClickEvent, no AsyncPlayerChatEvent — all native dialog.
 */
@SuppressWarnings("UnstableApiUsage")
public class PlayerSetupManager implements Listener, CommandExecutor {

    /** Tracks which players are in the wizard (for StarterKitCommand callback). */
    private final Set<String> active = new HashSet<>();

    private final VampireSMPPlugin plugin;

    public PlayerSetupManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Public API used by StarterKitCommand (kit-close callback)
    // =========================================================================

    public boolean isActive(String playerName) { return active.contains(playerName); }

    public void onKitMenuClosed(Player p) { done(p); }

    // =========================================================================
    // CommandExecutor — /playersetup
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command requires a player.");
            return true;
        }
        Player p = (Player) sender;
        active.add(p.getName());

        ThrallManager tm = plugin.getThrallManager();
        if (tm != null) {
            ThrallProfile profile = tm.getProfile(p.getUniqueId());
            if (profile != null && profile.getCount() > 0) {
                openCharacterPicker(p);
                return true;
            }
            // Thrall system loaded but player has no profiles yet — offer to create one
            openProfileCreatePrompt(p);
            return true;
        }
        openIntro(p);
        return true;
    }

    // =========================================================================
    // Screen 0 (pre): Create profile prompt — shown when player has no profiles
    // =========================================================================

    private void openProfileCreatePrompt(Player p) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Character Profile", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Create a character profile to give yourself a persona name and blood taste "
                    + "for the thrall system.\n"
                    + "You can skip this and set one up later with /thrall profile create."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Create a Profile", NamedTextColor.GREEN),
                    Component.text("Set up a character identity now."),
                    200,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> openProfileCreateForm(p)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Skip for Now", NamedTextColor.GRAY), null, 150,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> openIntro(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void openProfileCreateForm(Player p) {
        ThrallManager tm = plugin.getThrallManager();
        if (tm == null) { openIntro(p); return; }
        ThrallProfile profile = tm.getOrCreateProfile(p.getUniqueId());

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Create Character Profile", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Enter your character's name and what your blood tastes like to vampires."))))
                .inputs(List.of(
                    DialogInput.text("char_name", Component.text("Character Name"))
                        .initial("").maxLength(32).build(),
                    DialogInput.text("char_taste", Component.text("Blood Taste"))
                        .initial("Warm. Metallic. Familiar.").maxLength(128).build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Create", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((view, aud) -> {
                        String newName  = view.getText("char_name");
                        String newTaste = view.getText("char_taste");
                        sync(() -> {
                            if (newName == null || newName.trim().isEmpty()) {
                                p.sendMessage(Component.text("Name cannot be empty.", NamedTextColor.RED));
                                openProfileCreateForm(p);
                                return;
                            }
                            String taste = (newTaste == null || newTaste.trim().isEmpty()
                                    || "default".equalsIgnoreCase(newTaste.trim()))
                                ? "§8Warm. Metallic. Familiar."
                                : newTaste.trim();
                            int idx = profile.addProfile(newName.trim(), taste);
                            profile.setActiveIndex(idx);
                            tm.getDataManager().saveAllProfiles();
                            p.sendActionBar(Component.text("Profile created!", NamedTextColor.GREEN));
                            openThrallPref(p, idx);
                        });
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("Skip", NamedTextColor.GRAY), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> openIntro(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 0a: Character picker
    // =========================================================================

    private void openCharacterPicker(Player p) {
        ThrallManager tm = plugin.getThrallManager();
        if (tm == null) { openIntro(p); return; }
        ThrallProfile profile = tm.getProfile(p.getUniqueId());
        if (profile == null || profile.getCount() == 0) { openThrallPref(p, 1); return; }

        int count  = profile.getCount();
        int activeIdx = profile.getActiveIndex();
        List<ThrallProfile.ProfileEntry> entries = profile.getEntries();

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < Math.min(count, 5); i++) {
            final int idx = i + 1; // 1-based
            String name  = entries.get(i).getName();
            String check = (activeIdx == idx) ? " ✓" : "";
            buttons.add(ActionButton.create(
                Component.text(name + check,
                    (activeIdx == idx) ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                null, 150,
                DialogAction.customClick((view, aud) ->
                    sync(() -> openCharacterDetail(p, idx)), ClickCallback.Options.builder().build())));
        }
        buttons.add(ActionButton.create(
            Component.text("Skip — No Character", NamedTextColor.GRAY),
            Component.text("Continue as myself without selecting a character."),
            200,
            DialogAction.customClick((view, aud) ->
                sync(() -> openThrallPref(p, activeIdx > 0 ? activeIdx : 1)), ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Choose Your Character", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("Select which character you are playing this session."))))
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 0b: Character detail
    // =========================================================================

    private void openCharacterDetail(Player p, int idx) {
        ThrallManager tm = plugin.getThrallManager();
        if (tm == null) { openThrallPref(p, idx); return; }
        ThrallProfile profile = tm.getProfile(p.getUniqueId());
        if (profile == null) { openThrallPref(p, idx); return; }

        List<ThrallProfile.ProfileEntry> entries = profile.getEntries();
        if (idx < 1 || idx > entries.size()) { openThrallPref(p, idx); return; }

        String  name     = entries.get(idx - 1).getName();
        String  taste    = entries.get(idx - 1).getTaste();
        boolean isActive = (profile.getActiveIndex() == idx);

        Component tooltip = Component.text(
            "Blood taste: " + (taste != null ? taste : "(none)") + "\n"
            + (isActive ? "Currently active" : "Not currently active"));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Character: " + name, NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("View or edit details for this character."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    isActive ? Component.text("Continue with This Character", NamedTextColor.GREEN)
                             : Component.text("Select — Play as This Character", NamedTextColor.GREEN),
                    tooltip, 250,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        profile.setActiveIndex(idx);
                        tm.getDataManager().saveAllProfiles();
                        openThrallPref(p, idx);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Edit Name / Blood Taste", NamedTextColor.YELLOW),
                    Component.text("Change this character's name and blood taste."),
                    220,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> openCharacterEdit(p, idx)), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("← Back"), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> openCharacterPicker(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 0c: Character edit (name + taste in one dialog — no chat capture)
    // =========================================================================

    private void openCharacterEdit(Player p, int idx) {
        ThrallManager tm = plugin.getThrallManager();
        if (tm == null) { openCharacterDetail(p, idx); return; }
        ThrallProfile profile = tm.getProfile(p.getUniqueId());
        if (profile == null) { openCharacterDetail(p, idx); return; }

        List<ThrallProfile.ProfileEntry> entries = profile.getEntries();
        String curName  = (idx >= 1 && idx <= entries.size()) ? entries.get(idx - 1).getName()  : "";
        String rawTaste = (idx >= 1 && idx <= entries.size()) ? entries.get(idx - 1).getTaste() : null;
        String curTaste = (rawTaste != null) ? rawTaste : "§8Warm. Metallic. Familiar.";

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Edit Character", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("Update your character's display name and blood taste description."))))
                .inputs(List.of(
                    DialogInput.text("char_name", Component.text("Character Name"))
                        .initial(curName)
                        .maxLength(32)
                        .build(),
                    DialogInput.text("char_taste", Component.text("Blood Taste"))
                        .initial(curTaste)
                        .maxLength(128)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Save", NamedTextColor.GREEN), null, 100,
                    DialogAction.customClick((view, aud) -> {
                        String newName  = view.getText("char_name");
                        String newTaste = view.getText("char_taste");
                        sync(() -> {
                            if (newName == null || newName.trim().isEmpty()) {
                                p.sendMessage(Component.text("Name cannot be empty.", NamedTextColor.RED));
                                openCharacterEdit(p, idx);
                                return;
                            }
                            List<ThrallProfile.ProfileEntry> ent = profile.getEntries();
                            if (idx >= 1 && idx <= ent.size()) {
                                ent.get(idx - 1).setName(newName.trim());
                                String taste = (newTaste == null || newTaste.trim().isEmpty()
                                        || "default".equalsIgnoreCase(newTaste.trim()))
                                    ? "§8Warm. Metallic. Familiar."
                                    : newTaste.trim();
                                ent.get(idx - 1).setTaste(taste);
                                tm.getDataManager().saveAllProfiles();
                                p.sendActionBar(Component.text("Character updated.", NamedTextColor.GREEN));
                            }
                            openCharacterDetail(p, idx);
                        });
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("← Cancel"), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> openCharacterDetail(p, idx)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 0d: Thrall preferences
    // =========================================================================

    private void openThrallPref(Player p, int charIdx) {
        ThrallManager tm = plugin.getThrallManager();
        ThrallProfile profile = tm != null ? tm.getProfile(p.getUniqueId()) : null;
        String cur = (profile != null && charIdx > 0) ? profile.getThrallPref(charIdx) : null;

        String[]   keys   = {"open",  "owner",  "both",  "no",  "try"};
        String[]   labels = {
            "Open to Being a Thrall",
            "Open to Owning a Thrall",
            "Open to Both",
            "Please Don't",
            "You Can Try — But May Not Succeed"
        };

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            String lbl = labels[i] + (key.equals(cur) ? " ✓" : "");
            buttons.add(ActionButton.create(
                Component.text(lbl, key.equals(cur) ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                null, 220,
                DialogAction.customClick((view, aud) -> sync(() -> {
                    if (tm != null && profile != null) {
                        int idx = charIdx > 0 ? charIdx : 1;
                        profile.setThrallPref(idx, key);
                        tm.getDataManager().saveAllProfiles();
                    }
                    openIntro(p);
                }), ClickCallback.Options.builder().build())));
        }
        buttons.add(ActionButton.create(
            Component.text("Skip for Now", NamedTextColor.GRAY), null, 150,
            DialogAction.customClick((view, aud) ->
                sync(() -> openIntro(p)), ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Thrall Preferences", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("Set your preference for the thrall blood-bond system."))))
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 1: Intro
    // =========================================================================

    private void openIntro(Player p) {
        boolean thrallsLoaded = plugin.getThrallManager() != null;
        boolean rolesLoaded   = plugin.getRoleManager() != null
                             && !plugin.getRoleManager().getRoles().isEmpty();
        boolean kitLoaded     = plugin.getStarterKitManager() != null;

        String systems =
            (thrallsLoaded ? "§a[OK] Thralls" : "§8[--] Thralls") + "  " +
            (rolesLoaded   ? "§a[OK] Roles"   : "§8[--] Roles")   + "  " +
            (kitLoaded     ? "§a[OK] Kit"      : "§8[--] Kit");

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Player Setup", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("Complete your setup to register for roles and configure your kit."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Begin Setup ▸", NamedTextColor.GREEN),
                    Component.text(systems),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        RoleManager rm = plugin.getRoleManager();
                        if (rm != null && !rm.getRoles().isEmpty()) {
                            Deque<String> q = new ArrayDeque<>();
                            for (String role : RoleManager.KNOWN_ROLES)
                                if (rm.isRoleEnabled(role)) q.add(role);
                            openVampirePool(p, q);
                        } else if (kitLoaded) {
                            openKitPrompt(p);
                        } else {
                            done(p);
                        }
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        active.remove(p.getName());
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 2: Vampire pool opt-in
    // =========================================================================

    private void openVampirePool(Player p, Deque<String> roleQueue) {
        RoleManager rm = plugin.getRoleManager();
        boolean inPool = rm != null && rm.getVampirePool().contains(p.getName());

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Vampire Pool", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("Choose whether to be eligible for vampire selection at session start."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Yes — Opt In", NamedTextColor.GREEN),
                    Component.text("Puts you in the vampire selection pool."
                        + (inPool ? " (currently opted in)" : "")),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        if (rm != null) rm.addToVampirePool(p.getName());
                        nextRole(p, roleQueue);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("No — Opt Out", NamedTextColor.RED),
                    Component.text("Always human — never selected as a vampire."
                        + (!inPool ? " (currently opted out)" : "")),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        if (rm != null) rm.removeFromVampirePool(p.getName());
                        nextRole(p, roleQueue);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 3+: Role opt-in (one per enabled role)
    // =========================================================================

    private void nextRole(Player p, Deque<String> roleQueue) {
        String role = roleQueue.poll();
        if (role == null) {
            if (plugin.getStarterKitManager() != null) openKitPrompt(p);
            else done(p);
            return;
        }
        openRoleDialog(p, role, roleQueue);
    }

    private void openRoleDialog(Player p, String role, Deque<String> roleQueue) {
        String label = RoleManager.getRoleLabel(role);
        String desc  = roleDesc(role);
        RoleManager rm = plugin.getRoleManager();
        boolean inPool = rm != null && rm.getRolePool(role).contains(p.getName());

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Role: " + label, NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(desc))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Yes — Opt In", NamedTextColor.GREEN),
                    Component.text(desc + (inPool ? "\n(currently opted in)" : "")),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        if (rm != null) rm.addToPool(role, p.getName());
                        nextRole(p, roleQueue);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("No — Opt Out", NamedTextColor.RED),
                    Component.text(desc + (!inPool ? "\n(currently opted out)" : "")),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        if (rm != null) rm.removeFromPool(role, p.getName());
                        nextRole(p, roleQueue);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 4: Kit prompt
    // =========================================================================

    private void openKitPrompt(Player p) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Starter Kit", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure which item categories you receive in your starter kit."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Yes — Edit Kit", NamedTextColor.GREEN),
                    Component.text("Choose which item categories you receive."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        if (plugin.getStarterKitCommand() != null)
                            plugin.getStarterKitCommand().openMainMenu(p);
                        else
                            done(p);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("No — Finish", NamedTextColor.GRAY), null, 150,
                    DialogAction.customClick((view, aud) -> sync(() -> done(p)), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Completion
    // =========================================================================

    void done(Player p) {
        // Skin setup always runs last — handles first-time and return-visit cases
        SkinSetupDialog.open(plugin, p, () -> {
            p.sendMessage(Component.text("Setup complete! Your preferences have been saved.", NamedTextColor.GREEN));
            active.remove(p.getName());
        });
    }

    // =========================================================================
    // PlayerQuitEvent: clean up active state
    // =========================================================================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getName());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static String roleDesc(String role) {
        switch (role) {
            case "vampire_hunter":
                return "Hunt and expose vampires. Use /findvampires to highlight nearby vampires.";
            case "medic":
                return "Support the human team by keeping allies alive throughout the session.";
            case "tracker":
                return "Track a specific player using a compass. Right-click to mark target.";
            default:
                return "A special role with unique abilities.";
        }
    }
}
