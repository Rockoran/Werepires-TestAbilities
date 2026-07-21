package pow.crimson2.abilities.tome;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;

/** Last Vigil: a desperate stand — for a few seconds you are wrapped in resistance, regen and absorption. */
public class LastVigilTomeAbility extends TomeAbility {

   public LastVigilTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "LastVigil",
            new String[]{"You learn to make a final, desperate stand.",
                  "For a few seconds you are wrapped in protective, mending light."},
            plugin.getConfig().getInt("abilities.tome.lastvigil.cooldown", 120));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.lastvigil.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      int duration = plugin.getConfig().getInt("abilities.tome.lastvigil.duration-ticks", 120);
      int resistanceAmp = plugin.getConfig().getInt("abilities.tome.lastvigil.resistance-amplifier", 2);
      int regenAmp = plugin.getConfig().getInt("abilities.tome.lastvigil.regeneration-amplifier", 1);
      int absorptionAmp = plugin.getConfig().getInt("abilities.tome.lastvigil.absorption-amplifier", 1);

      player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, resistanceAmp, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, regenAmp, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, absorptionAmp, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, false, false));

      player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
      player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.7F, 1.4F);
      this.sendSuccessMessage(player, "You steel yourself for a last vigil — light wraps around you.");
      return true;
   }
}
