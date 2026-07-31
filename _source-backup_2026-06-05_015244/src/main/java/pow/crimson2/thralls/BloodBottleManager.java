package pow.crimson2.thralls;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import pow.crimson2.VampireSMPPlugin;

/**
 * Creates and identifies blood bottle items.
 *
 * Blood bottle:   Material.POTION (Healing) + lore tag "§8vbowner:<uuid>"
 * Thrall blood:   Material.EXPERIENCE_BOTTLE + lore tag "§8vthrall:<uuid>"
 */
public class BloodBottleManager {

    private static final String VBOWNER_PREFIX = "vbowner:";
    private static final String VTHRALL_PREFIX = "vthrall:";

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;

    public BloodBottleManager(VampireSMPPlugin plugin, ThrallManager thrallManager) {
        this.plugin = plugin;
        this.thrallManager = thrallManager;
    }

    // =========================================================================
    // Detection
    // =========================================================================

    public boolean isBloodBottle(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return false;
        for (String line : lore) {
            if (stripColor(line).contains(VBOWNER_PREFIX)) return true;
        }
        return false;
    }

    public boolean isThrallBloodBottle(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return false;
        for (String line : lore) {
            if (stripColor(line).contains(VTHRALL_PREFIX)) return true;
        }
        return false;
    }

    /**
     * Extracts the owner UUID from the lore of a blood bottle.
     * Returns null if not found.
     */
    public UUID getBloodOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return null;
        for (String line : lore) {
            String plain = stripColor(line).trim();
            if (plain.contains(VBOWNER_PREFIX)) {
                String uuidStr = plain.substring(plain.indexOf(VBOWNER_PREFIX) + VBOWNER_PREFIX.length()).trim();
                try { return UUID.fromString(uuidStr); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // =========================================================================
    // Creation
    // =========================================================================

    /**
     * Creates a blood bottle for the given vampire and gives it to their inventory.
     */
    public void giveBloodBottle(Player vampire) {
        // Determine display name and taste
        String displayName = vampire.getName();
        String displayTaste = "§8Warm. Metallic. Familiar.";

        if (thrallManager.getThrallConfig().isThrallAllowProfilesInBloodBottles()) {
            ThrallProfile profile = thrallManager.getProfile(vampire.getUniqueId());
            if (profile != null && profile.getActiveIndex() > 0) {
                String pName = profile.getActiveName();
                String pTaste = profile.getActiveTaste();
                if (pName != null && !pName.isEmpty())   displayName  = pName;
                if (pTaste != null && !pTaste.isEmpty()) displayTaste = pTaste;
            }
        }

        ItemStack bottle = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        if (meta == null) return;

        meta.setBasePotionType(PotionType.HEALING);
        meta.setDisplayName("§4Vampire Blood");

        List<String> lore = new ArrayList<>();
        lore.add(displayTaste);
        lore.add("§8From: " + displayName);
        lore.add("§8" + VBOWNER_PREFIX + vampire.getUniqueId().toString());
        meta.setLore(lore);
        bottle.setItemMeta(meta);

        vampire.getInventory().addItem(bottle);
    }

    /**
     * Creates a thrall blood bottle for a thrall-vampire and gives it.
     */
    public void giveThrallBloodBottle(Player thrallVamp) {
        String displayName = thrallVamp.getName();
        String displayTaste = "§8Thinned. Warm. Not quite right.";

        if (thrallManager.getThrallConfig().isThrallAllowProfilesInBloodBottles()) {
            ThrallProfile profile = thrallManager.getProfile(thrallVamp.getUniqueId());
            if (profile != null && profile.getActiveIndex() > 0) {
                String pName = profile.getActiveName();
                String pTaste = profile.getActiveTaste();
                if (pName != null && !pName.isEmpty())   displayName  = pName;
                if (pTaste != null && !pTaste.isEmpty()) displayTaste = pTaste;
            }
        }

        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = bottle.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§5Thrall Blood");
        List<String> lore = new ArrayList<>();
        lore.add(displayTaste);
        lore.add("§8From: " + displayName);
        lore.add("§8" + VTHRALL_PREFIX + thrallVamp.getUniqueId().toString());
        meta.setLore(lore);
        bottle.setItemMeta(meta);

        thrallVamp.getInventory().addItem(bottle);
    }

    /**
     * Removes one blood bottle from the player's inventory.
     * Returns true if one was found and removed.
     */
    public boolean takeOneBloodBottle(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isBloodBottle(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().remove(item);
                }
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Strip Minecraft color codes (§x) from a string. */
    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
