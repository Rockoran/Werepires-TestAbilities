package pow.crimson2.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;
import pow.crimson2.managers.VampireManager;

public class DeathHandler implements Listener {
   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private final Map<UUID, Material> lastWeaponUsed = new HashMap<>();
   private final Map<UUID, UUID> woodenStakeKills = new HashMap<>();

   public DeathHandler(VampireSMPPlugin plugin, VampireManager vampireManager) {
      this.plugin = plugin;
      this.vampireManager = vampireManager;
   }

   public void registerWoodenStakeKill(Player victim, Player killer) {
      this.woodenStakeKills.put(victim.getUniqueId(), killer.getUniqueId());
      this.plugin.logInfo("DEBUG: Registered wooden stake kill - Victim: " + victim.getName() + ", Killer: " + killer.getName());
   }

   @EventHandler
   public void onPlayerPostRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      boolean wasVampire = this.vampireManager.isVampire(player);
      boolean wasHuman = this.vampireManager.isHuman(player);

      // ── Force respawn into the currently-active world ──────────────────────
      // Without this, vanilla uses a cached bed/anchor that may still point to
      // a world that was swapped out, sending the player to the wrong map.
      if (this.vampireManager.isVampire(player)) {
         Location cryptLoc = this.plugin.getVampireRespawnLocation();
         if (cryptLoc != null && cryptLoc.getWorld() != null) {
            event.setRespawnLocation(cryptLoc);
         }
      } else {
         // For humans: honour a bed/anchor only if it's already in the active world.
         // If it's stale (from a previous map), fall back to the active world's spawn.
         org.bukkit.World activeWorld = this.plugin.getWorldManager().getActiveWorld();
         if (activeWorld != null) {
            boolean validBedOrAnchor = (event.isBedSpawn() || event.isAnchorSpawn())
                  && event.getRespawnLocation() != null
                  && activeWorld.equals(event.getRespawnLocation().getWorld());
            if (!validBedOrAnchor) {
               event.setRespawnLocation(activeWorld.getSpawnLocation());
            }
         }
      }
      // ─────────────────────────────────────────────────────────────────────
      if (this.vampireManager.isVampire(player) && player.getScoreboardTags().contains("PermaKilled")) {
         this.vampireManager.killVampirePermanently(player);
         player.removeScoreboardTag("PermaKilled");
      } else if (this.vampireManager.isHuman(player) && player.getScoreboardTags().contains("PermadeathChosen")) {
         this.vampireManager.killVampirePermanently(player);
         player.removeScoreboardTag("PermadeathChosen");
         this.broadcastHumanPermadeath(player);
      } else if (this.vampireManager.isVampire(player) && player.getScoreboardTags().contains("PromotionBanPending")) {
         this.vampireManager.applyPromotionBan(player);
         player.removeScoreboardTag("PromotionBanPending");
      }

      if (this.vampireManager.isWerewolf(player) && player.getScoreboardTags().contains("WerewolfDeathPenaltyPending")) {
         this.vampireManager.applyWerewolfDeathPenalty(player);
         player.removeScoreboardTag("WerewolfDeathPenaltyPending");
      }

