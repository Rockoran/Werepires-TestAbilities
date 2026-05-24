package pow.crimson2.abilities.tome;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;

public class EnlightenedEyeTomeAbility extends TomeAbility {
   private static final int NIGHT_VISION_DURATION = 6000;
   private static final int NIGHT_VISION_AMPLIFIER = 0;

   public EnlightenedEyeTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "EnlightenedEye",
         new String[]{"You learn the secret to discern shapes from shadow,", "and gain night vision for 5 minutes."},
         plugin.getConfigManager().getTomeEnlightenedEyeCooldown()
      );
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      } else {
         player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 6000, 0, false, false));
         player.playSound(player.getLocation(), "minecraft:block.beacon.power_select", 1.0F, 1.5F);
         this.sendSuccessMessage(player, "Your eyes adjust to pierce the darkness...");
         player.sendMessage("§7You can now see clearly in the shadows for 5 minutes.");
         return true;
      }
   }
}
