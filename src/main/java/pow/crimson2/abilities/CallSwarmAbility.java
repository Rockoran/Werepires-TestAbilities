package pow.crimson2.abilities;

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

/** Call the Swarm (Stage 2): summon a harrying cloud of bats that blind and harass nearby humans. */
public class CallSwarmAbility extends VampireAbility {

   @Override public String getName() { return "callswarm"; }
   @Override public String getDisplayName() { return "Call the Swarm"; }
   @Override public String getDescription() {
      return "Call a shrieking swarm of bats that blinds and harries every human around you.";
   }
   @Override public int getMinimumStage() { return 2; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.callswarm.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.callswarm.cooldown", 60);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      double radius = plugin.getConfig().getDouble("abilities.vampire.callswarm.radius", 10.0);
      int durationTicks = plugin.getConfig().getInt("abilities.vampire.callswarm.duration-ticks", 120);
      double chipDamage = plugin.getConfig().getDouble("abilities.vampire.callswarm.chip-damage", 1.0);
      int blindnessTicks = plugin.getConfig().getInt("abilities.vampire.callswarm.blindness-ticks", 40);
      boolean spawnBats = plugin.getConfig().getBoolean("abilities.vampire.callswarm.spawn-bats", true);
      int batCount = plugin.getConfig().getInt("abilities.vampire.callswarm.bat-count", 6);

      // Optionally spawn real (cosmetic) bats that flit around and are cleaned up when it ends.
      final java.util.List<org.bukkit.entity.Bat> bats = new java.util.ArrayList<>();
      if (spawnBats) {
         java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
         for (int i = 0; i < batCount; i++) {
            Location spawn = player.getLocation().add(
                  (rng.nextDouble() - 0.5) * 4.0, 1.5 + rng.nextDouble() * 2.0, (rng.nextDouble() - 0.5) * 4.0);
            try {
               bats.add(player.getWorld().spawn(spawn, org.bukkit.entity.Bat.class, b -> {
                  b.setPersistent(false);
                  b.setRemoveWhenFarAway(true);
               }));
            } catch (Throwable ignored) {
            }
         }
      }

      new BukkitRunnable() {
         int elapsed = 0;
         @Override public void run() {
            if (!player.isOnline() || !vampireManager.isVampire(player) || elapsed >= durationTicks) {
               for (org.bukkit.entity.Bat b : bats) {
                  if (b != null && b.isValid()) b.remove();
               }
               cancel();
               return;
            }
            Location center = player.getLocation();
            center.getWorld().spawnParticle(Particle.SMOKE, center.clone().add(0, 1.2, 0),
                  25, radius / 2.0, 1.0, radius / 2.0, 0.01);
            center.getWorld().playSound(center, Sound.ENTITY_BAT_AMBIENT, SoundCategory.HOSTILE, 0.7F, 1.0F);
            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
               if (e instanceof Player human && vampireManager.isHuman(human)) {
                  human.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, false));
                  if (chipDamage > 0) human.damage(chipDamage, player);
               }
            }
            elapsed += 20;
         }
      }.runTaskTimer(plugin, 0L, 20L);

      player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.0F, 0.8F);
      player.sendMessage("§4You loose a swarm of shrieking bats upon the living!");
      return true;
   }
}
