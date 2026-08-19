package pow.crimson2.thralls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles the /thrall command and all its sub-commands.
 * Registered via plugin.yml.
 */
@SuppressWarnings("UnstableApiUsage")
public class ThrallCommand implements CommandExecutor, TabCompleter {

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;
    private final BloodBottleManager bloodBottleManager;
    private final ThrallConfig thrallConfig;

    public ThrallCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.thrallManager = plugin.getThrallManager();
        this.bloodBottleManager = thrallManager.getBloodBottleManager();
        this.thrallConfig = thrallManager.getThrallConfig();
    }

    // =========================================================================
    // Main execute
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "bondinfo":    handleBondInfo(player, args);     break;
            case "findmaster":  handleFindMaster(player);         break;
            case "findthrall":  handleFindThrall(player, args);   break;
            case "list":        handleList(player);               break;
            case "command":     handleCommand(player, args);      break;
            case "punish":      handlePunish(player, args);       break;
            case "bar":         handleBar(player, args);          break;
            case "stay":        handleStay(player, args);         break;
            case "check":       handleCheck(player, args);        break;
            case "thrallcount": handleThrallCount(player);        break;
            case "force":       handleForce(player, args);        break;
            case "profile":     handleProfile(player, args);      break;
            case "preferences": handlePreferences(player, args);  break;
            case "checkconfig": handleCheckConfig(player);        break;
            case "admin":       handleAdmin(player, args);        break;
            default:            sendHelp(player);                 break;
        }
        return true;
    }

    // =========================================================================
    // bondinfo
    // =========================================================================

    private void handleBondInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§7Usage: /thrall bondinfo thrall <player>  §8|  /thrall bondinfo master");
            return;
        }
        String sub2 = args[1].toLowerCase();

        if ("thrall".equals(sub2)) {
            if (args.length < 3) { ab(player, "§7Usage: /thrall bondinfo thrall <player>"); return; }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { ab(player, "§7Player not found."); return; }

            ThrallBondData d = thrallManager.getBond(target.getUniqueId());
            if (d == null || !d.hasBond()) { ab(player, "§7That player has no bond."); return; }

            // Permission: must be their master OR admin
            boolean isMaster = d.isBoundTo(player.getUniqueId());
            if (!isMaster && !player.hasPermission("thralls.admin")) { ab(player, "§7That thrall is not yours."); return; }

            player.sendMessage("§8--- §4Bond Info: §f" + target.getName() + "§8 ---");
            printBondInfo(player, d);

        } else if ("master".equals(sub2)) {
            ThrallBondData d = thrallManager.getBond(player.getUniqueId());
            if (d == null || !d.hasBond()) { ab(player, "§8You carry no bond."); return; }
            player.sendMessage("§8--- §4Your Masters§8 ---");
            printBondInfo(player, d);
        } else {
            player.sendMessage("§7Usage: /thrall bondinfo thrall <player>  §8|  /thrall bondinfo master");
        }
    }

    private void printBondInfo(Player viewer, ThrallBondData d) {
        if ("bottles".equals(thrallConfig.getThrallStageMode())) {
            int max = thrallConfig.getThrallBottlesStage3();
            int b1 = d.getBottlesM1(); int s1 = d.getStageM1();
            String m1name = nameOf(d.getPrimaryMaster());
            viewer.sendMessage("§4★ Primary: §f" + m1name + " §8(§f" + b1 + "§8/§f" + max + " bottles — §f" + ThrallManager.stageName(s1) + "§8)");
            if (d.getSecondaryMaster() != null) {
                int b2 = d.getBottlesM2(); int s2 = d.getStageM2();
                String m2name = nameOf(d.getSecondaryMaster());
                viewer.sendMessage("§5⚬ Secondary: §f" + m2name + " §8(§f" + b2 + "§8/§f" + max + " bottles — §f" + ThrallManager.stageName(s2) + "§8)");
            }
        } else {
            String m1name = nameOf(d.getPrimaryMaster());
            viewer.sendMessage("§4★ Primary: §f" + m1name + " §8— §f" + ThrallManager.stageName(d.getStageM1()));
            if (d.getSecondaryMaster() != null) {
                String m2name = nameOf(d.getSecondaryMaster());
                viewer.sendMessage("§5⚬ Secondary: §f" + m2name + " §8— §f" + ThrallManager.stageName(d.getStageM2()));
            }
            viewer.sendMessage("§8Next dose in: §f" + ThrallManager.formatTime(d.getDoseLeft()));
        }
        viewer.sendMessage("§8Withdrawal in: §f" + ThrallManager.formatTime(d.getWithdrawLeft()));
    }

    // =========================================================================
    // findmaster
    // =========================================================================

    private void handleFindMaster(Player player) {
        if (!thrallConfig.isThrallFindmasterEnabled()) { ab(player, "§7That feature is disabled."); return; }
        ThrallBondData d = thrallManager.getBond(player.getUniqueId());
        if (d == null || !d.hasBond()) { ab(player, "§8You carry no bond."); return; }

        UUID masterId = d.getPrimaryMaster();
        Player master = Bukkit.getPlayer(masterId);
        if (master == null) { ab(player, "§8[Bond] §7Your master is §coffline§7."); return; }
        if (!player.getWorld().equals(master.getWorld())) { ab(player, "§8[Bond] §7Your master is in §canother world§7."); return; }

        // Live directional-arrow indicator (same arrow the vampire tracker uses).
        plugin.getVampireTrackingManager().showDirectionalArrow(
            player, master, "§8[Bond] §7" + master.getName(), 8);
    }

    // =========================================================================
    // findthrall
    // =========================================================================

    private void handleFindThrall(Player player, String[] args) {
        if (!thrallConfig.isThrallLocateEnabled()) { ab(player, "§7That feature is disabled."); return; }
        if (!thrallManager.isVampire(player)) { ab(player, "§7Only vampires can track thralls."); return; }
        if (args.length < 2) { ab(player, "§7Usage: /thrall findthrall <player>"); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { ab(player, "§7Player not found."); return; }

        ThrallBondData d = thrallManager.getBond(target.getUniqueId());
        if (d == null || !d.isBoundTo(player.getUniqueId())) { ab(player, "§7That is not your thrall."); return; }
        if (!player.getWorld().equals(target.getWorld())) { ab(player, "§8[Thrall] §c" + target.getName() + " is in another world."); return; }

        // Live directional-arrow indicator (same arrow the vampire tracker uses).
        plugin.getVampireTrackingManager().showDirectionalArrow(
            player, target, "§8[Thrall] §7" + target.getName(), 8);
    }

    // =========================================================================
    // list
    // =========================================================================

    private void handleList(Player player) {
        if (!thrallManager.isVampire(player)) { ab(player, "§7Only vampires can list thralls."); return; }
        UUID myId = player.getUniqueId();
        player.sendMessage("§8--- §4Your Thralls§8 ---");
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ThrallBondData d = thrallManager.getBond(p.getUniqueId());
            if (d != null && d.isBoundTo(myId)) {
                String stageName = ThrallManager.stageName(d.getStageFor(myId));
                String marker = d.isPrimaryMaster(myId) ? " §4★" : " §5⚬ secondary";
                player.sendMessage("§8  §f" + p.getName() + " §7— §f" + stageName + marker);
                count++;
            }
        }
        if (count == 0) player.sendMessage("§8  §7None.");
        else player.sendMessage("§8Total: §f" + count + " §7/ §f" + thrallConfig.getThrallMaxThralls());
    }

    // =========================================================================
    // command  (send roleplay instruction)
    // =========================================================================

    private void handleCommand(Player player, String[] args) {
        if (!thrallConfig.isThrallCommandEnabled()) { ab(player, "§7That feature is disabled."); return; }
        if (!thrallManager.isVampire(player)) { ab(player, "§7Only vampires can issue commands."); return; }
        if (thrallManager.isOnCooldown("command", player)) { ab(player, "§7Your will needs a moment to recover."); return; }
        if (args.length < 2) { ab(player, "§7Usage: /thrall command <player|all> <message...>"); return; }
        if (args.length < 3) { ab(player, "§7Usage: /thrall command <player|all> <message...>"); return; }

        String target = args[1];
        String msg = joinArgs(args, 2);
        UUID myId = player.getUniqueId();

        if ("all".equalsIgnoreCase(target)) {
            int sent = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                ThrallBondData d = thrallManager.getBond(p.getUniqueId());
                if (d == null || !d.isBoundTo(myId)) continue;
                if (thrallManager.rollForMaster(p, myId)) {
                    d.setCmdText(msg);
                    d.setCmdLeft(thrallConfig.getThrallCmdExpirySeconds());
                    p.sendMessage("§8[Master's Command] §c" + msg);
                    sent++;
                }
            }
            thrallManager.setCooldown("command", player);
            ab(player, "§8Commanded §f" + sent + "§8 thrall(s).");
        } else {
            Player thr = Bukkit.getPlayerExact(target);
            if (thr == null) { ab(player, "§7Player not found."); return; }
            ThrallBondData d = thrallManager.getBond(thr.getUniqueId());
            if (d == null || !d.isBoundTo(myId)) { ab(player, "§7That is not your thrall."); return; }
            if (!thrallManager.rollForMaster(thr, myId)) { ab(player, "§8The command slips through the bond."); return; }
            d.setCmdText(msg);
            d.setCmdLeft(thrallConfig.getThrallCmdExpirySeconds());
            thr.sendMessage("§8[Master's Command] §c" + msg);
            thrallManager.setCooldown("command", player);
            ab(player, "§8Command delivered to §f" + thr.getName() + "§8.");
        }
    }

    // =========================================================================
    // punish
    // =========================================================================

    private void handlePunish(Player player, String[] args) {
        if (!thrallConfig.isThrallPunishEnabled()) { ab(player, "§7That feature is disabled."); return; }
        if (!thrallManager.isVampire(player)) { ab(player, "§7Only vampires can punish."); return; }
        if (thrallManager.isOnCooldown("punish", player)) { ab(player, "§7Your bond needs to recover from that."); return; }
        if (args.length < 3) {
            player.sendMessage("§8Available effects: §fnausea, blindness, weakness, slowness, hunger, poison");
            return;
        }
        String effect = args[1].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { ab(player, "§7Player not found."); return; }
        ThrallBondData d = thrallManager.getBond(target.getUniqueId());
        if (d == null || !d.isBoundTo(player.getUniqueId())) { ab(player, "§7That is not your thrall."); return; }
        if (player.getLocation().distance(target.getLocation()) > thrallConfig.getThrallPunishRange()) {
            ab(player, "§7They are too far to reach."); return;
        }
        if (!thrallManager.rollForMaster(target, player.getUniqueId())) {
            ab(player, "§8The bond doesn't hold well enough."); return;
        }
        thrallManager.setCooldown("punish", player);
        thrallManager.punishEffect(target, effect, thrallConfig.getThrallPunishDurationSeconds());
    }

    // =========================================================================
    // bar
    // =========================================================================

    private void handleBar(Player player, String[] args) {
        if (!thrallConfig.isThrallBossBarEnabled()) { ab(player, "§7That feature is disabled."); return; }
        ThrallBondData d = thrallManager.getBond(player.getUniqueId());
        if (d == null || !d.hasBond()) { ab(player, "§8You carry no bond."); return; }
        if (args.length < 2) { player.sendMessage("§7Usage: /thrall bar <Dose|Withdraw|Unthrallment|on|off>"); return; }

        String mode = args[1].toLowerCase();
        switch (mode) {
            case "on":          d.setBarMode("dose"); ab(player, "§aBar enabled."); break;
            case "off":         d.setBarMode("off"); thrallManager.hideBossBar(player); ab(player, "§cBar hidden."); break;
            case "dose":
                if (!"timer".equals(thrallConfig.getThrallStageMode())) {
                    ab(player, "§7Dose bar is for bottle-progress in bottles mode — showing bottle count.");
                }
                d.setBarMode("dose"); ab(player, "§aBar set to dose tracker."); break;
            case "withdraw":    d.setBarMode("withdraw"); ab(player, "§aBar set to withdrawal timer."); break;
            case "unthrallment":d.setBarMode("unthral");  ab(player, "§aBar set to bond stage tracker."); break;
            default:            player.sendMessage("§7Valid options: Dose, Withdraw, Unthrallment, on, off"); break;
        }
    }

    // =========================================================================
    // stay
    // =========================================================================

    private void handleStay(Player player, String[] args) {
        if (!thrallConfig.isThrallStayEnabled()) { ab(player, "§7That feature is disabled."); return; }
        if (!thrallManager.isVampire(player)) return;
        if (thrallManager.isOnCooldown("stay", player)) { ab(player, "§7Your will is still cooling."); return; }
        if (args.length < 3) { ab(player, "§7Usage: /thrall stay <seconds> <player>"); return; }

        int sec;
        try { sec = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sec = 10; }
        sec = Math.max(1, Math.min(sec, thrallConfig.getThrallStayMaxSeconds()));

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { ab(player, "§7Player not found."); return; }
        ThrallBondData d = thrallManager.getBond(target.getUniqueId());
        if (d == null || !d.isBoundTo(player.getUniqueId())) { ab(player, "§7That is not your thrall."); return; }
        if (!thrallManager.rollForMaster(target, player.getUniqueId())) { ab(player, "§8The bond slips."); return; }

        d.setStayTime(sec);
        d.setStayLoc(target.getLocation().clone());
        thrallManager.setCooldown("stay", player);
        ab(target, "§4Stay.");
    }

    // =========================================================================
    // check
    // =========================================================================

    private void handleCheck(Player player, String[] args) {
        if (!thrallManager.isVampire(player)) { ab(player, "§7Only vampires can check thralls."); return; }
        if (args.length < 2) { ab(player, "§7Usage: /thrall check <player>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { ab(player, "§7Player not found."); return; }
        ThrallBondData d = thrallManager.getBond(target.getUniqueId());
        if (d == null || !d.isBoundTo(player.getUniqueId())) { ab(player, "§7That is not your thrall."); return; }

        player.sendMessage("§8--- §4Thrall Check: §f" + target.getName() + "§8 ---");
        player.sendMessage("§8Stage: §f" + ThrallManager.stageName(d.getEffectiveStage()));
        if ("bottles".equals(thrallConfig.getThrallStageMode())) {
            int max = thrallConfig.getThrallBottlesStage3();
            player.sendMessage("§8Bottles: §f" + d.getTotalBottles() + " / " + max);
        } else {
            player.sendMessage("§8Next dose in: §f" + ThrallManager.formatTime(d.getDoseLeft()));
        }
        if (thrallConfig.isThrallWithdrawalEnabled()) {
            player.sendMessage("§8Withdrawal in: §f" + ThrallManager.formatTime(d.getWithdrawLeft()));
        }
        if (d.getEffectiveStage() >= 3 && d.isTagOfferPending()) {
            player.sendMessage("§8[Stage 3] §7Thrall has not yet made their threshold choice.");
        }
    }

    // =========================================================================
    // thrallcount
    // =========================================================================

    private void handleThrallCount(Player player) {
        if (!thrallManager.isVampire(player) && !player.hasPermission("thralls.admin")) {
            ab(player, "§7Only vampires and admins can check thrall counts."); return;
        }
        int total = thrallManager.countAllThralls();
        int cap   = thrallConfig.getThrallGlobalCap();
        player.sendMessage("§8--- §4Thrall Count§8 ---");
        if (cap > 0) player.sendMessage("§8Global: §f" + total + " §7/ §f" + cap);
        else         player.sendMessage("§8Global: §f" + total + " §7(no global cap)");
        if (thrallManager.isVampire(player)) {
            int mine = thrallManager.countThralls(player.getUniqueId());
            player.sendMessage("§8Yours: §f" + mine + " §7/ §f" + thrallConfig.getThrallMaxThralls());
        }
    }

    // =========================================================================
    // force
    // =========================================================================

    private void handleForce(Player player, String[] args) {
        if (!thrallConfig.isThrallForceEnabled()) { ab(player, "§7That feature is disabled."); return; }
        if (!thrallManager.isTier2Vamp(player)) { ab(player, "§7Only stage 2 or 3 vampires can force a bond."); return; }
        if (thrallManager.isOnCooldown("force", player)) { ab(player, "§7You need more time before forcing another."); return; }
        if (args.length < 2) { ab(player, "§7Usage: /thrall force <player>"); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { ab(player, "§7Player not found."); return; }
        if (target.equals(player)) return;

        if (player.getLocation().distance(target.getLocation()) > thrallConfig.getThrallForceRange()) {
            ab(player, "§7They need to be within §f" + thrallConfig.getThrallForceRange() + "§7 blocks."); return;
        }

        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (!bloodBottleManager.isBloodBottle(held)) {
            ab(player, "§7Hold §4Vampire Blood§7 in your main hand."); return;
        }

        ThrallBondData td = thrallManager.getBond(target.getUniqueId());
        if (td != null && td.getOfferLeft() > 0) {
            ab(player, "§7They already have a pending offer."); return;
        }

        UUID ownerId = bloodBottleManager.getBloodOwner(held);
        if (ownerId == null) { ab(player, "§7That blood feels wrong."); return; }

        ThrallBondData d = thrallManager.getBonds().computeIfAbsent(target.getUniqueId(), k -> new ThrallBondData());
        d.setUsername(target.getName());
        d.setOfferOwner(ownerId);
        d.setOfferVamp(player.getUniqueId());
        d.setOfferLeft(thrallConfig.getThrallForceOfferSeconds());
        d.setOfferLock(false);

        thrallManager.setCooldown("force", player);
        thrallManager.openForceOfferGui(target);
        ab(target, "§4A blood offer presses into your thoughts...");
        ab(player, "§7Offer sent.");
    }

    // =========================================================================
    // profile
    // =========================================================================

    private void handleProfile(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (args.length < 2) {
            sendProfileHelp(player); return;
        }
        String sub2 = args[1].toLowerCase();
        ThrallProfile profile = thrallManager.getOrCreateProfile(uuid);

        switch (sub2) {
            case "create": handleProfileCreate(player, profile, uuid, args); break;
            case "select": handleProfileSelect(player, profile, uuid, args); break;
            case "list":   handleProfileList(player, profile, uuid);          break;
            case "delete": handleProfileDelete(player, profile, uuid, args); break;
            default:       sendProfileHelp(player); break;
        }
    }

    private void handleProfileCreate(Player player, ThrallProfile profile, UUID uuid, String[] args) {
        int max = thrallConfig.getThrallMaxProfiles();
        if (profile.getCount() >= max) {
            ab(player, "§7You have reached the profile limit (§f" + max + "§7).");
            return;
        }
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Create Character Profile", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Enter a character name and a blood taste description.\n"
                    + "Blood taste is revealed to vampires when they feed."))))
                .inputs(List.of(
                    DialogInput.text("char_name", Component.text("Character Name"))
                        .initial("").maxLength(32).build(),
                    DialogInput.text("char_taste", Component.text("Blood Taste"))
                        .initial("Warm. Metallic. Familiar.").maxLength(128).build()
                ))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(Component.text("Create", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((view, aud) -> {
                        String newName  = view.getText("char_name");
                        String newTaste = view.getText("char_taste");
                        sync(() -> {
                            if (newName == null || newName.trim().isEmpty()) {
                                player.sendMessage(Component.text("Name cannot be empty.", NamedTextColor.RED));
                                handleProfileCreate(player, profile, uuid, args);
                                return;
                            }
                            String taste = (newTaste == null || newTaste.trim().isEmpty()
                                    || "default".equalsIgnoreCase(newTaste.trim()))
                                ? "§8Warm. Metallic. Familiar."
                                : newTaste.trim();
                            int idx = profile.addProfile(newName.trim(), taste);
                            profile.setActiveIndex(idx);
                            thrallManager.saveProfiles();
                            ab(player, "§8Profile §f" + newName.trim() + "§8 created — set your thrall preference.");
                            openThrallPrefDialog(player, profile, idx);
                        });
                    }, ClickCallback.Options.builder().build())),
                ActionButton.create(Component.text("Cancel", NamedTextColor.GRAY), null, 100,
                    DialogAction.customClick((view, aud) -> {}, ClickCallback.Options.builder().build()))
            )).build())
        );
        player.showDialog(d);
    }

    /** Shown after profile creation — lets the player set their thrall preference before dismissing. */
    private void openThrallPrefDialog(Player player, ThrallProfile profile, int charIdx) {
        String cur = profile.getThrallPref(charIdx);

        String[] keys   = {"open",  "owner",  "both",  "no",  "try"};
        String[] labels = {
            "Open to Being a Thrall",
            "Open to Owning a Thrall",
            "Open to Both",
            "Please Don't",
            "You Can Try — But May Not Succeed"
        };

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            String lbl = labels[i] + (key.equals(cur) ? " ✓" : "");
            buttons.add(ActionButton.create(
                Component.text(lbl, key.equals(cur) ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                null, 220,
                DialogAction.customClick((view, aud) -> sync(() -> {
                    profile.setThrallPref(charIdx, key);
                    thrallManager.saveProfiles();
                    ab(player, "§8Preference set: §f" + key + "§8.");
                }), ClickCallback.Options.builder().build())));
        }
        buttons.add(ActionButton.create(
            Component.text("Skip for Now", NamedTextColor.GRAY), null, 150,
            DialogAction.customClick((view, aud) -> {}, ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Thrall Preferences", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "Set your preference for the thrall blood-bond system.\n"
                    + "This lets other players know if you are open to thrall RP."))))
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        player.showDialog(d);
    }

    private void handleProfileSelect(Player player, ThrallProfile profile, UUID uuid, String[] args) {
        int count = profile.getCount();
        if (count == 0) {
            ab(player, "§7You have no profiles. Use /thrall profile create.");
            return;
        }
        int activeIdx = profile.getActiveIndex();
        List<ActionButton> buttons = new ArrayList<>();

        // Option 0: no profile — use real name
        String noLabel = "No Profile — Use Real Name" + (activeIdx == 0 ? " ✓" : "");
        buttons.add(ActionButton.create(
            Component.text(noLabel, activeIdx == 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY),
            Component.text("Show your actual username in the thrall system."),
            200,
            DialogAction.customClick((view, aud) -> sync(() -> {
                profile.setActiveIndex(0);
                thrallManager.saveProfiles();
                ab(player, "§8Profile cleared. Using real name.");
            }), ClickCallback.Options.builder().build())));

        // One button per profile (max 5 — dialog limit)
        for (int i = 1; i <= Math.min(count, 5); i++) {
            final int idx = i;
            ThrallProfile.ProfileEntry e = profile.getEntry(i);
            String label     = e.getName() + (activeIdx == i ? " ✓" : "");
            String tasteText = e.getTaste() != null ? "Blood taste: " + e.getTaste() : "(no taste set)";
            buttons.add(ActionButton.create(
                Component.text(label, activeIdx == i ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                Component.text(tasteText),
                200,
                DialogAction.customClick((view, aud) -> sync(() -> {
                    profile.setActiveIndex(idx);
                    thrallManager.saveProfiles();
                    ThrallProfile.ProfileEntry sel = profile.getEntry(idx);
                    ab(player, "§8Active profile: §f" + sel.getName() + "§8.");
                }), ClickCallback.Options.builder().build())));
        }

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Select Profile", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(
                    Component.text("Choose which character you are playing as this session."))))
                .build())
            .type(DialogType.multiAction(buttons).build())
        );
        player.showDialog(d);
    }

    private void handleProfileList(Player player, ThrallProfile profile, UUID uuid) {
        int count = profile.getCount();
        if (count == 0) { ab(player, "§7No profiles created yet."); return; }
        player.sendMessage("§8--- §4Your Profiles§8 ---");
        int active = profile.getActiveIndex();
        for (int i = 1; i <= count; i++) {
            ThrallProfile.ProfileEntry e = profile.getEntry(i);
            String mark = active == i ? " §a✔" : "";
            player.sendMessage("§8" + i + ". §f" + e.getName() + " §7— " + e.getTaste() + mark);
        }
    }

    private void handleProfileDelete(Player player, ThrallProfile profile, UUID uuid, String[] args) {
        if (args.length < 3) { ab(player, "§7Usage: /thrall profile delete <number>"); return; }
        int idx;
        try { idx = Integer.parseInt(args[2]); }
        catch (NumberFormatException e) { ab(player, "§7Invalid number."); return; }
        int count = profile.getCount();
        if (idx < 1 || idx > count) { ab(player, "§7Profile §f" + idx + "§7 does not exist."); return; }
        ThrallProfile.ProfileEntry e = profile.getEntry(idx);
        String delName = e.getName();
        int active = profile.getActiveIndex();
        profile.deleteProfile(idx);
        // Adjust active index
        if (active == idx) {
            profile.setActiveIndex(0);
            ab(player, "§8Profile §f" + delName + "§8 deleted. Active profile cleared.");
        } else if (active > idx) {
            profile.setActiveIndex(active - 1);
            ab(player, "§8Profile §f" + delName + "§8 deleted.");
        } else {
            ab(player, "§8Profile §f" + delName + "§8 deleted.");
        }
        thrallManager.saveProfiles();
    }

    private void sendProfileHelp(Player player) {
        player.sendMessage("§7/thrall profile create <name> <taste> §8— Create a new character profile");
        player.sendMessage("§7/thrall profile select §8— Choose your active profile");
        player.sendMessage("§7/thrall profile list   §8— List all profiles");
        player.sendMessage("§7/thrall profile delete <n> §8— Delete a profile");
    }

    // =========================================================================
    // preferences
    // =========================================================================

    private void handlePreferences(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§8--- §4Thrall Preferences§8 ---");
            player.sendMessage("§7/thrall preferences list       §8— All players sorted by preference");
            player.sendMessage("§7/thrall preferences <player>   §8— View one player's preference");
            return;
        }
        String sub2 = args[1].toLowerCase();
        if ("list".equals(sub2)) {
            handlePreferencesList(player);
        } else {
            handlePreferencesLookup(player, args[1]);
        }
    }

    private void handlePreferencesList(Player player) {
        player.sendMessage("§8--- §4Thrall Preferences§8 ---");
        List<String> open = new ArrayList<>(), owner = new ArrayList<>(), both = new ArrayList<>(),
                     no   = new ArrayList<>(), tryList = new ArrayList<>();
        for (java.util.Map.Entry<UUID, ThrallProfile> e : thrallManager.getProfiles().entrySet()) {
            ThrallProfile prof = e.getValue();
            int idx = prof.getActiveIndex();
            String pref = prof.getThrallPref(idx);
            if (pref == null) continue;
            String uname = getUsername(e.getKey());
            String charName = prof.getActiveName();
            String disp = charName != null ? uname + " (" + charName + ")" : uname;
            switch (pref) {
                case "open":  open.add(disp); break;
                case "owner": owner.add(disp); break;
                case "both":  both.add(disp); break;
                case "no":    no.add(disp);   break;
                case "try":   tryList.add(disp); break;
            }
        }
        if (open.isEmpty() && owner.isEmpty() && both.isEmpty() && no.isEmpty() && tryList.isEmpty()) {
            player.sendMessage("§7No preferences have been set yet."); return;
        }
        if (!both.isEmpty())    { player.sendMessage("§aOpen to Both:");                both.forEach(n -> player.sendMessage("§8  §f" + n)); }
        if (!open.isEmpty())    { player.sendMessage("§bOpen to Being a Thrall:");       open.forEach(n -> player.sendMessage("§8  §f" + n)); }
        if (!owner.isEmpty())   { player.sendMessage("§eOpen to Owning a Thrall:");      owner.forEach(n -> player.sendMessage("§8  §f" + n)); }
        if (!tryList.isEmpty()) { player.sendMessage("§7You Can Try — But May Not Succeed:"); tryList.forEach(n -> player.sendMessage("§8  §f" + n)); }
        if (!no.isEmpty())      { player.sendMessage("§cPlease Don't:");                 no.forEach(n -> player.sendMessage("§8  §f" + n)); }
    }

    private void handlePreferencesLookup(Player player, String name) {
        Player target = Bukkit.getPlayerExact(name);
        UUID tuuid = null;
        String tname = null;
        if (target != null) { tuuid = target.getUniqueId(); tname = target.getName(); }
        else {
            // Search profiles by username
            for (java.util.Map.Entry<UUID, ThrallBondData> e : thrallManager.getBonds().entrySet()) {
                if (name.equalsIgnoreCase(e.getValue().getUsername())) {
                    tuuid = e.getKey(); tname = e.getValue().getUsername(); break;
                }
            }
        }
        if (tuuid == null) { ab(player, "§7Player not found: §f" + name); return; }
        ThrallProfile prof = thrallManager.getProfile(tuuid);
        String charName = prof != null ? prof.getActiveName() : null;
        String disp = charName != null ? tname + " (" + charName + ")" : tname;
        player.sendMessage("§8--- §4Thrall Preference: §f" + disp + "§8 ---");
        if (prof != null) {
            String pref = prof.getThrallPref(prof.getActiveIndex());
            player.sendMessage("§7Preference: §f" + (pref != null ? thrallPrefLabel(pref) : "§8Not set"));
        } else {
            player.sendMessage("§7Preference: §8Not set");
        }
    }

    private String thrallPrefLabel(String pref) {
        switch (pref) {
            case "open":  return "Open to Being a Thrall";
            case "owner": return "Open to Owning a Thrall";
            case "both":  return "Open to Both";
            case "no":    return "Please Don't";
            case "try":   return "You Can Try — But May Not Succeed";
            default:      return pref;
        }
    }

    // =========================================================================
    // checkconfig  (admin)
    // =========================================================================

    private void handleCheckConfig(Player player) {
        if (!player.hasPermission("thralls.admin")) { ab(player, "§cNo permission."); return; }
        player.sendMessage("§8--- §4Thrall Config§8 ---");
        player.sendMessage("§8BloodDraw: §f" + thrallConfig.isThrallBloodDrawEnabled());
        player.sendMessage("§8Withdrawal: §f" + thrallConfig.isThrallWithdrawalEnabled());
        player.sendMessage("§8HolyWater: §f" + thrallConfig.isThrallHolyWaterEnabled());
        player.sendMessage("§8AutoUnthrall: §f" + thrallConfig.isThrallAutoUnthrallEnabled());
        player.sendMessage("§8BossBar: §f" + thrallConfig.isThrallBossBarEnabled());
        player.sendMessage("§8SepAnxiety: §f" + thrallConfig.isThrallSepAnxietyEnabled());
        player.sendMessage("§8DualMaster: §f" + thrallConfig.isThrallDualMasterEnabled());
        player.sendMessage("§8MaxThralls: §f" + thrallConfig.getThrallMaxThralls()
            + "  §8GlobalCap: §f" + thrallConfig.getThrallGlobalCap());
        player.sendMessage("§8StageMode: §f" + thrallConfig.getThrallStageMode()
            + "  §8Bottles: §f" + thrallConfig.getThrallBottlesStage1()
            + "§8/§f" + thrallConfig.getThrallBottlesStage2()
            + "§8/§f" + thrallConfig.getThrallBottlesStage3());
        player.sendMessage("§8WithdrawDeductMode: §f" + thrallConfig.getThrallWithdrawDeductMode()
            + "  §8Type: §f" + thrallConfig.getThrallWithdrawDeductType()
            + "  §8Amount: §f" + thrallConfig.getThrallWithdrawDeductAmount());
        player.sendMessage("§8BloodTaste: §f" + thrallConfig.isThrallBloodTasteEnabled()
            + "  §8Mode: §f" + thrallConfig.getThrallBloodTasteMode()
            + "  §8CD: §f" + thrallConfig.getThrallBloodTasteCooldownSeconds() + "s");
    }

    // =========================================================================
    // admin
    // =========================================================================

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("thralls.admin")) { ab(player, "§cNo permission."); return; }
        if (args.length < 2) {
            player.sendMessage("§7Admin: cooldownreset, reset, setowner, bondset, forcesave"); return;
        }
        String sub2 = args[1].toLowerCase();

        switch (sub2) {
            case "cooldownreset":
                if (args.length < 3) { ab(player, "§7Usage: /thrall admin cooldownreset <player|@a>"); return; }
                if ("@a".equals(args[2])) {
                    Bukkit.getOnlinePlayers().forEach(thrallManager::clearCooldowns);
                    ab(player, "§aAll cooldowns cleared.");
                } else {
                    Player t = Bukkit.getPlayerExact(args[2]);
                    if (t == null) { ab(player, "§7Player not found."); return; }
                    thrallManager.clearCooldowns(t);
                    ab(player, "§aCooldowns cleared for §f" + t.getName() + "§a.");
                }
                break;

            case "reset":
                if (args.length < 3) { ab(player, "§7Usage: /thrall admin reset <player|@a>"); return; }
                if ("@a".equals(args[2])) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        thrallManager.resetPlayer(p);
                        ab(p, "§7The bond has been severed.");
                    }
                    ab(player, "§cAll thrall data wiped.");
                } else {
                    Player t = Bukkit.getPlayerExact(args[2]);
                    if (t == null) { ab(player, "§7Player not found."); return; }
                    thrallManager.resetPlayer(t);
                    ab(t, "§7Your bond is gone.");
                    ab(player, "§aReset thrall data for §f" + t.getName() + "§a.");
                }
                break;

            case "setowner":
                if (args.length < 4) { ab(player, "§7Usage: /thrall admin setowner <thrall> <master> [stage]"); return; }
                Player thr  = Bukkit.getPlayerExact(args[2]);
                Player mstr = Bukkit.getPlayerExact(args[3]);
                if (thr == null)  { ab(player, "§7Thrall player not found."); return; }
                if (mstr == null) { ab(player, "§7Master player not found."); return; }
                int stg = 1;
                if (args.length >= 5) { try { stg = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) {} }
                ThrallBondData nd = thrallManager.getBonds().computeIfAbsent(thr.getUniqueId(), k -> new ThrallBondData());
                nd.setUsername(thr.getName());
                nd.setPrimaryMaster(mstr.getUniqueId());
                nd.setStageM1(stg);
                nd.setBottlesM1(thrallManager.getBloodBottleManager() != null ? stg * thrallConfig.getThrallBottlesStage1() : 0);
                thrallManager.resetWithdraw(nd);
                thrallManager.getDataManager().saveAll();
                ab(player, "§aOwner set: §f" + thr.getName() + "§a -> §f" + mstr.getName() + "§a stage §f" + stg + "§a.");
                ab(thr, "§8A bond forms. You feel it settle.");
                break;

            case "bondset":
                if (args.length < 4) { ab(player, "§7Usage: /thrall admin bondset <thrall> <stage>"); return; }
                Player bthr = Bukkit.getPlayerExact(args[2]);
                if (bthr == null) { ab(player, "§7Player not found."); return; }
                int bstg;
                try { bstg = Integer.parseInt(args[3]); } catch (NumberFormatException e) { ab(player, "§7Invalid stage number."); return; }
                ThrallBondData bd = thrallManager.getBond(bthr.getUniqueId());
                if (bd == null || !bd.hasBond()) { ab(player, "§7That player has no master to bind to."); return; }
                bd.setStageM1(bstg);
                thrallManager.getDataManager().saveAll();
                ab(player, "§aBond stage for §f" + bthr.getName() + "§a set to §f" + bstg + "§a.");
                break;

            case "forcesave":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ThrallBondData fsd = thrallManager.getBond(p.getUniqueId());
                    if (fsd != null) fsd.setUsername(p.getName());
                }
                thrallManager.getDataManager().saveAll();
                thrallManager.getDataManager().saveAllProfiles();
                ab(player, "§aSaved all thrall data and profiles.");
                break;

            default:
                player.sendMessage("§7Admin: cooldownreset, reset, setowner, bondset, forcesave");
                break;
        }
    }

    // =========================================================================
    // Help
    // =========================================================================

    private void sendHelp(Player player) {
        player.sendMessage("§8--- §4Thrall Commands§8 ---");
        player.sendMessage("§7/thrall bondinfo thrall <p> §8| §7/thrall bondinfo master");
        player.sendMessage("§7/thrall list §8| §7/thrall check <p> §8| §7/thrall thrallcount");
        if (thrallConfig.isThrallFindmasterEnabled())
            player.sendMessage("§7/thrall findmaster");
        if (thrallConfig.isThrallLocateEnabled())
            player.sendMessage("§7/thrall findthrall <p>");
        if (thrallConfig.isThrallCommandEnabled())
            player.sendMessage("§7/thrall command <p|all> <message...>");
        if (thrallConfig.isThrallPunishEnabled())
            player.sendMessage("§7/thrall punish <effect> <p>");
        if (thrallConfig.isThrallBossBarEnabled())
            player.sendMessage("§7/thrall bar <Dose|Withdraw|Unthrallment|on|off>");
        if (thrallConfig.isThrallStayEnabled())
            player.sendMessage("§7/thrall stay <secs> <p>");
        if (thrallConfig.isThrallForceEnabled())
            player.sendMessage("§7/thrall force <p>");
        player.sendMessage("§7/thrall profile create <name> <taste>");
        player.sendMessage("§7/thrall profile select | list | delete <n>");
        player.sendMessage("§7/thrall preferences list §8| §7/thrall preferences <player>");
        if (player.hasPermission("thralls.admin")) {
            player.sendMessage("§7/thrall checkconfig");
            player.sendMessage("§8Admin: /thrall admin <cooldownreset|reset|setowner|bondset|forcesave>");
        }
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("bondinfo","findmaster","findthrall","list","command",
                "punish","bar","stay","check","thrallcount","force","profile","preferences","checkconfig","admin"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "bondinfo": completions.addAll(Arrays.asList("thrall", "master")); break;
                case "findthrall": case "check": case "force": case "stay":
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName())); break;
                case "command":
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    completions.add("all"); break;
                case "punish":
                    completions.addAll(Arrays.asList("nausea","blindness","weakness","slowness","hunger","poison")); break;
                case "bar":
                    completions.addAll(Arrays.asList("Dose","Withdraw","Unthrallment","on","off")); break;
                case "profile":
                    completions.addAll(Arrays.asList("create","select","list","delete")); break;
                case "preferences":
                    completions.add("list");
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName())); break;
                case "admin":
                    completions.addAll(Arrays.asList("cooldownreset","reset","setowner","bondset","forcesave")); break;
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("bondinfo".equals(sub) && "thrall".equals(args[1].toLowerCase()))
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            else if ("punish".equals(sub) || "stay".equals(sub) || "command".equals(sub))
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            else if ("admin".equals(sub)) {
                if ("reset".equals(args[1]) || "cooldownreset".equals(args[1])) {
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    completions.add("@a");
                } else if ("setowner".equals(args[1]) || "bondset".equals(args[1])) {
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                }
            }
        }

        // Filter by current input
        String current = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(current));
        return completions;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void ab(Player p, String msg) {
        thrallManager.sendActionBar(p, msg);
    }

    private String nameOf(UUID id) {
        if (id == null) return "Unknown";
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        // Check bond data username cache
        for (ThrallBondData d : thrallManager.getBonds().values()) {
            if (d.getUsername() != null) {
                // No direct map from UUID to username except online players and bond caches
            }
        }
        return id.toString().substring(0, 8) + "...";
    }

    private String getUsername(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        ThrallBondData d = thrallManager.getBond(uuid);
        if (d != null && d.getUsername() != null) return d.getUsername();
        return uuid.toString().substring(0, 8) + "...";
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
