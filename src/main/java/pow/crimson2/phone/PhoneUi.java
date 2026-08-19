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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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
            long unread = manager.store().database().conversations.getOrDefault(manager.conversationKey(self, target), new PhoneDataStore.Conversation()).messages.stream()
                    .filter(message -> !message.read && !self.equals(message.from)).count();
            buttons.add(manager.button(manager.contactName(self, target) + (unread > 0 ? " (" + unread + ")" : ""), "Open conversation", () -> openConversation(manager, player, target)));
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

    private static void openConversation(PhoneManager manager, Player player, String target) { openConversation(manager, player, target, Integer.MAX_VALUE); }
    private static void openConversation(PhoneManager manager, Player player, String target, int requestedPage) {
        String self = player.getUniqueId().toString();
        String key = manager.conversationKey(self, target);
        PhoneDataStore.Conversation conversation = manager.store().database().conversations
                .computeIfAbsent(key, ignored -> new PhoneDataStore.Conversation());
        StringBuilder body = new StringBuilder();
        int pages = Math.max(1, (int)Math.ceil(conversation.messages.size() / 8.0)); int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * 8, end = Math.min(conversation.messages.size(), start + 8);
        for (int i = start; i < end; i++) {
            PhoneDataStore.Message message = conversation.messages.get(i);
            body.append(manager.displayName(message.from)).append(": ").append(message.text).append('\n');
            if (!self.equals(message.from)) message.read = true;
        }
        manager.store().save();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(manager.button("Reply", "Send a message", () -> openReply(manager, player, target)));
        if (page > 1) buttons.add(manager.button("Older", "Previous page", () -> openConversation(manager, player, target, page - 1)));
        if (page < pages) buttons.add(manager.button("Newer", "Next page", () -> openConversation(manager, player, target, page + 1)));
        buttons.add(manager.button("Export", "Choose recent or full transcript", () -> openExport(manager, player, conversation.messages, manager.displayName(target), () -> openConversation(manager, player, target, page))));
        buttons.add(manager.button("Delete", "Delete this conversation for both participants", () -> {
            manager.store().database().conversations.remove(key); manager.store().save(); openMessages(manager, player);
        }));
        buttons.add(manager.button("Back", "Return to messages", () -> openMessages(manager, player)));
        showActions(player, manager.contactName(self, target), (body.length() == 0 ? "No messages yet." : body.toString()) + "\nPage " + page + "/" + pages, buttons, 2);
    }

    private static void openReply(PhoneManager manager, Player player, String target) {
        showForm(player, "Reply to " + manager.displayName(target), "Write a message.", List.of(DialogInput.text("message", Component.text("Message")).maxLength(256).build()), "Send", view -> {
            sendMessage(manager, player, target, view.getText("message")); openConversation(manager, player, target);
        }, () -> openConversation(manager, player, target));
    }

    private static void exportMessages(PhoneManager manager, Player player, List<PhoneDataStore.Message> messages, String title, int limit) {
        StringBuilder transcript = new StringBuilder(); int start = limit <= 0 ? 0 : Math.max(0, messages.size() - limit);
        for (int i = start; i < messages.size() && transcript.length() < 8000; i++) transcript.append(manager.displayName(messages.get(i).from)).append(": ").append(messages.get(i).text.replace('"', '\'')).append(" | ");
        if (transcript.length() == 0) { player.sendMessage(Component.text("Nothing to export.", NamedTextColor.RED)); return; }
        player.sendMessage(Component.text("[Click to copy " + title + "]", NamedTextColor.GREEN).clickEvent(ClickEvent.copyToClipboard(transcript.toString())));
    }

    private static void openExport(PhoneManager manager, Player player, List<PhoneDataStore.Message> messages, String title, Runnable back) {
        showActions(player, "Export " + title, "Choose the transcript size. Output is capped at 8,000 characters.", List.of(
                manager.button("Recent (50)", "Copy the latest 50 messages", () -> exportMessages(manager, player, messages, title + " recent", 50)),
                manager.button("Full", "Copy the complete conversation", () -> exportMessages(manager, player, messages, title + " full", 0)),
                manager.button("Back", "Return to conversation", back)
        ), 1);
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
            playNotification(online, recipient);
        }
    }

    private static void playNotification(Player player, PhoneDataStore.PlayerRecord settings) {
        if (settings.silent) return;
        if (settings.vibrate) player.playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 0.5f, 0.8f);
        else player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
    }

    static void openContacts(PhoneManager manager, Player player) {
        String self = player.getUniqueId().toString();
        PhoneDataStore.PlayerRecord record = manager.store().player(self);
        List<ActionButton> buttons = new ArrayList<>();
        record.contacts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, PhoneDataStore.Contact>, Boolean>comparing(entry -> !entry.getValue().favorite)
                        .thenComparing(entry -> manager.contactName(self, entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> buttons.add(manager.button(
                        (entry.getValue().favorite ? "★ " : "") + manager.contactName(self, entry.getKey()),
                        entry.getValue().blocked ? "Blocked" : "Open contact",
                        () -> openContact(manager, player, entry.getKey()))));
        buttons.add(manager.button("Add Contact", "Add by Minecraft or character name", () -> openAddContact(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Contacts", record.contacts.isEmpty()
                ? "No contacts yet. Shift + right-click a player while holding your phone to add them."
                : record.contacts.size() + " saved contact(s)", buttons, 2);
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
        showActions(player, manager.contactName(player.getUniqueId().toString(), target), contact.blocked ? "This contact is blocked." : "Contact options", List.of(
                manager.button("Message", "Open direct messages", () -> openConversation(manager, player, target)),
                manager.button("Call", "Start a private voice call", () -> manager.calls().call(player, target)),
                manager.button("Nickname", "Set or clear a private nickname", () -> openNickname(manager, player, target)),
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

    private static void openNickname(PhoneManager manager, Player player, String target) {
        PhoneDataStore.Contact contact = manager.store().player(player.getUniqueId().toString()).contacts.get(target);
        showForm(player, "Contact Nickname", "Leave blank to use their character name.",
                List.of(DialogInput.text("nickname", Component.text("Nickname")).initial(contact.nickname == null ? "" : contact.nickname).maxLength(32).build()), "Save", view -> {
                    contact.nickname = view.getText("nickname") == null ? "" : view.getText("nickname").strip(); manager.store().save(); openContact(manager, player, target);
                }, () -> openContact(manager, player, target));
    }

    static void openSettings(PhoneManager manager, Player player) {
        PhoneDataStore.PlayerRecord settings = manager.store().player(player.getUniqueId().toString());
        showActions(player, "Settings", "Color: " + settings.color, List.of(
                toggle(manager, player, "Do Not Disturb", settings.doNotDisturb, () -> settings.doNotDisturb = !settings.doNotDisturb),
                toggle(manager, player, "Silent", settings.silent, () -> settings.silent = !settings.silent),
                toggle(manager, player, "Vibrate", settings.vibrate, () -> settings.vibrate = !settings.vibrate),
                toggle(manager, player, "Disable Refresh", settings.noRefresh, () -> settings.noRefresh = !settings.noRefresh),
                manager.button("Change Color", "Choose and apply a phone color", () -> openColorPicker(manager, player)),
                manager.button("Back", "Return to the phone", () -> manager.openMain(player))
        ), 2);
    }

    private static void openColorPicker(PhoneManager manager, Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        PhoneManager.COLORS.stream().sorted().forEach(color -> buttons.add(manager.button(color.substring(0, 1).toUpperCase() + color.substring(1), "Apply to every phone in your inventory", () -> {
            manager.store().player(player.getUniqueId().toString()).color = color; manager.applyColor(player, color); manager.store().save(); openSettings(manager, player);
        })));
        buttons.add(manager.button("Back", "Return to settings", () -> openSettings(manager, player)));
        showActions(player, "Phone Color", "Current: " + manager.store().player(player.getUniqueId().toString()).color, buttons, 3);
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
                    () -> openGpsPoint(manager, player, point)));
        }
        buttons.add(manager.button("Save Current Location", "Create a named GPS point", () -> openSaveGps(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "GPS", record.gps.size() + " saved location(s)", buttons, 2);
    }

    private static void openSaveGps(PhoneManager manager, Player player) {
        if (manager.store().player(player.getUniqueId().toString()).gps.size() >= 50) { player.sendMessage(Component.text("GPS is full (50 pins).", NamedTextColor.RED)); openGps(manager, player); return; }
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

    private static void openGpsPoint(PhoneManager manager, Player player, PhoneDataStore.GpsPoint point) {
        showActions(player, point.name, point.world + " • " + Math.round(point.x) + ", " + Math.round(point.y) + ", " + Math.round(point.z)
                + (point.shared ? "\nShared by " + manager.displayName(point.sharedBy) : ""), List.of(
                manager.button("Track", "Show distance and direction", () -> { pointPlayer(player, point); openGpsPoint(manager, player, point); }),
                manager.button("Rename", "Rename this pin", () -> renameGps(manager, player, point)),
                manager.button("Share", "Copy this pin to a contact", () -> shareGps(manager, player, point)),
                manager.button("Delete", "Delete this pin", () -> { manager.store().player(player.getUniqueId().toString()).gps.remove(point); manager.store().save(); openGps(manager, player); }),
                manager.button("Back", "Return to GPS", () -> openGps(manager, player))
        ), 2);
    }

    private static void renameGps(PhoneManager manager, Player player, PhoneDataStore.GpsPoint point) {
        showForm(player, "Rename GPS Pin", "Choose a new name.", List.of(DialogInput.text("name", Component.text("Name")).initial(point.name).maxLength(40).build()), "Save", view -> {
            String name = view.getText("name"); if (name != null && !name.isBlank()) point.name = name.strip(); manager.store().save(); openGpsPoint(manager, player, point);
        }, () -> openGpsPoint(manager, player, point));
    }

    private static void shareGps(PhoneManager manager, Player player, PhoneDataStore.GpsPoint point) {
        List<ActionButton> buttons = new ArrayList<>(); String self = player.getUniqueId().toString();
        manager.store().player(self).contacts.keySet().forEach(target -> buttons.add(manager.button(manager.contactName(self, target), "Share a copy", () -> {
            PhoneDataStore.PlayerRecord recipient = manager.store().player(target); if (recipient.gps.size() >= 50) { player.sendMessage(Component.text("Their GPS is full.", NamedTextColor.RED)); return; }
            PhoneDataStore.GpsPoint copy = new PhoneDataStore.GpsPoint(); copy.name = point.name; copy.world = point.world; copy.x = point.x; copy.y = point.y; copy.z = point.z; copy.shared = true; copy.sharedBy = self;
            recipient.gps.add(copy); manager.store().save(); Player online = Bukkit.getPlayer(UUID.fromString(target)); if (online != null) online.sendMessage(Component.text(manager.displayName(self) + " shared GPS pin " + point.name + ".", NamedTextColor.AQUA)); openGpsPoint(manager, player, point);
        })));
        buttons.add(manager.button("Back", "Return to pin", () -> openGpsPoint(manager, player, point)));
        showActions(player, "Share " + point.name, "Choose a contact", buttons, 2);
    }

    private static void pointPlayer(Player player, PhoneDataStore.GpsPoint point) {
        if (!player.getWorld().getName().equals(point.world)) {
            player.sendMessage(Component.text("GPS point is in " + point.world + ".", NamedTextColor.YELLOW)); return;
        }
        double dx = point.x - player.getX(); double dz = point.z - player.getZ();
        double bearing = Math.toDegrees(Math.atan2(-dx, dz)); double relative = (bearing - player.getYaw() + 540) % 360 - 180;
        String arrow = Math.abs(relative) < 22.5 ? "↑" : relative > 0 && relative < 67.5 ? "↗" : relative >= 67.5 && relative < 112.5 ? "→" : relative >= 112.5 && relative < 157.5 ? "↘" : Math.abs(relative) >= 157.5 ? "↓" : relative <= -112.5 ? "↙" : relative <= -67.5 ? "←" : "↖";
        player.sendActionBar(Component.text(arrow + "  " + point.name + " • " + Math.round(Math.hypot(dx, dz)) + " blocks", NamedTextColor.AQUA));
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
        if (note == null && manager.store().player(player.getUniqueId().toString()).notes.size() >= 100) {
            player.sendMessage(Component.text("Notes are full (100 maximum).", NamedTextColor.RED));
            openNotes(manager, player);
            return;
        }
        showForm(player, note == null ? "New Note" : "Edit Note", "Notes are private.", List.of(
                DialogInput.text("title", Component.text("Title")).initial(note == null ? "" : note.title).maxLength(48).build(),
                DialogInput.text("body", Component.text("Body")).initial(note == null ? "" : note.body).maxLength(512).build()), "Save", view -> {
                    PhoneDataStore.Note changed = note == null ? new PhoneDataStore.Note() : note;
                    changed.title = view.getText("title"); changed.body = view.getText("body"); changed.editedAt = System.currentTimeMillis();
                    if (note == null) manager.store().player(player.getUniqueId().toString()).notes.add(changed);
                    manager.store().save(); openNotes(manager, player);
                }, () -> openNotes(manager, player));
    }

    static void openCalls(PhoneManager manager, Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().player(player.getUniqueId().toString()).contacts.forEach((uuid, contact) -> {
            if (!contact.blocked) buttons.add(manager.button("Call " + manager.displayName(uuid), "Start a private voice call", () -> manager.calls().call(player, uuid)));
        });
        buttons.add(manager.button("Answer", "Answer an incoming call", () -> manager.calls().answer(player)));
        buttons.add(manager.button("Decline", "Decline an incoming call", () -> manager.calls().decline(player)));
        buttons.add(manager.button("Hang Up", "Leave or cancel a call", () -> manager.calls().hangup(player)));
        if (manager.calls().isInCall(player)) {
            buttons.add(manager.button(manager.calls().isMuted(player) ? "Unmute Call Mic" : "Mute Call Mic", "Only affects the phone call; proximity remains active", () -> { manager.calls().toggleMute(player); openCalls(manager, player); }));
            buttons.add(manager.button(manager.calls().isDeafened(player) ? "Hear Call" : "Mute Call Audio", "Only affects incoming call audio; proximity remains active", () -> { manager.calls().toggleDeafen(player); openCalls(manager, player); }));
            buttons.add(manager.button(manager.calls().isSpeaker(player) ? "Speaker: ON" : "Speaker: OFF", "Nearby players can hear and speak into the call", () -> { manager.calls().toggleSpeaker(player); openCalls(manager, player); }));
        }
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Calls", manager.calls().available() ? "Simple Voice Chat connected" : "Voice calls unavailable", buttons, 2);
    }
    static void openSocial(PhoneManager manager, Player player) { openSocial(manager, player, false, 1); }
    private static void openSocial(PhoneManager manager, Player player, boolean followingOnly, int requestedPage) {
        String self = manager.characterKey(player);
        String handle = manager.store().database().handles.get(self);
        List<ActionButton> buttons = new ArrayList<>();
        List<Map.Entry<Integer, PhoneDataStore.SocialPost>> posts = manager.store().database().posts.entrySet().stream()
                .filter(entry -> !followingOnly || manager.socialFollowing(player).contains(entry.getValue().author) || self.equals(entry.getValue().author))
                .sorted(Map.Entry.<Integer, PhoneDataStore.SocialPost>comparingByKey().reversed()).toList();
        int pages = Math.max(1, (int)Math.ceil(posts.size() / 6.0)); int page = Math.max(1, Math.min(requestedPage, pages));
        posts.stream().skip((long)(page - 1) * 6).limit(6).forEach(entry -> buttons.add(manager.button("@" + entry.getValue().handle,
                        entry.getValue().text, () -> openPost(manager, player, entry.getKey()))));
        buttons.add(manager.button(handle == null ? "Create Handle" : "New Post",
                handle == null ? "Choose your permanent social handle" : "Post as @" + handle,
                () -> { if (handle == null) openHandleSetup(manager, player); else openCreatePost(manager, player); }));
        if (handle != null) buttons.add(manager.button("Discover", "Follow or unfollow phone accounts", () -> openDiscover(manager, player)));
        if (page > 1) buttons.add(manager.button("Newer", "Previous feed page", () -> openSocial(manager, player, followingOnly, page - 1)));
        if (page < pages) buttons.add(manager.button("Older", "Next feed page", () -> openSocial(manager, player, followingOnly, page + 1)));
        if (handle != null) buttons.add(manager.button(followingOnly ? "All Posts" : "Following Feed", "Switch feed filter", () -> openSocial(manager, player, !followingOnly, 1)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Social", (handle == null ? "No handle configured" : "Signed in as @" + handle) + " • Page " + page + "/" + pages, buttons, 2);
    }

    private static void openDiscover(PhoneManager manager, Player player) {
        String self = manager.characterKey(player);
        List<String> following = manager.socialFollowing(player);
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().database().handles.entrySet().stream().filter(e -> !e.getKey().equals(self))
                .sorted(Map.Entry.comparingByValue()).forEach(entry -> buttons.add(manager.button(
                        (following.contains(entry.getKey()) ? "✓ " : "") + "@" + entry.getValue(),
                        following.contains(entry.getKey()) ? "Click to unfollow" : "Click to follow", () -> {
                            if (!following.remove(entry.getKey())) following.add(entry.getKey());
                            manager.store().save(); openDiscover(manager, player);
                        })));
        buttons.add(manager.button("Back", "Return to social", () -> openSocial(manager, player)));
        showActions(player, "Discover", "Following " + following.size() + " account(s)", buttons, 2);
    }

    private static void openHandleSetup(PhoneManager manager, Player player) {
        showForm(player, "Create Handle", "Handles survive /phonereset and cannot be duplicated.",
                List.of(DialogInput.text("handle", Component.text("Handle (without @)")).maxLength(16).build()), "Create", view -> {
                    String raw = view.getText("handle");
                    String handle = raw == null ? "" : raw.strip().toLowerCase().replaceAll("[^a-z0-9_]", "");
                    if (handle.length() < 3 || manager.store().database().handles.values().stream().anyMatch(handle::equalsIgnoreCase)) {
                        player.sendMessage(Component.text("Handle must be unique and contain 3-16 letters, numbers, or underscores.", NamedTextColor.RED)); return;
                    }
                    manager.store().database().handles.put(manager.characterKey(player), handle);
                    manager.store().save(); openSocial(manager, player);
                }, () -> openSocial(manager, player));
    }

    private static void openCreatePost(PhoneManager manager, Player player) {
        showForm(player, "New Post", "Share with the phone social feed.",
                List.of(DialogInput.text("post", Component.text("Post")).maxLength(200).build()), "Post", view -> {
                    String text = view.getText("post"); if (text == null || text.isBlank()) return;
                    PhoneDataStore.SocialPost post = new PhoneDataStore.SocialPost();
                    post.author = manager.characterKey(player); post.handle = manager.store().database().handles.get(post.author);
                    post.text = text.strip(); post.sentAt = System.currentTimeMillis();
                    manager.store().database().posts.put(manager.store().database().nextPostId++, post);
                    manager.store().save(); openSocial(manager, player);
                }, () -> openSocial(manager, player));
    }

    private static void openPost(PhoneManager manager, Player player, int id) {
        PhoneDataStore.SocialPost post = manager.store().database().posts.get(id); if (post == null) { openSocial(manager, player); return; }
        String self = manager.characterKey(player);
        List<ActionButton> buttons = new ArrayList<>(List.of(manager.button(post.hearts.contains(self) ? "Remove Heart" : "Heart", "React to this post", () -> {
                    post.dislikes.remove(self); if (!post.hearts.remove(self)) post.hearts.add(self); manager.store().save(); openPost(manager, player, id);
                }),
                manager.button(post.dislikes.contains(self) ? "Remove Dislike" : "Dislike", "React to this post", () -> {
                    post.hearts.remove(self); if (!post.dislikes.remove(self)) post.dislikes.add(self); manager.store().save(); openPost(manager, player, id);
                })));
        if (self.equals(post.author)) buttons.add(manager.button("Delete Post", "Permanently remove this post", () -> { manager.store().database().posts.remove(id); manager.store().save(); openSocial(manager, player); }));
        buttons.add(manager.button("Back", "Return to social", () -> openSocial(manager, player)));
        showActions(player, "@" + post.handle, post.text + "\n\n♥ " + post.hearts.size() + "   👎 " + post.dislikes.size(), buttons, 2);
    }

    static void openGroups(PhoneManager manager, Player player) {
        String self = player.getUniqueId().toString();
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().database().groups.entrySet().stream().filter(e -> e.getValue().members.contains(self))
                .forEach(e -> { int unread = Math.max(0, e.getValue().messages.size() - e.getValue().lastRead.getOrDefault(self, 0)); buttons.add(manager.button(e.getValue().name + (unread > 0 ? " (" + unread + ")" : ""), e.getValue().members.size() + " members", () -> openGroup(manager, player, e.getKey()))); });
        buttons.add(manager.button("Create Group", "Start a group chat", () -> openCreateGroup(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Group Chats", "Your persistent group conversations", buttons, 2);
    }

    private static void openCreateGroup(PhoneManager manager, Player player) {
        showForm(player, "Create Group", "Create a new group chat.",
                List.of(DialogInput.text("name", Component.text("Group name")).maxLength(24).build()), "Create", view -> {
                    String name = view.getText("name"); if (name == null || name.isBlank()) return;
                    PhoneDataStore.GroupChat group = new PhoneDataStore.GroupChat();
                    group.name = name.strip(); group.owner = player.getUniqueId().toString(); group.members.add(group.owner);
                    int id = manager.store().database().nextGroupId++; manager.store().database().groups.put(id, group);
                    manager.store().save(); openGroup(manager, player, id);
                }, () -> openGroups(manager, player));
    }

    private static void openGroup(PhoneManager manager, Player player, int id) { openGroup(manager, player, id, Integer.MAX_VALUE); }
    private static void openGroup(PhoneManager manager, Player player, int id, int requestedPage) {
        PhoneDataStore.GroupChat group = manager.store().database().groups.get(id); if (group == null) { openGroups(manager, player); return; }
        String self = player.getUniqueId().toString();
        if (!group.members.contains(self)) { openGroups(manager, player); return; }
        group.lastRead.put(self, group.messages.size());
        StringBuilder body = new StringBuilder();
        int pages = Math.max(1, (int)Math.ceil(group.messages.size() / 8.0)); int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * 8, end = Math.min(group.messages.size(), start + 8);
        for (int i = start; i < end; i++) { PhoneDataStore.Message m = group.messages.get(i); body.append(manager.displayName(m.from)).append(": ").append(m.text).append('\n'); }
        manager.store().save();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(manager.button("Send Message", "Write to the group", () -> openGroupReply(manager, player, id)));
        buttons.add(manager.button("Call Group", "Ring available online group members", () -> manager.calls().callGroup(player, id)));
        if (page > 1) buttons.add(manager.button("Older", "Previous page", () -> openGroup(manager, player, id, page - 1)));
        if (page < pages) buttons.add(manager.button("Newer", "Next page", () -> openGroup(manager, player, id, page + 1)));
        buttons.add(manager.button("Export", "Choose recent or full transcript", () -> openExport(manager, player, group.messages, group.name, () -> openGroup(manager, player, id, page))));
        buttons.add(manager.button("Members", "Add, remove, or leave", () -> openGroupMembers(manager, player, id)));
        buttons.add(manager.button("Back", "Return to groups", () -> openGroups(manager, player)));
        showActions(player, group.name, (body.length() == 0 ? group.members.size() + " member(s)" : body.toString()) + "\nPage " + page + "/" + pages, buttons, 2);
    }

    private static void openGroupReply(PhoneManager manager, Player player, int id) {
        PhoneDataStore.GroupChat group = manager.store().database().groups.get(id); if (group == null) return;
        showForm(player, "Send to " + group.name, group.members.size() + " member(s) will see this.",
                List.of(DialogInput.text("message", Component.text("Message")).maxLength(256).build()), "Send", view -> {
                    String text = view.getText("message"); if (text == null || text.isBlank()) return;
                    PhoneDataStore.Message message = new PhoneDataStore.Message(); message.from = player.getUniqueId().toString();
                    message.text = text.strip(); message.sentAt = System.currentTimeMillis(); group.messages.add(message);
                    group.lastRead.put(message.from, group.messages.size()); manager.store().save();
                    for (String member : group.members) {
                        if (member.equals(message.from)) continue;
                        Player online = Bukkit.getPlayer(UUID.fromString(member));
                        PhoneDataStore.PlayerRecord recipient = manager.store().player(member);
                        if (online != null && !recipient.doNotDisturb)
                            online.sendMessage(Component.text("Phone: " + group.name + " | " + manager.displayName(message.from) + ": " + message.text, NamedTextColor.AQUA));
                        if (online != null && !recipient.doNotDisturb) playNotification(online, recipient);
                    }
                    openGroup(manager, player, id);
                }, () -> openGroup(manager, player, id));
    }

    private static void openGroupMembers(PhoneManager manager, Player player, int id) {
        PhoneDataStore.GroupChat group = manager.store().database().groups.get(id); if (group == null) return;
        String self = player.getUniqueId().toString();
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().player(self).contacts.keySet().stream().filter(uuid -> !group.members.contains(uuid))
                .forEach(uuid -> buttons.add(manager.button("Add " + manager.displayName(uuid), "Add contact to group", () -> {
                    if (group.members.size() >= 10) { player.sendMessage(Component.text("Group is full (10 members).", NamedTextColor.RED)); return; }
                    group.members.add(uuid); group.lastRead.put(uuid, group.messages.size()); manager.store().save();
                    Player online = Bukkit.getPlayer(UUID.fromString(uuid)); if (online != null) online.sendMessage(Component.text("You were added to " + group.name + ".", NamedTextColor.AQUA));
                    openGroupMembers(manager, player, id);
                })));
        if (group.owner.equals(self)) group.members.stream().filter(uuid -> !uuid.equals(self)).forEach(uuid ->
                buttons.add(manager.button("Remove " + manager.displayName(uuid), "Remove this member", () -> {
                    group.members.remove(uuid); group.lastRead.remove(uuid); manager.store().save(); openGroupMembers(manager, player, id);
                })));
        buttons.add(manager.button("Leave Group", "Leave and transfer ownership if needed", () -> leaveGroup(manager, player, id)));
        buttons.add(manager.button("Back", "Return to group", () -> openGroup(manager, player, id)));
        showActions(player, group.name + " Members", "Owner: " + manager.displayName(group.owner) + " • " + group.members.size() + "/10", buttons, 2);
    }

    private static void leaveGroup(PhoneManager manager, Player player, int id) {
        PhoneDataStore.GroupChat group = manager.store().database().groups.get(id); if (group == null) return;
        String self = player.getUniqueId().toString(); group.members.remove(self); group.lastRead.remove(self);
        if (group.members.isEmpty()) manager.store().database().groups.remove(id);
        else if (group.owner.equals(self)) group.owner = group.members.get(0);
        manager.store().save(); openGroups(manager, player);
    }
    static void openGames(PhoneManager manager, Player player) { manager.games().open(player); }

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

    static void showActions(Player player, String title, String body, List<ActionButton> buttons, int columns) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(body == null ? "" : body)))).build())
                .type(DialogType.multiAction(buttons).columns(columns).build()));
        player.showDialog(dialog);
    }

    interface FormSubmit { void accept(io.papermc.paper.dialog.DialogResponseView view); }

    static void showForm(Player player, String title, String body, List<DialogInput> inputs,
                                 String submitLabel, FormSubmit submit, Runnable back) {
        showForm(player, title, Component.text(body == null ? "" : body), inputs, submitLabel, submit, back);
    }

    static void showForm(Player player, String title, Component body, List<DialogInput> inputs,
                                 String submitLabel, FormSubmit submit, Runnable back) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(body == null ? Component.empty() : body)))
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
