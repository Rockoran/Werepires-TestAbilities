package pow.crimson2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

public class PendingMessageCommand implements CommandExecutor {
   private final VampireSMPPlugin plugin;

   public PendingMessageCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         this.plugin.getPlayerChatManager().handleSendPendingMessage(player);
         player.addScoreboardTag("ChatPrevented");
         return true;
      }
   }
}
