package pow.crimson2.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;

public class PowCommand implements CommandExecutor, TabCompleter {
   private final VampireSMPPlugin plugin;
   private final CommandHandler adminHandler;
   private final VampireAbilityCommand abilityCommand;
   private final WerewolfAbilityCommand werewolfAbilityCommand;
   private final TomeAbilityCommand tomeCommand;
   private final ForcedVampireCureCommand forceCureCommand;
   private final ForcedCureReopenCommand forceCureReopenCommand;
   private final HolySitesCommand beaconStatusCommand;
   private final TexturePackCommand texturePackCommand;
   private final PermadeathCommand permadeathCommand;
   private final ToggleTurningCommand turningCommand;
   private final PendingMessageCommand sendMessageCommand;
   private final PermakillCommand permakillCommand;
   private final TurnLockCommand turnLockCommand;
   private final DeathCounterCommand deathCounterCommand;
   private final FaeDealCommand faeDealCommand;
   private final FaeCommand faeCommand;
   private final AbilityCommand abilityRulesCommand;

   public PowCommand(VampireSMPPlugin plugin) {
      this.plugin = plugin;
      this.adminHandler = new CommandHandler(plugin, plugin.getSessionManager(), plugin.getVampireManager());
      this.permakillCommand = new PermakillCommand(plugin);
      this.abilityCommand = new VampireAbilityCommand(plugin);
      this.werewolfAbilityCommand = new WerewolfAbilityCommand(plugin);
      this.tomeCommand = new TomeAbilityCommand(plugin);
      this.forceCureCommand = new ForcedVampireCureCommand(plugin);
      this.forceCureReopenCommand = new ForcedCureReopenCommand(plugin);
      this.beaconStatusCommand = new HolySitesCommand(plugin);
      this.texturePackCommand = new TexturePackCommand(plugin);
      this.permadeathCommand = new PermadeathCommand(plugin);
      this.turningCommand = new ToggleTurningCommand(plugin);
      this.sendMessageCommand = new PendingMessageCommand(plugin);
      this.turnLockCommand = new TurnLockCommand(plugin);
      this.deathCounterCommand = new DeathCounterCommand(plugin);
      this.faeDealCommand = new FaeDealCommand(plugin);
      this.faeCommand = new FaeCommand(plugin);
      this.abilityRulesCommand = new AbilityCommand(plugin);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         this.sendHelp(sender);
         return true;
      }

