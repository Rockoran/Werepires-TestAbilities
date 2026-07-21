package pow.crimson2.managers;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pow.crimson2.VampireSMPPlugin;

/**
 * The four "Rite of Return" books — the revival counterpart to the cure books. Reading them teaches
 * the ritual that brings a ghost back to life:
 *   I   — the rite needs vampire blood
 *   II  — it must be performed at night
 *   III — it requires a desecrated beacon
 *   IV  — "The Price": reveals the cost and unlocks the rite (only readable after I–III)
 * Reading sets scoreboard tags; all four are required to perform the ritual.
 */
public class RevivalBookManager implements Listener {

   public static final String TAG_1 = "RevivalBook1Read";
   public static final String TAG_2 = "RevivalBook2Read";
   public static final String TAG_3 = "RevivalBook3Read";
   public static final String TAG_4 = "RevivalBook4Read";

   private final VampireSMPPlugin plugin;
   private final NamespacedKey key;

   public RevivalBookManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.key = new NamespacedKey(plugin, "vampiresmp_revival_book");
   }

   public static boolean hasReadConditions(Player p) {
      return p.getScoreboardTags().contains(TAG_1)
            && p.getScoreboardTags().contains(TAG_2)
            && p.getScoreboardTags().contains(TAG_3);
   }

   public static boolean hasReadAll(Player p) {
      return hasReadConditions(p) && p.getScoreboardTags().contains(TAG_4);
   }

   // ── Reading ─────────────────────────────────────────────────────────────────

   @EventHandler
   public void onInteract(PlayerInteractEvent event) {
      if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
      ItemStack item = event.getItem();
      int num = authenticNumber(item);
      if (num < 1) return;
      event.setCancelled(true);
      Player p = event.getPlayer();

      if (num == 4 && !hasReadConditions(p)) {
         // The Price stays sealed until the three conditions are learned.
         p.openBook(item);
         p.sendMessage("§8§o[The words within The Price are beyond you... you must first learn all three conditions of the rite.]");
         return;
      }

      String tag = switch (num) {
         case 1 -> TAG_1;
         case 2 -> TAG_2;
         case 3 -> TAG_3;
         case 4 -> TAG_4;
         default -> null;
      };
      String bookName = switch (num) {
         case 1 -> "The Offering";
         case 2 -> "The Witching Hour";
         case 3 -> "The Broken Light";
         case 4 -> "The Price";
         default -> "the rite";
      };
      if (tag == null) return;

      boolean already = p.getScoreboardTags().contains(tag);
      p.openBook(item);
      if (already) {
         p.sendMessage("§7These words are already etched into your memory.");
         return;
      }

      p.addScoreboardTag(tag);
      p.sendMessage("§8§o[You absorb the forbidden knowledge within " + bookName + "...]");
      p.playSound(p, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.MASTER, 1.0F, 0.6F);
      plugin.logInfo("[Revival] " + p.getName() + " read revival book " + num);

      // Completing the three conditions reveals what the rite demands, and lets The Price surface in loot.
      if (num != 4 && hasReadConditions(p)) {
         p.sendMessage("");
         p.sendMessage("§5§lTHE THREE CONDITIONS ARE KNOWN.");
         p.sendMessage("§dThe rite demands vampire blood, the dead of night, and ground broken by desecration.");
         p.sendMessage("§7But the words themselves, and their price, remain hidden in a final tome.");
         p.sendMessage("");
         if (!plugin.getConfig().getBoolean("revival.fourth-book-spawn-enabled", false)) {
            plugin.getConfig().set("revival.fourth-book-spawn-enabled", true);
            plugin.saveConfig();
            plugin.logInfo("[Revival] " + p.getName() + " learned the conditions; The Price can now spawn.");
         }
      }

      // Reading The Price (after the conditions) reveals the actual ritual command.
      if (num == 4 && hasReadAll(p)) {
         p.sendMessage("");
         p.sendMessage("§4§lTHE RITE OF RETURN IS YOURS.");
         p.sendMessage("§cYou have accepted the price. You now know the forbidden words to call a soul back:");
         p.sendMessage("§7/§4sanguine-et-nocte-te-ex-umbra-revoco §7<§4player§7>");
         p.sendMessage("§7Hold vampire blood, stand at a desecrated beacon in the dead of night, with the lingering soul close, and speak the words.");
         p.sendMessage("");
         plugin.logInfo("[Revival] " + p.getName() + " has unlocked the Rite of Return command.");
      }
   }

   // ── Book item creation + authentication ─────────────────────────────────────

   public int authenticNumber(ItemStack item) {
      if (item == null || item.getType() != Material.WRITTEN_BOOK) return 0;
      if (!(item.getItemMeta() instanceof BookMeta meta)) return 0;
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      return pdc.has(key, PersistentDataType.INTEGER) ? pdc.get(key, PersistentDataType.INTEGER) : 0;
   }

   public ItemStack createBook(int n) {
      if (n < 1 || n > 4) n = 1;
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      if (meta != null) {
         meta.setTitle(titleFor(n));
         meta.setAuthor("§4A whisper from beyond");
         meta.setPages(pageFor(n));
         meta.setLore(List.of("§8A fragment of the forbidden Rite of Return."));
         meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, n);
         book.setItemMeta(meta);
      }
      return book;
   }

   private static String titleFor(int n) {
      return switch (n) {
         case 1 -> "The Offering 1/4";
         case 2 -> "The Witching Hour 2/4";
         case 3 -> "The Broken Light 3/4";
         case 4 -> "The Price 4/4";
         default -> "The Rite of Return";
      };
   }

   private static String[] pageFor(int n) {
      return switch (n) {
         case 1 -> new String[]{"§5§lThe Offering§r\n\n§0To call a soul back from beyond the veil you must offer that which sustains the undead: the §4blood of a vampire§0, drawn and bottled.\n\n§0No mortal drink will answer."};
         case 2 -> new String[]{"§5§lThe Witching Hour§r\n\n§0The veil thins only under a sunless sky. The rite must be spoken in the §4dead of night§0, when the sun cannot witness what you do."};
         case 3 -> new String[]{"§5§lThe Broken Light§r\n\n§0Stand where the light has been broken — a beacon fallen to §4desecration§0.\n\n§0Only upon corrupted ground will the dead hear your call, and the soul you seek must linger near."};
         case 4 -> new String[]{"§4§lThe Price§r\n\n§0Know this before you speak the words. A soul dragged back is never whole.\n\n§0It returns §4lesser§0 — hollow and weak, bound in service to the blood that called it.",
               "§4§lThe Price§r\n\n§0The desecrated beacon is consumed utterly, its corruption spent to pay the toll.\n\n§0Defy death if you dare. Speak the rite over the fallen, blood in hand, and call them back."};
         default -> new String[]{"§0..."};
      };
   }
}
