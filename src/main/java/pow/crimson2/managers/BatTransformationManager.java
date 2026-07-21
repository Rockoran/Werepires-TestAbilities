package pow.crimson2.managers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

public class BatTransformationManager {
   private final VampireSMPPlugin plugin;
   private final ArmorStorageManager armorStorageManager;
   public static final String BAT_FORM_TAG = "bat_form";
   private final long batDurationStage1Ms;
   private final long batDurationStage2Ms;
   private final long batDurationStage3Ms;
   private final Map<UUID, BatTransformationManager.BatData> activeBats = new ConcurrentHashMap<>();
   private File batStateFile;
   private BukkitTask batCheckTask;

   public BatTransformationManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.armorStorageManager = new ArmorStorageManager(plugin);
      this.batDurationStage1Ms = plugin.getConfigManager().getVampireBatDurationSeconds(1) * 1000L;
      this.batDurationStage2Ms = plugin.getConfigManager().getVampireBatDurationSeconds(2) * 1000L;
      this.batDurationStage3Ms = plugin.getConfigManager().getVampireBatDurationSeconds(3) * 1000L;
      this.setupPersistence();
      this.startBatCheckTask();
      this.loadBatStates();
   }

   private void setupPersistence() {
      if (!this.plugin.getDataFolder().exists()) {
         this.plugin.getDataFolder().mkdirs();
      }

      this.batStateFile = new File(this.plugin.getDataFolder(), "bat_transformations.txt");

      try {
         if (!this.batStateFile.exists()) {
            this.batStateFile.createNewFile();
         }

         this.plugin.logInfo("Created bat transformation persistence file");
      } catch (IOException e) {
         this.plugin.getLogger().severe("Failed to create bat transformation file: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void startBatCheckTask() {
      this.batCheckTask = (new BukkitRunnable() {
         int tickCount = 0;

         public void run() {
            BatTransformationManager.this.checkExpiredTransformations();
            BatTransformationManager.this.checkBatEntityHealth();
            BatTransformationManager.this.updateBatActionBars();
            this.tickCount++;
            if (this.tickCount >= 6000) {
               BatTransformationManager.this.armorStorageManager.cleanupExpiredEntries();
               this.tickCount = 0;
            }
         }
      }).runTaskTimer(this.plugin, 20L, 20L);
   }

   private void checkExpiredTransformations() {
      Iterator<Entry<UUID, BatTransformationManager.BatData>> iterator = this.activeBats.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, BatTransformationManager.BatData> entry = iterator.next();
         UUID playerId = entry.getKey();
         BatTransformationManager.BatData batData = entry.getValue();
         if (batData.isExpired()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
               this.forceTransformToHuman(player, batData);
               player.sendMessage("§6Your bat transformation has expired.");
               player.sendMessage("§7You transform back into your vampiric form.");
               player.playSound(player, Sound.ENTITY_BAT_TAKEOFF, SoundCategory.MASTER, 0.8F, 0.8F);
            }

            this.cleanupBatData(batData);
            iterator.remove();
            this.saveBatStates();
         }
      }
   }

   private void updateBatActionBars() {
      for (Entry<UUID, BatTransformationManager.BatData> entry : this.activeBats.entrySet()) {
         UUID playerId = entry.getKey();
         BatTransformationManager.BatData batData = entry.getValue();
         Player player = Bukkit.getPlayer(playerId);
         if (player != null && player.isOnline()) {
            int remainingSeconds = batData.getRemainingSeconds();
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            String actionBar = "§8Bat Form: §c§l" + String.format("%d:%02d", minutes, seconds);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBar));
         }
      }
   }

   private void checkBatEntityHealth() {
      Iterator<Entry<UUID, BatTransformationManager.BatData>> iterator = this.activeBats.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, BatTransformationManager.BatData> entry = iterator.next();
         UUID playerId = entry.getKey();
         BatTransformationManager.BatData batData = entry.getValue();
         if (batData.batEntity != null && !batData.batEntity.isValid()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
               this.handleBatDeath(player, batData);
            }

            this.cleanupBatData(batData);
            iterator.remove();
            this.saveBatStates();
         }
      }
   }

   public void handleBatDeath(Player player, BatTransformationManager.BatData batData) {
      player.removeScoreboardTag("bat_form");
      this.restorePlayerArmor(player);
      player.removePotionEffect(PotionEffectType.INVISIBILITY);
      if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
         player.setFlying(false);
         player.setAllowFlight(false);
      }

      player.setHealth(0.0);
      this.plugin.logInfo("Player " + player.getName() + " died because their bat form was destroyed");
   }

   public void handleBatDeath(Player player) {
      if (this.isInBatForm(player)) {
         BatTransformationManager.BatData batData = this.activeBats.get(player.getUniqueId());
         if (batData != null) {
            this.handleBatDeath(player, batData);
            this.cleanupBatData(batData);
            this.activeBats.remove(player.getUniqueId());
            this.saveBatStates();
         } else {
            player.removeScoreboardTag("bat_form");
            this.restorePlayerArmor(player);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
               player.setFlying(false);
               player.setAllowFlight(false);
            }

            player.setHealth(0.0);
            this.plugin.getLogger().warning("Player " + player.getName() + " bat died but no batData found - cleaned up player state");
         }
      }
   }

   public Player getPlayerFromBat(Bat bat) {
      if (bat.getCustomName() != null && bat.getCustomName().startsWith("Â§8")) {
         String playerName = bat.getCustomName().substring(2);
         return Bukkit.getPlayer(playerName);
      } else {
         return null;
      }
   }

   public boolean transformToBat(final Player player) {
      if (this.isInBatForm(player)) {
         return false;
      }

      try {
         Location currentLoc = player.getLocation().clone();
         int stage = this.plugin.getVampireManager().getVampireStage(player);
         long durationMs = stage >= 3 ? this.batDurationStage3Ms
                 : (stage == 2 ? this.batDurationStage2Ms : this.batDurationStage1Ms);
         BatTransformationManager.BatData batData = new BatTransformationManager.BatData(System.currentTimeMillis(), durationMs);
         batData.lastValidLocation = currentLoc;
         boolean armorStored = this.armorStorageManager.storeAndClearPlayerArmor(player.getUniqueId(), player);
         if (armorStored) {
            this.plugin.logInfo("Successfully stored and cleared armor for player " + player.getName() + " during bat transformation");
         } else {
            this.plugin.logInfo("No armor to store for player " + player.getName() + " during bat transformation");
         }

         player.addScoreboardTag("bat_form");
         player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, -1, 0, false, false));
         player.setAllowFlight(true);
         player.setFlying(true);
         final Bat bat = (Bat)player.getWorld().spawn(player.getLocation(), Bat.class);

         for (int i = 0; i < 10; i++) {
            player.getWorld().spawn(player.getLocation(), Bat.class);
         }

         bat.setCustomName("Â§8" + player.getName());
         bat.setCustomNameVisible(false);
         bat.setSilent(true);
         bat.setPersistent(true);
         if (this.plugin.getVampireCastTeam() != null) {
            this.plugin.getVampireCastTeam().addEntry(bat.getUniqueId().toString());
         }

         batData.batEntity = bat;
         batData.transformationTask = (new BukkitRunnable() {
            public void run() {
               if (player.isOnline() && BatTransformationManager.this.isInBatForm(player)) {
                  Location playerLoc = player.getLocation();
                  if (bat.isValid()) {
                     bat.teleport(playerLoc);
                  } else {
                     this.cancel();
                  }
               } else {
                  this.cancel();
               }
            }
         }).runTaskTimer(this.plugin, 1L, 1L);
         this.activeBats.put(player.getUniqueId(), batData);
         this.saveBatStates();
         this.plugin.logInfo("Player " + player.getName() + " transformed into bat form");
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to transform player " + player.getName() + " into bat: " + e.getMessage());
         e.printStackTrace();
         return false;
      }
   }

   public boolean transformToHuman(Player player) {
      if (!this.isInBatForm(player)) {
         return false;
      } else {
         BatTransformationManager.BatData batData = this.activeBats.get(player.getUniqueId());
         if (batData == null) {
            player.removeScoreboardTag("bat_form");
            return false;
         } else {
            this.forceTransformToHuman(player, batData);
            this.activeBats.remove(player.getUniqueId());
            this.saveBatStates();
            this.plugin.logInfo("Player " + player.getName() + " transformed back to human form");
            return true;
         }
      }
   }

   private void forceTransformToHuman(Player player, BatTransformationManager.BatData batData) {
      try {
         player.removeScoreboardTag("bat_form");
         this.restorePlayerArmor(player);
         player.removePotionEffect(PotionEffectType.INVISIBILITY);
         player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false));
         if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
         }

         this.cleanupBatData(batData);
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to transform player " + player.getName() + " back to human: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void restorePlayerArmor(Player player) {
      UUID playerId = player.getUniqueId();

      try {
         if (!this.armorStorageManager.hasStoredArmor(playerId)) {
            this.plugin.logInfo("No stored armor found for player " + player.getName() + " - nothing to restore");
            return;
         }

         ArmorStorageManager.StoredArmor storedArmor = this.armorStorageManager.getStoredArmor(playerId);
         if (storedArmor == null) {
            this.plugin.getLogger().warning("Stored armor was null for player " + player.getName());
            return;
         }

         ItemStack[] currentArmor = player.getInventory().getArmorContents();
         boolean hasCurrentArmor = false;

         for (ItemStack piece : currentArmor) {
            if (piece != null && piece.getType() != Material.AIR) {
               hasCurrentArmor = true;
               break;
            }
         }

         if (hasCurrentArmor) {
            this.plugin
               .getLogger()
               .warning("ARMOR DUPLICATION BUG DETECTED: Player " + player.getName() + " has armor equipped when restoring bat transformation armor!");
            this.plugin.getLogger().warning("This should not happen with the fixed armor system. Dropping current armor to prevent duplication.");
            Location dropLocation = player.getLocation();

            for (ItemStack piece : currentArmor) {
               if (piece != null && piece.getType() != Material.AIR) {
                  player.getWorld().dropItemNaturally(dropLocation, piece);
                  this.plugin.getLogger().warning("Dropped armor piece: " + piece.getType());
               }
            }

            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
            player.getInventory().setArmorContents(new ItemStack[4]);
         }

         ItemStack[] restoredArmor = storedArmor.getArmorContents();
         player.getInventory().setArmorContents(restoredArmor);
         player.updateInventory();
         this.armorStorageManager.clearStoredArmor(playerId);
         int restoredPieces = 0;

         for (ItemStack piece : restoredArmor) {
            if (piece != null && piece.getType() != Material.AIR) {
               restoredPieces++;
            }
         }

         this.plugin.logInfo("Successfully restored " + restoredPieces + " armor pieces for player " + player.getName() + " after bat transformation");
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to restore armor for player " + player.getName() + ": " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void cleanupBatData(BatTransformationManager.BatData batData) {
      if (batData == null) {
         this.plugin.getLogger().warning("Attempted to cleanup null batData");
      } else {
         if (batData.transformationTask != null) {
            batData.transformationTask.cancel();
         }

         if (batData.batEntity != null && batData.batEntity.isValid()) {
            batData.batEntity.remove();
         }
      }
   }

   public boolean isInBatForm(Player player) {
      return player.getScoreboardTags().contains("bat_form");
   }

   public int getRemainingTime(Player player) {
      BatTransformationManager.BatData batData = this.activeBats.get(player.getUniqueId());
      return batData == null ? 0 : batData.getRemainingSeconds();
   }

   public void handlePlayerJoin(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.armorStorageManager.hasStoredArmor(playerId)) {
         this.plugin.logInfo("Found stored armor for player " + player.getName() + " on join - attempting restoration");
         this.restorePlayerArmor(player);
      }

      if (this.isInBatForm(player)) {
         BatTransformationManager.BatData batData = this.activeBats.get(player.getUniqueId());
         this.forceTransformToHuman(player, batData);
         this.plugin.logInfo("Player " + player.getName() + " was forced back to human form upon joining (was in bat form when offline)");
      }
   }

   public void handlePlayerQuit(Player player) {
      if (this.isInBatForm(player)) {
         this.transformToHuman(player);
         this.plugin.logInfo("Player " + player.getName() + " exited bat form due to disconnect");
      }
   }

   private void loadBatStates() {
      try (BufferedReader reader = new BufferedReader(new FileReader(this.batStateFile))) {
         while (true) {
            String line;
            if ((line = reader.readLine()) == null) {
               this.plugin.logInfo("Loaded bat transformation states from file");
               break;
            }

            String[] parts = line.split(":");
            if (parts.length == 2) {
               UUID playerId = UUID.fromString(parts[0]);
               long startTime = Long.parseLong(parts[1]);
               BatTransformationManager.BatData batData = new BatTransformationManager.BatData(startTime, this.batDurationStage1Ms);
               this.activeBats.put(playerId, batData);
            }
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("Could not load bat transformation states: " + e.getMessage());
      }
   }

   private void saveBatStates() {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.batStateFile))) {
         for (Entry<UUID, BatTransformationManager.BatData> entry : this.activeBats.entrySet()) {
            UUID playerId = entry.getKey();
            BatTransformationManager.BatData batData = entry.getValue();
            if (!batData.isExpired()) {
               writer.write(playerId.toString() + ":" + batData.startTime);
               writer.newLine();
            }
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("Could not save bat transformation states: " + e.getMessage());
      }
   }

   public void shutdown() {
      if (this.batCheckTask != null) {
         this.batCheckTask.cancel();
      }

      for (BatTransformationManager.BatData batData : this.activeBats.values()) {
         this.cleanupBatData(batData);
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.isInBatForm(player)) {
            player.removeScoreboardTag("bat_form");
            this.restorePlayerArmor(player);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
               player.setFlying(false);
               player.setAllowFlight(false);
            }
         }
      }

      if (this.armorStorageManager != null) {
         this.armorStorageManager.shutdown();
      }

      this.saveBatStates();
      this.plugin.logInfo("Bat transformation manager shutdown complete");
   }

   private static class BatData {
      public final long startTime;
      public final long endTime;
      public Bat batEntity;
      public BukkitTask transformationTask;
      public Location lastValidLocation;

      public BatData(long startTime, long durationMs) {
         this.startTime = startTime;
         this.endTime = startTime + durationMs;
         this.lastValidLocation = null;
      }

      public boolean isExpired() {
         return System.currentTimeMillis() >= this.endTime;
      }

      public long getRemainingTime() {
         return Math.max(0L, this.endTime - System.currentTimeMillis());
      }

      public int getRemainingSeconds() {
         return (int)(this.getRemainingTime() / 1000L);
      }
   }
}