      String subCommand = args[0].toLowerCase();
      String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
      switch (subCommand) {
         case "admin":
            return this.handleAdminCommand(sender, subArgs);
         case "vability":
            return this.abilityCommand.onCommand(sender, command, label, subArgs);
         case "wability":
            return this.werewolfAbilityCommand.onCommand(sender, command, label, subArgs);
         case "tome":
            return this.tomeCommand.onCommand(sender, command, label, subArgs);
         case "beaconstatus":
         case "holysites":
         case "holy":
            return this.beaconStatusCommand.onCommand(sender, command, label, subArgs);
         case "texture":
         case "texturepack":
         case "resourcepack":
            return this.texturePackCommand.onCommand(sender, command, label, subArgs);
         case "toggle_permadeath":
         case "toggle-permadeath":
         case "togglepermadeath":
         case "permadeath":
            return this.permadeathCommand.onCommand(sender, command, label, subArgs);
         case "permakill": {
            if (!(sender instanceof Player player)) {
               sender.sendMessage("§cOnly players can permakill themselves. Use §e/pow admin permakill <player>§c.");
               return true;
            }
            this.permakillCommand.requestSelf(player);
            return true;
         }
         case "ability":
         case "abilities":
            return this.abilityRulesCommand.onCommand(sender, command, label, subArgs);
         case "faedeal":
         case "fae":
            return this.faeDealCommand.onCommand(sender, command, label, subArgs);
         case "toggle-turning":
         case "turning":
            return this.turningCommand.onCommand(sender, command, label, subArgs);
         case "sendmessage":
         case "sendpendingmessage":
            return this.sendMessageCommand.onCommand(sender, command, label, subArgs);
         case "reopen":
         case "forcedcure-reopen":
            return this.forceCureReopenCommand.onCommand(sender, command, label, subArgs);
         case "help":
            this.sendHelp(sender);
            return true;
         default:
            sender.sendMessage("§cUnknown subcommand: " + subCommand);
            sender.sendMessage("§7Use §e/pow help §7for a list of commands");
            return true;
      }
   }

   private boolean handleAdminCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("vampiresmp.admin")) {
         sender.sendMessage("§cYou don't have permission to use admin commands.");
         return true;
      } else if (args.length == 0) {
         this.sendAdminHelp(sender);
         return true;
      } else {
         String adminSubCommand = args[0].toLowerCase();
         String[] adminArgs = Arrays.copyOfRange(args, 1, args.length);
         Command dummyCommand = new BukkitCommand(adminSubCommand) {
            public boolean execute(CommandSender sender, String commandLabel, String[] argsx) {
               return false;
            }
         };
         return this.adminHandler.onCommand(sender, dummyCommand, adminSubCommand, adminArgs);
      }
   }

   private void sendHelp(CommandSender sender) {
      sender.sendMessage("§6§l=== VampireSMP Commands ===");
      sender.sendMessage("§e/pow admin §7- Admin commands (requires permission)");
      sender.sendMessage("§e/pow vability <name> §7- Use vampire abilities");
      sender.sendMessage("§e/pow wability <name> §7- Use werewolf abilities");
      sender.sendMessage("§e/pow tome <name> §7- Use tome abilities (humans)");
      sender.sendMessage("§e/pow ability §7- Which abilities survive being turned");
      sender.sendMessage("§e/voluntate-mea-hoc-nefandum-vinculum-abicio §7- Cure yourself from vampirism");
      sender.sendMessage("§e/hoc-vinculum-tibi-dirumpo-mala-creatura <player> §7- Force cure a vampire");
      sender.sendMessage("§e/pow beaconstatus §7- Check beacon spiritual influence");
      sender.sendMessage("§e/pow texture §7- Apply VampireSMP texture pack");
      sender.sendMessage("§e/pow permadeath <on|off|absolute> §7- Set permadeath preference");
      sender.sendMessage("§e/pow permakill §7- Permanently kill yourself (asks to confirm)");
      sender.sendMessage("§e/pow toggle-turning §7- Toggle vampire turning ability");
      sender.sendMessage("§e/pow sendmessage §7- Send pending chat message");
      if (sender instanceof Player p && this.plugin.getFaeManager() != null && this.plugin.getFaeManager().isFae(p)) {
         sender.sendMessage("§d/pow faedeal §7- Fae bargains (you are fae)");
      }
   }

   private void sendAdminHelp(CommandSender sender) {
      sender.sendMessage("§6§l=== VampireSMP Admin Commands ===");
      sender.sendMessage("§e/pow admin init §7- Initialize a new game (full reset)");
      sender.sendMessage("§e/pow admin session <start|pause|end|prime|resume|building> §7- Manage session state");
      sender.sendMessage("§e/pow admin vampire <player> <human|1|2|3|turn> §7- Manage vampire status");
      sender.sendMessage("§e/pow admin werewolf <player> <human|1|2|3> §7- Manage werewolf status");
      sender.sendMessage("§e/pow admin beacon <subcommand> §7- Manage beacon sites (use tab for options)");
      sender.sendMessage("§e/pow admin vampirecooldowns <reset|clear> [player] §7- Reset vampire ability cooldowns");
      sender.sendMessage("§e/pow admin resettomecooldowns §7- Reset tome ability cooldowns for all humans");
      sender.sendMessage("§e/pow admin onehumanleft §7- Toggle One Human Left mode (no beacon cooldowns)");
      sender.sendMessage("§e/pow admin vampirehealthcheck <get|set> [ticks] §7- Configure vampire health check interval");
      sender.sendMessage("§e/pow admin barrier <lower|raise|status> §7- Toggle the Oakhurst movement barrier");
      sender.sendMessage("§e/pow admin barrier exempt <player> §7- Player can always leave the barrier");
      sender.sendMessage("§e/pow admin barrier normal <player> §7- Player follows standard barrier rules");
      sender.sendMessage("§e/pow admin barrier unexempt <player> §7- Player is always kept inside, even if barrier is lowered");
      sender.sendMessage("§e/pow admin break_warning §7- Play break warning sounds");
      sender.sendMessage("§e/pow admin givetome <player> <ability> [amount] §7- Give tome to player");
      sender.sendMessage("§e/pow admin select_tomes <player> §7- Open GUI to grant tome abilities");
      sender.sendMessage("§e/pow admin give_cure_book <player> <1|2|3|4> §7- Give cure book item to player");
      sender.sendMessage("§e/pow admin give_revival_book <player> <1|2|3|4> §7- Give Rite of Return book to player");
      sender.sendMessage("§e/pow admin distributetomes §7- Manually trigger tome distribution");
      sender.sendMessage("§e/pow admin bloodmoon <start|stop|status> §7- Force start/stop blood moon");
      sender.sendMessage("§e/pow admin clearbloodmoonbuffs <all|player> §7- Clear blood moon buffs");
      sender.sendMessage("§e/pow admin fixattributes <all|player> §7- Fix stuck attribute modifiers (health/speed)");
      sender.sendMessage("§e/pow admin removeendermen <all|toggle|status> §7- Manage enderman removal");
      sender.sendMessage("§e/pow admin damagesuppression <get|set> [percentage] §7- Configure damage suppression");
      sender.sendMessage("§e/pow admin setupplayer <player> §7- Give starter items to player");
      sender.sendMessage("§e/pow admin spawnanimals §7- Manually trigger passive mob spawning");
      sender.sendMessage("§e/pow admin addtomechest §7- Add current location as tome chest spawn");
      sender.sendMessage("§e/pow admin removetomechest §7- Remove nearest tome chest within 10 blocks");
      sender.sendMessage("§e/pow admin listtomechests §7- List all tome chest locations");
      sender.sendMessage("§e/pow admin addtomevault | removetomevault | listtomevault §7- Tome vaults (look at a block)");
      sender.sendMessage("§e/pow admin addominouscurevault | removeominouscurevault | listominouscurevaults §7- Cure-book vaults");
      sender.sendMessage("§e/pow admin resetplayer <player> §7- Fully reset player to fresh state");
      sender.sendMessage("§e/pow admin set_vampire_spawn [x y z] §7- Set vampire respawn location");
      sender.sendMessage("§e/pow admin reloadconfig §7- Reload this plugin's config.yml.");
      sender.sendMessage("§e/pow admin config §7- Open the in-game config editor (or get|set|list|reload).");
      sender.sendMessage("§e/pow admin permakill <player> §7- Permanently kill a player.");
      sender.sendMessage("§e/pow admin sire <player> §7- Get a player's sire status.");
      sender.sendMessage("§e/pow admin sire <player> clear §7- Remove a player's sire connection.");
      sender.sendMessage("§e/pow admin sire <player> <player> §7- Set a player's sire.");
      sender.sendMessage("§e/pow admin loadworld <name> §7- Load a named world template.");
      sender.sendMessage("§e/pow admin listworlds §7- List available world templates.");
      sender.sendMessage("§e/pow admin canturn <player> <vampire|werewolf> <allow|deny|status> §7- Control whether they can turn others.");
      sender.sendMessage("§e/pow admin canbeturned <player> <vampire|werewolf> <allow|deny|status> §7- Control whether they can be turned.");
      sender.sendMessage("§e/pow admin turnlocks <player> §7- Show all four turn locks for a player.");
      sender.sendMessage("§e/pow admin deaths <player> <get|set|add|remove> [n] §7- Adjust the death counter (hearts follow).");
      sender.sendMessage("§e/pow admin hearts <player> <get|give|take> [n] §7- Same counter, expressed as hearts.");
      sender.sendMessage("§e/pow admin fae <player> [add|remove|status] §7- Grant or revoke the fae tag.");
      sender.sendMessage("§e/pow admin fae list §7- List all fae and how many bargains they hold.");
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> completions = new ArrayList<>();
      if (args.length == 1) {
         List<String> subCommands = new ArrayList<>(Arrays.asList("vability", "wability", "tome", "ability", "beaconstatus", "permadeath", "toggle-turning", "help"));
         if (sender instanceof Player p && this.plugin.getFaeManager() != null && this.plugin.getFaeManager().isFae(p)) {
            subCommands.add(0, "faedeal");
         }
         if (sender.hasPermission("vampiresmp.admin")) {
            subCommands.add(0, "admin");
         }

         return subCommands.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
      } else {
         if (args[0].equalsIgnoreCase("ability") || args[0].equalsIgnoreCase("abilities")) {
            return this.abilityRulesCommand.tabComplete(Arrays.copyOfRange(args, 1, args.length));
         }

         if (args[0].equalsIgnoreCase("faedeal") || args[0].equalsIgnoreCase("fae")) {
            if (!(sender instanceof Player p) || this.plugin.getFaeManager() == null
                  || (!this.plugin.getFaeManager().isFae(p) && !sender.hasPermission("vampiresmp.admin"))) {
               return new ArrayList<>();
            }
            return this.faeDealCommand.tabComplete(Arrays.copyOfRange(args, 1, args.length));
         }

         if (args.length == 2 && args[0].equalsIgnoreCase("permadeath")) {
            List<String> permadeathOptions = Arrays.asList("on", "off", "absolute");
            return permadeathOptions.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
         }

         if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("vampiresmp.admin")) {
               return new ArrayList<>();
            }

            if (args.length == 2) {
               List<String> adminCommands = Arrays.asList(
                  "init",
                  "session",
                  "vampire",
                  "werewolf",
                  "beacon",
                  "vampirecooldowns",
                  "resettomecooldowns",
                  "onehumanleft",
                  "vampirehealthcheck",
                  "break_warning",
                  "givetome",
                  "select_tomes",
                  "give_cure_book",
                  "give_revival_book",
                  "distributetomes",
                  "clearbloodmoonbuffs",
                  "fixattributes",
                  "removeendermen",
                  "damagesuppression",
                  "setupplayer",
                  "spawnanimals",
                  "addtomechest",
                  "removetomechest",
                  "listtomechests",
                  "resetplayer",
                  "set_vampire_spawn",
                  "loadworld",
                  "listworlds",
                  "reloadconfig",
                  "config",
                  "permakill",
                  "addtomevault",
                  "removetomevault",
                  "listtomevault",
                  "addominouscurevault",
                  "removeominouscurevault",
                  "listominouscurevaults",
                  "canturn",
                  "canbeturned",
                  "turnlocks",
                  "deaths",
                  "hearts",
                  "fae",
                  "fadestatus"
               );
               return adminCommands.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }

            if (args[1].equalsIgnoreCase("fae")) {
               String[] sub = Arrays.copyOfRange(args, 2, args.length);
               return this.faeCommand.tabComplete(sub);
            }

            if (args[1].equalsIgnoreCase("canturn") || args[1].equalsIgnoreCase("canbeturned")) {
               String[] sub = Arrays.copyOfRange(args, 2, args.length);
               return this.turnLockCommand.tabComplete(sub);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("turnlocks")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args[1].equalsIgnoreCase("deaths") || args[1].equalsIgnoreCase("hearts")) {
               String[] sub = Arrays.copyOfRange(args, 2, args.length);
               return this.deathCounterCommand.tabComplete(sub, args[1].equalsIgnoreCase("hearts"));
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("session")) {
               List<String> sessionOptions = Arrays.asList("start", "pause", "end", "prime", "resume", "building");
               return sessionOptions.stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("vampire")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("vampire")) {
               List<String> vampireOptions = Arrays.asList("human", "1", "2", "3", "turn", "clearcap", "clearban");
               return vampireOptions.stream().filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("werewolf")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("werewolf")) {
               List<String> werewolfOptions = Arrays.asList("human", "1", "2", "3", "clearcap", "clearban");
               return werewolfOptions.stream().filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("loadworld")) {
               return this.plugin.getWorldManager().getAvailableWorlds().stream()
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("config")) {
               return Arrays.asList("get", "set", "list", "reload", "gui").stream()
                  .filter(s -> s.startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("config")
                  && (args[2].equalsIgnoreCase("get") || args[2].equalsIgnoreCase("set")
                      || args[2].equalsIgnoreCase("list"))) {
               return this.adminHandler.getConfigEditor().completePaths(args[3]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("beacon")) {
               List<String> beaconOptions = Arrays.asList(
                  "add",
                  "remove",
                  "list",
                  "info",
                  "stats",
                  "reload",
                  "holy",
                  "desecrated",
                  "neutral",
                  "validate",
                  "fix",
                  "refresh",
                  "cleanup",
                  "clearcooldowns",
                  "debug"
               );
               return beaconOptions.stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("beacon")) {
               String subCommand = args[2].toLowerCase();
               if (subCommand.equals("remove")
                  || subCommand.equals("delete")
                  || subCommand.equals("info")
                  || subCommand.equals("holy")
                  || subCommand.equals("desecrated")
                  || subCommand.equals("neutral")) {
                  return this.plugin
                     .getBeaconManager()
                     .getAllBeacons()
                     .stream()
                     .map(beacon -> beacon.getName())
                     .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                     .collect(Collectors.toList());
               }

               if (subCommand.equals("add")) {
                  return Arrays.asList("[name]");
               }
            }

            if (args.length == 5 && args[1].equalsIgnoreCase("beacon") && args[2].equalsIgnoreCase("add")) {
               return Arrays.asList("5", "10", "15", "20", "25", "50", "100");
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("vampirecooldowns")) {
               return Arrays.asList("reset", "clear").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("vampirecooldowns") && (args[2].equalsIgnoreCase("reset") || args[2].equalsIgnoreCase("clear"))) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("damagesuppression")) {
               return Arrays.asList("get", "set").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("damagesuppression") && args[2].equalsIgnoreCase("set")) {
               return Arrays.asList("0", "10", "25", "50", "75", "100");
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("vampirehealthcheck")) {
               return Arrays.asList("get", "set").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("vampirehealthcheck") && args[2].equalsIgnoreCase("set")) {
               return Arrays.asList("20", "40", "60", "100", "200");
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("givetome")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("givetome")) {
               List<String> tomeAbilities = Arrays.asList(
                  "blessing",
                  "banishundead",
                  "holyword",
                  "enlightenedeye",
                  "lanternthrash",
                  "prayeroffaith",
                  "rallyingcry",
                  "shoulderbarge",
                  "turnundead",
                  "uncannydirection",
                  "unnaturalhaste",
                  "wayoftheland",
                  "wayofthelumberjack",
                  "wayoftheprospector",
                  "stopthebleeding"
               );
               return tomeAbilities.stream().filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 5 && args[1].equalsIgnoreCase("givetome")) {
               return Arrays.asList("1", "5", "10", "16", "32", "64");
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("select_tomes")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("give_cure_book")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("give_cure_book")) {
               return Arrays.asList("1", "2", "3", "4").stream().filter(s -> s.startsWith(args[3])).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("clearbloodmoonbuffs")) {
               List<String> options = new ArrayList<>();
               options.add("all");
               Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
               return options.stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("resetplayer")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("resetplayer")) {
               return Arrays.asList("true", "false").stream().filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("fixattributes")) {
               List<String> options = new ArrayList<>();
               options.add("all");
               Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
               return options.stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("removeendermen")) {
               return Arrays.asList("all", "toggle", "status").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("setupplayer")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("resetplayer")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("init")) {
               return Arrays.asList("cancel").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
         }

         if (args.length == 2 && args[0].equalsIgnoreCase("vability")) {
            List<String> abilities = Arrays.asList("list", "all", "bat", "lunge", "vanish", "stormcall", "beacontravel", "vision");
            return abilities.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
         } else if (args.length == 2 && args[0].equalsIgnoreCase("wability")) {
            List<String> wabilities = Arrays.asList("list", "all", "feralcharge");
            return wabilities.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
         } else if (args.length != 2 || !args[0].equalsIgnoreCase("tome")) {
            return completions;
         } else if (!(sender instanceof Player player)) {
            return new ArrayList<>();
         } else {
            completions = new ArrayList<>();
            completions.add("list");
            completions.addAll(this.plugin.getTomeManager().getPlayerAbilities(player));
            return completions.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
         }
      }
   }
}
