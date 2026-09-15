package com.starskyxiii.collapsible_groups.compat.jei.element;

import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.client.preview.PreviewTooltipComponent;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JeiElementAbiTest {
	private static final String GROUP_ID = "jei_abi_test";
	private static final List<Class<?>> ELEMENT_TYPES = List.of(
		AbstractFluidChildElement.class,
		GenericChildElement.class,
		GroupChildElement.class,
		GroupHeaderElement.class
	);

	@AfterEach
	void clearExpansion() {
		GroupExpandState.load(Set.of());
	}

	@Test
	void customElementsImplementEveryRuntimeInterfaceMethod() throws ReflectiveOperationException {
		for (var contract : IElement.class.getMethods()) {
			if (!Modifier.isAbstract(contract.getModifiers())) continue;
			for (Class<?> elementType : ELEMENT_TYPES) {
				var implementation = elementType.getMethod(contract.getName(), contract.getParameterTypes());
				assertFalse(Modifier.isAbstract(implementation.getModifiers()),
					elementType.getName() + " must implement " + contract);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void headerTooltipPreservesNameCountPreviewAndAction(boolean expanded) {
		GroupExpandState.load(expanded ? Set.of(GROUP_ID) : Set.of());
		var preview = GroupPreviewEntry.ofRenderer((graphics, x, y) -> {});
		var count = Component.literal("3 entries");
		var header = new GroupHeaderElement(icon(), count, List.of(preview), () -> {});
		var tooltip = new JeiTooltip();

		header.appendTooltip(tooltip);

		var lines = tooltip.getLines();
		assertEquals(expanded ? 3 : 4, lines.size());
		var name = (Component) lines.get(0).left().orElseThrow();
		assertEquals("ABI group", name.getString());
		assertEquals(Services.CONFIG.groupNameColor(), name.getStyle().getColor().getValue());
		assertSame(count, lines.get(1).left().orElseThrow());
		if (!expanded) {
			var visual = assertInstanceOf(PreviewTooltipComponent.class, lines.get(2).right().orElseThrow());
			assertEquals(List.of(preview), visual.entries());
		}
		var action = (Component) lines.get(lines.size() - 1).left().orElseThrow();
		var text = assertInstanceOf(TranslatableContents.class, action.getContents());
		assertEquals(expanded ? ModTranslationKeys.TOOLTIP_COLLAPSE : ModTranslationKeys.TOOLTIP_EXPAND, text.getKey());
		assertTrue(action.getStyle().isItalic());
	}

	@Test
	void headerRetainsIngredientAndDoesNotOpenRecipes() {
		var ingredient = icon();
		IElement<GroupIcon> header = new GroupHeaderElement(ingredient, Component.empty(), List.of(), () -> {});
		assertSame(ingredient, header.getTypedIngredient());
		assertDoesNotThrow(() -> header.show(null, null, List.of()));
	}

	private static ITypedIngredient<GroupIcon> icon() {
		var icon = new GroupIcon(GROUP_ID, "test.jei_abi", "ABI group", List.of());
		return new ITypedIngredient<>() {
			@Override public IIngredientType<GroupIcon> getType() { return GroupIcon.TYPE; }
			@Override public GroupIcon getIngredient() { return icon; }
		};
	}
}
