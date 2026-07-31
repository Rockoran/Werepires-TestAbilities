package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.abilities.WerewolfAbility;
import pow.crimson2.managers.WerewolfAbilityManager;

public class WerewolfAbilityCommand implements CommandExecutor, TabCompleter {

   private final VampireSMPPlugin plugin;
   private final WerewolfAbilityManager abilityManager;

   public WerewolfAbilityCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.abilityManager = plugin.getWerewolfAbilityManager();
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("§cOnly players can use werewolf abilities.");
         return true;
      }

      if (args.length == 0) {
         this.sendHelp(player);
         return true;
      }

      String sub = args[0].toLowerCase();

      if (sub.equals("list")) {
         this.listAbilities(player);
         return true;
      }

      if (sub.equals("all")) {
         this.listAllAbilities(player);
         return true;
      }

      if (!this.plugin.getVampireManager().isWerewolf(player)) {
         player.sendMessage("§cOnly werewolves can use werewolf abilities.");
         return true;
      }

      if (player.getGameMode() == GameMode.SPECTATOR) {
         player.sendMessage("§cYou cannot use abilities in spectator mode.");
         return true;
      }

      this.abilityManager.useAbility(player, sub);
      return true;
   }

   private void sendHelp(Player player) {
      player.sendMessage("§6§l=== WEREWOLF ABILITIES ===");
      player.sendMessage("§e/pow wability list §7- Show your available abilities");
      player.sendMessage("§e/pow wability all §7- Show all abilities (including locked)");
      player.sendMessage("§e/pow wability <ability> §7- Use an ability");
      player.sendMessage("§7Example: §e/pow wability feralcharge");
   }

   private void listAbilities(Player player) {
      if (!this.plugin.getVampireManager().isWerewolf(player)) {
         player.sendMessage("§cYou must be a werewolf to see werewolf abilities.");
         return;
      }

      List<WerewolfAbility> available = this.abilityManager.getAvailableAbilities(player);
      int stage = this.plugin.getVampireManager().getWerewolfStage(player);

      if (available.isEmpty()) {
         player.sendMessage("§cNo abilities available at Stage " + stage + ".");
         player.sendMessage("§7Use §e/pow wability all §7to see what you can unlock.");
      } else {
         player.sendMessage("§6§l=== YOUR WEREWOLF ABILITIES ===");
         player.sendMessage("§7Your Stage: §e" + stage);
         player.sendMessage("");
         for (WerewolfAbility ability : available) {
            this.displayAbility(player, ability, true);
         }
         player.sendMessage("§7Use §e/pow wability all §7to see locked abilities.");
      }
   }

   private void listAllAbilities(Player player) {
      if (!this.plugin.getVampireManager().isWerewolf(player)) {
         player.sendMessage("§cYou must be a werewolf to see werewolf abilities.");
         return;
      }

      int stage = this.plugin.getVampireManager().getWerewolfStage(player);
      player.sendMessage("§6§l=== ALL WEREWOLF ABILITIES ===");
      player.sendMessage("§7Your Stage: §e" + stage);
      player.sendMessage("");

      for (WerewolfAbility ability : this.abilityManager.getAllAbilities()) {
         boolean canUse = ability.canUse(player, this.plugin.getVampireManager());
         this.displayAbility(player, ability, canUse);
      }
   }

   private void displayAbility(Player player, WerewolfAbility ability, boolean canUse) {
      String nameColor = canUse ? "§e" : "§8";
      String status;

      if (canUse) {
         if (this.abilityManager.isOnCooldown(player, ability.getName())) {
            long remaining = this.abilityManager.getRemainingCooldown(player, ability.getName());
            status = " §c(Cooldown: " + WerewolfAbilityManager.formatTime(remaining) + ")";
         } else {
            status = " §a(Ready)";
         }
      } else {
         status = " §c(Locked - Requires Stage " + ability.getMinimumStage() + ")";
      }

      player.sendMessage(nameColor + ability.getDisplayName() + status);
      player.sendMessage("  §7" + ability.getDescription());
      player.sendMessage(
         "  §7Required Stage: §e" + ability.getMinimumStage()
         + " §7| Cooldown: §e" + WerewolfAbilityManager.formatTime(ability.getCooldownSeconds(this.plugin))
      );
      if (canUse) {
         player.sendMessage("  §7Usage: §e/pow wability " + ability.getName());
      }
      player.sendMessage("");
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player)) {
         return new ArrayList<>();
      }

      List<String> completions = new ArrayList<>();
      if (args.length == 1) {
         completions.add("list");
         completions.add("all");
         if (this.plugin.getVampireManager().isWerewolf(player)) {
            completions.addAll(
               this.abilityManager.getAvailableAbilities(player)
                  .stream()
                  .map(WerewolfAbility::getName)
                  .collect(Collectors.toList())
            );
         }
      }

      if (args.length > 0) {
         String input = args[args.length - 1].toLowerCase();
         completions.removeIf(s -> !s.toLowerCase().startsWith(input));
      }

      return completions;
   }
}
