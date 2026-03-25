package com.starskyxiii.collapsible_groups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Forge client command registration for {@code /cg group_key dump <locale>}.
 */
public final class CgClientCommand {

	private static final SuggestionProvider<CommandSourceStack> LOCALE_SUGGESTIONS =
		(context, builder) -> LocaleSuggestionHelper.suggest(builder);

	private CgClientCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("cg")
				.then(Commands.literal("group_key")
					.then(Commands.literal("dump")
						.then(Commands.argument("locale", StringArgumentType.word())
							.suggests(LOCALE_SUGGESTIONS)
							.executes(ctx -> {
								String locale = StringArgumentType.getString(ctx, "locale");
								return GroupKeyDumpLogic.dump(
									locale, false,
									msg -> ctx.getSource().sendSuccess(() -> msg, false)
								);
							})
							.then(Commands.literal("clean")
								.executes(ctx -> {
									String locale = StringArgumentType.getString(ctx, "locale");
									return GroupKeyDumpLogic.dump(
										locale, true,
										msg -> ctx.getSource().sendSuccess(() -> msg, false)
									);
								})
							)
						)
					)
				)
		);
	}
}
