package pow.crimson2.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

/**
 * Per-player, per-ability cooldown reductions granted by a fae as part of a bargain.
 *
 * <p>A boon is a percentage off one named ability's cooldown for one player — the fae chooses
 * both. Reductions apply to tome, vampire and werewolf abilities alike, because all three funnel
 * their cooldown through a single {@code setCooldown} that consults this manager.
 *
 * <p>Stored in {@code cooldown_boons.json}: these are bargains, so they must outlive a restart.
 */
public class CooldownBoonManager {

   /** Nobody gets a free ability — a boon can shorten a cooldown, never remove it. */
   public static final int MAX_PERCENT = 90;

   private final VampireSMPPlugin plugin;
   private final File dataFile;
   private final Gson gson;

   /** player -> (lowercase ability name -> percent reduction) */
   private final Map<UUID, Map<String, Integer>> boons = new HashMap<>();

   public CooldownBoonManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.dataFile = new File(plugin.getDataFolder(), "cooldown_boons.json");
      this.gson = new GsonBuilder().setPrettyPrinting().create();
      this.load();
   }

   // ------------------------------------------------------------------ queries

   /** Percent reduction this player has on {@code ability}, or 0. */
   public int getReduction(Player player, String ability) {
      if (player == null || ability == null) return 0;
      Map<String, Integer> owned = this.boons.get(player.getUniqueId());
      if (owned == null) return 0;
      Integer pct = owned.get(ability.toLowerCase());
      return pct == null ? 0 : Math.max(0, Math.min(MAX_PERCENT, pct));
   }

   /**
    * Apply this player's boon to a cooldown length.
    *
    * <p>The single entry point every cooldown system calls. Always returns at least 1 second for
    * a cooldown that was non-zero, so a boon can never turn an ability into a free action.
    */
   public int applyTo(Player player, String ability, int seconds) {
      if (seconds <= 0) return seconds;
      int pct = this.getReduction(player, ability);
      if (pct <= 0) return seconds;
      int reduced = (int) Math.round(seconds * (100.0 - pct) / 100.0);
      return Math.max(1, reduced);
   }

   /** Everything this player has been granted, ability -> percent, sorted for display. */
   public Map<String, Integer> getBoons(Player player) {
      Map<String, Integer> owned = this.boons.get(player.getUniqueId());
      return owned == null ? new TreeMap<>() : new TreeMap<>(owned);
   }

   public boolean hasAny(Player player) {
      Map<String, Integer> owned = this.boons.get(player.getUniqueId());
      return owned != null && !owned.isEmpty();
   }

   // ----------------------------------------------------------------- mutation

   /** Grant or overwrite a boon. Percent is clamped to 1..{@value #MAX_PERCENT}. */
   public void grant(Player player, String ability, int percent) {
      int clamped = Math.max(1, Math.min(MAX_PERCENT, percent));
      this.boons.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
                .put(ability.toLowerCase(), clamped);
      this.save();
      this.plugin.logInfo("CooldownBoon: " + player.getName() + " " + ability + " -" + clamped + "%");
   }

   /** @return true if a boon was actually removed. */
   public boolean revoke(Player player, String ability) {
      Map<String, Integer> owned = this.boons.get(player.getUniqueId());
      if (owned == null) return false;
      boolean removed = owned.remove(ability.toLowerCase()) != null;
      if (owned.isEmpty()) this.boons.remove(player.getUniqueId());
      if (removed) this.save();
      return removed;
   }

   /** @return how many boons were cleared. */
   public int revokeAll(Player player) {
      Map<String, Integer> owned = this.boons.remove(player.getUniqueId());
      if (owned == null || owned.isEmpty()) return 0;
      this.save();
      return owned.size();
   }

   public void clearEverything() {
      this.boons.clear();
      this.save();
      this.plugin.logInfo("CooldownBoonManager: cleared all boons");
   }

   // ------------------------------------------------------------- ability names

   /** Union of every ability name that can carry a boon — tome, vampire and werewolf. */
   public Set<String> knownAbilities() {
      Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      if (this.plugin.getTomeManager() != null) {
         names.addAll(this.plugin.getTomeManager().getAllAbilityNames());
      }
      if (this.plugin.getVampireAbilityManager() != null) {
         names.addAll(this.plugin.getVampireAbilityManager().getAllAbilityNames());
      }
      if (this.plugin.getWerewolfAbilityManager() != null) {
         names.addAll(this.plugin.getWerewolfAbilityManager().getAllAbilityNames());
      }
      return names;
   }

   /** Resolve user input to a real ability name, case-insensitively. Null if unknown. */
   public String resolve(String input) {
      if (input == null) return null;
      for (String known : this.knownAbilities()) {
         if (known.equalsIgnoreCase(input)) return known;
      }
      return null;
   }

   // ------------------------------------------------------------- persistence

   private void load() {
      if (!this.dataFile.exists()) return;
      try (FileReader reader = new FileReader(this.dataFile)) {
         Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
         Map<String, Map<String, Integer>> raw = this.gson.fromJson(reader, type);
         if (raw != null) {
            for (Map.Entry<String, Map<String, Integer>> e : raw.entrySet()) {
               try {
                  this.boons.put(UUID.fromString(e.getKey()), new HashMap<>(e.getValue()));
               } catch (IllegalArgumentException ex) {
                  this.plugin.getLogger().warning("CooldownBoonManager: bad uuid " + e.getKey());
               }
            }
            this.plugin.logInfo("CooldownBoonManager: loaded boons for " + this.boons.size() + " player(s)");
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("CooldownBoonManager: load failed: " + e.getMessage());
      }
   }

   private void save() {
      try {
         if (!this.plugin.getDataFolder().exists()) this.plugin.getDataFolder().mkdirs();
         Map<String, Map<String, Integer>> raw = new HashMap<>();
         for (Map.Entry<UUID, Map<String, Integer>> e : this.boons.entrySet()) {
            raw.put(e.getKey().toString(), e.getValue());
         }
         try (FileWriter writer = new FileWriter(this.dataFile)) {
            this.gson.toJson(raw, writer);
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("CooldownBoonManager: save failed: " + e.getMessage());
      }
   }

   public void shutdown() {
      this.save();
   }
}
