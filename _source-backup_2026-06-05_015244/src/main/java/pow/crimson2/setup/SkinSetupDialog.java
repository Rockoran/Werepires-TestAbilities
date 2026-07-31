package pow.crimson2.setup;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.SkinShuffleManager;

import java.time.Duration;
import java.util.List;

/**
 * Skin registration wizard integrated into /playersetup.
 *
 * Architecture: dialogs are used ONLY for intro / status / completion screens.
 * The actual skin capture happens in CHAT, because players cannot interact with
 * the SkinShuffle mod menu while a dialog is open.
 *
 * Chat capture flow per stage:
 *   1. Dialog → [I understand] button → dialog closes
 *   2. Chat shows instructions + clickable [Capture Skin] button
 *   3. Player opens SkinShuffle, applies their skin
 *   4. Player clicks [Capture Skin] in chat (Adventure ClickCallback, single-use)
 *   5. Plugin reads player.getPlayerProfile() — SkinShuffle already set it server-side
 *   6. Skin saved → next stage prompt appears in chat automatically
 */
@SuppressWarnings("UnstableApiUsage")
public class SkinSetupDialog {

    private static final String[] STAGES = {"human", "stage1", "stage2", "stage3"};

    // How long a chat capture button stays clickable (in case player gets distracted)
    private static final Duration BUTTON_LIFETIME = Duration.ofMinutes(15);

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void open(VampireSMPPlugin plugin, Player p, Runnable continuation) {
        SkinShuffleManager mgr = plugin.getSkinShuffleManager();
        if (mgr == null || !mgr.isEnabled()) {
            continuation.run();
            return;
        }

        if (mgr.hasAnySkins(p)) {
            openReturnDialog(plugin, p, mgr, continuation);
        } else {
            openFirstTimeIntro(plugin, p, mgr, continuation);
        }
    }

    // =========================================================================
    // First-time intro (dialog only — no skin-switch needed yet)
    // =========================================================================

