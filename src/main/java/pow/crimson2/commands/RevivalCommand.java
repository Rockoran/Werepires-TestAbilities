package pow.crimson2.commands;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;
import pow.crimson2.managers.RevivalBookManager;
import pow.crimson2.thralls.BloodBottleManager;

/**
 * The Rite of Return — a living player who has read all four revival books calls a ghost back to
 * life. Requires: night, a vial of vampire blood (consumed), and a desecrated beacon nearby (which
 * is consumed → permanently corrupted). The target ghost must linger within range. The revived soul
 * returns as the configured outcome (revival.outcome: vampire | human | thrall | ghoul).
 */
public class RevivalCommand implements CommandExecutor {

   private final VampireSMPPlugin plugin;

   public RevivalCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player reviver)) {
         sender.sendMessage("§cOnly players can perform the rite.");
         return true;
      }
      if (!plugin.getConfig().getBoolean("revival.enabled", true)) {
         reviver.sendMessage("§cThe Rite of Return is forbidden on this server.");
         return true;
      }
      if (plugin.getGhostModeManager() != null && plugin.getGhostModeManager().isGhost(reviver)) {
         reviver.sendMessage("§cThe dead cannot call back the dead.");
         return true;
      }
      if (!RevivalBookManager.hasReadAll(reviver)) {
         reviver.sendMessage("§cYou do not know these forbidden words...");
         reviver.sendMessage("§7You must read all four books of the Rite of Return — including The Price.");
         return true;
      }
      if (args.length < 1) {
         reviver.sendMessage("§cYou must name the soul you wish to call back.");
         return true;
      }

      // Night
      long time = reviver.getWorld().getTime();
      boolean isDay = time >= 0L && time < 12300L;
      if (isDay && plugin.getConfig().getBoolean("revival.needs-night", true)) {
         reviver.sendMessage("§cThe veil is thick by day. This rite can only be spoken at night.");
         return true;
      }

      // Vampire blood
      BloodBottleManager bbm = plugin.getThrallManager() != null ? plugin.getThrallManager().getBloodBottleManager() : null;
      ItemStack blood = findBlood(reviver, bbm);
      if (blood == null) {
         reviver.sendMessage("§cYou need a vial of vampire blood to perform the rite.");
         return true;
      }

      // Desecrated beacon nearby
      double range = plugin.getConfig().getDouble("revival.range", 25.0);
      BeaconSite beacon = plugin.getBeaconManager().getNearestDesecratedBeacon(reviver.getLocation(), range);
      if (beacon == null) {
         reviver.sendMessage("§cYou must stand upon corrupted ground — a desecrated beacon — to call the dead.");
         return true;
      }

      // Target ghost, lingering close
      Player target = Bukkit.getPlayerExact(args[0]);
      if (target == null) {
         reviver.sendMessage("§cThat soul is not here: §e" + args[0]);
         return true;
      }
      if (plugin.getGhostModeManager() == null || !plugin.getGhostModeManager().isGhost(target)) {
         reviver.sendMessage("§c" + target.getName() + " is not a lingering spirit you can call back.");
         return true;
      }
      if (target.getWorld() != reviver.getWorld() || reviver.getLocation().distance(target.getLocation()) > range) {
         reviver.sendMessage("§cThe soul of " + target.getName() + " must linger close to the rite.");
         return true;
      }

      performRevival(reviver, target, blood, beacon, bbm);
      return true;
   }

   private void performRevival(Player reviver, Player target, ItemStack blood, BeaconSite beacon, BloodBottleManager bbm) {
      UUID bloodOwner = bbm != null ? bbm.getBloodOwner(blood) : null;

      // Consume one blood bottle
      if (blood.getAmount() > 1) blood.setAmount(blood.getAmount() - 1);
      else blood.setAmount(0);

      // Consume the beacon: the ritual burns it out for good rather than freeing it. Resetting to
      // neutral handed the site back as contestable ground, which let a single desecrated beacon
      // be recycled for revival after revival.
      plugin.getBeaconManager().setBeaconPermanentlyDesecrated(
         beacon.getName(), "Revival ritual by " + reviver.getName());

      // Pull the soul back into the living world.
      plugin.getGhostModeManager().clearGhostState(target);
      target.setGameMode(GameMode.SURVIVAL);

      String outcome = plugin.getConfig().getString("revival.outcome", "vampire").toLowerCase();
      switch (outcome) {
         case "human" -> plugin.getVampireManager().setPlayerAsHuman(target);
         case "thrall" -> {
            plugin.getVampireManager().setPlayerAsHuman(target);
            if (bloodOwner != null && plugin.getThrallManager() != null) {
               plugin.getThrallManager().applyBlood(target, bloodOwner);
            }
         }
         case "ghoul" -> {
            plugin.getVampireManager().setPlayerAsHuman(target);
            if (plugin.getGhoulManager() != null) plugin.getGhoulManager().makeGhoul(target, bloodOwner);
         }
         default -> plugin.getVampireManager().setPlayerAsVampire(target, 1); // "vampire"
      }

      // Bring the soul to the ritual site.
      target.teleport(reviver.getLocation());

      Location loc = reviver.getLocation();
      loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 80, 0.6, 1.0, 0.6, 0.05);
      loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.02);
      loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0F, 0.5F);
      loc.getWorld().playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.MASTER, 1.0F, 0.7F);
      loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.5F, 1.4F);

      target.sendTitle("§4§lCALLED BACK", "§7You are torn from the veil and made to live again", 10, 80, 30);
      target.sendMessage("§4A forbidden rite drags your soul back into flesh...");
      reviver.sendMessage("§4The rite is complete. §e" + target.getName() + " §4walks among the living once more.");
      String word = "ghoul".equals(outcome) ? "ghoul" : ("vampire".equals(outcome) ? "vampire" : ("thrall".equals(outcome) ? "thrall" : "human"));
      plugin.logInfo("REVIVAL: " + reviver.getName() + " revived " + target.getName() + " as a " + word
            + " (beacon '" + beacon.getName() + "' consumed).");
   }

   private ItemStack findBlood(Player player, BloodBottleManager bbm) {
      if (bbm == null) return null;
      for (ItemStack item : player.getInventory().getContents()) {
         if (item != null && bbm.isBloodBottle(item)) return item;
      }
      return null;
   }
}
