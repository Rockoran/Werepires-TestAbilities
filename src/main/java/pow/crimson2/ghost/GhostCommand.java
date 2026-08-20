package pow.crimson2.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import pow.crimson2.VampireSMPPlugin;

/** {@code /ghost} — lets a haunting player reveal/conceal themselves to specific players. */
public class GhostCommand implements CommandExecutor, TabCompleter {

    private final VampireSMPPlugin plugin;
    private final GhostModeManager ghost;

    public GhostCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.ghost = plugin.getGhostModeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this.");
            return true;
        }

        // setfree: end haunting and become a plain spectator (self, or admin freeing another).
        if (args.length >= 1 && args[0].equalsIgnoreCase("setfree")) {
            if (args.length >= 2) {
                if (!p.hasPermission("vampiresmp.admin")) {
                    p.sendMessage("§cYou don't have permission to free others.");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { p.sendMessage("§7Player not found."); return true; }
                if (!ghost.isGhost(t)) { p.sendMessage("§7" + t.getName() + " is not haunting."); return true; }
                ghost.becomeSpectator(t, true);
                p.sendMessage("§7You released §f" + t.getName() + " §7from haunting.");
            } else {
                if (!ghost.isGhost(p)) { p.sendMessage("§7You are not haunting these lands."); return true; }
                ghost.becomeSpectator(p, true);
                p.sendMessage("§7You let go of this world. You drift on as a spectator.");
            }
            return true;
        }

        if (!ghost.isGhost(p)) {
            p.sendMessage("§7You are not haunting these lands.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "show": {
                if (args.length < 2) { p.sendMessage("§7/ghost show <player>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null || t.equals(p)) { p.sendMessage("§7Player not found."); return true; }
                ghost.revealTo(p, t);
                p.sendMessage("§5You let §f" + t.getName() + " §5glimpse your form.");
                t.sendMessage("§5A chill runs down your spine... something watches you.");
                break;
            }
            case "hide": {
                if (args.length < 2) { p.sendMessage("§7/ghost hide <player>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { p.sendMessage("§7Player not found."); return true; }
                ghost.concealFrom(p, t);
                p.sendMessage("§7You fade from §f" + t.getName() + "§7's sight.");
                break;
            }
            case "showall": {
                for (Player t : Bukkit.getOnlinePlayers()) if (!t.equals(p)) ghost.revealTo(p, t);
                p.sendMessage("§5You reveal yourself to all the living.");
                break;
            }
            case "hideall": {
                for (Player t : Bukkit.getOnlinePlayers()) if (!t.equals(p)) ghost.concealFrom(p, t);
                p.sendMessage("§7You vanish from all sight.");
                break;
            }
            case "haunt": {
                if (args.length < 2) {
                    p.sendMessage("§7/ghost haunt <player> §8— add them to who hears you");
                    p.sendMessage("§7/ghost haunt all §8— haunt everyone online");
                    p.sendMessage("§7/ghost haunt list §8— who you are haunting");
                    return true;
                }

                if (args[1].equalsIgnoreCase("list")) {
                    Set<UUID> haunted = ghost.getHauntTargets(p.getUniqueId());
                    if (haunted.isEmpty()) {
                        p.sendMessage("§7You are haunting no one.");
                    } else {
                        StringBuilder sb = new StringBuilder("§5Haunting §f");
                        boolean first = true;
                        for (UUID id : haunted) {
                            Player hp = Bukkit.getPlayer(id);
                            if (hp == null) continue;
                            if (!first) sb.append("§7, §f");
                            sb.append(hp.getName());
                            first = false;
                        }
                        p.sendMessage(sb.toString());
                    }
                    return true;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    int added = 0;
                    for (Player t2 : Bukkit.getOnlinePlayers()) {
                        if (t2.equals(p)) continue;
                        if (ghost.addHauntTarget(p.getUniqueId(), t2.getUniqueId())) {
                            t2.sendMessage("§5A voice you cannot place whispers at the edge of hearing...");
                            added++;
                        }
                    }
                    p.sendMessage(added == 0
                        ? "§7You already haunt everyone here."
                        : "§5You haunt §f" + added + "§5 more — your voice reaches them all.");
                    return true;
                }

                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null || t.equals(p)) { p.sendMessage("§7Player not found."); return true; }
                if (!ghost.addHauntTarget(p.getUniqueId(), t.getUniqueId())) {
                    p.sendMessage("§7You already haunt §f" + t.getName() + "§7.");
                    return true;
                }
                int count = ghost.getHauntTargets(p.getUniqueId()).size();
                p.sendMessage("§5You haunt §f" + t.getName() + "§5 — your voice reaches "
                    + (count == 1 ? "only them." : "them and §f" + (count - 1) + "§5 other(s)."));
                t.sendMessage("§5A voice you cannot place whispers at the edge of hearing...");
                break;
            }
            case "unhaunt":
            case "stop": {
                if (args.length >= 2) {
                    Player t = Bukkit.getPlayerExact(args[1]);
                    if (t == null) { p.sendMessage("§7Player not found."); return true; }
                    p.sendMessage(ghost.removeHauntTarget(p.getUniqueId(), t.getUniqueId())
                        ? "§7You release §f" + t.getName() + "§7 from your voice."
                        : "§7You were not haunting " + t.getName() + ".");
                    return true;
                }
                int released = ghost.clearHauntTarget(p.getUniqueId());
                p.sendMessage(released == 0
                    ? "§7You were haunting no one."
                    : "§7You release your voice from §f" + released + "§7 listener(s).");
                break;
            }
            case "list": {
                Set<UUID> rev = ghost.getRevealedTo(p);
                if (rev.isEmpty()) {
                    p.sendMessage("§7No one can see you.");
                } else {
                    StringBuilder sb = new StringBuilder("§7Seen by: §f");
                    boolean first = true;
                    for (UUID id : rev) {
                        Player rp = Bukkit.getPlayer(id);
                        if (rp == null) continue;
                        if (!first) sb.append("§7, §f");
                        sb.append(rp.getName());
                        first = false;
                    }
                    p.sendMessage(sb.toString());
                }
                break;
            }
            default:
                sendHelp(p);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§5§lGhost §7— reveal yourself to the living:");
        p.sendMessage("§7/ghost show <player> §8— reveal to a player");
        p.sendMessage("§7/ghost hide <player> §8— hide from a player");
        p.sendMessage("§7/ghost showall §8| §7hideall §8— everyone");
        p.sendMessage("§7/ghost list §8— who can see you");
        p.sendMessage("§7/ghost haunt <player> §8— whisper to them in voice chat (unheard by others)");
        p.sendMessage("§7/ghost unhaunt §8— stop the voice whisper");
        p.sendMessage("§7/ghost setfree §8— stop haunting, become a spectator");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("show"); out.add("hide"); out.add("showall"); out.add("hideall");
            out.add("list"); out.add("haunt"); out.add("unhaunt"); out.add("setfree");
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("haunt")) {
                // haunt takes a player, or the two bulk/read verbs
                out.add("all");
                out.add("list");
                for (Player pl : Bukkit.getOnlinePlayers()) if (!pl.equals(sender)) out.add(pl.getName());
            } else if (sub.equals("unhaunt") || sub.equals("stop")) {
                // only suggest people this ghost is actually haunting
                if (sender instanceof Player p) {
                    for (java.util.UUID id : ghost.getHauntTargets(p.getUniqueId())) {
                        Player hp = Bukkit.getPlayer(id);
                        if (hp != null) out.add(hp.getName());
                    }
                }
            } else if (sub.equals("show") || sub.equals("hide") || sub.equals("setfree")) {
                for (Player pl : Bukkit.getOnlinePlayers()) out.add(pl.getName());
            }
        }

        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(o -> !o.toLowerCase().startsWith(prefix));
        return out;
    }
}