      if (this.vampireManager.isHuman(player)) {
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            try {
               Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
               Objective deathObjective = mainScoreboard.getObjective("vsmp_death");
               if (deathObjective != null) {
                  int currentDeaths = deathObjective.getScore(player.getName()).getScore();
                  int deathCap = this.plugin.getConfigManager().getHumanDeathScoreCap();
                  if (currentDeaths > deathCap) {
                     deathObjective.getScore(player.getName()).setScore(deathCap);
                     this.plugin.logInfo("Capped death count for " + player.getName() + " at " + deathCap + " (was " + currentDeaths + ")");
                  }
               }
            } catch (Exception e) {
               this.plugin.getLogger().warning("Failed to cap death count for " + player.getName() + ": " + e.getMessage());
            }
         });
      }

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (this.plugin.getBeaconMajorityManager() != null) {
            this.plugin.getBeaconMajorityManager().updateBeaconMajorityBonuses();
         }

         double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
         player.setHealth(maxHealth);
         this.plugin.getLogger().fine(player.getName() + " respawned with " + maxHealth + " HP (full health)");
      }, 5L);
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> checkAndAnnounceTeamElimination(this.plugin, wasHuman, wasVampire), 10L);
   }

   public static void checkAndAnnounceTeamElimination(VampireSMPPlugin plugin, boolean affectedWasHuman, boolean affectedWasVampire) {
      if (plugin.getSessionManager().isSessionActive()) {
         VampireManager vampireManager = plugin.getVampireManager();
         int aliveHumans = 0;
         int aliveVampires = 0;

         for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getGameMode() == GameMode.SURVIVAL) {
               if (vampireManager.isHuman(onlinePlayer)) {
                  aliveHumans++;
               } else if (vampireManager.isVampire(onlinePlayer)) {
                  aliveVampires++;
               }
            }
         }

         if (aliveHumans == 0 && affectedWasHuman) {
            announceAllHumansDeadStatic(plugin);
         } else if (aliveVampires == 0 && affectedWasVampire) {
            announceHumansWinStatic(plugin);
         }
      }
   }

   public static void checkAndAnnounceTeamElimination(VampireSMPPlugin plugin) {
      checkAndAnnounceTeamElimination(plugin, false, false);
   }

   private static void announceAllHumansDeadStatic(VampireSMPPlugin plugin) {
      plugin.logInfo("ALL HUMANS ELIMINATED");
      int totalBeacons = plugin.getBeaconManager().getAllBeacons().size();
      int evilBeacons = plugin.getBeaconManager().getAllEvilBeacons().size();
      boolean allBeaconsDesecrated = totalBeacons > 0 && evilBeacons == totalBeacons;

      for (Player player : Bukkit.getOnlinePlayers()) {
         player.sendTitle("§cThe last human has fallen.", "", 20, 100, 40);
         player.sendMessage("");
         player.sendMessage("§cThe last defender of humanity has fallen...");
         if (allBeaconsDesecrated) {
            player.sendMessage("§cDarkness reigns supreme over " + plugin.getConfigManager().getOakhurstName() + ". You are free.");
         } else {
            player.sendMessage("§cNow only the beacons lie between you and freedom.");
         }

         player.sendMessage("");
         player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 1.0F, 0.5F);
      }
   }

   private static void announceHumansWinStatic(VampireSMPPlugin plugin) {
      plugin.logInfo("ALL VAMPIRES ELIMINATED");
      int totalBeacons = plugin.getBeaconManager().getAllBeacons().size();
      int holyBeacons = plugin.getBeaconManager().getHolyBeacons().size();
      boolean allBeaconsHoly = totalBeacons > 0 && holyBeacons == totalBeacons;
      boolean anyPermanentlyCorrupted = plugin.getBeaconManager()
         .getAllBeacons()
         .stream()
         .anyMatch(beacon -> beacon.getState() == BeaconSite.BeaconState.PERMANENTLY_DESECRATED);

      for (Player player : Bukkit.getOnlinePlayers()) {
         player.sendTitle("§aThe last vampire has fallen.", "", 20, 100, 40);
         player.sendMessage("");
         player.sendMessage("§aThe last creature of darkness has fallen...");
         if (anyPermanentlyCorrupted) {
            player.sendMessage("§7But a beacon of light has been permanently corrupted.");
            player.sendMessage("§7The creatures of the night have been vanquished, but does freedom await you?");
         } else if (allBeaconsHoly) {
            player.sendMessage("§aLight reigns supreme over " + plugin.getConfigManager().getOakhurstName() + ". You are free.");
         } else {
            player.sendMessage("§7Now only the beacons lie between you and freedom.");
         }

         player.sendMessage("");
         player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.0F, 1.0F);
      }
   }

   @EventHandler
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
         Player victim = (Player)event.getEntity();
         Player attacker = (Player)event.getDamager();
         ItemStack weapon = attacker.getInventory().getItemInMainHand();
         if (weapon != null) {
            this.lastWeaponUsed.put(victim.getUniqueId(), weapon.getType());
         }
      }
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player victim = event.getEntity();
      Player killer = victim.getKiller();

      if (victim.getScoreboardTags().contains("CommandPermakillPending")) {
         String kind = this.vampireManager.isVampire(victim) ? "vampire" : "human";
         event.setDeathMessage("§4" + victim.getName() + " chose permanent death as a " + kind + ".");
         victim.removeScoreboardTag("CommandPermakillPending");
      }

      // Ordinary death never breaks a bargain. Life-bound bargains are handled by the permanent
      // death pipeline so staking and command permakills cannot bypass the rule.
      if (this.plugin.getFaeManager() != null) {
         this.plugin.getFaeManager().onFaeDeath(victim, false);
      }

      // Dying snaps you back to fully visible — a corpse should not stay half-faded.
      if (this.plugin.getFadeManager() != null) {
         this.plugin.getFadeManager().reset(victim);
      }
      UUID trackedKillerUUID = this.woodenStakeKills.remove(victim.getUniqueId());
      if (trackedKillerUUID != null && killer == null) {
         Player trackedKiller = this.plugin.getServer().getPlayer(trackedKillerUUID);
         if (trackedKiller != null) {
            killer = trackedKiller;
            this.plugin.logInfo("DEBUG: Retrieved tracked wooden stake killer: " + killer.getName() + " for victim: " + victim.getName());
         }
      }

      if (this.vampireManager.isHuman(victim)) {
         try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Objective deathObjective = mainScoreboard.getObjective("vsmp_death");
            if (deathObjective != null) {
               int currentDeaths = deathObjective.getScore(victim.getName()).getScore();
               deathObjective.getScore(victim.getName()).setScore(currentDeaths + 1);
               this.plugin.logInfo("Incremented death count for " + victim.getName() + " to " + (currentDeaths + 1));
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to increment death count for " + victim.getName() + ": " + e.getMessage());
         }
      }

      if (this.vampireManager.isWerewolf(victim)) {
         victim.addScoreboardTag("WerewolfDeathPenaltyPending");
         this.plugin.logInfo("WEREWOLF DEATH PENALTY: Applied WerewolfDeathPenaltyPending tag to " + victim.getName());
      }

      if (killer != null) {
         this.handlePvPDeath(victim, killer, event);
      } else if (this.vampireManager.isVampire(victim)) {
         victim.addScoreboardTag("PromotionBanPending");
      }
   }

   private void handlePvPDeath(Player victim, Player killer, PlayerDeathEvent event) {
      ItemStack weapon = killer.getInventory().getItemInMainHand();
      boolean killedWithWoodenWeapon = this.isWoodenWeapon(weapon);
      Material lastWeapon = this.lastWeaponUsed.get(victim.getUniqueId());
      if (!killedWithWoodenWeapon && lastWeapon != null) {
         killedWithWoodenWeapon = lastWeapon == Material.WOODEN_SWORD || lastWeapon == Material.WOODEN_AXE;
         if (killedWithWoodenWeapon) {
            this.plugin.logInfo("DEBUG: Using tracked last weapon: " + lastWeapon + " (current weapon broke/dropped)");
         }
      }

      this.plugin
         .logInfo(
            "DEBUG: PvP Death - Victim: "
               + victim.getName()
               + ", CurrentWeapon: "
               + (weapon != null ? weapon.getType() : "null")
               + ", LastTrackedWeapon: "
               + lastWeapon
               + ", IsWoodenWeapon: "
               + killedWithWoodenWeapon
               + ", IsVampire: "
               + this.vampireManager.isVampire(victim)
               + ", IsStage1: "
               + this.vampireManager.isVampireStage1(victim)
               + ", VictimTags: "
               + victim.getScoreboardTags()
         );
      this.lastWeaponUsed.remove(victim.getUniqueId());
      this.woodenStakeKills.remove(victim.getUniqueId());
      if (this.vampireManager.isVampire(victim)) {
         int woodenStakeThreshold = this.plugin.getConfigManager().getPermadeathMinimumStage();
         int victimStage = this.vampireManager.getVampireStage(victim);
         // A fae bargain makes you stakeable at whatever stage you were pinned to, so a stage 2
         // or 3 bargain is not a free ride past the normal stake threshold.
         boolean faeStakeable = this.plugin.getFaeManager() != null
            && this.plugin.getFaeManager().hasDeal(victim)
            && this.plugin.getConfigManager().isFaeStakingIgnoresMinimumStage();
         if ((victimStage <= woodenStakeThreshold || faeStakeable) && killedWithWoodenWeapon) {
            victim.addScoreboardTag("PermaKilled");
            killer.sendMessage("§4You have permanently killed the vampire " + victim.getName() + "!");
            this.createVampireDeathEffects(victim.getLocation());
            this.broadcastPermaKill(victim, killer);
            this.plugin
               .logInfo("PERMA-KILL: Applied PermaKilled tag to " + victim.getName() + " (Stage " + victimStage + ", Threshold: " + woodenStakeThreshold + ")");
         } else {
            victim.addScoreboardTag("PromotionBanPending");
            this.plugin
               .logInfo(
                  "PROMOTION BAN: Applied PromotionBanPending tag to "
                     + victim.getName()
                     + " (Stage "
                     + victimStage
                     + ", Threshold: "
                     + woodenStakeThreshold
                     + ")"
               );
         }
      }
   }

   private void broadcastPermaKill(Player victim, Player killer) {
      String vampireMessage = "§4You feel a dark soul ripped from its human coil, somebody has slayed a member of your monsterous family...";
      String humanMessage = "§aYou feel the realm has been purged of an evil spirit... Someone has succesfully killed a vampire. Permanently.";

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!player.getUniqueId().equals(victim.getUniqueId())) {
            if (this.vampireManager.isVampire(player)) {
               player.sendMessage(vampireMessage);
            } else {
               player.sendMessage(humanMessage);
            }
         }
      }

      String killerType = this.vampireManager.isVampire(killer) ? "vampire" : "human";
      this.plugin.logInfo("PERMA-KILL: " + victim.getName() + " was permanently killed by " + killerType + " " + killer.getName());
   }

   /**
    * Announce that a human has permanently died (chose permadeath). Messages are configurable
    * under {@code messages.human-permadeath} and differ for vampire vs human recipients;
    * {@code {player}} is replaced with the victim's name. The victim is skipped (their own
    * permadeath UI is handled by the ghost/spectator prompt).
    */
   private void broadcastHumanPermadeath(Player victim) {
      org.bukkit.configuration.file.FileConfiguration cfg = this.plugin.getConfig();
      if (!cfg.getBoolean("messages.human-permadeath.enabled", true)) {
         return;
      }
      String toVampires = cfg.getString("messages.human-permadeath.to-vampires",
            "&4A mortal soul slips beneath the veil... somewhere, the night grows one heartbeat richer.");
      String toHumans = cfg.getString("messages.human-permadeath.to-humans",
            "&cA cold dread settles over you... somewhere, one of the living has drawn their final breath.");

      // Deliberately nameless — the victim is never identified.
      toVampires = org.bukkit.ChatColor.translateAlternateColorCodes('&', toVampires);
      toHumans = org.bukkit.ChatColor.translateAlternateColorCodes('&', toHumans);

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.getUniqueId().equals(victim.getUniqueId())) {
            continue;
         }
         player.sendMessage(this.vampireManager.isVampire(player) ? toVampires : toHumans);
      }
      this.plugin.logInfo("PERMADEATH: Human " + victim.getName() + " has permanently died.");
   }

   private boolean isWoodenWeapon(ItemStack item) {
      if (item == null) {
         this.plugin.logInfo("DEBUG: Weapon is null");
         return false;
      } else {
         Material type = item.getType();
         boolean isWooden = type == Material.WOODEN_SWORD || type == Material.WOODEN_AXE;
         this.plugin.logInfo("DEBUG: Weapon type: " + type + ", Is wooden: " + isWooden);
         return isWooden;
      }
   }

   private void createVampireDeathEffects(Location deathLocation) {
      if (deathLocation.getWorld() != null) {
         Location centerLoc = deathLocation.clone().add(0.0, 1.0, 0.0);
         deathLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, centerLoc, 60, 1.5, 1.0, 1.5, 0.1);
         deathLocation.getWorld().spawnParticle(Particle.FLAME, centerLoc, 40, 1.2, 0.8, 1.2, 0.08);
         deathLocation.getWorld().spawnParticle(Particle.WHITE_ASH, centerLoc, 50, 1.0, 1.5, 1.0, 0.05);
         deathLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, centerLoc, 30, 1.8, 1.2, 1.8, 0.02);
         deathLocation.getWorld().playSound(deathLocation, Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.5F, 0.8F);
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            deathLocation.getWorld().spawnParticle(Particle.WHITE_ASH, centerLoc, 30, 1.5, 2.0, 1.5, 0.03);
            deathLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, centerLoc, 20, 1.0, 0.5, 1.0, 0.02);
            deathLocation.getWorld().playSound(deathLocation, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.2F);
         }, 20L);
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            deathLocation.getWorld().spawnParticle(Particle.LARGE_SMOKE, centerLoc, 15, 2.0, 1.8, 2.0, 0.01);
            deathLocation.getWorld().spawnParticle(Particle.WHITE_ASH, centerLoc, 10, 1.8, 2.5, 1.8, 0.02);
         }, 40L);
      }
   }
}
