package pow.crimson2.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.beacons.BeaconSite;

public class BrigadierCommands {
  private final VampireSMPPlugin plugin;
  private final PowCommand powCommand;
  private final VampireCureCommand cureCommand;
  private final ForcedVampireCureCommand forcedCureCommand;
  private final RevivalCommand revivalCommand;
  private static final List<String> VAMPIRE_ABILITIES = Arrays.asList("bat", "lunge", "vanish", "stormcall", "beacontravel", "vision",
          "mistform", "sanguinebite", "hypnoticgaze", "bloodscent", "crimsonveil", "callswarm", "sirescommand");
  private static final List<String> WEREWOLF_ABILITIES = Arrays.asList("feralcharge");
  private static final List<String> TOME_ABILITIES = Arrays.asList(
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
          "stopthebleeding",
          "consecrateground",
          "blessedblade",
          "daybreak",
          "sanctuary",
          "cleansingsmoke",
          "huntersmark",
          "lastvigil",
          "firebreath",
          "magicfirebreath",
          "scrying",
          "fading"
  );

  private static final List<String> FAE_SPECIES = Arrays.asList("vampire", "werewolf");

  public BrigadierCommands(VampireSMPPlugin plugin) {
    this.plugin = plugin;
    this.powCommand = new PowCommand(plugin);
    this.cureCommand = new VampireCureCommand(plugin);
    this.forcedCureCommand = new ForcedVampireCureCommand(plugin);
    this.revivalCommand = new RevivalCommand(plugin);
  }

