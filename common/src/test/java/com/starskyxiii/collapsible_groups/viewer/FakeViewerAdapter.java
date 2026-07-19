package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class FakeViewerAdapter implements ViewerAdapter<String, String> {
	private final String id;
	private final ViewerIngredientUniverse<String> universe;
	private final List<ViewerIngredientType<String>> ingredientTypes;
	private final FakeSearchState searchState;
	private final List<GroupChangeEvent.Kind> changes = new ArrayList<>();
	private boolean runtimeAvailable;

	FakeViewerAdapter(
		ViewerIngredientUniverse<String> universe,
		List<ViewerIngredientType<String>> ingredientTypes,
		ViewerSearchSnapshot<String> initialSearch
	) {
		this("fake", universe, ingredientTypes, initialSearch);
	}

	FakeViewerAdapter(String id) {
		this(id, new ViewerIngredientUniverse<>(List.of()), List.of(),
			new ViewerSearchSnapshot<>("", List.of(), false, 0));
	}

	private FakeViewerAdapter(String id, ViewerIngredientUniverse<String> universe,
		List<ViewerIngredientType<String>> ingredientTypes, ViewerSearchSnapshot<String> initialSearch) {
		this.id = id;
		this.universe = universe;
		this.ingredientTypes = List.copyOf(ingredientTypes);
		this.searchState = new FakeSearchState(initialSearch);
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public ViewerUniverseProvider<String> universeProvider() {
		return () -> {
			if (!runtimeAvailable) throw new IllegalStateException("runtime is not available");
			return universe;
		};
	}

	@Override
	public ViewerSearchState<String> searchState() {
		return searchState;
	}

	@Override
	public ViewerBootstrapContext<String> bootstrapContext() {
		return new ViewerBootstrapContext<>() {
			@Override
			public List<ViewerIngredientType<String>> ingredientTypes() {
				return ingredientTypes;
			}

			@Override
			public ViewerIngredientUniverse<String> universe() {
				return universe;
			}
		};
	}

	@Override
	public ViewerPresentation<String, String> presentation() {
		return new ViewerPresentation<>() {
			@Override
			public void renderIngredient(ViewerIngredient<String> ingredient, RenderContext context) {}

			@Override
			public void renderHeader(ViewerProjection.GroupHeader<String> header, RenderContext context) {}

			@Override
			public List<String> ingredientTooltip(ViewerIngredient<String> ingredient, TooltipContext context) {
				return List.of(ingredient.entry());
			}

			@Override
			public List<String> headerTooltip(ViewerProjection.GroupHeader<String> header, TooltipContext context) {
				return List.of(header.group().id());
			}
		};
	}

	@Override
	public ViewerBookmarkPolicy<String> bookmarkPolicy() {
		return ViewerBookmarkPolicy.headersCannotBeBookmarked();
	}

	@Override
	public ViewerOverlayHook overlayHook() {
		return new ViewerOverlayHook() {
			@Override
			public boolean shouldShowButton(boolean configuredVisible, boolean ingredientListVisible) {
				return configuredVisible && ingredientListVisible;
			}

			@Override
			public Bounds placeButton(Bounds configButton, int gap) {
				return new Bounds(
					configButton.x() - configButton.width() - gap,
					configButton.y(),
					configButton.width(),
					configButton.height()
				);
			}

			@Override
			public int adjustSearchFieldWidth(Bounds searchField, Bounds button, int gap) {
				return Math.max(0, button.x() - gap - searchField.x());
			}

			@Override
			public boolean handleInput(Input input) {
				return false;
			}
		};
	}

	@Override
	public void onGroupChange(GroupChangeEvent.Kind kind) {
		changes.add(kind);
	}

	List<GroupChangeEvent.Kind> changes() {
		return List.copyOf(changes);
	}

	void setRuntimeAvailable(boolean runtimeAvailable) {
		this.runtimeAvailable = runtimeAvailable;
	}

	private static final class FakeSearchState implements ViewerSearchState<String> {
		private final CopyOnWriteArrayList<Consumer<ViewerSearchSnapshot<String>>> observers =
			new CopyOnWriteArrayList<>();
		private ViewerSearchSnapshot<String> snapshot;

		private FakeSearchState(ViewerSearchSnapshot<String> snapshot) {
			this.snapshot = snapshot;
		}

		@Override
		public ViewerSearchSnapshot<String> snapshot() {
			return snapshot;
		}

		@Override
		public ViewerRegistration observe(Consumer<ViewerSearchSnapshot<String>> observer) {
			observers.add(observer);
			return () -> observers.remove(observer);
		}
	}
}
