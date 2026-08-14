package pow.crimson2.phone;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PhoneCallCommand implements CommandExecutor {
    private final PhoneManager manager;
    public PhoneCallCommand(PhoneManager manager) { this.manager = manager; }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        switch (command.getName().toLowerCase()) {
            case "answer", "cpacceptcall" -> manager.calls().answer(player);
            case "decline", "cpdeclinecall" -> manager.calls().decline(player);
            case "hangup", "cphanghup" -> manager.calls().hangup(player);
            default -> { return false; }
        }
        return true;
    }
}
