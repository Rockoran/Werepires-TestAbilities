package pow.crimson2.managers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

public class WerewolfHungerManager {

   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private final SessionManager sessionManager;
   private final ConfigManager configManager;
   private final float HUNGER_PER_SECOND;
   private final int IMMUNITY_DURATION_MINUTES;
   private File immunityFile;
   private Map<UUID, Integer> immunityTimers = new HashMap<>();
   private BukkitTask hungerTask;
   private int minuteCounter = 60;

   public static final String HUNGER_IMMUNITY_TAG = "ImmuneToHunger";

   public WerewolfHungerManager(VampireSMPPlugin plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.vampireManager = plugin.getVampireManager();
      this.sessionManager = plugin.getSessionManager();
      this.IMMUNITY_DURATION_MINUTES = configManager.getWerewolfHungerImmunityMinutes();
      int depletionMinutes = configManager.getWerewolfHungerDepletionMinutes();
      this.HUNGER_PER_SECOND = 1.0f / depletionMinutes / 60.0f;
      this.setupImmunitySystem();
      this.startHungerTask();
   }

   // -------------------------------------------------------------------------
   // Setup
   // -------------------------------------------------------------------------

   private void setupImmunitySystem() {
      if (!this.plugin.getDataFolder().exists()) {
         this.plugin.getDataFolder().mkdirs();
      }

      this.immunityFile = new File(this.plugin.getDataFolder(), "wolf_hunger_immunity.txt");
      if (!this.immunityFile.exists()) {
         try {
            this.immunityFile.createNewFile();
            this.plugin.logInfo("Created wolf hunger immunity persistence file");
         } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to create wolf hunger immunity file: " + e.getMessage());
            e.printStackTrace();
         }
      }

