package pow.crimson2.listeners;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Team;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.abilities.tome.TurnUndeadTomeAbility;
import pow.crimson2.managers.BeetrootManager;
import pow.crimson2.managers.EffectManager;
import pow.crimson2.managers.VampireManager;

public class PlayerJoinListener implements Listener {
   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private final EffectManager effectManager;
   private final BeetrootManager beetrootManager;

   public PlayerJoinListener(VampireSMPPlugin plugin, VampireManager vampireManager, EffectManager effectManager) {
      this.plugin = plugin;
      this.vampireManager = vampireManager;
      this.effectManager = effectManager;
      this.beetrootManager = plugin.getBeetrootManager();
   }

   @EventHandler
   public void onPreLogin(AsyncPlayerPreLoginEvent event) {
      if (this.plugin.getNetwork() != null && this.plugin.getNetwork().isSuspended()) {
         event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                 "§4§l[WerePires] §cThis server's WerePires license is currently suspended.\n§7Please try again later.");
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();

      // Stage caps live in an in-memory map, so a fae bargain has to be re-applied every login
      // or the bound player would silently regain the ability to change stage after a restart.
      if (this.plugin.getFaeManager() != null) {
         this.plugin.getFaeManager().reassert(player);
      }

      // Send the joining client everyone who is already faded, otherwise they render solid to it.
      if (this.plugin.getFadeManager() != null) {
         this.plugin.getFadeManager().onJoin(player);
      }

      // If the player spawned in the wrong world (e.g. vanilla overworld while a
      // loaded world pack is active as a named dimension), teleport them to the
      // active world's spawn before any other logic runs.
      org.bukkit.World activeWorld = this.plugin.getWorldManager() != null
            ? this.plugin.getWorldManager().getActiveWorld() : null;
      if (activeWorld != null && !player.getWorld().equals(activeWorld)) {
         player.teleport(activeWorld.getSpawnLocation());
      }

      // Verify the player is running the compatibility mod (kick a few seconds in if not).
      if (this.plugin.getModGateManager() != null) {
         this.plugin.getModGateManager().scheduleCheck(player);
      }

      // Load this player's registered stage skins from disk
      if (this.plugin.getSkinShuffleManager() != null) {
         this.plugin.getSkinShuffleManager().loadSkins(player);
      }

      this.addPlayerToCastTeam(player);
      TurnUndeadTomeAbility.cleanupHumanOnVampireCastTeam(this.plugin, player);
      this.vampireManager.initializeNewPlayer(player);
      this.vampireManager.ensureVampireTagConsistency(player);
      this.effectManager.applyJoinEffects(player);
      this.beetrootManager.restorePlayerState(player);
      this.plugin.getBeaconMajorityManager().applyBonusesToPlayer(player);
      if (this.vampireManager.isWerewolf(player) && this.plugin.getWerewolfHungerManager() != null) {
         this.plugin.getWerewolfHungerManager().restoreXpBar(player);
      }

      if (this.plugin.getVampireTexturePackManager() != null) {
         // Push the base server pack to everyone on join (covers players who haven't loaded it yet).
         this.plugin.getVampireTexturePackManager().onPlayerLogin(player);
         // Vampires get the vampire pack layered on top a moment later.
         if (this.vampireManager.isVampire(player) || player.getScoreboardTags().contains("CuredVampire")) {
            this.plugin.getVampireTexturePackManager().onVampireLogin(player);
         }
      }

      // Re-apply the correct vampire-stage skin for returning vampires
      if (this.plugin.getSkinShuffleManager() != null && this.vampireManager.isVampire(player)) {
         this.plugin.getSkinShuffleManager().applyExistingVampireSkin(player);
      }

      // Send the SkinShuffle handshake (suppresses the mod's reconnect prompt) and re-assert
      // the stage skin a little later, once the client's plugin-channel registration has
      // arrived — messages sent at join time can be dropped before the channel is ready.
      if (this.plugin.getSkinShuffleManager() != null) {
         org.bukkit.Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            this.plugin.getSkinShuffleManager().sendHandshake(player);
            if (this.vampireManager.isVampire(player)) {
               this.plugin.getSkinShuffleManager().applyExistingVampireSkin(player);
            }
         }, 40L);
      }

      if (this.plugin.getBloodMoonAttributeListener() != null) {
         this.plugin.getBloodMoonAttributeListener().forceCleanupOnJoin(player);
      }

      // Apply blood moon UNLUCK immediately on join so mid-moon joiners don't miss the buff.
      if (this.plugin.getBloodMoonManager() != null && this.plugin.getBloodMoonManager().isActive()) {
         boolean eligible = this.vampireManager.isVampireStage2(player) || this.vampireManager.isVampireStage3(player);
         if (eligible) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.UNLUCK, 240, 0, false, false), false);
         }
      }

      String sessionStatus = this.getSessionStatusMessage();
      player.sendMessage("§7" + sessionStatus);
      if (!this.plugin.getSessionManager().playerReturningToGame(player)) {
         player.sendMessage("§cA new game has been initialized since you last played. Resetting your stats accordingly.");
         this.plugin.getSessionManager().resetPlayer(player);
         this.plugin
            .getSessionManager()
            .getGameIDObjective()
            .getScore(player.getName())
            .setScore(this.plugin.getSessionManager().getGameIDObjective().getScore("game_id_holder").getScore());
         this.plugin
            .getSessionManager()
            .getSessionIDObjective()
            .getScore(player.getName())
            .setScore(this.plugin.getSessionManager().getSessionIDObjective().getScore("session_id_holder").getScore());
         this.plugin.getVampireManager().clearPromotionBan(player);
      } else if (this.plugin.getSessionManager().playerReturningToSession(player)) {
         player.sendMessage("You have rejoined a session");
      } else {
         player.sendMessage("The previous session you were in has passed, resetting your stats accordingly");
         this.plugin.getSessionManager().resetPlayer(player);
         this.plugin
            .getSessionManager()
            .getSessionIDObjective()
            .getScore(player.getName())
            .setScore(this.plugin.getSessionManager().getSessionIDObjective().getScore("session_id_holder").getScore());
         this.plugin.getVampireManager().clearPromotionBan(player);
      }

      this.plugin.getPlayerChatManager().removePlayersPendingMessages(player);
      if (player.isOp() || player.hasPermission("vampiresmp.admin")) {
         List<String> invariants = this.plugin.getConfigManager().validateGameplayInvariants();
         if (!invariants.isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§c§l[CONFIG WARNING] §eThese settings cancel each other out:");
            for (String w : invariants) player.sendMessage("§c  - " + w);
         }

         List<String> warnings = this.plugin.getConfigManager().validateConfiguredLocations(this.plugin.getBeaconManager());
         if (!warnings.isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§c§l[CONFIG WARNING] §eThe following locations are outside the border:");

            for (String warning : warnings) {
               player.sendMessage("§c  - " + warning);
            }

            player.sendMessage(
               "§7Border: X["
                  + (int)this.plugin.getConfigManager().getOakhurstMinX()
                  + " to "
                  + (int)this.plugin.getConfigManager().getOakhurstMaxX()
                  + "] Z["
                  + (int)this.plugin.getConfigManager().getOakhurstMinZ()
                  + " to "
                  + (int)this.plugin.getConfigManager().getOakhurstMaxZ()
                  + "]"
            );
            player.sendMessage("§7Check config.yml oakhurst.border settings.");
            player.sendMessage("");
         }
      }

      event.setJoinMessage(null);

      // Network sync — report player join + current role
      if (this.plugin.getNetwork() != null && this.plugin.getNetwork().isEnabled()) {
         String role = this.vampireManager.isVampire(player) ? "vampire"
                     : this.vampireManager.isWerewolf(player) ? "werewolf"
                     : "human";
         boolean isOp = player.isOp();
         this.plugin.getNetwork().logEvent("player_join", player.getUniqueId().toString(), player.getName(),
                 "role=" + role + ",op=" + isOp);
         this.plugin.getNetwork().updatePlayer(player.getUniqueId().toString(), player.getName(), role, "op=" + isOp);
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();

      // Clear their fade so remaining viewers stop drawing a player who is no longer here.
      if (this.plugin.getFadeManager() != null) {
         this.plugin.getFadeManager().onQuit(player);
      }

      for (Player potentialOp : this.plugin.getWorld().getPlayers()) {
         if (potentialOp.hasPermission("vampiresmp.admin")) {
            if (this.plugin.getSessionManager().isSessionActive()) {
               potentialOp.sendMessage(
                  "Note: " + event.getPlayer().getName() + " has left during an active session. Consider pausing the session if this was unintended."
               );
            } else {
               potentialOp.sendMessage("Note: " + event.getPlayer().getName() + " has left. Perhaps do not start/resume session until they return.");
            }
         }
      }

      this.plugin.getPlayerChatManager().removePlayersPendingMessages(player);
      this.plugin.getBeaconMajorityManager().removeBonusesFromPlayer(player);
      if (this.plugin.getVampireTexturePackManager() != null) {
         this.plugin.getVampireTexturePackManager().onPlayerQuit(player);
      }

      if (this.plugin.getSkinShuffleManager() != null) {
         this.plugin.getSkinShuffleManager().clearCache(player);
      }

      if (this.plugin.getModGateManager() != null) {
         this.plugin.getModGateManager().clear(player);
      }

      event.setQuitMessage(null);
   }

   private void addPlayerToCastTeam(Player player) {
      try {
         Team teamToJoin;
         if (this.plugin.getVampireManager().isIronAffected(player)) {
            teamToJoin = this.plugin.getVampireCastTeam();
         } else {
            teamToJoin = this.plugin.getCastTeam();
         }

         if (teamToJoin != null) {
            if (!teamToJoin.hasPlayer(player)) {
               teamToJoin.addPlayer(player);
               this.plugin.logInfo("Added player " + player.getName() + " to CastTeam");
            } else {
               this.plugin.logInfo("Player " + player.getName() + " is already on CastTeam");
            }
         } else {
            this.plugin.getLogger().warning("CastTeam is null - cannot add player " + player.getName());
         }
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to add player " + player.getName() + " to CastTeam: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private String getSessionStatusMessage() {
      int sessionState = this.plugin.getSessionManager().getSessionState();
      switch (sessionState) {
         case 0:
            return "The server is currently out of session. PvP, block breaking, and time are disabled.";
         case 1:
            return "A session is currently active! Be careful out there.";
         case 2:
            return "The session is currently paused. PvP, block breaking, and time are disabled.";
         default:
            return "Session status unknown.";
      }
   }
}
