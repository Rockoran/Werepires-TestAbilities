package pow.crimson2.abilities.tome;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;

public class RallyingCryTomeAbility extends TomeAbility {
   private final double EFFECT_RADIUS;
   private final int STRENGTH_DURATION;
   private final int STRENGTH_AMPLIFIER;

   public RallyingCryTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "RallyingCry",
         new String[]{"You learn the secrets needed to inspire man.", "At your word, you and humans around you", "gain strength for 30 seconds."},
         plugin.getConfigManager().getTomeRallyingCryCooldown()
      );
      this.EFFECT_RADIUS = plugin.getConfig().getDouble("abilities.tome.rallyingcry.radius", 20.0);
      this.STRENGTH_DURATION = plugin.getConfig().getInt("abilities.tome.rallyingcry.strength-duration-ticks", 600);
      this.STRENGTH_AMPLIFIER = plugin.getConfig().getInt("abilities.tome.rallyingcry.strength-amplifier", 0);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }

      int affectedCount = 0;
      player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, this.STRENGTH_DURATION, this.STRENGTH_AMPLIFIER, false, false));
      affectedCount++;
      List<Player> nearbyHumans = player.getNearbyEntities(this.EFFECT_RADIUS, this.EFFECT_RADIUS, this.EFFECT_RADIUS)
         .stream()
         .filter(entity -> entity instanceof Player)
         .map(entity -> (Player)entity)
         .filter(nearbyPlayer -> this.plugin.getVampireManager().isHuman(nearbyPlayer))
         .toList();
      List<Player> nearbyVampires = player.getNearbyEntities(this.EFFECT_RADIUS, this.EFFECT_RADIUS, this.EFFECT_RADIUS)
         .stream()
         .filter(entity -> entity instanceof Player)
         .map(entity -> (Player)entity)
         .filter(nearbyPlayer -> !this.plugin.getVampireManager().isHuman(nearbyPlayer))
         .toList();

      for (Player nearbyHuman : nearbyHumans) {
         nearbyHuman.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, this.STRENGTH_DURATION, this.STRENGTH_AMPLIFIER, false, false));
         nearbyHuman.sendMessage("§6" + player.getName() + "'s rallying cry fills you with strength.");
         affectedCount++;
      }

      for (Player nearbyVampire : nearbyVampires) {
         nearbyVampire.sendMessage("§7A human nearby rallies strength to their comrades. The words find no purchase in your cold dead heart.");
      }

      this.plugin.getWorld().playSound(player.getLocation(), "minecraft:entity.pillager.celebrate", 1.0F, 1.0F);
      this.sendSuccessMessage(player, "Your rallying cry inspires those around you!");
      return true;
   }
}
