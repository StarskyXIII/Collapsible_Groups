package com.starskyxiii.collapsible_groups.client.editor;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EditorPerformanceTraceTest {
	@TempDir Path temporary;

	@Test void recordsWholeInteractionAndFollowingRenderOnlyWhileRecording() throws Exception {
		var trace = new EditorPerformanceTrace();
		assertNull(trace.beginFrame("CONTENTS", 252, true));
		assertNull(trace.beginClick("CONTENTS", 252));
		Path file = temporary.resolve("editor.jfr");
		try (var recording = new Recording()) {
			recording.enable("collapsible_groups.EditorFrame").withoutThreshold();
			recording.enable("collapsible_groups.EditorClick").withoutThreshold();
			recording.start();
			var click = trace.beginClick("CONTENTS", 252);
			trace.endClick(click);
			trace.endFrame(trace.beginFrame("CONTENTS", 253, true));
			trace.endFrame(trace.beginFrame("CONTENTS", 253, true));
			recording.stop();
			recording.dump(file);
		}
		var events = RecordingFile.readAllEvents(file);
		var clicks = events.stream().filter(event -> event.getEventType().getName().endsWith("EditorClick")).toList();
		var frames = events.stream().filter(event -> event.getEventType().getName().endsWith("EditorFrame")).toList();
		assertEquals(1, clicks.size());
		assertEquals(2, frames.size());
		assertEquals(252, clicks.get(0).getInt("members"));
		assertEquals(253, frames.get(0).getInt("members"));
		assertTrue(frames.get(0).getLong("clickToRenderEnd") > 0);
		assertEquals(0, frames.get(frames.size() - 1).getLong("clickToRenderEnd"));
		assertTrue(frames.get(frames.size() - 1).getLong("interval") > 0);
		assertNull(trace.beginFrame("CONTENTS", 253, true));
	}
}
