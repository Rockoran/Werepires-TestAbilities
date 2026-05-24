package pow.crimson2.abilities;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public class VampireVisionAbility extends VampireAbility {
   @Override
   public String getName() {
      return "vision";
   }

   @Override
   public String getDisplayName() {
      return "Vampire Vision";
   }

   @Override
   public String getDescription() {
      return "Toggle supernatural night vision on and off.";
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getVampireVisionCooldown();
   }

   @Override
   public int getMinimumStage() {
      return 1;
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      boolean hasNightVision = player.hasPotionEffect(PotionEffectType.NIGHT_VISION);
      if (hasNightVision) {
         player.removePotionEffect(PotionEffectType.NIGHT_VISION);
         player.sendMessage("§8Your supernatural vision fades... The world returns to natural darkness.");
      } else {
         PotionEffect nightVision = new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false, false);
         player.addPotionEffect(nightVision);
         player.sendMessage("§5Your vampiric eyes pierce through the darkness...");
      }

      player.playSound(player.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 0.5F, 1.0F);
      return true;
   }
}
