package com.starskyxiii.collapsible_groups.compat.emi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmiInputDispatchRegressionTest {
	@Test void overlayDispatchHasNoReleaseHookAndOnlyConsumesAHitMouseClick() throws IOException {
		String controller = source("common/src/main/java/com/starskyxiii/collapsible_groups/compat/emi/EmiOverlayController.java");
		assertFalse(controller.contains("MOUSE_RELEASE"));
		assertFalse(controller.contains("mouseReleased("));
		assertTrue(controller.contains("case MOUSE_CLICK -> button.mouseClicked"));
		assertTrue(controller.contains("case KEY_PRESS -> false"), "keys outside and inside the button must pass through");
		assertTrue(controller.contains("!visible || !enabled"), "hidden, disabled, and external clicks pass through");
	}

	@Test void loaderHooksCannotCancelNativeReleaseAndRemainingInputTargetsAreGuarded() throws IOException {
		for (String loader : new String[]{"fabric", "neoforge"}) {
			String mixin = source(loader + "/src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinEmiScreenManager.java");
			assertFalse(mixin.contains("method = \"mouseReleased\""));
			assertFalse(mixin.contains("Input.Type.MOUSE_RELEASE"));
			assertTrue(mixin.contains("method = \"mouseClicked\", at = @At(\"HEAD\"), cancellable = true, require = 1"));
			assertTrue(mixin.contains("method = \"keyPressed\", at = @At(\"HEAD\"), cancellable = true, require = 1"));
			assertFalse(mixin.contains("method = \"mouseClicked\", at = @At(\"HEAD\"), cancellable = true, require = 0"));
			assertEquals(1, occurrences(mixin, "ViewerOverlayHook.Input.Type.MOUSE_CLICK"),
				"the button click must be dispatched and consumed at most once");
		}
	}

	@Test void modifiedHeaderLeftClickUsesTheRejectedOtherPath() throws IOException {
		String bind = source(".reference/emi-1.21/xplat/src/main/java/dev/emi/emi/input/EmiBind.java");
		String mixin = source("fabric/src/main/java/com/starskyxiii/collapsible_groups/mixin/MixinEmiScreenManager.java");
		assertTrue(bind.contains("LEFT_CLICK = new EmiBind(\"\", new EmiBind.ModifiedKey"));
		assertTrue(bind.contains("createFromCode(0), 0)"), "LEFT_CLICK has a zero-modifier binding");
		assertTrue(mixin.contains("function.apply(EmiBind.LEFT_CLICK)"));
		assertTrue(mixin.contains("EmiHeaderInteractionPolicy.Action.OTHER"));
	}

	@Test void upstreamReleaseStillOwnsInventoryDragCheatAndFinallyCleanupDispatch() throws IOException {
		String manager = source(".reference/emi-1.21/xplat/src/main/java/dev/emi/emi/screen/EmiScreenManager.java");
		String mouse = source(".reference/emi-1.21/xplat/src/main/java/dev/emi/emi/mixin/MouseMixin.java");
		assertTrue(manager.contains("public static boolean mouseReleased"));
		assertTrue(manager.contains("stackInteraction(hovered"), "native take/place/shift/cheat/drop dispatch remains reachable");
		assertTrue(manager.contains("pressedStack = EmiStack.EMPTY;"));
		assertTrue(manager.contains("draggedStack = EmiStack.EMPTY;"));
		assertTrue(mouse.contains("EmiScreenManager.mouseReleased(mx, my, button)"));
	}

	private static String source(String relative) throws IOException {
		Path root = Path.of(System.getProperty("user.dir"));
		Path path = root.resolve(relative);
		if (!Files.exists(path) && root.getParent() != null) path = root.getParent().resolve(relative);
		return Files.readString(path);
	}

	private static int occurrences(String value, String needle) {
		return (value.length() - value.replace(needle, "").length()) / needle.length();
	}
}
