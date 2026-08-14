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

    static void openCalls(PhoneManager manager, Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().player(player.getUniqueId().toString()).contacts.forEach((uuid, contact) -> {
            if (!contact.blocked) buttons.add(manager.button("Call " + manager.displayName(uuid), "Start a private voice call", () -> manager.calls().call(player, uuid)));
        });
        buttons.add(manager.button("Answer", "Answer an incoming call", () -> manager.calls().answer(player)));
        buttons.add(manager.button("Decline", "Decline an incoming call", () -> manager.calls().decline(player)));
        buttons.add(manager.button("Hang Up", "Leave or cancel a call", () -> manager.calls().hangup(player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Calls", manager.calls().available() ? "Simple Voice Chat connected" : "Voice calls unavailable", buttons, 2);
    }
    static void openSocial(PhoneManager manager, Player player) {
        String self = player.getUniqueId().toString();
        String handle = manager.store().database().handles.get(self);
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().database().posts.entrySet().stream()
                .sorted(Map.Entry.<Integer, PhoneDataStore.SocialPost>comparingByKey().reversed()).limit(12)
                .forEach(entry -> buttons.add(manager.button("@" + entry.getValue().handle,
                        entry.getValue().text, () -> openPost(manager, player, entry.getKey()))));
        buttons.add(manager.button(handle == null ? "Create Handle" : "New Post",
                handle == null ? "Choose your permanent social handle" : "Post as @" + handle,
                () -> { if (handle == null) openHandleSetup(manager, player); else openCreatePost(manager, player); }));
        if (handle != null) buttons.add(manager.button("Discover", "Follow or unfollow phone accounts", () -> openDiscover(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Social", handle == null ? "No handle configured" : "Signed in as @" + handle, buttons, 2);
    }

    private static void openDiscover(PhoneManager manager, Player player) {
        String self = player.getUniqueId().toString();
        PhoneDataStore.PlayerRecord record = manager.store().player(self);
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().database().handles.entrySet().stream().filter(e -> !e.getKey().equals(self))
                .sorted(Map.Entry.comparingByValue()).forEach(entry -> buttons.add(manager.button(
                        (record.following.contains(entry.getKey()) ? "✓ " : "") + "@" + entry.getValue(),
                        record.following.contains(entry.getKey()) ? "Click to unfollow" : "Click to follow", () -> {
                            if (!record.following.remove(entry.getKey())) record.following.add(entry.getKey());
                            manager.store().save(); openDiscover(manager, player);
                        })));
        buttons.add(manager.button("Back", "Return to social", () -> openSocial(manager, player)));
        showActions(player, "Discover", "Following " + record.following.size() + " account(s)", buttons, 2);
    }

    private static void openHandleSetup(PhoneManager manager, Player player) {
        showForm(player, "Create Handle", "Handles survive /phonereset and cannot be duplicated.",
                List.of(DialogInput.text("handle", Component.text("Handle (without @)")).maxLength(20).build()), "Create", view -> {
                    String raw = view.getText("handle");
                    String handle = raw == null ? "" : raw.strip().toLowerCase().replaceAll("[^a-z0-9_]", "");
                    if (handle.length() < 3 || manager.store().database().handles.values().stream().anyMatch(handle::equalsIgnoreCase)) {
                        player.sendMessage(Component.text("Handle must be unique and contain 3-20 letters, numbers, or underscores.", NamedTextColor.RED)); return;
                    }
                    manager.store().database().handles.put(player.getUniqueId().toString(), handle);
                    manager.store().save(); openSocial(manager, player);
                }, () -> openSocial(manager, player));
    }

    private static void openCreatePost(PhoneManager manager, Player player) {
        showForm(player, "New Post", "Share with the phone social feed.",
                List.of(DialogInput.text("post", Component.text("Post")).maxLength(240).build()), "Post", view -> {
                    String text = view.getText("post"); if (text == null || text.isBlank()) return;
                    PhoneDataStore.SocialPost post = new PhoneDataStore.SocialPost();
                    post.author = player.getUniqueId().toString(); post.handle = manager.store().database().handles.get(post.author);
                    post.text = text.strip(); post.sentAt = System.currentTimeMillis();
                    manager.store().database().posts.put(manager.store().database().nextPostId++, post);
                    manager.store().save(); openSocial(manager, player);
                }, () -> openSocial(manager, player));
    }

    private static void openPost(PhoneManager manager, Player player, int id) {
        PhoneDataStore.SocialPost post = manager.store().database().posts.get(id); if (post == null) { openSocial(manager, player); return; }
        String self = player.getUniqueId().toString();
        showActions(player, "@" + post.handle, post.text + "\n\n♥ " + post.hearts.size() + "   👎 " + post.dislikes.size(), List.of(
                manager.button(post.hearts.contains(self) ? "Remove Heart" : "Heart", "React to this post", () -> {
                    post.dislikes.remove(self); if (!post.hearts.remove(self)) post.hearts.add(self); manager.store().save(); openPost(manager, player, id);
                }),
                manager.button(post.dislikes.contains(self) ? "Remove Dislike" : "Dislike", "React to this post", () -> {
                    post.hearts.remove(self); if (!post.dislikes.remove(self)) post.dislikes.add(self); manager.store().save(); openPost(manager, player, id);
                }),
                manager.button("Back", "Return to social", () -> openSocial(manager, player))
        ), 2);
    }

    static void openGroups(PhoneManager manager, Player player) {
        String self = player.getUniqueId().toString();
        List<ActionButton> buttons = new ArrayList<>();
        manager.store().database().groups.entrySet().stream().filter(e -> e.getValue().members.contains(self))
                .forEach(e -> buttons.add(manager.button(e.getValue().name, e.getValue().members.size() + " members", () -> openGroup(manager, player, e.getKey()))));
        buttons.add(manager.button("Create Group", "Start a group chat", () -> openCreateGroup(manager, player)));
        buttons.add(manager.button("Back", "Return to the phone", () -> manager.openMain(player)));
        showActions(player, "Group Chats", "Your persistent group conversations", buttons, 2);
    }

    private static void openCreateGroup(PhoneManager manager, Player player) {
        showForm(player, "Create Group", "Create a new group chat.",
                List.of(DialogInput.text("name", Component.text("Group name")).maxLength(40).build()), "Create", view -> {
                    String name = view.getText("name"); if (name == null || name.isBlank()) return;
                    PhoneDataStore.GroupChat group = new PhoneDataStore.GroupChat();
                    group.name = name.strip(); group.owner = player.getUniqueId().toString(); group.members.add(group.owner);
                    int id = manager.store().database().nextGroupId++; manager.store().database().groups.put(id, group);
                    manager.store().save(); openGroup(manager, player, id);
                }, () -> openGroups(manager, player));
    }

    private static void openGroup(PhoneManager manager, Player player, int id) {
        PhoneDataStore.GroupChat group = manager.store().database().groups.get(id); if (group == null) { openGroups(manager, player); return; }
        String self = player.getUniqueId().toString();
        if (!group.members.contains(self)) { openGroups(manager, player); return; }
        group.lastRead.put(self, group.messages.size());
        StringBuilder body = new StringBuilder();
        group.messages.stream().skip(Math.max(0, group.messages.size() - 8)).forEach(m -> body.append(manager.displayName(m.from)).append(": ").append(m.text).append('\n'));
        manager.store().save();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(manager.button("Send Message", "Write to the group", () -> openGroupReply(manager, player, id)));
        buttons.add(manager.button("Call Group", "Ring available online group members", () -> manager.calls().callGroup(player, id)));
        buttons.add(manager.button("Members", "Add, remove, or leave", () -> openGroupMembers(manager, player, id)));
        buttons.add(manager.button("Back", "Return to groups", () -> openGroups(manager, player)));
        showActions(player, group.name, body.length() == 0 ? group.members.size() + " member(s)" : body.toString(), buttons, 2);
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
                    if (group.members.size() >= 20) { player.sendMessage(Component.text("Group is full.", NamedTextColor.RED)); return; }
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
        showActions(player, group.name + " Members", "Owner: " + manager.displayName(group.owner) + " • " + group.members.size() + "/20", buttons, 2);
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
