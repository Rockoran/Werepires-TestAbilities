package pow.crimson2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

public class ForcedCureReopenCommand implements CommandExecutor {
   private final VampireSMPPlugin plugin;

   public ForcedCureReopenCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         return true;
      } else if (!this.plugin.getForcedCureChoiceManager().hasPendingCure(player)) {
         player.sendMessage("§cYou do not have a pending cure decision.");
         return true;
      } else {
         this.plugin.getForcedCureChoiceManager().reopenChoiceGUI(player);
         return true;
      }
   }
}
