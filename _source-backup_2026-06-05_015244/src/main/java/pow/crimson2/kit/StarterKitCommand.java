package pow.crimson2.kit;

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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.OfflinePlayer;
import pow.crimson2.VampireSMPPlugin;

import java.util.*;

/**
 * Handles all starter-kit commands and provides a Dialog-based GUI for
 * /starterkit (edit).
 *
 * Commands:
 *   /kitstart               — enable auto-kit on first join   (starterkit.admin)
 *   /kitstop                — disable auto-kit                (starterkit.admin)
 *   /kitall                 — give kit to all online          (starterkit.admin)
 *   /starterkit [edit]      — open settings dialog or give kit(starterkit.use)
 *   /starterkitgive <player>— give kit to player              (starterkit.admin)
 *   /foodkitadd <player>    — mark player as food-kit received(starterkit.admin)
 *   /foodkitremove <player> — remove food-kit mark            (starterkit.admin)
 *   /foodkitgive <player>   — give food kit now               (starterkit.admin)
 *
 * No InventoryClickEvent listener needed — all GUI uses native dialogs.
 */
@SuppressWarnings("UnstableApiUsage")
public class StarterKitCommand implements CommandExecutor, TabCompleter, Listener {

    private final VampireSMPPlugin plugin;
    private final StarterKitManager skm;

