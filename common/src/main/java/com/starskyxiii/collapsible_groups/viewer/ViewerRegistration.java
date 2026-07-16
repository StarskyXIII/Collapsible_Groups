package com.starskyxiii.collapsible_groups.viewer;

/** A removable viewer registration or observation. */
@FunctionalInterface
public interface ViewerRegistration extends AutoCloseable {
	@Override
	void close();
}
