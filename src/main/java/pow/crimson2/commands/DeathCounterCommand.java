package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pow.crimson2.VampireSMPPlugin;

/**
 * Admin control over the human death counter — and therefore over hearts.
 *
 * <p>Hearts are not stored anywhere: {@code BeaconMajorityManager.applyDeathPenalty} derives a
 * MAX_HEALTH modifier of {@code -(deaths * human-death-hp-penalty)}. Moving the counter is the
 * only way to move hearts, so "give hearts" and "remove a death" are the same operation and are
 * exposed here as two names for one code path.
 *
 * <pre>
 *   /pow admin deaths &lt;player&gt; &lt;get|set|add|remove&gt; [amount]
 *   /pow admin hearts &lt;player&gt; &lt;get|give|take&gt;       [amount]
 * </pre>
 */
public class DeathCounterCommand {

   private static final String OBJECTIVE = "vsmp_death";

   private final VampireSMPPlugin plugin;

   public DeathCounterCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   /** @param heartsMode true when invoked as "hearts", which inverts the sign of add/remove. */
   public boolean handle(CommandSender sender, String[] args, boolean heartsMode) {
      String cmd = heartsMode ? "hearts" : "deaths";
      if (args.length < 1) {
         sender.sendMessage("§cUsage: §e/pow admin " + cmd + " <player> "
            + (heartsMode ? "<get|give|take> [amount]" : "<get|set|add|remove> [amount]"));
         return true;
      }

      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
         return true;
      }

      String action = args.length >= 2 ? args[1].toLowerCase() : "get";
      int amount = 1;
      if (args.length >= 3) {
         try {
            amount = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            sender.sendMessage("§c'" + args[2] + "' is not a number.");
            return true;
         }
         if (amount < 0) {
            sender.sendMessage("§cAmount must be zero or greater.");
            return true;
         }
      }

      int current = this.getDeaths(target);
      int cap = this.plugin.getConfigManager().getHumanDeathScoreCap();
      Integer desired;

      switch (action) {
         case "get":
         case "status":
            this.report(sender, target, current);
            return true;
         case "set":
            if (heartsMode) {
               sender.sendMessage("§cUse §e/pow admin deaths <player> set <n>§c to set the counter directly.");
               return true;
            }
            if (args.length < 3) {
               sender.sendMessage("§cUsage: §e/pow admin deaths <player> set <amount>");
               return true;
            }
            desired = amount;
            break;
         case "add":
            if (heartsMode) {
               sender.sendMessage("§cUse §egive§c or §etake§c with the hearts command.");
               return true;
            }
            desired = current + amount;
            break;
         case "remove":
         case "subtract":
            if (heartsMode) {
               sender.sendMessage("§cUse §egive§c or §etake§c with the hearts command.");
               return true;
            }
            desired = current - amount;
            break;
         case "give":
            // Giving hearts back means healing wounds — the counter goes down.
            desired = current - amount;
            break;
         case "take":
            desired = current + amount;
            break;
         default:
            sender.sendMessage("§cUnknown action '" + action + "'.");
            return true;
      }

      int clamped = Math.max(0, Math.min(cap, desired));
      if (clamped != desired) {
         sender.sendMessage("§7(clamped " + desired + " to " + clamped + "; valid range is 0–" + cap
            + " from §fcombat.human-death-score-cap§7)");
      }

      if (clamped == current) {
         sender.sendMessage("§7" + target.getName() + "'s death counter is already " + current + " — nothing changed.");
         return true;
      }

      this.setDeaths(target, clamped);
      this.refreshHealth(target);

      int delta = clamped - current;
      double hpPerDeath = this.plugin.getConfigManager().getHumanDeathHpPenalty();
      double heartsDelta = -delta * hpPerDeath / 2.0;
      sender.sendMessage("§a" + target.getName() + "'s death counter: §f" + current + " §7→ §f" + clamped
         + " §7(" + (heartsDelta >= 0 ? "+" : "") + trim(heartsDelta) + " hearts)");

      if (!this.plugin.getVampireManager().isHuman(target)) {
         sender.sendMessage("§7Note: " + target.getName() + " is not human, so the heart penalty is not applied to them.");
      } else if (!this.plugin.getSessionManager().isSessionActive()) {
         sender.sendMessage("§7Note: no session is active, so hearts will not update until one starts.");
      }

      if (delta > 0) {
         target.sendMessage("§cYou feel your wounds deepen.");
      } else {
         target.sendMessage("§aYou feel some of your strength return.");
      }

      this.warnIfAboveThreshold(sender, target, clamped);
      return true;
   }

   private void report(CommandSender sender, Player target, int deaths) {
      double hpPerDeath = this.plugin.getConfigManager().getHumanDeathHpPenalty();
      int cap = this.plugin.getConfigManager().getHumanDeathScoreCap();
      int threshold = this.plugin.getConfigManager().getVampireKillPermadeathThreshold();
      sender.sendMessage("§6§l=== " + target.getName() + " ===");
      sender.sendMessage("§7Deaths: §f" + deaths + "§7 / " + cap);
      sender.sendMessage("§7Heart penalty: §f-" + trim(deaths * hpPerDeath / 2.0) + " hearts");
      sender.sendMessage("§7Permadeath threshold: §f" + threshold
         + "§7 — " + (deaths >= threshold ? "§cat or above; a vampire kill is permanent" : "§abelow"));
   }

   private void warnIfAboveThreshold(CommandSender sender, Player target, int deaths) {
      int threshold = this.plugin.getConfigManager().getVampireKillPermadeathThreshold();
      if (deaths >= threshold) {
         sender.sendMessage("§4Warning: " + target.getName() + " is at or above the permadeath threshold ("
            + threshold + "). The next vampire kill will be permanent.");
      }
   }

   private int getDeaths(Player player) {
      try {
         Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
         Objective objective = board.getObjective(OBJECTIVE);
         if (objective != null) {
            return objective.getScore(player.getName()).getScore();
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to read death count for " + player.getName() + ": " + e.getMessage());
      }
      return 0;
   }

   private void setDeaths(Player player, int value) {
      try {
         Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
         Objective objective = board.getObjective(OBJECTIVE);
         if (objective != null) {
            objective.getScore(player.getName()).setScore(value);
            this.plugin.logInfo("Admin set death score for " + player.getName() + " to " + value);
         } else {
            this.plugin.getLogger().warning("Death objective '" + OBJECTIVE + "' is missing; cannot set score.");
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to set death count for " + player.getName() + ": " + e.getMessage());
      }
   }

   /** Recompute the derived MAX_HEALTH modifier, same path StopTheBleeding uses. */
   private void refreshHealth(Player player) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (this.plugin.getBeaconMajorityManager() != null) {
            this.plugin.getBeaconMajorityManager().applyBonusesToPlayer(player);
            this.plugin.getBeaconMajorityManager().updateBeaconMajorityBonuses();
         }
      });
   }

   private static String trim(double value) {
      if (value == Math.floor(value)) {
         return String.valueOf((long) value);
      }
      return String.valueOf(value);
   }

   public List<String> tabComplete(String[] args, boolean heartsMode) {
      if (args.length == 1) {
         List<String> names = new ArrayList<>();
         for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
         }
         return filter(names, args[0]);
      }
      if (args.length == 2) {
         return filter(heartsMode
            ? Arrays.asList("get", "give", "take")
            : Arrays.asList("get", "set", "add", "remove"), args[1]);
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
