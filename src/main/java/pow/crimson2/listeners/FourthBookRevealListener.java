package pow.crimson2.listeners;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.ConfigManager;

public class FourthBookRevealListener implements Listener {
   private final VampireSMPPlugin plugin;
   private final ConfigManager configManager;
   private final List<Location> tomeChestLocations;
   private final Location townChestLocation;

   public FourthBookRevealListener(VampireSMPPlugin plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.tomeChestLocations = configManager.getTomeChestLocations();
      if (plugin.getWorld() != null) {
         this.townChestLocation = new Location(plugin.getWorld(), 76.0, 80.0, 407.0);
         plugin.logInfo("FourthBookRevealListener: Loaded " + this.tomeChestLocations.size() + " tome chest locations from config");
      } else {
         this.townChestLocation = null;
         plugin.getLogger().warning("FourthBookRevealListener: World not found during initialization");
      }
   }

   @EventHandler
   public void onChestOpen(InventoryOpenEvent event) {
      if (event.getPlayer() instanceof Player) {
         Player player = (Player)event.getPlayer();
         if (this.plugin.getSessionManager().isSessionActive()) {
            if (!this.plugin.getVampireManager().isVampire(player)) {
               if (this.plugin.getStateConfig().getBoolean("fourth_book_spawn_enabled", false)) {
                  if (!this.hasBeenRevealed()) {
                     Inventory inventory = event.getInventory();
                     if (inventory.getHolder() != null && inventory.getHolder() instanceof Chest) {
                        Chest chest = (Chest)inventory.getHolder();
                        Location chestLocation = chest.getLocation();
                        if (!this.isTownChest(chestLocation)) {
                           if (this.isTomeChest(chestLocation)) {
                              this.revealFourthBook(chest, player);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isTownChest(Location location) {
      return this.townChestLocation == null
         ? false
         : location.getBlockX() == this.townChestLocation.getBlockX()
            && location.getBlockY() == this.townChestLocation.getBlockY()
            && location.getBlockZ() == this.townChestLocation.getBlockZ()
            && location.getWorld().equals(this.townChestLocation.getWorld());
   }

   private boolean isTomeChest(Location location) {
      for (Location tomeLocation : this.tomeChestLocations) {
         if (location.getBlockX() == tomeLocation.getBlockX()
            && location.getBlockY() == tomeLocation.getBlockY()
            && location.getBlockZ() == tomeLocation.getBlockZ()
            && location.getWorld().equals(tomeLocation.getWorld())) {
            return true;
         }
      }

      return false;
   }

   private boolean hasBeenRevealed() {
      return this.plugin.getStateConfig().getBoolean("fourth_book_has_spawned", false);
   }

   private void markAsRevealed() {
      this.plugin.getStateConfig().set("fourth_book_has_spawned", true);
      this.plugin.saveStateConfig();
      this.plugin.logInfo("FourthBookRevealListener: Marked fourth book as revealed in config");
   }

   private void revealFourthBook(Chest chest, Player player) {
      ItemStack fourthBook = this.createRetributionBook();
      Inventory chestInventory = chest.getInventory();
      chestInventory.addItem(new ItemStack[]{fourthBook});
      this.markAsRevealed();
      player.sendMessage("§8§o[As you open the chest, an unfamiliar book suddenly materializes within...]");
      player.sendMessage("§4§o[The smell of old blood emanates from its pages...]");
      this.plugin
         .logInfo(
            "FOURTH BOOK REVEALED: "
               + player.getName()
               + " opened tome chest at "
               + chest.getLocation().getBlockX()
               + ", "
               + chest.getLocation().getBlockY()
               + ", "
               + chest.getLocation().getBlockZ()
         );
   }

   private ItemStack createRetributionBook() {
      return CureBookReadingListener.createFourthCureBook(this.plugin);
   }
}
