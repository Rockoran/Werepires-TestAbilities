package pow.crimson2.kit;

import java.io.File;
import java.util.*;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import pow.crimson2.VampireSMPPlugin;

/**
 * Logic and persistence for the Starter Kit system.
 *
 * Per-player data (YAML: plugins/WerePires/starterkit_data.yml):
 *   - disabled category set (absence = enabled, presence = disabled)
 *   - character selection ("Lucifer", "Marlow", "Flynnley", "Ludwig", "Worven", "none")
 *   - log preference (e.g. "SPRUCE_LOG")
 *   - stone stack allocation per type
 *   - food kit flag
 *
 * Config (config.yml section "starterkit"):
 *   add-tools, add-food, add-bread, add-torches, add-book, add-armor,
 *   add-builder, add-vampire-exp, add-player-specific-items,
 *   jowoker-custom-enabled, lucifer-custom-enabled, marlow-custom-enabled,
 *   flynnley-custom-enabled, ludwig-custom-enabled, worven-custom-enabled,
 *   join-kit-delay (ticks), builder-max-stacks,
 *   broadcast-kitstart, broadcast-kitstop, broadcast-kitall, broadcast-kitgive
 */
public class StarterKitManager {

    // Stone type keys (internal)
    public static final String[] STONE_TYPES  = {"cobble", "deepslate", "tuff", "andesite", "diorite", "granite"};
    // Stone Material mapping
    public static final Map<String, Material> STONE_MATERIALS;
    // Log type keys shown in GUI → Material
    public static final Map<String, Material> LOG_MATERIALS;

    static {
        Map<String, Material> sm = new LinkedHashMap<>();
        sm.put("cobble",    Material.COBBLESTONE);
        sm.put("deepslate", Material.COBBLED_DEEPSLATE);
        sm.put("tuff",      Material.TUFF);
        sm.put("andesite",  Material.ANDESITE);
        sm.put("diorite",   Material.DIORITE);
        sm.put("granite",   Material.GRANITE);
        STONE_MATERIALS = Collections.unmodifiableMap(sm);

        Map<String, Material> lm = new LinkedHashMap<>();
        lm.put("SPRUCE_LOG",    Material.SPRUCE_LOG);
        lm.put("OAK_LOG",       Material.OAK_LOG);
        lm.put("DARK_OAK_LOG",  Material.DARK_OAK_LOG);
        lm.put("BIRCH_LOG",     Material.BIRCH_LOG);
        lm.put("JUNGLE_LOG",    Material.JUNGLE_LOG);
        lm.put("ACACIA_LOG",    Material.ACACIA_LOG);
        lm.put("MANGROVE_LOG",  Material.MANGROVE_LOG);
        lm.put("CHERRY_LOG",    Material.CHERRY_LOG);
        LOG_MATERIALS = Collections.unmodifiableMap(lm);
    }

    public static String stoneDisplayName(String key) {
        switch (key) {
            case "cobble":    return "Cobblestone";
            case "deepslate": return "Cobbled Deepslate";
            case "tuff":      return "Tuff";
            case "andesite":  return "Andesite";
            case "diorite":   return "Diorite";
            case "granite":   return "Granite";
            default:          return key;
        }
    }

    public static String logDisplayName(String key) {
        switch (key) {
            case "SPRUCE_LOG":   return "Spruce Log";
            case "OAK_LOG":      return "Oak Log";
            case "DARK_OAK_LOG": return "Dark Oak Log";
            case "BIRCH_LOG":    return "Birch Log";
            case "JUNGLE_LOG":   return "Jungle Log";
            case "ACACIA_LOG":   return "Acacia Log";
            case "MANGROVE_LOG": return "Mangrove Log";
            case "CHERRY_LOG":   return "Cherry Log";
            default:             return key;
        }
    }

    // =========================================================================

    private final VampireSMPPlugin plugin;
    private File dataFile;

    /** Per-UUID: set of disabled category keys */
    private final Map<UUID, Set<String>>         disabled   = new HashMap<>();
    /** Per-UUID: character ("Lucifer", "none", etc.) */
    private final Map<UUID, String>               character  = new HashMap<>();
    /** Per-UUID: log material key (e.g. "SPRUCE_LOG") */
    private final Map<UUID, String>               logPref    = new HashMap<>();
    /** Per-UUID: stone key → stack count */
    private final Map<UUID, Map<String, Integer>> stonePref  = new HashMap<>();
    /** UUIDs with the food-kit add-on */
    private final Set<UUID>                       foodKitSet = new HashSet<>();

