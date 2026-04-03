package com.starskyxiii.collapsible_groups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Fabric client command registration for {@code /cg group_key dump <locale>}.
 */
public final class CgClientCommand {

	private static final SuggestionProvider<FabricClientCommandSource> LOCALE_SUGGESTIONS =
		(context, builder) -> LocaleSuggestionHelper.suggest(builder);

	private CgClientCommand() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
			ClientCommands.literal("cg")
				.then(ClientCommands.literal("group_key")
					.then(ClientCommands.literal("dump")
						.then(ClientCommands.argument("locale", StringArgumentType.word())
							.suggests(LOCALE_SUGGESTIONS)
							.executes(ctx -> {
								String locale = StringArgumentType.getString(ctx, "locale");
								return GroupKeyDumpLogic.dump(
									locale, false,
									msg -> ctx.getSource().sendFeedback(msg)
								);
							})
							.then(ClientCommands.literal("clean")
								.executes(ctx -> {
									String locale = StringArgumentType.getString(ctx, "locale");
									return GroupKeyDumpLogic.dump(
										locale, true,
										msg -> ctx.getSource().sendFeedback(msg)
									);
								})
							)
						)
					)
				)
		);
	}
}
