package com.tom.cpm.paper;

import java.util.Collections;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.tom.cpl.command.BrigadierCommandHandlerBase;
import com.tom.cpl.command.LiteralCommandBuilder;
import com.tom.cpl.text.IText;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

public class PaperCommands extends BrigadierCommandHandlerBase<CommandSourceStack> {
	private final Commands commands;
	private final TextComponents text;

	public PaperCommands(Commands commands, TextComponents text) {
		this.commands = commands;
		this.text = text;
	}

	@Override
	public String toStringPlayer(Object pl) {
		return ((Player)pl).getDisplayName();
	}

	@Override
	public void sendSuccess(CommandSourceStack sender, IText text) {
		sender.getSender().sendMessage(text.<Component>remap());
	}

	@Override
	protected ArgumentType<?> player() {
		return ArgumentTypes.player();
	}

	@Override
	protected Object getPlayer(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
		return ctx.getArgument(id, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
	}

	@Override
	public void register(LiteralCommandBuilder builder, boolean isOp) {
		LiteralArgumentBuilder<CommandSourceStack> cmd = literal(builder.getName());
		if(isOp)cmd.requires(s -> s.getSender().isOp() || s.getSender().hasPermission("cpm." + builder.getName() + ".use_command"));
		build(cmd, builder, Collections.emptyList(), cmd.getLiteral().toLowerCase(Locale.ROOT));
		commands.register(CPMPaperPlugin.getInstance().getPluginMeta(), cmd.build(), text.format("commands." + builder.getName() + ".usage"), Collections.emptyList());
	}
}
