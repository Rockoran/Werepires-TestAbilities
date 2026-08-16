package pow.crimson2.roles;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles all role-related commands:
 *   /role       — opt-in / opt-out for human roles
 *   /rolecfg    — enable/disable roles, set cooldowns (no-args opens chest GUI via RoleCfgGui)
 *   /rolestart  — randomly assign roles for the session
 *   /vampire    — vampire pool management
 *   /gameadmin  — game-admin list management
 *   /findvampires — Vampire Hunter ability
 */
public class RoleCommand implements CommandExecutor, TabCompleter {

    private final VampireSMPPlugin plugin;
    private final RoleManager      roleManager;
    private final RoleCfgGui       cfgGui;

    public RoleCommand(VampireSMPPlugin plugin) {
        this.plugin      = plugin;
        this.roleManager = plugin.getRoleManager();
        this.cfgGui      = new RoleCfgGui(plugin);
    }

    /** Accessor so VampireSMPPlugin can register the GUI listener. */
    public RoleCfgGui getCfgGui() { return cfgGui; }

    // =========================================================================
    // CommandExecutor
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "role":         handleRole(sender, args);         return true;
            case "rolecfg":      handleRolecfg(sender, args);      return true;
            case "rolestart":    handleRolestart(sender, args);     return true;
            case "vampire":      handleVampire(sender, args);       return true;
            case "gameadmin":    handleGameadmin(sender, args);     return true;
            case "findvampires": handleFindVampires(sender);        return true;
        }
        return false;
    }

    // =========================================================================
    // /role
    // =========================================================================

    private void handleRole(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /role <role> <optin|optout>");
            return;
        }

        String sub = args[0].toLowerCase();

        // ── Admin sub-commands ──────────────────────────────────────────────
        if (sub.equals("cooldownreset")) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /role cooldownreset <role> [<player>|@a]");
                return;
            }
            String role   = args[1].toLowerCase();
            String target = args.length >= 3 ? args[2] : null;

            List<Player> targets = new ArrayList<>();
            if ("@a".equals(target)) {
                targets.addAll(Bukkit.getOnlinePlayers());
            } else if (target != null) {
                Player tp = Bukkit.getPlayerExact(target);
                if (tp == null) { sender.sendMessage("§cPlayer not found: §f" + target); return; }
                targets.add(tp);
            } else if (sender instanceof Player) {
                targets.add((Player) sender);
            }

            for (Player tp : targets) {
                UUID uid = tp.getUniqueId();
                if ("vampire_hunter".equals(role)) {
                    roleManager.resetFvCooldown(uid);
                    sender.sendMessage("§aFind Vampires cooldown reset for §f" + tp.getName());
                    if (!tp.equals(sender))
                        tp.sendMessage("§aYour Find Vampires cooldown was reset by §f" + sender.getName());
                } else if ("tracker".equals(role)) {
                    roleManager.resetTrackerCooldown(uid);
                    sender.sendMessage("§aTracker cooldown reset for §f" + tp.getName());
                    if (!tp.equals(sender))
                        tp.sendMessage("§aYour tracker cooldown was reset by §f" + sender.getName());
                } else if ("medic".equals(role)) {
                    sender.sendMessage("§7Medic has no active cooldown to reset.");
                } else {
                    sender.sendMessage("§cUnknown role: §f" + role);
                }
            }
            return;
        }

        if (sub.equals("clearlist")) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
            if (args.length < 2) { sender.sendMessage("§cUsage: /role clearlist <role>"); return; }
            String role = args[1].toLowerCase();
            if (!RoleManager.KNOWN_ROLES.contains(role)) {
                sender.sendMessage("§cUnknown role: §f" + role); return;
            }
            int count = roleManager.getRolePool(role).size();
            roleManager.clearPool(role);
            sender.sendMessage("§cCleared §f" + role + " §cpool (§f" + count + " §cplayer(s) removed).");
            return;
        }

        if (sub.equals("removeplayer")) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /role removeplayer <role> <player>"); return;
            }
            String role   = args[1].toLowerCase();
            String pName  = args[2];
            if (!RoleManager.KNOWN_ROLES.contains(role)) {
                sender.sendMessage("§cUnknown role: §f" + role); return;
            }
            if (!roleManager.getRolePool(role).contains(pName)) {
                sender.sendMessage("§e" + pName + " is not in the §f" + role + " §epool."); return;
            }
            roleManager.removeFromPool(role, pName);
            sender.sendMessage("§c" + pName + " §7removed from the §f" + role + " §7pool.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equals(pName))
                    p.sendMessage("§cYou were removed from the §f" + role + " §cpool by §f" + sender.getName());
            }
            return;
        }

        if (sub.equals("sessionreset")) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (String role : RoleManager.KNOWN_ROLES) {
                    p.removeScoreboardTag(role);
                }
            }
            sender.sendMessage("§aAll role tags cleared from online players.");
            return;
        }

        // ── Player optin / optout ────────────────────────────────────────────
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command requires a player."); return;
        }
        Player player = (Player) sender;

        if (!RoleManager.KNOWN_ROLES.contains(sub)) {
            player.sendMessage("§cUnknown role. Available: §f" + String.join(", ", RoleManager.KNOWN_ROLES));
            return;
        }
        if (!roleManager.isRoleEnabled(sub)) {
            player.sendMessage("§cThat role is currently disabled."); return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /role " + sub + " <optin|optout>"); return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("optin")) {
            if (!roleManager.addToPool(sub, player.getName()))
                player.sendMessage("§eYou are already in the §f" + sub + " §epool.");
            else
                player.sendMessage("§aYou joined the §f" + sub + " §apool.");
        } else if (action.equals("optout")) {
            if (!roleManager.removeFromPool(sub, player.getName()))
                player.sendMessage("§eYou are not in the §f" + sub + " §epool.");
            else
                player.sendMessage("§cYou left the §f" + sub + " §cpool.");
        } else {
            player.sendMessage("§cUsage: /role " + sub + " <optin|optout>");
        }
    }

    // =========================================================================
    // /rolecfg
    // =========================================================================

    private void handleRolecfg(CommandSender sender, String[] args) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThe /rolecfg GUI requires a player. Use /rolecfg <enable|disable|cooldown> <value>.");
                return;
            }
            cfgGui.openMain((Player) sender);
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("enable") || sub.equals("disable")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /rolecfg " + sub + " <role>"); return;
            }
            String role = args[1].toLowerCase();
            if (!RoleManager.KNOWN_ROLES.contains(role)) {
                sender.sendMessage("§cUnknown role: §f" + role); return;
            }
            boolean enabled = sub.equals("enable");
            roleManager.setRoleEnabled(role, enabled);
            sender.sendMessage(enabled
                    ? "§aRole §f" + role + " §ahas been enabled."
                    : "§cRole §f" + role + " §chas been disabled.");
        } else if (sub.equals("cooldown")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /rolecfg cooldown <minutes>"); return;
            }
            try {
                int val = Integer.parseInt(args[1]);
                if (val < 1) throw new NumberFormatException();
                roleManager.setFvCooldownMinutes(val);
                sender.sendMessage("§aCooldown set to §f" + val + " §aminute(s).");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid number.");
            }
        } else {
            sender.sendMessage("§cUsage: /rolecfg <enable|disable|cooldown> <value>");
        }
    }

    // =========================================================================
    // /rolestart
    // =========================================================================

    private void handleRolestart(CommandSender sender, String[] args) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /rolestart <role|all>"); return;
        }

        String arg = args[0].toLowerCase();
        if ("all".equals(arg)) {
            Player senderPlayer = sender instanceof Player ? (Player) sender : null;
            if (senderPlayer == null) {
                // Console: pick a fake player or send to all ops
                roleManager.startAllRoles(null, null);
                return;
            }
            roleManager.startAllRoles(senderPlayer, null);
        } else {
            if (!RoleManager.KNOWN_ROLES.contains(arg)) {
                sender.sendMessage("§cUnknown role: §f" + arg); return;
            }
            Player sp = sender instanceof Player ? (Player) sender : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (sp == null) { sender.sendMessage("§cNo online player to send messages to."); return; }
            roleManager.startSingleRole(sp, arg);
        }
    }

    // =========================================================================
    // /vampire
    // =========================================================================

    private void handleVampire(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /vampire <optin|optout>");
            sender.sendMessage("§cOp only: /vampire <select <amount>|list|clearlist|removeplayer <player>>");
            return;
        }

        String sub = args[0].toLowerCase();

        if (!(sender instanceof Player) && !sub.equals("list") && !sub.equals("clearlist")
                && !sub.equals("select") && !sub.equals("removeplayer")) {
            sender.sendMessage("§cThis requires a player."); return;
        }

        switch (sub) {
            case "optin": {
                if (!(sender instanceof Player)) { sender.sendMessage("§cPlayer only."); return; }
                String name = ((Player) sender).getName();
                if (!roleManager.addToVampirePool(name))
                    sender.sendMessage("§eYou are already opted into the vampire pool.");
                else
                    sender.sendMessage("§5You opted into the vampire pool.");
                break;
            }
            case "optout": {
                if (!(sender instanceof Player)) { sender.sendMessage("§cPlayer only."); return; }
                String name = ((Player) sender).getName();
                if (!roleManager.removeFromVampirePool(name))
                    sender.sendMessage("§eYou are not in the vampire pool.");
                else
                    sender.sendMessage("§cYou opted out of the vampire pool.");
                break;
            }
            case "select": {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /vampire select <amount>"); return; }
                try {
                    int amount = Integer.parseInt(args[1]);
                    List<String> pool = new ArrayList<>(roleManager.getVampirePool());
                    if (pool.isEmpty()) {
                        sender.sendMessage("§cNo players are opted into the vampire pool."); return;
                    }
                    if (pool.size() < amount) {
                        sender.sendMessage("§cNot enough players in pool. Size: §f" + pool.size()); return;
                    }
                    List<String> selected = new ArrayList<>();
                    for (int i = 0; i < amount; i++) {
                        String pick = pool.remove(ThreadLocalRandom.current().nextInt(pool.size()));
                        selected.add(pick);
                    }
                    String msg = "§5§lVAMPIRE SELECTED §8| §f" + String.join(", ", selected)
                               + " §5have been chosen as vampires!";
                    roleManager.broadcastToAdmins(msg);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (selected.contains(p.getName())) p.sendMessage(msg);
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cAmount must be a number.");
                }
                break;
            }
            case "list": {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
                List<String> pool = roleManager.getVampirePool();
                if (pool.isEmpty()) { sender.sendMessage("§cNo players are opted in."); return; }
                sender.sendMessage("§7§m--------------------");
                sender.sendMessage("§5Vampire Pool §7(" + pool.size() + "): §f" + String.join(", ", pool));
                sender.sendMessage("§7§m--------------------");
                break;
            }
            case "clearlist": {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
                roleManager.clearVampirePool();
                sender.sendMessage("§cVampire pool cleared.");
                break;
            }
            case "removeplayer": {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /vampire removeplayer <player>"); return; }
                String pName = args[1];
                if (!roleManager.removeFromVampirePool(pName)) {
                    sender.sendMessage("§e" + pName + " is not in the vampire pool."); return;
                }
                sender.sendMessage("§c" + pName + " §7removed from the vampire pool.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equals(pName))
                        p.sendMessage("§cYou were removed from the vampire pool by §f" + sender.getName());
                }
                break;
            }
            default:
                sender.sendMessage("§cUsage: /vampire <optin|optout>");
        }
    }

    // =========================================================================
    // /gameadmin
    // =========================================================================

    private void handleGameadmin(CommandSender sender, String[] args) {
            if (!sender.hasPermission("vampiresmp.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /gameadmin <add|remove|list|clear>"); return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add": {
                if (args.length < 2) { sender.sendMessage("§cUsage: /gameadmin add <player>"); return; }
                if (!roleManager.addGameAdmin(args[1])) {
                    sender.sendMessage("§e" + args[1] + " is already a Game Admin."); return;
                }
                sender.sendMessage("§a" + args[1] + " has been added as a Game Admin.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equals(args[1]))
                        p.sendMessage("§aYou have been assigned as a Game Admin.");
                }
                break;
            }
            case "remove": {
                if (args.length < 2) { sender.sendMessage("§cUsage: /gameadmin remove <player>"); return; }
                if (!roleManager.removeGameAdmin(args[1])) {
                    sender.sendMessage("§e" + args[1] + " is not a Game Admin."); return;
                }
                sender.sendMessage("§c" + args[1] + " has been removed as a Game Admin.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equals(args[1]))
                        p.sendMessage("§cYou are no longer a Game Admin.");
                }
                break;
            }
            case "list": {
                List<String> admins = roleManager.getGameAdmins();
                if (admins.isEmpty()) {
                    sender.sendMessage("§7No Game Admins set. Falling back to all ops."); return;
                }
                sender.sendMessage("§7§m--------------------");
                sender.sendMessage("§6Game Admins §7(" + admins.size() + "): §f" + String.join(", ", admins));
                sender.sendMessage("§7§m--------------------");
                break;
            }
            case "clear": {
                roleManager.clearGameAdmins();
                sender.sendMessage("§cAll Game Admins cleared. Falling back to all ops.");
                break;
            }
            default:
                sender.sendMessage("§cUsage: /gameadmin <add|remove|list|clear>");
        }
    }

    // =========================================================================
    // /findvampires
    // =========================================================================

    private void handleFindVampires(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command requires a player."); return;
        }
        Player player = (Player) sender;
        if (!player.getScoreboardTags().contains("vampire_hunter")) {
            player.sendMessage("§cYou are not a Vampire Hunter!"); return;
        }
        roleManager.activateFindVampires(player);
    }

    // =========================================================================
    // TabCompleter
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        switch (cmd.getName().toLowerCase()) {
            case "role":
                if (args.length == 1) {
                    out.addAll(RoleManager.KNOWN_ROLES);
                    if (sender.hasPermission("vampiresmp.admin")) out.addAll(Arrays.asList("cooldownreset", "clearlist", "removeplayer", "sessionreset"));
                } else if (args.length == 2) {
                    String a1 = args[0].toLowerCase();
                    if (RoleManager.KNOWN_ROLES.contains(a1)) out.addAll(Arrays.asList("optin", "optout"));
                    else if (sender.hasPermission("vampiresmp.admin")) {
                        if (a1.equals("cooldownreset") || a1.equals("clearlist") || a1.equals("removeplayer"))
                            out.addAll(RoleManager.KNOWN_ROLES);
                    }
                } else if (args.length == 3 && sender.hasPermission("vampiresmp.admin")) {
                    if (args[0].equalsIgnoreCase("cooldownreset"))
                        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    else if (args[0].equalsIgnoreCase("removeplayer"))
                        out.addAll(roleManager.getRolePool(args[1].toLowerCase()));
                }
                break;
            case "rolecfg":
                if (args.length == 1) out.addAll(Arrays.asList("enable", "disable", "cooldown"));
                else if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")))
                    out.addAll(RoleManager.KNOWN_ROLES);
                break;
            case "rolestart":
                if (args.length == 1) {
                    out.add("all");
                    out.addAll(RoleManager.KNOWN_ROLES);
                }
                break;
            case "vampire":
                if (args.length == 1) out.addAll(Arrays.asList("optin", "optout", "select", "list", "clearlist", "removeplayer"));
                else if (args.length == 2 && args[0].equalsIgnoreCase("removeplayer"))
                    out.addAll(roleManager.getVampirePool());
                break;
            case "gameadmin":
                if (args.length == 1) out.addAll(Arrays.asList("add", "remove", "list", "clear"));
                else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove")))
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                break;
        }
        String partial = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(partial));
        return out;
    }
}
