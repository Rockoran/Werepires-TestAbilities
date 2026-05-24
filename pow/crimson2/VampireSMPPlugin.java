package pow.crimson2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import pow.crimson2.commands.BrigadierCommands;
import pow.crimson2.listeners.BatTransformationListener;
import pow.crimson2.listeners.BeaconConversionListener;
import pow.crimson2.listeners.BeaconTeleportListener;
import pow.crimson2.listeners.BeetrootHarvestListener;
import pow.crimson2.listeners.BeetrootListener;
import pow.crimson2.listeners.BlockListener;
import pow.crimson2.listeners.BloodMoonAttributeListener;
import pow.crimson2.listeners.CombatListener;
import pow.crimson2.listeners.CureBookReadingListener;
import pow.crimson2.listeners.DamageSuppressionListener;
import pow.crimson2.listeners.DeathHandler;
import pow.crimson2.listeners.EndermanRemovalListener;
import pow.crimson2.listeners.ExperienceBottleListener;
import pow.crimson2.listeners.FeedingListener;
import pow.crimson2.listeners.ForcedCureChoiceListener;
import pow.crimson2.listeners.FourthBookRevealListener;
import pow.crimson2.listeners.InitGameListener;
import pow.crimson2.listeners.InteractionListener;
import pow.crimson2.listeners.IronWeaknessListener;
import pow.crimson2.listeners.MovementBoundaryListener;
import pow.crimson2.listeners.NoSleepListener;
import pow.crimson2.listeners.PlayerJoinListener;
import pow.crimson2.listeners.ThirstEffectsListener;
import pow.crimson2.listeners.TomeListener;
import pow.crimson2.listeners.TomeVampireRestrictionListener;
import pow.crimson2.listeners.VampireCraftBlocker;
import pow.crimson2.listeners.VampireFallDamageListener;
import pow.crimson2.listeners.WeaponDropRemover;
import pow.crimson2.managers.BatTransformationManager;
import pow.crimson2.managers.BeaconMajorityManager;
import pow.crimson2.managers.BeaconManager;
import pow.crimson2.managers.BeetrootManager;
import pow.crimson2.managers.BloodMoonManager;
import pow.crimson2.managers.ConfigManager;
import pow.crimson2.managers.EffectManager;
import pow.crimson2.managers.ForcedCureChoiceManager;
import pow.crimson2.managers.HolyWaterEffectManager;
import pow.crimson2.managers.InitGameManager;
import pow.crimson2.managers.MobTeamManager;
import pow.crimson2.managers.PassiveMobSpawningManager;
import pow.crimson2.managers.PermadeathManager;
import pow.crimson2.managers.PlayerChatManager;
import pow.crimson2.managers.SessionManager;
import pow.crimson2.managers.ThirstManager;
import pow.crimson2.managers.TomeDistributionManager;
import pow.crimson2.managers.TomeManager;
import pow.crimson2.managers.VampireAbilityManager;
import pow.crimson2.managers.VampireFeedingManager;
import pow.crimson2.managers.VampireManager;
import pow.crimson2.managers.VampireSireManager;
import pow.crimson2.managers.VampireTexturePackManager;
import pow.crimson2.managers.VampireTrackingManager;
import pow.crimson2.managers.VampireTurningManager;

