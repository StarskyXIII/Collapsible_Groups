package com.starskyxiii.collapsible_groups.client.editor;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

final class EditorPerformanceTrace {
	private static final EventType FRAME = EventType.getEventType(Frame.class);
	private static final EventType CLICK = EventType.getEventType(Click.class);
	private long previousFrame;
	private long pendingClick;

	Frame beginFrame(String mode, int members, boolean focused) {
		if (!FRAME.isEnabled()) {
			previousFrame = 0;
			pendingClick = 0;
			return null;
		}
		long now = System.nanoTime();
		Frame frame = new Frame();
		frame.mode = mode;
		frame.members = members;
		frame.focused = focused;
		frame.interval = previousFrame == 0 ? 0 : now - previousFrame;
		previousFrame = now;
		frame.begin();
		return frame;
	}

	void endFrame(Frame frame) {
		if (frame == null) return;
		frame.end();
		if (pendingClick != 0) {
			frame.clickToRenderEnd = System.nanoTime() - pendingClick;
			pendingClick = 0;
		}
		frame.commit();
	}

	Click beginClick(String mode, int members) {
		if (!CLICK.isEnabled()) return null;
		Click click = new Click();
		click.mode = mode;
		click.members = members;
		pendingClick = System.nanoTime();
		click.begin();
		return click;
	}

	void endClick(Click click) {
		if (click == null) return;
		click.end();
		click.commit();
	}

	@Name("collapsible_groups.EditorFrame")
	@Category("Collapsible Groups")
	@StackTrace(false)
	static final class Frame extends Event {
		public String mode;
		public int members;
		public boolean focused;
		@Timespan(Timespan.NANOSECONDS) public long interval;
		@Timespan(Timespan.NANOSECONDS) public long clickToRenderEnd;
	}

	@Name("collapsible_groups.EditorClick")
	@Category("Collapsible Groups")
	@StackTrace(false)
	static final class Click extends Event {
		public String mode;
		public int members;
	}
}
