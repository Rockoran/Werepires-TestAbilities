package pow.crimson2.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;

public class VampireTurningManager {
   private static final String TURNING_DISABLED_TAG = "turning_disabled";

   private final VampireSMPPlugin plugin;
   private final Map<UUID, Boolean> turningEnabled = new HashMap<>();

   public VampireTurningManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean isTurningEnabled(Player vampire) {
      // Scoreboard tag is the source of truth (survives restarts); in-memory map is a cache.
      if (vampire.getScoreboardTags().contains(TURNING_DISABLED_TAG)) return false;
      return this.turningEnabled.getOrDefault(vampire.getUniqueId(), true);
   }

   public boolean toggleTurning(Player vampire) {
      boolean currentState = this.isTurningEnabled(vampire);
      boolean newState = !currentState;
      this.setTurningEnabled(vampire, newState);
      return newState;
   }

   public void setTurningEnabled(Player vampire, boolean enabled) {
      this.turningEnabled.put(vampire.getUniqueId(), enabled);
      if (enabled) {
         vampire.removeScoreboardTag(TURNING_DISABLED_TAG);
      } else {
         vampire.addScoreboardTag(TURNING_DISABLED_TAG);
      }
      this.updateLuckEffect(vampire, enabled);
   }

   public void disableAllVampireTurning() {
      if (!this.plugin.getConfigManager().getDisableTurningAfterTurn()) {
         return;
      }
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.plugin.getVampireManager().isVampire(player)) {
            this.setTurningEnabled(player, false);
         }
      }
   }

   public void enableAllVampireTurning() {
      this.turningEnabled.clear();

      for (Player player : Bukkit.getOnlinePlayers()) {
         player.removeScoreboardTag(TURNING_DISABLED_TAG);
         if (this.plugin.getVampireManager().isVampire(player)) {
            this.updateLuckEffect(player, true);
         }
      }
   }

   private void updateLuckEffect(Player vampire, boolean turningEnabled) {
      if (turningEnabled) {
         vampire.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, -1, 0, false, false, true));
      } else {
         vampire.removePotionEffect(PotionEffectType.LUCK);
      }
   }

   public void applyLuckEffectIfEnabled(Player vampire) {
      if (this.isTurningEnabled(vampire)) {
         this.updateLuckEffect(vampire, true);
      }
   }

   public void shutdown() {
      this.turningEnabled.clear();
   }
}
