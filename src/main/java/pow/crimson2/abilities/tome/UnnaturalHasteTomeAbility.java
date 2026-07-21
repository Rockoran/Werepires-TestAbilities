package pow.crimson2.abilities.tome;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;

public class UnnaturalHasteTomeAbility extends TomeAbility {
   private final int HASTE_DURATION;
   private final int HASTE_AMPLIFIER;

   public UnnaturalHasteTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "UnnaturalHaste",
         new String[]{"You learn how to dip into a pool of strength unknown to this world,", "and gain haste for 5 minutes."},
         plugin.getConfigManager().getTomeUnnaturalHasteCooldown()
      );
      this.HASTE_DURATION = plugin.getConfig().getInt("abilities.tome.unnaturalhaste.haste-duration-ticks", 6000);
      this.HASTE_AMPLIFIER = plugin.getConfig().getInt("abilities.tome.unnaturalhaste.haste-amplifier", 1);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      } else {
         player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, this.HASTE_DURATION, this.HASTE_AMPLIFIER, false, false));
         this.plugin.getWorld().playSound(player.getLocation(), "minecraft:block.portal.ambient", 1.0F, 1.8F);
         this.sendSuccessMessage(player, "You tap into an otherworldly pool of energy...");
         player.sendMessage("§7Your hands move with unnatural speed for 5 minutes.");
         return true;
      }
   }
}
