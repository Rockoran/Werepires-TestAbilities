package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.ConfigManager;
import pow.crimson2.managers.TomeManager;

/**
 * {@code /pow ability} — which abilities keep working after their owner is turned.
 *
 * <p>Reading is open to everyone; changing the allow-list needs {@code vampiresmp.admin}.
 *
 * <pre>
 *   /pow ability                      - what survives turning
 *   /pow ability check [player]       - what that player can still use right now
 *   /pow ability allow &lt;ability|*&gt;    - admin: let it survive turning
 *   /pow ability deny  &lt;ability|*&gt;    - admin: stop it surviving
 *   /pow ability enable|disable       - admin: master switch
 * </pre>
 */
public class AbilityCommand implements CommandExecutor {

   private final VampireSMPPlugin plugin;

   public AbilityCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String action = args.length == 0 ? "list" : args[0].toLowerCase();
      String[] rest = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);

      switch (action) {
         case "list":
         case "status":
            return this.handleList(sender);
         case "check":
            return this.handleCheck(sender, rest);
         case "allow":
         case "deny":
            return this.handleSet(sender, rest, action.equals("allow"));
         case "enable":
         case "disable":
            return this.handleToggle(sender, action.equals("enable"));
         case "help":
            this.sendHelp(sender);
            return true;
         default:
            sender.sendMessage("§cUnknown option '" + action + "'.");
            this.sendHelp(sender);
            return true;
      }
   }

   private boolean handleList(CommandSender sender) {
      ConfigManager config = this.plugin.getConfigManager();
      sender.sendMessage("§6§l=== Abilities after turning ===");

      if (!config.isAllowUseAfterTurned()) {
         sender.sendMessage("§7Disabled — turning strips every tome ability.");
         if (sender.hasPermission("vampiresmp.admin")) {
            sender.sendMessage("§7Turn it on with §e/pow ability enable");
         }
         return true;
      }

      if (config.isEveryAbilityAllowedAfterTurned()) {
         sender.sendMessage("§aAll abilities §7survive turning (allow-list is §f*§7).");
      } else {
         List<String> allowed = config.getAbilitiesAllowedAfterTurned();
         if (allowed.isEmpty()) {
            sender.sendMessage("§7Enabled, but the allow-list is empty — nothing survives turning yet.");
         } else {
            sender.sendMessage("§7These survive turning:");
            for (String name : allowed) {
               sender.sendMessage("§a  " + name);
            }
         }
      }

      sender.sendMessage("§7Tome books after turning: " + (config.isAllowTomeItemsAfterTurned() ? "§acan be held" : "§cstill burn"));
      sender.sendMessage("§7Tags kept through the turn: " + (config.isKeepAbilitiesOnTurn() ? "§ayes" : "§cno"));
      return true;
   }

   private boolean handleCheck(CommandSender sender, String[] args) {
      Player target;
      if (args.length >= 1) {
         target = Bukkit.getPlayerExact(args[0]);
         if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
         }
      } else if (sender instanceof Player self) {
         target = self;
      } else {
         sender.sendMessage("§cUsage: §e/pow ability check <player>");
         return true;
      }

      TomeManager tomes = this.plugin.getTomeManager();
      Set<String> owned = tomes.getPlayerAbilities(target);
      boolean human = this.plugin.getVampireManager().isHuman(target);

      sender.sendMessage("§6§l=== " + target.getName() + " ===");
      sender.sendMessage("§7Currently: " + (human ? "§ahuman" : "§cturned"));

      if (owned.isEmpty()) {
         sender.sendMessage("§7They hold no tome abilities.");
         return true;
      }

      for (String name : owned) {
         boolean usable = human || this.plugin.getConfigManager().isAbilityAllowedAfterTurned(name);
         sender.sendMessage((usable ? "§a  ✔ " : "§c  ✘ ") + name + (usable ? "" : " §7(lost to the turn)"));
      }
      return true;
   }

   private boolean handleSet(CommandSender sender, String[] args, boolean allow) {
      if (!sender.hasPermission("vampiresmp.admin")) {
         sender.sendMessage("§cYou don't have permission to change the ability allow-list.");
         return true;
      }
      if (args.length < 1) {
         sender.sendMessage("§cUsage: §e/pow ability " + (allow ? "allow" : "deny") + " <ability|*>");
         return true;
      }

      String name = args[0];
      ConfigManager config = this.plugin.getConfigManager();

      if (!"*".equals(name)) {
         String resolved = this.resolveAbilityName(name);
         if (resolved == null) {
            sender.sendMessage("§cUnknown ability '" + name + "'. Use §e/pow ability list §cor tab-complete.");
            return true;
         }
         name = resolved;
      }

      boolean already = config.getAbilitiesAllowedAfterTurned().stream().anyMatch(e -> e.equalsIgnoreCase(args[0]));
      if (allow == already) {
         sender.sendMessage("§7'" + name + "' is already " + (allow ? "allowed" : "denied") + " after turning.");
         return true;
      }

      config.setAbilityAllowedAfterTurned(name, allow);
      sender.sendMessage((allow ? "§a" : "§c") + "'" + name + "' " + (allow ? "now survives" : "no longer survives") + " turning.");

      if (allow && !config.isAllowUseAfterTurned()) {
         sender.sendMessage("§7Note: the feature is still disabled overall. Run §e/pow ability enable§7.");
      }
      if (!allow) {
         sender.sendMessage("§7Players who already kept this ability keep the tag until they are re-turned.");
      }
      return true;
   }

   private boolean handleToggle(CommandSender sender, boolean enable) {
      if (!sender.hasPermission("vampiresmp.admin")) {
         sender.sendMessage("§cYou don't have permission to change this.");
         return true;
      }
      this.plugin.getConfigManager().setAllowUseAfterTurned(enable);
      sender.sendMessage(enable
         ? "§aAbilities on the allow-list will now survive turning."
         : "§cTurning will strip every tome ability again.");
      return true;
   }

   /** Match a user-typed name against the registered abilities, case-insensitively. */
   private String resolveAbilityName(String input) {
      for (String known : this.plugin.getTomeManager().getAllAbilityNames()) {
         if (known.equalsIgnoreCase(input)) {
            return known;
         }
      }
      return null;
   }

   private void sendHelp(CommandSender sender) {
      sender.sendMessage("§6§l=== /pow ability ===");
      sender.sendMessage("§e/pow ability §7- What survives being turned");
      sender.sendMessage("§e/pow ability check [player] §7- What they can still use");
      if (sender.hasPermission("vampiresmp.admin")) {
         sender.sendMessage("§e/pow ability allow <ability|*> §7- Let it survive turning");
         sender.sendMessage("§e/pow ability deny <ability|*> §7- Stop it surviving");
         sender.sendMessage("§e/pow ability enable|disable §7- Master switch");
      }
   }

   public List<String> tabComplete(String[] args) {
      if (args.length == 1) {
         List<String> options = new ArrayList<>(Arrays.asList("list", "check"));
         options.addAll(Arrays.asList("allow", "deny", "enable", "disable"));
         return filter(options, args[0]);
      }

      if (args.length == 2) {
         String sub = args[0].toLowerCase();
         if (sub.equals("allow") || sub.equals("deny")) {
            List<String> names = new ArrayList<>();
            names.add("*");
            names.addAll(new TreeSet<>(this.plugin.getTomeManager().getAllAbilityNames()));
            return filter(names, args[1]);
         }
         if (sub.equals("check")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
               names.add(p.getName());
            }
            return filter(names, args[1]);
         }
      }
      return new ArrayList<>();
   }

   private static List<String> filter(List<String> options, String prefix) {
      List<String> out = new ArrayList<>();
      for (String option : options) {
         if (option.toLowerCase().startsWith(prefix.toLowerCase())) {
            out.add(option);
         }
      }
      return out;
   }
}
