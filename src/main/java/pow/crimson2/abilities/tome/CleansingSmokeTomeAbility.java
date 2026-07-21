package pow.crimson2.abilities.tome;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Cleansing Smoke: loose a cloud of garlic smoke that hurls back vampires and shields nearby humans. */
public class CleansingSmokeTomeAbility extends TomeAbility {

   public CleansingSmokeTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "CleansingSmoke",
            new String[]{"You learn to loose a billowing cloud of garlic smoke.",
                  "Vampires are flung back and weakened; humans within are shielded."},
            plugin.getConfig().getInt("abilities.tome.cleansingsmoke.cooldown", 90));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.cleansingsmoke.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      double radius = plugin.getConfig().getDouble("abilities.tome.cleansingsmoke.radius", 6.0);
      int shieldTicks = plugin.getConfig().getInt("abilities.tome.cleansingsmoke.shield-ticks", 120);
      int weakenTicks = plugin.getConfig().getInt("abilities.tome.cleansingsmoke.vampire-weaken-ticks", 100);
      double knockback = plugin.getConfig().getDouble("abilities.tome.cleansingsmoke.knockback", 1.2);

      Location center = player.getLocation();
      VampireManager vm = plugin.getVampireManager();

      player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, shieldTicks, 1, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, shieldTicks, 0, false, false));
      for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
         if (!(e instanceof Player pl)) continue;
         if (vm.isHuman(pl)) {
            pl.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, shieldTicks, 1, false, false));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, shieldTicks, 0, false, false));
            pl.sendMessage("§aThe garlic smoke wards you against the night.");
         } else if (vm.isVampire(pl)) {
            Vector away = pl.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0.5, 0);
            pl.setVelocity(away.normalize().multiply(knockback).setY(0.4));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weakenTicks, 0, false, false));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, weakenTicks, 0, false, false));
            pl.sendMessage("§cThe reek of garlic sears your senses!");
         }
      }

      center.getWorld().spawnParticle(Particle.SNEEZE, center.clone().add(0, 1, 0), 80, radius / 2.0, 1.0, radius / 2.0, 0.05);
      center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0), 40, radius / 2.0, 1.0, radius / 2.0, 0.02);
      player.playSound(center, Sound.ENTITY_BREEZE_SHOOT, SoundCategory.MASTER, 1.0F, 0.8F);
      this.sendSuccessMessage(player, "You loose a cloud of cleansing garlic smoke!");
      return true;
   }
}
