package pow.crimson2.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.plugin.java.JavaPlugin;
import pow.crimson2.beacons.BeaconSite;
import pow.crimson2.utils.LeveledEnchantment;

public class ConfigManager {
   private final JavaPlugin plugin;
   private FileConfiguration config;

   public ConfigManager(JavaPlugin plugin) {
      this.plugin = plugin;
      this.loadConfig();
   }

   public void loadConfig() {
      this.plugin.saveDefaultConfig();
      this.plugin.reloadConfig();
      this.config = this.plugin.getConfig();
   }

   public void saveConfig() {
      this.plugin.saveConfig();
   }

   public boolean isCureBooksEnabled() {
      return this.config.getBoolean("cure_books_enabled", true);
   }

   public double getCureBooksSpawnChance() {
      return this.config.getDouble("cure_books_spawn_chance", 0.3);
   }

   public boolean isFirstMessageBlockingEnabled() {
      return this.config.getBoolean("chat.first-message-blocking-enabled", true);
   }

   public String getFirstMessageBlockedMessage() {
      return this.config
              .getString(
                      "chat.first-message-blocked-message",
                      "&eIt looks like you've attempted to send a message! Vampire SMP is geared to revolve around immersion, consider finding the person you need to speak to, or messaging them on discord. If you still need to send your chat message, [Click Here]&e. This prevention message will not appear again until your next log on if you do choose to send your message via the blue text."
              );
   }

   public long getTomeDistributionIntervalTicks() {
      int minutes = this.config.getInt("tome-distribution-interval-minutes", 20);
      return minutes * 60L * 20L;
   }

   public List<Location> getTomeChestLocations() {
      List<String> locationStrings = this.config.getStringList("tome-chests.locations");
      List<Location> locations = new ArrayList<>();
      World world = this.plugin.getServer().getWorld("world");
      if (world == null) {
         this.plugin.getLogger().severe("World 'world' not found! Cannot load tome chest locations.");
         return locations;
      }

      for (String locString : locationStrings) {
         try {
            String[] parts = locString.split(",");
            if (parts.length == 3) {
               int x = Integer.parseInt(parts[0].trim());
               int y = Integer.parseInt(parts[1].trim());
               int z = Integer.parseInt(parts[2].trim());
               locations.add(new Location(world, x, y, z));
            }
         } catch (NumberFormatException e) {
            this.plugin.getLogger().warning("Invalid tome chest location format: " + locString);
         }
      }

      return locations;
   }

   public int getTomeCount() {
      return this.config.getInt("tome-chest-items.tome-count", 4);
   }

   public List<String> getTomeTypes() {
      List<String> tomeTypes = this.config.getStringList("tome-chest-items.tomes-available");
      if (tomeTypes.isEmpty()) {
         return Arrays.asList(
                 "BanishUndead",
                 "Blessing",
                 "EnlightenedEye",
                 "HolyWord",
                 "LanternThrash",
                 "PrayerOfFaith",
                 "RallyingCry",
                 "ShoulderBarge",
                 "TurnUndead",
                 "UncannyDirection",
                 "UnnaturalHaste",
                 "WayOfTheLand",
                 "WayOfTheLumberjack",
                 "WayOfTheProspector"
         );
      } else {
         return tomeTypes;
      }
   }

   public List<LeveledEnchantment> getTomeEnchantmentTypes() {
      List<String> enchantStrings = this.config.getStringList("tome-chest-items.enchantments-available");
      List<LeveledEnchantment> enchants = new ArrayList<>();
      Registry<Enchantment> enchantReg = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
      for (String enchantString : enchantStrings) {
         String[] parts = enchantString.split(" ");
         try {
            assert parts.length == 2;
            Enchantment enchant = enchantReg.get(NamespacedKey.minecraft(parts[0]));
            if (enchant == null) {
               this.plugin.getLogger().warning("Unknown enchantment: " + parts[0]);
               continue;
            }
            int level = Integer.parseInt(parts[1]);
            enchants.add(new LeveledEnchantment(enchant, level));
         } catch (Exception e) {
            this.plugin.getLogger().warning("Invalid enchantment format: " + enchantString);
         }
      }
      if (enchants.isEmpty()) {
         return Arrays.asList(
                 new LeveledEnchantment(Enchantment.EFFICIENCY, 1),
                 new LeveledEnchantment(Enchantment.PROTECTION, 1),
                 new LeveledEnchantment(Enchantment.FEATHER_FALLING, 1),
                 new LeveledEnchantment(Enchantment.KNOCKBACK, 1),
                 new LeveledEnchantment(Enchantment.SWEEPING_EDGE, 1)
         );
      } else {
         return enchants;
      }
   }

