package pow.crimson2.abilities;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Sire's Command (Stage 3): pour your will into your thralls, granting them strength and swiftness. */
public class SiresCommandAbility extends VampireAbility {

   @Override public String getName() { return "sirescommand"; }
   @Override public String getDisplayName() { return "Sire's Command"; }
   @Override public String getDescription() {
      return "Pour your will into your thralls — those bound to you gain strength, swiftness and regeneration.";
   }
   @Override public int getMinimumStage() { return 3; }

   @Override public boolean isEnabled(VampireSMPPlugin plugin) {
      return plugin.getConfig().getBoolean("abilities.vampire.sirescommand.enabled", true);
   }
   @Override public int getCooldownSeconds(VampireSMPPlugin plugin) {
      return plugin.getConfig().getInt("abilities.vampire.sirescommand.cooldown", 90);
   }

   @Override
   public boolean execute(Player player, VampireManager vampireManager, VampireSMPPlugin plugin) {
      if (plugin.getThrallManager() == null) {
         player.sendMessage("§cThe bond between sire and thrall is severed here.");
         return false;
      }
      int duration = plugin.getConfig().getInt("abilities.vampire.sirescommand.duration-ticks", 200);
      int strengthAmp = plugin.getConfig().getInt("abilities.vampire.sirescommand.strength-amplifier", 0);
      int speedAmp = plugin.getConfig().getInt("abilities.vampire.sirescommand.speed-amplifier", 0);

      List<Player> thralls = plugin.getThrallManager().getOnlineThralls(player.getUniqueId());
      if (thralls.isEmpty()) {
         player.sendMessage("§cYou have no thralls to answer your call.");
         return false;
      }

      Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.4F);
      int count = 0;
      for (Player thrall : thralls) {
         thrall.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, strengthAmp, false, false));
         thrall.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedAmp, false, false));
         thrall.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Math.min(duration, 100), 0, false, false));
         thrall.getWorld().spawnParticle(Particle.DUST, thrall.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.0, red);
         thrall.playSound(thrall.getLocation(), Sound.ENTITY_WITHER_AMBIENT, SoundCategory.MASTER, 0.5F, 1.4F);
         thrall.sendMessage("§4Your master's will surges through you — strength and swiftness are yours.");
         count++;
      }

      player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.6F, 1.2F);
      player.sendMessage("§4You command " + count + " thrall" + (count == 1 ? "" : "s") + " to your aid.");
      return true;
   }
}