public class VampireSMPPlugin extends JavaPlugin {
   public static final String WORLD_NAME = "world";
   private ConfigManager configManager;
   private SessionManager sessionManager;
   private VampireManager vampireManager;
   private EffectManager effectManager;
   private DeathHandler deathHandler;
   private BloodMoonManager bloodMoonManager;
   private PlayerChatManager playerChatManager;
   private VampireAbilityManager vampireAbilityManager;
   private IronWeaknessListener ironWeaknessListener;
   private BeaconManager beaconManager;
   private BeetrootManager beetrootManager;
   private MobTeamManager mobTeamManager;
   private BatTransformationManager batTransformationManager;
   private ThirstManager thirstManager;
   private FeedingListener feedingListener;
   private ThirstEffectsListener thirstEffectsListener;
   private BeaconConversionListener beaconConversionListener;
   private BeaconTeleportListener beaconTeleportListener;
   private TomeManager tomeManager;
   private HolyWaterEffectManager holyWaterEffectManager;
   private VampireFeedingManager vampireFeedingManager;
   private BloodMoonAttributeListener bloodMoonAttributeListener;
   private BeaconMajorityManager beaconMajorityManager;
   private TomeVampireRestrictionListener tomeVampireRestrictionListener;
   private TomeDistributionManager tomeDistributionManager;
   private VampireTexturePackManager vampireTexturePackManager;
   private EndermanRemovalListener endermanRemovalListener;
   private DamageSuppressionListener damageSuppressionListener;
   private VampireTrackingManager vampireTrackingManager;
   private PermadeathManager permadeathManager;
   private PassiveMobSpawningManager passiveMobSpawningManager;
   private VampireTurningManager vampireTurningManager;
   private VampireSireManager sireManager;
   private ForcedCureChoiceManager forcedCureChoiceManager;
   private InitGameManager initGameManager;
   private CureBookReadingListener cureBookReadingListener;
   private World world;
   private Team castTeam;
   private Team vampireCastTeam;
   private Location vampireRespawnLocation;