   public int getTomeMiscCount() {
      return this.config.getInt("tome-chest-items.misc-items-count", 0);
   }

   public List<ItemStack> getTomeMiscTypes() {
      List<String> miscStrings = this.config.getStringList("tome-chest-items.misc-items");
      List<ItemStack> miscItems = new ArrayList<>();
      for (String miscString : miscStrings) {
         String[] parts = miscString.split(" ");
         try {
            assert parts.length <= 2;
            int count = 1;
            if (parts.length == 2) {
               count = Integer.parseInt(parts[1]);
            }
            Material mat = Material.getMaterial(parts[0]);
            if (mat == null) {
               this.plugin.getLogger().warning("Unknown item: " + parts[0]);
               continue;
            }
            miscItems.add(new ItemStack(mat, count));
         } catch (Exception e) {
            this.plugin.getLogger().warning("Invalid item format: " + miscString);
         }
      }
      return miscItems;
   }

   public boolean addTomeChestLocation(Location location) {
      List<String> locations = this.config.getStringList("tome-chests.locations");
      String locationString = String.format("%d,%d,%d", location.getBlockX(), location.getBlockY(), location.getBlockZ());
      if (locations.contains(locationString)) {
         return false;
      }

      locations.add(locationString);
      this.config.set("tome-chests.locations", locations);
      this.saveConfig();
      return true;
   }

   public boolean removeTomeChestLocation(Location location) {
      List<String> locations = this.config.getStringList("tome-chests.locations");
      String locationString = String.format("%d,%d,%d", location.getBlockX(), location.getBlockY(), location.getBlockZ());
      if (!locations.contains(locationString)) {
         return false;
      }

      locations.remove(locationString);
      this.config.set("tome-chests.locations", locations);
      this.saveConfig();
      return true;
   }

   public int getVampireBatCooldown() {
      return this.config.getInt("abilities.vampire.bat-cooldown", 900);
   }

   public int getVampireLungeCooldown() {
      return this.config.getInt("abilities.vampire.lunge-cooldown", 45);
   }

   public int getVampireVanishCooldown() {
      return this.config.getInt("abilities.vampire.vanish-cooldown", 420);
   }

   public int getVampireStormCallCooldown() {
      return this.config.getInt("abilities.vampire.storm-call-cooldown", 7200);
   }

   public int getVampireBeaconTeleportCooldown() {
      return this.config.getInt("abilities.vampire.beacon-teleport-cooldown", 300);
   }

   public int getVampireVisionCooldown() {
      return this.config.getInt("abilities.vampire.vampire-vision-cooldown", 1);
   }

   public int getTomeBlessingCooldown() {
      return this.config.getInt("abilities.tome.blessing-cooldown", 7200);
   }

   public int getTomeBanishUndeadCooldown() {
      return this.config.getInt("abilities.tome.banish-undead-cooldown", 900);
   }

   public int getTomeHolyWordCooldown() {
      return this.config.getInt("abilities.tome.holy-word-cooldown", 600);
   }

   public int getTomeEnlightenedEyeCooldown() {
      return this.config.getInt("abilities.tome.enlightened-eye-cooldown", 900);
   }

   public int getTomeLanternThrashCooldown() {
      return this.config.getInt("abilities.tome.lantern-thrash-cooldown", 300);
   }

   public int getTomePrayerOfFaithCooldown() {
      return this.config.getInt("abilities.tome.prayer-of-faith-cooldown", 900);
   }

   public int getTomeRallyingCryCooldown() {
      return this.config.getInt("abilities.tome.rallying-cry-cooldown", 1200);
   }

   public int getTomeShoulderBargeCooldown() {
      return this.config.getInt("abilities.tome.shoulder-barge-cooldown", 300);
   }

   public int getTomeTurnUndeadCooldown() {
      return this.config.getInt("abilities.tome.turn-undead-cooldown", 1800);
   }

