package pow.crimson2.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import pow.crimson2.VampireSMPPlugin;

public class DamageSuppressionListener implements Listener {
   private final VampireSMPPlugin plugin;

   public DamageSuppressionListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onEntityDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player) {
         try {
            int suppressionScore = this.plugin.getConfig().getInt("damage_suppression", 50);
            if (suppressionScore > 0) {
               double suppressionPercentage = suppressionScore / 100.0;
               double originalDamage = event.getDamage();
               double suppressedDamage = originalDamage * (1.0 - suppressionPercentage);
               event.setDamage(suppressedDamage);
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to read damage suppression from config: " + e.getMessage());
         }
      }
   }
}
