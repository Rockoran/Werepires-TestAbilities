package pow.crimson2.thralls;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles drinking of blood bottles / thrall blood bottles.
 *
 * Blood bottle → applies bond (vb_applyBlood)
 * Thrall blood → nutritional regen for existing thralls; nothing for others
 */
public class BloodConsumeListener implements Listener {

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;
    private final BloodBottleManager bloodBottleManager;

    public BloodConsumeListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.thrallManager = plugin.getThrallManager();
        this.bloodBottleManager = thrallManager.getBloodBottleManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItem();

        // ── Thrall blood (nutritious for thralls, nothing for others) ──────────
        if (bloodBottleManager.isThrallBloodBottle(item)) {
            if (thrallManager.isThrall(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 6 * 20, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,   3 * 20, 0, false, true));
                thrallManager.sendActionBar(player, "§5The blood settles. Not the right kind, but close.");
            } else {
                thrallManager.sendActionBar(player, "§8It tastes wrong. Nothing stirs.");
            }
            return;
        }

        // ── Vampire blood (bond-forming) ────────────────────────────────────────
        if (bloodBottleManager.isBloodBottle(item)) {
            // Vampires cannot be thralled
            if (thrallManager.isVampire(player)) return;

            UUID ownerId = bloodBottleManager.getBloodOwner(item);
            if (ownerId == null) return;

            // Defer so the consume animation finishes first
            final UUID ownerIdFinal = ownerId;
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                thrallManager.applyBlood(player, ownerIdFinal);
            }, 1L);
        }
    }
}
