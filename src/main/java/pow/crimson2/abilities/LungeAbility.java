package pow.crimson2.abilities;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public class LungeAbility extends VampireAbility {
   @Override
   public String getName() {
      return "lunge";
   }

   @Override
   public String getDisplayName() {
      return "Vampiric Lunge";
   }

   @Override
   public String getDescription() {
      return "Launch yourself forward and upward with supernatural force. Power increases with vampire stage.";
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getVampireLungeCooldown();
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin, org.bukkit.entity.Player player) {
      int stage = plugin.getVampireManager().getVampireStage(player);
      return plugin.getConfigManager().getVampireLungeCooldownStage(stage);
   }

   @Override
   public int getMinimumStage() {
      return 2;
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      int vampireStage = vampireManager.getVampireStage(player);
      double lungePower = this.getLungePower(vampireStage, plugin);
      vampireManager.addFallProtection(player);
      Vector direction = player.getLocation().getDirection().normalize();
      Vector lungeVector = direction.multiply(lungePower);
      player.setVelocity(lungeVector);
      this.playLungeSound(player, vampireStage);
      return true;
   }

   private double getLungePower(int stage, VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getVampireLungeStrengthStage(stage);
   }

   private void sendLungeMessage(Player player, int stage) {
      player.sendMessage("§c§lYou leap forward with vampiric strength.");
   }

   private void playLungeSound(Player player, int stage) {
      player.playSound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.MASTER, 0.3F, 1.5F);
   }
}
