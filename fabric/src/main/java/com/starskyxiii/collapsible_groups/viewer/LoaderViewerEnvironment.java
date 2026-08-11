package com.starskyxiii.collapsible_groups.viewer;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/** Fabric-backed viewer detection safe to call from the mixin selection phase. */
public final class LoaderViewerEnvironment {
	private LoaderViewerEnvironment() {}

	public static ViewerCompatibilityEnvironment detect() {
		FabricLoader loader = FabricLoader.getInstance();
		boolean jei = loader.isModLoaded(ViewerSelectionPolicy.JEI);
		boolean emi = loader.isModLoaded(ViewerSelectionPolicy.EMI);
		boolean tmrv = loader.isModLoaded(ViewerSelectionPolicy.TMRV);
		return ViewerCompatibilityEnvironment.detect(jei, emi, tmrv, () -> checkJei(loader));
	}

	private static ViewerCompatibilityEnvironment.JeiVersionCheck checkJei(FabricLoader loader) {
		return loader.getModContainer(ViewerSelectionPolicy.JEI)
			.map(ModContainer::getMetadata)
			.map(metadata -> compare(metadata.getVersion()))
			.orElseGet(ViewerCompatibilityEnvironment.JeiVersionCheck::unknown);
	}

	private static ViewerCompatibilityEnvironment.JeiVersionCheck compare(Version detected) {
		String text = detected.getFriendlyString();
		try {
			VersionPredicate required = VersionPredicate.parse(">=" + ViewerCompatibilitySpec.minimumJeiVersion());
			return new ViewerCompatibilityEnvironment.JeiVersionCheck(text, required.test(detected));
		} catch (VersionParsingException exception) {
			return new ViewerCompatibilityEnvironment.JeiVersionCheck(text, false);
		}
	}
}
