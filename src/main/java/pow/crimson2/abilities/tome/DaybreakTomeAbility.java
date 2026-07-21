package pow.crimson2.abilities.tome;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Daybreak: call a shaft of blinding daylight that burns vampires caught within it. */
public class DaybreakTomeAbility extends TomeAbility {

   public DaybreakTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "Daybreak",
            new String[]{"You learn to call forth a shaft of pure daylight.",
                  "Vampires caught in the light are scorched as if by the sun."},
            plugin.getConfig().getInt("abilities.tome.daybreak.cooldown", 120));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.daybreak.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      double radius = plugin.getConfig().getDouble("abilities.tome.daybreak.radius", 5.0);
      int duration = plugin.getConfig().getInt("abilities.tome.daybreak.duration-ticks", 100);
      int interval = Math.max(5, plugin.getConfig().getInt("abilities.tome.daybreak.interval-ticks", 20));
      double damage = plugin.getConfig().getDouble("abilities.tome.daybreak.vampire-damage", 2.0);
      int fireTicks = plugin.getConfig().getInt("abilities.tome.daybreak.fire-ticks", 40);

      // Anchor the pillar of light where the player is looking (or at their feet).
      final Location center = player.getLocation().clone();
      final VampireManager vm = plugin.getVampireManager();
      new BukkitRunnable() {
         int elapsed = 0;
         @Override public void run() {
            if (center.getWorld() == null || elapsed >= duration) { cancel(); return; }
            for (double y = 0; y < 6; y += 0.5) {
               center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, y, 0), 4, 0.4, 0.1, 0.4, 0.0);
            }
            for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
               if (e instanceof Player pl && vm.isVampire(pl)) {
                  if (fireTicks > 0) pl.setFireTicks(Math.max(pl.getFireTicks(), fireTicks));
                  if (damage > 0) pl.damage(damage);
               }
            }
            elapsed += interval;
         }
      }.runTaskTimer(plugin, 0L, interval);

      player.getWorld().playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.MASTER, 1.0F, 1.2F);
      player.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 1.0F, 2.0F);
      this.sendSuccessMessage(player, "Daylight erupts, scouring the undead!");
      return true;
   }
}
