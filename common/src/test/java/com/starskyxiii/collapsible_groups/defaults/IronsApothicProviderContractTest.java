package com.starskyxiii.collapsible_groups.defaults;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronsApothicProviderContractTest {
	private static final Pattern GEM = Pattern.compile("gem\\(\"(irons_apothic:(?:core|external)/[^\"]+)\"\\)");
	private static final Pattern GROUP = Pattern.compile("group\\(\"(__default_irons_apothic_gem_[^\"]+)\"");

	private static final Map<String, String> EXTERNAL_DEPENDENCIES = Map.ofEntries(
		Map.entry("aeolian_celestite", "monsterspellbooks"),
		Map.entry("amethyst_lotus", "iss_magicfromtheeast"),
		Map.entry("arcanists_edge", "ess_requiem"),
		Map.entry("deepsea_heartstone", "cataclysm_spellbooks"),
		Map.entry("goetic_stibnite", "discerning_the_eldritch"),
		Map.entry("harmonic_geode", "iss_magicfromtheeast"),
		Map.entry("lithographic_moissanite", "aces_spell_utils"),
		Map.entry("mycotoxin_crystal", "hazentouvelib"),
		Map.entry("resonant_quartz", "tunes_n_tomes"),
		Map.entry("sepulchral_howlite", "monsterspellbooks"),
		Map.entry("sirens_aquamarine", "aces_spell_utils"),
		Map.entry("sky_meteoritill", "aero_additions"),
		Map.entry("umbral_obsidian", "hazentouvelib")
	);

	@Test
	void providerDeclaresTenCoreAndThirteenIndividuallyGatedExternalGems() throws IOException {
		String source = Files.readString(root().resolve(
			"neoforge/src/main/java/com/starskyxiii/collapsible_groups/defaults/integration/IronsApothic.java"));
		Set<String> gems = matches(GEM, source);
		Set<String> groups = matches(GROUP, source);

		assertEquals(23, gems.size());
		assertEquals(10, gems.stream().filter(id -> id.startsWith("irons_apothic:core/")).count());
		assertEquals(13, gems.stream().filter(id -> id.startsWith("irons_apothic:external/")).count());
		assertEquals(23, groups.size());
		assertTrue(source.contains("itemWithComponent(\"apotheosis:gem\", \"apotheosis:gem\", gemId)"));

		for (Map.Entry<String, String> expected : EXTERNAL_DEPENDENCIES.entrySet()) {
			assertTrue(source.contains("addExternal(groups, \"" + expected.getValue() + "\", group(\"" +
				"__default_irons_apothic_gem_external_" + expected.getKey()), expected.toString());
		}
	}

	@Test
	void providerConfigServiceAndTranslationsAreRegistered() throws IOException {
		String service = Files.readString(root().resolve(
			"neoforge/src/main/resources/META-INF/services/com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider"));
		String config = Files.readString(root().resolve(
			"neoforge/src/main/java/com/starskyxiii/collapsible_groups/config/NeoForgeConfig.java"));
		String english = Files.readString(root().resolve(
			"common/src/main/resources/assets/collapsible_groups/lang/en_us.json"));
		String traditionalChinese = Files.readString(root().resolve(
			"common/src/main/resources/assets/collapsible_groups/lang/zh_tw.json"));

		assertTrue(service.contains("defaults.integration.IronsApothic"));
		assertTrue(config.contains("LOAD_IRONS_APOTHIC"));
		assertTrue(config.contains("define(\"loadIronsApothic\", true)"));
		assertTrue(config.contains("ModList.get().isLoaded(\"irons_apothic\")"));
		assertTrue(english.contains("ModIntegration.loadIronsApothic"));
		assertTrue(traditionalChinese.contains("ModIntegration.loadIronsApothic"));
	}

	@Test
	void generatedEnglishGroupLanguageContainsAllTwentyThreeGroups() throws IOException {
		JsonObject language = JsonParser.parseString(Files.readString(root().resolve(
			"common/src/main/resources/assets/collapsible_groups/group_lang/en_us.json"))).getAsJsonObject();

		long count = language.keySet().stream()
			.filter(key -> key.startsWith("collapsible_groups.group.__default_irons_apothic_gem_"))
			.count();
		assertEquals(23, count);
	}

	@Test
	void existingApotheosisGemsUseItemAndComponentConjunction() throws IOException {
		String source = Files.readString(root().resolve(
			"neoforge/src/main/java/com/starskyxiii/collapsible_groups/defaults/integration/Apotheosis.java"));

		assertEquals(23, Pattern.compile("gem\\(\"apotheosis:[^\"]+\"\\)").matcher(source).results().count());
		assertTrue(source.contains("itemWithComponent(\"apotheosis:gem\", \"apotheosis:gem\", gemId)"));
		assertFalse(source.contains("component(\"apotheosis:gem\""));
	}

	@Test
	void editorNoLongerReadsTheRegistryIdItemReverseIndex() throws IOException {
		String source = Files.readString(root().resolve(
			"common/src/main/java/com/starskyxiii/collapsible_groups/client/editor/EditorLeftPanel.java"));

		assertTrue(source.contains("itemOwnership(allItems, others)"));
		assertFalse(source.contains("itemReverseIndex()"));
	}

	private static Set<String> matches(Pattern pattern, String source) {
		Set<String> values = new LinkedHashSet<>();
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) values.add(matcher.group(1));
		return values;
	}

	private static Path root() {
		return Path.of(System.getProperty("collapsibleGroupsRoot"));
	}
}