  public void registerAll() {
    this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
      Commands commands = (Commands) event.registrar();
      this.registerPowCommand(commands);
      this.registerLatinCureCommand(commands);
      this.registerLatinForcedCureCommand(commands);
      this.registerLatinRevivalCommand(commands);
      this.plugin.logInfo("All Brigadier commands registered successfully!");
    });
  }

  private void registerPowCommand(Commands commands) {
    commands.register(
            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal(
                            "pow"
                    )
                    .then(Commands.literal("help").executes(ctx -> this.executePowCommand(ctx, "help")))
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("vability")
                                    .then(
                                            Commands.literal("list")
                                                    .executes(ctx -> this.executePowCommand(ctx, "vability", "list"))
                                    ))
                                    .then(
                                            Commands.literal("all")
                                                    .executes(ctx -> this.executePowCommand(ctx, "vability", "all"))
                                    ))
                                    .then(
                                            Commands.argument("ability", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> this.suggestVampireAbilities(builder))
                                                    .executes(ctx -> {
                                                      String ability = StringArgumentType.getString(ctx, "ability");
                                                      return this.executePowCommand(ctx, "vability", ability);
                                                    })
                                    )
                    )
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("wability")
                                    .then(
                                            Commands.literal("list")
                                                    .executes(ctx -> this.executePowCommand(ctx, "wability", "list"))
                                    ))
                                    .then(
                                            Commands.literal("all")
                                                    .executes(ctx -> this.executePowCommand(ctx, "wability", "all"))
                                    ))
                                    .then(
                                            Commands.argument("wability", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> this.suggestWerewolfAbilities(builder))
                                                    .executes(ctx -> {
                                                      String ability = StringArgumentType.getString(ctx, "wability");
                                                      return this.executePowCommand(ctx, "wability", ability);
                                                    })
                                    )
                    )
                    .then(
                            ((LiteralArgumentBuilder) Commands.literal("tome")
                                    .then(
                                            Commands.literal("list")
                                                    .executes(ctx -> this.executePowCommand(ctx, "tome", "list"))
                                    ))
                                    .then(
                                            ((RequiredArgumentBuilder) Commands.argument("ability", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> this.suggestTomeAbilities(ctx, builder))
                                                    .executes(ctx -> {
                                                      String ability = StringArgumentType.getString(ctx, "ability");
                                                      return this.executePowCommand(ctx, "tome", ability);
                                                    }))
                                                    // Tomes that take a target (Scrying needs one, Fading takes an
                                                    // optional opacity). Without this node Brigadier ends the command
                                                    // at the ability name, so there is nothing to tab-complete and a
                                                    // trailing argument is rejected as bad syntax - TomeAbilityCommand
                                                    // forwards it happily, but its Bukkit completer never runs while
                                                    // /pow is served by Brigadier.
                                                    .then(
                                                            Commands.argument("target", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestTomeAbilityTargets(ctx, builder))
                                                                    .executes(ctx -> {
                                                                      String ability = StringArgumentType.getString(ctx, "ability");
                                                                      String target = StringArgumentType.getString(ctx, "target");
                                                                      return this.executePowCommand(ctx, "tome", ability, target);
                                                                    })
                                                    )
                                    )
                    )
                    .then(Commands.literal("beaconstatus").executes(ctx -> this.executePowCommand(ctx, "beaconstatus"))))
                    .then(Commands.literal("holysites").executes(ctx -> this.executePowCommand(ctx, "beaconstatus"))))
                    .then(Commands.literal("holy").executes(ctx -> this.executePowCommand(ctx, "beaconstatus"))))
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal(
                                            "texture"
                                    )
                                    .executes(ctx -> this.executePowCommand(ctx, "texture")))
                                    .then(Commands.literal("all").executes(ctx -> this.executePowCommand(ctx, "texture", "all"))))
                                    .then(Commands.literal("force").executes(ctx -> this.executePowCommand(ctx, "texture", "force"))))
                                    .then(
                                            Commands.literal("vampire").executes(ctx -> this.executePowCommand(ctx, "texture", "vampire"))
                                    ))
                                    .then(Commands.literal("human").executes(ctx -> this.executePowCommand(ctx, "texture", "human")))
                    ))
                    .then(Commands.literal("texturepack").executes(ctx -> this.executePowCommand(ctx, "texture"))))
                    .then(Commands.literal("resourcepack").executes(ctx -> this.executePowCommand(ctx, "texture")))
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("permadeath")
                                    .then(Commands.literal("on").executes(ctx -> this.executePowCommand(ctx, "permadeath", "on"))))
                                    .then(Commands.literal("off").executes(ctx -> this.executePowCommand(ctx, "permadeath", "off"))))
                                    .then(Commands.literal("absolute").executes(ctx -> this.executePowCommand(ctx, "permadeath", "absolute")))
                    )
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("toggle-permadeath")
                                    .then(Commands.literal("on").executes(ctx -> this.executePowCommand(ctx, "permadeath", "on"))))
                                    .then(Commands.literal("off").executes(ctx -> this.executePowCommand(ctx, "permadeath", "off"))))
                                    .then(Commands.literal("absolute").executes(ctx -> this.executePowCommand(ctx, "permadeath", "absolute")))
                    )
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("toggle_permadeath")
                                    .then(Commands.literal("on").executes(ctx -> this.executePowCommand(ctx, "permadeath", "on"))))
                                    .then(Commands.literal("off").executes(ctx -> this.executePowCommand(ctx, "permadeath", "off"))))
                                    .then(Commands.literal("absolute").executes(ctx -> this.executePowCommand(ctx, "permadeath", "absolute")))
                    )
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("togglepermadeath")
                                    .then(Commands.literal("on").executes(ctx -> this.executePowCommand(ctx, "permadeath", "on"))))
                                    .then(Commands.literal("off").executes(ctx -> this.executePowCommand(ctx, "permadeath", "off"))))
                                    .then(Commands.literal("absolute").executes(ctx -> this.executePowCommand(ctx, "permadeath", "absolute")))
                    )
                    .then(Commands.literal("toggle-turning").executes(ctx -> this.executePowCommand(ctx, "toggle-turning")))
                    .then(Commands.literal("turning").executes(ctx -> this.executePowCommand(ctx, "toggle-turning")))
                    .then(Commands.literal("permakill").executes(ctx -> this.executePowCommand(ctx, "permakill")))
                    .then(Commands.literal("sendmessage").executes(ctx -> this.executePowCommand(ctx, "sendmessage")))
                    .then(Commands.literal("sendpendingmessage").executes(ctx -> this.executePowCommand(ctx, "sendmessage")))
                    .then(Commands.literal("reopen").executes(ctx -> this.executePowCommand(ctx, "reopen"))))
                    .then(Commands.literal("forcedcure-reopen").executes(ctx -> this.executePowCommand(ctx, "reopen")))
                    .then(
                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal(
                                            "admin"
                                    )
                                    .requires(
                                            source -> source.getSender()
                                                    .hasPermission("vampiresmp.admin")
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("init")
                                                    .executes(
                                                            ctx -> this.executePowCommand(ctx, "admin", "init")
                                                    ))
                                                    .then(
                                                            Commands.literal("cancel")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "init", "cancel"
                                                                            )
                                                                    )
                                                    )
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal(
                                                            "session"
                                                    )
                                                    .then(
                                                            Commands.literal("start")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "session", "start"
                                                                            )
                                                                    )
                                                    ))
                                                    .then(
                                                            Commands.literal("pause")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "session", "pause"
                                                                            )
                                                                    )
                                                    ))
                                                    .then(
                                                            Commands.literal("end")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "session", "end"
                                                                            )
                                                                    )
                                                    ))
                                                    .then(
                                                            Commands.literal("prime")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "session", "prime"
                                                                            )
                                                                    )
                                                    ))
                                                    .then(
                                                            Commands.literal("resume")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "session", "resume"
                                                                            )
                                                                    )
                                                    ))
                                                    .then(
                                                            Commands.literal("building")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "session", "building"
                                                                            )
                                                                    )
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("vampire")
                                                    .then(
                                                            ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument(
                                                                            "player", StringArgumentType.word()
                                                                    )
                                                                    .suggests(
                                                                            (ctx, builder) -> this.suggestOnlinePlayers(
                                                                                    builder
                                                                            )
                                                                    )
                                                                    .then(
                                                                            Commands.literal("human")
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(
                                                                                              ctx, "player"
                                                                                      );
                                                                                      return this.executePowCommand(
                                                                                              ctx,
                                                                                              "admin",
                                                                                              "vampire",
                                                                                              player,
                                                                                              "human"
                                                                                      );
                                                                                    })
                                                                    ))
                                                                    .then(Commands.literal("1").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(
                                                                              ctx, "player"
                                                                      );
                                                                      return this.executePowCommand(
                                                                              ctx, "admin", "vampire", player, "1"
                                                                      );
                                                                    })))
                                                                    .then(Commands.literal("2").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(
                                                                              ctx, "player"
                                                                      );
                                                                      return this.executePowCommand(
                                                                              ctx, "admin", "vampire", player, "2"
                                                                      );
                                                                    })))
                                                                    .then(Commands.literal("3").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(
                                                                              ctx, "player"
                                                                      );
                                                                      return this.executePowCommand(
                                                                              ctx, "admin", "vampire", player, "3"
                                                                      );
                                                                    })))
                                                                    .then(
                                                                            ((LiteralArgumentBuilder) Commands.literal("turn")
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(
                                                                                              ctx, "player"
                                                                                      );
                                                                                      return this.executePowCommand(
                                                                                              ctx, "admin", "vampire", player, "turn"
                                                                                      );
                                                                                    }))
                                                                                    .then(
                                                                                            Commands.argument(
                                                                                                            "turner", StringArgumentType.word()
                                                                                                    )
                                                                                                    .suggests(
                                                                                                            (ctx, builder) -> this.suggestOnlinePlayers(
                                                                                                                    builder
                                                                                                            )
                                                                                                    )
                                                                                                    .executes(ctx -> {
                                                                                                      String player = StringArgumentType.getString(
                                                                                                              ctx, "player"
                                                                                                      );
                                                                                                      String turner = StringArgumentType.getString(
                                                                                                              ctx, "turner"
                                                                                                      );
                                                                                                      return this.executePowCommand(
                                                                                                              ctx,
                                                                                                              "admin",
                                                                                                              "vampire",
                                                                                                              player,
                                                                                                              "turn",
                                                                                                              turner
                                                                                                      );
                                                                                                    })
                                                                                    )
                                                                    ))
                                                                    .then(Commands.literal("clearcap").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(
                                                                              ctx, "player"
                                                                      );
                                                                       return this.executePowCommand(
                                                                               ctx, "admin", "vampire", player, "clearcap"
                                                                       );
                                                                     })))
                                                                    .then(Commands.literal("clearban").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(
                                                                              ctx, "player"
                                                                      );
                                                                       return this.executePowCommand(
                                                                               ctx, "admin", "vampire", player, "clearban"
                                                                       );
                                                                     }))
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("werewolf")
                                                    .then(
                                                            ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument(
                                                                            "player", StringArgumentType.word()
                                                                    )
                                                                    .suggests(
                                                                            (ctx, builder) -> this.suggestOnlinePlayers(builder)
                                                                    )
                                                                    .then(
                                                                            Commands.literal("human")
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                                      return this.executePowCommand(ctx, "admin", "werewolf", player, "human");
                                                                                    })
                                                                    ))
                                                                    .then(Commands.literal("1").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "werewolf", player, "1");
                                                                    })))
                                                                    .then(Commands.literal("2").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "werewolf", player, "2");
                                                                    })))
                                                                    .then(Commands.literal("3").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "werewolf", player, "3");
                                                                    })))
                                                                    .then(Commands.literal("clearcap").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "werewolf", player, "clearcap");
                                                                    }))
                                                                    .then(Commands.literal("clearban").executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "werewolf", player, "clearban");
                                                                    }))
                                                    )
                                    ))
                                    .then(this.buildBeaconSubcommand()))
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("vampirecooldowns")
                                                    .then(
                                                            ((LiteralArgumentBuilder) Commands.literal("reset")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "vampirecooldowns", "reset"
                                                                            )
                                                                    ))
                                                                    .then(
                                                                            Commands.argument("player", StringArgumentType.word())
                                                                                    .suggests(
                                                                                            (ctx, builder) -> this.suggestOnlinePlayers(builder)
                                                                                    )
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(
                                                                                              ctx, "player"
                                                                                      );
                                                                                      return this.executePowCommand(
                                                                                              ctx, "admin", "vampirecooldowns", "reset", player
                                                                                      );
                                                                                    })
                                                                    )
                                                    ))
                                                    .then(
                                                            ((LiteralArgumentBuilder) Commands.literal("clear")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "vampirecooldowns", "clear"
                                                                            )
                                                                    ))
                                                                    .then(
                                                                            Commands.argument("player", StringArgumentType.word())
                                                                                    .suggests(
                                                                                            (ctx, builder) -> this.suggestOnlinePlayers(builder)
                                                                                    )
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(
                                                                                              ctx, "player"
                                                                                      );
                                                                                      return this.executePowCommand(
                                                                                              ctx, "admin", "vampirecooldowns", "clear", player
                                                                                      );
                                                                                    })
                                                                    )
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("resettomecooldowns")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "resettomecooldowns"))
                                    ))
                                    .then(
                                            Commands.literal("onehumanleft")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "onehumanleft"))
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("vampirehealthcheck")
                                                    .then(
                                                            Commands.literal("get")
                                                                    .executes(
                                                                            ctx -> this.executePowCommand(
                                                                                    ctx, "admin", "vampirehealthcheck", "get"
                                                                            )
                                                                    )
                                                    ))
                                                    .then(
                                                            Commands.literal("set")
                                                                    .then(
                                                                            Commands.argument("ticks", IntegerArgumentType.integer(1, 1000))
                                                                                    .executes(ctx -> {
                                                                                      int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                                                                      return this.executePowCommand(
                                                                                              ctx,
                                                                                              "admin",
                                                                                              "vampirehealthcheck",
                                                                                              "set",
                                                                                              String.valueOf(ticks)
                                                                                      );
                                                                                    })
                                                                    )
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("break_warning")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "break_warning"))
                                    ))
                                    .then(
                                            Commands.literal("givetome")
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .then(
                                                                            ((RequiredArgumentBuilder) Commands.argument(
                                                                                            "ability", StringArgumentType.word()
                                                                                    )
                                                                                    .suggests((ctx, builder) -> this.suggestAllTomeAbilities(builder))
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                                      String ability = StringArgumentType.getString(ctx, "ability");
                                                                                      return this.executePowCommand(
                                                                                              ctx, "admin", "givetome", player, ability
                                                                                      );
                                                                                    }))
                                                                                    .then(
                                                                                            Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                                                                    .executes(ctx -> {
                                                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                                                      String ability = StringArgumentType.getString(ctx, "ability");
                                                                                                      int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                                                                      return this.executePowCommand(
                                                                                                              ctx,
                                                                                                              "admin",
                                                                                                              "givetome",
                                                                                                              player,
                                                                                                              ability,
                                                                                                              String.valueOf(amount)
                                                                                                      );
                                                                                                    })
                                                                                    )
                                                                    )
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("select_tomes")
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "select_tomes", player);
                                                                    })
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("give_cure_book")
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .then(
                                                                            Commands.argument("book_number", IntegerArgumentType.integer(1, 4))
                                                                                    .executes(ctx -> {
                                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                                      int bookNum = IntegerArgumentType.getInteger(ctx, "book_number");
                                                                                      return this.executePowCommand(
                                                                                              ctx, "admin", "give_cure_book", player, String.valueOf(bookNum)
                                                                                      );
                                                                                    })
                                                                    )
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("distributetomes")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "distributetomes"))
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("bloodmoon")
                                                    .then(Commands.literal("start")
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "bloodmoon", "start"))))
                                                    .then(Commands.literal("stop")
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "bloodmoon", "stop"))))
                                                    .then(Commands.literal("status")
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "bloodmoon", "status")))
                                    )
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("clearbloodmoonbuffs")
                                                    .then(
                                                            Commands.literal("all")
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "clearbloodmoonbuffs", "all"))
                                                    ))
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "clearbloodmoonbuffs", player);
                                                                    })
                                                    )
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("fixattributes")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "fixattributes")))
                                                    .then(
                                                            Commands.literal("all")
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "fixattributes", "all"))
                                                    ))
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "fixattributes", player);
                                                                    })
                                                    )
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("removeendermen")
                                                    .then(
                                                            Commands.literal("all")
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "removeendermen", "all"))
                                                    ))
                                                    .then(
                                                            Commands.literal("toggle")
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "removeendermen", "toggle"))
                                                    ))
                                                    .then(
                                                            Commands.literal("status")
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "removeendermen", "status"))
                                                    )
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("damagesuppression")
                                                    .then(
                                                            Commands.literal("get")
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "damagesuppression", "get"))
                                                    ))
                                                    .then(
                                                            Commands.literal("set")
                                                                    .then(Commands.argument("percentage", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                                                                      int percentage = IntegerArgumentType.getInteger(ctx, "percentage");
                                                                      return this.executePowCommand(ctx, "admin", "damagesuppression", "set", String.valueOf(percentage));
                                                                    }))
                                                    )
                                    ))
                                    .then(
                                            Commands.literal("setupplayer")
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "setupplayer", player);
                                                                    })
                                                    )
                                    ))
                                    .then(Commands.literal("spawnanimals").executes(ctx -> this.executePowCommand(ctx, "admin", "spawnanimals"))))
                                    .then(Commands.literal("addtomechest").executes(ctx -> this.executePowCommand(ctx, "admin", "addtomechest"))))
                                    .then(Commands.literal("removetomechest").executes(ctx -> this.executePowCommand(ctx, "admin", "removetomechest"))))
                                    .then(Commands.literal("listtomechests").executes(ctx -> this.executePowCommand(ctx, "admin", "listtomechests"))))
                                    .then(Commands.literal("addtomevault").executes(ctx -> this.executePowCommand(ctx, "admin", "addtomevault")))
                                    .then(Commands.literal("removetomevault").executes(ctx -> this.executePowCommand(ctx, "admin", "removetomevault")))
                                    .then(Commands.literal("listtomevault").executes(ctx -> this.executePowCommand(ctx, "admin", "listtomevault")))
                                    .then(Commands.literal("listtomevaults").executes(ctx -> this.executePowCommand(ctx, "admin", "listtomevaults")))
                                    .then(Commands.literal("addominouscurevault").executes(ctx -> this.executePowCommand(ctx, "admin", "addominouscurevault")))
                                    .then(Commands.literal("removeominouscurevault").executes(ctx -> this.executePowCommand(ctx, "admin", "removeominouscurevault")))
                                    .then(Commands.literal("listominouscurevault").executes(ctx -> this.executePowCommand(ctx, "admin", "listominouscurevault")))
                                    .then(Commands.literal("listominouscurevaults").executes(ctx -> this.executePowCommand(ctx, "admin", "listominouscurevaults")))
                                    .then(Commands.literal("give_revival_book")
                                            .then(Commands.argument("player", StringArgumentType.word())
                                                    .suggests((ctx, b) -> this.suggestOnlinePlayers(b))
                                                    .then(Commands.argument("book", IntegerArgumentType.integer(1, 4))
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "give_revival_book",
                                                                    StringArgumentType.getString(ctx, "player"),
                                                                    String.valueOf(IntegerArgumentType.getInteger(ctx, "book")))))))
                                    .then(
                                            Commands.literal("resetplayer")
                                                    .then(
                                                            ((RequiredArgumentBuilder) Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      return this.executePowCommand(ctx, "admin", "resetplayer", player);
                                                                    }))
                                                                    .then(Commands.argument("clearInventory", BoolArgumentType.bool()).executes(ctx -> {
                                                                      String player = StringArgumentType.getString(ctx, "player");
                                                                      boolean clearInv = BoolArgumentType.getBool(ctx, "clearInventory");
                                                                      return this.executePowCommand(ctx, "admin", "resetplayer", player, String.valueOf(clearInv));
                                                                    }))
                                                    )
                                    ))
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("set_vampire_spawn")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "set_vampire_spawn")))
                                                    .then(
                                                            Commands.argument("x", IntegerArgumentType.integer())
                                                                    .then(
                                                                            Commands.argument("y", IntegerArgumentType.integer())
                                                                                    .then(Commands.argument("z", IntegerArgumentType.integer()).executes(ctx -> {
                                                                                      int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                                      int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                                      int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                                      return this.executePowCommand(
                                                                                              ctx, "admin", "set_vampire_spawn", String.valueOf(x), String.valueOf(y), String.valueOf(z)
                                                                                      );
                                                                                    }))
                                                                    )
                                                    )
                                    )
                                    .then(
                                            Commands.literal("reloadconfig")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "reloadconfig"))
                                    )
                                    .then(this.buildConfigNode())
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("barrier")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "status")))
                                                    .then(Commands.literal("lower")
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "lower")))
                                                    .then(Commands.literal("raise")
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "raise")))
                                                    .then(Commands.literal("status")
                                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "status")))
                                                    .then(Commands.literal("exempt")
                                                            .then(Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "exempt",
                                                                            StringArgumentType.getString(ctx, "player")))))
                                                    .then(Commands.literal("normal")
                                                            .then(Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "normal",
                                                                            StringArgumentType.getString(ctx, "player")))))
                                                    .then(Commands.literal("unexempt")
                                                            .then(Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "barrier", "unexempt",
                                                                            StringArgumentType.getString(ctx, "player")))))
                                    )
                                    .then(
                                            Commands.literal("permakill")
                                                    .then(
                                                            Commands.argument("player", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> this.executePowCommand(
                                                                            ctx, "admin", "permakill",
                                                                            StringArgumentType.getString(ctx, "player")))
                                                    )
                                    )
                                    .then(
                                            Commands.literal("sire")
                                                    .then(
                                                            Commands.argument("victim", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                    .executes(ctx -> {
                                                                      String victim = StringArgumentType.getString(ctx, "victim");
                                                                      return this.executePowCommand(ctx, "admin", "sire", victim);
                                                                    })
                                                                    .then(Commands.literal("clear")
                                                                            .executes(ctx -> {
                                                                              String victim = StringArgumentType.getString(ctx, "victim");
                                                                              return this.executePowCommand(ctx, "admin", "sire", victim, "clear");
                                                                            })
                                                                    )
                                                                    .then(Commands.argument("sire", StringArgumentType.word())
                                                                            .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                                                                            .executes(ctx -> {
                                                                              String victim = StringArgumentType.getString(ctx, "victim");
                                                                              String sire = StringArgumentType.getString(ctx, "sire");
                                                                              return this.executePowCommand(ctx, "admin", "sire", victim, sire);
                                                                            })
                                                                    )

                                                    )
                                    )
                                    .then(
                                            Commands.literal("listworlds")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "listworlds"))
                                    )
                                    .then(
                                            Commands.literal("fadestatus")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "fadestatus"))
                                    )
                                    .then(this.buildAdminFaeSubcommand())
                                    .then(this.buildAdminStageAliasSubcommand("vampire"))
                                    .then(this.buildAdminStageAliasSubcommand("werewolf"))
                                    .then(this.buildTurnLockSubcommand("canturn", "admin"))
                                    .then(this.buildTurnLockSubcommand("canbeturned", "admin"))
                                    .then(this.buildTurnLocksStatusSubcommand("admin"))
                                    .then(this.buildDeathCounterSubcommand("deaths", "admin"))
                                    .then(this.buildDeathCounterSubcommand("hearts", "admin"))
                                    .then(
                                            ((LiteralArgumentBuilder) Commands.literal("loadworld")
                                                    .executes(ctx -> this.executePowCommand(ctx, "admin", "loadworld")))
                                                    .then(
                                                            Commands.argument("worldName", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> this.suggestAvailableWorlds(builder))
                                                                    .executes(ctx -> {
                                                                      String worldName = StringArgumentType.getString(ctx, "worldName");
                                                                      return this.executePowCommand(ctx, "admin", "loadworld", worldName);
                                                                    })
                                                    )
                                    )
                    )
                    .then(this.buildAbilitySubcommand())
                    .then(this.buildAbilitySubcommand("abilities"))
                    .then(this.buildFaeDealSubcommand())
                    .then(this.buildFaeDealSubcommand("fae"))
                    .executes(ctx -> this.executePowCommand(ctx, "help")))
                    .build(),
            "VampireSMP main command",
            List.of("vampiresmp", "vsmp")
    );
  }

  // ---------------------------------------------------------------------------
  // Subcommands added alongside the turn-lock / fae / fading features.
  //
  // /pow is registered through Brigadier, so a subcommand that only exists in
  // PowCommand's own switch is unreachable — it has to be declared here too.
  // ---------------------------------------------------------------------------

  private CompletableFuture<Suggestions> suggestFrom(SuggestionsBuilder builder, List<String> options) {
    String remaining = builder.getRemainingLowerCase();
    for (String option : options) {
      if (option.toLowerCase().startsWith(remaining)) {
        builder.suggest(option);
      }
    }
    return builder.buildFuture();
  }

  /** {@code /pow ability [list|check|allow|deny|enable|disable]} */
  private LiteralArgumentBuilder<CommandSourceStack> buildAbilitySubcommand() {
    return this.buildAbilitySubcommand("ability");
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildAbilitySubcommand(String literal) {
    return Commands.literal(literal)
            .executes(ctx -> this.executePowCommand(ctx, "ability"))
            .then(Commands.literal("list").executes(ctx -> this.executePowCommand(ctx, "ability", "list")))
            .then(Commands.literal("status").executes(ctx -> this.executePowCommand(ctx, "ability", "status")))
            .then(Commands.literal("help").executes(ctx -> this.executePowCommand(ctx, "ability", "help")))
            .then(Commands.literal("enable").executes(ctx -> this.executePowCommand(ctx, "ability", "enable")))
            .then(Commands.literal("disable").executes(ctx -> this.executePowCommand(ctx, "ability", "disable")))
            .then(Commands.literal("check")
                    .executes(ctx -> this.executePowCommand(ctx, "ability", "check"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                            .executes(ctx -> this.executePowCommand(ctx, "ability", "check",
                                    StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("allow")
                    .then(Commands.argument("ability", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestFrom(builder, TOME_ABILITIES))
                            .executes(ctx -> this.executePowCommand(ctx, "ability", "allow",
                                    StringArgumentType.getString(ctx, "ability")))))
            .then(Commands.literal("deny")
                    .then(Commands.argument("ability", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestFrom(builder, TOME_ABILITIES))
                            .executes(ctx -> this.executePowCommand(ctx, "ability", "deny",
                                    StringArgumentType.getString(ctx, "ability")))));
  }

  /** {@code /pow faedeal ...} — fae-only, gated in FaeDealCommand rather than here. */
  private LiteralArgumentBuilder<CommandSourceStack> buildFaeDealSubcommand() {
    return this.buildFaeDealSubcommand("faedeal");
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildFaeDealSubcommand(String literal) {
    return Commands.literal(literal)
            .executes(ctx -> this.executePowCommand(ctx, "faedeal"))
            .then(Commands.literal("bargains").executes(ctx -> this.executePowCommand(ctx, "faedeal", "bargains")))
            .then(Commands.literal("list").executes(ctx -> this.executePowCommand(ctx, "faedeal", "list")))
            .then(Commands.literal("help").executes(ctx -> this.executePowCommand(ctx, "faedeal", "help")))
            .then(Commands.literal("vampire")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                            .then(Commands.argument("stage", IntegerArgumentType.integer(1, 3))
                                    .executes(ctx -> this.executePowCommand(ctx, "faedeal", "vampire",
                                            StringArgumentType.getString(ctx, "player"),
                                            String.valueOf(IntegerArgumentType.getInteger(ctx, "stage"))))
                                    .then(Commands.argument("breakOnFaePermadeath", BoolArgumentType.bool())
                                            .executes(ctx -> this.executePowCommand(ctx, "faedeal", "vampire",
                                                    StringArgumentType.getString(ctx, "player"),
                                                    String.valueOf(IntegerArgumentType.getInteger(ctx, "stage")),
                                                    String.valueOf(BoolArgumentType.getBool(ctx, "breakOnFaePermadeath"))))))))
            .then(Commands.literal("release")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                            .executes(ctx -> this.executePowCommand(ctx, "faedeal", "release",
                                    StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("free")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                            .executes(ctx -> this.executePowCommand(ctx, "faedeal", "free",
                                    StringArgumentType.getString(ctx, "player")))))
            .then(this.buildFaeCooldownSubcommand())
            .then(this.buildTurnLockSubcommand("canturn", "faedeal"))
            .then(this.buildTurnLockSubcommand("canbeturned", "faedeal"))
            .then(this.buildTurnLocksStatusSubcommand("faedeal"))
            .then(this.buildDeathCounterSubcommand("deaths", "faedeal"))
            .then(this.buildDeathCounterSubcommand("hearts", "faedeal"));
  }

  /** {@code /pow faedeal cooldown <player> <ability|list|clear> [percent]} */
  private LiteralArgumentBuilder<CommandSourceStack> buildFaeCooldownSubcommand() {
    return Commands.literal("cooldown")
            .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                    .executes(ctx -> this.executePowCommand(ctx, "faedeal", "cooldown",
                            StringArgumentType.getString(ctx, "player")))
                    .then(Commands.literal("list")
                            .executes(ctx -> this.executePowCommand(ctx, "faedeal", "cooldown",
                                    StringArgumentType.getString(ctx, "player"), "list")))
                    .then(Commands.literal("clear")
                            .executes(ctx -> this.executePowCommand(ctx, "faedeal", "cooldown",
                                    StringArgumentType.getString(ctx, "player"), "clear"))
                            .then(Commands.argument("ability", StringArgumentType.word())
                                    .suggests((ctx, builder) -> this.suggestBoonAbilities(builder))
                                    .executes(ctx -> this.executePowCommand(ctx, "faedeal", "cooldown",
                                            StringArgumentType.getString(ctx, "player"), "clear",
                                            StringArgumentType.getString(ctx, "ability")))))
                    .then(Commands.argument("ability", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestBoonAbilities(builder))
                            .then(Commands.argument("percent", IntegerArgumentType.integer(0, 90))
                                    .executes(ctx -> this.executePowCommand(ctx, "faedeal", "cooldown",
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "ability"),
                                            String.valueOf(IntegerArgumentType.getInteger(ctx, "percent")))))));
  }

  /** Every ability that can carry a fae cooldown boon — tome, vampire and werewolf. */
  private CompletableFuture<Suggestions> suggestBoonAbilities(SuggestionsBuilder builder) {
    if (this.plugin.getCooldownBoonManager() == null) return builder.buildFuture();
    return this.suggestFrom(builder,
            new java.util.ArrayList<>(this.plugin.getCooldownBoonManager().knownAbilities()));
  }

  /** {@code /pow admin fae <player> [add|remove|status]} and {@code /pow admin fae list}. */
  private LiteralArgumentBuilder<CommandSourceStack> buildAdminFaeSubcommand() {
    return Commands.literal("fae")
            .executes(ctx -> this.executePowCommand(ctx, "admin", "fae"))
            .then(Commands.literal("list").executes(ctx -> this.executePowCommand(ctx, "admin", "fae", "list")))
            .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                    .executes(ctx -> this.executePowCommand(ctx, "admin", "fae",
                            StringArgumentType.getString(ctx, "player")))
                    .then(Commands.argument("action", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestFrom(builder, Arrays.asList("add", "remove", "status")))
                            .executes(ctx -> this.executePowCommand(ctx, "admin", "fae",
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "action")))));
  }

  /** Handler-supported long spellings retained for scripts and old command books. */
  private LiteralArgumentBuilder<CommandSourceStack> buildAdminStageAliasSubcommand(String faction) {
    return Commands.literal(faction)
            .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                    .then(Commands.literal("clear_stage_cap")
                            .executes(ctx -> this.executePowCommand(ctx, "admin", faction,
                                    StringArgumentType.getString(ctx, "player"), "clear_stage_cap")))
                    .then(Commands.literal("clear_promotion_ban")
                            .executes(ctx -> this.executePowCommand(ctx, "admin", faction,
                                    StringArgumentType.getString(ctx, "player"), "clear_promotion_ban"))));
  }

  /**
   * {@code canturn} / {@code canbeturned}, shared by the admin tree and {@code /pow faedeal}.
   *
   * @param prefix "admin" or "faedeal" — the leading argument PowCommand dispatches on.
   */
  private LiteralArgumentBuilder<CommandSourceStack> buildTurnLockSubcommand(String literal, String prefix) {
    return Commands.literal(literal)
            .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                    .then(Commands.argument("species", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestFrom(builder, FAE_SPECIES))
                            .executes(ctx -> this.executePowCommand(ctx, prefix, literal,
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "species")))
                            .then(Commands.argument("action", StringArgumentType.word())
                                    .suggests((ctx, builder) -> this.suggestFrom(builder, Arrays.asList("allow", "deny", "status")))
                                    .executes(ctx -> this.executePowCommand(ctx, prefix, literal,
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "species"),
                                            StringArgumentType.getString(ctx, "action"))))));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildTurnLocksStatusSubcommand(String prefix) {
    return Commands.literal("turnlocks")
            .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                    .executes(ctx -> this.executePowCommand(ctx, prefix, "turnlocks",
                            StringArgumentType.getString(ctx, "player"))));
  }

  /** {@code deaths} / {@code hearts} — same counter, different verbs. */
  private LiteralArgumentBuilder<CommandSourceStack> buildDeathCounterSubcommand(String literal, String prefix) {
    List<String> actions = literal.equals("hearts")
            ? Arrays.asList("get", "give", "take")
            : Arrays.asList("get", "set", "add", "remove");

    return Commands.literal(literal)
            .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> this.suggestOnlinePlayers(builder))
                    .executes(ctx -> this.executePowCommand(ctx, prefix, literal,
                            StringArgumentType.getString(ctx, "player")))
                    .then(Commands.argument("action", StringArgumentType.word())
                            .suggests((ctx, builder) -> this.suggestFrom(builder, actions))
                            .executes(ctx -> this.executePowCommand(ctx, prefix, literal,
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "action")))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(ctx -> this.executePowCommand(ctx, prefix, literal,
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "action"),
                                            String.valueOf(IntegerArgumentType.getInteger(ctx, "amount")))))));
  }

  private LiteralArgumentBuilder<CommandSourceStack> buildBeaconSubcommand() {
    return (LiteralArgumentBuilder<CommandSourceStack>) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal(
                    "beacon"
            )
            .then(
                    Commands.literal("add")
                            .then(((RequiredArgumentBuilder) Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                              String name = StringArgumentType.getString(ctx, "name");
                              return this.executePowCommand(ctx, "admin", "beacon", "add", name);
                            })).then(Commands.argument("radius", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
                              String name = StringArgumentType.getString(ctx, "name");
                              int radius = IntegerArgumentType.getInteger(ctx, "radius");
                              return this.executePowCommand(ctx, "admin", "beacon", "add", name, String.valueOf(radius));
                            })))
            ))
            .then(
                    Commands.literal("remove")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> {
                                              String name = StringArgumentType.getString(ctx, "name");
                                              return this.executePowCommand(ctx, "admin", "beacon", "remove", name);
                                            })
                            )
            ))
            .then(
                    Commands.literal("delete")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "delete",
                                                    StringArgumentType.getString(ctx, "name")))
                            )
            )
            .then(Commands.literal("list").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "list"))))
            .then(
                    Commands.literal("info")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> {
                                              String name = StringArgumentType.getString(ctx, "name");
                                              return this.executePowCommand(ctx, "admin", "beacon", "info", name);
                                            })
                            )
            ))
            .then(Commands.literal("stats").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "stats"))))
            .then(Commands.literal("reload").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "reload"))))
            .then(Commands.literal("validate").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "validate"))))
            .then(
                    Commands.literal("holy")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> {
                                              String name = StringArgumentType.getString(ctx, "name");
                                              return this.executePowCommand(ctx, "admin", "beacon", "holy", name);
                                            })
                            )
            ))
            .then(
                    Commands.literal("desecrated")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> {
                                              String name = StringArgumentType.getString(ctx, "name");
                                              return this.executePowCommand(ctx, "admin", "beacon", "desecrated", name);
                                            })
                            )
            ))
            .then(
                    Commands.literal("desecrate")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "desecrate",
                                                    StringArgumentType.getString(ctx, "name")))
                            )
            )
            .then(
                    Commands.literal("neutral")
                            .then(
                                    Commands.argument("name", StringArgumentType.word())
                                            .suggests((ctx, builder) -> this.suggestBeaconNames(builder))
                                            .executes(ctx -> {
                                              String name = StringArgumentType.getString(ctx, "name");
                                              return this.executePowCommand(ctx, "admin", "beacon", "neutral", name);
                                            })
                            )
            ))
            .then(Commands.literal("fix").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "fix"))))
            .then(Commands.literal("repair").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "repair"))))
            .then(Commands.literal("refresh").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "refresh"))))
            .then(Commands.literal("cleanup").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "cleanup"))))
            .then(Commands.literal("clearcooldowns").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "clearcooldowns"))))
            .then(
                    ((LiteralArgumentBuilder) Commands.literal("debug").executes(ctx -> this.executePowCommand(ctx, "admin", "beacon", "debug")))
                            .then(Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> this.suggestBeaconNames(builder)).executes(ctx -> {
                              String name = StringArgumentType.getString(ctx, "name");
                              return this.executePowCommand(ctx, "admin", "beacon", "debug", name);
                            }))
            );
  }

  private void registerLatinCureCommand(Commands commands) {
    commands.register(((LiteralArgumentBuilder) Commands.literal("voluntate-mea-hoc-nefandum-vinculum-abicio").executes(ctx -> {
      CommandSender sender = ((CommandSourceStack) ctx.getSource()).getSender();
      this.cureCommand.onCommand(sender, null, "voluntate-mea-hoc-nefandum-vinculum-abicio", new String[0]);
      return 1;
    })).build(), "Cure yourself from vampirism");
  }

  private void registerLatinForcedCureCommand(Commands commands) {
    commands.register(
            ((LiteralArgumentBuilder) Commands.literal("hoc-vinculum-tibi-dirumpo-mala-creatura")
                    .then(Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> this.suggestOnlinePlayers(builder)).executes(ctx -> {
                      CommandSender sender = ((CommandSourceStack) ctx.getSource()).getSender();
                      String playerName = StringArgumentType.getString(ctx, "player");
                      this.forcedCureCommand.onCommand(sender, null, "hoc-vinculum-tibi-dirumpo-mala-creatura", new String[]{playerName});
                      return 1;
                    })))
                    .build(),
            "Force a vampire back to humanity"
    );
  }

  private void registerLatinRevivalCommand(Commands commands) {
    commands.register(
            ((LiteralArgumentBuilder) Commands.literal("sanguine-et-nocte-te-ex-umbra-revoco")
                    .then(Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> this.suggestOnlinePlayers(builder)).executes(ctx -> {
                      CommandSender sender = ((CommandSourceStack) ctx.getSource()).getSender();
                      String playerName = StringArgumentType.getString(ctx, "player");
                      this.revivalCommand.onCommand(sender, null, "sanguine-et-nocte-te-ex-umbra-revoco", new String[]{playerName});
                      return 1;
                    })))
                    .build(),
            "Rite of Return — call a ghost back to life"
    );
  }

  /** Build the {@code /pow admin config} subtree (get / set / list / reload / gui). */
  private LiteralArgumentBuilder<CommandSourceStack> buildConfigNode() {
    return Commands.literal("config")
        .requires(src -> src.getSender().hasPermission("vampiresmp.admin"))
        .executes(ctx -> this.executePowCommand(ctx, "admin", "config"))
        .then(Commands.literal("gui")
            .executes(ctx -> this.executePowCommand(ctx, "admin", "config", "gui")))
        .then(Commands.literal("reload")
            .executes(ctx -> this.executePowCommand(ctx, "admin", "config", "reload")))
        .then(Commands.literal("list")
            .executes(ctx -> this.executePowCommand(ctx, "admin", "config", "list"))
            .then(Commands.argument("prefix", StringArgumentType.string())
                .suggests((ctx, b) -> this.suggestConfigPaths(b))
                .executes(ctx -> this.executePowCommand(ctx, "admin", "config", "list",
                        StringArgumentType.getString(ctx, "prefix")))))
        .then(Commands.literal("get")
            .then(Commands.argument("path", StringArgumentType.string())
                .suggests((ctx, b) -> this.suggestConfigPaths(b))
                .executes(ctx -> this.executePowCommand(ctx, "admin", "config", "get",
                        StringArgumentType.getString(ctx, "path")))))
        .then(Commands.literal("set")
            .then(Commands.argument("path", StringArgumentType.string())
                .suggests((ctx, b) -> this.suggestConfigPaths(b))
                .then(Commands.argument("value", StringArgumentType.greedyString())
                    .executes(ctx -> this.executePowCommand(ctx, "admin", "config", "set",
                            StringArgumentType.getString(ctx, "path"),
                            StringArgumentType.getString(ctx, "value"))))));
  }

  /** Suggest config keys (sections + leaves) for the config path arguments. */
  private CompletableFuture<Suggestions> suggestConfigPaths(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();
    for (String key : this.plugin.getConfig().getKeys(true)) {
      if (key.toLowerCase().startsWith(remaining)) {
        builder.suggest(key);
      }
    }
    return builder.buildFuture();
  }

  private int executePowCommand(CommandContext<CommandSourceStack> ctx, String... args) {
    CommandSender sender = ((CommandSourceStack) ctx.getSource()).getSender();
    Command dummyCommand = new BukkitCommand("pow") {
      public boolean execute(CommandSender sender, String commandLabel, String[] argsx) {
        return false;
      }
    };
    this.powCommand.onCommand(sender, dummyCommand, "pow", args);
    return 1;
  }

  /**
   * Second argument of {@code /pow tome <ability> <arg>}, which means different things per
   * tome: Scrying names a player to point at, Fading takes an opacity. Anything else has no
   * second argument, so nothing is suggested rather than offering a misleading player list.
   */
  private CompletableFuture<Suggestions> suggestTomeAbilityTargets(
          CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
    String ability;
    try {
      ability = StringArgumentType.getString(ctx, "ability").toLowerCase();
    } catch (IllegalArgumentException e) {
      return builder.buildFuture();
    }

    if (ability.equals("scrying")) {
      CommandSender sender = ((CommandSourceStack) ctx.getSource()).getSender();
      String remaining = builder.getRemainingLowerCase();
      for (Player player : Bukkit.getOnlinePlayers()) {
        // Pointing an arrow at yourself is not useful, so leave the caller out.
        if (sender instanceof Player self && player.getUniqueId().equals(self.getUniqueId())) continue;
        if (player.getName().toLowerCase().startsWith(remaining)) {
          builder.suggest(player.getName());
        }
      }
      return builder.buildFuture();
    }

    if (ability.equals("fading")) {
      String remaining = builder.getRemainingLowerCase();
      for (String opacity : new String[] { "0", "25", "50", "75", "100" }) {
        if (opacity.startsWith(remaining)) builder.suggest(opacity);
      }
      return builder.buildFuture();
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();

    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getName().toLowerCase().startsWith(remaining)) {
        builder.suggest(player.getName());
      }
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestVampireAbilities(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();

    for (String ability : VAMPIRE_ABILITIES) {
      if (ability.startsWith(remaining)) {
        builder.suggest(ability);
      }
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestWerewolfAbilities(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();

    for (String ability : WEREWOLF_ABILITIES) {
      if (ability.startsWith(remaining)) {
        builder.suggest(ability);
      }
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestTomeAbilities(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();
    if (((CommandSourceStack) ctx.getSource()).getSender() instanceof Player player) {
      for (String ability : this.plugin.getTomeManager().getPlayerAbilities(player)) {
        if (ability.toLowerCase().startsWith(remaining)) {
          builder.suggest(ability);
        }
      }
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestAllTomeAbilities(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();

    for (String ability : TOME_ABILITIES) {
      if (ability.startsWith(remaining)) {
        builder.suggest(ability);
      }
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestBeaconNames(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();

    for (BeaconSite beacon : this.plugin.getBeaconManager().getAllBeacons()) {
      if (beacon.getName().toLowerCase().startsWith(remaining)) {
        builder.suggest(beacon.getName());
      }
    }

    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestAvailableWorlds(SuggestionsBuilder builder) {
    String remaining = builder.getRemainingLowerCase();

    for (String worldName : this.plugin.getWorldManager().getAvailableWorlds()) {
      if (worldName.toLowerCase().startsWith(remaining)) {
        builder.suggest(worldName);
      }
    }

    return builder.buildFuture();
  }
}
