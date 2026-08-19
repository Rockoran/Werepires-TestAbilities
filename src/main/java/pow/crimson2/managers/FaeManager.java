package pow.crimson2.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

/**
 * The fae and their bargains.
 *
 * <p>A fae is just a tagged player with access to {@code /pow faedeal}. The interesting part is
 * the deal: a fae can turn a human into a vampire locked at a chosen stage. That vampire cannot
 * climb or fall from the stage, can be staked at it, and reverts to human if the fae who made
 * the bargain dies.
 *
 * <p>State is persisted to {@code fae_data.json} because it has to outlive a restart —
 * {@link VampireManager}'s stage caps are an in-memory map only, so the lock is re-asserted from
 * here on join.
 */
public class FaeManager {

   public static final String FAE_TAG = "fae";
   public static final String FAE_DEAL_TAG = "fae_deal";

   private final VampireSMPPlugin plugin;
   private final File dataFile;
   private final Gson gson;

   private final Set<UUID> faes = new HashSet<>();
   /** keyed by the turned player */
   private final Map<UUID, Deal> deals = new HashMap<>();

   public FaeManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.dataFile = new File(plugin.getDataFolder(), "fae_data.json");
      this.gson = new GsonBuilder().setPrettyPrinting().create();
      this.load();
   }

   /** A single bargain: who made it, and at what stage the target is pinned. */
   public static class Deal {
      public String faeId;
      public String faeName;
      public int stage;
      public boolean breakOnFaePermadeath;

      public Deal() {
      }

      public Deal(UUID faeId, String faeName, int stage, boolean breakOnFaePermadeath) {
         this.faeId = faeId.toString();
         this.faeName = faeName;
         this.stage = stage;
         this.breakOnFaePermadeath = breakOnFaePermadeath;
      }

      public UUID getFaeId() {
         try {
            return UUID.fromString(this.faeId);
         } catch (IllegalArgumentException e) {
            return null;
         }
      }
   }

   /** On-disk shape. */
   private static class FaeData {
      List<String> faes = new ArrayList<>();
      Map<String, Deal> deals = new HashMap<>();
   }

   // ------------------------------------------------------------ fae membership

   public boolean isFae(Player player) {
      return player != null && (this.faes.contains(player.getUniqueId()) || player.getScoreboardTags().contains(FAE_TAG));
   }

   public void setFae(Player player, boolean fae) {
      if (fae) {
         this.faes.add(player.getUniqueId());
         player.addScoreboardTag(FAE_TAG);
      } else {
         this.faes.remove(player.getUniqueId());
         player.removeScoreboardTag(FAE_TAG);
      }
      this.save();
      this.plugin.logInfo("FaeManager: " + player.getName() + " fae = " + fae);
   }

   public Set<UUID> getFaes() {
      return new HashSet<>(this.faes);
   }

   // ------------------------------------------------------------------- deals

   public boolean hasDeal(Player player) {
      return player != null && this.deals.containsKey(player.getUniqueId());
   }

   public Deal getDeal(Player player) {
      return player == null ? null : this.deals.get(player.getUniqueId());
   }

   /** The stage a fae-deal vampire is pinned to, or -1 if they have no deal. */
   public int getLockedStage(Player player) {
      Deal deal = this.getDeal(player);
      return deal == null ? -1 : deal.stage;
   }

   /** Every player currently bound by a deal from this fae. */
   public List<UUID> getDealTargets(UUID faeId) {
      List<UUID> out = new ArrayList<>();
      for (Map.Entry<UUID, Deal> entry : this.deals.entrySet()) {
         if (faeId.equals(entry.getValue().getFaeId())) {
            out.add(entry.getKey());
         }
      }
      return out;
   }

   /**
    * Strike a bargain: turn {@code target} into a vampire pinned at {@code stage}.
    *
    * @return an error string to show the fae, or null on success.
    */
   public String createDeal(Player fae, Player target, int stage, boolean breakOnFaePermadeath) {
      if (!this.plugin.getConfigManager().isFaeEnabled()) {
         return "The fae system is disabled.";
      }
      if (!this.plugin.getConfigManager().getFaeAllowedStages().contains(stage)) {
         return "Stage " + stage + " is not an allowed fae-deal stage.";
      }
      if (target.equals(fae)) {
         return "You cannot strike a bargain with yourself.";
      }
      if (this.hasDeal(target)) {
         return target.getName() + " is already bound by a fae bargain.";
      }
      if (!this.plugin.getVampireManager().isHuman(target)) {
         return target.getName() + " is not human.";
      }
      if (!this.plugin.getTurnLockManager().canBeTurned(target, TurnLockManager.Species.VAMPIRE)) {
         return target.getName() + " cannot be turned into a vampire.";
      }

      Deal deal = new Deal(fae.getUniqueId(), fae.getName(), stage, breakOnFaePermadeath);
      this.deals.put(target.getUniqueId(), deal);
      this.save();

      this.applyDeal(target, stage);

      this.plugin.logInfo("FaeManager: " + fae.getName() + " bound " + target.getName() + " at stage " + stage);
      return null;
   }

   /** Put (or re-put) a target into their locked vampire state. */
   public void applyDeal(Player target, int stage) {
      VampireManager vampires = this.plugin.getVampireManager();

      // bypassFaeLock, otherwise the guard in setPlayerAsVampire would pin us to the old value
      vampires.setPlayerAsVampire(target, stage, true, true);

      target.addScoreboardTag(FAE_DEAL_TAG);
      vampires.setStageCap(target, stage);
      target.setExp(0.001F);

      if (this.plugin.getConfigManager().isFaeTurnedCannotTurn()) {
         this.plugin.getTurnLockManager().setCanTurn(target, TurnLockManager.Species.VAMPIRE, false);
      }
   }

   /** Re-assert the lock after a restart, since stage caps do not persist. */
   public void reassert(Player player) {
      Deal deal = this.getDeal(player);

      if (deal == null) {
         // Their bargain was broken while they were offline (their fae died). Finish the job now
         // instead of leaving a stale tag and an uncapped vampire behind.
         if (player.getScoreboardTags().contains(FAE_DEAL_TAG)) {
            player.removeScoreboardTag(FAE_DEAL_TAG);
            this.plugin.getVampireManager().clearStageCap(player);
            if (this.plugin.getConfigManager().isFaeTurnedCannotTurn()) {
               this.plugin.getTurnLockManager().setCanTurn(player, TurnLockManager.Species.VAMPIRE, true);
            }
            if (this.plugin.getConfigManager().isFaeRevertToHumanOnBreak()
                  && this.plugin.getVampireManager().isVampire(player)) {
               this.plugin.getVampireManager().setPlayerAsHuman(player);
               if (this.plugin.getConfigManager().isFaeMarkCuredOnBreak()) {
                  player.addScoreboardTag("CuredVampire");
               }
            }
            player.sendMessage("§d§lTHE BARGAIN IS BROKEN");
            player.sendMessage("§7Your fae fell while you were away. The curse no longer binds you.");
            this.plugin.logInfo("FaeManager: cleaned up orphaned bargain for " + player.getName() + " on join");
         }
         return;
      }

      this.plugin.getVampireManager().setStageCap(player, deal.stage);
      player.addScoreboardTag(FAE_DEAL_TAG);
      if (this.plugin.getVampireManager().getVampireStage(player) != deal.stage) {
         this.applyDeal(player, deal.stage);
      } else {
         player.setExp(Math.max(0.001F, player.getExp()));
      }
      if (this.plugin.getConfigManager().isFaeTurnedCannotTurn()) {
         this.plugin.getTurnLockManager().setCanTurn(player, TurnLockManager.Species.VAMPIRE, false);
      }
   }

   /** Drop a single deal and, if configured, hand the player back their humanity. */
   public void breakDeal(Player target, String reason) {
      Deal deal = this.deals.remove(target.getUniqueId());
      if (deal == null) return;
      this.save();

      VampireManager vampires = this.plugin.getVampireManager();
      target.removeScoreboardTag(FAE_DEAL_TAG);
      vampires.clearStageCap(target);
      vampires.clearPromotionBan(target);

      if (this.plugin.getConfigManager().isFaeTurnedCannotTurn()) {
         this.plugin.getTurnLockManager().setCanTurn(target, TurnLockManager.Species.VAMPIRE, true);
      }

      if (this.plugin.getConfigManager().isFaeRevertToHumanOnBreak()) {
         vampires.setPlayerAsHuman(target);
         if (this.plugin.getConfigManager().isFaeMarkCuredOnBreak()) {
            target.addScoreboardTag("CuredVampire");
         }
         target.sendMessage("§d§lTHE BARGAIN IS BROKEN");
         target.sendMessage("§7" + reason);
         target.sendMessage("§aThe curse drains out of you. You are human once more.");
      } else {
         target.sendMessage("§d§lTHE BARGAIN IS BROKEN");
         target.sendMessage("§7" + reason);
         target.sendMessage("§7The curse remains, but it no longer binds you to a stage.");
      }

      this.plugin.logInfo("FaeManager: broke deal for " + target.getName() + " (" + reason + ")");
   }

   /**
    * Drop a bargain without the revert ceremony. Used when the bound player is themselves being
    * permanently killed — they are already leaving play, so telling them they are "human once
    * more" on top of the FINAL DEATH title would just be noise.
    */
   public void removeDealSilently(Player target) {
      if (this.deals.remove(target.getUniqueId()) == null) return;
      this.save();
      target.removeScoreboardTag(FAE_DEAL_TAG);
      this.plugin.getVampireManager().clearStageCap(target);
      this.plugin.logInfo("FaeManager: cleared bargain for " + target.getName() + " (permanent death)");
   }

   /**
    * Break every life-bound bargain made by this fae after their permanent death.
    */
   public void breakDealsBy(UUID faeId, String faeName) {
      List<UUID> targets = new ArrayList<>();
      for (Map.Entry<UUID, Deal> entry : this.deals.entrySet()) {
         if (faeId.equals(entry.getValue().getFaeId()) && entry.getValue().breakOnFaePermadeath) {
            targets.add(entry.getKey());
         }
      }
      if (targets.isEmpty()) return;

      String reason = "Your fae, " + faeName + ", is gone.";
      int broken = 0;
      for (UUID targetId : targets) {
         Player target = Bukkit.getPlayer(targetId);
         if (target != null && target.isOnline()) {
            this.breakDeal(target, reason);
            broken++;
         } else {
            // Offline: drop the record so it cannot be re-asserted, and let join clean up tags.
            this.deals.remove(targetId);
         }
      }
      this.save();

      this.plugin.logInfo("FaeManager: " + faeName + " died; broke " + targets.size() + " bargain(s), " + broken + " applied live");

      if (this.plugin.getConfigManager().isFaeBroadcastOnBreak() && !targets.isEmpty()) {
         Bukkit.broadcastMessage("§d§oA fae bargain unravels. Somewhere, a debt is forgiven.");
      }
   }

   /** Hook for the death pipeline. Returns true if this player's death broke any bargains. */
   public boolean onFaeDeath(Player player, boolean permanent) {
      if (!this.isFae(player)) return false;

      if (!permanent) return false;

      this.breakDealsBy(player.getUniqueId(), player.getName());
      return true;
   }

   // ------------------------------------------------------------- persistence

   private void load() {
      if (!this.dataFile.exists()) {
         this.plugin.logInfo("FaeManager: no existing fae data, starting fresh.");
         return;
      }

      try (FileReader reader = new FileReader(this.dataFile)) {
         Type type = new TypeToken<FaeData>() {}.getType();
         FaeData data = this.gson.fromJson(reader, type);
         if (data != null) {
            if (data.faes != null) {
               for (String raw : data.faes) {
                  try {
                     this.faes.add(UUID.fromString(raw));
                  } catch (IllegalArgumentException e) {
                     this.plugin.getLogger().warning("FaeManager: bad fae uuid in data file: " + raw);
                  }
               }
            }
            if (data.deals != null) {
               for (Map.Entry<String, Deal> entry : data.deals.entrySet()) {
                  try {
                     this.deals.put(UUID.fromString(entry.getKey()), entry.getValue());
                  } catch (IllegalArgumentException e) {
                     this.plugin.getLogger().warning("FaeManager: bad deal uuid in data file: " + entry.getKey());
                  }
               }
            }
            this.plugin.logInfo("FaeManager: loaded " + this.faes.size() + " fae(s) and " + this.deals.size() + " bargain(s)");
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("FaeManager: failed to load fae data: " + e.getMessage());
      }
   }

   private void save() {
      try {
         if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
         }

         FaeData data = new FaeData();
         for (UUID id : this.faes) {
            data.faes.add(id.toString());
         }
         for (Map.Entry<UUID, Deal> entry : this.deals.entrySet()) {
            data.deals.put(entry.getKey().toString(), entry.getValue());
         }

         try (FileWriter writer = new FileWriter(this.dataFile)) {
            this.gson.toJson(data, writer);
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("FaeManager: failed to save fae data: " + e.getMessage());
      }
   }

   public void clearAll() {
      this.faes.clear();
      this.deals.clear();
      this.save();
      this.plugin.logInfo("FaeManager: cleared all fae data");
   }

   public void shutdown() {
      this.save();
      this.plugin.logInfo("FaeManager: shutdown complete");
   }
}