    private static void openFirstTimeIntro(VampireSMPPlugin plugin, Player p,
                                            SkinShuffleManager mgr, Runnable continuation) {
        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Stage Skins", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "You can link a custom skin to each vampire stage.\n"
                    + "When you tier up or down, your skin changes automatically.\n\n"
                    + "How it works:\n"
                    + " 1. This dialog closes.\n"
                    + " 2. Instructions appear in chat.\n"
                    + " 3. Switch your skin in SkinShuffle.\n"
                    + " 4. Click [Capture Skin] in chat.\n"
                    + " 5. Repeat for each stage.\n\n"
                    + "You can skip any stage and set it up later with /skin register."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("I Understand — Start Setup", NamedTextColor.GREEN),
                    Component.text("Closes this dialog and shows the chat prompt."),
                    200,
                    DialogAction.customClick((view, aud) ->
                        sync(plugin, () -> sendChatCapture(plugin, p, mgr, 0, continuation)),
                        ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Skip for Now", NamedTextColor.GRAY),
                    Component.text("Use /skin register <stage> any time later."),
                    150,
                    DialogAction.customClick((view, aud) ->
                        sync(plugin, continuation),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    // =========================================================================
    // Chat-based capture steps
    // =========================================================================

    /**
     * Sends the capture prompt for {@code STAGES[stageIndex]} into chat.
     * All buttons are Adventure ClickCallbacks — single-use, expire after 15 min.
     */
    private static void sendChatCapture(VampireSMPPlugin plugin, Player p,
                                         SkinShuffleManager mgr,
                                         int stageIndex, Runnable continuation) {
        if (stageIndex >= STAGES.length) {
            sendChatComplete(plugin, p, continuation);
            return;
        }

        String stage = STAGES[stageIndex];
        String label = SkinShuffleManager.displayName(stage);
        boolean canCopy = stageIndex > 0;
        String copyLabel = canCopy ? SkinShuffleManager.displayName(STAGES[stageIndex - 1]) : "";

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("━━━ Skin Setup: " + label + " ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        p.sendMessage(Component.text(stageIndex == 0
            ? "Switch to your Human skin in the SkinShuffle mod"
            : "Switch to your " + label + " skin in the SkinShuffle mod",
            NamedTextColor.GRAY));
        p.sendMessage(Component.text("(open the SkinShuffle menu → apply your skin),", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("then click the button below when your skin is active on screen.", NamedTextColor.GRAY));
        p.sendMessage(Component.empty());

        // Build the row of buttons
        // [Capture] [Use Same as X] [Skip]
        Component captureBtn = Component.text(" [Capture " + label + " Skin] ", NamedTextColor.GREEN, TextDecoration.BOLD)
            .clickEvent(ClickEvent.callback(
                aud -> {
                    if (!(aud instanceof Player clicker) || !clicker.equals(p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        mgr.registerCurrentSkin(p, stage);
                        sendChatCapture(plugin, p, mgr, stageIndex + 1, continuation);
                    });
                },
                ClickCallback.Options.builder().uses(1).lifetime(BUTTON_LIFETIME).build()
            ))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                Component.text("Reads your current skin and saves it as " + label + ".", NamedTextColor.GRAY)));

        Component buttons;
        if (canCopy) {
            Component copyBtn = Component.text(" [Same as " + copyLabel + "] ", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.callback(
                    aud -> {
                        if (!(aud instanceof Player clicker) || !clicker.equals(p)) return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            mgr.copySkin(p, STAGES[stageIndex - 1], stage);
                            sendChatCapture(plugin, p, mgr, stageIndex + 1, continuation);
                        });
                    },
                    ClickCallback.Options.builder().uses(1).lifetime(BUTTON_LIFETIME).build()
                ))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    Component.text("Copy your " + copyLabel + " skin to this stage.", NamedTextColor.GRAY)));

            Component skipBtn = Component.text(" [Skip] ", NamedTextColor.DARK_GRAY)
                .clickEvent(ClickEvent.callback(
                    aud -> {
                        if (!(aud instanceof Player clicker) || !clicker.equals(p)) return;
                        Bukkit.getScheduler().runTask(plugin, () ->
                            sendChatCapture(plugin, p, mgr, stageIndex + 1, continuation));
                    },
                    ClickCallback.Options.builder().uses(1).lifetime(BUTTON_LIFETIME).build()
                ))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    Component.text("No custom skin for " + label + ".", NamedTextColor.GRAY)));

            buttons = captureBtn
                .append(Component.text("│", NamedTextColor.DARK_GRAY))
                .append(copyBtn)
                .append(Component.text("│", NamedTextColor.DARK_GRAY))
                .append(skipBtn);
        } else {
            Component skipBtn = Component.text(" [Skip] ", NamedTextColor.DARK_GRAY)
                .clickEvent(ClickEvent.callback(
                    aud -> {
                        if (!(aud instanceof Player clicker) || !clicker.equals(p)) return;
                        Bukkit.getScheduler().runTask(plugin, () ->
                            sendChatCapture(plugin, p, mgr, stageIndex + 1, continuation));
                    },
                    ClickCallback.Options.builder().uses(1).lifetime(BUTTON_LIFETIME).build()
                ))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    Component.text("No custom skin for this stage.", NamedTextColor.GRAY)));

            buttons = captureBtn
                .append(Component.text("│", NamedTextColor.DARK_GRAY))
                .append(skipBtn);
        }

        p.sendMessage(buttons);
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        p.sendMessage(Component.empty());
    }

    /** Shown in chat after all stages are captured/skipped. */
    private static void sendChatComplete(VampireSMPPlugin plugin, Player p, Runnable continuation) {
        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("✔ Skin setup complete!", NamedTextColor.GREEN, TextDecoration.BOLD));
        p.sendMessage(Component.text("Use /skin list to review or /skin register <stage> to update.", NamedTextColor.GRAY));
        p.sendMessage(Component.empty());
        // Return to setup flow (next dialog)
        Bukkit.getScheduler().runTask(plugin, continuation);
    }

    // =========================================================================
    // Return-visit flow (skins already registered)
    // =========================================================================

    private static void openReturnDialog(VampireSMPPlugin plugin, Player p,
                                          SkinShuffleManager mgr, Runnable continuation) {
        String status = buildStatusLine(p, mgr);

        Dialog d = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Stage Skins", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text(
                    "You already have stage skins registered:\n\n"
                    + status + "\n\n"
                    + "Open the preview to update individual skins,\n"
                    + "or continue with your current ones."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Preview & Edit Skins", NamedTextColor.YELLOW),
                    Component.text("See a visual preview and update any stage."),
                    200,
                    DialogAction.customClick((view, aud) -> sync(plugin, () ->
                        SkinPreviewGui.open(plugin, p, mgr,
                            () -> openReturnDialog(plugin, p, mgr, continuation))),
                        ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Keep Current Skins ▸", NamedTextColor.GREEN), null, 180,
                    DialogAction.customClick((view, aud) ->
                        sync(plugin, continuation),
                        ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Re-register All Skins", NamedTextColor.GRAY),
                    Component.text("Restart the capture wizard from scratch.\n"
                        + "Closes this dialog and shows instructions in chat."),
                    150,
                    DialogAction.customClick((view, aud) ->
                        sync(plugin, () -> sendChatCapture(plugin, p, mgr, 0, continuation)),
                        ClickCallback.Options.builder().build()))
            )).build())
        );
        p.showDialog(d);
    }

    /**
     * Entry point for updating a single stage from the preview GUI.
     * Sends a chat capture prompt for that one stage, then calls {@code afterCapture}.
     */
    public static void openSingleCaptureStep(VampireSMPPlugin plugin, Player p,
                                              SkinShuffleManager mgr,
                                              String stage, Runnable afterCapture) {
        String label = SkinShuffleManager.displayName(stage);

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("━━━ Update Skin: " + label + " ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        p.sendMessage(Component.text("Switch to your " + label + " skin in SkinShuffle,", NamedTextColor.GRAY));
        p.sendMessage(Component.text("then click the button below.", NamedTextColor.GRAY));
        p.sendMessage(Component.empty());

        Component captureBtn = Component.text(" [Capture " + label + " Skin] ", NamedTextColor.GREEN, TextDecoration.BOLD)
            .clickEvent(ClickEvent.callback(
                aud -> {
                    if (!(aud instanceof Player clicker) || !clicker.equals(p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        mgr.registerCurrentSkin(p, stage);
                        afterCapture.run();
                    });
                },
                ClickCallback.Options.builder().uses(1).lifetime(BUTTON_LIFETIME).build()
            ));

        Component cancelBtn = Component.text(" [Cancel] ", NamedTextColor.DARK_GRAY)
            .clickEvent(ClickEvent.callback(
                aud -> {
                    if (!(aud instanceof Player clicker) || !clicker.equals(p)) return;
                    Bukkit.getScheduler().runTask(plugin, afterCapture);
                },
                ClickCallback.Options.builder().uses(1).lifetime(BUTTON_LIFETIME).build()
            ));

        p.sendMessage(captureBtn
            .append(Component.text("│", NamedTextColor.DARK_GRAY))
            .append(cancelBtn));
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        p.sendMessage(Component.empty());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String buildStatusLine(Player p, SkinShuffleManager mgr) {
        StringBuilder sb = new StringBuilder();
        for (String stage : STAGES) {
            sb.append(mgr.hasStageRegistered(p, stage) ? "§a✔ " : "§8✗ ")
              .append(SkinShuffleManager.displayName(stage))
              .append("\n");
        }
        return sb.toString().trim();
    }

    private static void sync(VampireSMPPlugin plugin, Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
