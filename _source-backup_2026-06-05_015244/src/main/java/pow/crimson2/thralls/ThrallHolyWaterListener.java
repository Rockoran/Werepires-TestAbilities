package pow.crimson2.thralls;

import io.papermc.paper.event.entity.WaterBottleSplashEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import pow.crimson2.VampireSMPPlugin;

/**
 * Holy water splash severs / weakens thrall bonds.
 *
 * <p>Detection mirrors {@link pow.crimson2.managers.HolyWaterEffectManager} (the vampire
 * system): a <b>plain water splash potion</b> fires Paper's {@link WaterBottleSplashEvent}
 * and has an <b>empty</b> {@code getAffectedEntities()} (water bottles apply no potion
 * effect, so vanilla considers no entity "affected"). Relying on that list — as the old
 * implementation did — meant the actual holy-water splash never freed anyone. Instead we
 * scan for nearby players by radius around the splash, exactly like the vampire effect.</p>
 */
public class ThrallHolyWaterListener implements Listener {

    /** Radius (blocks) around the splash within which thralls are affected. Matches the vampire system. */
    private static final double SPLASH_RADIUS = 4.0;

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;

    public ThrallHolyWaterListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.thrallManager = plugin.getThrallManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!thrallManager.getThrallConfig().isThrallHolyWaterEnabled()) return;

        ThrownPotion potion = event.getPotion();

        // Notify the thrower (if a player) that the holy water landed.
        if (potion.getShooter() instanceof Player shooter) {
            thrallManager.sendActionBar(shooter, "§7Holy water scatters across the stone.");
        }

        if (event instanceof WaterBottleSplashEvent waterEvent) {
            // Plain water bottle: affected-entities list is empty, so scan by radius.
            Location splash = waterEvent.getPotion().getLocation();
            if (splash.getWorld() == null) return;
            for (Entity nearby : splash.getWorld().getNearbyEntities(splash, SPLASH_RADIUS, SPLASH_RADIUS, SPLASH_RADIUS)) {
                if (nearby instanceof Player p && nearby.getLocation().distance(splash) <= SPLASH_RADIUS) {
                    affectThrall(p);
                }
            }
        } else if (isWaterPotion(potion.getItem())) {
            // Fallback for water-type potions that do populate affected entities.
            for (LivingEntity affected : event.getAffectedEntities()) {
                if (affected instanceof Player p) {
                    affectThrall(p);
                }
            }
        }
    }

    /** Apply the holy-water reaction to a player if they are a thrall. */
    private void affectThrall(Player p) {
        if (thrallManager.isThrall(p)) {
            thrallManager.handleHolyWaterHit(p);
        }
    }

    /** Returns true if the thrown potion item is a splash water potion (no effects). */
    private boolean isWaterPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.hasCustomEffects()) return false;
        PotionType base = meta.getBasePotionType();
        return base == null || base == PotionType.WATER;
    }
}
