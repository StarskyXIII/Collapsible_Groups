package com.starskyxiii.collapsible_groups.viewer;

import java.util.Objects;
import java.util.function.Supplier;

/** Loader-neutral snapshot used by early mixin gates and client bootstrap. */
public record ViewerCompatibilityEnvironment(
	boolean jeiPresent,
	boolean emiPresent,
	boolean tmrvPresent,
	ViewerSelectionPolicy.Viewer selectedViewer,
	JeiVersionCheck jeiVersion
) {
	public ViewerCompatibilityEnvironment {
		Objects.requireNonNull(selectedViewer, "selectedViewer");
		Objects.requireNonNull(jeiVersion, "jeiVersion");
	}

	public static ViewerCompatibilityEnvironment detect(boolean jeiPresent, boolean emiPresent,
		boolean tmrvPresent, Supplier<JeiVersionCheck> selectedJeiVersion) {
		ViewerSelectionPolicy.Viewer selected = ViewerSelectionPolicy.select(
			jeiPresent, emiPresent, tmrvPresent, true, true);
		JeiVersionCheck version = selected == ViewerSelectionPolicy.Viewer.JEI
			? Objects.requireNonNull(selectedJeiVersion.get(), "selectedJeiVersion result")
			: JeiVersionCheck.skipped();
		return new ViewerCompatibilityEnvironment(jeiPresent, emiPresent, tmrvPresent, selected, version);
	}

	public boolean mayApplyJeiInternals() {
		return selectedViewer == ViewerSelectionPolicy.Viewer.JEI && jeiVersion.compatible();
	}

	public void requireCompatibleSelectedViewer() {
		if (selectedViewer != ViewerSelectionPolicy.Viewer.JEI || jeiVersion.compatible()) return;
		throw new IllegalStateException("Collapsible Groups cannot enable its JEI integration: detected JEI "
			+ jeiVersion.detectedVersion() + ", but version " + ViewerCompatibilitySpec.minimumJeiVersion()
			+ " or newer is required.");
	}

	public record JeiVersionCheck(String detectedVersion, boolean compatible) {
		public JeiVersionCheck {
			detectedVersion = Objects.requireNonNullElse(detectedVersion, "unknown");
		}

		public static JeiVersionCheck skipped() {
			return new JeiVersionCheck("not selected", true);
		}

		public static JeiVersionCheck unknown() {
			return new JeiVersionCheck("unknown", false);
		}
	}
}
