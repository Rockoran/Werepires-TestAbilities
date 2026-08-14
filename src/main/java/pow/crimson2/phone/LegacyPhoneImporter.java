package pow.crimson2.phone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pow.crimson2.VampireSMPPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** One-time importer for CellPhone.sk's seven skript-yaml files. */
final class LegacyPhoneImporter {
    private LegacyPhoneImporter() {}

    static boolean importIfPresent(VampireSMPPlugin plugin, PhoneDataStore.PhoneDatabase db) {
        File directory = new File(plugin.getDataFolder().getParentFile(), "skript-yaml");
        File contacts = new File(directory, "phone_contacts.yml");
        File messages = new File(directory, "phone_messages.yml");
        File settings = new File(directory, "phone_settings.yml");
        File gps = new File(directory, "phone_gps.yml");
        File notes = new File(directory, "phone_notes.yml");
        File social = new File(directory, "phone_social.yml");
        File groups = new File(directory, "phone_groups.yml");
        File games = new File(directory, "phone_games.yml");
        if (!contacts.exists() && !messages.exists() && !settings.exists() && !gps.exists() && !notes.exists()
                && !social.exists() && !groups.exists() && !games.exists()) return false;
        try {
            importContacts(load(contacts), db);
            importMessages(load(messages), db);
            importSettings(load(settings), db);
            importGps(load(gps), db);
            importNotes(load(notes), db);
            importSocial(load(social), db);
            importGroups(load(groups), db);
            importGames(load(games), db);
            plugin.getLogger().info("Imported legacy CellPhone.sk YAML data into phone-data.json.");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Legacy phone import failed and will be retried next start: " + ex.getMessage());
            return false;
        }
    }

    private static YamlConfiguration load(File file) { return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration(); }
    private static ConfigurationSection section(ConfigurationSection root, String path) { return root == null ? null : root.getConfigurationSection(path); }

    private static void importContacts(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection players = section(yaml, "players"); if (players == null) return;
        for (String uuid : players.getKeys(false)) {
            PhoneDataStore.PlayerRecord player = db.players.computeIfAbsent(uuid, ignored -> new PhoneDataStore.PlayerRecord());
            player.characterName = players.getString(uuid + ".charname", player.characterName);
            player.minecraftName = players.getString(uuid + ".mcname", player.minecraftName);
            ConfigurationSection contacts = section(players, uuid + ".contacts"); if (contacts == null) continue;
            for (String target : contacts.getKeys(false)) {
                PhoneDataStore.Contact contact = player.contacts.computeIfAbsent(target, ignored -> new PhoneDataStore.Contact());
                contact.nickname = contacts.getString(target + ".nick", "");
                contact.favorite = contacts.getBoolean(target + ".fav");
                contact.blocked = contacts.getBoolean(target + ".blocked");
            }
        }
    }

    private static void importMessages(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection conversations = section(yaml, "conversations"); if (conversations == null) return;
        for (String legacyKey : conversations.getKeys(false)) {
            PhoneDataStore.Conversation conversation = db.conversations.computeIfAbsent(canonicalConversation(legacyKey), ignored -> new PhoneDataStore.Conversation());
            ConfigurationSection messages = section(conversations, legacyKey + ".messages"); if (messages == null) continue;
            messages.getKeys(false).stream().sorted(Comparator.comparingInt(LegacyPhoneImporter::number)).forEach(index -> {
                PhoneDataStore.Message message = new PhoneDataStore.Message();
                message.from = messages.getString(index + ".from", ""); message.text = messages.getString(index + ".text", "");
                message.sentAt = parseTime(messages.get(index + ".time")); message.read = messages.getBoolean(index + ".read"); conversation.messages.add(message);
            });
        }
    }

    private static void importSettings(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection players = section(yaml, "players"); if (players == null) return;
        for (String uuid : players.getKeys(false)) {
            PhoneDataStore.PlayerRecord player = db.players.computeIfAbsent(uuid, ignored -> new PhoneDataStore.PlayerRecord());
            player.doNotDisturb = players.getBoolean(uuid + ".dnd"); player.silent = players.getBoolean(uuid + ".silent");
            player.vibrate = players.getBoolean(uuid + ".vibrate"); player.noRefresh = players.getBoolean(uuid + ".norefresh");
            player.color = players.getString(uuid + ".color", "purple").toLowerCase(Locale.ROOT);
        }
    }

    private static void importGps(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection players = section(yaml, "players"); if (players == null) return;
        for (String uuid : players.getKeys(false)) {
            ConfigurationSection points = section(players, uuid + ".locations"); if (points == null) continue;
            PhoneDataStore.PlayerRecord player = db.players.computeIfAbsent(uuid, ignored -> new PhoneDataStore.PlayerRecord());
            for (String index : points.getKeys(false)) {
                PhoneDataStore.GpsPoint point = new PhoneDataStore.GpsPoint(); point.name = points.getString(index + ".name", "Pin " + index);
                point.world = points.getString(index + ".world", "world"); point.x = points.getDouble(index + ".x"); point.y = points.getDouble(index + ".y"); point.z = points.getDouble(index + ".z");
                point.shared = points.getBoolean(index + ".shared"); point.sharedBy = points.getString(index + ".sharedby"); player.gps.add(point);
            }
        }
    }

