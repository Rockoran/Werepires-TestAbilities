package pow.crimson2.abilities.tome;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

/**
 * Scrying: point the vampire indicator arrow at another player for a while.
 *
 * <p>Reuses {@code VampireTrackingManager.showDirectionalArrow}, the same action-bar arrow and
 * distance readout the new-vampire tracker and thrall bond use, so it looks native rather than
 * like a bolted-on compass.
 *
 * <p>A target name is required — scrying blind would make the tome a free radar sweep rather
 * than something you aim at a specific person.
 *
 * <pre>
 *   /pow tome scrying &lt;player&gt;
 * </pre>
 */
public class ScryingTomeAbility extends TomeAbility {

   public ScryingTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "Scrying",
            new String[]{"You learn to cast your senses out across the land.",
                  "For a time, you feel the direction and distance of another soul."},
            plugin.getConfig().getInt("abilities.tome.scrying.cooldown", 180));
   }

   @Override
   public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.scrying.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      return this.useAbility(player, new String[0]);
   }

   @Override
   protected boolean useAbility(Player player, String[] args) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }

      int duration = plugin.getConfig().getInt("abilities.tome.scrying.duration-seconds", 20);
      int maxRange = plugin.getConfig().getInt("abilities.tome.scrying.max-range", 0);
      boolean vampiresOnly = plugin.getConfig().getBoolean("abilities.tome.scrying.vampires-only", false);
      boolean notifyTarget = plugin.getConfig().getBoolean("abilities.tome.scrying.notify-target", true);

      if (args.length < 1 || args[0].isBlank()) {
         this.sendCannotUseMessage(player, "you must name whose soul to reach for — §e/pow tome scrying <player>");
         return false;
      }

      Player target = this.resolveNamed(player, args[0], vampiresOnly);
      if (target == null) {
         return false; // resolveNamed already explained why
      }

      if (target.equals(player)) {
         this.sendCannotUseMessage(player, "you cannot scry for yourself.");
         return false;
      }

      if (!target.getWorld().equals(player.getWorld())) {
         this.sendCannotUseMessage(player, "that soul is beyond this world.");
         return false;
      }

      double distance = player.getLocation().distance(target.getLocation());
      if (maxRange > 0 && distance > maxRange) {
         this.sendCannotUseMessage(player, "that soul is too distant to reach — no further than " + maxRange + " blocks.");
         return false;
      }

      plugin.getVampireTrackingManager().showDirectionalArrow(
         player, target, "§5[Scrying] §7" + target.getName(), duration);

      player.getWorld().spawnParticle(Particle.WITCH, player.getEyeLocation(), 30, 0.4, 0.5, 0.4, 0.02);
      player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 0.7F);
      this.sendSuccessMessage(player, "Your senses reach out and find " + target.getName() + "...");

      if (notifyTarget) {
         target.sendMessage("§5§oYou feel unseen eyes settle upon you...");
         target.playSound(target.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.6F, 1.4F);
      }

      return true;
   }

   /** Look up a named player, honouring the vampires-only restriction. */
   private Player resolveNamed(Player caster, String name, boolean vampiresOnly) {
      Player target = Bukkit.getPlayerExact(name);
      if (target == null || !target.isOnline()) {
         this.sendCannotUseMessage(caster, "no soul by the name '" + name + "' answers.");
         return null;
      }
      if (vampiresOnly && !plugin.getVampireManager().isVampire(target)) {
         this.sendCannotUseMessage(caster, "your sight is attuned only to the cursed.");
         return null;
      }
      return target;
   }

}
