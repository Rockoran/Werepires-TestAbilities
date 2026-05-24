package pow.crimson2.listeners;

import java.util.Collection;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;

public class MovementBoundaryListener implements Listener {
   private final VampireSMPPlugin plugin;

   public MovementBoundaryListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      Location to = event.getTo();
      if (to != null) {
         Location from = event.getFrom();
         if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            if (player.getGameMode() != GameMode.CREATIVE) {
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
               boolean crossedBoundary = toX < minX || toX > maxX || toZ < minZ || toZ > maxZ;
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
                     if (leaveMessage != null) {
                        player.sendMessage(leaveMessage);
                     }
                  }
               } else {
                  if (!canLeave && crossedBoundary) {
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
            }
         }
      }
   }

   private boolean areAllBeaconsDesecrated() {
      Collection<BeaconSite> beacons = this.plugin.getBeaconManager().getAllBeacons();
      if (beacons.isEmpty()) {
         return false;
      }

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
      if (beacons.isEmpty()) {
         return false;
      }

      boolean skipCorrupted = !this.plugin.getConfigManager().doCorruptedBeaconsTrapHumans();
      int checked = 0;

      for (BeaconSite beacon : beacons) {
         if (!skipCorrupted || beacon.getState() != BeaconSite.BeaconState.PERMANENTLY_DESECRATED) {
            checked++;
            if (beacon.getState() != BeaconSite.BeaconState.HOLY) {
               return false;
            }
         }
      }

      return checked > 0;
   }

   private boolean anySurvivalModeHumansExist() {
      for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
         if (onlinePlayer.getGameMode() == GameMode.SURVIVAL && this.plugin.getVampireManager().isHuman(onlinePlayer)) {
            return true;
         }
      }

      return false;
   }

   private boolean anySurvivalModeVampiresExist() {
      for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
         if (onlinePlayer.getGameMode() == GameMode.SURVIVAL && !this.plugin.getVampireManager().isHuman(onlinePlayer)) {
            return true;
         }
      }

      return false;
   }
}