    public StarterKitCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.skm    = plugin.getStarterKitManager();
    }

    // =========================================================================
    // CommandExecutor
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {

            case "kitstart": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                skm.setEnabled(true);
                String msg = "§aStarter kits are now enabled for new players.";
                if (skm.broadcastKitStart()) Bukkit.broadcastMessage(msg);
                else sender.sendMessage(msg);
                return true;
            }

            case "kitstop": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                skm.setEnabled(false);
                String msg = "§cStarter kits have been disabled.";
                if (skm.broadcastKitStop()) Bukkit.broadcastMessage(msg);
                else sender.sendMessage(msg);
                return true;
            }

            case "kitall": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                for (Player p : Bukkit.getOnlinePlayers()) skm.giveStarterKit(p);
                String msg = "§eEveryone online has received the §6Starter Kit§e.";
                if (skm.broadcastKitAll()) Bukkit.broadcastMessage(msg);
                else sender.sendMessage(msg);
                return true;
            }

            case "starterkit": {
                if (!sender.hasPermission("starterkit.use")) { sender.sendMessage("§cNo permission."); return true; }
                if (!(sender instanceof Player)) { sender.sendMessage("§cPlayer only."); return true; }
                Player p = (Player) sender;
                if (args.length > 0 && args[0].equalsIgnoreCase("edit")) {
                    openMainMenu(p);
                } else {
                    skm.giveStarterKit(p);
                }
                return true;
            }

            case "starterkitgive": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length == 0) { sender.sendMessage("§cUsage: /starterkitgive <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
                skm.giveStarterKit(target);
                sender.sendMessage("§aGave the §6Starter Kit §ato " + target.getName() + ".");
                if (skm.broadcastKitGive())
                    Bukkit.broadcastMessage("§e" + target.getName() + " has received the §6Starter Kit§e.");
                return true;
            }

            case "foodkitadd": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length == 0) { sender.sendMessage("§cUsage: /foodkitadd <player>"); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
                skm.addFoodKit(op.getUniqueId());
                sender.sendMessage("§a" + args[0] + " now has the food kit.");
                return true;
            }

            case "foodkitremove": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length == 0) { sender.sendMessage("§cUsage: /foodkitremove <player>"); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
                skm.removeFoodKit(op.getUniqueId());
                sender.sendMessage("§c" + args[0] + " no longer has the food kit.");
                return true;
            }

            case "foodkitgive": {
                if (!sender.hasPermission("starterkit.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length == 0) { sender.sendMessage("§cUsage: /foodkitgive <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
                skm.giveFoodKit(target);
                sender.sendMessage("§eGave the food kit to " + target.getName() + ".");
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // First-join auto-kit
    // =========================================================================

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPlayedBefore()) return;
        if (!skm.isEnabled()) return;
        int    delay = skm.joinKitDelay();
        Player p     = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) skm.giveStarterKit(p);
        }, delay);
    }

    // =========================================================================
    // Main menu dialog
    // =========================================================================

    /** Open the kit settings main dialog.  Called from PlayerSetupManager too. */
    public void openMainMenu(Player p) {
        UUID uuid = p.getUniqueId();

        // Build the input list dynamically (only server-enabled categories)
        List<DialogInput> inputs = new ArrayList<>();
        addBoolInput(inputs, uuid, "tools",    "Tools (Sword, Pickaxe, Shovel, Axe)");
        addBoolInput(inputs, uuid, "food",     "Food (Cooked Beef, Raw Beef)");
        addBoolInput(inputs, uuid, "bread",    "Bread");
        addBoolInput(inputs, uuid, "torches",  "Torches");
        addBoolInput(inputs, uuid, "book",     "Book (Writable Book)");
        addBoolInput(inputs, uuid, "armor",    "Armor (Leather Set)");
        addBoolInput(inputs, uuid, "vampexp",  "Vampire XP (XP Bottles)");

        // Buttons: Builder Pack sub-menu, Character (if applicable), Save & Close
        List<ActionButton> buttons = new ArrayList<>();

        if (serverCatEnabled("builder")) {
            boolean bOn = skm.isCatEnabled(uuid, "builder");
            buttons.add(ActionButton.create(
                Component.text("Builder Pack  " + onOff(bOn)),
                Component.text("Configure builder-pack details."),
                200,
                DialogAction.customClick((view, aud) ->
                    sync(() -> openBuilderMenu(p)), ClickCallback.Options.builder().build())));
        }

        if (skm.addPlayerSpecific() && StarterKitManager.hasCharacters(p)) {
            String ch = skm.getCharacter(uuid);
            buttons.add(ActionButton.create(
                Component.text("Character: " + ("none".equals(ch) ? "None" : ch)),
                Component.text("Choose which character you're playing."),
                200,
                DialogAction.customClick((view, aud) ->
                    sync(() -> openCharacterMenu(p)), ClickCallback.Options.builder().build())));
        }

        buttons.add(ActionButton.create(
            Component.text("Save & Close", NamedTextColor.GREEN), null, 150,
            DialogAction.customClick((view, aud) -> {
                // Read all boolean inputs
                Boolean tools   = view.getBoolean("tools");
                Boolean food    = view.getBoolean("food");
                Boolean bread   = view.getBoolean("bread");
                Boolean torches = view.getBoolean("torches");
                Boolean book    = view.getBoolean("book");
                Boolean armor   = view.getBoolean("armor");
                Boolean vampexp = view.getBoolean("vampexp");
                sync(() -> {
                    applyCat(uuid, "tools",    tools);
                    applyCat(uuid, "food",     food);
                    applyCat(uuid, "bread",    bread);
                    applyCat(uuid, "torches",  torches);
                    applyCat(uuid, "book",     book);
                    applyCat(uuid, "armor",    armor);
                    applyCat(uuid, "vampexp",  vampexp);
                    // Notify PlayerSetupManager if wizard is active
                    if (plugin.getPlayerSetupManager() != null
                            && plugin.getPlayerSetupManager().isActive(p.getName())) {
                        plugin.getPlayerSetupManager().onKitMenuClosed(p);
                    } else {
                        p.sendMessage(Component.text("Kit settings saved.", NamedTextColor.GREEN));
                    }
                });
            }, ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Starter Kit Settings", NamedTextColor.YELLOW))
                .body(List.of(DialogBody.plainMessage(Component.text("Toggle which item categories are included in your starter kit."))))
                .inputs(inputs)
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Builder Pack dialog
    // =========================================================================

    private void openBuilderMenu(Player p) {
        UUID   uuid      = p.getUniqueId();
        boolean on       = skm.isCatEnabled(uuid, "builder");
        String  log      = StarterKitManager.logDisplayName(skm.getLogPref(uuid));
        int     total    = skm.getStoneTotal(uuid);
        int     maxStacks= skm.builderMaxStacks();

        // Stone inputs (0-maxStacks each)
        List<DialogInput> inputs = new ArrayList<>();
        for (String type : StarterKitManager.STONE_TYPES) {
            int cur = skm.getStoneStacks(uuid, type);
            inputs.add(
                DialogInput.numberRange(type,
                        Component.text(StarterKitManager.stoneDisplayName(type) + " stacks"),
                        0f, (float) maxStacks)
                    .step(1f)
                    .initial((float) cur)
                    .build()
            );
        }

        List<ActionButton> buttons = new ArrayList<>();

        // Toggle enable/disable
        buttons.add(ActionButton.create(
            on ? Component.text("✓ Enabled — click to disable", NamedTextColor.GREEN)
               : Component.text("✗ Disabled — click to enable", NamedTextColor.RED),
            null, 220,
            DialogAction.customClick((view, aud) -> sync(() -> {
                skm.toggleCat(uuid, "builder");
                openBuilderMenu(p);
            }), ClickCallback.Options.builder().build())));

        // Choose log type sub-screen
        buttons.add(ActionButton.create(
            Component.text("Log: " + log + " ▸"),
            Component.text("Choose which log type you receive."),
            200,
            DialogAction.customClick((view, aud) -> sync(() -> openBuilderLogs(p)), ClickCallback.Options.builder().build())));

        // Save stone counts + back
        buttons.add(ActionButton.create(
            Component.text("Save Stone Mix"), null, 150,
            DialogAction.customClick((view, aud) -> {
                Map<String, Float> vals = new LinkedHashMap<>();
                for (String type : StarterKitManager.STONE_TYPES) {
                    vals.put(type, view.getFloat(type));
                }
                sync(() -> {
                    int newTotal = 0;
                    for (Float v : vals.values()) newTotal += (v != null ? Math.round(v) : 0);
                    if (newTotal > maxStacks) {
                        p.sendMessage(Component.text(
                            "Total stone (" + newTotal + ") exceeds the " + maxStacks + "-stack limit.",
                            NamedTextColor.RED));
                    } else {
                        for (Map.Entry<String, Float> e : vals.entrySet()) {
                            int stacks = e.getValue() != null ? Math.round(e.getValue()) : 0;
                            skm.setStoneStacks(uuid, e.getKey(), stacks);
                        }
                        p.sendMessage(Component.text("Stone mix saved.", NamedTextColor.GREEN));
                    }
                    openBuilderMenu(p);
                });
            }, ClickCallback.Options.builder().build())));

        buttons.add(ActionButton.create(
            Component.text("← Back"), null, 100,
            DialogAction.customClick((view, aud) -> sync(() -> openMainMenu(p)), ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Builder Pack  — " + total + "/" + maxStacks + " stacks",
                        NamedTextColor.YELLOW))
                .body(List.of(DialogBody.plainMessage(Component.text("Configure your builder pack contents and stone mix."))))
                .inputs(inputs)
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Log picker dialog
    // =========================================================================

    private void openBuilderLogs(Player p) {
        UUID   uuid   = p.getUniqueId();
        String curKey = skm.getLogPref(uuid);

        List<ActionButton> buttons = new ArrayList<>();
        for (String key : StarterKitManager.LOG_MATERIALS.keySet()) {
            String name  = StarterKitManager.logDisplayName(key);
            String check = key.equals(curKey) ? " ✓" : "";
            buttons.add(ActionButton.create(
                Component.text(name + check,
                    key.equals(curKey) ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                null, 150,
                DialogAction.customClick((view, aud) -> sync(() -> {
                    skm.setLogPref(uuid, key);
                    openBuilderLogs(p);
                }), ClickCallback.Options.builder().build())));
        }
        buttons.add(ActionButton.create(
            Component.text("← Back"), null, 100,
            DialogAction.customClick((view, aud) -> sync(() -> openBuilderMenu(p)), ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Choose Log Type", NamedTextColor.YELLOW))
                .body(List.of(DialogBody.plainMessage(Component.text("Choose which log type to include in your builder pack."))))
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Character picker dialog
    // =========================================================================

    private void openCharacterMenu(Player p) {
        UUID   uuid = p.getUniqueId();
        String cur  = skm.getCharacter(uuid);

        List<ActionButton> buttons = new ArrayList<>();

        // "None" option
        buttons.add(ActionButton.create(
            Component.text("None (no character kit)" + ("none".equals(cur) ? " ✓" : ""),
                "none".equals(cur) ? NamedTextColor.GREEN : NamedTextColor.GRAY),
            null, 200,
            DialogAction.customClick((view, aud) -> sync(() -> {
                skm.setCharacter(uuid, "none");
                openCharacterMenu(p);
            }), ClickCallback.Options.builder().build())));

        for (String ch : StarterKitManager.getCharactersFor(p)) {
            String check = ch.equals(cur) ? " ✓" : "";
            buttons.add(ActionButton.create(
                Component.text(ch + check,
                    ch.equals(cur) ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                null, 150,
                DialogAction.customClick((view, aud) -> sync(() -> {
                    skm.setCharacter(uuid, ch);
                    openCharacterMenu(p);
                }), ClickCallback.Options.builder().build())));
        }

        buttons.add(ActionButton.create(
            Component.text("← Back"), null, 100,
            DialogAction.customClick((view, aud) -> sync(() -> openMainMenu(p)), ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(
                    Component.text("Choose Your Character", NamedTextColor.YELLOW))
                .body(List.of(DialogBody.plainMessage(Component.text("Select the character you are playing this session."))))
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Add a BooleanDialogInput for a category IF the server has that category enabled. */
    private void addBoolInput(List<DialogInput> list, UUID uuid, String cat, String label) {
        if (!serverCatEnabled(cat)) return;
        list.add(
            DialogInput.bool(cat, Component.text(label))
                .initial(skm.isCatEnabled(uuid, cat))
                .build()
        );
    }

    /** Apply a Boolean read from dialog back to the kit manager. */
    private void applyCat(UUID uuid, String cat, Boolean value) {
        if (!serverCatEnabled(cat) || value == null) return;
        boolean current = skm.isCatEnabled(uuid, cat);
        if (current != value) skm.toggleCat(uuid, cat);
    }

    private boolean serverCatEnabled(String cat) {
        switch (cat) {
            case "tools":   return skm.addTools();
            case "food":    return skm.addFood();
            case "bread":   return skm.addBread();
            case "torches": return skm.addTorches();
            case "book":    return skm.addBook();
            case "armor":   return skm.addArmor();
            case "builder": return skm.addBuilder();
            case "vampexp": return skm.addVampireExp();
            default:        return false;
        }
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static String onOff(boolean on) { return on ? "[ON]" : "[OFF]"; }

    // =========================================================================
    // TabCompleter
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("starterkit") && args.length == 1)
            return Collections.singletonList("edit");
        if (cmd.getName().equalsIgnoreCase("starterkitgive") && args.length == 1) {
            List<String> out = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            return out;
        }
        return Collections.emptyList();
    }
}
