package pow.crimson2.managers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

public class VampireTexturePackManager {
   private static final UUID BASE_TEXTURE_PACK_ID = UUID.fromString("b75409cb-7f31-4a42-a8d8-8e2dc43d11ae");
   private final VampireSMPPlugin plugin;
   private final VampireManager vampireManager;
   private static final String VAMPIRE_TEXTURE_PACK_URL = "https://download.mc-packs.net/pack/e139890dd34f56724efcd5becb476999651ca43c.zip";
   private static final String VAMPIRE_TEXTURE_PACK_SHA1_STRING = "e139890dd34f56724efcd5becb476999651ca43c";
   private static final String VAMPIRE_TEXTURE_PACK_PROMPT = "§5VampireSMP Vampire Pack\n§7This pack enhances your vampire experience!";
   private static final String HUMAN_TEXTURE_PACK_URL = "https://download.mc-packs.net/pack/b1fbd00667c6ad35c11967a385184aa336d605e1.zip";
   private static final String HUMAN_TEXTURE_PACK_SHA1_STRING = "b1fbd00667c6ad35c11967a385184aa336d605e1";
   private static final String HUMAN_TEXTURE_PACK_PROMPT = "§aVampireSMP Human Pack\n§7This pack enhances your human experience!";
   // Base server pack pushed to every player on join (before any vampire pack is layered on top).
   // Hosted on mc-packs.net; the URL path is the file's SHA-1.
   private final String baseTexturePackUrl;
   private final String baseTexturePackSha1;
   private final String baseTexturePackPrompt;
   private final boolean baseTexturePackRequired;
   private final long baseTexturePackDelay;
   private final Set<UUID> playersWithVampireTexturePack = new HashSet<>();
   private final Set<UUID> playersWithHumanTexturePack = new HashSet<>();
   private final Set<UUID> playersWithBaseTexturePack = new HashSet<>();

