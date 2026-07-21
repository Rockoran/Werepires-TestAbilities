package pow.crimson2.abilities;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;
import pow.crimson2.managers.VampireManager;

public class BeaconTeleportAbility extends VampireAbility {
   public static final String INVENTORY_TITLE = "§4Desecrated Beacon Network";

   @Override
   public String getName() {
      return "beacontravel";
   }

   @Override
   public String getDisplayName() {
      return "Beacon Travel";
   }

   @Override
   public String getDescription() {
      return "Harness dark energy to instantly travel to any desecrated beacon.";
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getVampireBeaconTeleportCooldown();
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin, org.bukkit.entity.Player player) {
      int stage = plugin.getVampireManager().getVampireStage(player);
      return plugin.getConfigManager().getVampireBeaconTeleportCooldownStage(stage);
   }

   @Override
   public int getMinimumStage() {
      return 2;
   }

   @Override
   protected boolean canUseAdditionalRequirements(Player player, VampireManager vampireManager) {
      return true;
   }

   @Override
   protected String getAdditionalRequirementMessage(Player player, VampireManager vampireManager) {
      return "The shadow network is unavailable right now.";
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      if (plugin.getConfigManager().getBeaconTeleportRequireFullHealth() && player.getHealth() < player.getMaxHealth()) {
         player.sendMessage("§cYou find yourself too weak to use that ability... Rest up and heal first.");
         return false;
      } else {
         List<BeaconSite> desecratedBeacons = plugin.getBeaconManager().getDesecratedBeacons();
         if (desecratedBeacons.isEmpty()) {
            player.sendMessage("§cNo desecrated beacons are available for beacon travel.");
            player.sendMessage("§7Beacons must be desecrated to connect to the beacon network.");
            return false;
         } else {
            this.openBeaconTeleportGUI(player, desecratedBeacons, plugin);
            player.sendMessage("§5The shadows whisper of distant beacons...");
            return false;
         }
      }
   }

   private void openBeaconTeleportGUI(Player player, List<BeaconSite> desecratedBeacons, VampireSMPPlugin plugin) {
      int slots = Math.max(9, (desecratedBeacons.size() + 8) / 9 * 9);
      slots = Math.min(54, slots);
      Inventory inventory = Bukkit.createInventory(null, slots, "§4Desecrated Beacon Network");

      for (int i = 0; i < desecratedBeacons.size() && i < slots; i++) {
         BeaconSite beacon = desecratedBeacons.get(i);
         ItemStack item = this.createBeaconItem(beacon, player);
         inventory.setItem(i, item);
      }

      player.openInventory(inventory);
   }

   private ItemStack createBeaconItem(BeaconSite beacon, Player player) {
      ItemStack item = new ItemStack(Material.BEACON);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName("§4§l" + beacon.getName());
         List<String> lore = new ArrayList<>();
         lore.add("§7Location: §f" + beacon.getLocation().getWorld().getName());
         lore.add("§7Coordinates: §f" + beacon.getLocation().getBlockX() + ", " + beacon.getLocation().getBlockY() + ", " + beacon.getLocation().getBlockZ());
         lore.add("§7State: " + beacon.getState().getColorCode() + beacon.getState().getDisplayName());
         double distance = beacon.getLocation().distance(player.getLocation());
         lore.add("§7Distance: §e" + Math.round(distance) + " blocks");
         lore.add("");
         lore.add("§5⚡ Desecrated Energy");
         lore.add("§8The beacon pulses with dark power,");
         lore.add("§8connected to the shadow network.");
         lore.add("");
         lore.add("§e▶ Click to travel through shadows");
         meta.setLore(lore);
         item.setItemMeta(meta);
      }

      return item;
   }
}
