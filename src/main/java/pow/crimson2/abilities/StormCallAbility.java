package pow.crimson2.abilities;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

public class StormCallAbility extends VampireAbility {
   @Override
   public String getName() {
      return "stormcall";
   }

   @Override
   public String getDisplayName() {
      return "Call of the Storm";
   }

   @Override
   public String getDescription() {
      return "Summon dark rain clouds to shroud the world in storm. Only the most powerful vampires can command the very skies.";
   }

   @Override
   public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfigManager().getVampireStormCallCooldown();
   }

   @Override
   public int getMinimumStage() {
      return 3;
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      World world = player.getWorld();
      if (world.hasStorm()) {
         player.sendMessage("§8The skies are already under your influence...");
         return false;
      } else {
         int duration = plugin.getConfigManager().getVampireStormCallDurationSeconds();
         this.createStormSummonEffects(player);
         this.sendStormCallMessage(player, duration);
         this.playStormCallSound(player);
         world.setStorm(true);
         world.setThundering(false);
         this.broadcastStormArrival(world, player);
         this.scheduleStormClearing(world, player, plugin, duration);
         return true;
      }
   }

   private void createStormSummonEffects(Player player) {
      if (player.getWorld() != null) {
         for (int i = 0; i < 50; i++) {
            double angle = i * 0.3;
            double radius = 2.0;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = i * 0.1;
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(x, y + 1.0, z), 1, 0.0, 0.0, 0.0, 0.05);
         }

         player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0, 3.0, 0.0), 30, 3.0, 1.0, 3.0, 0.1);
         player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 2.0, 0.0), 40, 2.0, 2.0, 2.0, 0.5);
      }
   }

   private void sendStormCallMessage(Player player, int durationSeconds) {
      int min = durationSeconds / 60;
      int sec = durationSeconds % 60;
      String dur = sec == 0 ? min + " minute" + (min != 1 ? "s" : "") : min + "m " + sec + "s";
      player.sendMessage("§7Rain will fall for the next " + dur + ".");
   }

   private void playStormCallSound(Player player) {
      player.playSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.0F, 0.8F);
      player.playSound(player, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.MASTER, 0.8F, 0.6F);
      player.playSound(player, Sound.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.6F, 0.5F);
      player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0F, 0.7F);
   }

   private void broadcastStormArrival(World world, Player caster) {
      String message = "§8§lDark clouds gather across the sky...";
      String casterMessage = "§7§o A vampire has called upon an ancient storm...";

      for (Player worldPlayer : world.getPlayers()) {
         worldPlayer.sendMessage(message);
         if (!worldPlayer.equals(caster)) {
            worldPlayer.sendMessage(casterMessage);
            worldPlayer.playSound(worldPlayer, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.3F, 1.2F);
         }
      }
   }

   private void scheduleStormClearing(World world, Player caster, VampireSMPPlugin plugin, int durationSeconds) {
      int durationTicks = durationSeconds * 20;
      int warningTicks = Math.max(20, durationTicks - 60 * 20); // warn 60 s before end
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
         if (world.hasStorm()) {
            String warningMessage = "§7§oThe clouds are beginning to thin... The storm will pass soon.";

            for (Player worldPlayer : world.getPlayers()) {
               worldPlayer.sendMessage(warningMessage);
               worldPlayer.playSound(worldPlayer, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.WEATHER, 0.3F, 0.8F);
            }
         }
      }, warningTicks);
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
         if (world.hasStorm()) {
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(plugin.getConfigManager().getVampireStormClearWeatherTicks());
            if (caster.isOnline()) {
               this.createStormClearingEffects(caster);
            }

            this.broadcastStormClearing(world, caster);
         }
      }, durationTicks);
   }

   private void createStormClearingEffects(Player caster) {
      if (caster.getWorld() != null) {
         caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(0.0, 1.0, 0.0), 20, 2.0, 3.0, 2.0, 0.1);
         caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, caster.getLocation().add(0.0, 1.0, 0.0), 15, 1.5, 1.0, 1.5, 0.1);
      }
   }

   private void broadcastStormClearing(World world, Player caster) {
      String message = "§f§lThe storm clouds part, revealing clear skies once more...";
      String casterMessage = "§7§oYour dominion over the weather comes to an end.";

      for (Player worldPlayer : world.getPlayers()) {
         worldPlayer.sendMessage(message);
         if (worldPlayer.equals(caster)) {
            worldPlayer.sendMessage(casterMessage);
            worldPlayer.playSound(worldPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0F, 1.5F);
         } else {
            worldPlayer.playSound(worldPlayer, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.WEATHER, 0.4F, 1.8F);
         }
      }
   }
}
