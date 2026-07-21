package pow.crimson2.abilities;

import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public abstract class WerewolfAbility {

   public abstract String getName();

   public abstract String getDisplayName();

   public abstract String getDescription();

   public abstract int getCooldownSeconds(VampireSMPPlugin plugin);

   public abstract int getMinimumStage();

   public boolean canUse(Player player, VampireManager vampireManager) {
      if (!vampireManager.isWerewolf(player)) {
         return false;
      }

      int stage = vampireManager.getWerewolfStage(player);
      return stage >= this.getMinimumStage() && this.canUseAdditionalRequirements(player, vampireManager);
   }

   protected boolean canUseAdditionalRequirements(Player player, VampireManager vampireManager) {
      return true;
   }

   public String getRequirementMessage(Player player, VampireManager vampireManager) {
      if (!vampireManager.isWerewolf(player)) {
         return "You must be a werewolf to use this ability!";
      }

      int stage = vampireManager.getWerewolfStage(player);
      if (stage < this.getMinimumStage()) {
         return "You must be at least Stage " + this.getMinimumStage() + " to use " + this.getDisplayName() + "! (You are Stage " + stage + ")";
      }

      return this.getAdditionalRequirementMessage(player, vampireManager);
   }

   protected String getAdditionalRequirementMessage(Player player, VampireManager vampireManager) {
      return "You cannot use this ability right now!";
   }

   public abstract boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin);
}
