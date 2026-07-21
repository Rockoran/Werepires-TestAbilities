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

public class FireBreathTomeAbility extends TomeAbility {

   public FireBreathTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "FireBreath",
         new String[]{
            "You unleash a torrent of flame in a cone before you,",
            "igniting and scorching all living creatures caught in its path."
         },
         plugin.getConfigManager().getTomeFireBreathCooldown()
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

      player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);
      this.sendSuccessMessage(player, "You unleash a torrent of flame!");
      this.startBreathStream(player);
      return true;
   }

   private void startBreathStream(Player player) {
      double range = plugin.getConfigManager().getTomeFireBreathRange();
      double coneHalfAngle = Math.toRadians(plugin.getConfigManager().getTomeFireBreathConeAngle());
      int durationTicks = plugin.getConfigManager().getTomeFireBreathDurationTicks();
      int particlesPerTick = plugin.getConfigManager().getTomeFireBreathParticlesPerTick();
      double damage = plugin.getConfigManager().getTomeFireBreathDamage();
      int fireTicks = plugin.getConfigManager().getTomeFireBreathFireTicks();

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
               point.getWorld().spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
               if (dist > range * 0.6 && rand.nextInt(4) == 0) {
                  point.getWorld().spawnParticle(Particle.SMOKE, point, 1, 0, 0, 0, 0);
               }
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
