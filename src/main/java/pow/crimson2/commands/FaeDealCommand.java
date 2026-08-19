package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.FaeManager;

/**
 * The fae-only command section.
 *
 * <pre>
 *   /pow faedeal vampire &lt;player&gt; &lt;stage&gt;   - bind a human into a locked vampire stage
 *   /pow faedeal bargains                    - list your active bargains
 *   /pow faedeal release &lt;player&gt;            - break one of your own bargains
 *   /pow faedeal deaths|hearts &lt;player&gt; ...  - same as the admin versions
 *   /pow faedeal canturn|canbeturned|turnlocks &lt;player&gt; ...
 * </pre>
 *
 * Access is the {@code fae} tag, not a permission node — admins also pass.
 */
public class FaeDealCommand implements CommandExecutor {

   private final VampireSMPPlugin plugin;
   private final TurnLockCommand turnLockCommand;
   private final DeathCounterCommand deathCounterCommand;

   public FaeDealCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.turnLockCommand = new TurnLockCommand(plugin);
      this.deathCounterCommand = new DeathCounterCommand(plugin);
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!this.plugin.getConfigManager().isFaeEnabled()) {
         sender.sendMessage("§cThe fae system is disabled.");
         return true;
      }

      if (!(sender instanceof Player fae)) {
         sender.sendMessage("§cOnly players can strike fae bargains.");
         return true;
      }

      if (!this.plugin.getFaeManager().isFae(fae) && !sender.hasPermission("vampiresmp.admin")) {
         sender.sendMessage("§cYou are not fae.");
         return true;
      }

      if (args.length == 0) {
         this.sendHelp(fae);
         return true;
      }

      String sub = args[0].toLowerCase();
      String[] rest = Arrays.copyOfRange(args, 1, args.length);

      switch (sub) {
         case "vampire":
            return this.handleVampireDeal(fae, rest);
         case "bargains":
         case "list":
            return this.handleBargains(fae);
         case "release":
         case "free":
            return this.handleRelease(fae, rest);
         // Each of these returns true even when the gate rejects, so Bukkit does not fall back
         // to printing /pow's usage line on top of our own refusal message.
         case "deaths":
            if (this.gated(fae, rest)) this.deathCounterCommand.handle(fae, rest, false);
            return true;
         case "hearts":
            if (this.gated(fae, rest)) this.deathCounterCommand.handle(fae, rest, true);
            return true;
         case "canturn":
            if (this.gated(fae, rest)) this.turnLockCommand.handle(fae, rest, true);
            return true;
         case "canbeturned":
            if (this.gated(fae, rest)) this.turnLockCommand.handle(fae, rest, false);
            return true;
         case "turnlocks":
            if (this.gated(fae, rest)) this.turnLockCommand.handleStatus(fae, rest);
            return true;
         case "help":
            this.sendHelp(fae);
            return true;
         default:
            fae.sendMessage("§cUnknown faedeal subcommand: " + sub);
            this.sendHelp(fae);
            return true;
      }
   }

   /**
    * Enforce {@code fae.subcommands-target-any-player}. Returns true when the fae may act on the
    * named target; otherwise complains and returns false so the caller short-circuits.
    */
   private boolean gated(Player fae, String[] rest) {
      if (this.plugin.getConfigManager().isFaeAdminSubcommandsAnyPlayer()) {
         return true;
      }
      if (fae.hasPermission("vampiresmp.admin")) {
         return true;
      }
      if (rest.length < 1) {
         return true; // let the delegate print its own usage message
      }

      Player target = Bukkit.getPlayerExact(rest[0]);
      if (target == null) {
         return true; // delegate reports the unknown player
      }

      FaeManager.Deal deal = this.plugin.getFaeManager().getDeal(target);
      if (deal != null && fae.getUniqueId().equals(deal.getFaeId())) {
         return true;
      }

      fae.sendMessage("§cYou may only use that on someone bound by your own bargain.");
      return false;
   }

   private boolean handleVampireDeal(Player fae, String[] args) {
      if (args.length < 2) {
         fae.sendMessage("§cUsage: §e/pow faedeal vampire <player> <stage> [true|false]");
         fae.sendMessage("§7Allowed stages: §f" + this.stageList());
         return true;
      }

      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         fae.sendMessage("§cPlayer '" + args[0] + "' is not online.");
         return true;
      }

      int stage;
      try {
         stage = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
         fae.sendMessage("§c'" + args[1] + "' is not a stage number. Allowed: " + this.stageList());
         return true;
      }

      boolean breakOnFaePermadeath = false;
      if (args.length >= 3) {
         if (!"true".equalsIgnoreCase(args[2]) && !"false".equalsIgnoreCase(args[2])) {
            fae.sendMessage("§cThe final argument must be true or false.");
            return true;
         }
         breakOnFaePermadeath = Boolean.parseBoolean(args[2]);
      }

      String error = this.plugin.getFaeManager().createDeal(fae, target, stage, breakOnFaePermadeath);
      if (error != null) {
         fae.sendMessage("§c" + error);
         return true;
      }

      fae.sendMessage("§d§lTHE BARGAIN IS STRUCK");
      fae.sendMessage("§7" + target.getName() + " is bound to you as a stage §f" + stage + "§7 vampire.");
      fae.sendMessage("§7They cannot rise or fall from it. " + (breakOnFaePermadeath
         ? "Your permanent death will unravel the bargain."
         : "The bargain will survive your death."));
      fae.playSound(fae.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.0F, 0.6F);

      target.sendMessage("§d§lA BARGAIN IS STRUCK");
      target.sendMessage("§7" + fae.getName() + " has bound you as a stage §f" + stage + "§7 vampire.");
      target.sendMessage("§7You cannot grow stronger, nor weaker. A stake will end you.");
      target.sendMessage(breakOnFaePermadeath
         ? "§7Should your fae permanently fall, so does this bargain."
         : "§7This bargain is not bound to your fae's life.");
      target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.0F, 0.4F);

      this.plugin.logInfo("FAE DEAL: " + fae.getName() + " -> " + target.getName() + " @ stage " + stage
         + " (break on fae permadeath: " + breakOnFaePermadeath + ")");
      return true;
   }

   private boolean handleBargains(Player fae) {
      List<UUID> bound = this.plugin.getFaeManager().getDealTargets(fae.getUniqueId());
      fae.sendMessage("§6§l=== Your bargains (" + bound.size() + ") ===");
      if (bound.isEmpty()) {
         fae.sendMessage("§7You hold none.");
         return true;
      }
      for (UUID id : bound) {
         Player online = Bukkit.getPlayer(id);
         OfflinePlayer op = Bukkit.getOfflinePlayer(id);
         String name = op.getName() != null ? op.getName() : id.toString();
         if (online != null) {
            FaeManager.Deal deal = this.plugin.getFaeManager().getDeal(online);
            fae.sendMessage("§7 - §a" + name + " §7(stage §f" + this.plugin.getFaeManager().getLockedStage(online)
               + "§7, life-bound: §f" + (deal != null && deal.breakOnFaePermadeath) + "§7)");
         } else {
            fae.sendMessage("§7 - §8" + name + " §7(offline)");
         }
      }
      return true;
   }

   private boolean handleRelease(Player fae, String[] args) {
      if (args.length < 1) {
         fae.sendMessage("§cUsage: §e/pow faedeal release <player>");
         return true;
      }

      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         fae.sendMessage("§cPlayer '" + args[0] + "' is not online.");
         return true;
      }

      FaeManager.Deal deal = this.plugin.getFaeManager().getDeal(target);
      if (deal == null) {
         fae.sendMessage("§c" + target.getName() + " is not bound by any bargain.");
         return true;
      }
      if (!fae.getUniqueId().equals(deal.getFaeId()) && !fae.hasPermission("vampiresmp.admin")) {
         fae.sendMessage("§cThat bargain is not yours to break. It belongs to " + deal.faeName + ".");
         return true;
      }

      this.plugin.getFaeManager().breakDeal(target, fae.getName() + " has released you.");
      fae.sendMessage("§dYou release " + target.getName() + " from your bargain.");
      return true;
   }

   private String stageList() {
      return this.plugin.getConfigManager().getFaeAllowedStages().stream()
         .map(String::valueOf)
         .collect(Collectors.joining(", "));
   }

   private void sendHelp(Player fae) {
      fae.sendMessage("§d§l=== Fae Bargains ===");
      fae.sendMessage("§e/pow faedeal vampire <player> <stage> [true|false] §7- Bind at a stage; true makes your permadeath break it");
      fae.sendMessage("§e/pow faedeal bargains §7- List the bargains you hold");
      fae.sendMessage("§e/pow faedeal release <player> §7- Break one of your own bargains");
      fae.sendMessage("§e/pow faedeal deaths <player> <get|set|add|remove> [n] §7- Adjust their death counter");
      fae.sendMessage("§e/pow faedeal hearts <player> <get|give|take> [n] §7- The same, in hearts");
      fae.sendMessage("§e/pow faedeal canturn <player> <vampire|werewolf> <allow|deny|status>");
      fae.sendMessage("§e/pow faedeal canbeturned <player> <vampire|werewolf> <allow|deny|status>");
      fae.sendMessage("§e/pow faedeal turnlocks <player> §7- Show all four locks");
      if (!this.plugin.getConfigManager().isFaeAdminSubcommandsAnyPlayer()) {
         fae.sendMessage("§7You may only target players bound by your own bargains.");
      }
   }

   public List<String> tabComplete(String[] args) {
      if (args.length == 1) {
         return filter(Arrays.asList(
            "vampire", "bargains", "release", "deaths", "hearts",
            "canturn", "canbeturned", "turnlocks", "help"), args[0]);
      }

      String sub = args[0].toLowerCase();
      String[] rest = Arrays.copyOfRange(args, 1, args.length);

      switch (sub) {
         case "canturn":
         case "canbeturned":
            return this.turnLockCommand.tabComplete(rest);
         case "deaths":
            return this.deathCounterCommand.tabComplete(rest, false);
         case "hearts":
            return this.deathCounterCommand.tabComplete(rest, true);
         case "vampire":
            if (args.length == 2) return onlineNames(args[1]);
            if (args.length == 3) {
               return filter(this.plugin.getConfigManager().getFaeAllowedStages().stream()
                  .map(String::valueOf).collect(Collectors.toList()), args[2]);
            }
            if (args.length == 4) return filter(Arrays.asList("false", "true"), args[3]);
            return new ArrayList<>();
         case "release":
         case "turnlocks":
            if (args.length == 2) return onlineNames(args[1]);
            return new ArrayList<>();
         default:
            return new ArrayList<>();
      }
   }

   private static List<String> onlineNames(String prefix) {
      List<String> names = new ArrayList<>();
      for (Player p : Bukkit.getOnlinePlayers()) {
         names.add(p.getName());
      }
      return filter(names, prefix);
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
