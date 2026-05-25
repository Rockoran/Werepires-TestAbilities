package pow.crimson2.managers;

import java.util.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.abilities.tome.BlessingTomeAbility;
import pow.crimson2.abilities.tome.TomeAbility;
import pow.crimson2.listeners.CureBookReadingListener;
import pow.crimson2.utils.LeveledEnchantment;

public class TomeDistributionManager {
   private final VampireSMPPlugin plugin;
   private final ConfigManager configManager;
   private final Random random;
   private BukkitTask distributionTask;
   private List<Location> tomeLocations = new ArrayList<>();
   private List<String> tomeTypes;
   private List<LeveledEnchantment> enchantmentTypes;

   public TomeDistributionManager(VampireSMPPlugin plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.random = new Random();
      this.reloadConfig();
   }

   public void reloadConfig() {
      this.initializeTomeLocations();
      this.initializeTomeTypes();
      this.initializeEnchantmentTypes();
   }

   private void initializeTomeLocations() {
      this.tomeLocations = this.configManager.getTomeChestLocations();
      if (this.tomeLocations.isEmpty()) {
         this.plugin.getLogger().warning("TomeDistributionManager: No tome locations found in config!");
      } else {
         this.plugin.logInfo("TomeDistributionManager: Loaded " + this.tomeLocations.size() + " tome locations from config");
      }
   }

   private void initializeTomeTypes() {
      this.tomeTypes = new ArrayList<>();
      List<String> tomeTypes = this.configManager.getTomeTypes();
      for (String tome : tomeTypes) {
         if (this.plugin.getTomeManager().isValidAbility(tome)) {
            this.tomeTypes.add(tome);
         } else {
            this.plugin.getLogger().warning("TomeDistributionManager: Unknown tome type " + tome + "!");
         }
      }
      if (this.tomeTypes.isEmpty()) {
         this.plugin.getLogger().warning("TomeDistributionManager: No valid tome types specified. Chests containing a tome will instead be empty!");
      } else {
         this.plugin.logInfo("TomeDistributionManager: Loaded " + this.tomeTypes.size() + " tome types.");
      }
   }

   private void initializeEnchantmentTypes() {
      this.enchantmentTypes = this.plugin.getConfigManager().getTomeEnchantmentTypes();
      this.plugin.logInfo("TomeDistributionManager: Loaded " + this.enchantmentTypes.size() + " tome types.");
   }

