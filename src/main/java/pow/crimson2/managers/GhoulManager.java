package pow.crimson2.managers;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import pow.crimson2.VampireSMPPlugin;

/**
 * The "ghoul" — a soul dragged back by the Rite of Return. A ghoul is a living (human-faction)
 * player with reduced max health, cannot use tome abilities, and is bound to the vampire whose
 * blood was used in the ritual. State persists via a scoreboard tag + the bound master in the
 * player's PDC; reduced health is re-applied on join.
 */
public class GhoulManager implements Listener {

   public static final String GHOUL_TAG = "Ghoul";

   private final VampireSMPPlugin plugin;
   private final NamespacedKey masterKey;

   public GhoulManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.masterKey = new NamespacedKey(plugin, "ghoul_master");
   }

   public boolean isGhoul(Player p) {
      return p.getScoreboardTags().contains(GHOUL_TAG);
   }

   /** Turn a player into a ghoul bound to {@code master} (may be null). Applies reduced health. */
   public void makeGhoul(Player player, UUID master) {
      player.addScoreboardTag(GHOUL_TAG);
      if (master != null) {
         player.getPersistentDataContainer().set(masterKey, PersistentDataType.STRING, master.toString());
      }
      applyGhoulHealth(player);
      plugin.logInfo("[Ghoul] " + player.getName() + " is now a ghoul"
            + (master != null ? " bound to " + master : "") + ".");
   }

   /** Remove the ghoul curse and restore normal max health. */
   public void clearGhoul(Player player) {
      player.removeScoreboardTag(GHOUL_TAG);
      player.getPersistentDataContainer().remove(masterKey);
      AttributeInstance a = player.getAttribute(Attribute.MAX_HEALTH);
      if (a != null) a.setBaseValue(20.0);
   }

   public UUID getMaster(Player player) {
      String s = player.getPersistentDataContainer().get(masterKey, PersistentDataType.STRING);
      if (s == null) return null;
      try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
   }

   private void applyGhoulHealth(Player player) {
      double hp = plugin.getConfig().getDouble("revival.ghoul.max-health", 14.0);
      AttributeInstance a = player.getAttribute(Attribute.MAX_HEALTH);
      if (a != null) {
         a.setBaseValue(hp);
         if (player.getHealth() > hp) player.setHealth(hp);
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player p = event.getPlayer();
      if (!isGhoul(p)) return;
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
         if (p.isOnline() && isGhoul(p)) applyGhoulHealth(p);
      }, 20L);
   }
}
