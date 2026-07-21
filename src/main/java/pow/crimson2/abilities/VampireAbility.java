package pow.crimson2.abilities;

import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public abstract class VampireAbility {
   public abstract String getName();

   public abstract String getDisplayName();

   public abstract String getDescription();

   public abstract int getCooldownSeconds(VampireSMPPlugin var1);

   /** Stage-aware override — abilities that have per-stage cooldowns override this. */
   public int getCooldownSeconds(VampireSMPPlugin plugin, Player player) {
      return getCooldownSeconds(plugin);
   }

   public abstract int getMinimumStage();

   /**
    * Whether this ability is enabled on the server. New abilities override this to read a
    * config toggle; the built-in ones are always enabled. Checked before use so abilities can
    * be disabled live via config without removing them.
    */
   public boolean isEnabled(VampireSMPPlugin plugin) {
      return true;
   }

   public boolean canUse(Player player, VampireManager vampireManager) {
      if (!vampireManager.isVampire(player)) {
         return false;
      }

      int playerStage = vampireManager.getVampireStage(player);
      return playerStage < this.getMinimumStage() ? false : this.canUseAdditionalRequirements(player, vampireManager);
   }

   protected boolean canUseAdditionalRequirements(Player player, VampireManager vampireManager) {
      return true;
   }

   public String getRequirementMessage(Player player, VampireManager vampireManager) {
      if (!vampireManager.isVampire(player)) {
         return "You must be a vampire to use this ability!";
      }

      int playerStage = vampireManager.getVampireStage(player);
      return playerStage < this.getMinimumStage()
         ? "You must be at least a Stage " + this.getMinimumStage() + " vampire to use " + this.getDisplayName() + "! (You are Stage " + playerStage + ")"
         : this.getAdditionalRequirementMessage(player, vampireManager);
   }

   protected String getAdditionalRequirementMessage(Player player, VampireManager vampireManager) {
      return "You cannot use this ability right now!";
   }

   public abstract boolean execute(Player var1, VampireManager var2, VampireSMPPlugin var3);
}
