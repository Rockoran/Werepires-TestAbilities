package pow.crimson2.managers;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

public class BloodMoonManager {
   private boolean isBloodMoonActive = false;
   private VampireSMPPlugin plugin;
   private BukkitTask vampireBuffTask;

   public BloodMoonManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      (new BukkitRunnable() {
         public void run() {
            BloodMoonManager.this.checkTimeAndMoon();
         }
      }).runTaskTimer(this.plugin, 0L, 20L);
   }

   private void checkTimeAndMoon() {
      World world = this.plugin.getWorld();
      if (world == null) return; // world temporarily unloaded during world-swap
      long time = world.getTime();
      long fullTime = world.getFullTime();
      boolean isNight = time >= 12000L && time < 24000L;
      boolean isFullMoon = fullTime % 192000L < 24000L;
      if (isNight && isFullMoon && !this.isBloodMoonActive) {
         this.startBloodMoon(world);
      }

      if (this.isBloodMoonActive && (!isNight || !isFullMoon)) {
         this.endBloodMoon();
      }
   }

   private void startBloodMoon(World world) {
      if (!this.isBloodMoonActive) {
         this.isBloodMoonActive = true;
         this.plugin.logInfo("Blood moon started!");
         this.announceBloodMoon(world);
         this.vampireBuffTask = (new BukkitRunnable() {
            public void run() {
               BloodMoonManager.this.applyVampireBuffs();
            }
         }).runTaskTimer(this.plugin, 0L, 20L);
      }
   }

   private void endBloodMoon() {
      if (this.isBloodMoonActive) {
         this.isBloodMoonActive = false;
         this.plugin.logInfo("Blood moon ended!");
         if (this.vampireBuffTask != null && !this.vampireBuffTask.isCancelled()) {
            this.vampireBuffTask.cancel();
            this.vampireBuffTask = null;
         }

         String endMessage = "§7The blood moon fades away...";
         World world = this.plugin.getWorld();
         if (world != null) world.getPlayers().forEach(player -> player.sendMessage(endMessage));
      }
   }

   private void announceBloodMoon(World world) {
      String message = "§c§lA blood moon rises...";
      world.getPlayers().forEach(player -> {
         player.sendMessage(message);
         player.playSound(player, Sound.AMBIENT_CRIMSON_FOREST_MOOD, 1.0F, 1.0F);
      });
   }

   private void applyVampireBuffs() {
      if (this.isBloodMoonActive) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            boolean isEligible = this.plugin.getVampireManager().isVampireStage2(player) || this.plugin.getVampireManager().isVampireStage3(player);
            if (isEligible) {
               if (this.canPlayerSeeSky(player)) {
                  player.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 240, 0, false, false), false);
                  if (!player.getScoreboardTags().contains("informed_blood_moon")) {
                     player.addScoreboardTag("informed_blood_moon");
                     player.playSound(player, Sound.AMBIENT_NETHER_WASTES_MOOD, 1.0F, 1.0F);
                     player.sendMessage("§4You feel the Blood Moon's power coursing through your veins...");
                  }
               } else {
                  player.removePotionEffect(PotionEffectType.UNLUCK);
               }
            }
         }
      }
   }

   private boolean canPlayerSeeSky(Player player) {
      Block highestBlock = player.getWorld().getHighestBlockAt(player.getLocation());
      return player.getLocation().getBlockY() >= highestBlock.getY();
   }

   public boolean isActive() {
      return this.isBloodMoonActive;
   }

   public void forceStart() {
      World world = this.plugin.getWorld();
      if (!this.isBloodMoonActive && world != null) {
         this.startBloodMoon(world);
      }
   }

   public void forceStop() {
      if (this.isBloodMoonActive) {
         this.endBloodMoon();
      }
   }

   public String getCurrentMoonPhase() {
      World world = this.plugin.getWorld();
      if (world == null) return "Unknown";
      return this.getMoonPhaseName(world.getFullTime());
   }

   private String getMoonPhaseName(long fullTime) {
      long moonCycle = fullTime % 192000L;
      if (moonCycle < 24000L) {
         return "Full Moon";
      } else if (moonCycle < 48000L) {
         return "Waning Gibbous";
      } else if (moonCycle < 72000L) {
         return "Third Quarter";
      } else if (moonCycle < 96000L) {
         return "Waning Crescent";
      } else if (moonCycle < 120000L) {
         return "New Moon";
      } else if (moonCycle < 144000L) {
         return "Waxing Crescent";
      } else {
         return moonCycle < 168000L ? "First Quarter" : "Waxing Gibbous";
      }
   }

   public void shutdown() {
      if (this.vampireBuffTask != null && !this.vampireBuffTask.isCancelled()) {
         this.vampireBuffTask.cancel();
      }
   }
}