      this.loadImmunityData();
   }

   private void loadImmunityData() {
      String line;
      try (BufferedReader reader = new BufferedReader(new FileReader(this.immunityFile))) {
         while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
               UUID uuid = UUID.fromString(parts[0]);
               int minutes = Integer.parseInt(parts[1]);
               this.immunityTimers.put(uuid, minutes);
            }
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("Could not load wolf hunger immunity data: " + e.getMessage());
      }
   }

   private void saveImmunityData() {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.immunityFile))) {
         for (Entry<UUID, Integer> entry : this.immunityTimers.entrySet()) {
            writer.write(entry.getKey().toString() + ":" + entry.getValue());
            writer.newLine();
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("Could not save wolf hunger immunity data: " + e.getMessage());
      }
   }

   // -------------------------------------------------------------------------
   // Hunger tick task
   // -------------------------------------------------------------------------

   private void startHungerTask() {
      this.hungerTask = new BukkitRunnable() {
         @Override
         public void run() {
            if (WerewolfHungerManager.this.sessionManager.isSessionActive()) {
               for (Player player : Bukkit.getOnlinePlayers()) {
                  if (WerewolfHungerManager.this.vampireManager.isWerewolf(player)) {
                     WerewolfHungerManager.this.processWerewolfHunger(player);
                  }
               }

               WerewolfHungerManager.this.minuteCounter--;
               if (WerewolfHungerManager.this.minuteCounter <= 0) {
                  WerewolfHungerManager.this.minuteCounter = 60;
                  WerewolfHungerManager.this.updateImmunityTimers();
               }
            }
         }
      }.runTaskTimer(this.plugin, 20L, 20L);
   }

   // -------------------------------------------------------------------------
   // Hunger drain
   // -------------------------------------------------------------------------

   private void processWerewolfHunger(Player wolf) {
      if (!wolf.getScoreboardTags().contains(HUNGER_IMMUNITY_TAG)) {
         float currentHunger = wolf.getExp();
         float newHunger = currentHunger - this.HUNGER_PER_SECOND;
         if (newHunger <= 0.0f) {
            wolf.setExp(0.0f);
            this.handleHungerStarvation(wolf);
         } else {
            wolf.setExp(newHunger);
         }
      }
   }

   private void handleHungerStarvation(Player wolf) {
      int currentStage = this.vampireManager.getWerewolfStage(wolf);
      if (currentStage > 1) {
         this.demoteWerewolf(wolf, true);
      }
   }

   // -------------------------------------------------------------------------
   // Feed / promote
   // -------------------------------------------------------------------------

   public void feedHunger(Player wolf, float hungerGained) {
      float currentHunger = wolf.getExp();
      float newHunger = currentHunger + hungerGained;
      float maxHunger = this.getMaxHungerForWolf(wolf);
      if (newHunger >= 1.0f && this.vampireManager.getWerewolfStage(wolf) < 3) {
         this.promoteWerewolf(wolf);
      } else {
         wolf.setExp(Math.min(maxHunger, newHunger));
      }
   }

   private float getMaxHungerForWolf(Player wolf) {
      if (this.vampireManager.getWerewolfStage(wolf) >= 3) {
         return 0.99f;
      }
      return this.vampireManager.hasWerewolfPromotionBan(wolf) ? 0.99f : 1.0f;
   }

   public void promoteWerewolf(Player wolf) {
      if (this.vampireManager.hasWerewolfPromotionBan(wolf)) {
         wolf.sendMessage("§6§lPROMOTION DENIED");
         wolf.sendMessage("§e§lThe weakness of defeat still holds you back...");
         wolf.sendMessage("§e§lYou cannot grow stronger until the next session begins.");
         wolf.setExp(0.99f);
         return;
      }

      int currentStage = this.vampireManager.getWerewolfStage(wolf);
      int newStage = Math.min(3, currentStage + 1);

      if (this.vampireManager.hasWerewolfStageCap(wolf)) {
         int cap = this.vampireManager.getWerewolfStageCap(wolf);
         if (newStage > cap) {
            wolf.sendMessage("§6§lPROMOTION DENIED");
            wolf.sendMessage("§e§lThe memory of starvation still haunts you...");
            wolf.sendMessage("§e§lYou cannot reach Stage " + newStage + " until the next session begins.");
            wolf.setExp(0.99f);
            return;
         }
      }

      this.vampireManager.setPlayerAsWerewolf(wolf, newStage);
      this.giveHungerImmunity(wolf);
      wolf.setExp(0.25f);

      wolf.sendMessage("§6§l▶ THE BEAST EVOLVES ◀");
      wolf.sendMessage("§e§lRaw flesh fills your belly; the beast within surges with power.");
      if (newStage == 2) {
         wolf.sendMessage("§6Your claws are sharper. Your speed, greater. You are now Stage 2.");
      } else {
         wolf.sendMessage("§6§lThe Apex Predator Awakens. You have reached Stage 3 — the full beast!");
      }
      wolf.sendMessage("§eYour hunger is sated, you are stronger, for now...");
      wolf.playSound(wolf, Sound.ENTITY_WOLF_AMBIENT, SoundCategory.MASTER, 1.0f, 0.7f);
   }

   // -------------------------------------------------------------------------
   // Demote
   // -------------------------------------------------------------------------

   private void demoteWerewolf(Player wolf, boolean fromStarvation) {
      int currentStage = this.vampireManager.getWerewolfStage(wolf);
      if (currentStage > 1) {
         if (fromStarvation) {
            int newStage = currentStage - 1;
            this.vampireManager.setWerewolfStageCap(wolf, newStage);
            wolf.sendMessage("§6§lYou cannot return to Stage " + currentStage + " until the next session begins.");
         }

         this.vampireManager.reduceWerewolfStage(wolf);
         this.giveHungerImmunity(wolf);
         wolf.setExp(0.5f);

         if (fromStarvation) {
            wolf.sendMessage("§6§lSTARVATION");
            wolf.sendMessage("§e§lThe beast within you whimpers with weakness.");
            wolf.sendMessage("§e§lYou must feed more often, or the beast will abandon you...");
         } else {
            wolf.sendMessage("§6§lDEATH'S TOLL");
            wolf.sendMessage("§e§lThe beast within you retreats, licking its wounds.");
         }

         wolf.playSound(wolf, Sound.ENTITY_WOLF_WHINE, SoundCategory.MASTER, 1.0f, 0.8f);
      }
   }

   // -------------------------------------------------------------------------
   // Immunity
   // -------------------------------------------------------------------------

   private void giveHungerImmunity(Player wolf) {
      UUID uuid = wolf.getUniqueId();
      this.immunityTimers.put(uuid, this.IMMUNITY_DURATION_MINUTES);
      wolf.addScoreboardTag(HUNGER_IMMUNITY_TAG);
      this.saveImmunityData();
   }

   private void updateImmunityTimers() {
      Set<UUID> onlinePlayers = Bukkit.getOnlinePlayers().stream()
         .<UUID>map(OfflinePlayer::getUniqueId).collect(Collectors.toSet());
      Set<UUID> toRemove = new HashSet<>();

      for (Entry<UUID, Integer> entry : this.immunityTimers.entrySet()) {
         UUID uuid = entry.getKey();
         if (onlinePlayers.contains(uuid)) {
            int timeLeft = entry.getValue() - 1;
            if (timeLeft <= 0) {
               toRemove.add(uuid);
               Player player = Bukkit.getPlayer(uuid);
               if (player != null) {
                  player.removeScoreboardTag(HUNGER_IMMUNITY_TAG);
                  if (this.vampireManager.isWerewolf(player)) {
                     player.sendMessage("§6§lIMMUNITY EXPIRED");
                     player.sendMessage("§eThe gnawing hunger reminds you of your primal needs...");
                     player.sendMessage("§eThe time to hunt and feed is approaching...");
                     player.playSound(player, Sound.ENTITY_WOLF_WHINE, SoundCategory.MASTER, 0.8f, 0.9f);
                  }
               }
            } else {
               this.immunityTimers.put(uuid, timeLeft);
            }
         }
      }

      for (UUID uuid : toRemove) {
         this.immunityTimers.remove(uuid);
      }

      this.saveImmunityData();
   }

   // -------------------------------------------------------------------------
   // Death / reset
   // -------------------------------------------------------------------------

   /**
    * Called by applyWerewolfDeathPenalty after the wolf has already been reset to stage 1.
    * Gives immunity so they don't immediately starve on respawn, and clears hunger to 0.
    */
   public void resetAfterDeath(Player wolf) {
      this.giveHungerImmunity(wolf);
      wolf.setExp(0.0f);
   }

   // -------------------------------------------------------------------------
   // Login bar restore
   // -------------------------------------------------------------------------

   /**
    * Ensures the level number in the XP bar matches the werewolf's stage.
    * The exp fill (0.0-1.0) is automatically persisted by Paper across restarts.
    */
   public void restoreXpBar(Player wolf) {
      if (!this.vampireManager.isWerewolf(wolf)) return;
      wolf.setLevel(this.vampireManager.getWerewolfStage(wolf));
   }

   // -------------------------------------------------------------------------
   // Public queries
   // -------------------------------------------------------------------------

   public boolean hasHungerImmunity(Player player) {
      return player.getScoreboardTags().contains(HUNGER_IMMUNITY_TAG);
   }

   public int getRemainingImmunity(Player player) {
      return this.immunityTimers.getOrDefault(player.getUniqueId(), 0);
   }

   // -------------------------------------------------------------------------
   // Shutdown
   // -------------------------------------------------------------------------

   public void shutdown() {
      if (this.hungerTask != null) {
         this.hungerTask.cancel();
      }
      this.saveImmunityData();
   }
}
