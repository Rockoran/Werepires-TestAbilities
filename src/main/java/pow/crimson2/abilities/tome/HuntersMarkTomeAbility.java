package pow.crimson2.abilities.tome;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/** Hunter's Mark: brand the vampire you're looking at with holy light, revealing them (glowing). */
public class HuntersMarkTomeAbility extends TomeAbility {

   public HuntersMarkTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "HuntersMark",
            new String[]{"You learn to brand a vampire with a hunter's mark.",
                  "The marked creature glows with holy light for all to see."},
            plugin.getConfig().getInt("abilities.tome.huntersmark.cooldown", 90));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.huntersmark.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      int range = plugin.getConfig().getInt("abilities.tome.huntersmark.range", 24);
      int duration = plugin.getConfig().getInt("abilities.tome.huntersmark.duration-ticks", 300);

      VampireManager vm = plugin.getVampireManager();
      Entity looked = player.getTargetEntity(range);
      if (!(looked instanceof Player vampire) || !vm.isVampire(vampire)) {
         this.sendCannotUseMessage(player, "you must fix your gaze upon a vampire.");
         return false;
      }

      vampire.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, false));
      vampire.getWorld().spawnParticle(Particle.END_ROD, vampire.getEyeLocation(), 20, 0.3, 0.4, 0.3, 0.01);
      player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_HIT, SoundCategory.PLAYERS, 1.0F, 1.4F);
      vampire.sendMessage("§eA hunter's mark blazes upon you — you cannot hide.");
      this.sendSuccessMessage(player, "You mark " + vampire.getName() + " — they glow for all to see.");
      return true;
   }
}
