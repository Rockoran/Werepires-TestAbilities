package pow.crimson2.abilities.tome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireAbilityManager;

public abstract class TomeAbility {
   protected final VampireSMPPlugin plugin;
   protected final String name;
   protected final String[] descriptionLines;
   protected final int cooldownSeconds;
   private static final Map<UUID, Map<String, Long>> playerCooldowns = new HashMap<>();
   private static final Map<String, BukkitTask> cooldownNotificationTasks = new HashMap<>();

   public TomeAbility(VampireSMPPlugin plugin, String name, String[] descriptionLines, int cooldownSeconds) {
      this.plugin = plugin;
      this.name = name;
      this.descriptionLines = descriptionLines;
      this.cooldownSeconds = cooldownSeconds;
   }

   public String getName() {
      return this.name;
   }

   /** Abilities in the same family can share one cooldown without sharing a display name. */
   protected String getCooldownKey() {
      return this.name;
   }

   /**
    * Whether this tome is enabled on the server. New tomes override this to read a config toggle;
    * built-in ones are always enabled. Checked before learning and before use, so a tome can be
    * disabled live via config.
    */
   public boolean isEnabled() {
      return true;
   }

   /** Whether this tome can appear in chest/vault loot pools. Override to return false for admin-only tomes. */
   public boolean isInLootPool() {
      return true;
   }

   public String[] getDescriptionLines() {
      return this.descriptionLines;
   }

   public String getDescription() {
      return String.join(" ", this.descriptionLines);
   }

   public final boolean use(Player player) {
      return this.use(player, new String[0]);
   }

   /** @param args anything the player typed after the ability name, e.g. a target player. */
   public final boolean use(Player player, String[] args) {
      if (this.isOnCooldown(player)) {
         long remainingTime = this.getRemainingCooldown(player);
         this.sendCannotUseMessage(player, "ability is on cooldown! " + VampireAbilityManager.formatTime(remainingTime) + " remaining.");
         return false;
      }

      boolean success = this.useAbility(player, args == null ? new String[0] : args);
      if (success) {
         this.setCooldown(player);
      }

      return success;
   }

   /**
    * Argument-aware entry point. Abilities that take no arguments — which is all of the original
    * ones — inherit this and never see the array.
    */
   protected boolean useAbility(Player player, String[] args) {
      return this.useAbility(player);
   }

   protected abstract boolean useAbility(Player var1);

   protected boolean canUse(Player player) {
      if (this.plugin.getVampireManager().isHuman(player)) {
         return true;
      }
      // Turned players may still use abilities named in allow.use.after.turned.abilities.
      // Every ability routes its self-check through here, so this is the one place that decides it.
      return this.plugin.getConfigManager().isAbilityAllowedAfterTurned(this.name);
   }

   protected void sendCannotUseMessage(Player player, String reason) {
      player.sendMessage("§cCannot use " + this.name + ": " + reason);
   }

   protected void sendSuccessMessage(Player player, String message) {
      player.sendMessage("§a" + message);
   }

   protected boolean isOnCooldown(Player player) {
      UUID playerId = player.getUniqueId();
      Map<String, Long> cooldowns = playerCooldowns.get(playerId);
      String key = this.getCooldownKey();
      if (cooldowns != null && cooldowns.containsKey(key)) {
         long cooldownEnd = cooldowns.get(key);
         return System.currentTimeMillis() < cooldownEnd;
      } else {
         return false;
      }
   }

   protected long getRemainingCooldown(Player player) {
      UUID playerId = player.getUniqueId();
      Map<String, Long> cooldowns = playerCooldowns.get(playerId);
      String key = this.getCooldownKey();
      if (cooldowns != null && cooldowns.containsKey(key)) {
         long cooldownEnd = cooldowns.get(key);
         long remaining = cooldownEnd - System.currentTimeMillis();
         return Math.max(0L, remaining / 1000L);
      } else {
         return 0L;
      }
   }

   protected void setCooldown(Player player) {
      UUID playerId = player.getUniqueId();
      Map<String, Long> cooldowns = playerCooldowns.computeIfAbsent(playerId, k -> new HashMap<>());
      long cooldownEnd = System.currentTimeMillis() + this.cooldownSeconds * 1000L;
      cooldowns.put(this.getCooldownKey(), cooldownEnd);
      this.scheduleCooldownNotification(player, this.cooldownSeconds);
   }

   private void scheduleCooldownNotification(Player player, int cooldownSeconds) {
      String taskKey = player.getUniqueId() + ":" + this.getCooldownKey();
      BukkitTask existingTask = cooldownNotificationTasks.get(taskKey);
      if (existingTask != null && !existingTask.isCancelled()) {
         existingTask.cancel();
      }

      BukkitTask notificationTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         cooldownNotificationTasks.remove(taskKey);
         if (player.isOnline() && !this.plugin.getVampireManager().isVampire(player)) {
            this.notifyAbilityReady(player);
         }
      }, cooldownSeconds * 20L);
      cooldownNotificationTasks.put(taskKey, notificationTask);
   }

   private void notifyAbilityReady(Player player) {
      player.sendMessage("§a§l⚡ TOME ABILITY READY ⚡");
      player.sendMessage("§a" + this.name + " is now available.");
      player.playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.5F, 1.5F);
   }

   public static void clearCooldown(Player player, String abilityName) {
      UUID playerId = player.getUniqueId();
      Map<String, Long> cooldowns = playerCooldowns.get(playerId);
      if (cooldowns != null) {
         cooldowns.remove(abilityName);
      }

      String taskKey = playerId + ":" + abilityName;
      BukkitTask task = cooldownNotificationTasks.get(taskKey);
      if (task != null && !task.isCancelled()) {
         task.cancel();
         cooldownNotificationTasks.remove(taskKey);
      }
   }

   public static void clearAllCooldowns(Player player) {
      UUID playerId = player.getUniqueId();
      playerCooldowns.remove(playerId);
      String playerPrefix = playerId + ":";
      cooldownNotificationTasks.entrySet().removeIf(entry -> {
         if (entry.getKey().startsWith(playerPrefix)) {
            BukkitTask task = entry.getValue();
            if (task != null && !task.isCancelled()) {
               task.cancel();
            }

            return true;
         } else {
            return false;
         }
      });
   }

   public static void cancelAllNotificationTasks() {
      for (BukkitTask task : cooldownNotificationTasks.values()) {
         if (task != null && !task.isCancelled()) {
            task.cancel();
         }
      }

      cooldownNotificationTasks.clear();
   }
}