    /** Global toggle for auto-kit on first join */
    private boolean starterKitEnabled = false;

    public StarterKitManager(VampireSMPPlugin plugin) {
        this.plugin   = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.dataFile = new File(plugin.getDataFolder(), "starterkit_data.yml");
        loadData();
    }

    // =========================================================================
    // Config helpers
    // =========================================================================

    private boolean cfg(String key, boolean def) {
        return plugin.getConfig().getBoolean("starterkit." + key, def);
    }

    public boolean isEnabled()             { return starterKitEnabled; }
    public void    setEnabled(boolean e)   { starterKitEnabled = e; saveData(); }

    public boolean addTools()              { return cfg("add-tools",                 true); }
    public boolean addFood()               { return cfg("add-food",                  true); }
    public boolean addBread()              { return cfg("add-bread",                 true); }
    public boolean addTorches()            { return cfg("add-torches",               true); }
    public boolean addBook()               { return cfg("add-book",                  true); }
    public boolean addArmor()              { return cfg("add-armor",                 true); }
    public boolean addBuilder()            { return cfg("add-builder",               true); }
    public boolean addVampireExp()         { return cfg("add-vampire-exp",           true); }
    public boolean addPlayerSpecific()     { return cfg("add-player-specific-items", true); }

    public boolean jowokerEnabled()        { return cfg("jowoker-custom-enabled",    true); }
    public boolean luciferEnabled()        { return cfg("lucifer-custom-enabled",    true); }
    public boolean marlowEnabled()         { return cfg("marlow-custom-enabled",     true); }
    public boolean flynnleyEnabled()       { return cfg("flynnley-custom-enabled",   true); }
    public boolean ludwigEnabled()         { return cfg("ludwig-custom-enabled",     true); }
    public boolean worvenEnabled()         { return cfg("worven-custom-enabled",     true); }

    public int     joinKitDelay()          { return plugin.getConfig().getInt("starterkit.join-kit-delay", 2); }
    public int     builderMaxStacks()      { return plugin.getConfig().getInt("starterkit.builder-max-stacks", 4); }

    public boolean broadcastKitStart()     { return cfg("broadcast-kitstart", true); }
    public boolean broadcastKitStop()      { return cfg("broadcast-kitstop",  true); }
    public boolean broadcastKitAll()       { return cfg("broadcast-kitall",   true); }
    public boolean broadcastKitGive()      { return cfg("broadcast-kitgive", false); }

    // =========================================================================
    // Preferences
    // =========================================================================

    public boolean isCatEnabled(UUID uuid, String cat) {
        Set<String> d = disabled.get(uuid);
        return d == null || !d.contains(cat);
    }

    public void setCatEnabled(UUID uuid, String cat, boolean enabled) {
        Set<String> d = disabled.computeIfAbsent(uuid, k -> new HashSet<>());
        if (enabled) d.remove(cat); else d.add(cat);
        saveData();
    }

    public void toggleCat(UUID uuid, String cat) {
        setCatEnabled(uuid, cat, !isCatEnabled(uuid, cat));
    }

    public String getCharacter(UUID uuid) {
        return character.getOrDefault(uuid, "none");
    }

    public void setCharacter(UUID uuid, String char_) {
        character.put(uuid, char_);
        saveData();
    }

    public String getLogPref(UUID uuid) {
        return logPref.getOrDefault(uuid, "SPRUCE_LOG");
    }

    public void setLogPref(UUID uuid, String key) {
        logPref.put(uuid, key);
        saveData();
    }

    public int getStoneStacks(UUID uuid, String type) {
        Map<String, Integer> m = stonePref.get(uuid);
        return m == null ? 0 : m.getOrDefault(type, 0);
    }

