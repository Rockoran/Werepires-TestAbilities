package pow.crimson2.managers;

import io.papermc.paper.event.entity.WaterBottleSplashEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

public class HolyWaterEffectManager implements Listener {
   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private final ConfigManager configManager;
   private final Map<UUID, BukkitTask> disabledVampires = new HashMap<>();

   public HolyWaterEffectManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
      this.configManager = plugin.getConfigManager();
      Bukkit.getPluginManager().registerEvents(this, plugin);
      plugin.logInfo("HolyWaterEffectManager initialized and event listener registered!");
   }

   @EventHandler
   public void onPotionSplash(PotionSplashEvent event) {
      if (event instanceof WaterBottleSplashEvent waterEvent) {
         Location splashLocation = waterEvent.getPotion().getLocation();
         double splashRadius = 4.0;

         for (Entity nearby : splashLocation.getWorld().getNearbyEntities(splashLocation, splashRadius, splashRadius, splashRadius)) {
            if (nearby instanceof Player player) {
               double distance = nearby.getLocation().distance(splashLocation);
               if (distance <= splashRadius) {
                  this.processHolyWaterHit(player);
               }
            }
         }
      } else {
         ThrownPotion potion = event.getPotion();
         ItemStack potionItem = potion.getItem();
         if (this.isWaterSplashBottle(potionItem)) {
            for (LivingEntity entity : event.getAffectedEntities()) {
               this.processHolyWaterHit(entity);
            }
         }
      }
   }

   private void processHolyWaterHit(LivingEntity entity) {
      if (entity instanceof Player player
         && this.vampireManager.isVampire(player)
         && (this.vampireManager.isVampireStage2(player) || this.vampireManager.isVampireStage3(player))) {
         this.applyHolyWaterEffect(player);
      }
   }

   private boolean isWaterSplashBottle(ItemStack item) {
      if (item == null) {
         return false;
      }

      if (item.getType() != Material.SPLASH_POTION) {
         return false;
      }

      if (!item.hasItemMeta()) {
         return true;
      }

      if (!(item.getItemMeta() instanceof PotionMeta)) {
         return true;
      }

      PotionMeta potionMeta = (PotionMeta)item.getItemMeta();
      if (potionMeta.hasCustomEffects()) {
         return false;
      }

      PotionType baseType = potionMeta.getBasePotionType();
      return baseType == null || baseType == PotionType.WATER
         ? true
         : baseType == PotionType.AWKWARD || baseType == PotionType.MUNDANE || baseType == PotionType.THICK;
   }

   public void applyHolyWaterEffect(Player vampire) {
      UUID vampireId = vampire.getUniqueId();
      BukkitTask existingTask = this.disabledVampires.get(vampireId);
      if (existingTask != null && !existingTask.isCancelled()) {
         existingTask.cancel();
      }

      this.disabledVampires.put(vampireId, null);
      vampire.getWorld().playSound(vampire.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 1.0F, 1.0F);
      this.notifyVampireDisabled(vampire);
      BukkitTask enableTask = Bukkit.getScheduler()
         .runTaskLater(this.plugin, () -> this.removeHolyWaterEffect(vampire), this.configManager.getHolyWaterDisableDurationSeconds() * 20L);
      this.disabledVampires.put(vampireId, enableTask);
      this.plugin.logInfo("Applied holy water effect to vampire: " + vampire.getName());
   }

   private void removeHolyWaterEffect(Player vampire) {
      this.removeHolyWaterEffect(vampire, true);
   }

   public void removeHolyWaterEffect(Player vampire, boolean notify) {
      UUID vampireId = vampire.getUniqueId();
      BukkitTask task = this.disabledVampires.remove(vampireId);
      if (task != null && !task.isCancelled()) {
         task.cancel();
      }

      if (notify && vampire.isOnline()) {
         this.notifyVampireEnabled(vampire);
      }

      this.plugin.logInfo("Removed holy water effect from vampire: " + vampire.getName());
   }

   public boolean isAbilitiesDisabled(Player vampire) {
      return this.disabledVampires.containsKey(vampire.getUniqueId());
   }

   public long getRemainingDisableTime(Player vampire) {
      UUID vampireId = vampire.getUniqueId();
      BukkitTask task = this.disabledVampires.get(vampireId);
      if (task == null) {
         return 0L;
      }

      int duration = this.configManager.getHolyWaterDisableDurationSeconds();
      return Math.max(0L, duration - System.currentTimeMillis() / 1000L % duration);
   }

   private void notifyVampireDisabled(Player vampire) {
      vampire.sendMessage("§cThe holy water sears your vampiric essence!");
      int duration = this.configManager.getHolyWaterDisableDurationSeconds();
      if (duration >= 60) {
         vampire.sendMessage(
            "§cYour abilities and blood regeneration have been disabled for " + duration / 60 + " minute" + (duration / 60 != 1 ? "s" : "") + "."
         );
      } else {
         vampire.sendMessage("§cYour abilities and blood regeneration have been disabled for " + duration + " second" + (duration != 1 ? "s" : "") + ".");
      }

      vampire.playSound(vampire, Sound.ENTITY_GENERIC_HURT, SoundCategory.MASTER, 1.0F, 0.8F);
      vampire.playSound(vampire, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 1.0F, 1.2F);
      vampire.playSound(vampire, Sound.ENTITY_WITCH_HURT, SoundCategory.MASTER, 0.8F, 1.5F);
   }

   private void notifyVampireEnabled(Player vampire) {
      vampire.sendMessage("§cYou feel your dark powers flowing through you once more.");
      vampire.playSound(vampire, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 0.5F, 0.8F);
   }

   public int getDisabledVampireCount() {
      return this.disabledVampires.size();
   }

   public void clearAllEffects() {
      for (Entry<UUID, BukkitTask> entry : this.disabledVampires.entrySet()) {
         UUID vampireId = entry.getKey();
         BukkitTask task = entry.getValue();
         if (task != null && !task.isCancelled()) {
            task.cancel();
         }

         Player vampire = Bukkit.getPlayer(vampireId);
         if (vampire != null && vampire.isOnline()) {
            vampire.sendMessage("§aAn admin has restored your vampiric abilities.");
         }
      }

      int cleared = this.disabledVampires.size();
      this.disabledVampires.clear();
      this.plugin.logInfo("Cleared holy water effects from " + cleared + " vampires");
   }

   public void shutdown() {
      for (BukkitTask task : this.disabledVampires.values()) {
         if (task != null && !task.isCancelled()) {
            task.cancel();
         }
      }

      this.disabledVampires.clear();
      this.plugin.logInfo("HolyWaterEffectManager shutdown complete");
   }
}
