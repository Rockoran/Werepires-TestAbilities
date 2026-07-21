package pow.crimson2.items;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import pow.crimson2.VampireSMPPlugin;

/**
 * Adds the "Silver Arrow" — mechanically a spectral arrow, but it does <b>not</b> apply the
 * Glowing effect to whatever it hits.
 *
 * <p>Crafted in an arrow shape (vertical, centre column): silver ingot on top, stick in the
 * middle, arrow on the bottom. ("Silver" is iron on this server, matching the silver
 * sword / silver-weakness theme.) Yields 2 silver arrows.</p>
 *
 * <p>The no-glow behaviour works by tagging the launched {@link SpectralArrow} and setting
 * its glowing duration to 0, so it keeps the spectral-arrow look without the Glowing debuff.</p>
 */
public class SilverArrowManager implements Listener {

    private final VampireSMPPlugin plugin;
    private final NamespacedKey itemKey;        // marks the item as a silver arrow
    private final NamespacedKey projectileKey;  // marks a launched silver-arrow projectile
    private final NamespacedKey recipeKey;

    public SilverArrowManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.itemKey       = new NamespacedKey(plugin, "silver_arrow");
        this.projectileKey = new NamespacedKey(plugin, "silver_arrow_projectile");
        this.recipeKey     = new NamespacedKey(plugin, "silver_arrow_recipe");
        registerRecipe();
    }

    /** Build a stack of silver arrows. */
    public ItemStack createSilverArrow(int amount) {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Silver Arrow");
            meta.setLore(Arrays.asList(
                    "§8A spectral-tipped arrow coated in silver.",
                    "§8Does not mark its target."));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True if the given item is a silver arrow. */
    public boolean isSilverArrow(ItemStack item) {
        if (item == null || item.getType() != Material.SPECTRAL_ARROW || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** True if the given projectile was fired from a silver arrow. */
    public boolean isSilverArrowProjectile(org.bukkit.entity.Entity projectile) {
        return projectile != null
                && projectile.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE);
    }

    private void registerRecipe() {
        // Remove first so a plugin reload doesn't throw "recipe already exists".
        try { Bukkit.removeRecipe(recipeKey); } catch (Throwable ignored) {}

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createSilverArrow(2));
        recipe.shape(" I ", " S ", " A ");
        recipe.setIngredient('I', Material.IRON_INGOT); // "silver" ingot on this server
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('A', Material.ARROW);
        try {
            Bukkit.addRecipe(recipe);
            plugin.logInfo("Registered Silver Arrow recipe.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Silver Arrow recipe: " + e.getMessage());
        }
    }

    /** Strip the Glowing effect from silver arrows by zeroing their glowing duration. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!isSilverArrow(event.getConsumable())) return;
        if (event.getProjectile() instanceof SpectralArrow arrow) {
            arrow.setGlowingTicks(0); // no Glowing applied on hit
            arrow.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public void shutdown() {
        try { Bukkit.removeRecipe(recipeKey); } catch (Throwable ignored) {}
    }
}
