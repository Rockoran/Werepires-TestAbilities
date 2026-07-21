package pow.crimson2.listeners;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;

public class MovementBoundaryListener implements Listener {
   private final VampireSMPPlugin plugin;
   private boolean barrierLowered = false;

   /** Always allowed through the barrier even when raised. */
   private final Set<UUID> exemptPlayers = new HashSet<>();
   /** Always blocked by the barrier even when lowered. */
   private final Set<UUID> confinedPlayers = new HashSet<>();

   /** How many blocks from the boundary wall triggers vehicle ejection / mount prevention. */
   private static final double MOUNT_BUFFER = 2.0;

   public MovementBoundaryListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean isBarrierLowered() {
      return barrierLowered;
   }

   public void setBarrierLowered(boolean lowered) {
      this.barrierLowered = lowered;
   }

   public boolean isExempt(Player player) {
      return exemptPlayers.contains(player.getUniqueId());
   }

   public boolean isConfined(Player player) {
      return confinedPlayers.contains(player.getUniqueId());
   }

   /** Set exempt (always out), normal (neither), or confined (always in). */
   public void setExempt(UUID uuid) {
      confinedPlayers.remove(uuid);
      exemptPlayers.add(uuid);
   }

   public void setNormal(UUID uuid) {
      exemptPlayers.remove(uuid);
      confinedPlayers.remove(uuid);
   }

   public void setConfined(UUID uuid) {
      exemptPlayers.remove(uuid);
      confinedPlayers.add(uuid);
   }

   public Set<UUID> getExemptPlayers() {
      return exemptPlayers;
   }

   public Set<UUID> getConfinedPlayers() {
      return confinedPlayers;
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      if (isExempt(player)) return; // always allowed through
      boolean effectivelyLowered = barrierLowered && !isConfined(player);
      if (effectivelyLowered) return;

      Location to = event.getTo();
      if (to == null) return;
      Location from = event.getFrom();
      if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;

      GameMode gameMode = player.getGameMode();
      if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) return;

      double minX = this.plugin.getConfigManager().getOakhurstMinX();
      double maxX = this.plugin.getConfigManager().getOakhurstMaxX();
      double minZ = this.plugin.getConfigManager().getOakhurstMinZ();
      double maxZ = this.plugin.getConfigManager().getOakhurstMaxZ();

      double toX = to.getX();
      double toZ = to.getZ();
      double fromX = from.getX();
      double fromZ = from.getZ();

      boolean wasInsideBoundary = fromX >= minX && fromX <= maxX && fromZ >= minZ && fromZ <= maxZ;
      boolean isOutsideBoundary = toX < minX || toX > maxX || toZ < minZ || toZ > maxZ;
      boolean crossedBoundary   = toX < minX || toX > maxX || toZ < minZ || toZ > maxZ;

      // --- Vehicle: eject before crossing instead of rubber-banding ---
      if (player.isInsideVehicle() && wasInsideBoundary && crossedBoundary) {
         Entity vehicle = player.getVehicle();
         if (vehicle != null) vehicle.eject();
         event.setCancelled(true);
         player.sendMessage("§cYour mount cannot cross the barrier. You have been dismounted.");
         return;
      }

      boolean canLeave = false;
      String leaveMessage = null;
      if (player.getScoreboardTags().contains("CuredVampire")) {
         canLeave = true;
         leaveMessage = "§6You are leaving " + this.plugin.getConfigManager().getOakhurstName() + "...\n§eThe familiar lands fade behind you as you venture beyond the border.";
      } else if (!this.plugin.getVampireManager().isHuman(player)) {
         if (this.areAllBeaconsDesecrated() && !this.anySurvivalModeHumansExist()) {
            canLeave = true;
            leaveMessage = "§4You are free of your chains, creature of the night...";
         } else if (this.areAllBeaconsDesecrated()) {
            canLeave = false;
         }
      } else if (this.areAllBeaconsHoly() && !this.anySurvivalModeVampiresExist()) {
         canLeave = true;
         leaveMessage = "§aYou are free... Finally free...";
      } else if (this.areAllBeaconsHoly()) {
         canLeave = false;
      }

