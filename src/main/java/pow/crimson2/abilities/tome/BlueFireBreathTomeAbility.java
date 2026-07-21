package pow.crimson2.abilities.tome;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;

public class BlueFireBreathTomeAbility extends TomeAbility {

   public BlueFireBreathTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "BlueFireBreath",
         new String[]{
            "You channel ancient soul fire into a concentrated torrent of blue flame,",
            "scorching all in a narrow cone before you with supernatural heat."
         },
         plugin.getConfigManager().getTomeBlueFireBreathCooldown()
      );
   }

   @Override
   public boolean isInLootPool() {
      return false;
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }

      if (!this.plugin.getTomeManager().hasAbility(player, "FireBreath")) {
         this.sendCannotUseMessage(player, "You must first master Fire Breath before wielding soul flame.");
         return false;
      }

      player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.6f);
      player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SAND_PLACE, SoundCategory.PLAYERS, 0.8f, 1.5f);
      this.sendSuccessMessage(player, "You unleash a torrent of soul flame!");
      this.startBreathStream(player);
      return true;
   }

   private void startBreathStream(Player player) {
      double range = plugin.getConfigManager().getTomeBlueFireBreathRange();
      double coneHalfAngle = Math.toRadians(plugin.getConfigManager().getTomeBlueFireBreathConeAngle());
      int durationTicks = plugin.getConfigManager().getTomeBlueFireBreathDurationTicks();
      int particlesPerTick = plugin.getConfigManager().getTomeBlueFireBreathParticlesPerTick();
      double damage = plugin.getConfigManager().getTomeBlueFireBreathDamage();
      int fireTicks = plugin.getConfigManager().getTomeBlueFireBreathFireTicks();

      new BukkitRunnable() {
         final Random rand = new Random();
         int tick = 0;

         @Override
         public void run() {
            if (!player.isOnline() || tick >= durationTicks) {
               this.cancel();
               return;
            }

            Vector dir = player.getLocation().getDirection().normalize();
            Location origin = player.getEyeLocation();

            Vector perp1 = dir.clone().crossProduct(new Vector(0, 1, 0));
            if (perp1.lengthSquared() < 0.001) perp1 = dir.clone().crossProduct(new Vector(1, 0, 0));
            perp1.normalize();
            Vector perp2 = dir.clone().crossProduct(perp1).normalize();

            for (int i = 0; i < particlesPerTick; i++) {
               double dist = 0.3 + Math.pow(rand.nextDouble(), 0.6) * range;
               double maxRadius = dist * Math.tan(coneHalfAngle);
               double angle = rand.nextDouble() * Math.PI * 2;
               double radius = Math.sqrt(rand.nextDouble()) * maxRadius;

               double sx = (perp1.getX() * Math.cos(angle) + perp2.getX() * Math.sin(angle)) * radius;
               double sy = (perp1.getY() * Math.cos(angle) + perp2.getY() * Math.sin(angle)) * radius;
               double sz = (perp1.getZ() * Math.cos(angle) + perp2.getZ() * Math.sin(angle)) * radius;

               Location point = origin.clone().add(dir.clone().multiply(dist)).add(sx, sy, sz);
               point.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0, 0, 0, 0);
            }

            if (tick % 4 == 0) {
               for (Entity entity : player.getNearbyEntities(range, range, range)) {
                  if (entity.equals(player) || !(entity instanceof LivingEntity living)) continue;
                  Vector toEntity = entity.getLocation().add(0, 1, 0).toVector().subtract(origin.toVector());
                  if (toEntity.length() > range) continue;
                  if (toEntity.normalize().angle(dir) > coneHalfAngle) continue;
                  living.damage(damage, player);
                  living.setFireTicks(fireTicks);
               }
            }

            tick++;
         }
      }.runTaskTimer(plugin, 0L, 1L);
   }
}
