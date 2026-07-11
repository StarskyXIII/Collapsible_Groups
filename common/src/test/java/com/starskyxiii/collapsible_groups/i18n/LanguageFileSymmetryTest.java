package com.starskyxiii.collapsible_groups.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the bundled locale files against drifting apart: every key present in one
 * language file must exist in the other. Values are free to differ; key sets are not.
 */
class LanguageFileSymmetryTest {

	@Test
	void englishAndTraditionalChineseShareTheExactSameKeySet() {
		Set<String> english = keys(language("en_us"));
		Set<String> chinese = keys(language("zh_tw"));

		Set<String> missingInChinese = new TreeSet<>(english);
		missingInChinese.removeAll(chinese);
		Set<String> missingInEnglish = new TreeSet<>(chinese);
		missingInEnglish.removeAll(english);

		assertEquals(Set.of(), missingInChinese, "keys missing from zh_tw");
		assertEquals(Set.of(), missingInEnglish, "keys missing from en_us");
	}

	private static Set<String> keys(JsonObject language) {
		return new TreeSet<>(language.keySet());
	}

	private static JsonObject language(String locale) {
		String path = "/assets/collapsible_groups/lang/" + locale + ".json";
		try (var reader = new InputStreamReader(
			LanguageFileSymmetryTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception exception) {
			throw new AssertionError("Cannot read " + path, exception);
		}
	}
}
