package pow.crimson2.abilities.tome;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Sanctuary: raise a warding light that briefly shields you and nearby humans from harm. */
public class SanctuaryTomeAbility extends TomeAbility {

   public SanctuaryTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "Sanctuary",
            new String[]{"You learn to raise a sanctuary of warding light.",
                  "For a few moments you and nearby humans are shielded from harm."},
            plugin.getConfig().getInt("abilities.tome.sanctuary.cooldown", 120));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.sanctuary.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      double radius = plugin.getConfig().getDouble("abilities.tome.sanctuary.radius", 6.0);
      int duration = plugin.getConfig().getInt("abilities.tome.sanctuary.duration-ticks", 100);
      int resistanceAmp = plugin.getConfig().getInt("abilities.tome.sanctuary.resistance-amplifier", 3);

      VampireManager vm = plugin.getVampireManager();
      int affected = 0;
      player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, resistanceAmp, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, false, false));
      affected++;
      for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
         if (e instanceof Player pl && vm.isHuman(pl)) {
            pl.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, resistanceAmp, false, false));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, false, false));
            pl.sendMessage("§eA sanctuary of light shields you.");
            affected++;
         }
      }

      player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 60, radius / 2.0, 1.0, radius / 2.0, 0.02);
      player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, SoundCategory.MASTER, 1.0F, 1.4F);
      this.sendSuccessMessage(player, "You raise a sanctuary of warding light (" + affected + " shielded).");
      return true;
   }
}
