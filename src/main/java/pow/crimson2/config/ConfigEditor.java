package pow.crimson2.config;

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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-game editor for the plugin's {@code config.yml}. Generic over every key — no per-setting
 * maintenance. Exposed through {@code /pow admin config}:
 *
 * <pre>
 *   /pow admin config                 → open the Dialog editor (players)
 *   /pow admin config get  &lt;path&gt;     → print a value
 *   /pow admin config set  &lt;path&gt; &lt;v&gt; → change a value (saved + reloaded)
 *   /pow admin config list [prefix]   → list keys (optionally under a prefix)
 *   /pow admin config reload          → re-read config.yml
 * </pre>
 *
 * Values keep their existing type (boolean/int/long/double/string/list). After a change we save
 * to disk and run the same reload the {@code reloadconfig} command uses so managers pick it up.
 */
@SuppressWarnings("UnstableApiUsage")
public class ConfigEditor {

    private static final int PAGE_SIZE = 7;

    /**
     * Dialog layout. {@code multiAction} defaults to TWO columns, which paired a 320-wide setting
     * button with a 150-wide nav button on the same row and made the menu look ragged at every GUI
     * scale. One column with a single uniform width keeps rows aligned and gives long
     * "key = value" labels room to breathe.
     */
    private static final int COLUMNS = 1;
    private static final int BUTTON_WIDTH = 400;
    /** Max label length before "key = value" is truncated. Scales with BUTTON_WIDTH. */
    private static final int VALUE_CHARS = 40;

    private final VampireSMPPlugin plugin;

