package pow.crimson2.managers;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;
import pow.crimson2.gamestart.GameStartManager;
import pow.crimson2.roles.RoleManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("UnstableApiUsage")
public class InitGameManager {

    private final VampireSMPPlugin plugin;

    // ── Chest-GUI state (only active while a player is in the selection GUI) ──
    private static final int PLAYERS_PER_PAGE = 45;
    private static final int INVENTORY_SIZE   = 54;
    private final Map<UUID, WizardState> guiWizardStates     = new HashMap<>();
    private final Map<UUID, Boolean>     guiRefreshInProgress = new HashMap<>();

    public InitGameManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Per-wizard state
    // =========================================================================

    private static final class WizardState {
        String           vampireMode           = "random";
        int              minVampires           = 1;
        int              maxVampires           = 3;
        int              desiredVampireCount   = 1;
        final Set<UUID>  selectedVampires      = new HashSet<>();
        boolean          preSelectionDone      = false;
        int              currentPage           = 0;
        boolean          distributeKits        = true;
        String           sessionMode      = "oneshot";
        int              seriesHalf       = 60;
        int              seriesBrk        = 15;
        int              seriesBld        = 45;
        int              seriesInitialBuild = 15;
        int              seriesPreBreak     = 60;
        int              seriesPostBreak    = 60;
        int              seriesFinalBuild   = 15;
        int              breakMins        = 0;
        int              breakDur         = 15;
        boolean          breakLoop        = false;
        // Oneshot break behaviour: "resume" = continue session as-is, "restart" = end then
        // start a fresh session (rolls tomes) when the break ends.
        String           breakMode        = "resume";
        final Map<String, Integer> roleCounts = new LinkedHashMap<>();
    }

    // =========================================================================
    // Public entry points
    // =========================================================================

    public void startInitialization(Player admin) {
        openWarning(admin);
    }

    public void cancelInitialization(Player admin) {
        guiWizardStates.remove(admin.getUniqueId());
        admin.sendMessage("§cGame initialization cancelled.");
    }

    // =========================================================================
    // Screen 1: Warning
    // =========================================================================

