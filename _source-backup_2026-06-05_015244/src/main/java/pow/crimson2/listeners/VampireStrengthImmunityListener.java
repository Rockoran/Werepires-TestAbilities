package pow.crimson2.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffectType;

import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/**
 * Vampires are immune to the Strength potion effect — any attempt to apply Strength to a
 * vampire (drinking a potion, splash/lingering, beacon, command, etc.) is cancelled, so a
 * vampire can never gain the Strength buff.
 */
public class VampireStrengthImmunityListener implements Listener {

    private final VampireManager vampireManager;

    public VampireStrengthImmunityListener(VampireSMPPlugin plugin) {
        this.vampireManager = plugin.getVampireManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getModifiedType() != PotionEffectType.STRENGTH) return;

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED
            && action != EntityPotionEffectEvent.Action.CHANGED) return;

        if (event.getEntity() instanceof Player player && vampireManager.isVampire(player)) {
            event.setCancelled(true);
        }
    }
}
