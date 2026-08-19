package pow.crimson2.phone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PhoneCommand implements CommandExecutor, TabCompleter {
    private final PhoneManager manager;

    public PhoneCommand(PhoneManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "givephone" -> { return give(sender, args.length == 0 ? null : args[0]); }
            case "phonegive" -> {
                if (!sender.hasPermission("vampiresmp.phone.admin")) return denied(sender);
                if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
                    Bukkit.getOnlinePlayers().forEach(manager::givePhone);
                    sender.sendMessage(Component.text("Issued phones to all online players.", NamedTextColor.GREEN));
                    return true;
                }
                return give(sender, args.length == 0 ? null : args[0]);
            }
            case "checkphonestatus" -> {
                if (!(sender instanceof Player player)) return playersOnly(sender);
                sender.sendMessage(Component.text(manager.hasPhone(player) ? "You have a phone." : "You do not have a phone.",
                        manager.hasPhone(player) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return true;
            }
            case "phonereset" -> {
                if (!sender.hasPermission("vampiresmp.phone.admin")) return denied(sender);
                if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(Component.text("Run /phonereset confirm to clear session phone data. Social handles are preserved.", NamedTextColor.YELLOW));
                    return true;
                }
                manager.store().resetSessionData();
                sender.sendMessage(Component.text("Phone session data reset.", NamedTextColor.GREEN));
                Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(Component.text(
                        "Phone reset for a new session — settings and social handle kept.", NamedTextColor.GRAY)));
                return true;
            }
            case "cellphone" -> { return cellphone(sender, args); }
            default -> { return false; }
        }
    }

    private boolean cellphone(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) return playersOnly(sender);
            manager.openMain(player);
            return true;
        }
        if (!sender.hasPermission("vampiresmp.phone.admin")) return denied(sender);
        if (args[0].equalsIgnoreCase("reload")) {
            manager.store().load();
            sender.sendMessage(Component.text("Phone data reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) return give(sender, args.length > 1 ? args[1] : null);
        if (args[0].equalsIgnoreCase("open") && args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Player is not online.", NamedTextColor.RED)); return true; }
            manager.openMain(target);
            return true;
        }
        sender.sendMessage(Component.text("/cellphone [give <player>|open <player>|reload]", NamedTextColor.YELLOW));
        return true;
    }

    private boolean give(CommandSender sender, String name) {
        if (!sender.hasPermission("vampiresmp.phone.admin")) return denied(sender);
        Player target = name == null && sender instanceof Player player ? player : Bukkit.getPlayerExact(name == null ? "" : name);
        if (target == null) { sender.sendMessage(Component.text("Player is not online.", NamedTextColor.RED)); return true; }
        manager.updateIdentity(target);
        manager.givePhone(target);
        sender.sendMessage(Component.text("Issued a phone to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean denied(CommandSender sender) { sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED)); return true; }
    private boolean playersOnly(CommandSender sender) { sender.sendMessage(Component.text("Players only.", NamedTextColor.RED)); return true; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("cellphone") && args.length == 1) choices.addAll(List.of("give", "open", "reload"));
        if (command.getName().equalsIgnoreCase("phonegive") && args.length == 1) choices.add("all");
        if (command.getName().equalsIgnoreCase("phonereset") && args.length == 1) choices.add("confirm");
        if (args.length <= 2) Bukkit.getOnlinePlayers().forEach(player -> choices.add(player.getName()));
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        return choices.stream().filter(value -> value.toLowerCase().startsWith(prefix)).distinct().sorted().toList();
    }
}