      if (canLeave && wasInsideBoundary && isOutsideBoundary) {
         if (!player.getScoreboardTags().contains("LeftOakhurst")) {
            player.addScoreboardTag("LeftOakhurst");
            if (leaveMessage != null) player.sendMessage(leaveMessage);
         }
      } else if (!canLeave && crossedBoundary) {
         event.setCancelled(true);
         if (!player.getScoreboardTags().contains("informed_boundary")) {
            player.addScoreboardTag("informed_boundary");
            String blockedMessage;
            if (!this.plugin.getVampireManager().isHuman(player)) {
               if (this.areAllBeaconsDesecrated()) {
                  blockedMessage = "§4But while humans remain... Hope still stands...";
               } else {
                  blockedMessage = "§cYou feel a force tying you to " + this.plugin.getConfigManager().getOakhurstName() + "... You may not leave while an enemy's beacon remains... But one that has embraced darkness, and yet has found strength to return to the light... Could escape...";
               }
            } else if (this.areAllBeaconsHoly()) {
               blockedMessage = "§aBut while evil creatures still walk " + this.plugin.getConfigManager().getOakhurstName() + ", your job is not yet finished...";
            } else {
               blockedMessage = "§cYou feel a force tying you to " + this.plugin.getConfigManager().getOakhurstName() + "... You may not leave while an enemy's beacon remains... But one that has embraced darkness, and yet has found strength to return to the light... Could escape...";
            }
            player.sendMessage(blockedMessage);
         }
      }
   }

   /** Prevent mounting a vehicle when within MOUNT_BUFFER blocks of the barrier (inside). */
   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onMount(EntityMountEvent event) {
      if (!(event.getEntity() instanceof Player player)) return;
      if (isExempt(player)) return;
      if (barrierLowered && !isConfined(player)) return;
      GameMode gm = player.getGameMode();
      if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

      double minX = this.plugin.getConfigManager().getOakhurstMinX();
      double maxX = this.plugin.getConfigManager().getOakhurstMaxX();
      double minZ = this.plugin.getConfigManager().getOakhurstMinZ();
      double maxZ = this.plugin.getConfigManager().getOakhurstMaxZ();

      Location loc = player.getLocation();
      double x = loc.getX();
      double z = loc.getZ();

      boolean insideBoundary = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
      if (!insideBoundary) return;

      // Distance to each wall from inside
      double distToMinX = x - minX;
      double distToMaxX = maxX - x;
      double distToMinZ = z - minZ;
      double distToMaxZ = maxZ - z;
      double nearestWall = Math.min(Math.min(distToMinX, distToMaxX), Math.min(distToMinZ, distToMaxZ));

      if (nearestWall <= MOUNT_BUFFER) {
         event.setCancelled(true);
         player.sendMessage("§cYou are too close to the barrier to mount. Step away from the border first.");
      }
   }

   private boolean areAllBeaconsDesecrated() {
      Collection<BeaconSite> beacons = this.plugin.getBeaconManager().getAllBeacons();
      if (beacons.isEmpty()) return false;
      boolean skipCorrupted = !this.plugin.getConfigManager().doCorruptedBeaconsTrapHumans();
      int checked = 0;
      for (BeaconSite beacon : beacons) {
         if (!skipCorrupted || beacon.getState() != BeaconSite.BeaconState.PERMANENTLY_DESECRATED) {
            checked++;
            if (beacon.getState() != BeaconSite.BeaconState.DESECRATED && beacon.getState() != BeaconSite.BeaconState.PERMANENTLY_DESECRATED) {
               return false;
            }
         }
      }
      return checked > 0;
   }

   private boolean areAllBeaconsHoly() {
      Collection<BeaconSite> beacons = this.plugin.getBeaconManager().getAllBeacons();
      if (beacons.isEmpty()) return false;
      boolean skipCorrupted = !this.plugin.getConfigManager().doCorruptedBeaconsTrapHumans();
      int checked = 0;
      for (BeaconSite beacon : beacons) {
         if (!skipCorrupted || beacon.getState() != BeaconSite.BeaconState.PERMANENTLY_DESECRATED) {
            checked++;
            if (beacon.getState() != BeaconSite.BeaconState.HOLY) return false;
         }
      }
      return checked > 0;
   }

   private boolean anySurvivalModeHumansExist() {
      for (Player p : this.plugin.getServer().getOnlinePlayers()) {
         if (p.getGameMode() == GameMode.SURVIVAL && this.plugin.getVampireManager().isHuman(p)) return true;
      }
      return false;
   }

   private boolean anySurvivalModeVampiresExist() {
      for (Player p : this.plugin.getServer().getOnlinePlayers()) {
         if (p.getGameMode() == GameMode.SURVIVAL && !this.plugin.getVampireManager().isHuman(p)) return true;
      }
      return false;
   }
}
