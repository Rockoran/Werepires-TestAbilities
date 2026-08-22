package pow.crimson2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pow.crimson2.VampireSMPPlugin;
import java.util.List;

/** Private runtime control for Rockoran's Fading flight/noclip perks. */
public final class RockoranFadePerksCommand implements CommandExecutor, TabCompleter {
    private final VampireSMPPlugin plugin;

    public RockoranFadePerksCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !"Rockoran".equalsIgnoreCase(player.getName())) {
            sender.sendMessage("§cOnly Rockoran can use this command.");
            return true;
        }
        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            sender.sendMessage("§7Fading bypass (cooldown, flight, noclip): "
                    + (plugin.getFadeManager().areRockoranPerksEnabled(player) ? "§aON" : "§cOFF"));
            sender.sendMessage("§cUsage: /z184761 <on|off>");
            return true;
        }
        boolean enabled = args[0].equalsIgnoreCase("on");
        plugin.getFadeManager().setRockoranPerksEnabled(player, enabled);
        sender.sendMessage("§5Fading cooldown, flight, and noclip bypass: " + (enabled ? "§aON" : "§cOFF"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !"Rockoran".equalsIgnoreCase(player.getName()) || args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase();
        return List.of("on", "off").stream().filter(value -> value.startsWith(typed)).toList();
    }
}
