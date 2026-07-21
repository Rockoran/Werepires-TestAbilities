package pow.crimson2.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.listeners.CureBookReadingListener;

public class StashFourthBookCommand implements CommandExecutor {
   private final VampireSMPPlugin plugin;

   public StashFourthBookCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length < 3) {
         sender.sendMessage("§cUsage: /stash_fourth_book <x> <y> <z>");
         return true;
      }

      int x;
      int y;
      int z;
      try {
         x = Integer.parseInt(args[0]);
         y = Integer.parseInt(args[1]);
         z = Integer.parseInt(args[2]);
      } catch (NumberFormatException e) {
         sender.sendMessage("§cInvalid coordinates. Use whole numbers.");
         return true;
      }

      World world = this.plugin.getWorld();
      if (world == null) {
         sender.sendMessage("§cWorld not found.");
         return true;
      } else {
         Location chestLocation = new Location(world, x, y, z);
         Block block = world.getBlockAt(chestLocation);
         if (!(block.getState() instanceof Chest)) {
            sender.sendMessage("§cNo chest found at coordinates " + x + ", " + y + ", " + z + ".");
            return true;
         } else {
            Chest chest = (Chest)block.getState();
            Inventory chestInventory = chest.getInventory();
            chestInventory.clear();
            ItemStack book = this.createRetributionBook();
            chestInventory.addItem(new ItemStack[]{book});
            sender.sendMessage("§aSuccessfully stashed 'The Retribution 4/3' in the chest at " + x + ", " + y + ", " + z + ".");
            this.plugin.logInfo(sender.getName() + " used /stash_fourth_book - placed The Retribution 4/3 at " + x + ", " + y + ", " + z);
            return true;
         }
      }
   }

   private ItemStack createRetributionBook() {
      return CureBookReadingListener.createFourthCureBook(this.plugin);
   }
}
