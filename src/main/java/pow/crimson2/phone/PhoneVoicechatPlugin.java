package pow.crimson2.phone;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Routes phone audio separately while leaving Simple Voice Chat proximity audio intact. */
public final class PhoneVoicechatPlugin implements VoicechatPlugin, PhoneCallService {
    private static final String CATEGORY = "werepires_phone";
    private final VampireSMPPlugin plugin;
    private final PhoneManager manager;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<Integer, Call> calls = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> connected = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> ringing = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPosition> positions = new ConcurrentHashMap<>();
    private VoicechatServerApi api;
    private BukkitTask maintenance;

    PhoneVoicechatPlugin(VampireSMPPlugin plugin, PhoneManager manager) { this.plugin = plugin; this.manager = manager; }
    public String getPluginId() { return "werepires_phone_calls"; }
    public void initialize(de.maxhenkel.voicechat.api.VoicechatApi api) {}

    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender != null && sender.getPlayer() != null) {
                routeAudio(sender.getPlayer().getUuid(), event.getPacket().getOpusEncodedData());
            }
        });
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            api = event.getVoicechat();
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORY).setName("Phone Calls")
                    .setDescription("WerePires phone calls and speakerphone audio").build());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (maintenance != null) maintenance.cancel();
                maintenance = Bukkit.getScheduler().runTaskTimer(plugin, this::maintainSpeakerphones, 20L, 20L);
            });
        });
    }

    public boolean available() { return api != null; }

    public void connect(Player player) { }

    public void call(Player caller, String targetUuid) {
        Player target;
        try { target = Bukkit.getPlayer(UUID.fromString(targetUuid)); } catch (IllegalArgumentException ex) { target = null; }
        if (target == null) { caller.sendMessage("§cThat contact is offline."); return; }
        start(caller, List.of(target), manager.displayName(targetUuid));
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
                .filter(p -> !ringing.containsKey(p.getUniqueId()))
                .filter(p -> !manager.store().player(p.getUniqueId().toString()).doNotDisturb)
                .filter(p -> {
                    PhoneDataStore.Contact callerContact = manager.store().player(p.getUniqueId().toString())
                            .contacts.get(callerId.toString());
                    return callerContact == null || !callerContact.blocked;
                }).toList();
        if (availableTargets.isEmpty()) { caller.sendMessage("§cNobody is available to answer."); return; }
        int id = sequence.incrementAndGet();
        String callerName = manager.displayName(callerId.toString());
        Call call = new Call(id, callerName); calls.put(id, call);
        if (!join(caller, call, false)) { destroy(call); return; }
        for (Player target : availableTargets) {
            ringing.put(target.getUniqueId(), id);
            target.sendMessage("§b[Phone] §e" + callerName + "§f is calling. §a/answer §7or §c/decline");
        }
        caller.sendMessage("§b[Phone] §fCalling §e" + title + "§f... §7(/hangup to cancel)");
        call.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(call), 600L);
    }

    public void answer(Player player) {
        Integer id = ringing.remove(player.getUniqueId()); Call call = id == null ? null : calls.get(id);
        if (call == null) { player.sendMessage("§7You have no incoming call."); return; }
        if (!join(player, call, true)) cleanupIfEmpty(call);
    }

    private boolean join(Player player, Call call, boolean announce) {
        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection == null || !connection.isInstalled() || !connection.isConnected()) { player.sendMessage("§cSimple Voice Chat is not connected."); return false; }
        connected.put(player.getUniqueId(), call.id); call.members.add(player.getUniqueId());
        if (call.speakerOwner != null) {
            Player owner = Bukkit.getPlayer(call.speakerOwner); if (owner != null) prepareSpeakerChannels(call, owner);
        }
        if (announce) broadcast(call, "§a" + manager.displayName(player.getUniqueId().toString()) + " joined the call.");
        return true;
    }

    public void decline(Player player) {
        Integer id = ringing.remove(player.getUniqueId()); Call call = id == null ? null : calls.get(id);
        if (call == null) { player.sendMessage("§7You have no incoming call."); return; }
        broadcast(call, "§c" + manager.displayName(player.getUniqueId().toString()) + " declined."); cleanupIfEmpty(call);
    }

    public void hangup(Player player) {
        Integer ringingId = ringing.get(player.getUniqueId()); if (ringingId != null) { decline(player); return; }
        Integer id = connected.remove(player.getUniqueId()); Call call = id == null ? null : calls.get(id);
        if (call == null) { player.sendMessage("§7You are not in a call."); return; }
        UUID uuid = player.getUniqueId(); call.members.remove(uuid); call.muted.remove(uuid); call.deafened.remove(uuid);
        if (uuid.equals(call.speakerOwner)) disableSpeaker(call, "§7Speakerphone turned off because its owner left.");
        player.sendMessage("§7You left the call."); broadcast(call, "§7" + manager.displayName(uuid.toString()) + " left the call."); cleanupIfEmpty(call);
    }

    public void toggleMute(Player player) {
        Call call = callOf(player); if (call == null) { player.sendMessage("§7You are not in a call."); return; }
        boolean enabled = !call.muted.remove(player.getUniqueId()); if (enabled) call.muted.add(player.getUniqueId());
        player.sendMessage(enabled ? "§eYour microphone is muted for this call. Proximity chat still works." : "§aYour microphone is live in the call again.");
        broadcastExcept(call, player.getUniqueId(), "§7" + manager.displayName(player.getUniqueId().toString()) + (enabled ? " muted their call microphone." : " unmuted their call microphone."));
    }

    public void toggleDeafen(Player player) {
        Call call = callOf(player); if (call == null) { player.sendMessage("§7You are not in a call."); return; }
        boolean enabled = !call.deafened.remove(player.getUniqueId()); if (enabled) call.deafened.add(player.getUniqueId());
        player.sendMessage(enabled ? "§eIncoming call audio muted. Proximity chat still works." : "§aIncoming call audio restored.");
    }

    public void toggleSpeaker(Player player) {
        Call call = callOf(player); if (call == null) { player.sendMessage("§7You are not in a call."); return; }
        if (!plugin.getConfig().getBoolean("phone.calls.speaker.enabled", true)) { player.sendMessage("§cSpeakerphone is disabled."); return; }
        UUID uuid = player.getUniqueId();
        if (uuid.equals(call.speakerOwner)) { disableSpeaker(call, "§7Speakerphone turned off."); return; }
        if (call.speakerOwner != null) { player.sendMessage("§cSomeone else already has this call on speaker."); return; }
        call.speakerOwner = uuid;
        for (Player online : Bukkit.getOnlinePlayers()) {
            Location location = online.getLocation();
            positions.put(online.getUniqueId(), new PlayerPosition(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()));
        }
        prepareSpeakerChannels(call, player);
        broadcast(call, "§e" + manager.displayName(uuid.toString()) + " put the call on speaker. Nearby players can hear and speak into the call.");
    }

    public boolean isInCall(Player player) { return connected.containsKey(player.getUniqueId()); }
    public boolean isMuted(Player player) { Call call = callOf(player); return call != null && call.muted.contains(player.getUniqueId()); }
    public boolean isDeafened(Player player) { Call call = callOf(player); return call != null && call.deafened.contains(player.getUniqueId()); }
    public boolean isSpeaker(Player player) { Call call = callOf(player); return call != null && player.getUniqueId().equals(call.speakerOwner); }
    private Call callOf(Player player) { Integer id = connected.get(player.getUniqueId()); return id == null ? null : calls.get(id); }

    private void routeAudio(UUID source, byte[] audio) {
        if (audio == null || audio.length == 0) return;
            Integer ownCallId = connected.get(source);
            if (ownCallId != null) {
                Call call = calls.get(ownCallId);
                if (call != null && !call.muted.contains(source)) {
                    sendPrivate(call, source, audio, call.members);
                    if (call.speakerOwner != null && !call.speakerOwner.equals(source)) sendSpeaker(call, source, audio);
                }
            }
            for (Call call : calls.values()) {
                UUID speaker = call.speakerOwner;
                if (speaker == null || call.members.contains(source) || !isNearSpeaker(source, speaker)) continue;
                sendPrivate(call, source, audio, call.members.stream().filter(member -> !member.equals(speaker)).toList());
            }
    }

    private void sendPrivate(Call call, UUID source, byte[] audio, Collection<UUID> recipients) {
        StaticAudioChannel channel = call.privateChannels.computeIfAbsent(source, uuid -> {
            StaticAudioChannel created = api.createStaticAudioChannel(channelId("private", call.id, uuid));
            created.setCategory(CATEGORY); created.setBypassGroupIsolation(true); return created;
        });
        channel.clearTargets();
        for (UUID recipient : recipients) {
            if (recipient.equals(source) || call.deafened.contains(recipient)) continue;
            VoicechatConnection connection = api.getConnectionOf(recipient);
            if (connection != null && connection.isConnected()) channel.addTarget(connection);
        }
        channel.send(audio);
    }

    private void sendSpeaker(Call call, UUID source, byte[] audio) {
        EntityAudioChannel channel = call.speakerChannels.get(source); if (channel != null) channel.send(audio);
    }

    private boolean isNearSpeaker(UUID source, UUID speaker) {
        PlayerPosition nearby = positions.get(source), owner = positions.get(speaker);
        if (nearby == null || owner == null || !nearby.world.equals(owner.world)) return false;
        double dx = nearby.x - owner.x, dy = nearby.y - owner.y, dz = nearby.z - owner.z, radius = transmitRadius();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private float speakerRadius() { return (float)Math.max(1.0, plugin.getConfig().getDouble("phone.calls.speaker.radius", 8.0)); }
    private double transmitRadius() { return Math.max(1.0, plugin.getConfig().getDouble("phone.calls.speaker.transmit-radius", 8.0)); }

    private void maintainSpeakerphones() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation(); positions.put(player.getUniqueId(), new PlayerPosition(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ()));
        }
        for (Call call : calls.values()) if (call.speakerOwner != null) {
            Player owner = Bukkit.getPlayer(call.speakerOwner);
            if (owner == null || !call.members.contains(call.speakerOwner)) { disableSpeaker(call, "§7Speakerphone turned off."); continue; }
            prepareSpeakerChannels(call, owner);
        }
    }

    private void prepareSpeakerChannels(Call call, Player owner) {
        for (UUID source : call.members) if (!source.equals(call.speakerOwner)) call.speakerChannels.computeIfAbsent(source, uuid -> {
            EntityAudioChannel created = api.createEntityAudioChannel(channelId("speaker", call.id, uuid), api.fromEntity(owner));
            created.setCategory(CATEGORY); created.setDistance(speakerRadius());
            created.setFilter(listener -> !call.members.contains(listener.getUuid())); return created;
        });
    }

    private void disableSpeaker(Call call, String message) {
        call.speakerOwner = null; call.speakerChannels.values().forEach(EntityAudioChannel::flush); call.speakerChannels.clear(); broadcast(call, message);
    }

    public void shutdown() {
        if (maintenance != null) maintenance.cancel();
        for (Call call : new ArrayList<>(calls.values())) destroy(call);
    }

    public void disconnect(Player player) {
        UUID uuid = player.getUniqueId(); positions.remove(uuid);
        Integer ring = ringing.remove(uuid); Integer id = connected.remove(uuid); Call call = id == null ? (ring == null ? null : calls.get(ring)) : calls.get(id);
        if (call != null) { call.members.remove(uuid); call.muted.remove(uuid); call.deafened.remove(uuid); if (uuid.equals(call.speakerOwner)) disableSpeaker(call, "§7Speakerphone turned off."); cleanupIfEmpty(call); }
    }

    private void expire(Call call) {
        ringing.entrySet().removeIf(entry -> {
            if (entry.getValue() != call.id) return false;
            Player player = Bukkit.getPlayer(entry.getKey()); if (player != null) player.sendMessage("§7Missed call from §e" + call.callerName + "§7."); return true;
        }); cleanupIfEmpty(call);
    }

    private void cleanupIfEmpty(Call call) { if (call.members.size() <= 1 && !ringing.containsValue(call.id)) destroy(call); }

    private void destroy(Call call) {
        if (call.timeout != null) call.timeout.cancel();
        call.privateChannels.values().forEach(StaticAudioChannel::flush); call.speakerChannels.values().forEach(EntityAudioChannel::flush);
        for (UUID member : new ArrayList<>(call.members)) connected.remove(member);
        ringing.entrySet().removeIf(e -> e.getValue() == call.id); calls.remove(call.id);
    }

    private UUID channelId(String type, int call, UUID source) {
        return UUID.nameUUIDFromBytes((getPluginId() + ':' + type + ':' + call + ':' + source).getBytes(StandardCharsets.UTF_8));
    }

    private void broadcast(Call call, String message) { for (UUID member : call.members) { Player p = Bukkit.getPlayer(member); if (p != null) p.sendMessage("§b[Phone] " + message); } }
    private void broadcastExcept(Call call, UUID excluded, String message) { for (UUID member : call.members) if (!member.equals(excluded)) { Player p = Bukkit.getPlayer(member); if (p != null) p.sendMessage("§b[Phone] " + message); } }

    private static final class Call {
        final int id; final Set<UUID> members = ConcurrentHashMap.newKeySet();
        final Set<UUID> muted = ConcurrentHashMap.newKeySet(); final Set<UUID> deafened = ConcurrentHashMap.newKeySet();
        final Map<UUID, StaticAudioChannel> privateChannels = new ConcurrentHashMap<>();
        final Map<UUID, EntityAudioChannel> speakerChannels = new ConcurrentHashMap<>();
        final String callerName;
        volatile UUID speakerOwner; BukkitTask timeout;
        Call(int id, String callerName) { this.id = id; this.callerName = callerName; }
    }
    private record PlayerPosition(UUID world, double x, double y, double z) {}
}