    private void openWarning(Player p) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Warning", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "WARNING: You are about to start a new game.\n\n" +
                    "This will:\n" +
                    "  • Reset all player tags and inventories\n" +
                    "  • Neutralize all beacons\n" +
                    "  • Reset the session\n" +
                    "  • Teleport all online players\n" +
                    "  • Assign new vampires"
                ))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Proceed", NamedTextColor.GREEN),
                    Component.text("Open the setup wizard."),
                    150,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> showVampireMode(p, new WizardState())),
                        ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(Component.text("Initialization cancelled.", NamedTextColor.RED))),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 2: Vampire Mode
    // =========================================================================

    private void showVampireMode(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Vampire Assignment", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "How would you like to assign starting vampires?"
                ))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Random", NamedTextColor.YELLOW),
                    Component.text("Randomly select vampires from online players."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.vampireMode = "random";
                        showVampireCount(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Selected", NamedTextColor.AQUA),
                    Component.text("Pick players from a chest GUI."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.vampireMode = "selected";
                        showVampireCountForSelected(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(Component.text("Initialization cancelled.", NamedTextColor.RED))),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 3a-selected: How many vampires to pre-select in the chest GUI
    // =========================================================================

    private void showVampireCountForSelected(Player p, WizardState state) {
        int online = Bukkit.getOnlinePlayers().size();
        int poolSize = plugin.getRoleManager().getVampirePool().size();
        String bodyText = "How many players should start as vampires?\n\n" +
            "§7Online players: §f" + online +
            "\n§7Opted in: §a" + poolSize;

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — How Many Vampires?", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(bodyText))))
                .inputs(List.of(
                    DialogInput.text("vamp_count", Component.text("Number of vampires"))
                        .initial(String.valueOf(Math.max(1, poolSize > 0 ? poolSize : 1)))
                        .maxLength(3)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Open Selection →"), null, 200,
                    DialogAction.customClick((view, aud) -> {
                        String raw = view.getText("vamp_count");
                        sync(() -> {
                            int v = parseIntSafe(raw, -1);
                            if (v < 1) {
                                p.sendMessage(Component.text("Must be at least 1.", NamedTextColor.RED));
                                showVampireCountForSelected(p, state);
                                return;
                            }
                            if (v > online) {
                                p.sendMessage(Component.text("Cannot exceed online player count (" + online + ").", NamedTextColor.RED));
                                showVampireCountForSelected(p, state);
                                return;
                            }
                            state.desiredVampireCount = v;
                            openPlayerSelectionGUI(p, state);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 3a: Random vampire count (min/max)
    // =========================================================================

    private void showVampireCount(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Vampire Count", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set the minimum and maximum number of vampires to randomly assign."
                ))))
                .inputs(List.of(
                    DialogInput.text("min_vamp", Component.text("Minimum vampires"))
                        .initial(String.valueOf(state.minVampires))
                        .maxLength(3)
                        .build(),
                    DialogInput.text("max_vamp", Component.text("Maximum vampires"))
                        .initial(String.valueOf(state.maxVampires))
                        .maxLength(3)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String rawMin = view.getText("min_vamp");
                        String rawMax = view.getText("max_vamp");
                        sync(() -> {
                            int mn = parseIntSafe(rawMin, -1);
                            int mx = parseIntSafe(rawMax, -1);
                            if (mn < 0) {
                                p.sendMessage(Component.text("Minimum must be 0 or more.", NamedTextColor.RED));
                                showVampireCount(p, state);
                                return;
                            }
                            if (mx < mn) {
                                p.sendMessage(Component.text("Maximum must be ≥ minimum.", NamedTextColor.RED));
                                showVampireCount(p, state);
                                return;
                            }
                            state.minVampires = mn;
                            state.maxVampires = mx;
                            showSessionMode(p, state);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Screen 3b: Chest GUI for manual player selection
    // =========================================================================

    private void openPlayerSelectionGUI(Player admin, WizardState state) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int playerCount = onlinePlayers.size();
        if (playerCount == 0) {
            admin.sendMessage("§cNo players are online to select.");
            return;
        }

        // Pre-select on first open only: opt-ins first, then random fill
        if (!state.preSelectionDone) {
            state.preSelectionDone = true;
            List<String> pool = plugin.getRoleManager().getVampirePool();
            List<Player> optIns = new ArrayList<>();
            List<Player> others = new ArrayList<>();
            for (Player pl : onlinePlayers) {
                if (pool.contains(pl.getName())) optIns.add(pl);
                else                             others.add(pl);
            }
            Collections.shuffle(optIns);
            Collections.shuffle(others);
            int needed = state.desiredVampireCount;
            for (Player pl : optIns) { if (needed <= 0) break; state.selectedVampires.add(pl.getUniqueId()); needed--; }
            for (Player pl : others) { if (needed <= 0) break; state.selectedVampires.add(pl.getUniqueId()); needed--; }
        }

        guiWizardStates.put(admin.getUniqueId(), state);

        int totalPages   = (int) Math.ceil(playerCount / (double) PLAYERS_PER_PAGE);
        int currentPage  = Math.min(state.currentPage, totalPages - 1);
        state.currentPage = currentPage;
        int startIndex   = currentPage * PLAYERS_PER_PAGE;
        int endIndex     = Math.min(startIndex + PLAYERS_PER_PAGE, playerCount);

        List<String> vampirePool = plugin.getRoleManager().getVampirePool();
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, "§4§lSelect Vampires");
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Player player     = onlinePlayers.get(i);
            boolean isVampire = state.selectedVampires.contains(player.getUniqueId());
            boolean isOptIn   = vampirePool.contains(player.getName());
            String nameColor  = isOptIn ? "§a" : "§c";
            ItemStack item    = new ItemStack(isVampire ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE);
            ItemMeta meta     = item.getItemMeta();
            meta.setDisplayName(nameColor + player.getName() + (isVampire ? " - Vampire" : " - Human"));
            meta.setLore(List.of(
                isOptIn ? "§aOpted in" : "§cDid not opt in",
                "§7Click to toggle"
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm    = prev.getItemMeta();
            pm.setDisplayName("§e« Previous Page");
            pm.setLore(List.of("§7Go to page " + currentPage));
            prev.setItemMeta(pm);
            inventory.setItem(45, prev);
        }

        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta im         = indicator.getItemMeta();
        im.setDisplayName("§fPage " + (currentPage + 1) + " of " + totalPages);
        im.setLore(List.of("§7" + playerCount + " players total",
                           "§7" + state.selectedVampires.size() + " selected as vampires"));
        indicator.setItemMeta(im);
        inventory.setItem(49, indicator);

        if (currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm    = next.getItemMeta();
            nm.setDisplayName("§eNext Page »");
            nm.setLore(List.of("§7Go to page " + (currentPage + 2)));
            next.setItemMeta(nm);
            inventory.setItem(50, next);
        }

        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cm       = confirm.getItemMeta();
        cm.setDisplayName("§a§lCONFIRM SELECTION");
        cm.setLore(List.of("§7Click to proceed with these selections",
                           "§7Selected: §e" + state.selectedVampires.size() + " vampires"));
        confirm.setItemMeta(cm);
        inventory.setItem(53, confirm);

        admin.openInventory(inventory);
        Bukkit.getScheduler().runTaskLater(plugin, () -> guiRefreshInProgress.remove(admin.getUniqueId()), 1L);
    }

    // ── Listener callbacks ────────────────────────────────────────────────────

    public boolean isPlayerSelectionGUI(String title) {
        return "§4§lSelect Vampires".equals(title);
    }

    public boolean isGUIRefreshInProgress(UUID adminId) {
        return guiRefreshInProgress.getOrDefault(adminId, false);
    }

    public void handlePageChange(Player admin, int delta) {
        WizardState state = guiWizardStates.get(admin.getUniqueId());
        if (state == null) return;
        state.currentPage += delta;
        guiRefreshInProgress.put(admin.getUniqueId(), true);
        openPlayerSelectionGUI(admin, state);
    }

    public void handlePlayerToggle(Player admin, String playerName) {
        WizardState state = guiWizardStates.get(admin.getUniqueId());
        if (state == null) return;
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) return;
        UUID targetId = target.getUniqueId();
        if (state.selectedVampires.contains(targetId)) state.selectedVampires.remove(targetId);
        else state.selectedVampires.add(targetId);
        guiRefreshInProgress.put(admin.getUniqueId(), true);
        openPlayerSelectionGUI(admin, state);
    }

    public void handleGUIConfirmation(Player admin) {
        WizardState state = guiWizardStates.remove(admin.getUniqueId());
        if (state == null) return;
        admin.closeInventory();
        showSessionMode(admin, state);
    }

    public void handleGUIClose(Player admin) {
        if (guiWizardStates.remove(admin.getUniqueId()) != null) {
            admin.sendMessage("§cSelection closed — initialization cancelled.");
        }
    }

    // =========================================================================
    // Screen 4: Session Mode
    // =========================================================================

    private void showSessionMode(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Session Mode", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Choose whether this is a standard single session or a two-half series."
                ))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Oneshot", NamedTextColor.GREEN),
                    Component.text("Standard single session with optional break schedule."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.sessionMode = "oneshot";
                        showRoles(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Series", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Automatic building → session → break → session → building timeline."),
                    150,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.sessionMode = "series";
                        showSeriesSchedule(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(Component.text("Initialization cancelled.", NamedTextColor.RED))),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Series screens
    // =========================================================================

    private void showSeriesSchedule(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Series Schedule", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Configure the automatic timeline. All values are whole minutes."
                ))))
                .inputs(List.of(
                    DialogInput.text("initial_build", Component.text("Beginning building time"))
                        .initial(String.valueOf(state.seriesInitialBuild)).maxLength(5).build(),
                    DialogInput.text("before_break", Component.text("Session time until break"))
                        .initial(String.valueOf(state.seriesPreBreak)).maxLength(5).build(),
                    DialogInput.text("break_time", Component.text("Break duration"))
                        .initial(String.valueOf(state.seriesBrk)).maxLength(5).build(),
                    DialogInput.text("after_break", Component.text("Session time after break"))
                        .initial(String.valueOf(state.seriesPostBreak)).maxLength(5).build(),
                    DialogInput.text("final_build", Component.text("Building time after session end"))
                        .initial(String.valueOf(state.seriesFinalBuild)).maxLength(5).build()
                )).build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →", NamedTextColor.GREEN),
                    Component.text("Continue to role selection."), 170,
                    DialogAction.customClick((view, aud) -> {
                        String[] raw = { view.getText("initial_build"), view.getText("before_break"),
                            view.getText("break_time"), view.getText("after_break"), view.getText("final_build") };
                        sync(() -> {
                            int[] values = new int[5];
                            for (int i = 0; i < raw.length; i++) values[i] = parseIntSafe(raw[i], -1);
                            for (int value : values) {
                                if (!pow.crimson2.gamestart.ScheduledSessionManager.validMinutes(value)) {
                                    p.sendMessage(Component.text("Every value must be from 1 to 10080 minutes.", NamedTextColor.RED));
                                    showSeriesSchedule(p, state);
                                    return;
                                }
                            }
                            state.seriesInitialBuild = values[0];
                            state.seriesPreBreak = values[1];
                            state.seriesBrk = values[2];
                            state.seriesPostBreak = values[3];
                            state.seriesFinalBuild = values[4];
                            showRoles(p, state);
                        });
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) -> sync(() ->
                        p.sendMessage(Component.text("Initialization cancelled.", NamedTextColor.RED))),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showSeriesHalf(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Half Length", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set how long each half of the series lasts."
                ))))
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
            .base(DialogBase.builder(Component.text("Init — Series Break", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set how long the break between halves lasts."
                ))))
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
            .base(DialogBase.builder(Component.text("Init — Build Phase", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set how long the building phase lasts before the session begins."
                ))))
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
    // Screen: Roles
    // =========================================================================

    private void showRoles(Player p, WizardState state) {
        RoleManager rm = plugin.getRoleManager();
        boolean vhOn   = rm.isRoleEnabled("vampire_hunter");
        boolean mdOn   = rm.isRoleEnabled("medic");
        boolean trOn   = rm.isRoleEnabled("tracker");

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Roles", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Select which special roles are active this session."
                ))))
                .inputs(List.of(
                    DialogInput.bool("vampire_hunter", Component.text("Vampire Hunter"))
                        .initial(vhOn).build(),
                    DialogInput.bool("medic", Component.text("Medic"))
                        .initial(mdOn).build(),
                    DialogInput.bool("tracker", Component.text("Tracker"))
                        .initial(trOn).build()
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
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Role Count: " + label, NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set how many players to assign as " + label + " this session."
                ))))
                .inputs(List.of(
                    DialogInput.numberRange("count",
                            Component.text("Number of " + label + "s to assign"),
                            0f, 10f)
                        .step(1f).initial(0f).build()
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

    private void afterRoleCounts(Player p, WizardState state) {
        showStarterKits(p, state);
    }

    // =========================================================================
    // Screen: Starter Kits
    // =========================================================================

    private void showStarterKits(Player p, WizardState state) {
        boolean kitsEnabled = plugin.getStarterKitManager() != null
                && plugin.getStarterKitManager().isEnabled();
        state.distributeKits = kitsEnabled;

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Starter Kits", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Should starter kits be distributed to all players when the game starts?\n\n" +
                    "§7Current kit setting: §f" + (kitsEnabled ? "§aEnabled" : "§cDisabled")
                ))))
                .inputs(List.of(
                    DialogInput.bool("give_kits", Component.text("Distribute starter kits"))
                        .initial(kitsEnabled)
                        .build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        Boolean give = view.getBoolean("give_kits");
                        sync(() -> {
                            state.distributeKits = give != null && give;
                            if ("series".equals(state.sessionMode)) showConfirm(p, state);
                            else                                    showBreakSchedule(p, state);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Oneshot break screens
    // =========================================================================

    private void showBreakSchedule(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Break Schedule", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Schedule a break during the session. Enter 0 in both fields to skip."
                ))))
                .inputs(List.of(
                    DialogInput.text("break_hrs", Component.text("Hours until first break"))
                        .initial("0").maxLength(3).build(),
                    DialogInput.text("break_mins_field", Component.text("Minutes until first break"))
                        .initial("0").maxLength(2).build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Continue →"), null, 150,
                    DialogAction.customClick((view, aud) -> {
                        String rawHrs  = view.getText("break_hrs");
                        String rawMins = view.getText("break_mins_field");
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
                            else           showConfirm(p, state);
                        });
                    }, ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    private void showBreakDuration(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — Break Duration", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set how long the break lasts."
                ))))
                .inputs(List.of(
                    DialogInput.text("break_dur", Component.text("Break duration (minutes)"))
                        .initial(String.valueOf(state.breakDur)).maxLength(4).build()
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
            .base(DialogBase.builder(Component.text("Init — Loop Breaks?", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Choose whether breaks repeat throughout the session."
                ))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Yes — Loop Breaks", NamedTextColor.GREEN),
                    Component.text("Break timer restarts after each break ends."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.breakLoop = true;
                        showBreakMode(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("No — Single Break", NamedTextColor.RED),
                    Component.text("Only one break will occur."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.breakLoop = false;
                        showBreakMode(p, state);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    /** Oneshot only: how the session behaves when each break ends. */
    private void showBreakMode(Player p, WizardState state) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Init — After the Break?", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Choose what happens when the break ends."
                ))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Resume as Normal", NamedTextColor.GREEN),
                    Component.text("Continue the same session (/pow admin resume)."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.breakMode = "resume";
                        showConfirm(p, state);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Restart for Tomes", NamedTextColor.GOLD),
                    Component.text("End then start a fresh session — rolls tomes (/pow admin end → start)."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(() -> {
                        state.breakMode = "restart";
                        showConfirm(p, state);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Confirm screen
    // =========================================================================

    private void showConfirm(Player p, WizardState state) {
        StringBuilder sb = new StringBuilder("§7Vampires: §f");
        if ("selected".equals(state.vampireMode)) {
            sb.append("Selected — ").append(state.selectedVampires.size()).append(" players");
        } else {
            sb.append("Random ").append(state.minVampires).append("-").append(state.maxVampires);
        }

        sb.append("\n§7Session: §f").append(state.sessionMode);

        if ("series".equals(state.sessionMode)) {
            sb.append("\n§7Initial building: §f").append(state.seriesInitialBuild).append(" min")
              .append("\n§7Session before break: §f").append(state.seriesPreBreak).append(" min")
              .append("\n§7Break: §f").append(state.seriesBrk).append(" min")
              .append("\n§7Session after break: §f").append(state.seriesPostBreak).append(" min")
              .append("\n§7Final building: §f").append(state.seriesFinalBuild).append(" min");
        } else {
            if (state.breakMins > 0) {
                int bh = state.breakMins / 60, bm = state.breakMins % 60;
                String bStr = (bh > 0 ? bh + "h " : "") + bm + "m";
                sb.append("\n§7Break: T+§f").append(bStr)
                  .append("§7, lasts §f").append(state.breakDur).append("§7 min")
                  .append("\n§7Loop: §f").append(state.breakLoop ? "Yes" : "No")
                  .append("\n§7After break: §f")
                  .append("restart".equals(state.breakMode) ? "Restart (rolls tomes)" : "Resume as normal");
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
            .base(DialogBase.builder(Component.text("Init — Confirm", NamedTextColor.RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Review your settings. Proceeding will reset the entire game state."
                ))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("✓ Initialize Game", NamedTextColor.GREEN),
                    summary, 200,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> executeAll(p, state)),
                        ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("✗ Cancel", NamedTextColor.RED), null, 100,
                    DialogAction.customClick((view, aud) ->
                        sync(() -> p.sendMessage(Component.text("Initialization cancelled.", NamedTextColor.RED))),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Execute
    // =========================================================================

    private void executeAll(Player admin, WizardState state) {
        admin.sendMessage("§6§l========================================");
        admin.sendMessage("§6§lINITIALIZING GAME...");
        admin.sendMessage("§6§l========================================");

        World world = plugin.getWorldManager().getActiveWorld();
        if (world == null) {
            admin.sendMessage("§cError: No active world found. Use §f/pow admin loadworld §cor ensure 'world' is loaded.");
            return;
        }
        if (plugin.getScheduledSessionManager() != null && plugin.getScheduledSessionManager().isActive()) {
            plugin.getScheduledSessionManager().cancel(admin);
        }
        admin.sendMessage("§7World: §e" + world.getName());

        admin.sendMessage("§7[1/9] Neutralizing beacons...");
        for (BeaconSite beacon : plugin.getBeaconManager().getAllBeacons()) {
            plugin.getBeaconManager().setBeaconNeutral(beacon.getName(), true);
            Location beaconLoc = beacon.getLocation();
            if (beaconLoc != null && beaconLoc.getWorld() != null) {
                beaconLoc.getBlock().setType(Material.BARRIER);
            }
        }
        if (plugin.getBeaconManager().getBeacon("castle") != null) {
            plugin.getBeaconManager().setBeaconDesecrated("castle");
            admin.sendMessage("§7  → Castle beacon set to desecrated");
        }

        admin.sendMessage("§7[2/9] Clearing beacon cooldowns...");
        plugin.getBeaconManager().clearAllBeaconCooldownsForNewSession();

        admin.sendMessage("§7[3/9] Resetting player data...");
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            for (String tag : new HashSet<>(player.getScoreboardTags())) {
                player.removeScoreboardTag(tag);
            }
            player.getInventory().clear();
        }

        admin.sendMessage("§7[3.5/9] Resetting scoreboard objectives...");
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Objective obj : new HashSet<>(mainScoreboard.getObjectives())) {
            if (obj.getName().startsWith("vsmp_")) {
                String name        = obj.getName();
                String displayName = obj.getDisplayName();
                String criteria    = obj.getCriteria();
                obj.unregister();
                mainScoreboard.registerNewObjective(name, criteria, displayName);
            }
        }
        for (Player player : onlinePlayers) {
            AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            for (AttributeModifier modifier : healthAttr.getModifiers()) {
                healthAttr.removeModifier(modifier);
            }
            healthAttr.setBaseValue(20.0);
            player.setHealth(20.0);
            player.setLevel(0);
            player.setExp(0.0F);
            player.setTotalExperience(0);
        }
        try {
            Objective deathObjective = mainScoreboard.getObjective("vsmp_death");
            if (deathObjective != null) {
                for (Player player : onlinePlayers) {
                    deathObjective.getScore(player.getName()).setScore(0);
                }
                admin.sendMessage("§7  → Reset death counts for all players");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reset death scoreboard: " + e.getMessage());
        }

        admin.sendMessage("§7[4/9] Priming new session and incrementing game ID...");
        plugin.getSessionManager().primeNewSession();
        plugin.getSessionManager().incrementGameID();

        admin.sendMessage("§7[4.5/9] Resetting game state flags...");
        plugin.getStateConfig().set("first_beacon_converted", false);
        plugin.getStateConfig().set("humans_own_all_beacons", false);
        plugin.getStateConfig().set("vampires_own_all_beacons", false);
        plugin.getStateConfig().set("one_human_left", false);
        plugin.getStateConfig().set("fourth_book_has_spawned", false);
        plugin.getStateConfig().set("fourth_book_spawn_enabled", false);
        plugin.saveStateConfig();

        admin.sendMessage("§7[4.6/9] Clearing sire mappings...");
        plugin.getSireManager().clearAllSireMappings();

        admin.sendMessage("§7[4.7/9] Stopping vampire tracking...");
        if (plugin.getVampireTrackingManager() != null) {
            plugin.getVampireTrackingManager().stopAllTracking();
        }

        admin.sendMessage("§7[4.8/9] Clearing permadeath preferences...");
        plugin.getPermadeathManager().clearAllPermadeathModes();

        admin.sendMessage("§7[4.85/9] Wiping thrall bonds...");
        if (plugin.getThrallManager() != null) {
            plugin.getThrallManager().wipeAllBonds();
        }

        admin.sendMessage("§7[5/9] Setting world time and border...");
        world.setFullTime(1L);
        world.getWorldBorder().setSize(900000.0);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule playersNetherPortalDefaultDelay 80");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule locatorBar false");

        admin.sendMessage("§7[6/9] Applying saturation effect...");
        for (Player player : onlinePlayers) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 200, 9));
        }

        admin.sendMessage("§7[7/9] Teleporting players...");
        for (Player player : onlinePlayers) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                GameMode oldMode = player.getGameMode();
                player.setGameMode(GameMode.SURVIVAL);
                admin.sendMessage("§7  → Reset " + player.getName() + " from "
                    + oldMode.name().toLowerCase() + " to survival");
            }
            Location teleportLoc = getRandomTeleportLocation(world);
            if (teleportLoc != null) player.teleport(teleportLoc);
            else admin.sendMessage("§cWarning: Could not find valid teleport location for " + player.getName());
        }

        admin.sendMessage("§7[8/9] Assigning vampires...");
        List<Player> playersToConvert = new ArrayList<>();
        if ("selected".equals(state.vampireMode)) {
            for (Player player : onlinePlayers) {
                if (state.selectedVampires.contains(player.getUniqueId())) {
                    playersToConvert.add(player);
                }
            }
        } else {
            int vampireCount = ThreadLocalRandom.current()
                    .nextInt(state.minVampires, state.maxVampires + 1);
            List<Player> available = new ArrayList<>(onlinePlayers);
            Collections.shuffle(available);
            vampireCount = Math.min(vampireCount, available.size());
            playersToConvert = available.subList(0, vampireCount);
        }

        Set<UUID> vampireIds = new HashSet<>();
        for (Player player : playersToConvert) {
            plugin.getVampireManager().setPlayerAsVampire(player, 1);
            vampireIds.add(player.getUniqueId());
            player.setExp(0.5F);
            player.sendTitle("§4§lVampire", "", 10, 100, 20);
            player.sendMessage("");
            player.sendMessage("§4§l========================================");
            player.sendMessage("§cYou are a creature of the night, and it is time to feed.");
            player.sendMessage("");
            player.sendMessage(
                "§7What to do: Turn other humans by 'killing' them when no one is looking. As a level 1 vampire, there are very few ways you can be found out, but still be cautious. You cannot help turn beacons, eating food is bad but stomachable for now, only attack during the night. Press \"k\" to customize your vampire ability keybinds."
            );
            player.sendMessage("§4§l========================================");
            player.sendMessage("");
        }
        admin.sendMessage("§7  → Converted " + playersToConvert.size() + " players to vampires");

        for (Player player : onlinePlayers) {
            if (!vampireIds.contains(player.getUniqueId())) {
                player.addScoreboardTag("human");
                player.sendTitle("§e§lHuman", "", 10, 100, 20);
                player.sendMessage("");
                player.sendMessage("§e§l========================================");
                player.sendMessage("§7Welcome to " + plugin.getConfigManager().getOakhurstName()
                    + ". Survive, consecrate beacons, find tomes, and above all: Fear the night.");
                player.sendMessage("§e§l========================================");
                player.sendMessage("");
            }
        }

        if ("series".equals(state.sessionMode)) {
            admin.sendMessage("§7[9/11] Preparing scheduled building phase...");
        } else {
            admin.sendMessage("§7[9/11] Starting session...");
            plugin.getSessionManager().startSession();
        }

        admin.sendMessage("§7[10/11] Distributing tomes to chests...");
        if (plugin.getTomeDistributionManager().getTomeLocations().isEmpty()) {
            admin.sendMessage("§e  → No tome chest locations configured, skipping tome distribution");
        } else {
            plugin.getTomeDistributionManager().triggerDistribution();
            admin.sendMessage("§7  → Tomes distributed to "
                + plugin.getTomeDistributionManager().getTomeLocations().size() + " chest locations");
        }

        admin.sendMessage("§7[11/11] Clearing potion effects...");
        for (Player player : onlinePlayers) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        }

        plugin.getVampireTurningManager().enableAllVampireTurning();

        // Distribute starter kits
        if (state.distributeKits && plugin.getStarterKitManager() != null) {
            admin.sendMessage("§7Distributing starter kits...");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kitall");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kitstart");
        }

        // Assign roles
        if (!state.roleCounts.isEmpty()) {
            admin.sendMessage("§7Selecting roles...");
            plugin.getRoleManager().startAllRoles(admin, state.roleCounts);
        }

        // Setup timers
        GameStartManager gsm = plugin.getGameStartManager();
        if ("series".equals(state.sessionMode)) {
            plugin.getScheduledSessionManager().start(
                state.seriesInitialBuild,
                state.seriesPreBreak,
                state.seriesBrk,
                state.seriesPostBreak,
                state.seriesFinalBuild
            );
        } else if (state.breakMins > 0) {
            gsm.setupBreakOnly(state.breakMins, state.breakDur, state.breakLoop, state.breakMode);
        }

        admin.sendMessage("");
        admin.sendMessage("§a§l========================================");
        admin.sendMessage("§a§lGAME INITIALIZED SUCCESSFULLY.");
        admin.sendMessage("§a§l========================================");
        admin.sendMessage("§7Players: §e" + onlinePlayers.size());
        admin.sendMessage("§7Vampires: §c" + playersToConvert.size());
        admin.sendMessage("§7Humans: §a" + (onlinePlayers.size() - playersToConvert.size()));
        admin.sendMessage("§a§l========================================");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Location getRandomTeleportLocation(World world) {
        int maxAttempts = 50;
        ConfigManager config  = plugin.getConfigManager();
        double townCenterX    = config.getOakhurstTownCenterX();
        double townCenterZ    = config.getOakhurstTownCenterZ();
        double teleportRadius = config.getOakhurstTeleportRadius();
        double minX = config.getOakhurstMinX();
        double maxX = config.getOakhurstMaxX();
        double minZ = config.getOakhurstMinZ();
        double maxZ = config.getOakhurstMaxZ();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle    = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
            double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * teleportRadius;
            double x        = townCenterX + distance * Math.cos(angle);
            double z        = townCenterZ + distance * Math.sin(angle);
            if (x < minX + 50.0 || x > maxX - 50.0 || z < minZ + 50.0 || z > maxZ - 50.0) continue;
            Location loc = new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);
            if (loc.getY() > 0.0 && loc.getY() < world.getMaxHeight()) return loc;
        }
        return new Location(world, townCenterX,
            world.getHighestBlockYAt((int) townCenterX, (int) townCenterZ) + 1, townCenterZ);
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
