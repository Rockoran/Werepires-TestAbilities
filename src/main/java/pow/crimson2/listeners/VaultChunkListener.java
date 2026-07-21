package pow.crimson2.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import pow.crimson2.VampireSMPPlugin;

/** Re-applies vault block configuration (key / loot table / display) for vaults in a loaded chunk. */
public class VaultChunkListener implements Listener {

   private final VampireSMPPlugin plugin;

   public VaultChunkListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
      if (plugin.getVaultManager() == null) return;
      Chunk c = event.getChunk();
      // Re-apply next tick — block states are best touched after the chunk fully loads.
      Bukkit.getScheduler().runTask(plugin, () ->
            plugin.getVaultManager().configureVaultsInChunk(c.getWorld(), c.getX(), c.getZ()));
   }
}
