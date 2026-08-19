package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.abilities.tome.TomeAbility;
import pow.crimson2.managers.TomeManager;
import pow.crimson2.managers.VampireManager;

public class TomeAbilityCommand implements CommandExecutor, TabCompleter {
   private final VampireSMPPlugin plugin;
   private final TomeManager tomeManager;
   private final VampireManager vampireManager;

   public TomeAbilityCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.tomeManager = plugin.getTomeManager();
      this.vampireManager = plugin.getVampireManager();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("§cOnly players can use tome abilities.");
         return true;
      } else if (!this.vampireManager.isHuman(player) && !this.plugin.getConfigManager().isAllowUseAfterTurned()) {
         // When the feature is on we let the request through; TomeManager.useAbility does the
         // per-ability allow-list check and refuses anything not on it.
         player.sendMessage("§cOnly humans can use tome abilities.");
         return true;
      } else if (args.length == 0) {
         this.sendUsage(player);
         return true;
      } else {
         String subCommand = args[0].toLowerCase();
         if (subCommand.equals("list")) {
            return this.handleListCommand(player);
         }
         return this.handleAbilityUse(player, subCommand, java.util.Arrays.copyOfRange(args, 1, args.length));
      }
   }

   private boolean handleListCommand(Player player) {
      Set<String> playerAbilities = this.tomeManager.getPlayerAbilities(player);
      if (playerAbilities.isEmpty()) {
         player.sendMessage("§7You have not learned any tome abilities yet.");
         player.sendMessage("§7Find ancient tomes scattered throughout the world to learn new abilities.");
         return true;
      }

      player.sendMessage("§6§l=== YOUR TOME ABILITIES ===");

      for (String abilityName : playerAbilities) {
         TomeAbility ability = this.tomeManager.getAbility(abilityName);
         player.sendMessage("§e" + abilityName);
         if (ability != null) {
            String[] descriptionLines = ability.getDescriptionLines();

            for (String line : descriptionLines) {
               player.sendMessage("§7  " + line);
            }
         } else {
            player.sendMessage("§7  No description available");
         }

         player.sendMessage("§8  Use: /pow tome " + abilityName.toLowerCase());
         player.sendMessage("");
      }

      player.sendMessage("§7Total abilities: §e" + playerAbilities.size());
      return true;
   }

   private boolean handleAbilityUse(Player player, String abilityName, String[] args) {
      this.tomeManager.useAbility(player, abilityName, args);
      return true;
   }

   private void sendUsage(Player player) {
      player.sendMessage("§6§l=== TOME ABILITIES ===");
      player.sendMessage("§e/pow tome list §7- Show your available abilities");
      player.sendMessage("§e/pow tome <ability> §7- Use a specific ability");
      player.sendMessage("");
      player.sendMessage("§7Find ancient tomes in the world to learn new abilities.");
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player)) {
         return new ArrayList<>();
      } else {
         List<String> completions = new ArrayList<>();
         if (args.length == 1) {
            completions.add("list");
            Set<String> playerAbilities = this.tomeManager.getPlayerAbilities(player);
            completions.addAll(playerAbilities);
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
         } else if (args.length == 2 && args[0].equalsIgnoreCase("scrying")) {
            // Scrying takes a target player; suggest who they could reach for.
            String input = args[1].toLowerCase();
            for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
               if (!other.equals(player) && other.getName().toLowerCase().startsWith(input)) {
                  completions.add(other.getName());
               }
            }
         } else if (args.length == 2 && args[0].equalsIgnoreCase("fading")) {
            // Fading takes an optional opacity; offer a few sensible stops.
            String input = args[1].toLowerCase();
            for (String step : new String[]{"0", "25", "50", "75", "100"}) {
               if (step.startsWith(input)) {
                  completions.add(step);
               }
            }
         }

         return completions;
      }
   }
}
