package pow.crimson2.abilities.tome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;

public class ShoulderBargeTomeAbility extends TomeAbility {
   private final int CHARGE_DURATION;
   private final double CHARGE_VELOCITY;
   private final double UPWARD_VELOCITY;
   private final double KNOCKBACK_STRENGTH;
   private final int SLOWNESS_DURATION;
   private final double DAMAGE_TO_PLAYERS;
   private final double DAMAGE_TO_MOBS;
   private final Map<UUID, BukkitTask> chargingPlayers = new HashMap<>();
   private final Map<UUID, Set<UUID>> chargeHitEntities = new HashMap<>();
   private final Map<UUID, Long> recentlyBargedEntities = new HashMap<>();
   private final long TARGET_COOLDOWN_MS;

   public ShoulderBargeTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "ShoulderBarge",
         new String[]{
            "You learn to use your very body as a weapon.",
            "You charge forwards, and any entity that collides with you during this charge",
            "is knocked back and given slowness for 15 seconds."
         },
         plugin.getConfigManager().getTomeShoulderBargeCooldown()
      );
      this.CHARGE_DURATION = plugin.getConfigManager().getShoulderBargeChargeDurationTicks();
      this.CHARGE_VELOCITY = plugin.getConfig().getDouble("abilities.tome.shoulderbarge.charge-velocity", 1.5);
      this.UPWARD_VELOCITY = plugin.getConfigManager().getShoulderBargeUpwardVelocity();
      this.KNOCKBACK_STRENGTH = plugin.getConfig().getDouble("abilities.tome.shoulderbarge.knockback-strength", 1.2);
      this.SLOWNESS_DURATION = plugin.getConfig().getInt("abilities.tome.shoulderbarge.slowness-duration-ticks", 300);
      this.DAMAGE_TO_PLAYERS = plugin.getConfig().getDouble("abilities.tome.shoulderbarge.damage-to-players", 10.0);
      this.DAMAGE_TO_MOBS = plugin.getConfig().getDouble("abilities.tome.shoulderbarge.damage-to-mobs", 20.0);
      this.TARGET_COOLDOWN_MS = plugin.getConfigManager().getShoulderBargeTargetCooldownMs();
      Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOldEntries, 600L, 600L);
   }

   @Override
   protected boolean useAbility(final Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }

      if (this.chargingPlayers.containsKey(player.getUniqueId())) {
         this.sendCannotUseMessage(player, "You are already charging!");
         return false;
      }

      Vector direction = player.getLocation().getDirection();
      direction.setY(Math.max(direction.getY(), 0.1));
      Vector chargeVelocity = direction.multiply(this.CHARGE_VELOCITY);
      chargeVelocity.setY(UPWARD_VELOCITY);
      player.setVelocity(chargeVelocity);
      player.getWorld().playSound(player.getLocation(), "minecraft:entity.player.attack.crit", 0.8F, 1.2F);
      this.sendSuccessMessage(player, "You lower your shoulder and charge forward!");
      final UUID playerId = player.getUniqueId();
      synchronized (this.chargeHitEntities) {
         this.chargeHitEntities.put(playerId, new HashSet<>());
      }

      BukkitRunnable collisionTask = new BukkitRunnable() {
         int ticksRemaining = ShoulderBargeTomeAbility.this.CHARGE_DURATION;

         public void run() {
            if (this.ticksRemaining > 0 && player.isOnline() && ShoulderBargeTomeAbility.this.chargingPlayers.containsKey(playerId)) {
               ShoulderBargeTomeAbility.this.checkForCollisions(player);
               this.ticksRemaining--;
            } else {
               this.cancel();
            }
         }
      };
      collisionTask.runTaskTimer(this.plugin, 0L, 1L);
      BukkitTask chargeTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         this.chargingPlayers.remove(playerId);
         synchronized (this.chargeHitEntities) {
            this.chargeHitEntities.remove(playerId);
         }

         collisionTask.cancel();
      }, (long) this.CHARGE_DURATION);
      this.chargingPlayers.put(playerId, chargeTask);
      return true;
   }

   private void checkForCollisions(Player player) {
      UUID playerId = player.getUniqueId();
      Set<UUID> hitEntities;
      synchronized (this.chargeHitEntities) {
         hitEntities = this.chargeHitEntities.get(playerId);
      }

      if (hitEntities != null) {
         for (Entity entity : player.getNearbyEntities(1.5, 2.0, 1.5)) {
            UUID entityId = entity.getUniqueId();
            synchronized (hitEntities) {
               if (hitEntities.contains(entityId)) {
                  continue;
               }
            }

            if (!entity.equals(player)) {
               synchronized (this.recentlyBargedEntities) {
                  Long lastBargeTime = this.recentlyBargedEntities.get(entityId);
                  if (lastBargeTime != null && System.currentTimeMillis() - lastBargeTime < this.TARGET_COOLDOWN_MS) {
                     continue;
                  }
               }

               this.handleCollision(player, entity);
               synchronized (hitEntities) {
                  hitEntities.add(entityId);
               }
            }
         }
      }
   }

   private void handleCollision(Player player, Entity target) {
      synchronized (this.recentlyBargedEntities) {
         this.recentlyBargedEntities.put(target.getUniqueId(), System.currentTimeMillis());
      }

      Vector knockbackDirection = target.getLocation().subtract(player.getLocation()).toVector();
      if (knockbackDirection.lengthSquared() == 0.0) {
         knockbackDirection = player.getLocation().getDirection();
      }

      knockbackDirection = knockbackDirection.normalize();
      knockbackDirection.setY(Math.max(knockbackDirection.getY(), 0.2));
      Vector knockback = knockbackDirection.multiply(this.KNOCKBACK_STRENGTH);
      target.setVelocity(knockback);
      if (target instanceof LivingEntity livingTarget) {
         double damageAmount = (target instanceof Player) ? this.DAMAGE_TO_PLAYERS : this.DAMAGE_TO_MOBS;
         livingTarget.damage(damageAmount, player);
         livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, this.SLOWNESS_DURATION, 1, false, false));
      }

      player.getWorld().playSound(player.getLocation(), "minecraft:entity.player.attack.knockback", 1.0F, 0.8F);
      player.getWorld().playSound(target.getLocation(), "minecraft:entity.generic.hurt", 0.8F, 1.1F);
      player.sendMessage("§aYou barrel into " + this.getEntityName(target) + ".");
      if (target instanceof Player) {
         ((Player)target).sendMessage("§c" + player.getName() + " charges into you with a shoulder barge.");
      }
   }

   private String getEntityName(Entity entity) {
      return entity instanceof Player ? ((Player)entity).getName() : entity.getType().name().toLowerCase().replace("_", " ");
   }

   private void cleanupOldEntries() {
      long currentTime = System.currentTimeMillis();
      synchronized (this.recentlyBargedEntities) {
         this.recentlyBargedEntities.entrySet().removeIf(entry -> currentTime - entry.getValue() > this.TARGET_COOLDOWN_MS);
      }
   }

   public void cleanup() {
      for (BukkitTask task : this.chargingPlayers.values()) {
         if (task != null && !task.isCancelled()) {
            task.cancel();
         }
      }

      this.chargingPlayers.clear();
      synchronized (this.recentlyBargedEntities) {
         this.recentlyBargedEntities.clear();
      }
   }
}
