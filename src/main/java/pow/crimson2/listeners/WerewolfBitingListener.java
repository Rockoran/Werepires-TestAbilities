package pow.crimson2.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public class WerewolfBitingListener implements Listener {

   // Values loaded from config at bite-start time (see startBite)
   private double maxBiteDistance;
   private int biteNotifyTicks;
   private int biteCompleteTicks;

   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;

   // keyed by biter UUID
   private final Map<UUID, BiteSession> activeBitesByBiter = new HashMap<>();
   // keyed by victim UUID (to prevent being bitten by multiple wolves)
   private final Map<UUID, UUID> bitersForVictim = new HashMap<>();

   public WerewolfBitingListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
      this.reloadConfig();
   }

   public void reloadConfig() {
      this.maxBiteDistance = plugin.getConfigManager().getWerewolfBiteMaxDistance();
      this.biteNotifyTicks = plugin.getConfigManager().getWerewolfBiteNotifyTicks();
      this.biteCompleteTicks = plugin.getConfigManager().getWerewolfBiteCompleteTicks();
   }

   private static class BiteSession {
      final UUID biterUUID;
      final UUID victimUUID;
      int ticks = 0;
      boolean victimNotified = false;
      BukkitTask task;

      BiteSession(UUID biterUUID, UUID victimUUID) {
         this.biterUUID = biterUUID;
         this.victimUUID = victimUUID;
      }
   }

   @EventHandler
   public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
      // Only main hand to avoid double-firing
      if (event.getHand() != EquipmentSlot.HAND) return;

      if (!(event.getRightClicked() instanceof Player victim)) return;

      Player biter = event.getPlayer();

      if (!this.plugin.getSessionManager().isSessionActive()) return;
      if (!this.vampireManager.isWerewolf(biter)) return;
      if (!biter.isSneaking()) return;

      // Must have empty main hand
      ItemStack mainHand = biter.getInventory().getItemInMainHand();
      if (mainHand != null && mainHand.getType() != Material.AIR) return;

      // Cannot bite other werewolves
      if (this.vampireManager.isWerewolf(victim)) {
         biter.sendMessage("§6You cannot turn another beast.");
         event.setCancelled(true);
         return;
      }

      // Already biting someone
      if (this.activeBitesByBiter.containsKey(biter.getUniqueId())) {
         biter.sendMessage("§6You are already sinking your claws into someone.");
         event.setCancelled(true);
         return;
      }

      // Victim already being bitten
      if (this.bitersForVictim.containsKey(victim.getUniqueId())) {
         biter.sendMessage("§6That prey is already being claimed by another.");
         event.setCancelled(true);
         return;
      }

      event.setCancelled(true);

      // Start bite session
      BiteSession session = new BiteSession(biter.getUniqueId(), victim.getUniqueId());
      this.activeBitesByBiter.put(biter.getUniqueId(), session);
      this.bitersForVictim.put(victim.getUniqueId(), biter.getUniqueId());

      biter.sendMessage("§6You begin to sink your claws into " + victim.getName() + "...");
      biter.sendMessage("§6§oStay close and keep sneaking to complete the infection.");

      BukkitRunnable task = new BukkitRunnable() {
         @Override
         public void run() {
            Player biterPlayer = plugin.getServer().getPlayer(session.biterUUID);
            Player victimPlayer = plugin.getServer().getPlayer(session.victimUUID);

            // Cancel if either player is offline or dead
            if (biterPlayer == null || !biterPlayer.isOnline() || biterPlayer.isDead()
               || victimPlayer == null || !victimPlayer.isOnline() || victimPlayer.isDead()) {
               cancelSession(session, false);
               return;
            }

            // Cancel if biter is no longer sneaking
            if (!biterPlayer.isSneaking()) {
               cancelSession(session, true);
               biterPlayer.sendMessage("§cYou stopped sneaking — the infection was broken.");
               return;
            }

            // Cancel if players drifted apart
            if (!biterPlayer.getWorld().equals(victimPlayer.getWorld())
               || biterPlayer.getLocation().distance(victimPlayer.getLocation()) > maxBiteDistance) {
               cancelSession(session, true);
               biterPlayer.sendMessage("§cYou moved too far — the infection was broken.");
               victimPlayer.sendMessage("§aYou managed to escape the beast's grasp.");
               return;
            }

            // Cancel if victim is now a werewolf (turned by someone else?)
            if (vampireManager.isWerewolf(victimPlayer)) {
               cancelSession(session, false);
               return;
            }

            session.ticks += 2;

            // Syphon particles every 10 ticks
            if (session.ticks % 10 == 0) {
               victimPlayer.getWorld().spawnParticle(
                  Particle.ANGRY_VILLAGER,
                  victimPlayer.getLocation().add(0, 1.0, 0),
                  3, 0.3, 0.3, 0.3, 0
               );
               biterPlayer.getWorld().playSound(
                  biterPlayer.getLocation(), Sound.ENTITY_WOLF_PANT, SoundCategory.PLAYERS, 0.3F, 0.7F
               );
            }

            // Notify victim at 10 seconds
            if (!session.victimNotified && session.ticks >= biteNotifyTicks) {
               session.victimNotified = true;
               victimPlayer.sendMessage("§c§lYou feel claws raking across your skin...");
               victimPlayer.sendMessage("§c§lYou are being turned! Move away within 5 seconds!");
               biterPlayer.sendMessage("§6Your prey has been warned — hold your grip for 5 more seconds!");
               victimPlayer.getWorld().playSound(
                  victimPlayer.getLocation(), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.MASTER, 0.8F, 0.6F
               );
            }

            // Complete turning at 15 seconds
            if (session.ticks >= biteCompleteTicks) {
               cancelSession(session, false);
               performTurning(biterPlayer, victimPlayer);
            }
         }
      };

      BukkitTask bukkitTask = task.runTaskTimer(this.plugin, 2L, 2L);
      session.task = bukkitTask;
   }

   private void performTurning(Player biter, Player victim) {
      if (biter == null || victim == null) return;
      if (!biter.isOnline() || !victim.isOnline()) return;
      if (this.vampireManager.isWerewolf(victim)) return;

      this.vampireManager.setPlayerAsWerewolf(victim, 1);
      victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
         org.bukkit.potion.PotionEffectType.REGENERATION, 200, 1, false, false
      ));

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (this.plugin.getBeaconMajorityManager() != null) {
            this.plugin.getBeaconMajorityManager().removeBonusesFromPlayer(victim);
            this.plugin.getBeaconMajorityManager().updateBeaconMajorityBonuses();
         }
         double maxHealth = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
         victim.setHealth(maxHealth);
      }, 5L);

      biter.sendMessage("§6§lThe infection is complete! " + victim.getName() + " joins the pack!");
      victim.sendMessage("§6§lYou feel the beast awaken within you...");
      victim.sendMessage("§6§lYou have been turned into a werewolf!");
      biter.getWorld().playSound(biter.getLocation(), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.MASTER, 1.0F, 0.7F);
      victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.MASTER, 1.0F, 0.9F);
   }

   private void cancelSession(BiteSession session, boolean cleanup) {
      this.activeBitesByBiter.remove(session.biterUUID);
      this.bitersForVictim.remove(session.victimUUID);
      if (session.task != null && !session.task.isCancelled()) {
         session.task.cancel();
      }
   }

   public void cancelBiteSessionForPlayer(Player player) {
      UUID uuid = player.getUniqueId();

      // If this player is a biter
      BiteSession biterSession = this.activeBitesByBiter.remove(uuid);
      if (biterSession != null) {
         this.bitersForVictim.remove(biterSession.victimUUID);
         if (biterSession.task != null && !biterSession.task.isCancelled()) {
            biterSession.task.cancel();
         }
      }

      // If this player is a victim
      UUID biterUUID = this.bitersForVictim.remove(uuid);
      if (biterUUID != null) {
         BiteSession victimSession = this.activeBitesByBiter.remove(biterUUID);
         if (victimSession != null && victimSession.task != null && !victimSession.task.isCancelled()) {
            victimSession.task.cancel();
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.cancelBiteSessionForPlayer(event.getPlayer());
   }

   public void shutdown() {
      for (BiteSession session : this.activeBitesByBiter.values()) {
         if (session.task != null && !session.task.isCancelled()) {
            session.task.cancel();
         }
      }
      this.activeBitesByBiter.clear();
      this.bitersForVictim.clear();
   }
}
