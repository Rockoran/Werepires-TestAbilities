package pow.crimson2.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pow.crimson2.VampireSMPPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import pow.crimson2.utils.OptionalTypeAdapter;

public class ArmorStorageManager {
   private final VampireSMPPlugin plugin;
   private final Gson gson;
   private final File storageFile;
   private final File backupFile;
   private final Map<UUID, ArmorStorageManager.StoredArmor> armorCache = new ConcurrentHashMap<>();

   /** Serializes ItemStack as a Base64 string via Paper's serializeAsBytes / deserializeBytes. */
   private static final TypeAdapter<ItemStack> ITEM_ADAPTER = new TypeAdapter<ItemStack>() {
      @Override
      public void write(JsonWriter out, ItemStack value) throws IOException {
         if (value == null) {
            out.nullValue();
         } else {
            out.value(Base64.getEncoder().encodeToString(value.serializeAsBytes()));
         }
      }
      @Override
      public ItemStack read(JsonReader in) throws IOException {
         if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
         }
         return ItemStack.deserializeBytes(Base64.getDecoder().decode(in.nextString()));
      }
   };

   public ArmorStorageManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(ItemStack.class, ITEM_ADAPTER)
            .registerTypeAdapterFactory(new OptionalTypeAdapter())
            .setPrettyPrinting()
            .create();
      this.storageFile = new File(plugin.getDataFolder(), "bat_armor_storage.json");
      this.backupFile = new File(plugin.getDataFolder(), "bat_armor_storage.backup.json");
      this.setupStorageFiles();
      this.loadArmorData();
   }

   private void setupStorageFiles() {
      try {
         if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
         }

         if (!this.storageFile.exists()) {
            this.storageFile.createNewFile();

            try (FileWriter writer = new FileWriter(this.storageFile)) {
               writer.write("{}");
            }
         }

         this.plugin.logInfo("ArmorStorageManager: Storage files initialized");
      } catch (IOException e) {
         this.plugin.getLogger().severe("ArmorStorageManager: Failed to initialize storage files: " + e.getMessage());
         e.printStackTrace();
      }
   }

   public boolean storeAndClearPlayerArmor(UUID playerId, Player player) {
      try {
         ItemStack[] currentArmor = player.getInventory().getArmorContents();
         ArmorStorageManager.StoredArmor storedArmor = new ArmorStorageManager.StoredArmor(currentArmor[3], currentArmor[2], currentArmor[1], currentArmor[0]);
         if (storedArmor.hasAnyArmor()) {
            this.armorCache.put(playerId, storedArmor);
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.updateInventory();
            this.saveArmorData();
            this.plugin.logInfo("ArmorStorageManager: Stored and cleared armor for player " + player.getName());
            return true;
         } else {
            this.plugin.logInfo("ArmorStorageManager: No armor to store for player " + player.getName());
            return false;
         }
      } catch (Exception e) {
         this.plugin.getLogger().severe("ArmorStorageManager: Failed to store and clear armor for player " + playerId + ": " + e.getMessage());
         e.printStackTrace();
         return false;
      }
   }

   public ArmorStorageManager.StoredArmor getStoredArmor(UUID playerId) {
      return this.armorCache.get(playerId);
   }

   public boolean hasStoredArmor(UUID playerId) {
      ArmorStorageManager.StoredArmor stored = this.armorCache.get(playerId);
      return stored != null && stored.hasAnyArmor();
   }

   public void clearStoredArmor(UUID playerId) {
      if (this.armorCache.remove(playerId) != null) {
         this.saveArmorData();
         this.plugin.logInfo("ArmorStorageManager: Cleared stored armor for player " + playerId);
      }
   }

   private void loadArmorData() {
      File fileToLoad = this.storageFile;
      if (!this.isFileValid(this.storageFile) && this.isFileValid(this.backupFile)) {
         this.plugin.getLogger().warning("ArmorStorageManager: Main storage file corrupt, using backup");
         fileToLoad = this.backupFile;
      }

      try (FileReader reader = new FileReader(fileToLoad)) {
         Type mapType = (new TypeToken<Map<String, ArmorStorageManager.StoredArmor>>() {}).getType();
         Map<String, ArmorStorageManager.StoredArmor> loadedData = this.gson.fromJson(reader, mapType);
         if (loadedData != null) {
            for (Entry<String, ArmorStorageManager.StoredArmor> entry : loadedData.entrySet()) {
               try {
                  UUID playerId = UUID.fromString(entry.getKey());
                  this.armorCache.put(playerId, entry.getValue());
               } catch (IllegalArgumentException e) {
                  this.plugin.getLogger().warning("ArmorStorageManager: Invalid UUID in storage: " + entry.getKey());
               }
            }

            this.plugin.logInfo("ArmorStorageManager: Loaded armor data for " + this.armorCache.size() + " players");
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("ArmorStorageManager: Could not load armor storage file: " + e.getMessage());
      } catch (Exception e) {
         this.plugin.getLogger().severe("ArmorStorageManager: Error parsing armor storage file: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void saveArmorData() {
      try {
         if (this.storageFile.exists() && this.storageFile.length() > 0L) {
            try {
               Files.copy(this.storageFile.toPath(), this.backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
               this.plugin.getLogger().warning("ArmorStorageManager: Failed to create backup: " + e.getMessage());
            }
         }

         Map<String, ArmorStorageManager.StoredArmor> dataToSave = new HashMap<>();

         for (Entry<UUID, ArmorStorageManager.StoredArmor> entry : this.armorCache.entrySet()) {
            dataToSave.put(entry.getKey().toString(), entry.getValue());
         }

         try (FileWriter writer = new FileWriter(this.storageFile)) {
            this.gson.toJson(dataToSave, writer);
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("ArmorStorageManager: Failed to save armor data: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private boolean isFileValid(File file) {
      return file.exists() && file.length() > 2L;
   }

   public void cleanupExpiredEntries() {
      long currentTime = System.currentTimeMillis();
      long maxAge = 604800000L;
      this.armorCache.entrySet().removeIf(entry -> {
         boolean expired = currentTime - entry.getValue().timestamp > maxAge;
         if (expired) {
            this.plugin.logInfo("ArmorStorageManager: Removed expired armor storage for player " + entry.getKey());
         }

         return expired;
      });
      if (!this.armorCache.isEmpty()) {
         this.saveArmorData();
      }
   }

   public Map<UUID, ArmorStorageManager.StoredArmor> getAllStoredArmor() {
      return new HashMap<>(this.armorCache);
   }

   public void shutdown() {
      this.saveArmorData();
      this.plugin.logInfo("ArmorStorageManager: Shutdown complete, " + this.armorCache.size() + " armor sets saved");
   }

   public static class StoredArmor {
      public final ItemStack helmet;
      public final ItemStack chestplate;
      public final ItemStack leggings;
      public final ItemStack boots;
      public final long timestamp;

      public StoredArmor(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
         this.helmet = helmet != null ? helmet.clone() : null;
         this.chestplate = chestplate != null ? chestplate.clone() : null;
         this.leggings = leggings != null ? leggings.clone() : null;
         this.boots = boots != null ? boots.clone() : null;
         this.timestamp = System.currentTimeMillis();
      }

      public boolean hasAnyArmor() {
         return this.helmet != null || this.chestplate != null || this.leggings != null || this.boots != null;
      }

      public ItemStack[] getArmorContents() {
         return new ItemStack[]{this.boots, this.leggings, this.chestplate, this.helmet};
      }
   }
}