    public int getStoneTotal(UUID uuid) {
        Map<String, Integer> m = stonePref.get(uuid);
        if (m == null) return 0;
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void setStoneStacks(UUID uuid, String type, int stacks) {
        stonePref.computeIfAbsent(uuid, k -> new HashMap<>()).put(type, stacks);
        if (stacks == 0) stonePref.get(uuid).remove(type);
        saveData();
    }

    public boolean hasFoodKit(UUID uuid)         { return foodKitSet.contains(uuid); }
    public void    addFoodKit(UUID uuid)          { foodKitSet.add(uuid);    saveData(); }
    public void    removeFoodKit(UUID uuid)       { foodKitSet.remove(uuid); saveData(); }

    // =========================================================================
    // Character detection
    // =========================================================================

    public static boolean hasCharacters(Player p) {
        String n = p.getName();
        return "DeathAzHere".equals(n) || "Yenushi".equals(n) || "BratDog_".equals(n);
    }

    public static List<String> getCharactersFor(Player p) {
        switch (p.getName()) {
            case "DeathAzHere": return Arrays.asList("Flynnley", "Lucifer", "Marlow");
            case "Yenushi":     return Collections.singletonList("Ludwig");
            case "BratDog_":    return Collections.singletonList("Worven");
            default:            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Kit giving
    // =========================================================================

    public void giveFoodKit(Player p) {
        p.getInventory().addItem(new ItemStack(Material.BREAD, 16));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16));
        p.getInventory().addItem(new ItemStack(Material.BEEF, 16));
        p.getInventory().addItem(new ItemStack(Material.APPLE, 8));
        p.sendMessage("§eFood Kit §ahas been given to you.");
    }

    public void giveStarterKit(Player p) {
        UUID uuid = p.getUniqueId();

        if (addTools() && isCatEnabled(uuid, "tools")) {
            p.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.STONE_SHOVEL));
            p.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        }

