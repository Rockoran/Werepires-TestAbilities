package pow.crimson2.phone;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhoneVoicechatPlugin implements VoicechatPlugin, PhoneCallService {
    private final VampireSMPPlugin plugin;
    private final PhoneManager manager;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<Integer, Call> calls = new HashMap<>();
    private final Map<UUID, Integer> connected = new HashMap<>();
    private final Map<UUID, Integer> ringing = new HashMap<>();
    private VoicechatServerApi api;

    PhoneVoicechatPlugin(VampireSMPPlugin plugin, PhoneManager manager) { this.plugin = plugin; this.manager = manager; }
    public String getPluginId() { return "werepires_phone_calls"; }
    public void initialize(de.maxhenkel.voicechat.api.VoicechatApi api) {}
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> this.api = event.getVoicechat());
    }
    public boolean available() { return api != null; }

    public void call(Player caller, String targetUuid) {
        Player target;
        try { target = Bukkit.getPlayer(UUID.fromString(targetUuid)); } catch (IllegalArgumentException ex) { target = null; }
        if (target == null) { caller.sendMessage("§cThat contact is offline."); return; }
        start(caller, List.of(target), manager.displayName(caller.getUniqueId().toString()));
    }

    public void callGroup(Player caller, int groupId) {
        PhoneDataStore.GroupChat chat = manager.store().database().groups.get(groupId);
        if (chat == null || !chat.members.contains(caller.getUniqueId().toString())) { caller.sendMessage("§cYou are not in that group."); return; }
        List<Player> targets = chat.members.stream().filter(u -> !u.equals(caller.getUniqueId().toString()))
                .map(u -> Bukkit.getPlayer(UUID.fromString(u))).filter(p -> p != null).toList();
        start(caller, targets, chat.name);
    }

    private void start(Player caller, List<Player> targets, String title) {
        if (!available()) { caller.sendMessage("§cVoice chat is not ready."); return; }
        UUID callerId = caller.getUniqueId();
        if (connected.containsKey(callerId) || ringing.containsKey(callerId)) { caller.sendMessage("§cYou are already in or receiving a call."); return; }
        List<Player> availableTargets = targets.stream().filter(p -> !connected.containsKey(p.getUniqueId()))
                .filter(p -> !ringing.containsKey(p.getUniqueId())).filter(p -> !manager.store().player(p.getUniqueId().toString()).doNotDisturb).toList();
        if (availableTargets.isEmpty()) { caller.sendMessage("§cNobody is available to answer."); return; }
        int id = sequence.incrementAndGet();
        Group voiceGroup = api.groupBuilder().setName("call-" + id).setPassword(UUID.randomUUID().toString())
                .setType(Group.Type.OPEN).setHidden(true).setPersistent(false).build();
        Call call = new Call(id, title, voiceGroup); calls.put(id, call); join(caller, call, false);
        for (Player target : availableTargets) {
            ringing.put(target.getUniqueId(), id);
            target.sendMessage("§b[Phone] §e" + manager.displayName(callerId.toString()) + "§f is calling. §a/answer §7or §c/decline");
        }
        caller.sendMessage("§b[Phone] §fCalling §e" + title + "§f... §7(/hangup to cancel)");
        call.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(call), 600L);
    }

    public void answer(Player player) {
        Integer id = ringing.remove(player.getUniqueId());
        Call call = id == null ? null : calls.get(id);
        if (call == null) { player.sendMessage("§7You have no incoming call."); return; }
        join(player, call, true);
    }

    private void join(Player player, Call call, boolean announce) {
        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection == null || !connection.isInstalled()) { player.sendMessage("§cSimple Voice Chat is not connected."); return; }
        connection.setGroup(call.group); connected.put(player.getUniqueId(), call.id); call.members.add(player.getUniqueId());
        if (announce) broadcast(call, "§a" + manager.displayName(player.getUniqueId().toString()) + " joined the call.");
    }

    public void decline(Player player) {
        Integer id = ringing.remove(player.getUniqueId());
        Call call = id == null ? null : calls.get(id);
        if (call == null) { player.sendMessage("§7You have no incoming call."); return; }
        broadcast(call, "§c" + manager.displayName(player.getUniqueId().toString()) + " declined."); cleanupIfEmpty(call);
    }

    public void hangup(Player player) {
        Integer ringingId = ringing.get(player.getUniqueId()); if (ringingId != null) { decline(player); return; }
        Integer id = connected.remove(player.getUniqueId()); Call call = id == null ? null : calls.get(id);
        if (call == null) { player.sendMessage("§7You are not in a call."); return; }
        call.members.remove(player.getUniqueId()); VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection != null) connection.setGroup(null); player.sendMessage("§7You left the call.");
        broadcast(call, "§7" + manager.displayName(player.getUniqueId().toString()) + " left the call."); cleanupIfEmpty(call);
    }

    public void shutdown() { for (Call call : new ArrayList<>(calls.values())) destroy(call); }
    public void disconnect(Player player) {
        UUID uuid = player.getUniqueId(); Integer ring = ringing.remove(uuid);
        Integer id = connected.remove(uuid); Call call = id == null ? (ring == null ? null : calls.get(ring)) : calls.get(id);
        if (call != null) { call.members.remove(uuid); cleanupIfEmpty(call); }
    }

    private void expire(Call call) {
        ringing.entrySet().removeIf(entry -> {
            if (entry.getValue() != call.id) return false;
            Player player = Bukkit.getPlayer(entry.getKey()); if (player != null) player.sendMessage("§7Missed call from §e" + call.title + "§7."); return true;
        }); cleanupIfEmpty(call);
    }

    private void cleanupIfEmpty(Call call) {
        boolean hasRingers = ringing.containsValue(call.id);
        if (call.members.size() > 1 || hasRingers) return;
        destroy(call);
    }

    private void destroy(Call call) {
        if (call.timeout != null) call.timeout.cancel();
        for (UUID member : new ArrayList<>(call.members)) {
            connected.remove(member); VoicechatConnection connection = api.getConnectionOf(member); if (connection != null) connection.setGroup(null);
        }
        ringing.entrySet().removeIf(e -> e.getValue() == call.id); api.removeGroup(call.group.getId()); calls.remove(call.id);
    }

    private void broadcast(Call call, String message) { for (UUID member : call.members) { Player p = Bukkit.getPlayer(member); if (p != null) p.sendMessage("§b[Phone] " + message); } }
    private static final class Call {
        final int id; final String title; final Group group; final List<UUID> members = new ArrayList<>(); BukkitTask timeout;
        Call(int id, String title, Group group) { this.id = id; this.title = title; this.group = group; }
    }
}