   public void onEnable() {
      this.saveDefaultConfig();
      this.configManager = new ConfigManager(this);
      this.world = Bukkit.getWorld("world");
      this.initializeCastTeam();
      this.initializeVampireCastTeam();
      this.sessionManager = new SessionManager(this);
      this.sessionManager.initializeScoreboard();
      this.sessionManager.startBackgroundTasks();
      this.vampireManager = new VampireManager(this);
      this.thirstManager = new ThirstManager(this, this.configManager);
      this.beaconManager = new BeaconManager(this);
      this.effectManager = new EffectManager(this);
      this.deathHandler = new DeathHandler(this, this.vampireManager);
      this.bloodMoonManager = new BloodMoonManager(this);
      this.ironWeaknessListener = new IronWeaknessListener(this, this.vampireManager);
      this.feedingListener = new FeedingListener(this);
      this.thirstEffectsListener = new ThirstEffectsListener(this);
      this.thirstEffectsListener.startTasks();
      this.playerChatManager = new PlayerChatManager(this);
      this.vampireAbilityManager = new VampireAbilityManager(this);
      this.beaconConversionListener = new BeaconConversionListener(this);
      this.beaconTeleportListener = new BeaconTeleportListener(this);
      this.beetrootManager = new BeetrootManager(this);
      this.mobTeamManager = new MobTeamManager(this);
      this.batTransformationManager = new BatTransformationManager(this);
      this.tomeManager = new TomeManager(this);
      this.holyWaterEffectManager = new HolyWaterEffectManager(this);
      this.vampireFeedingManager = new VampireFeedingManager(this);
      this.beaconMajorityManager = new BeaconMajorityManager(this);
      this.tomeDistributionManager = new TomeDistributionManager(this, this.configManager);
      this.vampireTexturePackManager = new VampireTexturePackManager(this);
      this.endermanRemovalListener = new EndermanRemovalListener(this);
      this.damageSuppressionListener = new DamageSuppressionListener(this);
      this.vampireTrackingManager = new VampireTrackingManager(this);
      this.permadeathManager = new PermadeathManager(this);
      this.passiveMobSpawningManager = new PassiveMobSpawningManager(this, this.configManager);
      this.vampireTurningManager = new VampireTurningManager(this);
      this.sireManager = new VampireSireManager(this);
      this.forcedCureChoiceManager = new ForcedCureChoiceManager(this);
      this.initGameManager = new InitGameManager(this);
      this.getServer().getPluginManager().registerEvents(this.damageSuppressionListener, this);
      this.getServer().getPluginManager().registerEvents(this.deathHandler, this);
      this.getServer().getPluginManager().registerEvents(new CombatListener(this, this.vampireManager), this);
      this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, this.vampireManager, this.effectManager), this);
      this.getServer().getPluginManager().registerEvents(new BlockListener(this, this.sessionManager), this);
      this.getServer().getPluginManager().registerEvents(new VampireCraftBlocker(this), this);
      this.getServer().getPluginManager().registerEvents(this.ironWeaknessListener, this);
      this.getServer().getPluginManager().registerEvents(this.feedingListener, this);
      this.getServer().getPluginManager().registerEvents(this.thirstEffectsListener, this);
      this.getServer().getPluginManager().registerEvents(new NoSleepListener(this), this);
      this.getServer().getPluginManager().registerEvents(this.playerChatManager, this);
      this.getServer().getPluginManager().registerEvents(new VampireFallDamageListener(this.vampireManager), this);
      this.getServer().getPluginManager().registerEvents(this.beaconConversionListener, this);
      this.getServer().getPluginManager().registerEvents(this.beaconTeleportListener, this);
      this.getServer().getPluginManager().registerEvents(new BeetrootListener(this), this);
      this.getServer().getPluginManager().registerEvents(new WeaponDropRemover(this), this);
      this.getServer().getPluginManager().registerEvents(new InteractionListener(this, this.sessionManager), this);
      this.getServer().getPluginManager().registerEvents(new BatTransformationListener(this), this);
      this.getServer().getPluginManager().registerEvents(new ExperienceBottleListener(this), this);
      this.cureBookReadingListener = new CureBookReadingListener(this);
      this.getServer().getPluginManager().registerEvents(this.cureBookReadingListener, this);
      this.getServer().getPluginManager().registerEvents(new TomeListener(this), this);
      this.getServer().getPluginManager().registerEvents(new BeetrootHarvestListener(this), this);
      this.tomeVampireRestrictionListener = new TomeVampireRestrictionListener(this);
      this.getServer().getPluginManager().registerEvents(this.tomeVampireRestrictionListener, this);
      this.getServer().getPluginManager().registerEvents(this.endermanRemovalListener, this);
      this.getServer().getPluginManager().registerEvents(new MovementBoundaryListener(this), this);
      this.getServer().getPluginManager().registerEvents(new FourthBookRevealListener(this, this.configManager), this);
      this.getServer().getPluginManager().registerEvents(new ForcedCureChoiceListener(this), this);
      this.getServer().getPluginManager().registerEvents(new InitGameListener(this), this);
      this.bloodMoonAttributeListener = new BloodMoonAttributeListener(this);
      this.getServer().getPluginManager().registerEvents(this.bloodMoonAttributeListener, this);
      BrigadierCommands brigadierCommands = new BrigadierCommands(this);
      brigadierCommands.registerAll();
      this.initializeDeathScoreboard();
      this.effectManager.startEffectTask();
      this.beaconManager.validateBeacons();
      this.initVampireRespawnLocation();
      this.sessionManager.executeServerCommand("tick freeze");
      this.logInfo("VampireSMP Plugin has been enabled!");
   }

   public void onDisable() {
      if (this.effectManager != null) {
         this.effectManager.stopEffectTask();
         this.effectManager.shutdown();
      }

      if (this.thirstManager != null) {
         this.thirstManager.shutdown();
      }

      if (this.vampireManager != null) {
         this.vampireManager.shutdown();
      }

      if (this.sessionManager.isSessionActive()) {
         this.sessionManager.pauseSession();
      } else if (this.sessionManager.getSessionState() == 3) {
         this.sessionManager.primeNewSession();
      }

      if (this.vampireAbilityManager != null) {
         this.vampireAbilityManager.shutdown();
      }

      if (this.bloodMoonManager != null) {
         this.bloodMoonManager.shutdown();
      }

      if (this.beaconManager != null) {
         this.beaconManager.shutdown();
      }

      if (this.beaconConversionListener != null) {
         this.beaconConversionListener.shutdown();
      }

      if (this.beetrootManager != null) {
         this.beetrootManager.shutdown();
      }

      if (this.tomeDistributionManager != null) {
         this.tomeDistributionManager.shutdown();
      }

      if (this.passiveMobSpawningManager != null) {
         this.passiveMobSpawningManager.shutdown();
      }

      if (this.mobTeamManager != null) {
         this.mobTeamManager.shutdown();
      }

      if (this.batTransformationManager != null) {
         this.batTransformationManager.shutdown();
      }

      if (this.tomeManager != null) {
         this.tomeManager.shutdown();
      }

      if (this.holyWaterEffectManager != null) {
         this.holyWaterEffectManager.shutdown();
      }

      if (this.vampireFeedingManager != null) {
         this.vampireFeedingManager.shutdown();
      }

      if (this.bloodMoonAttributeListener != null) {
         this.bloodMoonAttributeListener.shutdown();
      }

      if (this.beaconMajorityManager != null) {
         this.beaconMajorityManager.shutdown();
      }

      if (this.vampireTexturePackManager != null) {
         this.vampireTexturePackManager.shutdown();
      }

      if (this.endermanRemovalListener != null) {
         this.endermanRemovalListener.shutdown();
      }

      if (this.vampireTrackingManager != null) {
         this.vampireTrackingManager.shutdown();
      }

      if (this.permadeathManager != null) {
         this.permadeathManager.shutdown();
      }

      if (this.vampireTurningManager != null) {
         this.vampireTurningManager.shutdown();
      }

      if (this.sireManager != null) {
         this.sireManager.shutdown();
      }

      if (this.forcedCureChoiceManager != null) {
         this.forcedCureChoiceManager.shutdown();
      }

      this.logInfo("VampireSMP Plugin has been disabled!");
   }

   private void initializeCastTeam() {
      try {
         Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
         Team existingTeam = mainScoreboard.getTeam("CastTeam");
         if (existingTeam != null) {
            this.castTeam = existingTeam;
            this.logInfo("Found existing CastTeam, updating settings...");
         } else {
            this.castTeam = mainScoreboard.registerNewTeam("CastTeam");
            this.logInfo("Created new CastTeam for name tag management.");
         }

         this.castTeam.setNameTagVisibility(NameTagVisibility.NEVER);
         this.castTeam.setDisplayName("§6Human Team");
         this.castTeam.setCanSeeFriendlyInvisibles(false);
         this.logInfo("CastTeam initialized successfully with hidden name tags.");
      } catch (Exception e) {
         this.getLogger().severe("Failed to initialize CastTeam: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void initializeDeathScoreboard() {
      try {
         Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
         String objectiveName = "vsmp_death";
         Objective existingObjective = mainScoreboard.getObjective(objectiveName);
         if (existingObjective != null) {
            String criteria = existingObjective.getCriteria();
            if ("deathCount".equals(criteria)) {
               this.logInfo("Migrating death scoreboard from 'deathCount' to 'dummy' criteria...");
               existingObjective.unregister();
               mainScoreboard.registerNewObjective(objectiveName, "dummy", "Deaths");
               this.logInfo("Migration complete - death scoreboard now uses 'dummy' criteria.");
            } else {
               this.logInfo("Found existing death scoreboard objective with correct criteria.");
            }
         } else {
            Objective deathObjective = mainScoreboard.registerNewObjective(objectiveName, "dummy", "Deaths");
            this.logInfo("Created new death scoreboard objective with 'dummy' criteria.");
         }
      } catch (Exception e) {
         this.getLogger().severe("Failed to initialize death scoreboard: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void initializeVampireCastTeam() {
      try {
         Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
         Team existingTeam = mainScoreboard.getTeam("VampireCastTeam");
         if (existingTeam != null) {
            this.vampireCastTeam = existingTeam;
            this.logInfo("Found existing VampireCastTeam, updating settings...");
         } else {
            this.vampireCastTeam = mainScoreboard.registerNewTeam("VampireCastTeam");
            this.logInfo("Created new VampireCastTeam for name tag management.");
         }

         this.vampireCastTeam.setNameTagVisibility(NameTagVisibility.NEVER);
         this.vampireCastTeam.setCanSeeFriendlyInvisibles(false);
         this.vampireCastTeam.setDisplayName("§4Vampire Team");
         this.logInfo("VampireCastTeam initialized successfully with hidden name tags.");
      } catch (Exception e) {
         this.getLogger().severe("Failed to initialize VampireCastTeam: " + e.getMessage());
         e.printStackTrace();
      }
   }

   private void initVampireRespawnLocation() {
      this.vampireRespawnLocation = this.configManager.getVampireRespawnLocation(this.getWorld());
      this.logInfo(
         "Vampire respawn location set to: "
            + this.vampireRespawnLocation.getBlockX()
            + ", "
            + this.vampireRespawnLocation.getBlockY()
            + ", "
            + this.vampireRespawnLocation.getBlockZ()
      );
   }

   public Location getVampireRespawnLocation() {
      return this.vampireRespawnLocation;
   }

   public void reloadVampireRespawnLocation() {
      this.initVampireRespawnLocation();
   }

   public BatTransformationManager getBatTransformationManager() {
      return this.batTransformationManager;
   }

   public Team getVampireCastTeam() {
      return this.vampireCastTeam;
   }

   public World getWorld() {
      return this.world;
   }

   public BeetrootManager getBeetrootManager() {
      return this.beetrootManager;
   }

   public PlayerChatManager getPlayerChatManager() {
      return this.playerChatManager;
   }

   public VampireAbilityManager getVampireAbilityManager() {
      return this.vampireAbilityManager;
   }

   public IronWeaknessListener getIronWeaknessListener() {
      return this.ironWeaknessListener;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public SessionManager getSessionManager() {
      return this.sessionManager;
   }

   public VampireManager getVampireManager() {
      return this.vampireManager;
   }

   public EffectManager getEffectManager() {
      return this.effectManager;
   }

   public DeathHandler getDeathHandler() {
      return this.deathHandler;
   }

   public BeaconManager getBeaconManager() {
      return this.beaconManager;
   }

   public BeaconConversionListener getBeaconConversionListener() {
      return this.beaconConversionListener;
   }

   public BeaconTeleportListener getBeaconTeleportListener() {
      return this.beaconTeleportListener;
   }

   public ThirstManager getThirstManager() {
      return this.thirstManager;
   }

   public VampireFeedingManager getVampireFeedingManager() {
      return this.vampireFeedingManager;
   }

   public TomeManager getTomeManager() {
      return this.tomeManager;
   }

   public HolyWaterEffectManager getHolyWaterEffectManager() {
      return this.holyWaterEffectManager;
   }

   public BloodMoonAttributeListener getBloodMoonAttributeListener() {
      return this.bloodMoonAttributeListener;
   }

   public BloodMoonManager getBloodMoonManager() {
      return this.bloodMoonManager;
   }

   public BeaconMajorityManager getBeaconMajorityManager() {
      return this.beaconMajorityManager;
   }

   public TomeVampireRestrictionListener getTomeVampireRestrictionListener() {
      return this.tomeVampireRestrictionListener;
   }

   public TomeDistributionManager getTomeDistributionManager() {
      return this.tomeDistributionManager;
   }

   public VampireTexturePackManager getVampireTexturePackManager() {
      return this.vampireTexturePackManager;
   }

   public VampireTrackingManager getVampireTrackingManager() {
      return this.vampireTrackingManager;
   }

   public PermadeathManager getPermadeathManager() {
      return this.permadeathManager;
   }

   public PassiveMobSpawningManager getPassiveMobSpawningManager() {
      return this.passiveMobSpawningManager;
   }

   public EndermanRemovalListener getEndermanRemovalListener() {
      return this.endermanRemovalListener;
   }

   public VampireTurningManager getVampireTurningManager() {
      return this.vampireTurningManager;
   }

   public VampireSireManager getSireManager() {
      return this.sireManager;
   }

   public ForcedCureChoiceManager getForcedCureChoiceManager() {
      return this.forcedCureChoiceManager;
   }

   public InitGameManager getInitGameManager() {
      return this.initGameManager;
   }

   public CureBookReadingListener getCureBookReadingListener() {
      return this.cureBookReadingListener;
   }

   public void logInfo(String message) {
      if (!this.getConfigManager().isNonEssentialLoggingDisabled()) {
         this.getLogger().info(message);
      }
   }

   public Team getCastTeam() {
      return this.castTeam;
   }
}
