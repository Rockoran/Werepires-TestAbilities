package pow.crimson2.listeners;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.ThirstManager;
import pow.crimson2.managers.VampireManager;
import pow.crimson2.thralls.BloodBottleManager;
import pow.crimson2.thralls.ThrallManager;

import java.util.UUID;

public class ExperienceBottleListener implements Listener {
   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private final ThirstManager thirstManager;
   private final ThrallManager thrallManager;
   private final BloodBottleManager bloodBottleManager;

   public ExperienceBottleListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
      this.thirstManager = plugin.getThirstManager();
      this.thrallManager = plugin.getThrallManager();
      this.bloodBottleManager = thrallManager.getBloodBottleManager();
   }

   @EventHandler(priority = EventPriority.NORMAL)
   public void onPlayerInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      ItemStack item = event.getItem();
      if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) return;

      Action action = event.getAction();
      if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

      // Vampire blood (vbowner lore) is now drinkable via FOOD/CONSUMABLE components
      // and handled entirely by BloodConsumeListener — skip it here so it isn't thrown.
      if (bloodBottleManager.isBloodBottle(item)) return;

      event.setCancelled(true);

      // ── Thrall bonding blood (has vthrall lore) ───────────────────────────────
      if (bloodBottleManager.isThrallBloodBottle(item)) {
         if (thrallManager.isThrall(player)) {
            consumeBottle(player, event);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 6 * 20, 1, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,   3 * 20, 0, false, true));
            thrallManager.sendActionBar(player, "§5The blood settles. Not the right kind, but close.");
         } else if (vampireManager.isVampire(player)) {
            // Vampire can still drink thrall blood to quench thirst
            consumeBottle(player, event);
            thirstManager.quenchThirst(player, 4);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
               new TextComponent("§8Thinned. Unsatisfying. But it will do..."));
         } else {
            player.sendMessage("§cYou recoil at the sight of thick crimson blood rolling across the inside of the bottle... Who would collect something like this?");
         }
         return;
      }

      // ── Plain blood (animal kill blood, human blood draw) ────────────────────
      if (vampireManager.isVampire(player)) {
         consumeBottle(player, event);
         thirstManager.quenchThirst(player, 8);
         player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            new TextComponent("§cYou drain the essence from the bottle, satisfying your vampiric thirst..."));
      } else {
         player.sendMessage("§cYou recoil at the sight of thick crimson blood rolling across the inside of the bottle... Who would collect something like this?");
      }
   }

   /** Removes one bottle from the hand that triggered the event and returns a glass bottle. */
   private void consumeBottle(Player player, PlayerInteractEvent event) {
      PlayerInventory inventory = player.getInventory();
      boolean isOffHand = event.getHand() == EquipmentSlot.OFF_HAND;
      ItemStack heldItem = isOffHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
      if (heldItem.getAmount() > 1) {
         heldItem.setAmount(heldItem.getAmount() - 1);
      } else if (isOffHand) {
         inventory.setItemInOffHand(new ItemStack(Material.AIR));
      } else {
         inventory.setItemInMainHand(new ItemStack(Material.AIR));
      }
      ItemStack glass = new ItemStack(Material.GLASS_BOTTLE, 1);
      if (inventory.firstEmpty() != -1) inventory.addItem(glass);
      else player.getWorld().dropItemNaturally(player.getLocation(), glass);
   }
}
