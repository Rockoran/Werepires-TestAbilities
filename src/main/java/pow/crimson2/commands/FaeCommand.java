package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.FaeManager;

/**
 * Admin side of the fae system.
 *
 * <pre>
 *   /pow admin fae &lt;player&gt; [add|remove|status]
 *   /pow admin fae list
 * </pre>
 */
public class FaeCommand {

   private final VampireSMPPlugin plugin;

   public FaeCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean handle(CommandSender sender, String[] args) {
      if (args.length < 1) {
         sender.sendMessage("§cUsage: §e/pow admin fae <player> [add|remove|status]");
         sender.sendMessage("§7       §e/pow admin fae list");
         return true;
      }

      FaeManager faes = this.plugin.getFaeManager();

      if (args[0].equalsIgnoreCase("list")) {
         return this.listFaes(sender, faes);
      }

      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         sender.sendMessage("§cPlayer '" + args[0] + "' is not online. The fae tag is a scoreboard tag and needs the player online.");
         return true;
      }

      String action = args.length >= 2 ? args[1].toLowerCase() : "add";

      switch (action) {
         case "add":
         case "grant":
         case "on":
            if (faes.isFae(target)) {
               sender.sendMessage("§7" + target.getName() + " is already fae.");
               return true;
            }
            faes.setFae(target, true);
            sender.sendMessage("§d" + target.getName() + " is now fae. They can use §e/pow faedeal§d.");
            target.sendMessage("§d§lYOU ARE FAE");
            target.sendMessage("§7Something older than the curse has taken an interest in you.");
            target.sendMessage("§7Use §e/pow faedeal §7to see what you can offer.");
            return true;

         case "remove":
         case "revoke":
         case "off": {
            if (!faes.isFae(target)) {
               sender.sendMessage("§7" + target.getName() + " is not fae.");
               return true;
            }
            List<UUID> bound = faes.getDealTargets(target.getUniqueId());
            faes.setFae(target, false);
            sender.sendMessage("§d" + target.getName() + " is no longer fae.");
            if (!bound.isEmpty()) {
               sender.sendMessage("§7They still hold §f" + bound.size() + "§7 active bargain(s). Removing the tag does not break them —");
               sender.sendMessage("§7use §e/pow admin fae " + target.getName() + " status§7 to review, or let their death do it.");
            }
            target.sendMessage("§7The old power withdraws its interest in you.");
            return true;
         }

         case "status": {
            boolean isFae = faes.isFae(target);
            sender.sendMessage("§6§l=== Fae: " + target.getName() + " ===");
            sender.sendMessage("§7Fae: " + (isFae ? "§dyes" : "§7no"));
            List<UUID> bound = faes.getDealTargets(target.getUniqueId());
            sender.sendMessage("§7Bargains held: §f" + bound.size());
            for (UUID id : bound) {
               OfflinePlayer op = Bukkit.getOfflinePlayer(id);
               Player online = Bukkit.getPlayer(id);
               int stage = online != null ? faes.getLockedStage(online) : -1;
               sender.sendMessage("§7  - §f" + (op.getName() != null ? op.getName() : id.toString())
                  + (stage > 0 ? " §7(stage " + stage + ")" : " §8(offline)"));
            }
            FaeManager.Deal own = faes.getDeal(target);
            if (own != null) {
               sender.sendMessage("§7Bound by: §f" + own.faeName + " §7at stage §f" + own.stage);
            }
            return true;
         }

         default:
            sender.sendMessage("§cUnknown action '" + action + "'. Use §eadd§c, §eremove§c or §estatus§c.");
            return true;
      }
   }

   private boolean listFaes(CommandSender sender, FaeManager faes) {
      java.util.Set<UUID> all = faes.getFaes();
      sender.sendMessage("§6§l=== Fae (" + all.size() + ") ===");
      if (all.isEmpty()) {
         sender.sendMessage("§7None.");
         return true;
      }
      for (UUID id : all) {
         OfflinePlayer op = Bukkit.getOfflinePlayer(id);
         String name = op.getName() != null ? op.getName() : id.toString();
         int bargains = faes.getDealTargets(id).size();
         boolean online = Bukkit.getPlayer(id) != null;
         sender.sendMessage("§7 - " + (online ? "§a" : "§8") + name + " §7— " + bargains + " bargain(s)");
      }
      return true;
   }

   public List<String> tabComplete(String[] args) {
      if (args.length == 1) {
         List<String> names = new ArrayList<>();
         names.add("list");
         for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
         }
         return filter(names, args[0]);
      }
      if (args.length == 2) {
         return filter(Arrays.asList("add", "remove", "status"), args[1]);
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
