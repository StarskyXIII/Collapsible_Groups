package com.starskyxiii.collapsible_groups.viewer;

/** Viewer-neutral policy for favorites/bookmarks. */
public interface ViewerBookmarkPolicy<E> {
	boolean canBookmarkGroupHeader();

	default boolean canBookmark(ViewerProjection.DisplayEntry<E> entry) {
		return !(entry instanceof ViewerProjection.DisplayHeader<?>) || canBookmarkGroupHeader();
	}

	static <E> ViewerBookmarkPolicy<E> headersCannotBeBookmarked() {
		return () -> false;
	}
}
