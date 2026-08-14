package pow.crimson2.phone;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.thralls.ThrallProfile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class PhoneManager implements Listener {
    public static final Set<String> COLORS = Set.of("black", "blue", "green", "orange", "pink", "purple", "red", "white", "yellow");
    private final VampireSMPPlugin plugin;
    private final PhoneDataStore store;
    private final NamespacedKey phoneKey;
    private final NamespacedKey colorKey;
    private PhoneCallService callService = PhoneCallService.UNAVAILABLE;

    public PhoneManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.store = new PhoneDataStore(plugin);
        this.phoneKey = new NamespacedKey(plugin, "cell_phone");
        this.colorKey = new NamespacedKey(plugin, "cell_phone_color");
    }

    public PhoneDataStore store() { return store; }
    public PhoneCallService calls() { return callService; }
    public void setCallService(PhoneCallService callService) { this.callService = callService == null ? PhoneCallService.UNAVAILABLE : callService; }

    public ItemStack createPhone(Player owner) {
        String color = store.player(owner.getUniqueId().toString()).color;
        if (!COLORS.contains(color)) color = "black";
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cell Phone", NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Right-click to open", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(phoneKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(colorKey, PersistentDataType.STRING, color);
        meta.setItemModel(NamespacedKey.minecraft("phone_" + color));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPhone(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(phoneKey, PersistentDataType.BYTE);
    }

    public boolean hasPhone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isPhone(item)) return true;
        return false;
    }

    public void givePhone(Player player) {
        player.getInventory().addItem(createPhone(player)).values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public void updateIdentity(Player player) {
        PhoneDataStore.PlayerRecord record = store.player(player.getUniqueId().toString());
        record.minecraftName = player.getName();
        String activeName = null;
        if (plugin.getThrallManager() != null) {
            ThrallProfile profile = plugin.getThrallManager().getProfile(player.getUniqueId());
            if (profile != null) activeName = profile.getActiveName();
        }
        record.characterName = activeName == null || activeName.isBlank() ? player.getName() : activeName;
        store.save();
    }

    public void openMain(Player player) {
        PhoneDataStore.PlayerRecord record = store.player(player.getUniqueId().toString());
        int unread = unreadCount(player);
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Cell Phone", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                record.characterName + (unread == 0 ? "" : "  •  " + unread + " unread"), NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(List.of(
                        button("Messages", "Read and send direct messages.", () -> PhoneUi.openMessages(this, player)),
                        button("Contacts", "Manage contacts, favorites, and blocks.", () -> PhoneUi.openContacts(this, player)),
                        button("Calls", "Start or manage a voice call.", () -> PhoneUi.openCalls(this, player)),
                        button("GPS", "Saved and shared locations.", () -> PhoneUi.openGps(this, player)),
                        button("Notes", "Private notes.", () -> PhoneUi.openNotes(this, player)),
                        button("Social", "Handles, feed, follows, and reactions.", () -> PhoneUi.openSocial(this, player)),
                        button("Group Chats", "Messages and group calls.", () -> PhoneUi.openGroups(this, player)),
                        button("Games", "Phone games and records.", () -> PhoneUi.openGames(this, player)),
                        button("Settings", "Phone appearance and notifications.", () -> PhoneUi.openSettings(this, player))
                )).columns(3).build()));
        player.showDialog(dialog);
    }

    ActionButton button(String name, String tooltip, Runnable action) {
        return ActionButton.create(Component.text(name), Component.text(tooltip), 150,
                DialogAction.customClick((view, audience) -> plugin.getServer().getScheduler().runTask(plugin, action),
                        ClickCallback.Options.builder().build()));
    }

    int unreadCount(Player player) {
        String uuid = player.getUniqueId().toString();
        int unread = 0;
        for (PhoneDataStore.Conversation conversation : store.database().conversations.values()) {
            for (PhoneDataStore.Message message : conversation.messages) {
                if (!message.read && !uuid.equals(message.from)) unread++;
            }
        }
        return unread;
    }

    String displayName(String uuid) {
        PhoneDataStore.PlayerRecord record = store.database().players.get(uuid);
        if (record == null) return uuid;
        return record.characterName == null || record.characterName.isBlank() ? record.minecraftName : record.characterName;
    }

    String conversationKey(String first, String second) {
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    public void shutdown() { callService.shutdown(); store.save(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { updateIdentity(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { callService.disconnect(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isPhone(event.getItem())) return;
        event.setCancelled(true);
        openMain(event.getPlayer());
    }
}