   public VampireTexturePackManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.vampireManager = plugin.getVampireManager();
      this.baseTexturePackUrl = plugin.getConfig().getString("server-resource-pack.url", "").strip();
      this.baseTexturePackSha1 = plugin.getConfig().getString("server-resource-pack.sha1", "").strip();
      this.baseTexturePackPrompt = plugin.getConfig().getString("server-resource-pack.prompt", "").strip();
      this.baseTexturePackRequired = plugin.getConfig().getBoolean("server-resource-pack.required", false);
      this.baseTexturePackDelay = Math.max(0L, plugin.getConfig().getLong("server-resource-pack.join-delay-ticks", 20L));
      plugin.logInfo("VampireTexturePackManager initialized");
   }

   public void applyVampireTexturePack(Player player, String reason) {
      try {
         byte[] sha1Bytes = hexStringToByteArray("e139890dd34f56724efcd5becb476999651ca43c");
         UUID packId = UUID.randomUUID();
         player.addResourcePack(
            packId,
            "https://download.mc-packs.net/pack/e139890dd34f56724efcd5becb476999651ca43c.zip",
            sha1Bytes,
            "§5VampireSMP Vampire Pack\n§7This pack enhances your vampire experience!",
            true
         );
         this.playersWithVampireTexturePack.add(player.getUniqueId());
         player.sendMessage("§7Applying vampire texture pack...");
         this.plugin.logInfo("Sent vampire texture pack request to " + player.getName() + " - " + reason);
         this.plugin.logInfo("Pack URL: https://download.mc-packs.net/pack/e139890dd34f56724efcd5becb476999651ca43c.zip");
         this.plugin.logInfo("Pack SHA1: e139890dd34f56724efcd5becb476999651ca43c");
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to apply vampire texture pack to " + player.getName() + ": " + e.getMessage());
         e.printStackTrace();
         player.sendMessage("§cFailed to apply texture pack. Check server logs for details.");
      }
   }

   public void applyVampireTexturePackDelayed(Player player, long delayTicks, String reason) {
      BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline() && this.vampireManager.isVampire(player)) {
            try {
               this.applyVampireTexturePack(player, reason + " (delayed)");
            } catch (Exception e) {
               this.plugin.getLogger().warning("Failed to apply delayed vampire texture pack to " + player.getName() + ": " + e.getMessage());
            }
         } else if (player.isOnline()) {
            this.plugin.logInfo("Skipped vampire texture pack for " + player.getName() + " - no longer a vampire (" + reason + ")");
         }
      }, delayTicks);
      this.plugin.logInfo("Scheduled vampire texture pack for " + player.getName() + " in " + delayTicks / 20.0 + " seconds - " + reason);
   }

   public void onVampireTransformation(Player player) {
      this.plugin.logInfo("Vampire transformation completed for " + player.getName() + " - awaiting voluntary texture pack application");
   }

   public void onVampireLogin(Player player) {
      this.applyVampireTexturePackDelayed(player, 100L, "vampire login");
   }

   /**
    * Push the base server texture pack. Called for every player on join so that anyone who
    * hasn't loaded the server pack yet receives it. Vampires additionally get the vampire
    * pack layered on top a moment later (see {@link #onVampireLogin(Player)}).
    */
   public void applyBaseTexturePack(Player player, String reason) {
      if (!this.plugin.getConfig().getBoolean("server-resource-pack.enabled", true)) return;
      try {
         if (!this.baseTexturePackSha1.matches("(?i)[0-9a-f]{40}")) throw new IllegalArgumentException("Base texture pack SHA-1 must be 40 hexadecimal characters");
         byte[] sha1Bytes = hexStringToByteArray(this.baseTexturePackSha1);
         String prompt = this.baseTexturePackPrompt.isBlank() ? null : this.baseTexturePackPrompt;
         player.addResourcePack(BASE_TEXTURE_PACK_ID, this.baseTexturePackUrl, sha1Bytes, prompt, this.baseTexturePackRequired);
         this.playersWithBaseTexturePack.add(player.getUniqueId());
         player.sendMessage("§7Applying server texture pack...");
         this.plugin.logInfo("Sent base server texture pack to " + player.getName() + " - " + reason);
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to apply base texture pack to " + player.getName() + ": " + e.getMessage());
         e.printStackTrace();
      }
   }

   /** Push the base server pack shortly after join, once the connection has settled. */
   public void onPlayerLogin(Player player) {
      this.plugin.logInfo("Scheduled base server texture pack for " + player.getName() + " in " + this.baseTexturePackDelay / 20.0 + " seconds - player join");
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            this.applyBaseTexturePack(player, "player join");
         }
      }, this.baseTexturePackDelay);
   }

   public boolean hasBaseTexturePack(Player player) {
      return this.playersWithBaseTexturePack.contains(player.getUniqueId());
   }

   public void onPlayerBecomeHuman(Player player) {
      this.playersWithVampireTexturePack.remove(player.getUniqueId());
   }

   public void applyHumanTexturePack(Player player, String reason) {
      try {
         byte[] sha1Bytes = hexStringToByteArray("b1fbd00667c6ad35c11967a385184aa336d605e1");
         UUID packId = UUID.randomUUID();
         player.addResourcePack(
            packId,
            "https://download.mc-packs.net/pack/b1fbd00667c6ad35c11967a385184aa336d605e1.zip",
            sha1Bytes,
            "§aVampireSMP Human Pack\n§7This pack enhances your human experience!",
            true
         );
         this.playersWithHumanTexturePack.add(player.getUniqueId());
         this.playersWithVampireTexturePack.remove(player.getUniqueId());
         player.sendMessage("§7Applying human texture pack...");
         this.plugin.logInfo("Sent human texture pack request to " + player.getName() + " - " + reason);
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to apply human texture pack to " + player.getName() + ": " + e.getMessage());
         e.printStackTrace();
         player.sendMessage("§cFailed to apply texture pack. Check server logs for details.");
      }
   }

   public void applyHumanTexturePackDelayed(Player player, long delayTicks, String reason) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            this.applyHumanTexturePack(player, reason + " (delayed)");
         }
      }, delayTicks);
   }

   public boolean hasHumanTexturePack(Player player) {
      return this.playersWithHumanTexturePack.contains(player.getUniqueId());
   }

   public void onPlayerQuit(Player player) {
      this.playersWithVampireTexturePack.remove(player.getUniqueId());
      this.playersWithHumanTexturePack.remove(player.getUniqueId());
      this.playersWithBaseTexturePack.remove(player.getUniqueId());
   }

   public boolean hasVampireTexturePack(Player player) {
      return this.playersWithVampireTexturePack.contains(player.getUniqueId());
   }

   public void manualApplication(Player player) {
      this.applyVampireTexturePack(player, "admin command");
   }

   public void forceApplyVampireTexturePack(Player player, String reason) {
      this.playersWithVampireTexturePack.remove(player.getUniqueId());
      this.applyVampireTexturePack(player, reason + " (forced)");
   }

   public void ensureAllVampiresHaveTexturePack() {
      int applied = 0;

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.vampireManager.isVampire(player)) {
            long delay = applied * 20L;
            this.applyVampireTexturePackDelayed(player, delay, "ensure all vampires");
            applied++;
         }
      }

      if (applied > 0) {
         this.plugin.logInfo("Ensured vampire texture pack for " + applied + " online vampires");
      }
   }

   private static byte[] hexStringToByteArray(String s) {
      int len = s.length();
      byte[] data = new byte[len / 2];

      for (int i = 0; i < len; i += 2) {
         data[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
      }

      return data;
   }

   public void shutdown() {
      this.playersWithVampireTexturePack.clear();
      this.playersWithHumanTexturePack.clear();
      this.playersWithBaseTexturePack.clear();
      this.plugin.logInfo("VampireTexturePackManager shutdown complete");
   }
}
