package pow.crimson2.abilities.tome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/**
 * Blessed Blade: anoint your weapon in holy light. For a time, your strikes against vampires deal
 * bonus damage and blunt their healing (weakness). Implemented as a self-contained on-hit hook.
 */
public class BlessedBladeTomeAbility extends TomeAbility implements Listener {

   private final Map<UUID, Long> primedUntil = new HashMap<>();

   public BlessedBladeTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "BlessedBlade",
            new String[]{"You learn to anoint your weapon in holy light.",
                  "For a time, your strikes sear vampires and blunt their healing."},
            plugin.getConfig().getInt("abilities.tome.blessedblade.cooldown", 90));
   }

   @Override public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.blessedblade.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }
      int duration = plugin.getConfig().getInt("abilities.tome.blessedblade.duration-ticks", 200);
      primedUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 50L);
      player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.02);
      player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 0.8F, 1.6F);
      this.sendSuccessMessage(player, "Your weapon kindles with holy light.");
      player.sendMessage("§7For the next " + (duration / 20) + "s, your strikes sear the undead.");
      return true;
   }

   @EventHandler(ignoreCancelled = true)
   public void onHit(EntityDamageByEntityEvent event) {
      if (!isEnabled()) return;
      if (!(event.getDamager() instanceof Player attacker)) return;
      if (!(event.getEntity() instanceof Player victim)) return;
      Long until = primedUntil.get(attacker.getUniqueId());
      if (until == null || System.currentTimeMillis() > until) return;

      VampireManager vm = plugin.getVampireManager();
      if (!vm.isHuman(attacker) || !vm.isVampire(victim)) return;

      double multiplier = plugin.getConfig().getDouble("abilities.tome.blessedblade.bonus-multiplier", 1.5);
      int weakenTicks = plugin.getConfig().getInt("abilities.tome.blessedblade.weaken-ticks", 100);
      int weakenAmp = plugin.getConfig().getInt("abilities.tome.blessedblade.weaken-amplifier", 0);

      event.setDamage(event.getDamage() * multiplier);
      victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weakenTicks, weakenAmp, false, false));
      victim.getWorld().spawnParticle(Particle.END_ROD, victim.getEyeLocation(), 12, 0.2, 0.3, 0.2, 0.01);
      victim.playSound(victim.getLocation(), Sound.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.6F, 1.6F);
   }
}
