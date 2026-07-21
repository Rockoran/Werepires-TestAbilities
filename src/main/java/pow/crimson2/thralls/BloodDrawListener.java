package pow.crimson2.thralls;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles blood draw: sneak + right-click while holding a glass bottle
 * draws blood and converts the glass bottle into a blood bottle.
 */
public class BloodDrawListener implements Listener {

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;
    private final BloodBottleManager bloodBottleManager;

    public BloodDrawListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.thrallManager = plugin.getThrallManager();
        this.bloodBottleManager = thrallManager.getBloodBottleManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!thrallManager.getThrallConfig().isThrallBloodDrawEnabled()) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        // Check if holding a glass bottle in either hand
        boolean hasGlass = isGlassBottle(player.getInventory().getItemInMainHand())
                        || isGlassBottle(player.getInventory().getItemInOffHand());
        if (!hasGlass) return;

        if (thrallManager.canDrawBlood(player)) {
            // Vampire blood draw
            event.setCancelled(true);
            handleVampireBloodDraw(player);

        } else if (thrallManager.isThrallVampire(player)) {
            // Thrall vampire blood draw
            event.setCancelled(true);
            handleThrallBloodDraw(player);

        } else if (plugin.getVampireManager().isHuman(player)
                && thrallManager.getThrallConfig().isHumanBloodDrawEnabled()) {
            // Human self blood draw
            ThrallConfig cfg = thrallManager.getThrallConfig();
            if (cfg.isHumanBloodDrawThrallsOnly() && !thrallManager.isThrall(player)) {
                event.setCancelled(true);
                thrallManager.sendActionBar(player, "§7Only thralls may bottle their own blood.");
                return;
            }
            event.setCancelled(true);
            handleHumanBloodDraw(player);

        } else if (!thrallManager.getThrallConfig().isHumanBloodDrawEnabled()) {
            // Not eligible — hint about which stages can draw
            event.setCancelled(true);
            StringBuilder stages = new StringBuilder();
            if (thrallManager.getThrallConfig().isThrallBloodDrawStage1()) stages.append("stage 1, ");
            if (thrallManager.getThrallConfig().isThrallBloodDrawStage2()) stages.append("stage 2, ");
            if (thrallManager.getThrallConfig().isThrallBloodDrawStage3()) stages.append("stage 3, ");
            String stageStr = stages.length() > 0 ? stages.toString().replaceAll(", $", "") : "no stage";
            thrallManager.sendActionBar(player, "§7Only " + stageStr + " vampires can bottle blood.");
        }
    }

    private void handleVampireBloodDraw(Player player) {
        // Cooldown check
        if (thrallManager.isOnCooldown("blood", player)) {
            thrallManager.sendActionBar(player, "§7Your body needs a moment to recover.");
            return;
        }

        double dmg = thrallManager.getThrallConfig().getThrallBloodDrawDamageHearts() * 2.0;
        if (player.getHealth() <= dmg) {
            thrallManager.sendActionBar(player, "§7You are too weakened to spare blood.");
            return;
        }

        thrallManager.setCooldown("blood", player);
        thrallManager.sendActionBar(player, "§8You press glass to your wrist...");
        startDrinkingAnimation(player);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            thrallManager.sendActionBar(player, "§4Warm. Metallic. Familiar.");
        }, 3L);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            removeOneGlassBottle(player);
            player.damage(dmg);
            bloodBottleManager.giveBloodBottle(player);
            thrallManager.sendActionBar(player, "§8Blood sealed in glass.");
        }, 6L);
    }

    private void handleThrallBloodDraw(Player player) {
        if (thrallManager.isOnCooldown("blood", player)) {
            thrallManager.sendActionBar(player, "§7Your body needs a moment to recover.");
            return;
        }

        double dmg = thrallManager.getThrallConfig().getThrallBloodDrawDamageHearts() * 2.0;
        if (player.getHealth() <= dmg) {
            thrallManager.sendActionBar(player, "§7You are too weakened to spare blood.");
            return;
        }

        thrallManager.setCooldown("blood", player);
        thrallManager.sendActionBar(player, "§8You press glass to your wrist...");
        startDrinkingAnimation(player);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            thrallManager.sendActionBar(player, "§5Thin. Warm. Not quite right.");
        }, 3L);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            removeOneGlassBottle(player);
            player.damage(dmg);
            bloodBottleManager.giveThrallBloodBottle(player);
            thrallManager.sendActionBar(player, "§8Thrall blood sealed in glass.");
        }, 6L);
    }

    private void handleHumanBloodDraw(Player player) {
        if (thrallManager.isOnCooldown("human_blood", player)) {
            thrallManager.sendActionBar(player, "§7Your body needs a moment to recover.");
            return;
        }

        ThrallConfig cfg = thrallManager.getThrallConfig();
        double dmg = cfg.getHumanBloodDrawDamageHearts() * 2.0;
        if (player.getHealth() <= dmg) {
            thrallManager.sendActionBar(player, "§7You are too weakened to spare blood.");
            return;
        }

        thrallManager.setCooldown("human_blood", player);
        thrallManager.sendActionBar(player, "§8You press glass to your wrist...");
        startDrinkingAnimation(player);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            thrallManager.sendActionBar(player, "§7Warm. Ordinary. Mortal.");
        }, 3L);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            removeOneGlassBottle(player);
            player.damage(dmg);
            bloodBottleManager.giveHumanBloodBottle(player);
            thrallManager.sendActionBar(player, "§8Human blood sealed in glass.");
        }, 6L);
    }

    /** Plays the vampire drinking sound + arm-swing animation for the draw duration. */
    private void startDrinkingAnimation(Player player) {
        // Pulse the witch-drink sound 3 times over ~6 ticks to mimic consuming
        for (int tick : new int[]{0, 2, 4}) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.getWorld().playSound(
                        player.getLocation(),
                        Sound.ENTITY_WITCH_DRINK,
                        SoundCategory.PLAYERS,
                        1.0F, 0.8F);
                player.swingMainHand();
            }, tick);
        }
    }

    private boolean isGlassBottle(ItemStack item) {
        return item != null && item.getType() == Material.GLASS_BOTTLE;
    }

    private void removeOneGlassBottle(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isGlassBottle(main)) {
            if (main.getAmount() > 1) main.setAmount(main.getAmount() - 1);
            else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isGlassBottle(off)) {
            if (off.getAmount() > 1) off.setAmount(off.getAmount() - 1);
            else player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }
    }
}
