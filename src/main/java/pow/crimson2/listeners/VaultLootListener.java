package pow.crimson2.listeners;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Vault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.VaultDisplayItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.event.block.VaultChangeStateEvent;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VaultManager;

/**
 * Makes real (vanilla) vault blocks dispense our custom loot. Vaults don't fire LootGenerateEvent,
 * so we intercept the items the vault ejects: the FIRST item a registered vault ejects each unlock
 * cycle is swapped to a tome / cure book; the rest of that cycle's items are dropped. The dedup is
 * keyed to the unlock cycle (reset when the vault enters UNLOCKING/EJECTING) — a single unlock rolls
 * several items, sometimes spread over a few seconds, so a timer-based dedup is not reliable.
 * {@link VaultDisplayItemEvent} forces the floating display to the right book.
 */
public class VaultLootListener implements Listener {

   private final VampireSMPPlugin plugin;
   /** vault key -> whether we've already given the reward for the current unlock cycle. */
   private final Map<String, Boolean> rewardGiven = new HashMap<>();

   public VaultLootListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(ignoreCancelled = true)
   public void onVaultChangeState(VaultChangeStateEvent event) {
      Location loc = event.getBlock().getLocation();
      VaultManager vm = plugin.getVaultManager();
      if (vm == null || (!vm.isTomeVault(loc) && !vm.isCureVault(loc))) return;
      // A new unlock cycle is starting — allow exactly one reward swap for it.
      if (event.getNewState() == Vault.State.UNLOCKING || event.getNewState() == Vault.State.EJECTING) {
         rewardGiven.put(VaultManager.key(loc), false);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onItemSpawn(ItemSpawnEvent event) {
      VaultManager vm = plugin.getVaultManager();
      if (vm == null) return;
      VaultManager.VaultHit hit = vm.nearestVault(event.getEntity().getLocation(), 2.0);
      if (hit == null || !isEjecting(hit.location)) return; // not a vault that's actively ejecting

      String vk = VaultManager.key(hit.location);
      if (Boolean.TRUE.equals(rewardGiven.get(vk))) {
         event.setCancelled(true); // already rewarded this unlock — drop the extra vanilla rolls
         return;
      }
      rewardGiven.put(vk, true);
      ItemStack book = hit.cure
            ? plugin.getTomeDistributionManager().createOminousVaultBook()  // cure OR revival book
            : plugin.getTomeDistributionManager().createRandomTomeBook();
      if (book != null) {
         event.getEntity().setItemStack(book);
      } else {
         event.setCancelled(true);
      }
   }

   private boolean isEjecting(Location l) {
      try {
         BlockData bd = l.getBlock().getBlockData();
         if (bd instanceof Vault vd) {
            Vault.State s = vd.getVaultState();
            return s == Vault.State.EJECTING || s == Vault.State.UNLOCKING;
         }
      } catch (Throwable ignored) {
      }
      return false;
   }

   @EventHandler(ignoreCancelled = true)
   public void onVaultDisplay(VaultDisplayItemEvent event) {
      Location loc = event.getBlock().getLocation();
      VaultManager vm = plugin.getVaultManager();
      if (vm == null) return;
      if (vm.isTomeVault(loc)) {
         event.setDisplayItem(vm.displayBook(false));
      } else if (vm.isCureVault(loc)) {
         event.setDisplayItem(vm.displayBook(true));
      }
   }
}
