package pow.crimson2.abilities.tome;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Consecrate Ground: sanctify the area around you — humans are mended, vampires seared and weakened. */
public class ConsecrateGroundTomeAbility extends TomeAbility {

   public ConsecrateGroundTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "ConsecrateGround",
            new String[]{"You learn to sanctify the very ground beneath you.",
                  "Holy light heals the faithful and sears the undead nearby."},
            plugin.getConfig().getInt("abilities.tome.consecrateground.cooldown", 120));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.consecrateground.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      double radius = plugin.getConfig().getDouble("abilities.tome.consecrateground.radius", 6.0);
      int duration = plugin.getConfig().getInt("abilities.tome.consecrateground.duration-ticks", 200);
      int interval = Math.max(5, plugin.getConfig().getInt("abilities.tome.consecrateground.interval-ticks", 20));
      double vampDamage = plugin.getConfig().getDouble("abilities.tome.consecrateground.vampire-damage", 1.0);

      final Location center = player.getLocation().clone();
      final VampireManager vm = plugin.getVampireManager();
      final int effectTicks = interval + 10;
      new BukkitRunnable() {
         int elapsed = 0;
         @Override public void run() {
            if (center.getWorld() == null || elapsed >= duration) { cancel(); return; }
            center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 0.2, 0),
                  30, radius / 2.0, 0.2, radius / 2.0, 0.0);
            for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
               if (!(e instanceof Player pl)) continue;
               if (vm.isVampire(pl)) {
                  pl.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, effectTicks, 0, false, false));
                  if (vampDamage > 0) pl.damage(vampDamage);
               } else if (vm.isHuman(pl)) {
                  pl.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, effectTicks, 0, false, false));
               }
            }
            elapsed += interval;
         }
      }.runTaskTimer(plugin, 0L, interval);

      player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.0F, 1.5F);
      this.sendSuccessMessage(player, "You consecrate the ground — holy light wells up around you.");
      return true;
   }
}
