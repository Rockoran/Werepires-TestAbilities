package pow.crimson2.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.abilities.FeralChargeAbility;
import pow.crimson2.abilities.WerewolfAbility;

public class WerewolfAbilityManager {

   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private final SessionManager sessionManager;
   private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
   private final Map<String, WerewolfAbility> abilities = new HashMap<>();
   private BukkitTask cooldownTask;

   public WerewolfAbilityManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
      this.sessionManager = plugin.getSessionManager();
      this.registerAbilities();
      this.startCooldownTask();
   }

   private void registerAbilities() {
      this.register(new FeralChargeAbility());
      this.plugin.logInfo("Registered " + this.abilities.size() + " werewolf abilities");
   }

   private void register(WerewolfAbility ability) {
      this.abilities.put(ability.getName().toLowerCase(), ability);
   }

   private void startCooldownTask() {
      this.cooldownTask = new BukkitRunnable() {
         @Override
         public void run() {
            checkCooldownExpirations();
         }
      }.runTaskTimer(this.plugin, 20L, 20L);
   }

   private void checkCooldownExpirations() {
      long currentTime = this.sessionManager.getSessionTimeSeconds();

      for (UUID playerId : this.cooldowns.keySet()) {
         Player player = this.plugin.getServer().getPlayer(playerId);
         if (player != null && player.isOnline()) {
            Map<String, Long> playerCooldowns = this.cooldowns.get(playerId);
            Iterator<Entry<String, Long>> iterator = playerCooldowns.entrySet().iterator();

            while (iterator.hasNext()) {
               Entry<String, Long> entry = iterator.next();
               if (currentTime >= entry.getValue()) {
                  iterator.remove();
                  this.notifyAbilityReady(player, entry.getKey());
               }
            }

            if (playerCooldowns.isEmpty()) {
               this.cooldowns.remove(playerId);
            }
         }
      }
   }

   private void notifyAbilityReady(Player player, String abilityName) {
      WerewolfAbility ability = this.abilities.get(abilityName.toLowerCase());
      if (ability != null) {
         player.sendMessage("§6§l⚡ ABILITY READY ⚡");
         player.sendMessage("§6" + ability.getDisplayName() + " is now available.");
         player.playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.5F, 1.5F);
      }
   }

   public boolean useAbility(Player player, String abilityName) {
      if (!this.vampireManager.isWerewolf(player)) {
         player.sendMessage("§cOnly werewolves can use werewolf abilities.");
         return false;
      }

      if (player.getGameMode() == GameMode.SPECTATOR) {
         player.sendMessage("§cYou cannot use abilities in spectator mode.");
         return false;
      }

      if (!this.sessionManager.isSessionActive()) {
         player.sendMessage("§cAbilities cannot be used while the session is inactive.");
         return false;
      }

      WerewolfAbility ability = this.abilities.get(abilityName.toLowerCase());
      if (ability == null) {
         player.sendMessage("§cUnknown ability: " + abilityName);
         player.sendMessage("§eUse §6/pow wability list §eto see your available abilities.");
         return false;
      }

      if (!ability.canUse(player, this.vampireManager)) {
         player.sendMessage("§c" + ability.getRequirementMessage(player, this.vampireManager));
         return false;
      }

      if (this.isOnCooldown(player, abilityName)) {
         long remaining = this.getRemainingCooldown(player, abilityName);
         player.sendMessage("§c§l ABILITY ON COOLDOWN");
         player.sendMessage("§c" + ability.getDisplayName() + " will be ready in " + formatTime(remaining) + ".");
         return false;
      }

      boolean success = ability.execute(player, this.vampireManager, this.plugin);
      if (success) {
         this.setCooldown(player, abilityName, ability.getCooldownSeconds(this.plugin));
      }
      return success;
   }

   public boolean isOnCooldown(Player player, String abilityName) {
      Map<String, Long> playerCooldowns = this.cooldowns.get(player.getUniqueId());
      if (playerCooldowns == null) return false;
      Long end = playerCooldowns.get(abilityName.toLowerCase());
      if (end == null) return false;
      return this.sessionManager.getSessionTimeSeconds() < end;
   }

   public long getRemainingCooldown(Player player, String abilityName) {
      Map<String, Long> playerCooldowns = this.cooldowns.get(player.getUniqueId());
      if (playerCooldowns == null) return 0L;
      Long end = playerCooldowns.get(abilityName.toLowerCase());
      if (end == null) return 0L;
      return Math.max(0L, end - this.sessionManager.getSessionTimeSeconds());
   }

   private void setCooldown(Player player, String abilityName, int seconds) {
      long end = this.sessionManager.getSessionTimeSeconds() + seconds;
      this.cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(abilityName.toLowerCase(), end);
   }

   public void clearCooldowns(Player player) {
      this.cooldowns.remove(player.getUniqueId());
      this.plugin.logInfo("Cleared werewolf ability cooldowns for " + player.getName());
   }

   public void clearAllCooldownsForNewSession() {
      int count = this.cooldowns.size();
      this.cooldowns.clear();
      this.plugin.logInfo("NEW SESSION: Cleared werewolf ability cooldowns for " + count + " players");
   }

   public List<WerewolfAbility> getAvailableAbilities(Player player) {
      List<WerewolfAbility> available = new ArrayList<>();
      for (WerewolfAbility ability : this.abilities.values()) {
         if (ability.canUse(player, this.vampireManager)) {
            available.add(ability);
         }
      }
      return available;
   }

   public Collection<WerewolfAbility> getAllAbilities() {
      return this.abilities.values();
   }

   public WerewolfAbility getAbility(String name) {
      return this.abilities.get(name.toLowerCase());
   }

   public static String formatTime(long seconds) {
      long minutes = seconds / 60L;
      long remaining = seconds % 60L;
      return String.format("%d:%02d", minutes, remaining);
   }

   public void shutdown() {
      if (this.cooldownTask != null) {
         this.cooldownTask.cancel();
      }
   }
}
