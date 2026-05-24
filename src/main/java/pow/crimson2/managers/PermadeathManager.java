package pow.crimson2.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.libs.gson.Gson;
import pow.crimson2.libs.gson.reflect.TypeToken;

public class PermadeathManager {
   private final VampireSMPPlugin plugin;
   private final Map<UUID, PermadeathManager.PermadeathMode> permadeathModes;
   private final File dataFile;
   private final Gson gson;

   public PermadeathManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.permadeathModes = new HashMap<>();
      this.dataFile = new File(plugin.getDataFolder(), "permadeath_modes.json");
      this.gson = new Gson();
      this.loadPermadeathData();
      this.migrateOldData();
   }

   public PermadeathManager.PermadeathMode getPermadeathMode(Player player) {
      return this.permadeathModes.getOrDefault(player.getUniqueId(), PermadeathManager.PermadeathMode.OFF);
   }

   public void setPermadeathMode(Player player, PermadeathManager.PermadeathMode mode) {
      UUID playerId = player.getUniqueId();
      this.permadeathModes.put(playerId, mode);
      this.savePermadeathData();
      this.plugin.logInfo("Player " + player.getName() + " permadeath mode set to: " + mode);
   }

   public boolean hasPermadeathEnabled(Player player) {
      PermadeathManager.PermadeathMode mode = this.getPermadeathMode(player);
      return mode == PermadeathManager.PermadeathMode.ON || mode == PermadeathManager.PermadeathMode.ABSOLUTE;
   }

   public boolean hasAbsolutePermadeathEnabled(Player player) {
      return this.getPermadeathMode(player) == PermadeathManager.PermadeathMode.ABSOLUTE;
   }

   public void removePlayer(Player player) {
      UUID playerId = player.getUniqueId();
      this.permadeathModes.remove(playerId);
      this.savePermadeathData();
   }

   public int getPermadeathCount() {
      return (int)this.permadeathModes
         .values()
         .stream()
         .filter(mode -> mode == PermadeathManager.PermadeathMode.ON || mode == PermadeathManager.PermadeathMode.ABSOLUTE)
         .count();
   }

   public void clearAllPermadeathModes() {
      this.permadeathModes.clear();
      this.savePermadeathData();
      this.plugin.logInfo("PermadeathManager: Cleared all permadeath modes");
   }

   private void loadPermadeathData() {
      if (!this.dataFile.exists()) {
         this.plugin.logInfo("PermadeathManager: No existing permadeath data file found, starting fresh.");
      } else {
         try (FileReader reader = new FileReader(this.dataFile)) {
            Type type = (new TypeToken<Map<String, String>>() {}).getType();
            Map<String, String> rawData = this.gson.fromJson(reader, type);
            if (rawData != null) {
               for (Entry<String, String> entry : rawData.entrySet()) {
                  try {
                     UUID playerId = UUID.fromString(entry.getKey());
                     PermadeathManager.PermadeathMode mode = PermadeathManager.PermadeathMode.valueOf(entry.getValue());
                     this.permadeathModes.put(playerId, mode);
                  } catch (IllegalArgumentException e) {
                     this.plugin.getLogger().warning("PermadeathManager: Invalid data in file: " + entry.getKey() + " = " + entry.getValue());
                  }
               }

               this.plugin.logInfo("PermadeathManager: Loaded " + this.permadeathModes.size() + " permadeath preferences");
            }
         } catch (IOException e) {
            this.plugin.getLogger().severe("PermadeathManager: Failed to load permadeath data: " + e.getMessage());
         }
      }
   }

   private void migrateOldData() {
      File oldPermadeathFile = new File(this.plugin.getDataFolder(), "permadeath_preferences.json");
      File oldAbsoluteFile = new File(this.plugin.getDataFolder(), "absolute_permadeath_preferences.json");
      boolean migrated = false;
      if (oldPermadeathFile.exists()) {
         try (FileReader reader = new FileReader(oldPermadeathFile)) {
            Type type = (new TypeToken<Map<String, Boolean>>() {}).getType();
            Map<String, Boolean> oldData = this.gson.fromJson(reader, type);
            if (oldData != null) {
               for (Entry<String, Boolean> entry : oldData.entrySet()) {
                  try {
                     UUID playerId = UUID.fromString(entry.getKey());
                     if (entry.getValue() && !this.permadeathModes.containsKey(playerId)) {
                        this.permadeathModes.put(playerId, PermadeathManager.PermadeathMode.ON);
                     }
                  } catch (IllegalArgumentException var13) {
                  }
               }

               migrated = true;
            }
         } catch (IOException e) {
            this.plugin.getLogger().warning("PermadeathManager: Failed to migrate old permadeath data: " + e.getMessage());
         }
      }

      if (oldAbsoluteFile.exists()) {
         try (FileReader reader = new FileReader(oldAbsoluteFile)) {
            Type type = (new TypeToken<Map<String, Boolean>>() {}).getType();
            Map<String, Boolean> oldData = this.gson.fromJson(reader, type);
            if (oldData != null) {
               for (Entry<String, Boolean> entry : oldData.entrySet()) {
                  try {
                     UUID playerId = UUID.fromString(entry.getKey());
                     if (entry.getValue()) {
                        this.permadeathModes.put(playerId, PermadeathManager.PermadeathMode.ABSOLUTE);
                     }
                  } catch (IllegalArgumentException var11) {
                  }
               }

               migrated = true;
            }
         } catch (IOException e) {
            this.plugin.getLogger().warning("PermadeathManager: Failed to migrate old absolute permadeath data: " + e.getMessage());
         }
      }

      if (migrated) {
         this.savePermadeathData();
         this.plugin.logInfo("PermadeathManager: Migrated old permadeath data to new format");
      }
   }

   private void savePermadeathData() {
      try {
         if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
         }

         Map<String, String> rawData = new HashMap<>();

         for (Entry<UUID, PermadeathManager.PermadeathMode> entry : this.permadeathModes.entrySet()) {
            rawData.put(entry.getKey().toString(), entry.getValue().name());
         }

         try (FileWriter writer = new FileWriter(this.dataFile)) {
            this.gson.toJson(rawData, writer);
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("PermadeathManager: Failed to save permadeath data: " + e.getMessage());
      }
   }

   public void shutdown() {
      this.savePermadeathData();
      this.plugin.logInfo("PermadeathManager: Shutdown complete");
   }

   public enum PermadeathMode {
      OFF,
      ON,
      ABSOLUTE;
   }
}
