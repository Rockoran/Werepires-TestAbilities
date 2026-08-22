package pow.crimson2.gamestart;

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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

import java.util.List;

/** Paper-dialog wizard for /pow admin sessionsetup. */
@SuppressWarnings("UnstableApiUsage")
public final class SessionSetupCommand {
    private final VampireSMPPlugin plugin;
    private final ScheduledSessionManager manager;

    public SessionSetupCommand(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getScheduledSessionManager();
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            manager.status(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            if (!manager.cancel(sender)) sender.sendMessage("§7There is no active scheduled session.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers use the setup dialog. Console usage: /pow admin sessionsetup <initialBuild> <beforeBreak> <break> <afterBreak> <finalBuild>");
            if (args.length != 5) return true;
            int[] values = parse(args);
            if (values == null) { sender.sendMessage("§cAll five values must be whole minutes from 1 to 10080."); return true; }
            if (manager.isActive()) {
                sender.sendMessage("§cA schedule is already active. Use /pow admin sessionsetup cancel first.");
                return true;
            }
            manager.start(values[0], values[1], values[2], values[3], values[4]);
            return true;
        }
        showInputs(player);
        return true;
    }

    private void showInputs(Player player) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("WerePires — Session Setup", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Enter each phase in whole minutes. The normal WerePires break system will pause and resume the session."))))
                        .inputs(List.of(
                                input("initial", "Beginning building time", "15"),
                                input("before", "Session time until break", "60"),
                                input("break", "Break duration", "15"),
                                input("after", "Session time after break", "60"),
                                input("final", "Building time after session end", "15")
                        )).build())
                .type(DialogType.multiAction(List.of(
                        ActionButton.create(Component.text("Review Schedule", NamedTextColor.GREEN),
                                Component.text("Validate these times and show the final timeline."), 180,
                                DialogAction.customClick((view, audience) -> {
                                    String[] raw = { view.getText("initial"), view.getText("before"),
                                            view.getText("break"), view.getText("after"), view.getText("final") };
                                    sync(() -> {
                                        int[] values = parse(raw);
                                        if (values == null) {
                                            player.sendMessage(Component.text("Every value must be a whole number from 1 to 10080 minutes.", NamedTextColor.RED));
                                            showInputs(player);
                                        } else showConfirmation(player, values);
                                    });
                                }, ClickCallback.Options.builder().build())),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 100,
                                DialogAction.customClick((view, audience) -> sync(() ->
                                        player.sendMessage(Component.text("Session setup cancelled.", NamedTextColor.RED))),
                                        ClickCallback.Options.builder().build()))
                )).build()));
        player.showDialog(dialog);
    }

    private void showConfirmation(Player player, int[] v) {
        String timeline = "Building " + v[0] + "m → Session " + v[1] + "m → Break " + v[2]
                + "m → Session " + v[3] + "m → Final building " + v[4] + "m";
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Confirm Session Schedule", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(timeline)))).build())
                .type(DialogType.multiAction(List.of(
                        ActionButton.create(Component.text("Start Schedule", NamedTextColor.GREEN),
                                Component.text("This immediately enters the beginning building phase."), 180,
                                DialogAction.customClick((view, audience) -> sync(() -> {
                                    if (manager.isActive()) {
                                        player.sendMessage(Component.text("A schedule is already active. Cancel it first with /pow admin sessionsetup cancel.", NamedTextColor.RED));
                                    } else manager.start(v[0], v[1], v[2], v[3], v[4]);
                                }), ClickCallback.Options.builder().build())),
                        ActionButton.create(Component.text("Back", NamedTextColor.YELLOW), null, 100,
                                DialogAction.customClick((view, audience) -> sync(() -> showInputs(player)),
                                        ClickCallback.Options.builder().build())),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 100,
                                DialogAction.customClick((view, audience) -> sync(() ->
                                        player.sendMessage(Component.text("Session setup cancelled.", NamedTextColor.RED))),
                                        ClickCallback.Options.builder().build()))
                )).build()));
        player.showDialog(dialog);
    }

    private static DialogInput input(String key, String label, String initial) {
        return DialogInput.text(key, Component.text(label + " (minutes)"))
                .initial(initial).maxLength(5).build();
    }

    private static int[] parse(String[] args) {
        if (args.length != 5) return null;
        int[] values = new int[5];
        try {
            for (int i = 0; i < 5; i++) {
                values[i] = Integer.parseInt(args[i]);
                if (!ScheduledSessionManager.validMinutes(values[i])) return null;
            }
            return values;
        } catch (NumberFormatException ex) { return null; }
    }

    private void sync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
