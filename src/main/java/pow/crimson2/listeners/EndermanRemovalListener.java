package pow.crimson2.listeners;

import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import pow.crimson2.VampireSMPPlugin;

public class EndermanRemovalListener implements Listener {
   private final VampireSMPPlugin plugin;
   private final AtomicBoolean endermanRemovalEnabled;

   public EndermanRemovalListener(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.endermanRemovalEnabled = new AtomicBoolean(true);
      plugin.logInfo("EndermanRemovalListener initialized - Enderman removal is ENABLED");
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (this.endermanRemovalEnabled.get()) {
         if (event.getEntityType() == EntityType.ENDERMAN) {
            event.setCancelled(true);
            this.plugin
               .logInfo(
                  "Prevented Enderman spawn at "
                     + event.getLocation().getBlockX()
                     + ", "
                     + event.getLocation().getBlockY()
                     + ", "
                     + event.getLocation().getBlockZ()
                     + " (Reason: "
                     + event.getSpawnReason()
                     + ")"
               );
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onChunkLoad(ChunkLoadEvent event) {
      if (this.endermanRemovalEnabled.get()) {
         Chunk chunk = event.getChunk();
         Entity[] entities = chunk.getEntities();
         int removedCount = 0;

         for (Entity entity : entities) {
            if (entity instanceof Enderman) {
               entity.remove();
               removedCount++;
            }
         }
      }
   }

   public boolean isEndermanRemovalEnabled() {
      return this.endermanRemovalEnabled.get();
   }

   public void setEndermanRemovalEnabled(boolean enabled) {
      this.endermanRemovalEnabled.set(enabled);
      this.plugin.logInfo("Enderman removal " + (enabled ? "ENABLED" : "DISABLED"));
   }

   public int removeAllEndermen() {
      int totalRemoved = 0;

      for (Chunk chunk : this.plugin.getWorld().getLoadedChunks()) {
         Entity[] entities = chunk.getEntities();

         for (Entity entity : entities) {
            if (entity instanceof Enderman) {
               entity.remove();
               totalRemoved++;
            }
         }
      }

      this.plugin.logInfo("Removed " + totalRemoved + " Endermen from all loaded chunks");
      return totalRemoved;
   }

   public void shutdown() {
      this.plugin.logInfo("EndermanRemovalListener shutdown");
   }
}
