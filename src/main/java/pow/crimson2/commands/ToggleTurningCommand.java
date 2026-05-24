package pow.crimson2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

public class ToggleTurningCommand implements CommandExecutor {
   private final VampireSMPPlugin plugin;

   public ToggleTurningCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!this.plugin.getVampireManager().isVampire(player)) {
            player.sendMessage("§cOnly vampires can use this command.");
            return true;
         }

         boolean newState = this.plugin.getVampireTurningManager().toggleTurning(player);
         if (newState) {
            player.sendMessage("§aVampire turning enabled. You will now turn humans into vampires when you kill them.");
         } else {
            player.sendMessage("§cVampire turning disabled. Humans will die normally when you kill them.");
         }

         return true;
      } else {
         sender.sendMessage("§cThis command can only be used by players.");
         return true;
      }
   }
}
