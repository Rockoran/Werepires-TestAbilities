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
import pow.crimson2.world.WorldManager;
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
import pow.crimson2.listeners.WerewolfBitingListener;
import pow.crimson2.listeners.WerewolfDietListener;
import pow.crimson2.thralls.BloodConsumeListener;
import pow.crimson2.thralls.BloodDrawListener;
import pow.crimson2.thralls.ThrallCommand;
import pow.crimson2.thralls.ThrallHolyWaterListener;
import pow.crimson2.thralls.ThrallInventoryListener;
import pow.crimson2.thralls.ThrallJoinQuitListener;
import pow.crimson2.thralls.ThrallManager;
import pow.crimson2.thralls.ThrallStayListener;
import pow.crimson2.roles.RoleCommand;
import pow.crimson2.roles.RoleManager;
import pow.crimson2.roles.TrackerListener;
import pow.crimson2.kit.StarterKitCommand;
import pow.crimson2.kit.StarterKitManager;
import pow.crimson2.setup.PlayerSetupManager;
import pow.crimson2.gamestart.GameStartCommand;
import pow.crimson2.gamestart.GameStartManager;
import pow.crimson2.managers.WerewolfAbilityManager;
import pow.crimson2.managers.WerewolfHungerManager;
import pow.crimson2.managers.WerewolfPackManager;
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
import pow.crimson2.commands.SkinCommand;
import pow.crimson2.managers.SkinShuffleManager;
import pow.crimson2.managers.VampireAbilityManager;
import pow.crimson2.managers.VampireFeedingManager;
import pow.crimson2.managers.VampireManager;
import pow.crimson2.managers.VampireSireManager;
import pow.crimson2.managers.VampireTexturePackManager;
import pow.crimson2.managers.VampireTrackingManager;
import pow.crimson2.managers.FadeManager;
import pow.crimson2.managers.FaeManager;
import pow.crimson2.managers.TurnLockManager;
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
   private pow.crimson2.managers.VaultManager vaultManager;
   private pow.crimson2.managers.RevivalBookManager revivalBookManager;
   private pow.crimson2.managers.GhoulManager ghoulManager;
   private VampireTexturePackManager vampireTexturePackManager;
   private EndermanRemovalListener endermanRemovalListener;
   private DamageSuppressionListener damageSuppressionListener;
   private MovementBoundaryListener movementBoundaryListener;
   private VampireTrackingManager vampireTrackingManager;
   private PermadeathManager permadeathManager;
   private PassiveMobSpawningManager passiveMobSpawningManager;
   private VampireTurningManager vampireTurningManager;
   private TurnLockManager turnLockManager;
   private FaeManager faeManager;
   private FadeManager fadeManager;
   private VampireSireManager sireManager;
   private ForcedCureChoiceManager forcedCureChoiceManager;
   private InitGameManager initGameManager;
   private CureBookReadingListener cureBookReadingListener;
   private WerewolfAbilityManager werewolfAbilityManager;
   private WerewolfHungerManager werewolfHungerManager;
   private WerewolfPackManager werewolfPackManager;
   private WerewolfBitingListener werewolfBitingListener;
   private WerewolfDietListener werewolfDietListener;
   private ThrallManager thrallManager;
   private RoleManager roleManager;
   private RoleCommand roleCommand;
   private StarterKitManager starterKitManager;
   private StarterKitCommand starterKitCommand;
   private PlayerSetupManager playerSetupManager;
   private GameStartManager gameStartManager;
   private GameStartCommand gameStartCommand;
   private WorldManager worldManager;
   private SkinShuffleManager skinShuffleManager;
   private pow.crimson2.items.SilverArrowManager silverArrowManager;
   private pow.crimson2.ghost.GhostModeManager ghostModeManager;
   private pow.crimson2.ghost.ModGateManager modGateManager;
   private pow.crimson2.network.WerePiresNetwork werePiresNetwork;
   private pow.crimson2.world.WorldPackManager worldPackManager;
   private org.bukkit.configuration.file.YamlConfiguration stateConfig;
   private java.io.File stateConfigFile;
   private Team castTeam;
   private Team vampireCastTeam;
   private Location vampireRespawnLocation;

   public void onEnable() {
      this.saveDefaultConfig();
      this.loadStateConfig();
      this.configManager = new ConfigManager(this);
      this.worldManager = new WorldManager(this);
      // world reference is now resolved dynamically via worldManager.getActiveWorld()
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
      this.vaultManager = new pow.crimson2.managers.VaultManager(this);
      this.vampireTexturePackManager = new VampireTexturePackManager(this);
      this.endermanRemovalListener = new EndermanRemovalListener(this);
      this.damageSuppressionListener = new DamageSuppressionListener(this);
      this.vampireTrackingManager = new VampireTrackingManager(this);
      this.permadeathManager = new PermadeathManager(this);
      this.passiveMobSpawningManager = new PassiveMobSpawningManager(this, this.configManager);
      this.vampireTurningManager = new VampireTurningManager(this);
      this.turnLockManager = new TurnLockManager(this);
      this.faeManager = new FaeManager(this);
      this.fadeManager = new FadeManager(this);
      this.sireManager = new VampireSireManager(this);
      this.forcedCureChoiceManager = new ForcedCureChoiceManager(this);
      this.initGameManager = new InitGameManager(this);
      this.werewolfAbilityManager = new WerewolfAbilityManager(this);
      this.werewolfHungerManager = new WerewolfHungerManager(this, this.configManager);
      this.werewolfPackManager = new WerewolfPackManager(this);
      this.werewolfBitingListener = new WerewolfBitingListener(this);
      this.werewolfDietListener = new WerewolfDietListener(this);
      this.thrallManager = new ThrallManager(this);
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
      this.movementBoundaryListener = new MovementBoundaryListener(this);
      this.getServer().getPluginManager().registerEvents(this.movementBoundaryListener, this);
      this.getServer().getPluginManager().registerEvents(new FourthBookRevealListener(this, this.configManager), this);
      this.getServer().getPluginManager().registerEvents(new ForcedCureChoiceListener(this), this);
      this.getServer().getPluginManager().registerEvents(new InitGameListener(this), this);
      this.bloodMoonAttributeListener = new BloodMoonAttributeListener(this);
      this.getServer().getPluginManager().registerEvents(this.bloodMoonAttributeListener, this);
      this.getServer().getPluginManager().registerEvents(new pow.crimson2.listeners.VampireStrengthImmunityListener(this), this);
      this.silverArrowManager = new pow.crimson2.items.SilverArrowManager(this);
      this.getServer().getPluginManager().registerEvents(this.silverArrowManager, this);
      this.getServer().getPluginManager().registerEvents(new pow.crimson2.listeners.VaultLootListener(this), this);
      this.getServer().getPluginManager().registerEvents(new pow.crimson2.listeners.VaultChunkListener(this), this);
      // Revival rite: books + ghoul state.
      this.revivalBookManager = new pow.crimson2.managers.RevivalBookManager(this);
      this.ghoulManager = new pow.crimson2.managers.GhoulManager(this);
      this.getServer().getPluginManager().registerEvents(this.revivalBookManager, this);
      this.getServer().getPluginManager().registerEvents(this.ghoulManager, this);
      // Re-apply vault block config for any vaults already in loaded chunks.
      this.getServer().getScheduler().runTaskLater(this, () -> {
         if (this.vaultManager != null) {
            this.vaultManager.cleanupLegacyDisplays();
            this.vaultManager.configureLoadedVaults();
         }
      }, 60L);
      this.ghostModeManager = new pow.crimson2.ghost.GhostModeManager(this);
      this.getServer().getPluginManager().registerEvents(this.ghostModeManager, this);
      this.getServer().getMessenger().registerOutgoingPluginChannel(
              this, pow.crimson2.ghost.GhostModeManager.GHOST_CHANNEL);
      // Register the ghost voice-haunt add-on with Simple Voice Chat, ONLY if it's installed.
      // The presence check must happen before any voicechat type is referenced — all such
      // references live in VoicechatHook so this method never forces loading the SVC API.
      if (this.getServer().getPluginManager().getPlugin("voicechat") != null) {
         try {
            if (pow.crimson2.ghost.VoicechatHook.register(this)) {
               this.logInfo("Registered ghost voice-haunt with Simple Voice Chat.");
            } else {
               this.logInfo("Simple Voice Chat service unavailable — ghost voice haunt disabled.");
            }
         } catch (Throwable t) {
            this.getLogger().warning("Failed to register ghost voice-haunt SVC plugin: " + t.getMessage());
         }
      } else {
         this.logInfo("Simple Voice Chat not detected — ghost voice haunt disabled.");
      }
      // Compatibility-mod gate: require the client mod (handshake key) or kick.
      this.modGateManager = new pow.crimson2.ghost.ModGateManager(this);
      this.getServer().getMessenger().registerIncomingPluginChannel(
              this, pow.crimson2.ghost.ModGateManager.CHANNEL, this.modGateManager);
      this.getServer().getMessenger().registerOutgoingPluginChannel(
              this, pow.crimson2.ghost.ModGateManager.CHANNEL);
      pow.crimson2.ghost.GhostCommand ghostCommand = new pow.crimson2.ghost.GhostCommand(this);
      this.getCommand("ghost").setExecutor(ghostCommand);
      this.getCommand("ghost").setTabCompleter(ghostCommand);
      BrigadierCommands brigadierCommands = new BrigadierCommands(this);
      brigadierCommands.registerAll();
      this.getServer().getPluginManager().registerEvents(this.werewolfBitingListener, this);
      this.getServer().getPluginManager().registerEvents(this.werewolfDietListener, this);
      this.getServer().getPluginManager().registerEvents(new BloodDrawListener(this), this);
      this.getServer().getPluginManager().registerEvents(new BloodConsumeListener(this), this);
      this.getServer().getPluginManager().registerEvents(new ThrallHolyWaterListener(this), this);
      this.getServer().getPluginManager().registerEvents(new ThrallStayListener(this), this);
      this.getServer().getPluginManager().registerEvents(new ThrallInventoryListener(this), this);
      this.getServer().getPluginManager().registerEvents(new ThrallJoinQuitListener(this), this);
      ThrallCommand thrallCommand = new ThrallCommand(this);
      this.getCommand("thrall").setExecutor(thrallCommand);
      this.getCommand("thrall").setTabCompleter(thrallCommand);

      // ── Roles ──────────────────────────────────────────────────────────────
      this.roleManager = new RoleManager(this);
      this.roleCommand = new RoleCommand(this);
      this.getServer().getPluginManager().registerEvents(new TrackerListener(this), this);
      this.getCommand("role").setExecutor(this.roleCommand);
      this.getCommand("role").setTabCompleter(this.roleCommand);
      this.getCommand("rolecfg").setExecutor(this.roleCommand);
      this.getCommand("rolecfg").setTabCompleter(this.roleCommand);
      this.getCommand("rolestart").setExecutor(this.roleCommand);
      this.getCommand("vampire").setExecutor(this.roleCommand);
      this.getCommand("gameadmin").setExecutor(this.roleCommand);
      this.getCommand("findvampires").setExecutor(this.roleCommand);

      // ── Starter Kit ────────────────────────────────────────────────────────
      this.starterKitManager = new StarterKitManager(this);
      this.starterKitCommand = new StarterKitCommand(this);
      this.getServer().getPluginManager().registerEvents(this.starterKitCommand, this);
      this.getCommand("kitstart").setExecutor(this.starterKitCommand);
      this.getCommand("kitstop").setExecutor(this.starterKitCommand);
      this.getCommand("kitall").setExecutor(this.starterKitCommand);
      this.getCommand("starterkit").setExecutor(this.starterKitCommand);
      this.getCommand("starterkit").setTabCompleter(this.starterKitCommand);
      this.getCommand("starterkitgive").setExecutor(this.starterKitCommand);
      this.getCommand("foodkitadd").setExecutor(this.starterKitCommand);
      this.getCommand("foodkitremove").setExecutor(this.starterKitCommand);
      this.getCommand("foodkitgive").setExecutor(this.starterKitCommand);

      // ── Player Setup ───────────────────────────────────────────────────────
      this.playerSetupManager = new PlayerSetupManager(this);
      this.getServer().getPluginManager().registerEvents(this.playerSetupManager, this);
      this.getCommand("playersetup").setExecutor(this.playerSetupManager);

      // ── Game Start ─────────────────────────────────────────────────────────
      this.gameStartManager = new GameStartManager(this);
      this.gameStartCommand = new GameStartCommand(this);
      this.getServer().getPluginManager().registerEvents(this.gameStartCommand, this);
      this.getCommand("gamestart").setExecutor(this.gameStartCommand);
      this.getCommand("gamestart").setTabCompleter(this.gameStartCommand);

      this.skinShuffleManager = new SkinShuffleManager(this);
      // Bridge the SkinShuffle Fabric mod → server skin changes (C2S)
      this.getServer().getMessenger().registerIncomingPluginChannel(
              this, SkinShuffleManager.CHANNEL, this.skinShuffleManager);
      // Outgoing channels: handshake (suppresses the mod's reconnect prompt) and
      // force_skin (pushes server-decided skins to the client without a reconnect).
      this.getServer().getMessenger().registerOutgoingPluginChannel(
              this, SkinShuffleManager.HANDSHAKE_CHANNEL);
      this.getServer().getMessenger().registerOutgoingPluginChannel(
              this, SkinShuffleManager.FORCE_SKIN_CHANNEL);
      // Per-player opacity for the Fading tome (see FadeManager).
      this.getServer().getMessenger().registerOutgoingPluginChannel(
              this, FadeManager.FADE_CHANNEL);
      SkinCommand skinCommand = new SkinCommand(this);
      this.getCommand("skin").setExecutor(skinCommand);
      this.getCommand("skin").setTabCompleter(skinCommand);
      this.werewolfPackManager.start();
      this.initializeDeathScoreboard();
      this.effectManager.startEffectTask();
      this.beaconManager.validateBeacons();
      this.initVampireRespawnLocation();
      this.sessionManager.executeServerCommand("tick freeze");
      this.werePiresNetwork = new pow.crimson2.network.WerePiresNetwork(this);
      this.werePiresNetwork.start();
      String networkKey = getConfig().getString("werepires-network.server-key", "");
      if (!networkKey.isEmpty()) {
         String ip = org.bukkit.Bukkit.getServer().getIp();
         int port = org.bukkit.Bukkit.getServer().getPort();
         String srvName = (ip.isEmpty() ? "0.0.0.0" : ip) + ":" + port;
         this.worldPackManager = new pow.crimson2.world.WorldPackManager(this, networkKey, srvName);
         this.worldPackManager.start();
      }
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

      if (this.faeManager != null) {
         this.faeManager.shutdown();
      }

      if (this.fadeManager != null) {
         this.fadeManager.shutdown();
         this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, FadeManager.FADE_CHANNEL);
      }

      if (this.sireManager != null) {
         this.sireManager.shutdown();
      }

      if (this.forcedCureChoiceManager != null) {
         this.forcedCureChoiceManager.shutdown();
      }

      if (this.werewolfAbilityManager != null) {
         this.werewolfAbilityManager.shutdown();
      }

      if (this.werewolfHungerManager != null) {
         this.werewolfHungerManager.shutdown();
      }

      if (this.werewolfBitingListener != null) {
         this.werewolfBitingListener.shutdown();
      }

      if (this.werewolfPackManager != null) {
         this.werewolfPackManager.shutdown();
      }

      if (this.thrallManager != null) {
         this.thrallManager.shutdown();
      }

      if (this.roleManager != null) {
         this.roleManager.shutdown();
      }

      if (this.gameStartManager != null) {
         this.gameStartManager.shutdown();
      }

      if (this.skinShuffleManager != null) {
         this.getServer().getMessenger().unregisterIncomingPluginChannel(
                 this, SkinShuffleManager.CHANNEL);
         this.getServer().getMessenger().unregisterOutgoingPluginChannel(
                 this, SkinShuffleManager.HANDSHAKE_CHANNEL);
         this.getServer().getMessenger().unregisterOutgoingPluginChannel(
                 this, SkinShuffleManager.FORCE_SKIN_CHANNEL);
         this.skinShuffleManager.shutdown();
      }

      if (this.silverArrowManager != null) {
         this.silverArrowManager.shutdown();
      }

      this.getServer().getMessenger().unregisterOutgoingPluginChannel(
              this, pow.crimson2.ghost.GhostModeManager.GHOST_CHANNEL);
      this.getServer().getMessenger().unregisterIncomingPluginChannel(
              this, pow.crimson2.ghost.ModGateManager.CHANNEL);
      this.getServer().getMessenger().unregisterOutgoingPluginChannel(
              this, pow.crimson2.ghost.ModGateManager.CHANNEL);

      if (this.worldPackManager != null) {
         this.worldPackManager.stop();
      }

      if (this.werePiresNetwork != null) {
         this.werePiresNetwork.stop();
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
      World w = this.getWorld();
      if (w == null) {
         this.getLogger().warning("initVampireRespawnLocation: active world not loaded yet — skipping");
         return;
      }
      this.vampireRespawnLocation = this.configManager.getVampireRespawnLocation(w);
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
      if (this.worldManager != null) return this.worldManager.getActiveWorld();
      return Bukkit.getWorld("world");
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

   public pow.crimson2.items.SilverArrowManager getSilverArrowManager() {
      return this.silverArrowManager;
   }

   public pow.crimson2.ghost.GhostModeManager getGhostModeManager() {
      return this.ghostModeManager;
   }

   public pow.crimson2.ghost.ModGateManager getModGateManager() {
      return this.modGateManager;
   }

   public SkinShuffleManager getSkinShuffleManager() {
      return this.skinShuffleManager;
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

   public MovementBoundaryListener getMovementBoundaryListener() {
      return this.movementBoundaryListener;
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

   public pow.crimson2.managers.RevivalBookManager getRevivalBookManager() {
      return this.revivalBookManager;
   }

   public pow.crimson2.managers.GhoulManager getGhoulManager() {
      return this.ghoulManager;
   }

   public pow.crimson2.managers.VaultManager getVaultManager() {
      return this.vaultManager;
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

   public TurnLockManager getTurnLockManager() {
      return this.turnLockManager;
   }

   public FaeManager getFaeManager() {
      return this.faeManager;
   }

   public FadeManager getFadeManager() {
      return this.fadeManager;
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

   public WorldManager getWorldManager() {
      return this.worldManager;
   }

   public CureBookReadingListener getCureBookReadingListener() {
      return this.cureBookReadingListener;
   }

   public WerewolfAbilityManager getWerewolfAbilityManager() {
      return this.werewolfAbilityManager;
   }

   public WerewolfPackManager getWerewolfPackManager() {
      return this.werewolfPackManager;
   }

   public WerewolfBitingListener getWerewolfBitingListener() {
      return this.werewolfBitingListener;
   }

   public WerewolfDietListener getWerewolfDietListener() {
      return this.werewolfDietListener;
   }

   public WerewolfHungerManager getWerewolfHungerManager() {
      return this.werewolfHungerManager;
   }

   public ThrallManager getThrallManager() {
      return this.thrallManager;
   }

   public pow.crimson2.network.WerePiresNetwork getNetwork() {
      return this.werePiresNetwork;
   }

   public RoleManager getRoleManager() {
      return this.roleManager;
   }

   public StarterKitManager getStarterKitManager() {
      return this.starterKitManager;
   }

   public StarterKitCommand getStarterKitCommand() {
      return this.starterKitCommand;
   }

   public PlayerSetupManager getPlayerSetupManager() {
      return this.playerSetupManager;
   }

   public GameStartManager getGameStartManager() {
      return this.gameStartManager;
   }

   private void loadStateConfig() {
      this.stateConfigFile = new java.io.File(getDataFolder(), "state.yml");
      this.stateConfig = new org.bukkit.configuration.file.YamlConfiguration();
      if (this.stateConfigFile.exists()) {
         this.stateConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(this.stateConfigFile);
      } else {
         // Migrate old state keys out of config.yml on first run
         String[] stateKeys = {"first_beacon_converted","humans_own_all_beacons","vampires_own_all_beacons",
                               "one_human_left","fourth_book_has_spawned","fourth_book_spawn_enabled"};
         boolean migrated = false;
         for (String key : stateKeys) {
            if (getConfig().contains(key)) {
               this.stateConfig.set(key, getConfig().get(key));
               getConfig().set(key, null);
               migrated = true;
            }
         }
         if (migrated) saveConfig();
         saveStateConfig();
         getLogger().info("[WerePires] Migrated game-state keys from config.yml → state.yml");
      }
   }

   public org.bukkit.configuration.file.YamlConfiguration getStateConfig() {
      return this.stateConfig;
   }

   public void saveStateConfig() {
      try {
         this.stateConfig.save(this.stateConfigFile);
      } catch (java.io.IOException e) {
         getLogger().severe("[WerePires] Failed to save state.yml: " + e.getMessage());
      }
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
