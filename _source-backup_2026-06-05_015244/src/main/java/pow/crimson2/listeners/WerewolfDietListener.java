package pow.crimson2.listeners;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public class WerewolfDietListener implements Listener {

   private static final Set<Material> ALLOWED_MEATS = EnumSet.of(
      Material.BEEF,
      Material.COOKED_BEEF,
      Material.PORKCHOP,
      Material.COOKED_PORKCHOP,
      Material.CHICKEN,
      Material.COOKED_CHICKEN,
      Material.MUTTON,
      Material.COOKED_MUTTON,
      Material.RABBIT,
      Material.COOKED_RABBIT,
      Material.SALMON,
      Material.COOKED_SALMON,
      Material.COD,
      Material.COOKED_COD,
      Material.TROPICAL_FISH,
      Material.ROTTEN_FLESH
   );

   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;

   public WerewolfDietListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
   }

   @EventHandler
   public void onPlayerConsume(PlayerItemConsumeEvent event) {
      Player player = event.getPlayer();
      if (!this.vampireManager.isWerewolf(player)) return;

      Material food = event.getItem().getType();

      // Garlic (beetroot) is ignored entirely for werewolves — no effect either way
      if (food == Material.BEETROOT || food == Material.BEETROOT_SOUP) return;

      // Block non-meat food
      if (!ALLOWED_MEATS.contains(food)) {
         event.setCancelled(true);
         player.sendMessage("§6The beast within rejects this food.");
         player.sendMessage("§6You can only consume meat.");
         return;
      }

      // Feed the beast — delegate to WerewolfHungerManager
      if (this.plugin.getWerewolfHungerManager() != null) {
         float hungerGained = this.plugin.getConfigManager().getWerewolfHungerPerMeat();
         this.plugin.getWerewolfHungerManager().feedHunger(player, hungerGained);
      }
   }
}
