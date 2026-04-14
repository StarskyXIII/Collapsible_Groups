package com.starskyxiii.collapsible_groups.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GroupFilterValidatorTest {
	@Test
	void validateComponentsReturnsTranslatedMessagesForInvalidResourceLocation() {
		List<Component> errors = GroupFilterValidator.validateComponents(new GroupFilter.Id("item", "not a rl"));

		assertEquals(1, errors.size());
		TranslatableContents contents = assertInstanceOf(TranslatableContents.class, errors.getFirst().getContents());
		assertEquals("collapsible_groups.editor.rules.error.invalid_resource_location", contents.getKey());
		assertEquals("not a rl", contents.getArgs()[0]);
	}

	@Test
	void validateComponentsReturnsTranslatedMessagesForInvalidComponentPathGrammar() {
		List<Component> errors = GroupFilterValidator.validateComponents(new GroupFilter.ComponentPath(
			"minecraft:custom_data",
			"bad path",
			"value"
		));

		assertEquals(1, errors.size());
		TranslatableContents contents = assertInstanceOf(TranslatableContents.class, errors.getFirst().getContents());
		assertEquals("collapsible_groups.editor.rules.error.component_path_grammar", contents.getKey());
		assertEquals("bad path", contents.getArgs()[0]);
	}
}
