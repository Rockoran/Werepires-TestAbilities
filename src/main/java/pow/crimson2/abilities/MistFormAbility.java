package pow.crimson2.abilities;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Mist Form (Stage 2): dissolve to mist and surge forward through walls, briefly untouchable. */
public class MistFormAbility extends VampireAbility {

   @Override public String getName() { return "mistform"; }
   @Override public String getDisplayName() { return "Mist Form"; }
   @Override public String getDescription() {
      return "Dissolve into mist and surge forward through walls, briefly untouchable.";
   }
   @Override public int getMinimumStage() { return 2; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.mistform.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.mistform.cooldown", 30);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      double distance = plugin.getConfig().getDouble("abilities.vampire.mistform.distance", 8.0);
      int protectTicks = plugin.getConfig().getInt("abilities.vampire.mistform.protect-ticks", 30);

      Location eye = player.getEyeLocation();
      Location origin = player.getLocation().clone();
      Vector dir = eye.getDirection().normalize();
      double eyeHeight = eye.getY() - origin.getY();

      Location target = null;
      for (double d = 1.0; d <= distance; d += 0.5) {
         Location point = eye.clone().add(dir.clone().multiply(d));
         Location feet = point.clone().subtract(0, eyeHeight, 0);
         Block fb = feet.getBlock();
         Block hb = fb.getRelative(BlockFace.UP);
         if (fb.isPassable() && hb.isPassable()) {
            target = new Location(player.getWorld(), fb.getX() + 0.5, fb.getY(), fb.getZ() + 0.5,
                  origin.getYaw(), origin.getPitch());
         }
      }
      if (target == null) {
         player.sendMessage("§cThe mist finds no purchase — something solid blocks your path.");
         return false;
      }

      player.getWorld().spawnParticle(Particle.LARGE_SMOKE, origin.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
      player.playSound(origin, Sound.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.0F, 0.6F);
      player.teleport(target);
      player.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
      player.playSound(target, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.2F);

      player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, protectTicks, 4, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, protectTicks, 0, false, false));
      player.sendMessage("§5You dissolve into mist and slip through the world...");
      return true;
   }
}