   public int getTomeUncannyDirectionCooldown() {
      return this.config.getInt("abilities.tome.uncanny-direction-cooldown", 30);
   }

   public int getTomeUnnaturalHasteCooldown() {
      return this.config.getInt("abilities.tome.unnatural-haste-cooldown", 900);
   }

   public int getTomeWayOfTheLandCooldown() {
      return this.config.getInt("abilities.tome.way-of-the-land-cooldown", 600);
   }

   public int getTomeWayOfTheLumberjackCooldown() {
      return this.config.getInt("abilities.tome.way-of-the-lumberjack-cooldown", 600);
   }

   public int getTomeWayOfTheProspectorCooldown() {
      return this.config.getInt("abilities.tome.way-of-the-prospector-cooldown", 600);
   }

   public int getTomeStopTheBleedingCooldown() {
      return this.config.getInt("abilities.tome.stop-the-bleeding-cooldown", 7200);
   }

   public int getThirstDepletionMinutes() {
      return this.config.getInt("thirst.depletion-minutes", 120);
   }

   public int getMaxFeedingThirstPerSession() {
      return this.config.getInt("thirst.max-feeding-per-session", 60);
   }

   public Location getVampireRespawnLocation(World world) {
      String locationStr = this.config.getString("vampire.respawn-location", "40,101,-113");
      String[] parts = locationStr.split(",");

      try {
         double x = Double.parseDouble(parts[0].trim());
         double y = Double.parseDouble(parts[1].trim());
         double z = Double.parseDouble(parts[2].trim());
         return new Location(world, x, y, z);
      } catch (Exception e) {
         this.plugin.getLogger().warning("Invalid vampire respawn location format: " + locationStr + ". Using default.");
         return new Location(world, 40.0, 101.0, -113.0);
      }
   }

   public void setVampireRespawnLocation(String locationStr) {
      this.config.set("vampire.respawn-location", locationStr);
      this.plugin.saveConfig();
   }

   public boolean getDisableTurningAfterTurn() { return this.config.getBoolean("vampire.disable-turning-after-turn", true); }

   public double getCureBeaconDistance() {
      return this.config.getDouble("cure.cure-distance", 25.0);
   }

   public boolean getCureAllowCuredSire() { return this.config.getBoolean("cure.allow-cured-sire", false); }

   public boolean getCureNeedsDaytime() { return this.config.getBoolean("cure.needs-daytime", true); }

   public boolean getTrackingEnabled() { return this.config.getBoolean("vampire-indicator.enabled", true); }

   public int getTrackingDurationSeconds() { return this.config.getInt("vampire-indicator.duration-seconds", 120); }

   public double getTrackingDistance() { return this.config.getDouble("vampire-indicator.distance", 0); }

   public boolean getTrackingArrowEnabled() { return this.config.getBoolean("vampire-indicator.tracker", true); }

   public int getTrackingMinimumStage() { return this.config.getInt("vampire-indicator.minimum-stage", 1); }

   public long getBeaconConversionTimeMs() {
      long seconds = this.config.getLong("beacons.conversion-time-seconds", 300L);
      return seconds * 1000L;
   }

   public long getBeaconConversionCooldownMs() {
      long minutes = this.config.getLong("beacons.conversion-cooldown-minutes", 60L);
      return minutes * 60L * 1000L;
   }

   public double getBeaconHumanSpeedMultiplier() {
      return this.config.getDouble("beacons.human-speed-multiplier", 1.5);
   }

   public double getBeaconFinalStandMultiplier() {
      return this.config.getDouble("beacons.final-stand-multiplier", 6.0);
   }

   public int getBeaconNeutralAnnouncementDelaySeconds() {
      return this.config.getInt("beacons.neutral-announcement-delay-seconds", 60);
   }

   public boolean doCorruptedBeaconsTrapHumans() {
      return this.config.getBoolean("beacons.corrupted-beacons-trap-humans", true);
   }

   public int getGarlicProcessingTimeMin() {
      return this.config.getInt("garlic.processing-time-min-seconds", 480);
   }

   public int getGarlicProcessingTimeMax() {
      return this.config.getInt("garlic.processing-time-max-seconds", 720);
   }

   public int getGarlicImmunityDurationMin() {
      return this.config.getInt("garlic.immunity-duration-min-seconds", 480);
   }