    public ConfigEditor(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Command entry ─────────────────────────────────────────────────────────

    /** Handle {@code /pow admin config ...}. {@code args} excludes the "config" token. */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                openConfigMenu(p);
            } else {
                sender.sendMessage("§eUsage: /pow admin config <get|set|list|reload>  (GUI is player-only)");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui", "menu", "open", "edit" -> {
                if (sender instanceof Player p) openConfigMenu(p);
                else sender.sendMessage("§cThe config GUI is player-only. Use get/set/list/reload.");
            }
            case "get" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /pow admin config get <path>"); return true; }
                String path = args[1];
                if (isHidden(path)) { sender.sendMessage("§cThat config path is protected."); return true; }
                if (!plugin.getConfig().contains(path)) {
                    sender.sendMessage("§cNo such config path: §e" + path);
                    return true;
                }
                Object v = plugin.getConfig().get(path);
                sender.sendMessage("§e" + path + " §7= §f" + render(v) + " §8(" + typeName(v) + ")");
            }
            case "set" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /pow admin config set <path> <value>"); return true; }
                String path = args[1];
                if (isHidden(path)) { sender.sendMessage("§cThat config path is protected."); return true; }
                String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                applyValue(sender, path, raw);
            }
            case "list" -> listKeys(sender, args.length >= 2 ? args[1] : "");
            case "reload" -> { reload(); sender.sendMessage("§aReloaded config.yml from disk."); }
            default -> sender.sendMessage("§cUnknown sub-command. Use: get | set | list | reload | gui");
        }
        return true;
    }

    /** Keys hidden from the config editor — contain sensitive credentials. */
    private static boolean isHidden(String path) {
        return path.equals("werepires-network") || path.startsWith("werepires-network.");
    }

    /** Path suggestions for tab-completion (all keys, sections included). */
    public List<String> completePaths(String partial) {
        String low = partial == null ? "" : partial.toLowerCase();
        return plugin.getConfig().getKeys(true).stream()
                .filter(k -> !isHidden(k))
                .filter(k -> k.toLowerCase().startsWith(low))
                .sorted()
                .limit(50)
                .collect(Collectors.toList());
    }

    // ── Core get/set ──────────────────────────────────────────────────────────

    /** Parse {@code raw} to the path's current type, set, save and reload. Returns success. */
    private boolean applyValue(CommandSender sender, String path, String raw) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.contains(path)) {
            sender.sendMessage("§cNo such config path: §e" + path
                    + "§c. Only existing keys can be edited (prevents typo keys).");
            return false;
        }
        Object current = cfg.get(path);
        Object parsed;
        try {
            parsed = parseToType(current, raw);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cCouldn't read '§e" + raw + "§c' as §e" + typeName(current) + "§c.");
            return false;
        }
        cfg.set(path, parsed);
        plugin.saveConfig();
        reload();
        sender.sendMessage("§aSet §e" + path + " §a= §f" + render(parsed) + " §7(saved + reloaded)");
        return true;
    }

    private Object parseToType(Object current, String raw) {
        String t = raw.trim();
        if (current instanceof Boolean) return parseBool(t);
        if (current instanceof Integer) return Integer.parseInt(t);
        if (current instanceof Long)    return Long.parseLong(t);
        if (current instanceof Double)  return Double.parseDouble(t);
        if (current instanceof Float)   return Double.parseDouble(t); // normalise to double
        if (current instanceof List) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return raw; // string (or unknown) — store verbatim
    }

    private static boolean parseBool(String s) {
        return switch (s.toLowerCase()) {
            case "true", "yes", "on", "1"  -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new IllegalArgumentException("not a boolean: " + s);
        };
    }

    /** Re-read config the same way {@code /pow admin reloadconfig} does. */
    private void reload() {
        if (plugin.getConfigManager() != null) plugin.getConfigManager().loadConfig();
        if (plugin.getTomeDistributionManager() != null) plugin.getTomeDistributionManager().reloadConfig();
    }

    private void listKeys(CommandSender sender, String prefix) {
        List<String> leaves = leafPaths(prefix);
        if (leaves.isEmpty()) {
            sender.sendMessage("§7No config keys" + (prefix.isEmpty() ? "." : " under §e" + prefix + "§7."));
            return;
        }
        sender.sendMessage("§6§lConfig keys" + (prefix.isEmpty() ? "" : " under §e" + prefix)
                + " §7(" + leaves.size() + ")");
        int shown = 0;
        for (String path : leaves) {
            if (shown++ >= 60) {
                sender.sendMessage("§8…and " + (leaves.size() - 60) + " more. Narrow with a prefix.");
                break;
            }
            sender.sendMessage("§e" + path + " §7= §f" + render(plugin.getConfig().get(path)));
        }
    }

    /** All leaf (non-section) keys, optionally filtered to those starting with {@code prefix}. */
    private List<String> leafPaths(String prefix) {
        String low = prefix == null ? "" : prefix.toLowerCase();
        FileConfiguration cfg = plugin.getConfig();
        return cfg.getKeys(true).stream()
                .filter(k -> !cfg.isConfigurationSection(k))
                .filter(k -> !isHidden(k))
                .filter(k -> low.isEmpty() || k.toLowerCase().startsWith(low))
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Dialog UI ─────────────────────────────────────────────────────────────

    /** Convenience entry point — open the editor at the top level. */
    public void openConfigMenu(Player p) {
        openConfigMenu(p, "", 0);
    }

    /**
     * Browse the config tree one level at a time. {@code basePath} is the section being viewed
     * ("" = root). Categories (sections) drill in; settings (leaves) open the edit dialog.
     * Example: root shows "Abilities ▸", clicking it shows "Vampire ▸ / Human ▸ / Werewolf ▸",
     * and clicking one of those shows its individual settings.
     */
    public void openConfigMenu(Player p, String basePath, int page) {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection section = basePath.isEmpty() ? cfg : cfg.getConfigurationSection(basePath);

        List<String> children = new ArrayList<>();
        if (section != null) section.getKeys(false).stream()
                .filter(k -> !isHidden(child(basePath, k)))
                .forEach(children::add);
        // Categories (folders) first, then settings; each group alphabetical.
        children.sort((a, b) -> {
            boolean as = cfg.isConfigurationSection(child(basePath, a));
            boolean bs = cfg.isConfigurationSection(child(basePath, b));
            if (as != bs) return as ? -1 : 1;
            return a.compareToIgnoreCase(b);
        });

        int totalPages = Math.max(1, (children.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, children.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String key = children.get(i);
            String full = child(basePath, key);
            if (cfg.isConfigurationSection(full)) {
                int count = countLeaves(full);
                buttons.add(ActionButton.create(
                        Component.text(prettify(key) + "  ▸", NamedTextColor.AQUA),
                        Component.text(count + " setting" + (count == 1 ? "" : "s")),
                        BUTTON_WIDTH,
                        DialogAction.customClick((v, a) -> sync(() -> openConfigMenu(p, full, 0)),
                                ClickCallback.Options.builder().build())));
            } else {
                Object val = cfg.get(full);
                Component label = Component.text(prettify(key), NamedTextColor.YELLOW)
                        .append(Component.text("  =  " + shorten(render(val)), NamedTextColor.WHITE));
                buttons.add(ActionButton.create(
                        label,
                        Component.text(full + " (" + typeName(val) + ")"),
                        BUTTON_WIDTH,
                        DialogAction.customClick((v, a) -> sync(() -> openEditDialog(p, full)),
                                ClickCallback.Options.builder().build())));
            }
        }

        final int pg = page, tp = totalPages;
        final String base = basePath;
        if (page > 0) {
            buttons.add(ActionButton.create(Component.text("◀ Previous page"), null, BUTTON_WIDTH,
                    DialogAction.customClick((v, a) -> sync(() -> openConfigMenu(p, base, pg - 1)),
                            ClickCallback.Options.builder().build())));
        }
        if (page < totalPages - 1) {
            buttons.add(ActionButton.create(Component.text("Next page ▶"), null, BUTTON_WIDTH,
                    DialogAction.customClick((v, a) -> sync(() -> openConfigMenu(p, base, pg + 1)),
                            ClickCallback.Options.builder().build())));
        }
        if (!basePath.isEmpty()) {
            buttons.add(ActionButton.create(Component.text("⤴ Back", NamedTextColor.GRAY), null, BUTTON_WIDTH,
                    DialogAction.customClick((v, a) -> sync(() -> openConfigMenu(p, parentPath(base), 0)),
                            ClickCallback.Options.builder().build())));
        }
        buttons.add(ActionButton.create(
                Component.text("⟳ Reload from disk", NamedTextColor.AQUA), null, BUTTON_WIDTH,
                DialogAction.customClick((v, a) -> sync(() -> {
                    reload();
                    p.sendMessage(Component.text("Config reloaded from disk.", NamedTextColor.AQUA));
                    openConfigMenu(p, base, pg);
                }), ClickCallback.Options.builder().build())));

        String title = basePath.isEmpty() ? "Config" : "Config ▸ " + breadcrumb(basePath);
        String bodyText = (tp > 1 ? "Page " + (pg + 1) + "/" + tp + " — " : "")
                + "Click a category to open it, or a setting to edit it.";
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(bodyText))))
                        .build())
                .type(DialogType.multiAction(buttons).columns(COLUMNS).build()));
        p.showDialog(d);
    }

    /** Edit one key. Boolean → toggle input; everything else → text input parsed to its type. */
    public void openEditDialog(Player p, String path) {
        Object current = plugin.getConfig().get(path);
        boolean isBool = current instanceof Boolean;

        List<DialogInput> inputs = new ArrayList<>();
        if (isBool) {
            inputs.add(DialogInput.bool("value", Component.text("Value"))
                    .initial((Boolean) current).build());
        } else {
            inputs.add(DialogInput.text("value", Component.text("Value"))
                    .initial(render(current))
                    .maxLength(512)
                    .width(320)
                    .build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.create(
                Component.text("Save", NamedTextColor.GREEN), null, BUTTON_WIDTH,
                DialogAction.customClick((view, aud) -> {
                    String raw = isBool ? String.valueOf(view.getBoolean("value")) : view.getText("value");
                    sync(() -> {
                        if (raw != null) applyValue(p, path, raw);
                        openEditDialog(p, path);
                    });
                }, ClickCallback.Options.builder().build())));
        buttons.add(ActionButton.create(
                Component.text("⤴ Back"), null, BUTTON_WIDTH,
                DialogAction.customClick((v, a) -> sync(() -> openConfigMenu(p, parentPath(path), 0)),
                        ClickCallback.Options.builder().build())));

        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text(breadcrumb(path), NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Path: " + path + "\nType: " + typeName(current)
                                        + "\nCurrent: " + render(current)
                                        + (isBool ? "" : "\n(For lists, separate items with commas.)")))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons).columns(COLUMNS).build()));
        p.showDialog(d);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    /** Join a section path and a child key ("" base → just the key). */
    private static String child(String base, String key) {
        return base.isEmpty() ? key : base + "." + key;
    }

    /** The parent section of a path ("abilities.vampire.x" → "abilities.vampire"; top-level → ""). */
    private static String parentPath(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? "" : path.substring(0, i);
    }

    /** Count the editable (leaf) settings anywhere under a section. */
    private int countLeaves(String sectionPath) {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection s = cfg.getConfigurationSection(sectionPath);
        if (s == null) return 0;
        int n = 0;
        for (String k : s.getKeys(true)) {
            if (!cfg.isConfigurationSection(sectionPath + "." + k)) n++;
        }
        return n;
    }

    /** "vanish-cooldown" / "vampire_spawn" → "Vanish Cooldown" / "Vampire Spawn". */
    private static String prettify(String key) {
        String[] words = key.replace('-', ' ').replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        return sb.length() == 0 ? key : sb.toString();
    }

    /** "abilities.vampire.vanish-cooldown" → "Abilities ▸ Vampire ▸ Vanish Cooldown". */
    private static String breadcrumb(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("\\.")) {
            if (sb.length() > 0) sb.append(" ▸ ");
            sb.append(prettify(part));
        }
        return sb.toString();
    }

    private static String render(Object v) {
        if (v == null) return "(unset)";
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(v);
    }

    private static String shorten(String s) {
        return s.length() <= VALUE_CHARS ? s : s.substring(0, VALUE_CHARS - 3) + "…";
    }

    private static String typeName(Object v) {
        if (v == null) return "unset";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof Integer) return "int";
        if (v instanceof Long) return "long";
        if (v instanceof Double || v instanceof Float) return "decimal";
        if (v instanceof List) return "list";
        return "text";
    }
}
