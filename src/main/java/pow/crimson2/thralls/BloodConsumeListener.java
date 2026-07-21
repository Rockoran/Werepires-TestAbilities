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

        // ── Vampire blood (bond-forming, drinkable XP bottle) ──────────────────
        if (bloodBottleManager.isBloodBottle(item)) {
            // Give back a glass bottle (FOOD items don't return a container automatically)
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                org.bukkit.inventory.ItemStack glass = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GLASS_BOTTLE);
                if (player.getInventory().firstEmpty() != -1) player.getInventory().addItem(glass);
                else player.getWorld().dropItemNaturally(player.getLocation(), glass);
            }, 1L);

            // Vampires cannot be thralled
            if (thrallManager.isVampire(player)) return;

            UUID ownerId = bloodBottleManager.getBloodOwner(item);
            if (ownerId == null) return;

            final UUID ownerIdFinal = ownerId;
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) thrallManager.applyBlood(player, ownerIdFinal);
            }, 1L);
        }
    }
}
