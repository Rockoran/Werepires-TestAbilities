package pow.crimson2.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class VampireSireManager {
   private final VampireSMPPlugin plugin;
   private final Map<String, String> sireMap;
   private final File dataFile;
   private final Gson gson;

   public VampireSireManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.sireMap = new HashMap<>();
      this.dataFile = new File(plugin.getDataFolder(), "sire_mappings.json");
      this.gson = new Gson();
      this.loadSireMappings();
      plugin.logInfo("VampireSireManager initialized with " + this.sireMap.size() + " sire mappings");
   }

   public String getSire(Player vampire) {
      return this.getSireByName(vampire.getName());
   }

   public String getSireByName(String name) {
      return this.sireMap.get(name.toLowerCase());
   }

   public List<String> getCureSireLineage(Player vampire) {
      List<String> sireLine = new ArrayList<>();
      int maxSireLine = this.plugin.getConfigManager().getCureSireLineLength();
      for (int i = 0; i < maxSireLine; i++) {
         String sireName = this.getSire(vampire);
         // Player has no sire.
         if (sireName == null) break;
         Player sire = Bukkit.getPlayer(sireName);
         // Player's sire is offline.
         if (sire == null
                 // Player's sire is dead.
                 ||sire.getGameMode() == GameMode.SPECTATOR
                 // Player's sire is cured.
                 || this.plugin.getConfigManager().getCureAllowCuredSire() && this.plugin.getVampireManager().isHuman(sire))
            return new ArrayList<>();
         sireLine.add(sireName);
         vampire = sire;
      }
      return sireLine;
   }

   public void setSire(String vampireName, String sireName) {
      this.sireMap.put(vampireName.toLowerCase(), sireName);
      this.saveSireMappings();
      this.plugin.logInfo("Sire mapping added: " + vampireName + " -> " + sireName);
   }

   public void removeSire(String vampireName) {
      this.sireMap.remove(vampireName.toLowerCase());
      this.saveSireMappings();
      this.plugin.logInfo("Sire mapping removed for: " + vampireName);
   }

   public Map<String, String> getAllSireMappings() {
      return new HashMap<>(this.sireMap);
   }

   public void clearAllSireMappings() {
      this.sireMap.clear();
      this.saveSireMappings();
      this.plugin.logInfo("VampireSireManager: Cleared all sire mappings");
   }

   private void loadSireMappings() {
      if (!this.dataFile.exists()) {
         this.plugin.logInfo("VampireSireManager: No existing sire mappings file found, starting fresh.");
         this.plugin.logInfo("VampireSireManager: Edit " + this.dataFile.getPath() + " to add sire relationships.");
         this.plugin.logInfo("VampireSireManager: Format: {\"Fledgling_Name\": \"Sire_Name\"}");
         Map<String, String> exampleMap = new HashMap<>();
         exampleMap.put("Fledgling_Name", "Sire_Name");

         try {
            if (!this.plugin.getDataFolder().exists()) {
               this.plugin.getDataFolder().mkdirs();
            }

            try (FileWriter writer = new FileWriter(this.dataFile)) {
               this.gson.toJson(exampleMap, writer);
            }

            this.plugin.logInfo("VampireSireManager: Created example sire_mappings.json file");
         } catch (IOException e) {
            this.plugin.getLogger().severe("VampireSireManager: Failed to create example file: " + e.getMessage());
         }

         this.sireMap.putAll(exampleMap);
      } else {
         try (FileReader reader = new FileReader(this.dataFile)) {
            Type type = (new TypeToken<Map<String, String>>() {}).getType();
            Map<String, String> rawData = this.gson.fromJson(reader, type);
            if (rawData != null) {
               for (Entry<String, String> entry : rawData.entrySet()) {
                  this.sireMap.put(entry.getKey().toLowerCase(), entry.getValue());
               }

               this.plugin.logInfo("VampireSireManager: Loaded " + this.sireMap.size() + " sire mappings from file");
            }
         } catch (IOException e) {
            this.plugin.getLogger().severe("VampireSireManager: Failed to load sire mappings: " + e.getMessage());
         }
      }
   }

   private void saveSireMappings() {
      try {
         if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
         }

         try (FileWriter writer = new FileWriter(this.dataFile)) {
            this.gson.toJson(this.sireMap, writer);
         }

         this.plugin.logInfo("VampireSireManager: Saved " + this.sireMap.size() + " sire mappings to file");
      } catch (IOException e) {
         this.plugin.getLogger().severe("VampireSireManager: Failed to save sire mappings: " + e.getMessage());
      }
   }

   public void shutdown() {
      this.saveSireMappings();
      this.plugin.logInfo("VampireSireManager: Shutdown complete");
   }
}
