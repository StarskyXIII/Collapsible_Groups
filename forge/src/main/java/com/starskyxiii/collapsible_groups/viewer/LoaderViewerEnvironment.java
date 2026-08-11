package com.starskyxiii.collapsible_groups.viewer;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.List;

/** Forge-backed viewer detection for early mixin selection and runtime bootstrap. */
public final class LoaderViewerEnvironment {
	private LoaderViewerEnvironment() {}

	public static ViewerCompatibilityEnvironment detectEarly() {
		LoadingModList loading = LoadingModList.get();
		List<ModInfo> mods = loading == null ? List.of() : loading.getMods();
		return detect(mods.stream().filter(mod -> mod.getModId().equals(ViewerSelectionPolicy.JEI)).findFirst().orElse(null),
			mods.stream().anyMatch(mod -> mod.getModId().equals(ViewerSelectionPolicy.EMI)),
			mods.stream().anyMatch(mod -> mod.getModId().equals(ViewerSelectionPolicy.TMRV)));
	}

	public static ViewerCompatibilityEnvironment detectRuntime() {
		ModList mods = ModList.get();
		ArtifactVersion jeiVersion = mods.getModContainerById(ViewerSelectionPolicy.JEI)
			.map(container -> container.getModInfo().getVersion()).orElse(null);
		return detect(jeiVersion, mods.isLoaded(ViewerSelectionPolicy.EMI),
			mods.isLoaded(ViewerSelectionPolicy.TMRV));
	}

	private static ViewerCompatibilityEnvironment detect(ModInfo jei, boolean emi, boolean tmrv) {
		return detect(jei == null ? null : jei.getVersion(), emi, tmrv);
	}

	private static ViewerCompatibilityEnvironment detect(ArtifactVersion jei, boolean emi, boolean tmrv) {
		return ViewerCompatibilityEnvironment.detect(jei != null, emi, tmrv, () -> compare(jei));
	}

	private static ViewerCompatibilityEnvironment.JeiVersionCheck compare(ArtifactVersion detected) {
		if (detected == null) return ViewerCompatibilityEnvironment.JeiVersionCheck.unknown();
		String text = detected.toString();
		try {
			ArtifactVersion minimum = new DefaultArtifactVersion(ViewerCompatibilitySpec.minimumJeiVersion());
			return new ViewerCompatibilityEnvironment.JeiVersionCheck(text, detected.compareTo(minimum) >= 0);
		} catch (RuntimeException exception) {
			return new ViewerCompatibilityEnvironment.JeiVersionCheck(text, false);
		}
	}
}
