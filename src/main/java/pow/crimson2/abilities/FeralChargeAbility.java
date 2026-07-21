package pow.crimson2.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public class FeralChargeAbility extends WerewolfAbility {

   @Override
   public String getName() {
      return "feralcharge";
   }

   @Override
   public String getDisplayName() {
      return "Feral Charge";
   }

   @Override
   public String getDescription() {
      return "Unleash a burst of primal speed, launching yourself forward. Power increases with stage.";
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getWerewolfFeralChargeCooldown();
   }

   @Override
   public int getMinimumStage() {
      return 2;
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      int stage = vampireManager.getWerewolfStage(player);
      double power = (stage >= 3)
         ? plugin.getConfigManager().getWerewolfFeralChargePowerStage3()
         : plugin.getConfigManager().getWerewolfFeralChargePowerStage2();

      Vector direction = player.getLocation().getDirection().normalize();
      direction.multiply(power);
      direction.setY(direction.getY() + 0.2);
      player.setVelocity(direction);

      player.getWorld().spawnParticle(
         Particle.SWEEP_ATTACK,
         player.getLocation().add(0, 1.0, 0),
         8, 0.5, 0.3, 0.5, 0.0
      );
      player.getWorld().playSound(
         player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.PLAYERS, 0.8F, 1.1F
      );
      player.sendMessage("§6§lFERAL CHARGE!");
      return true;
   }
}
