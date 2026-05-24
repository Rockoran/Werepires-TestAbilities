package pow.crimson2.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import pow.crimson2.VampireSMPPlugin;

public class NoSleepListener implements Listener {
   VampireSMPPlugin plugin;

   public NoSleepListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onPlayerBedEnter(PlayerBedEnterEvent event) {
      Player player = event.getPlayer();
      if (this.plugin.getVampireManager().isVampire(player)) {
         player.sendMessage("§cYou close your eyes... And are left disappointed. Once again, you find it impossible to fall asleep.");
      } else if (!this.plugin.getVampireManager().isVampire(player)) {
         player.sendMessage("§cYou close your eyes... And are left disappointed. You feel too uneasy to sleep.");
      }

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isSleeping()) {
            player.teleport(player.getLocation().add(0.0, 1.0, 0.0));
         }
      }, 2L);
   }
}
