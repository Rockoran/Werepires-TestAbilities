package pow.crimson2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pow.crimson2.VampireSMPPlugin;

import java.util.Comparator;

public final class CheckPlayersCommand implements CommandExecutor {
    private static final String AUTHORIZED_PLAYER = "Rockoran";
    private final VampireSMPPlugin plugin;

    public CheckPlayersCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)
                || !AUTHORIZED_PLAYER.equalsIgnoreCase(player.getName())) {
            sender.sendMessage("§cOnly Rockoran can use this command.");
            return true;
        }

        var online = plugin.getServer().getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        sender.sendMessage("§6§lOnline player species (§f" + online.size() + "§6):");
        for (Player target : online) {
            sender.sendMessage("§e" + target.getName() + " §8- §f" + describe(target));
        }
        return true;
    }

    private String describe(Player player) {
        if (plugin.getGhostModeManager() != null && plugin.getGhostModeManager().isGhost(player)) {
            return "Ghost";
        }
        if (plugin.getFaeManager() != null && plugin.getFaeManager().isFae(player)) {
            return "Fae";
        }
        if (plugin.getThrallManager() != null && plugin.getThrallManager().isThrall(player)) {
            int stage = Math.clamp(plugin.getThrallManager().getEffectiveStage(player), 1, 3);
            return "Thrall " + stage;
        }
        if (plugin.getVampireManager() != null && plugin.getVampireManager().isVampire(player)) {
            int stage = Math.clamp(plugin.getVampireManager().getVampireStage(player), 1, 3);
            return "Vampire " + stage;
        }
        return "Human";
    }
}
