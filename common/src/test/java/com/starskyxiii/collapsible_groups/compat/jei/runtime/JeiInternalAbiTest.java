package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.ingredients.IIngredientListOverlayContents;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JeiInternalAbiTest {
	@Test
	void ingredientFilterRetainsEveryPrivateMemberUsedByMixins() throws ReflectiveOperationException {
		Class<?> filter = Class.forName("mezz.jei.gui.ingredients.IngredientFilter");

		assertNotNull(filter.getDeclaredConstructor(
			Class.forName("mezz.jei.gui.filter.IFilterTextSource"),
			IClientConfig.class,
			Class.forName("mezz.jei.common.config.IIngredientFilterConfig"),
			Class.forName("mezz.jei.api.runtime.IIngredientManager"),
			Comparator.class,
			List.class,
			Class.forName("mezz.jei.api.helpers.IModIdHelper"),
			Class.forName("mezz.jei.api.runtime.IIngredientVisibility"),
			Class.forName("mezz.jei.api.helpers.IColorHelper"),
			Class.forName("mezz.jei.api.search.ISearchStorageBuilderFactory"),
			IClientToggleState.class
		));
		assertEquals(List.class, filter.getDeclaredField("ingredientListCached").getType());
		assertEquals(
			Class.forName("mezz.jei.gui.filter.IFilterTextSource"),
			filter.getDeclaredField("filterTextSource").getType()
		);
		assertEquals(
			Class.forName("mezz.jei.api.runtime.IIngredientManager"),
			filter.getDeclaredField("ingredientManager").getType()
		);
		assertEquals(
			Stream.class,
			filter.getDeclaredMethod("getIngredientListUncached", String.class).getReturnType()
		);
		assertNotNull(filter.getDeclaredMethod("notifyListenersOfChange"));
	}

	@Test
	void ingredientOverlayRetainsExactConstructorAndRenderContracts() throws ReflectiveOperationException {
		assertNotNull(IngredientListOverlay.class.getDeclaredConstructor(
			IIngredientGridSource.class,
			Class.forName("mezz.jei.gui.filter.IFilterTextSource"),
			IScreenHelper.class,
			IIngredientListOverlayContents.class,
			LookupHistoryOverlay.class,
			IClientConfig.class,
			IClientToggleState.class,
			IInternalKeyMappings.class
		));
		assertNotNull(IngredientListOverlay.class.getDeclaredMethod(
			"drawBackground", GuiGraphicsExtractor.class));
		assertNotNull(IngredientListOverlay.class.getDeclaredMethod(
			"drawForeground", Minecraft.class, GuiGraphicsExtractor.class,
			int.class, int.class, float.class));
		assertNotNull(IngredientListOverlay.class.getDeclaredMethod(
			"drawTooltips", Minecraft.class, GuiGraphicsExtractor.class, int.class, int.class));
		assertNotNull(IngredientListOverlay.class.getDeclaredMethod("createInputHandler"));
	}

	@Test
	void rendererAndSlotRetainPreRenderHookContracts() throws ReflectiveOperationException {
		assertEquals(List.class, IngredientListRenderer.class.getDeclaredField("slots").getType());
		assertNotNull(IngredientListRenderer.class.getDeclaredMethod(
			"render", GuiGraphicsExtractor.class));
		assertNotNull(IngredientListSlot.class.getDeclaredMethod("getOptionalElement"));
		assertNotNull(IngredientListSlot.class.getDeclaredMethod("getRenderArea"));
	}

	@Test
	void bookmarkTextFieldAndInputContractsRemainAvailable() throws ReflectiveOperationException {
		assertNotNull(BookmarkList.class.getDeclaredMethod(
			"onElementBookmarked", IElement.class, UserInput.class, BookmarkOverlay.class));
		assertNotNull(GuiTextFieldFilter.class.getDeclaredField("area"));
		assertNotNull(UserInput.class.getDeclaredMethod(
			"ifMouseEvent", UserInput.MouseClickable.class));
		assertNotNull(IUserInputHandler.class.getDeclaredMethod(
			"handleUserInput", Screen.class, IGuiProperties.class,
			UserInput.class, IInternalKeyMappings.class));
	}
}
