package pow.crimson2.abilities.tome;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import pow.crimson2.VampireSMPPlugin;

public class BlessingTomeAbility extends TomeAbility {
   public BlessingTomeAbility(VampireSMPPlugin plugin) {
      super(
         plugin,
         "Blessing",
         new String[]{"Once per day, you are able to turn a bottle of water", "into a splash bottle of holy water."},
         plugin.getConfigManager().getTomeBlessingCooldown()
      );
   }

   @Override
   protected boolean useAbility(Player player) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }

      if (player.getScoreboardTags().contains("blessing_used_session")) {
         this.sendCannotUseMessage(player, "You have already used Blessing this session!");
         return false;
      }

      PlayerInventory inventory = player.getInventory();
      ItemStack mainHandItem = inventory.getItemInMainHand();
      if (mainHandItem.getType() == Material.POTION && this.isWaterBottle(mainHandItem)) {
         if (mainHandItem.getAmount() > 1) {
            mainHandItem.setAmount(mainHandItem.getAmount() - 1);
            ItemStack splashWater = new ItemStack(Material.SPLASH_POTION, 1);
            this.addHolyWaterDescription(plugin, splashWater);
            if (inventory.firstEmpty() != -1) {
               inventory.addItem(new ItemStack[]{splashWater});
            } else {
               player.getWorld().dropItemNaturally(player.getLocation(), splashWater);
               player.sendMessage("§7Your inventory is full. The holy water was dropped at your feet.");
            }
         } else {
            ItemStack splashWater = new ItemStack(Material.SPLASH_POTION, 1);
            this.addHolyWaterDescription(plugin, splashWater);
            inventory.setItemInMainHand(splashWater);
         }

         player.playSound(player.getLocation(), "minecraft:block.beacon.activate", 0.8F, 1.4F);
         player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 0.5F, 1.2F);
         this.sendSuccessMessage(player, "Divine light flows through the water, blessing it into holy water!");
         player.sendMessage("§7The blessed water can now be thrown as a splash potion.");
         player.addScoreboardTag("blessing_used_session");
         return false;
      } else {
         this.sendCannotUseMessage(player, "You must be holding a water bottle in your main hand!");
         return false;
      }
   }

   public static void addHolyWaterDescription(VampireSMPPlugin plugin, ItemStack item) {
      if (item.getItemMeta() instanceof PotionMeta potionMeta) {
         int durationSeconds = plugin.getConfigManager().getHolyWaterDisableDurationSeconds();
         String durationText;
         if (durationSeconds >= 60) {
            int minutes = durationSeconds / 60;
            durationText = minutes + " minute" + (minutes != 1 ? "s" : "");
         } else {
            durationText = durationSeconds + " second" + (durationSeconds != 1 ? "s" : "");
         }

         potionMeta.setBasePotionType(PotionType.WATER);
         potionMeta.setDisplayName("§aHoly Water");
         potionMeta.setLore(Arrays.asList("§7Throw this on an evil creature to disable their powers for " + durationText + "!"));
         item.setItemMeta(potionMeta);
      }
   }

   private boolean isWaterBottle(ItemStack item) {
      return item.getType() != Material.POTION ? false : !item.hasItemMeta() || item.getItemMeta().getPersistentDataContainer().isEmpty();
   }
}
