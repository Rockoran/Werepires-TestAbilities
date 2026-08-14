package pow.crimson2.phone;

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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
final class PhoneUi {
    private PhoneUi() {}

    static void openMessages(PhoneManager manager, Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        String self = player.getUniqueId().toString();
        for (Map.Entry<String, PhoneDataStore.Contact> entry : manager.store().player(self).contacts.entrySet()) {
            String target = entry.getKey();
            buttons.add(manager.button(manager.displayName(target), "Open conversation", () -> openConversation(manager, player, target)));
        }
        buttons.add(manager.button("New Message", "Send by Minecraft or character name", () -> openCompose(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Messages", "Direct messages remain available while players are offline.", buttons, 2);
    }

    private static void openCompose(PhoneManager manager, Player player) {
        showForm(player, "New Message", "Enter a player and message.", List.of(
                DialogInput.text("target", Component.text("Player")).maxLength(32).build(),
                DialogInput.text("message", Component.text("Message")).maxLength(256).build()),
                "Send", view -> {
                    String target = resolvePlayer(manager, view.getText("target"));
                    if (target == null) {
                        player.sendMessage(Component.text("That phone user was not found.", NamedTextColor.RED));
                        return;
                    }
                    sendMessage(manager, player, target, view.getText("message"));
                    openConversation(manager, player, target);
                }, () -> openMessages(manager, player));
    }

    private static void openConversation(PhoneManager manager, Player player, String target) {
        String self = player.getUniqueId().toString();
        String key = manager.conversationKey(self, target);
        PhoneDataStore.Conversation conversation = manager.store().database().conversations
                .computeIfAbsent(key, ignored -> new PhoneDataStore.Conversation());
        StringBuilder body = new StringBuilder();
        int start = Math.max(0, conversation.messages.size() - 8);
        for (int i = start; i < conversation.messages.size(); i++) {
            PhoneDataStore.Message message = conversation.messages.get(i);
            body.append(manager.displayName(message.from)).append(": ").append(message.text).append('\n');
            if (!self.equals(message.from)) message.read = true;
        }
        manager.store().save();
        showForm(player, manager.displayName(target), body.length() == 0 ? "No messages yet." : body.toString(),
                List.of(DialogInput.text("message", Component.text("Reply")).maxLength(256).build()),
                "Send", view -> {
                    sendMessage(manager, player, target, view.getText("message"));
                    openConversation(manager, player, target);
                }, () -> openMessages(manager, player));
    }

    private static void sendMessage(PhoneManager manager, Player sender, String target, String text) {
        if (text == null || text.isBlank()) return;
        String self = sender.getUniqueId().toString();
        PhoneDataStore.PlayerRecord recipient = manager.store().player(target);
        PhoneDataStore.Contact recipientContact = recipient.contacts.get(self);
        if (recipientContact != null && recipientContact.blocked) {
            sender.sendMessage(Component.text("Your message could not be delivered.", NamedTextColor.RED));
            return;
        }
        PhoneDataStore.Message message = new PhoneDataStore.Message();
        message.from = self;
        message.text = text.strip();
        message.sentAt = System.currentTimeMillis();
        message.read = false;
        manager.store().database().conversations
                .computeIfAbsent(manager.conversationKey(self, target), ignored -> new PhoneDataStore.Conversation())
                .messages.add(message);
        manager.store().save();
        Player online = Bukkit.getPlayer(UUID.fromString(target));
        if (online != null && !recipient.doNotDisturb) {
            online.sendMessage(Component.text("Phone: new message from " + manager.displayName(self), NamedTextColor.AQUA));
        }
    }

    static void openContacts(PhoneManager manager, Player player) {
        String self = player.getUniqueId().toString();
        PhoneDataStore.PlayerRecord record = manager.store().player(self);
        List<ActionButton> buttons = new ArrayList<>();
        record.contacts.entrySet().stream()
                .sorted(Comparator.comparing(entry -> manager.displayName(entry.getKey())))
                .forEach(entry -> buttons.add(manager.button(
                        (entry.getValue().favorite ? "★ " : "") + manager.displayName(entry.getKey()),
                        entry.getValue().blocked ? "Blocked" : "Open contact",
                        () -> openContact(manager, player, entry.getKey()))));
        buttons.add(manager.button("Add Contact", "Add by Minecraft or character name", () -> openAddContact(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Contacts", record.contacts.size() + " saved contact(s)", buttons, 2);
    }

    private static void openAddContact(PhoneManager manager, Player player) {
        showForm(player, "Add Contact", "Enter a known player name.",
                List.of(DialogInput.text("target", Component.text("Player")).maxLength(32).build()),
                "Add", view -> {
                    String target = resolvePlayer(manager, view.getText("target"));
                    if (target == null || target.equals(player.getUniqueId().toString())) {
                        player.sendMessage(Component.text("That phone user was not found.", NamedTextColor.RED));
                        return;
                    }
                    manager.store().player(player.getUniqueId().toString()).contacts
                            .computeIfAbsent(target, ignored -> new PhoneDataStore.Contact());
                    manager.store().save();
                    openContacts(manager, player);
                }, () -> openContacts(manager, player));
    }

    private static void openContact(PhoneManager manager, Player player, String target) {
        PhoneDataStore.Contact contact = manager.store().player(player.getUniqueId().toString()).contacts.get(target);
        showActions(player, manager.displayName(target), contact.blocked ? "This contact is blocked." : "Contact options", List.of(
                manager.button("Message", "Open direct messages", () -> openConversation(manager, player, target)),
                manager.button(contact.favorite ? "Remove Favorite" : "Favorite", "Toggle favorite", () -> {
                    contact.favorite = !contact.favorite; manager.store().save(); openContact(manager, player, target);
                }),
                manager.button(contact.blocked ? "Unblock" : "Block", "Toggle message and call blocking", () -> {
                    contact.blocked = !contact.blocked; manager.store().save(); openContact(manager, player, target);
                }),
                manager.button("Delete Contact", "Remove this contact", () -> {
                    manager.store().player(player.getUniqueId().toString()).contacts.remove(target);
                    manager.store().save(); openContacts(manager, player);
                }),
                manager.button("Back", "Return to contacts", () -> openContacts(manager, player))
        ), 2);
    }

    static void openSettings(PhoneManager manager, Player player) {
        PhoneDataStore.PlayerRecord settings = manager.store().player(player.getUniqueId().toString());
        showActions(player, "Settings", "Color: " + settings.color, List.of(
                toggle(manager, player, "Do Not Disturb", settings.doNotDisturb, () -> settings.doNotDisturb = !settings.doNotDisturb),
                toggle(manager, player, "Silent", settings.silent, () -> settings.silent = !settings.silent),
                toggle(manager, player, "Vibrate", settings.vibrate, () -> settings.vibrate = !settings.vibrate),
                toggle(manager, player, "Disable Refresh", settings.noRefresh, () -> settings.noRefresh = !settings.noRefresh),
                manager.button("Change Color", "Cycle phone case color", () -> {
                    List<String> colors = new ArrayList<>(PhoneManager.COLORS); colors.sort(String::compareTo);
                    settings.color = colors.get((colors.indexOf(settings.color) + 1 + colors.size()) % colors.size());
                    manager.store().save(); player.sendMessage(Component.text("Newly issued phones will use " + settings.color + ".", NamedTextColor.AQUA));
                    openSettings(manager, player);
                }),
                manager.button("Back", "Return to the phone", () -> manager.openMain(player))
        ), 2);
    }

    private static ActionButton toggle(PhoneManager manager, Player player, String name, boolean enabled, Runnable change) {
        return manager.button(name + ": " + (enabled ? "ON" : "OFF"), "Toggle setting", () -> {
            change.run(); manager.store().save(); openSettings(manager, player);
        });
    }

    static void openGps(PhoneManager manager, Player player) {
        PhoneDataStore.PlayerRecord record = manager.store().player(player.getUniqueId().toString());
        List<ActionButton> buttons = new ArrayList<>();
        for (PhoneDataStore.GpsPoint point : record.gps) {
            buttons.add(manager.button(point.name, point.world + "  " + Math.round(point.x) + ", " + Math.round(point.y) + ", " + Math.round(point.z),
                    () -> pointPlayer(player, point)));
        }
        buttons.add(manager.button("Save Current Location", "Create a named GPS point", () -> openSaveGps(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "GPS", record.gps.size() + " saved location(s)", buttons, 2);
    }

    private static void openSaveGps(PhoneManager manager, Player player) {
        showForm(player, "Save GPS Location", "Save your current position.",
                List.of(DialogInput.text("name", Component.text("Location name")).maxLength(40).build()), "Save", view -> {
                    String name = view.getText("name"); if (name == null || name.isBlank()) return;
                    Location location = player.getLocation();
                    PhoneDataStore.GpsPoint point = new PhoneDataStore.GpsPoint();
                    point.name = name.strip(); point.world = location.getWorld().getName();
                    point.x = location.getX(); point.y = location.getY(); point.z = location.getZ();
                    manager.store().player(player.getUniqueId().toString()).gps.add(point);
                    manager.store().save(); openGps(manager, player);
                }, () -> openGps(manager, player));
    }

    private static void pointPlayer(Player player, PhoneDataStore.GpsPoint point) {
        if (!player.getWorld().getName().equals(point.world)) {
            player.sendMessage(Component.text("GPS point is in " + point.world + ".", NamedTextColor.YELLOW)); return;
        }
        double dx = point.x - player.getX(); double dz = point.z - player.getZ();
        player.sendMessage(Component.text("GPS: " + point.name + " is " + Math.round(Math.hypot(dx, dz)) + " blocks away (ΔX " + Math.round(dx) + ", ΔZ " + Math.round(dz) + ").", NamedTextColor.AQUA));
    }

    static void openNotes(PhoneManager manager, Player player) {
        PhoneDataStore.PlayerRecord record = manager.store().player(player.getUniqueId().toString());
        List<ActionButton> buttons = new ArrayList<>();
        record.notes.stream().sorted(Comparator.comparing((PhoneDataStore.Note n) -> !n.pinned))
                .forEach(note -> buttons.add(manager.button((note.pinned ? "★ " : "") + note.title, note.body, () -> openNote(manager, player, note))));
        buttons.add(manager.button("New Note", "Create a note", () -> editNote(manager, player, null)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Notes", record.notes.size() + " note(s)", buttons, 2);
    }

    private static void openNote(PhoneManager manager, Player player, PhoneDataStore.Note note) {
        showActions(player, note.title, note.body, List.of(
                manager.button("Edit", "Edit this note", () -> editNote(manager, player, note)),
                manager.button(note.pinned ? "Unpin" : "Pin", "Toggle pin", () -> { note.pinned = !note.pinned; manager.store().save(); openNote(manager, player, note); }),
                manager.button("Delete", "Delete this note", () -> { manager.store().player(player.getUniqueId().toString()).notes.remove(note); manager.store().save(); openNotes(manager, player); }),
                manager.button("Back", "Return to notes", () -> openNotes(manager, player))
        ), 2);
    }

    private static void editNote(PhoneManager manager, Player player, PhoneDataStore.Note note) {
        showForm(player, note == null ? "New Note" : "Edit Note", "Notes are private.", List.of(
                DialogInput.text("title", Component.text("Title")).initial(note == null ? "" : note.title).maxLength(48).build(),
                DialogInput.text("body", Component.text("Body")).initial(note == null ? "" : note.body).maxLength(512).build()), "Save", view -> {
                    PhoneDataStore.Note changed = note == null ? new PhoneDataStore.Note() : note;
                    changed.title = view.getText("title"); changed.body = view.getText("body"); changed.editedAt = System.currentTimeMillis();
                    if (note == null) manager.store().player(player.getUniqueId().toString()).notes.add(changed);
                    manager.store().save(); openNotes(manager, player);
                }, () -> openNotes(manager, player));
    }

    static void openCalls(PhoneManager manager, Player player) { comingSoon(manager, player, "Calls", "Simple Voice Chat call sessions are being wired next."); }
    static void openSocial(PhoneManager manager, Player player) { comingSoon(manager, player, "Social", "Handles, feed, follows, and reactions are stored in the new schema."); }
    static void openGroups(PhoneManager manager, Player player) { comingSoon(manager, player, "Group Chats", "Persistent group records and messages are ready for the group UI."); }
    static void openGames(PhoneManager manager, Player player) { comingSoon(manager, player, "Games", "Game records are ready for the native game implementations."); }

    private static void comingSoon(PhoneManager manager, Player player, String title, String body) {
        showActions(player, title, body, List.of(manager.button("Back", "Return to the phone", () -> manager.openMain(player))), 1);
    }

    private static String resolvePlayer(PhoneManager manager, String input) {
        if (input == null) return null;
        String wanted = input.strip();
        for (Map.Entry<String, PhoneDataStore.PlayerRecord> entry : manager.store().database().players.entrySet()) {
            PhoneDataStore.PlayerRecord record = entry.getValue();
            if (wanted.equalsIgnoreCase(record.minecraftName) || wanted.equalsIgnoreCase(record.characterName)) return entry.getKey();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(wanted);
        return offline == null ? null : offline.getUniqueId().toString();
    }

    private static void showActions(Player player, String title, String body, List<ActionButton> buttons, int columns) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(body == null ? "" : body)))).build())
                .type(DialogType.multiAction(buttons).columns(columns).build()));
        player.showDialog(dialog);
    }

    private interface FormSubmit { void accept(io.papermc.paper.dialog.DialogResponseView view); }

    private static void showForm(Player player, String title, String body, List<DialogInput> inputs,
                                 String submitLabel, FormSubmit submit, Runnable back) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(body == null ? "" : body))))
                        .inputs(inputs).build())
                .type(DialogType.multiAction(List.of(
                        ActionButton.create(Component.text(submitLabel, NamedTextColor.GREEN), null, 120,
                                DialogAction.customClick((view, audience) -> Bukkit.getScheduler().runTask(
                                        VampireSMPPluginHolder.plugin(player), () -> submit.accept(view)), ClickCallback.Options.builder().build())),
                        ActionButton.create(Component.text("Back"), null, 100,
                                DialogAction.customClick((view, audience) -> Bukkit.getScheduler().runTask(
                                        VampireSMPPluginHolder.plugin(player), back), ClickCallback.Options.builder().build()))
                )).build()));
        player.showDialog(dialog);
    }

    /** Avoids retaining a second plugin field in every static form callback. */
    private static final class VampireSMPPluginHolder {
        static org.bukkit.plugin.java.JavaPlugin plugin(Player player) {
            return (org.bukkit.plugin.java.JavaPlugin) Bukkit.getPluginManager().getPlugin("WerePires");
        }
    }
}
