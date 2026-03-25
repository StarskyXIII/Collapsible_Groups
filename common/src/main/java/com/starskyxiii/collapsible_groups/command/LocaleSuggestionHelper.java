package com.starskyxiii.collapsible_groups.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Shared locale suggestions for client commands.
 *
 * <p>Suggestions prefer the live client language registry so the command keeps
 * pace with whatever language packs the player currently has available. We also
 * include existing overlay lang filenames to preserve discoverability for
 * custom locale files that may not exist in the vanilla language registry.</p>
 */
public final class LocaleSuggestionHelper {

	private static final List<String> FALLBACK_LOCALES = List.of(
		"en_us", "zh_tw", "zh_cn", "ja_jp", "ko_kr", "de_de", "fr_fr", "es_es", "pt_br", "ru_ru", "it_it"
	);

	private LocaleSuggestionHelper() {}

	public static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(getSuggestedLocales(), builder);
	}

	public static Collection<String> getSuggestedLocales() {
		TreeSet<String> locales = new TreeSet<>();

		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null && minecraft.getLanguageManager() != null) {
				locales.addAll(minecraft.getLanguageManager().getLanguages().keySet());
			}
		} catch (Exception ignored) {
		}

		Path overlayDir = GroupTranslationHelper.getOverlayLangDir();
		try (Stream<Path> files = Files.list(overlayDir)) {
			files
				.filter(Files::isRegularFile)
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".json"))
				.map(name -> name.substring(0, name.length() - 5))
				.filter(name -> !name.isBlank())
				.forEach(locales::add);
		} catch (IOException ignored) {
		}

		if (locales.isEmpty()) {
			return FALLBACK_LOCALES;
		}

		return locales;
	}
}
