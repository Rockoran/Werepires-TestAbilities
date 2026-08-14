package pow.crimson2.phone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pow.crimson2.VampireSMPPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable, UUID-keyed storage for the native phone implementation. */
public final class PhoneDataStore {
    private final VampireSMPPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private PhoneDatabase database;

    public PhoneDataStore(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("phone-data.json");
        load();
    }

    public synchronized PhoneDatabase database() {
        return database;
    }

    public synchronized PlayerRecord player(String uuid) {
        return database.players.computeIfAbsent(uuid, ignored -> new PlayerRecord());
    }

    public synchronized void load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                database = new PhoneDatabase();
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                database = gson.fromJson(reader, PhoneDatabase.class);
            }
            if (database == null) database = new PhoneDatabase();
            database.normalize();
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load phone-data.json: " + ex.getMessage());
            database = new PhoneDatabase();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(database, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save phone-data.json: " + ex.getMessage());
        }
    }

    public synchronized void resetSessionData() {
        Map<String, String> handles = new HashMap<>(database.handles);
        database = new PhoneDatabase();
        database.handles.putAll(handles);
        save();
    }

    public static final class PhoneDatabase {
        public int schemaVersion = 1;
        public Map<String, PlayerRecord> players = new HashMap<>();
        public Map<String, Conversation> conversations = new HashMap<>();
        public Map<Integer, GroupChat> groups = new LinkedHashMap<>();
        public Map<Integer, SocialPost> posts = new LinkedHashMap<>();
        public Map<String, String> handles = new HashMap<>();
        public Map<String, GameRecord> games = new HashMap<>();
        public Map<Integer, GameMatch> matches = new LinkedHashMap<>();
        public int nextGroupId = 1;
        public int nextPostId = 1;
        public int nextMatchId = 1;

        void normalize() {
            if (players == null) players = new HashMap<>();
            if (conversations == null) conversations = new HashMap<>();
            if (groups == null) groups = new LinkedHashMap<>();
            if (posts == null) posts = new LinkedHashMap<>();
            if (handles == null) handles = new HashMap<>();
            if (games == null) games = new HashMap<>();
            if (matches == null) matches = new LinkedHashMap<>();
        }
    }

    public static final class PlayerRecord {
        public String minecraftName = "";
        public String characterName = "";
        public String color = "black";
        public boolean doNotDisturb;
        public boolean silent;
        public boolean vibrate;
        public boolean noRefresh;
        public Map<String, Contact> contacts = new LinkedHashMap<>();
        public List<GpsPoint> gps = new ArrayList<>();
        public List<Note> notes = new ArrayList<>();
        public List<String> following = new ArrayList<>();
    }

    public static final class Contact {
        public String nickname = "";
        public boolean favorite;
        public boolean blocked;
    }

    public static final class Conversation {
        public List<Message> messages = new ArrayList<>();
    }

    public static final class Message {
        public String from;
        public String text;
        public long sentAt;
        public boolean read;
    }

    public static final class GpsPoint {
        public String name;
        public String world;
        public double x;
        public double y;
        public double z;
        public boolean shared;
        public String sharedBy;
    }

    public static final class Note {
        public String title;
        public String body;
        public boolean pinned;
        public long editedAt;
    }

    public static final class GroupChat {
        public String name;
        public String owner;
        public List<String> members = new ArrayList<>();
        public List<Message> messages = new ArrayList<>();
        public Map<String, Integer> lastRead = new HashMap<>();
    }

    public static final class SocialPost {
        public String author;
        public String handle;
        public String text;
        public long sentAt;
        public List<String> hearts = new ArrayList<>();
        public List<String> dislikes = new ArrayList<>();
    }

    public static final class GameRecord {
        public String name = "Unknown";
        public int wins;
        public int losses;
        public int streak;
        public int bestStreak;
        public int chips = 100;
        public int bestChips = 100;
        public int memoryBestRound;
        public int wordleStreak;
        public int wordleBestStreak;
    }

    public static final class GameMatch {
        public int id;
        public String game;
        public String challenger;
        public String opponent;
        public String challengerUuid;
        public String opponentUuid;
        public String state = "pending";
        public String turn = "challenger";
        public int wager;
        public List<String> board = new ArrayList<>();
        public String challengerPick;
        public String opponentPick;
        public int challengerScore;
        public int opponentScore;
        public String note;
    }
}
