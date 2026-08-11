package com.starskyxiii.collapsible_groups.client.preview;

/** Viewer-neutral slot position used by same-frame group rendering passes. */
public record GroupSlotPosition(Kind kind, String groupId, int x, int y) {
	public enum Kind {
		HEADER,
		CHILD
	}
}
