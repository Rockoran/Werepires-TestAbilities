package pow.crimson2.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Hypnotic Gaze (Stage 3): fix your gaze on a human, clouding their mind with slowness and nausea. */
public class HypnoticGazeAbility extends VampireAbility {

   @Override public String getName() { return "hypnoticgaze"; }
   @Override public String getDisplayName() { return "Hypnotic Gaze"; }
   @Override public String getDescription() {
      return "Lock eyes with a human and cloud their mind — slowness and nausea grip them. Garlic breaks your hold.";
   }
   @Override public int getMinimumStage() { return 3; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.hypnoticgaze.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.hypnoticgaze.cooldown", 45);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      int range = plugin.getConfig().getInt("abilities.vampire.hypnoticgaze.range", 12);
      int duration = plugin.getConfig().getInt("abilities.vampire.hypnoticgaze.duration-ticks", 100);
      int slowAmplifier = plugin.getConfig().getInt("abilities.vampire.hypnoticgaze.slowness-amplifier", 2);
      int blindTicks = plugin.getConfig().getInt("abilities.vampire.hypnoticgaze.blindness-ticks", 60);

      Entity looked = player.getTargetEntity(range);
      if (!(looked instanceof Player human) || !vampireManager.isHuman(human)) {
         player.sendMessage("§cYou must fix your gaze upon a human.");
         return false;
      }
      if (plugin.getBeetrootManager() != null && plugin.getBeetrootManager().hasBeetrootImmunity(human)) {
         player.sendMessage("§cThe reek of garlic shatters your hold over " + human.getName() + ".");
         return false;
      }

      human.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, slowAmplifier, false, false));
      human.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, false, false));
      if (blindTicks > 0) {
         human.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.min(duration, blindTicks), 0, false, false));
      }

      human.getWorld().spawnParticle(Particle.WITCH, human.getEyeLocation(), 20, 0.3, 0.4, 0.3, 0.01);
      player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.7F, 1.4F);
      human.playSound(human.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 0.8F, 0.6F);
      human.sendMessage("§5Your mind clouds under the vampire's gaze...");
      player.sendMessage("§5You ensnare " + human.getName() + " in your gaze.");
      return true;
   }
}
