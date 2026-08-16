package pow.crimson2.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.SkinShuffleManager;

import java.util.List;

/**
 * /skin — manage per-stage skin registrations for the vampire tier system.
 *
 * Subcommands (players):
 *   /skin register <stage>   — save current skin as this stage's skin
 *   /skin apply   <stage>    — manually apply a registered skin
 *   /skin clear   <stage>    — remove a registered skin
 *   /skin list               — show which stages have skins registered
 *   /skin tierdown <on|off>  — allow or suppress automatic skins when losing a tier
 *
 * Stage aliases accepted: human/0  stage1/1  stage2/2  stage3/3
 *
 * Admin:
 *   /skin reload             — re-fetch stage skins from disk (after config changes)
 */
public class SkinCommand implements CommandExecutor, TabCompleter {

    private static final List<String> STAGES =
            List.of("human", "stage1", "stage2", "stage3", "cured", "ghost");
    private static final List<String> SUB_PLAYER = List.of("register", "apply", "clear", "list", "tierdown");
    private static final List<String> SUB_ADMIN  = List.of("register", "apply", "clear", "list", "tierdown", "reload");

    private final VampireSMPPlugin plugin;

    public SkinCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        SkinShuffleManager mgr = plugin.getSkinShuffleManager();
        if (mgr == null) {
            player.sendMessage("§cSkin system is not initialised.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {

            // ── /skin register <stage> ──────────────────────────────────────
            case "register" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /skin register <human|stage1|stage2|stage3|cured|ghost>");
                    return true;
                }
                String stage = SkinShuffleManager.normaliseStage(args[1]);
                if (stage == null) {
                    player.sendMessage("§cUnknown stage '§e" + args[1] + "§c'. "
                            + "Use: human, stage1, stage2, stage3, cured, ghost");
                    return true;
                }
                mgr.registerCurrentSkin(player, stage);
            }

            // ── /skin apply <stage> ─────────────────────────────────────────
            case "apply" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /skin apply <human|stage1|stage2|stage3|cured|ghost>");
                    return true;
                }
                String stage = SkinShuffleManager.normaliseStage(args[1]);
                if (stage == null) {
                    player.sendMessage("§cUnknown stage '§e" + args[1] + "§c'.");
                    return true;
                }
                mgr.applyRegisteredSkin(player, stage);
                player.sendMessage("§7Applying §e" + SkinShuffleManager.displayName(stage)
                        + " §7skin...");
            }

            // ── /skin clear <stage> ─────────────────────────────────────────
            case "clear" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /skin clear <human|stage1|stage2|stage3|cured|ghost>");
                    return true;
                }
                String stage = SkinShuffleManager.normaliseStage(args[1]);
                if (stage == null) {
                    player.sendMessage("§cUnknown stage '§e" + args[1] + "§c'.");
                    return true;
                }
                mgr.clearRegisteredSkin(player, stage);
            }

            // ── /skin list ──────────────────────────────────────────────────
            case "list" -> player.sendMessage(mgr.getRegistrationStatus(player));

            // ── /skin tierdown <on|off> ────────────────────────────────────
            case "tierdown" -> {
                if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
                    player.sendMessage("§cUsage: /skin tierdown <on|off>");
                    player.sendMessage("§7Currently: " + (mgr.isTierDownEnabled(player) ? "§aon" : "§coff"));
                    return true;
                }
                boolean enabled = args[1].equalsIgnoreCase("on");
                mgr.setTierDownEnabled(player, enabled);
                player.sendMessage(enabled
                        ? "§a[SkinShuffle] Your skin will now change when you tier down."
                        : "§e[SkinShuffle] Tier-up skins stay automatic; tier-down skins will keep your current look.");
            }

            // ── /skin reload (admin) ────────────────────────────────────────
            case "reload" -> {
                if (!player.hasPermission("vampiresmp.admin")) {
                    player.sendMessage("§cYou don't have permission to do that.");
                    return true;
                }
                // Reload skin data for all online players
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    mgr.clearCache(online);
                    mgr.loadSkins(online);
                }
                player.sendMessage("§a[SkinShuffle] Reloaded skin data for "
                        + plugin.getServer().getOnlinePlayers().size() + " online player(s).");
            }

            default -> sendHelp(player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("vampiresmp.admin");
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return (isAdmin ? SUB_ADMIN : SUB_PLAYER).stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("register") || sub.equals("apply") || sub.equals("clear")) {
                String partial = args[1].toLowerCase();
                return STAGES.stream().filter(s -> s.startsWith(partial)).toList();
            }
            if (sub.equals("tierdown")) {
                String partial = args[1].toLowerCase();
                return List.of("on", "off").stream().filter(s -> s.startsWith(partial)).toList();
            }
        }
        return List.of();
    }

    private static void sendHelp(Player player) {
        player.sendMessage("§6§l--- Skin Commands ---");
        player.sendMessage("§e/skin register <stage> §7— save your current skin for a stage");
        player.sendMessage("§e/skin apply <stage>    §7— manually wear a registered skin");
        player.sendMessage("§e/skin clear <stage>    §7— remove a registered skin");
        player.sendMessage("§e/skin list             §7— show which stages are set up");
        player.sendMessage("§e/skin tierdown <on|off> §7— choose whether losing a tier changes your skin");
        player.sendMessage("§7Stages: §fhuman §8| §fstage1 §8| §fstage2 §8| §fstage3 §8| §fcured §8| §fghost");
        player.sendMessage("§7Tip: switch skin in the SkinShuffle menu first, then run /skin register.");
    }
}
