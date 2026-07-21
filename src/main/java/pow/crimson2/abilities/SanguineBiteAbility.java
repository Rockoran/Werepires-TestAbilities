package pow.crimson2.abilities;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Sanguine Bite (Stage 2): lunge-bite the human in front of you, healing and slaking your thirst. */
public class SanguineBiteAbility extends VampireAbility {

   @Override public String getName() { return "sanguinebite"; }
   @Override public String getDisplayName() { return "Sanguine Bite"; }
   @Override public String getDescription() {
      return "Sink your fangs into the human before you, drinking their blood to heal and slake your thirst.";
   }
   @Override public int getMinimumStage() { return 2; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.sanguinebite.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.sanguinebite.cooldown", 20);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      double range = plugin.getConfig().getDouble("abilities.vampire.sanguinebite.range", 4.0);
      double damage = plugin.getConfig().getDouble("abilities.vampire.sanguinebite.damage", 4.0);
      double heal = plugin.getConfig().getDouble("abilities.vampire.sanguinebite.heal", 6.0);
      int quench = plugin.getConfig().getInt("abilities.vampire.sanguinebite.quench", 15);

      Location eye = player.getEyeLocation();
      Vector dir = eye.getDirection().normalize();
      Player target = null;
      double best = Double.MAX_VALUE;
      for (Entity e : player.getNearbyEntities(range, range, range)) {
         if (!(e instanceof Player human) || !vampireManager.isHuman(human)) continue;
         Vector to = human.getEyeLocation().toVector().subtract(eye.toVector());
         double dist = to.length();
         if (dist > range || dist == 0) continue;
         if (dir.dot(to.normalize()) < 0.5) continue; // must be roughly in front
         if (dist < best) { best = dist; target = human; }
      }
      if (target == null) {
         player.sendMessage("§cNo prey within reach of your fangs.");
         return false;
      }

      target.damage(damage, player);
      double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
      player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
      if (plugin.getThirstManager() != null) {
         plugin.getThirstManager().modifyQuench(player, quench, true);
      }

      target.getWorld().spawnParticle(Particle.DUST, target.getEyeLocation(), 24, 0.2, 0.3, 0.2, 0.0,
            new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.4F));
      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 0.6F);
      player.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, SoundCategory.PLAYERS, 1.0F, 0.7F);
      player.sendMessage("§4You sink your fangs in and drink deep.");
      target.sendMessage("§cA vampire's fangs tear into you!");
      return true;
   }
}
