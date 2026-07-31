package pow.crimson2.managers;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

public class WerewolfPackManager {

   // Loaded from config in start()
   private double packRadius;
   private int packEffectDuration;
   private long tickInterval;

   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private BukkitTask packTask;

   public WerewolfPackManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
   }

   public void start() {
      if (this.packTask != null && !this.packTask.isCancelled()) {
         this.packTask.cancel();
      }

      this.packRadius = this.plugin.getConfigManager().getWerewolfPackRadius();
      this.packEffectDuration = this.plugin.getConfigManager().getWerewolfPackEffectDurationTicks();
      this.tickInterval = this.plugin.getConfigManager().getWerewolfPackCheckIntervalTicks();

      this.packTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::applyPackBonuses, this.tickInterval, this.tickInterval);
      this.plugin.logInfo("WerewolfPackManager started - pack bonuses active (radius=" + packRadius + ", interval=" + tickInterval + "t)");
   }

   public void stop() {
      if (this.packTask != null && !this.packTask.isCancelled()) {
         this.packTask.cancel();
         this.packTask = null;
      }

      this.plugin.logInfo("WerewolfPackManager stopped");
   }

   private void applyPackBonuses() {
      if (!this.plugin.getSessionManager().isSessionActive()) return;

      for (Player wolf : Bukkit.getOnlinePlayers()) {
         if (!this.vampireManager.isWerewolf(wolf)) continue;
         if (wolf.isDead()) continue;

         int nearbyWolves = countNearbyWerewolves(wolf);

         if (nearbyWolves >= 1) {
            // Speed I + Strength I for any pack member
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, this.packEffectDuration, 0, false, false));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, this.packEffectDuration, 0, false, false));
         }

         if (nearbyWolves >= 2) {
            // Regeneration I additionally for larger packs
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, this.packEffectDuration, 0, false, false));
         }
      }
   }

   private int countNearbyWerewolves(Player wolf) {
      int count = 0;

      for (Player other : Bukkit.getOnlinePlayers()) {
         if (other.getUniqueId().equals(wolf.getUniqueId())) continue;
         if (!this.vampireManager.isWerewolf(other)) continue;
         if (other.isDead()) continue;
         if (!other.getWorld().equals(wolf.getWorld())) continue;

         if (wolf.getLocation().distance(other.getLocation()) <= this.packRadius) {
            count++;
         }
      }

      return count;
   }

   public void shutdown() {
      this.stop();
   }
}
