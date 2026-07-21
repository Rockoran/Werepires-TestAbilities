package pow.crimson2.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

import java.util.List;

/**
 * Handles permanent self-kill ({@code /pow permakill}, config-gated, with a confirmation dialog)
 * and admin-forced permakill ({@code /pow admin permakill <player>}). The actual permadeath is
 * performed by {@link pow.crimson2.managers.VampireManager#permakillPlayer(Player)}.
 */
@SuppressWarnings("UnstableApiUsage")
public class PermakillCommand {

    private final VampireSMPPlugin plugin;

    public PermakillCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSelfPermakillEnabled() {
        return plugin.getConfig().getBoolean("allow-self-permakill", true);
    }

    // ── Self permakill ────────────────────────────────────────────────────────

    /** Player ran {@code /pow permakill}. Gate on config, then show a confirmation dialog. */
    public void requestSelf(Player player) {
        if (!isSelfPermakillEnabled()) {
            player.sendMessage("§cThis server has self-permakill disabled.");
            return;
        }
        openConfirmDialog(player);
    }

    private void openConfirmDialog(Player player) {
        ActionButton confirm = ActionButton.create(
                Component.text("End my journey — forever", NamedTextColor.DARK_RED), null, 220,
                DialogAction.customClick((view, aud) -> sync(() -> {
                    if (!isSelfPermakillEnabled()) {
                        player.sendMessage("§cThis server has self-permakill disabled.");
                        return;
                    }
                    boolean ok = plugin.getVampireManager().permakillPlayer(player);
                    if (!ok) {
                        player.sendMessage("§cYou can't permakill yourself right now.");
                    } else {
                        plugin.logInfo("SELF-PERMAKILL: " + player.getName() + " ended their own journey.");
                    }
                }), ClickCallback.Options.builder().build()));

        ActionButton cancel = ActionButton.create(
                Component.text("Cling to life", NamedTextColor.GREEN), null, 220,
                DialogAction.customClick((view, aud) -> { }, ClickCallback.Options.builder().build()));

        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text("Final Death", NamedTextColor.DARK_RED))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "This will permanently kill you. There is no coming back — "
                                        + "your journey ends here. Are you certain?"))))
                        .build())
                .type(DialogType.multiAction(List.of(confirm, cancel)).build()));
        player.showDialog(d);
    }

    // ── Admin permakill ───────────────────────────────────────────────────────

    /** Admin ran {@code /pow admin permakill <player>}. */
    public void adminPermakill(CommandSender admin, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            admin.sendMessage("§cUsage: /pow admin permakill <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            admin.sendMessage("§cPlayer not found or offline: §e" + targetName);
            return;
        }
        boolean ok = plugin.getVampireManager().permakillPlayer(target);
        if (!ok) {
            admin.sendMessage("§c" + target.getName() + " can't be permakilled right now "
                    + "(they're not in normal play — already a spectator/ghost?).");
            return;
        }
        admin.sendMessage("§4Permanently killed §c" + target.getName() + "§4.");
        plugin.logInfo("ADMIN PERMAKILL: " + admin.getName() + " permakilled " + target.getName() + ".");
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
