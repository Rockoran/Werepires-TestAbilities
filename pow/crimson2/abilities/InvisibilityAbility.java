package pow.crimson2.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireAbilityManager;
import pow.crimson2.managers.VampireManager;

public class InvisibilityAbility extends VampireAbility {
   @Override
   public String getName() {
      return "vanish";
   }

   @Override
   public String getDisplayName() {
      return "Vampiric Vanish";
   }

   @Override
   public String getDescription() {
      return "Become invisible to enemies for a short time. Duration increases with vampire stage.";
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getVampireVanishCooldown();
   }

   @Override
   public int getMinimumStage() {
      return 2;
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
         player.removePotionEffect(PotionEffectType.INVISIBILITY);
         plugin.getVampireAbilityManager().clearInvisibilityAttackCount(player);
         this.createVanishEffects(player, false);
         this.sendReappearMessage(player);
         this.playReappearSound(player);
         return true;
      } else {
         int vampireStage = vampireManager.getVampireStage(player);
         int durationTicks = this.getInvisibilityDuration(vampireStage);
         this.createVanishEffects(player, true);
         PotionEffect invisibility = new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, false, false, false);
         player.addPotionEffect(invisibility);
         this.sendVanishMessage(player, vampireStage, durationTicks / 20);
         this.playVanishSound(player, vampireStage);
         this.scheduleInvisibilityWarning(player, durationTicks, plugin);
         return true;
      }
   }

   private int getInvisibilityDuration(int stage) {
      switch (stage) {
         case 2:
            return 2400;
         case 3:
            return 4800;
         default:
            return 1600;
      }
   }

   private void createVanishEffects(Player player, boolean isVanishing) {
      if (player.getWorld() != null) {
         if (isVanishing) {
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0.0, 1.0, 0.0), 30, 0.5, 1.0, 0.5, 0.1);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 15, 1.0, 1.5, 1.0, 0.3);
         } else {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 1.0, 0.0), 20, 0.3, 0.8, 0.3, 0.05);
         }
      }
   }

   private void sendVanishMessage(Player player, int stage, int durationSeconds) {
      player.sendMessage("§8§lYou fade into the shadows... (" + VampireAbilityManager.formatTime(durationSeconds) + ")");
   }

   private void playVanishSound(Player player, int stage) {
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.5F, 0.6F);
   }

   private void scheduleInvisibilityWarning(Player player, int totalDurationTicks, VampireSMPPlugin plugin) {
      int warningDelay = Math.max(totalDurationTicks - 40, 20);
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
         if (player.isOnline() && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.sendMessage("§7§oYour invisibility is fading...");
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.3F, 0.8F);
         }
      }, warningDelay);
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
         if (player.isOnline() && player.isInvisible() && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            plugin.getVampireAbilityManager().clearInvisibilityAttackCount(player);
            this.createVanishEffects(player, false);
            this.sendReappearMessage(player);
            this.playReappearSound(player);
         }
      }, totalDurationTicks + 5);
   }

   private void sendReappearMessage(Player player) {
      player.sendMessage("§7You emerge from the shadows...");
   }

   private void playReappearSound(Player player) {
      player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.4F, 1.2F);
   }
}
