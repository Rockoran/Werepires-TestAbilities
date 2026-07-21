package pow.crimson2.abilities;

import org.bukkit.Color;
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

/** Crimson Veil (Stage 3): wreathe yourself in a blood mist that heals vampires and saps humans. */
public class CrimsonVeilAbility extends VampireAbility {

   @Override public String getName() { return "crimsonveil"; }
   @Override public String getDisplayName() { return "Crimson Veil"; }
   @Override public String getDescription() {
      return "Wreathe yourself in a roiling blood mist — vampires within are mended, humans grow weak and slow.";
   }
   @Override public int getMinimumStage() { return 3; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.crimsonveil.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.crimsonveil.cooldown", 120);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      double radius = plugin.getConfig().getDouble("abilities.vampire.crimsonveil.radius", 8.0);
      int durationTicks = plugin.getConfig().getInt("abilities.vampire.crimsonveil.duration-ticks", 200);
      int intervalTicks = Math.max(5, plugin.getConfig().getInt("abilities.vampire.crimsonveil.interval-ticks", 20));
      int regenAmp = plugin.getConfig().getInt("abilities.vampire.crimsonveil.vampire-regen-amplifier", 0);
      int weakAmp = plugin.getConfig().getInt("abilities.vampire.crimsonveil.human-weakness-amplifier", 0);

      final Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 2.0F);
      final int effectTicks = intervalTicks + 10;
      new BukkitRunnable() {
         int elapsed = 0;
         @Override public void run() {
            if (!player.isOnline() || elapsed >= durationTicks) {
               cancel();
               return;
            }
            Location center = player.getLocation();
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(0, 1, 0),
                  40, radius / 2.0, 1.0, radius / 2.0, 0.0, red);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, effectTicks, regenAmp, false, false));
            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
               if (!(e instanceof Player pl)) continue;
               if (vampireManager.isVampire(pl)) {
                  pl.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, effectTicks, regenAmp, false, false));
               } else if (vampireManager.isHuman(pl)) {
                  pl.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, effectTicks, weakAmp, false, false));
                  pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectTicks, 0, false, false));
               }
            }
            elapsed += intervalTicks;
         }
      }.runTaskTimer(plugin, 0L, intervalTicks);

      player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.6F, 0.5F);
      player.sendMessage("§4A veil of blood mist rises around you...");
      return true;
   }
}
