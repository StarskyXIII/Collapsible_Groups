package com.starskyxiii.collapsible_groups.viewer;

import java.util.function.Consumer;

/** Observable viewer search/filter state. */
public interface ViewerSearchState<E> {
	ViewerSearchSnapshot<E> snapshot();

	ViewerRegistration observe(Consumer<ViewerSearchSnapshot<E>> observer);
}
