package com.starskyxiii.collapsible_groups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
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
			ClientCommandManager.literal("cg")
				.then(ClientCommandManager.literal("group_key")
					.then(ClientCommandManager.literal("dump")
						.then(ClientCommandManager.argument("locale", StringArgumentType.word())
							.suggests(LOCALE_SUGGESTIONS)
							.executes(ctx -> {
								String locale = StringArgumentType.getString(ctx, "locale");
								return GroupKeyDumpLogic.dump(
									locale, false,
									msg -> ctx.getSource().sendFeedback(msg)
								);
							})
							.then(ClientCommandManager.literal("clean")
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