   public void startDistributionTask() {
      this.stopDistributionTask();
      long intervalTicks = this.configManager.getTomeDistributionIntervalTicks();
      this.distributionTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::distributeTomes, 0L, intervalTicks);
      this.plugin.logInfo("TomeDistributionManager: Started tome distribution task (every " + intervalTicks / 20L / 60L + " minutes)");
   }

   public void stopDistributionTask() {
      if (this.distributionTask != null) {
         this.distributionTask.cancel();
         this.distributionTask = null;
         this.plugin.logInfo("TomeDistributionManager: Stopped tome distribution task");
      }
   }

   public void distributeTomes() {
      if (this.plugin.getSessionManager().getSessionState() == 1) {
         if (this.tomeLocations.isEmpty()) {
            this.plugin.getLogger().warning("TomeDistributionManager: No tome locations available for distribution");
         } else {
            this.clearAllTomeChests();
            List<Location> locations = new ArrayList<>(this.tomeLocations);
            Collections.shuffle(locations, this.random);

            // spawn misc items
            int miscCount = Math.min(this.plugin.getConfigManager().getTomeMiscCount(), locations.size());
            List<ItemStack> miscTypes = this.plugin.getConfigManager().getTomeMiscTypes();
            if (!miscTypes.isEmpty()) {
               for (int i = 0; i < miscCount; i++) {
                  ItemStack item = new ItemStack(miscTypes.get(this.random.nextInt(miscTypes.size())));
                  // Semi-arbitrary special case: if the item is holy water, give it appropriate name and lore.
                  // Nothing breaks if we don't do this, just makes it consistent with holy water made with Blessing.
                  if (item.getType() == Material.SPLASH_POTION) {
                     BlessingTomeAbility.addHolyWaterDescription(this.plugin, item);
                  }
                  this.distributeItemToLocation(locations.removeLast(), item);
               }
            }

            // spawn tome books
            int tomeCount = Math.min(this.plugin.getConfigManager().getTomeCount(), locations.size());
            if (!this.tomeTypes.isEmpty()) {
               for (int i = 0; i < tomeCount; i++) {
                  String randomTome = this.tomeTypes.get(this.random.nextInt(this.tomeTypes.size()));
                  this.distributeTomeToLocation(locations.removeLast(), randomTome);
               }
            }

            // spawn enchantment books
            for (Location location : locations) {
               this.addEnchantmentBookToLocation(location);
            }

            boolean cureBooksEnabled = this.configManager.isCureBooksEnabled();
            double cureBooksSpawnChance = this.configManager.getCureBooksSpawnChance();
            boolean cureBookAdded = false;
            if (cureBooksEnabled && this.random.nextDouble() < cureBooksSpawnChance) {
               Location randomLocation = this.tomeLocations.get(this.random.nextInt(this.tomeLocations.size()));
               this.replaceCureBookAtLocation(randomLocation);
               cureBookAdded = true;
            }

            this.plugin
               .logInfo(
                  "TomeDistributionManager: Distributed "
                     + miscCount
                     + " miscellaneous items, "
                     + tomeCount
                     + " tomes, "
                     + locations.size()
                     + " enchantment books"
                     + (cureBookAdded ? ", and 1 cure book (replaced a chest)" : "")
                     + " to chest locations"
               );
         }
      }
   }

   private void clearAllTomeChests() {
      for (Location location : this.tomeLocations) {
         Block block = location.getBlock();
         if (block.getType() == Material.CHEST) {
            Chest chest = (Chest)block.getState();
            chest.getInventory().clear();
         }
      }
   }

   private void distributeTomeToLocation(Location location, String tomeType) {
      ItemStack tome = this.createTomeItem(tomeType);
      distributeItemToLocation(location, tome);
      this.plugin.logInfo("TomeDistributionManager: Added " + tomeType + " tome to chest at " + this.locationToString(location));
   }

   private void distributeItemToLocation(Location location, ItemStack item) {
      Block block = location.getBlock();
      if (block.getType() != Material.CHEST) {
         block.setType(Material.CHEST);
         this.plugin.logInfo("TomeDistributionManager: Created chest at " + this.locationToString(location));
      }

      Chest chest = (Chest)block.getState();
      Inventory chestInventory = chest.getInventory();
      chestInventory.addItem(new ItemStack[]{item});
   }

   private ItemStack createTomeItem(String tomeType) {
      ItemStack tome = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta bookMeta = (BookMeta)tome.getItemMeta();
      if (bookMeta != null) {
         bookMeta.setTitle(tomeType);
         bookMeta.setAuthor("§6A source unknown...");
         TomeAbility ability = this.plugin.getTomeManager().getAbility(tomeType);
         if (ability != null) {
            List<String> lore = new ArrayList<>();
            String[] descriptionLines = ability.getDescriptionLines();

            for (String line : descriptionLines) {
               lore.add("§7" + line);
            }

            lore.add("");
            lore.add("§eRight-click with this tome in hand to learn its secrets");
            bookMeta.setLore(lore);
         }

         List<String> pages = new ArrayList<>();
         StringBuilder pageContent = new StringBuilder();
         pageContent.append("§5§lANCIENT KNOWLEDGE§r\n\n");
         pageContent.append("§8The secrets of ").append(tomeType).append(" are contained within these pages.\n\n");
         if (ability != null) {
            String[] descriptionLines = ability.getDescriptionLines();

            for (String line : descriptionLines) {
               pageContent.append("§7").append(line).append("\n");
            }
         } else {
            pageContent.append("§7No description available\n");
         }

         pageContent.append("\n§6Use this knowledge wisely, for it comes with great responsibility.");
         pages.add(pageContent.toString());
         bookMeta.setPages(pages);
         tome.setItemMeta(bookMeta);
      }

      return tome;
   }

   private ItemStack createRandomEnchantmentBook() {
      ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
      EnchantmentStorageMeta meta = (EnchantmentStorageMeta)enchantedBook.getItemMeta();
      if (meta != null) {
         LeveledEnchantment randomEnchantment = this.enchantmentTypes.get(this.random.nextInt(this.enchantmentTypes.size()));
         meta.addStoredEnchant(randomEnchantment.enchantment, randomEnchantment.level, true);
         enchantedBook.setItemMeta(meta);
      }

      return enchantedBook;
   }

   private void addEnchantmentBookToLocation(Location location) {
      Block block = location.getBlock();
      if (block.getType() != Material.CHEST) {
         block.setType(Material.CHEST);
         this.plugin.logInfo("TomeDistributionManager: Created chest at " + this.locationToString(location));
      }

      Chest chest = (Chest)block.getState();
      ItemStack enchantmentBook = this.createRandomEnchantmentBook();
      Inventory chestInventory = chest.getInventory();
      chestInventory.addItem(new ItemStack[]{enchantmentBook});
      this.plugin.logInfo("TomeDistributionManager: Added enchantment book to chest at " + this.locationToString(location));
   }

   private void replaceCureBookAtLocation(Location location) {
      Block block = location.getBlock();
      if (block.getType() != Material.CHEST) {
         block.setType(Material.CHEST);
         this.plugin.logInfo("TomeDistributionManager: Created chest at " + this.locationToString(location));
      }

      Chest chest = (Chest)block.getState();
      Inventory chestInventory = chest.getInventory();
      chestInventory.clear();
      ItemStack cureBook = this.createRandomCureBook();
      chestInventory.addItem(new ItemStack[]{cureBook});
      this.plugin
         .logInfo(
            "TomeDistributionManager: Replaced chest contents with cure book ("
               + cureBook.getItemMeta().getDisplayName()
               + ") at "
               + this.locationToString(location)
         );
   }

   private ItemStack createRandomCureBook() {
      int bookChoice = this.random.nextInt(3);
      int bookNumber = bookChoice + 1;
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta bookMeta = (BookMeta)book.getItemMeta();
      if (bookMeta != null) {
         switch (bookChoice) {
            case 0:
               bookMeta.setTitle("The Remedy 1/3");
               bookMeta.setAuthor("§5An ancient scholar");
               bookMeta.setPages(
                  new String[]{
                     "§5§lTHE REMEDY§r\n§8Part I of III\n\n§7In the darkest hours, when the cursed blood burns within your veins, know that salvation exists.\n\n§7The ancients spoke of a trinity of knowledge...",
                     "§7...that when combined, can sever the unholy bond between mortal and monster.\n\n§7This is the first piece of that forbidden wisdom.\n\n§8Read on, seeker of the light..."
                  }
               );
               break;
            case 1:
               bookMeta.setTitle("The Cure 2/3");
               bookMeta.setAuthor("§5An ancient scholar");
               bookMeta.setPages(
                  new String[]{
                     "§5§lTHE CURE§r\n§8Part II of III\n\n§7The second fragment reveals the nature of the curse itself.\n\n§7Born of darkness, sustained by blood, the vampire's existence is a perversion of nature's order...",
                     "§7...yet within this perversion lies the key to its undoing.\n\n§7Holy water, blessed by the righteous, weakens the bond.\n\n§8Continue your search, truth-seeker..."
                  }
               );
               break;
            case 2:
               bookMeta.setTitle("The Absolution 3/3");
               bookMeta.setAuthor("§5An ancient scholar");
               bookMeta.setPages(
                  new String[]{
                     "§5§lTHE ABSOLUTION§r\n§8Part III of III\n\n§7The final piece completes the trinity.\n\n§7With all three fragments of knowledge, the words of power are revealed:\n\n§6voluntate-mea-hoc-nefandum-vinculum-abicio",
                     "§7Stand near a holy beacon, with holy water upon your person, beneath the light of day.\n\n§7Speak the words, and be free of the curse forevermore.\n\n§8May the light guide your path."
                  }
               );
         }

         List<String> lore = new ArrayList<>();
         lore.add("§5An ancient tome of forbidden knowledge");
         lore.add("§7Part " + bookNumber + " of the cure series");
         lore.add("");
         lore.add("§eRead this book to absorb its wisdom");
         bookMeta.setLore(lore);
         CureBookReadingListener.markAsAuthenticCureBook(bookMeta, bookNumber, this.plugin);
         book.setItemMeta(bookMeta);
      }

      return book;
   }

   private String locationToString(Location location) {
      return String.format("(%d, %d, %d)", location.getBlockX(), location.getBlockY(), location.getBlockZ());
   }

   public boolean addTomeLocation(Location location) {
      boolean added = this.configManager.addTomeChestLocation(location);
      if (added) {
         this.tomeLocations = this.configManager.getTomeChestLocations();
         this.plugin.logInfo("TomeDistributionManager: Added tome location at " + this.locationToString(location));
         return true;
      } else {
         this.plugin.getLogger().warning("TomeDistributionManager: Location " + this.locationToString(location) + " already exists in config");
         return false;
      }
   }

   public boolean removeTomeLocation(Location location) {
      boolean removed = this.configManager.removeTomeChestLocation(location);
      if (removed) {
         this.tomeLocations = this.configManager.getTomeChestLocations();
         this.plugin.logInfo("TomeDistributionManager: Removed tome location at " + this.locationToString(location));
         return true;
      } else {
         this.plugin.getLogger().warning("TomeDistributionManager: Location " + this.locationToString(location) + " not found in config");
         return false;
      }
   }

   public List<Location> getTomeLocations() {
      return new ArrayList<>(this.tomeLocations);
   }

   public void triggerDistribution() {
      this.distributeTomes();
   }

   public void shutdown() {
      if (this.distributionTask != null) {
         this.distributionTask.cancel();
         this.distributionTask = null;
      }

      this.plugin.logInfo("TomeDistributionManager: Shutdown complete");
   }
}