    private static void importNotes(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection players = section(yaml, "players"); if (players == null) return;
        for (String uuid : players.getKeys(false)) {
            ConfigurationSection notes = section(players, uuid + ".notes"); if (notes == null) continue;
            PhoneDataStore.PlayerRecord player = db.players.computeIfAbsent(uuid, ignored -> new PhoneDataStore.PlayerRecord());
            for (String index : notes.getKeys(false)) {
                PhoneDataStore.Note note = new PhoneDataStore.Note(); note.title = notes.getString(index + ".title", "Untitled");
                note.body = notes.getString(index + ".body", ""); note.pinned = notes.getBoolean(index + ".pinned"); note.editedAt = parseTime(notes.get(index + ".edited")); player.notes.add(note);
            }
        }
    }

    private static void importSocial(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection handles = section(yaml, "handles");
        if (handles != null) for (String lower : handles.getKeys(false)) {
            String identity = identity(handles.getString(lower + ".key", "")); String handle = handles.getString(lower + ".handle", lower);
            if (!identity.isBlank()) db.handles.put(identity, handle);
        }
        ConfigurationSection follows = section(yaml, "follows");
        if (follows != null) for (String followerKey : follows.getKeys(false)) {
            String follower = identity(followerKey); PhoneDataStore.PlayerRecord record = db.players.computeIfAbsent(follower, ignored -> new PhoneDataStore.PlayerRecord());
            ConfigurationSection followed = section(follows, followerKey); if (followed == null) continue;
            for (String handle : followed.getKeys(false)) if (followed.getBoolean(handle)) findHandleOwner(db, handle).ifPresent(record.following::add);
        }
        ConfigurationSection posts = section(yaml, "posts"); if (posts == null) return;
        for (String index : posts.getKeys(false)) {
            PhoneDataStore.SocialPost post = new PhoneDataStore.SocialPost(); post.handle = posts.getString(index + ".handle", "unknown");
            post.author = identity(posts.getString(index + ".key", "")); post.text = posts.getString(index + ".text", ""); post.sentAt = parseTime(posts.get(index + ".time"));
            int id = number(index); db.posts.put(id, post); db.nextPostId = Math.max(db.nextPostId, id + 1);
        }
    }

    private static java.util.Optional<String> findHandleOwner(PhoneDataStore.PhoneDatabase db, String handle) {
        return db.handles.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(handle)).map(java.util.Map.Entry::getKey).findFirst();
    }

    private static void importGroups(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection groups = section(yaml, "groups"); if (groups == null) return;
        for (String rawId : groups.getKeys(false)) {
            int id = number(rawId); PhoneDataStore.GroupChat group = new PhoneDataStore.GroupChat(); group.name = groups.getString(rawId + ".name", "Group " + id);
            group.owner = groups.getString(rawId + ".owner", ""); ConfigurationSection members = section(groups, rawId + ".members");
            if (members != null) for (String uuid : members.getKeys(false)) if (members.getBoolean(uuid)) group.members.add(uuid);
            ConfigurationSection reads = section(groups, rawId + ".read"); if (reads != null) for (String uuid : reads.getKeys(false)) group.lastRead.put(uuid, reads.getInt(uuid));
            PhoneDataStore.Conversation thread = db.conversations.remove("grp_" + id); if (thread != null) group.messages.addAll(thread.messages);
            db.groups.put(id, group); db.nextGroupId = Math.max(db.nextGroupId, id + 1);
        }
    }

    private static void importGames(YamlConfiguration yaml, PhoneDataStore.PhoneDatabase db) {
        ConfigurationSection blackjack = section(yaml, "bj"); if (blackjack != null) for (String key : blackjack.getKeys(false)) {
            PhoneDataStore.GameRecord game = db.games.computeIfAbsent(key, ignored -> new PhoneDataStore.GameRecord());
            game.name = blackjack.getString(key + ".name", "Unknown"); game.chips = blackjack.getInt(key + ".chips", 100); game.bestChips = blackjack.getInt(key + ".best", game.chips);
        }
        ConfigurationSection pvp = section(yaml, "pvp"); if (pvp != null) for (String key : pvp.getKeys(false)) {
            PhoneDataStore.GameRecord game = db.games.computeIfAbsent(key, ignored -> new PhoneDataStore.GameRecord()); game.name = pvp.getString(key + ".name", game.name);
            game.wins = pvp.getInt(key + ".wins"); game.losses = pvp.getInt(key + ".losses"); game.streak = pvp.getInt(key + ".streak"); game.bestStreak = pvp.getInt(key + ".best");
        }
        ConfigurationSection wordle = section(yaml, "wd"); if (wordle != null) for (String key : wordle.getKeys(false)) {
            PhoneDataStore.GameRecord game = db.games.computeIfAbsent(key, ignored -> new PhoneDataStore.GameRecord()); game.wordleStreak = wordle.getInt(key + ".streak"); game.wordleBestStreak = wordle.getInt(key + ".best");
        }
    }

    private static String canonicalConversation(String key) {
        if (key.length() == 73 && key.charAt(36) == '_') { String a = key.substring(0, 36), b = key.substring(37); return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a; }
        return key;
    }
    private static String identity(String key) { int pipe = key.indexOf('|'); if (pipe > 0) return key.substring(0, pipe); return key; }
    private static int number(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; } }
    private static long parseTime(Object value) { if (value instanceof Number number) return number.longValue(); return System.currentTimeMillis(); }
}
