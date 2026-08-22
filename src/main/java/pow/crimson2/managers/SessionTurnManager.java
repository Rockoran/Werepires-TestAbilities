package pow.crimson2.managers;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

/** Session-scoped shared allowance for successful vampire and werewolf turns. */
public final class SessionTurnManager {
    private static final String PATH = "session-turns.";
    private static final int MAX_TURNS = 100000;
    private final VampireSMPPlugin plugin;
    private boolean limited;
    private int limit;
    private int used;

    public SessionTurnManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.limited = plugin.getStateConfig().getBoolean(PATH + "limited", false);
        this.limit = Math.max(0, plugin.getStateConfig().getInt(PATH + "limit", 0));
        this.used = Math.clamp(plugin.getStateConfig().getInt(PATH + "used", 0), 0, limit);
    }

    public static boolean validAmount(int amount) { return amount >= 0 && amount <= MAX_TURNS; }
    public boolean isLimited() { return limited; }
    public int getRemaining() { return limited ? Math.max(0, limit - used) : Integer.MAX_VALUE; }
    public boolean canTurn() { return !limited || used < limit; }

    public void setLimit(int amount, CommandSender sender) {
        limited = true;
        limit = amount;
        used = 0;
        save();
        Bukkit.broadcastMessage("§5§lSESSION TURNS §8| §fTurn limit set to §d" + amount
                + "§f by §d" + sender.getName() + "§f.");
    }

    public boolean extend(int amount, CommandSender sender) {
        if (!limited || amount < 1 || limit > MAX_TURNS - amount) return false;
        limit += amount;
        save();
        Bukkit.broadcastMessage("§5§lSESSION TURNS §8| §fTurn allowance extended by §d" + amount
                + "§f. Remaining: §d" + getRemaining() + "§f.");
        return true;
    }

    /** Consume one slot only after every other turn condition has succeeded. */
    public boolean consume(Player turner, Player target, String species) {
        if (!limited) return true;
        if (!canTurn()) return false;
        used++;
        save();
        int remaining = getRemaining();
        turner.sendMessage("§5Session turns remaining: §f" + remaining);
        plugin.logInfo("Session turn " + used + "/" + limit + ": " + turner.getName()
                + " turned " + target.getName() + " into " + species);
        if (remaining == 0) {
            Bukkit.broadcastMessage("§4§lTURN LIMIT REACHED §8| §cNo more players may be turned this session.");
        }
        return true;
    }

    public void status(CommandSender sender) {
        if (!limited) sender.sendMessage("§7Session turns are currently §funlimited§7.");
        else sender.sendMessage("§5Session turns: §f" + used + " used §8/ §f" + limit
                + " total §8(§d" + getRemaining() + " remaining§8)");
    }

    /** Called only by a real session end; pause/break deliberately does not clear it. */
    public void clearAtSessionEnd() {
        if (!limited && limit == 0 && used == 0) return;
        limited = false;
        limit = 0;
        used = 0;
        save();
    }

    private void save() {
        plugin.getStateConfig().set(PATH + "limited", limited);
        plugin.getStateConfig().set(PATH + "limit", limit);
        plugin.getStateConfig().set(PATH + "used", used);
        plugin.saveStateConfig();
    }
}