   public int getGarlicImmunityDurationMax() {
      return this.config.getInt("garlic.immunity-duration-max-seconds", 600);
   }

   public boolean isNonEssentialLoggingDisabled() {
      return this.config.getBoolean("disable-nonessential-logging", false);
   }

   public int getHolyWaterDisableDurationSeconds() {
      return this.config.getInt("holy-water.disable-duration-seconds", 120);
   }

   public boolean isPassiveMobAutoSpawnEnabled() {
      return this.config.getBoolean("passive-mob-spawning.auto-spawn-enabled", true);
   }

   public int getPassiveMobMinimumThreshold() {
      return this.config.getInt("passive-mob-spawning.minimum-animal-threshold", 40);
   }

   public int getPassiveMobSpawnCount() {
      return this.config.getInt("passive-mob-spawning.spawn-count", 80);
   }

   public int getPermadeathMinimumStage() {
      return this.config.getInt("combat.permadeath-minimum-stage", 1);
   }

   public int getWoodenStakeCooldownTicks() {
      return this.config.getInt("combat.wooden-stake-cooldown-ticks", 80);
   }

   public String getOakhurstName() {
      return this.config.getString("oakhurst.name", "Oakhurst");
   }

   public double getOakhurstTownCenterX() {
      return this.config.getDouble("oakhurst.town-center-x", 79.0);
   }

   public double getOakhurstTownCenterZ() {
      return this.config.getDouble("oakhurst.town-center-z", 440.0);
   }

   public double getOakhurstTeleportRadius() {
      return this.config.getDouble("oakhurst.teleport-radius", 400.0);
   }

   public double getOakhurstBorderCenterX() {
      return this.config.getDouble("oakhurst.border.center-x", 50.0);
   }

   public double getOakhurstBorderCenterZ() {
      return this.config.getDouble("oakhurst.border.center-z", 50.0);
   }

   public double getOakhurstBorderDiameter() {
      return this.config.getDouble("oakhurst.border.diameter", 1098.0);
   }

   public double getOakhurstBorderRadius() {
      return this.getOakhurstBorderDiameter() / 2.0;
   }

   public double getOakhurstMinX() {
      return this.getOakhurstBorderCenterX() - this.getOakhurstBorderRadius();
   }

   public double getOakhurstMaxX() {
      return this.getOakhurstBorderCenterX() + this.getOakhurstBorderRadius();
   }

   public double getOakhurstMinZ() {
      return this.getOakhurstBorderCenterZ() - this.getOakhurstBorderRadius();
   }

   public double getOakhurstMaxZ() {
      return this.getOakhurstBorderCenterZ() + this.getOakhurstBorderRadius();
   }

   public boolean isLocationWithinBorder(double x, double z) {
      return x >= this.getOakhurstMinX() && x <= this.getOakhurstMaxX() && z >= this.getOakhurstMinZ() && z <= this.getOakhurstMaxZ();
   }

   public List<String> validateConfiguredLocations(BeaconManager beaconManager) {
      List<String> warnings = new ArrayList<>();
      double townX = this.getOakhurstTownCenterX();
      double townZ = this.getOakhurstTownCenterZ();
      if (!this.isLocationWithinBorder(townX, townZ)) {
         warnings.add("Town center (" + (int)townX + ", " + (int)townZ + ")");
      }

      World world = this.plugin.getServer().getWorld("world");
      if (world != null) {
         Location vampireSpawn = this.getVampireRespawnLocation(world);
         if (!this.isLocationWithinBorder(vampireSpawn.getX(), vampireSpawn.getZ())) {
            warnings.add("Vampire respawn (" + vampireSpawn.getBlockX() + ", " + vampireSpawn.getBlockZ() + ")");
         }

         for (Location loc : this.getTomeChestLocations()) {
            if (!this.isLocationWithinBorder(loc.getX(), loc.getZ())) {
               warnings.add("Tome chest at (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")");
            }
         }
      }

      if (beaconManager != null) {
         for (BeaconSite beacon : beaconManager.getAllBeacons()) {
            Location loc = beacon.getLocation();
            if (!this.isLocationWithinBorder(loc.getX(), loc.getZ())) {
               warnings.add("Beacon '" + beacon.getName() + "' (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")");
            }
         }
      }

      return warnings;
   }
}
