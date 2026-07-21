package pow.crimson2.abilities;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Bloodscent (Stage 1): for a short time, wounded humans nearby trail blood-red motes only you can see. */
public class BloodscentAbility extends VampireAbility {

   @Override public String getName() { return "bloodscent"; }
   @Override public String getDisplayName() { return "Bloodscent"; }
   @Override public String getDescription() {
      return "Breathe in the scent of blood — wounded humans nearby are marked with red motes only you can see.";
   }
   @Override public int getMinimumStage() { return 1; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.bloodscent.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.bloodscent.cooldown", 30);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      double radius = plugin.getConfig().getDouble("abilities.vampire.bloodscent.radius", 40.0);
      double threshold = plugin.getConfig().getDouble("abilities.vampire.bloodscent.health-threshold", 10.0);
      int durationTicks = plugin.getConfig().getInt("abilities.vampire.bloodscent.duration-ticks", 200);
      int intervalTicks = Math.max(2, plugin.getConfig().getInt("abilities.vampire.bloodscent.interval-ticks", 10));

      final Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(170, 0, 0), 1.6F);
      new BukkitRunnable() {
         int elapsed = 0;
         @Override public void run() {
            if (!player.isOnline() || !vampireManager.isVampire(player) || elapsed >= durationTicks) {
               cancel();
               return;
            }
            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
               if (e instanceof Player human && vampireManager.isHuman(human)
                     && human.getHealth() <= threshold) {
                  // Per-player particle — only the caster sees the mark.
                  player.spawnParticle(Particle.DUST, human.getLocation().add(0, 1.0, 0),
                        6, 0.25, 0.5, 0.25, 0.0, red);
               }
            }
            elapsed += intervalTicks;
         }
      }.runTaskTimer(plugin, 0L, intervalTicks);

      player.playSound(player.getLocation(), Sound.ENTITY_FOX_SNIFF, SoundCategory.PLAYERS, 1.0F, 0.8F);
      player.sendMessage("§4You breathe deep, drinking in the scent of fresh blood...");
      return true;
   }
}
