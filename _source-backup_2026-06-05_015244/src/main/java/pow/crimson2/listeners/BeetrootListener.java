package pow.crimson2.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.BeetrootManager;
import pow.crimson2.managers.SessionManager;

public class BeetrootListener implements Listener {
   private final VampireSMPPlugin plugin;
   private final BeetrootManager beetrootManager;
   private final SessionManager sessionManager;

   public BeetrootListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.beetrootManager = plugin.getBeetrootManager();
      this.sessionManager = plugin.getSessionManager();
   }

   @EventHandler(priority = EventPriority.NORMAL)
   public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
      Player player = event.getPlayer();
      ItemStack item = event.getItem();
      if (this.sessionManager.isSessionActive()) {
         if (item.getType() == Material.BEETROOT) {
            this.beetrootManager.handleBeetrootConsumption(player);
         }
      }
   }
}
