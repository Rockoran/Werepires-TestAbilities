package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.TurnLockManager;
import pow.crimson2.managers.TurnLockManager.Species;

/**
 * Admin front-end for {@link TurnLockManager}.
 *
 * <pre>
 *   /pow admin canturn     &lt;player&gt; &lt;vampire|werewolf&gt; &lt;allow|deny|status&gt;
 *   /pow admin canbeturned &lt;player&gt; &lt;vampire|werewolf&gt; &lt;allow|deny|status&gt;
 *   /pow admin turnlocks   &lt;player&gt;
 * </pre>
 */
public class TurnLockCommand {

   private final VampireSMPPlugin plugin;

   public TurnLockCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   /** @param turnSide true for "can this player turn others", false for "can they be turned". */
   public boolean handle(CommandSender sender, String[] args, boolean turnSide) {
      String cmd = turnSide ? "canturn" : "canbeturned";
      if (args.length < 2) {
         sender.sendMessage("§cUsage: §e/pow admin " + cmd + " <player> <vampire|werewolf> [allow|deny|status]");
         return true;
      }

      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         sender.sendMessage("§cPlayer '" + args[0] + "' is not online. Turn locks are stored as scoreboard tags and can only be changed while the player is online.");
         return true;
      }

      Species species = Species.fromString(args[1]);
      if (species == null) {
         sender.sendMessage("§cUnknown species '" + args[1] + "'. Use §evampire §cor §ewerewolf§c.");
         return true;
      }

      TurnLockManager locks = this.plugin.getTurnLockManager();
      String action = args.length >= 3 ? args[2].toLowerCase() : "status";

      switch (action) {
         case "allow":
         case "on":
         case "unlock": {
            this.setLock(locks, target, species, turnSide, true);
            sender.sendMessage("§a" + target.getName() + " may now "
               + (turnSide ? "turn others into " + species.getLabel() + "s." : "be turned into a " + species.getLabel() + "."));
            this.notifyTarget(sender, target, species, turnSide, true);
            return true;
         }
         case "deny":
         case "off":
         case "lock": {
            this.setLock(locks, target, species, turnSide, false);
            sender.sendMessage("§c" + target.getName() + " can no longer "
               + (turnSide ? "turn others into " + species.getLabel() + "s." : "be turned into a " + species.getLabel() + "."));
            this.notifyTarget(sender, target, species, turnSide, false);
            return true;
         }
         case "status": {
            boolean allowed = turnSide ? locks.canTurn(target, species) : locks.canBeTurned(target, species);
            String tag = turnSide ? species.getTurnTag() : species.getTurnedTag();
            sender.sendMessage("§6" + target.getName() + " §7— " + (turnSide ? "can turn" : "can be turned") + " (" + species.getLabel() + "): "
               + (allowed ? "§aallowed" : "§cdenied"));
            sender.sendMessage("§7Tag: §f" + tag + " §7" + (allowed ? "(absent)" : "(present)"));
            return true;
         }
         default:
            sender.sendMessage("§cUnknown action '" + action + "'. Use §eallow§c, §edeny§c or §estatus§c.");
            return true;
      }
   }

   /** Full four-lock readout for one player. */
   public boolean handleStatus(CommandSender sender, String[] args) {
      if (args.length < 1) {
         sender.sendMessage("§cUsage: §e/pow admin turnlocks <player>");
         return true;
      }

      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
         return true;
      }

      TurnLockManager locks = this.plugin.getTurnLockManager();
      sender.sendMessage("§6§l=== Turn locks: " + target.getName() + " ===");
      for (Species species : Species.values()) {
         boolean canTurn = locks.canTurn(target, species);
         boolean canBeTurned = locks.canBeTurned(target, species);
         sender.sendMessage("§e" + species.getLabel() + "§7 — can turn others: " + (canTurn ? "§ayes" : "§cno")
            + "§7, can be turned: " + (canBeTurned ? "§ayes" : "§cno"));
      }
      return true;
   }

   private void setLock(TurnLockManager locks, Player target, Species species, boolean turnSide, boolean allowed) {
      if (turnSide) {
         locks.setCanTurn(target, species, allowed);
      } else {
         locks.setCanBeTurned(target, species, allowed);
      }
   }

   private void notifyTarget(CommandSender sender, Player target, Species species, boolean turnSide, boolean allowed) {
      if (target.equals(sender)) return;
      if (turnSide) {
         target.sendMessage(allowed
            ? "§7You feel the curse stir within you once more — you can pass on " + species.getLabel() + "ism again."
            : "§7Something in your bite has gone quiet. You cannot pass on " + species.getLabel() + "ism.");
      } else {
         target.sendMessage(allowed
            ? "§7You feel strangely vulnerable to the " + species.getLabel() + "'s curse."
            : "§7A quiet resilience settles over you. The " + species.getLabel() + "'s curse cannot take hold.");
      }
   }

   public List<String> tabComplete(String[] args) {
      if (args.length == 1) {
         List<String> names = new ArrayList<>();
         for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
         }
         return filter(names, args[0]);
      }
      if (args.length == 2) {
         return filter(Arrays.asList("vampire", "werewolf"), args[1]);
      }
      if (args.length == 3) {
         return filter(Arrays.asList("allow", "deny", "status"), args[2]);
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