        if (addFood() && isCatEnabled(uuid, "food")) {
            p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16));
            p.getInventory().addItem(new ItemStack(Material.BEEF, 16));
            if (addBread() && isCatEnabled(uuid, "bread")) {
                p.getInventory().addItem(new ItemStack(Material.BREAD, 8));
            }
        }

        if (addBook() && isCatEnabled(uuid, "book")) {
            p.getInventory().addItem(new ItemStack(Material.WRITABLE_BOOK));
        }

        if (addTorches() && isCatEnabled(uuid, "torches")) {
            p.getInventory().addItem(new ItemStack(Material.TORCH, 16));
        }

        if (addVampireExp() && isCatEnabled(uuid, "vampexp")) {
            if (p.getScoreboardTags().contains("vampire")) {
                p.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
            }
        }

        if (addBuilder() && isCatEnabled(uuid, "builder")) {
            // Logs
            String logKey = getLogPref(uuid);
            Material logMat = LOG_MATERIALS.getOrDefault(logKey, Material.SPRUCE_LOG);
            p.getInventory().addItem(new ItemStack(logMat, 64));

            // Stone
            int total = getStoneTotal(uuid);
            if (total <= 0) {
                // Default: 2 cobblestone + 2 cobbled deepslate
                p.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64));
                p.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64));
                p.getInventory().addItem(new ItemStack(Material.COBBLED_DEEPSLATE, 64));
                p.getInventory().addItem(new ItemStack(Material.COBBLED_DEEPSLATE, 64));
            } else {
                for (String type : STONE_TYPES) {
                    int stacks = getStoneStacks(uuid, type);
                    if (stacks > 0) {
                        Material mat = STONE_MATERIALS.get(type);
                        p.getInventory().addItem(new ItemStack(mat, stacks * 64));
                    }
                }
            }
        }

        if (addArmor() && isCatEnabled(uuid, "armor")) {
            p.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            p.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            p.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            p.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        }

        if (hasFoodKit(uuid)) {
            giveFoodKit(p);
        }

        if (addPlayerSpecific()) {
            givePlayerSpecificItems(p);
        }

        p.sendMessage("§6Starter Kit §ahas been given to you.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void givePlayerSpecificItems(Player p) {
        String name = p.getName();
        String ch   = getCharacter(p.getUniqueId());

        // Jowoker (by player name, no character profile)
        if ("Jowoker".equals(name) && jowokerEnabled()) {
            // Remove default stone sword first
            p.getInventory().remove(Material.STONE_SWORD);
            p.getInventory().addItem(namedItem(Material.IRON_SWORD, "§7Silver Sword",
                    "§7Since given sword fighting lessons as payment",
                    "§7for an extermination job, Jade has found",
                    "§7using a sword easier for him to use",
                    "§7while hunting as opposed to bow and arrow",
                    "§7and hunting rifle."));
            p.sendMessage("§7You received your custom item: §fSilver Sword");
        }

        // Lucifer (DeathAzHere)
        if ("Lucifer".equals(ch) && luciferEnabled()) {
            p.getInventory().remove(Material.STONE_SWORD);
            p.getInventory().addItem(namedItem(Material.IRON_SWORD, "§7Silver Dagger",
                    "§7A compact silvered blade meant for close combat.",
                    "§7Reliable in a fight and easy to carry."));
            p.getInventory().addItem(new ItemStack(Material.FLINT_AND_STEEL));
            p.getInventory().addItem(namedItem(Material.WRITABLE_BOOK, "§fA Letter"));
            p.sendMessage("§7You received your custom items: §fSilver Dagger§7, §fFlint and Steel§7, §fA Letter");
        }

        // Marlow (DeathAzHere)
        if ("Marlow".equals(ch) && marlowEnabled()) {
            p.getInventory().addItem(namedItem(Material.BRUSH, "§7Paint Brush"));
            p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE, 6));
            p.sendMessage("§7You received your custom items: §fPaint Brush§7 and §f6 Paint Bottles");
        }

        // Flynnley (DeathAzHere)
        if ("Flynnley".equals(ch) && flynnleyEnabled()) {
            p.getInventory().addItem(new ItemStack(Material.TRIDENT));
            ItemStack helm = namedItem(Material.GOLDEN_HELMET, "§7Headpiece");
            ItemMeta  hm   = helm.getItemMeta();
            if (hm != null) {
                hm.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
                hm.addEnchant(Enchantment.BINDING_CURSE,  1, true);
                helm.setItemMeta(hm);
            }
            p.getInventory().setHelmet(helm);
            // Permanent conduit power (very long duration)
            p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, Integer.MAX_VALUE, 0, true, false));
            p.sendMessage("§7You received your custom items: §fTrident§7 and §fHeadpiece");
            p.sendMessage("§7You have been granted: §fConduit Power");
        }

        // Ludwig (Yenushi)
        if ("Ludwig".equals(ch) && ludwigEnabled()) {
            p.getInventory().addItem(new ItemStack(Material.CROSSBOW));
            p.getInventory().addItem(new ItemStack(Material.ARROW, 16));
            p.sendMessage("§7You received your custom items: §fCrossbow§7 and §f16 Arrows");
        }

        // Worven (BratDog_)
        if ("Worven".equals(ch) && worvenEnabled()) {
            p.getInventory().addItem(namedItem(Material.PAPER, "§fLetter"));
            p.getInventory().addItem(namedItem(Material.PAPER, "§fLetter"));
            p.getInventory().addItem(namedItem(Material.PAPER, "§fLetter"));
            p.getInventory().addItem(namedItem(Material.OAK_SLAB, "§fWooden Package"));
            p.sendMessage("§7You received your custom items: §f3x Letter§7, §fWooden Package");
        }
    }

    private static ItemStack namedItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", starterKitEnabled);

        for (Map.Entry<UUID, Set<String>> e : disabled.entrySet()) {
            String path = "players." + e.getKey() + ".";
            yaml.set(path + "disabled", new ArrayList<>(e.getValue()));
        }
        for (Map.Entry<UUID, String> e : character.entrySet()) {
            yaml.set("players." + e.getKey() + ".character", e.getValue());
        }
        for (Map.Entry<UUID, String> e : logPref.entrySet()) {
            yaml.set("players." + e.getKey() + ".log", e.getValue());
        }
        for (Map.Entry<UUID, Map<String, Integer>> e : stonePref.entrySet()) {
            String base = "players." + e.getKey() + ".stone.";
            for (Map.Entry<String, Integer> se : e.getValue().entrySet()) {
                yaml.set(base + se.getKey(), se.getValue());
            }
        }
        for (UUID uid : foodKitSet) {
            yaml.set("players." + uid + ".food-kit", true);
        }

        try { yaml.save(dataFile); }
        catch (Exception e) {
            plugin.getLogger().warning("StarterKitManager: save failed: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);

        starterKitEnabled = yaml.getBoolean("enabled", false);

        if (!yaml.contains("players")) return;
        for (String uuidStr : Objects.requireNonNull(yaml.getConfigurationSection("players")).getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "players." + uuidStr;

                List<String> dis = yaml.getStringList(path + ".disabled");
                if (!dis.isEmpty()) disabled.put(uuid, new HashSet<>(dis));

                String ch = yaml.getString(path + ".character", null);
                if (ch != null) character.put(uuid, ch);

                String log = yaml.getString(path + ".log", null);
                if (log != null) logPref.put(uuid, log);

                if (yaml.contains(path + ".stone")) {
                    Map<String, Integer> sm = new HashMap<>();
                    for (String type : STONE_TYPES) {
                        int v = yaml.getInt(path + ".stone." + type, 0);
                        if (v > 0) sm.put(type, v);
                    }
                    if (!sm.isEmpty()) stonePref.put(uuid, sm);
                }

                if (yaml.getBoolean(path + ".food-kit", false)) {
                    foodKitSet.add(uuid);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("StarterKitManager: failed loading " + uuidStr + ": " + ex.getMessage());
            }
        }
        plugin.logInfo("StarterKitManager: data loaded.");
    }

    public void shutdown() {
        saveData();
    }
}
