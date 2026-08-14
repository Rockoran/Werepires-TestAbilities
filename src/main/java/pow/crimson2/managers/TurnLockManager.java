package pow.crimson2.managers;

import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

/**
 * Admin-imposed locks on turning, kept entirely in scoreboard tags so they survive
 * restarts without a data file of their own.
 *
 * <p>These are deliberately separate from {@link VampireTurningManager}, which owns the
 * vampire's *own* opt-in toggle ({@code turning_disabled}). A player may have their personal
 * toggle on while an admin lock still forbids the turn; the lock always wins.
 *
 * <p>Four independent tags, one per (species, direction) pair, so an admin can e.g. stop a
 * player from siring vampires while still letting the pack infect them.
 */
public class TurnLockManager {

   /** Cannot turn others into vampires. */
   public static final String NO_VAMPIRE_TURN = "no_vampire_turn";
   /** Cannot be turned into a vampire. */
   public static final String NO_VAMPIRE_TURNED = "no_vampire_turned";
   /** Cannot infect others with lycanthropy. */
   public static final String NO_WEREWOLF_TURN = "no_werewolf_turn";
   /** Cannot be infected with lycanthropy. */
   public static final String NO_WEREWOLF_TURNED = "no_werewolf_turned";

   private final VampireSMPPlugin plugin;

   public TurnLockManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public enum Species {
      VAMPIRE("vampire", NO_VAMPIRE_TURN, NO_VAMPIRE_TURNED),
      WEREWOLF("werewolf", NO_WEREWOLF_TURN, NO_WEREWOLF_TURNED);

      private final String label;
      private final String turnTag;
      private final String turnedTag;

      Species(String label, String turnTag, String turnedTag) {
         this.label = label;
         this.turnTag = turnTag;
         this.turnedTag = turnedTag;
      }

      public String getLabel() {
         return this.label;
      }

      public String getTurnTag() {
         return this.turnTag;
      }

      public String getTurnedTag() {
         return this.turnedTag;
      }

      public static Species fromString(String raw) {
         if (raw == null) return null;
         for (Species s : values()) {
            if (s.label.equalsIgnoreCase(raw)) return s;
         }
         return null;
      }
   }

   // ---------------------------------------------------------------- queries

   /** True if {@code player} is allowed to turn others into {@code species}. */
   public boolean canTurn(Player player, Species species) {
      return player != null && !player.getScoreboardTags().contains(species.getTurnTag());
   }

   /** True if {@code player} is allowed to be turned into {@code species}. */
   public boolean canBeTurned(Player player, Species species) {
      return player != null && !player.getScoreboardTags().contains(species.getTurnedTag());
   }

   /**
    * True if an admin lock forbids this specific turn, on either side.
    * Callers should treat this the same as the turner having turning switched off.
    */
   public boolean isTurnBlocked(Player turner, Player target, Species species) {
      return !this.canTurn(turner, species) || !this.canBeTurned(target, species);
   }

   /** Which side of the block is responsible — used purely for admin-facing messaging. */
   public String describeBlock(Player turner, Player target, Species species) {
      if (!this.canTurn(turner, species)) {
         return turner.getName() + " is barred from turning others into " + species.getLabel() + "s";
      }
      if (!this.canBeTurned(target, species)) {
         return target.getName() + " is barred from being turned into a " + species.getLabel();
      }
      return "no lock applies";
   }

   // ---------------------------------------------------------------- mutation

   public void setCanTurn(Player player, Species species, boolean allowed) {
      if (allowed) {
         player.removeScoreboardTag(species.getTurnTag());
      } else {
         player.addScoreboardTag(species.getTurnTag());
      }
      this.plugin.logInfo("TurnLockManager: " + player.getName() + " canTurn(" + species.getLabel() + ") = " + allowed);
   }

   public void setCanBeTurned(Player player, Species species, boolean allowed) {
      if (allowed) {
         player.removeScoreboardTag(species.getTurnedTag());
      } else {
         player.addScoreboardTag(species.getTurnedTag());
      }
      this.plugin.logInfo("TurnLockManager: " + player.getName() + " canBeTurned(" + species.getLabel() + ") = " + allowed);
   }

   /** Drops every lock on a player — used by resetplayer / init. */
   public void clearAll(Player player) {
      for (Species s : Species.values()) {
         player.removeScoreboardTag(s.getTurnTag());
         player.removeScoreboardTag(s.getTurnedTag());
      }
   }
}
