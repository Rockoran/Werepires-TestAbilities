package pow.crimson2.managers;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import pow.crimson2.VampireSMPPlugin;

/**
 * Tracks admin-placed "vault" loot points. Tome vaults hand out a random tome book; ominous cure
 * vaults hand out a cure book. Locations persist in config under {@code vaults.tome} /
 * {@code vaults.cure} as "world,x,y,z" strings. The vault block itself is cosmetic — the
 * {@link pow.crimson2.listeners.VaultListener} drives the unlock on right-click.
 */
public class VaultManager {

   private final VampireSMPPlugin plugin;
   private final List<Location> tomeVaults = new ArrayList<>();
   private final List<Location> cureVaults = new ArrayList<>();

   public VaultManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.load();
   }

   public void reload() {
      this.load();
   }

   private void load() {
      this.tomeVaults.clear();
      this.cureVaults.clear();
      for (String s : plugin.getConfig().getStringList("vaults.tome")) {
         Location l = parse(s);
         if (l != null) this.tomeVaults.add(l);
      }
      for (String s : plugin.getConfig().getStringList("vaults.cure")) {
         Location l = parse(s);
         if (l != null) this.cureVaults.add(l);
      }
   }

   private void save() {
      List<String> t = new ArrayList<>();
      for (Location l : tomeVaults) t.add(fmt(l));
      List<String> c = new ArrayList<>();
      for (Location l : cureVaults) c.add(fmt(l));
      plugin.getConfig().set("vaults.tome", t);
      plugin.getConfig().set("vaults.cure", c);
      plugin.saveConfig();
   }

   private static Location parse(String s) {
      String[] p = s.split(",");
      if (p.length != 4) return null;
      World w = Bukkit.getWorld(p[0].trim());
      if (w == null) return null;
      try {
         return new Location(w, Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private static String fmt(Location l) {
      return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
   }

   private static boolean sameBlock(Location a, Location b) {
      return a.getWorld() != null && a.getWorld().equals(b.getWorld())
            && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
   }

   public boolean addTomeVault(Location l) {
      Location loc = l.getBlock().getLocation();
      if (isTomeVault(loc) || isCureVault(loc)) return false;
      tomeVaults.add(loc);
      save();
      return true;
   }

   public boolean addCureVault(Location l) {
      Location loc = l.getBlock().getLocation();
      if (isTomeVault(loc) || isCureVault(loc)) return false;
      cureVaults.add(loc);
      save();
      return true;
   }

   public boolean isTomeVault(Location l) {
      for (Location e : tomeVaults) if (sameBlock(e, l)) return true;
      return false;
   }

   public boolean isCureVault(Location l) {
      for (Location e : cureVaults) if (sameBlock(e, l)) return true;
      return false;
   }

   public Location removeNearestTomeVault(Location from, double max) {
      return removeNearest(tomeVaults, from, max);
   }

   public Location removeNearestCureVault(Location from, double max) {
      return removeNearest(cureVaults, from, max);
   }

   private Location removeNearest(List<Location> list, Location from, double max) {
      Location best = null;
      double bd = Double.MAX_VALUE;
      for (Location l : list) {
         if (l.getWorld() != null && l.getWorld().equals(from.getWorld())) {
            double d = l.distance(from);
            if (d < bd) { bd = d; best = l; }
         }
      }
      if (best != null && bd <= max) {
         list.remove(best);
         save();
         return best;
      }
      return null;
   }

   public List<Location> getTomeVaults() { return tomeVaults; }
   public List<Location> getCureVaults() { return cureVaults; }

   /** Result of a nearby-vault lookup. */
   public static final class VaultHit {
      public final Location location;
      public final boolean cure;
      VaultHit(Location location, boolean cure) { this.location = location; this.cure = cure; }
   }

   /** The registered vault nearest to {@code loc} within {@code radius} blocks, or null. */
   public VaultHit nearestVault(Location loc, double radius) {
      VaultHit best = null;
      double bd = radius * radius;
      for (Location l : tomeVaults) {
         double d = distSq(l, loc);
         if (d <= bd) { bd = d; best = new VaultHit(l, false); }
      }
      for (Location l : cureVaults) {
         double d = distSq(l, loc);
         if (d <= bd) { bd = d; best = new VaultHit(l, true); }
      }
      return best;
   }

   public static String key(Location l) {
      return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
   }

   private static double distSq(Location a, Location b) {
      if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return Double.MAX_VALUE;
      double dx = a.getBlockX() + 0.5 - b.getX();
      double dy = a.getBlockY() + 0.5 - b.getY();
      double dz = a.getBlockZ() + 0.5 - b.getZ();
      return dx * dx + dy * dy + dz * dz;
   }

   // ── Vanilla vault block configuration ──────────────────────────────────────

   /** The item the vault shows floating above it (the loot is a random book). */
   public ItemStack displayBook(boolean cure) {
      ItemStack out = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) out.getItemMeta();
      if (meta != null) {
         meta.setTitle(cure ? "Sacred Cure" : "Ancient Tome");
         meta.setAuthor("Vault");
         out.setItemMeta(meta);
      }
      return out;
   }

   /**
    * Configure a real vault block: required key, a working reward loot table (so it activates and
    * ejects), and the displayed item. The actual reward is swapped to a tome / cure book by the
    * {@link pow.crimson2.listeners.VaultLootListener}. Idempotent — skips already-configured vaults.
    */
   public void configureVault(Location vaultBlock, boolean cure) {
      World w = vaultBlock.getWorld();
      if (w == null || !w.isChunkLoaded(vaultBlock.getBlockX() >> 4, vaultBlock.getBlockZ() >> 4)) return;
      Block b = vaultBlock.getBlock();
      if (b.getType() != Material.VAULT) b.setType(Material.VAULT);

      // Ominous variant for cure vaults (controls visuals + which key family vanilla expects).
      try {
         BlockData bd = b.getBlockData();
         if (bd instanceof org.bukkit.block.data.type.Vault vd) {
            vd.setOminous(cure);
            b.setBlockData(vd);
         }
      } catch (Throwable ignored) {
      }

      BlockState state = b.getState();
      if (!(state instanceof org.bukkit.block.Vault vault)) return;

      Material wantKey = cure ? Material.OMINOUS_TRIAL_KEY : Material.TRIAL_KEY;
      // The "unique" reward table rolls exactly ONE item per unlock (instead of ~5), so the vault
      // ejects a single item we swap to the tome — nothing extra to suppress.
      LootTable want = (cure ? LootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE
                             : LootTables.TRIAL_CHAMBERS_REWARD_UNIQUE).getLootTable();

      // Skip if already configured (right key + loot table) — avoids resetting the vault each
      // chunk load, but still migrates older vaults that used the multi-item reward table.
      LootTable current = vault.getLootTable();
      ItemStack existingKey = vault.getKeyItem();
      if (existingKey != null && existingKey.getType() == wantKey
            && current != null && want != null && current.getKey().equals(want.getKey())) {
         return;
      }

      vault.setKeyItem(new ItemStack(wantKey));
      vault.setLootTable(want);
      try {
         vault.setDisplayedItem(displayBook(cure));
      } catch (Throwable ignored) {
      }
      vault.update(true, false);
   }

   /** Remove leftover floating ItemDisplay entities from the earlier (non-vanilla) vault system. */
   public void cleanupLegacyDisplays() {
      for (World w : Bukkit.getWorlds()) {
         for (org.bukkit.entity.Entity e : w.getEntities()) {
            if (e instanceof org.bukkit.entity.ItemDisplay && e.getScoreboardTags().contains("vsmp_vault_display")) {
               e.remove();
            }
         }
      }
   }

   /**
    * Clear the "already rewarded" list on every loaded vault, so all players can loot them again.
    * Called when tomes are (re)distributed, so vaults refresh on the same cadence as tome chests.
    */
   public void resetLoadedVaults() {
      int reset = 0;
      for (Location l : tomeVaults) if (resetVault(l)) reset++;
      for (Location l : cureVaults) if (resetVault(l)) reset++;
      if (reset > 0) plugin.logInfo("[Vault] reset " + reset + " vault(s) — lootable again.");
   }

   private boolean resetVault(Location loc) {
      World w = loc.getWorld();
      if (w == null || !w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return false;
      Block b = loc.getBlock();
      if (b.getType() != Material.VAULT) return false;
      BlockState st = b.getState();
      if (!(st instanceof org.bukkit.block.Vault vault)) return false;
      boolean any = false;
      for (java.util.UUID u : new ArrayList<>(vault.getRewardedPlayers())) {
         vault.removeRewardedPlayer(u);
         any = true;
      }
      if (any) vault.update(true, false);
      return any;
   }

   /** Re-apply vault config to every registered vault whose chunk is loaded. */
   public void configureLoadedVaults() {
      for (Location l : tomeVaults) configureVault(l, false);
      for (Location l : cureVaults) configureVault(l, true);
   }

   /** Re-apply vault config for vaults inside a specific (just-loaded) chunk. */
   public void configureVaultsInChunk(World world, int chunkX, int chunkZ) {
      for (Location l : tomeVaults) {
         if (matchesChunk(l, world, chunkX, chunkZ)) configureVault(l, false);
      }
      for (Location l : cureVaults) {
         if (matchesChunk(l, world, chunkX, chunkZ)) configureVault(l, true);
      }
   }

   private static boolean matchesChunk(Location l, World world, int cx, int cz) {
      return l.getWorld() != null && l.getWorld().equals(world)
            && (l.getBlockX() >> 4) == cx && (l.getBlockZ() >> 4) == cz;
   }
}
