package com.starskyxiii.collapsible_groups.linkage;

import com.starskyxiii.collapsible_groups.group.GroupRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Resolves pure-EMI entry points while making every JEI class unavailable. */
class EmiOnlyLinkageTest {
	private static final List<String> ROOTS = List.of(
		"com.starskyxiii.collapsible_groups.group.GroupRepository",
		"com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator",
		"com.starskyxiii.collapsible_groups.viewer.JeiSoftDependencyBootstrap",
		"com.starskyxiii.collapsible_groups.compat.kubejs.KnownRecipeViewerTypeIds",
		"com.starskyxiii.collapsible_groups.compat.emi.CollapsibleGroupsEmiPlugin",
		"com.starskyxiii.collapsible_groups.compat.emi.EmiViewerAdapter",
		"com.starskyxiii.collapsible_groups.compat.emi.EmiEditorRuntimeAccess",
		"com.starskyxiii.collapsible_groups.compat.emi.EmiOverlayController",
		"com.starskyxiii.collapsible_groups.compat.emi.GroupHeaderEmiStack",
		"com.starskyxiii.collapsible_groups.compat.jei.manager.GroupManagerCard",
		"com.starskyxiii.collapsible_groups.compat.jei.manager.GroupManagerScreen",
		"com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry",
		"com.starskyxiii.collapsible_groups.client.preview.GroupSampleRenderer",
		"com.starskyxiii.collapsible_groups.client.preview.PreviewTooltipComponent"
	);

	@Test
	void sharedAndEmiClassesResolveWithoutJei() {
		assertDoesNotThrow(() -> {
			URL mainOutput = GroupRepository.class.getProtectionDomain().getCodeSource().getLocation();
			try (NoJeiClassLoader loader = new NoJeiClassLoader(mainOutput, getClass().getClassLoader())) {
				for (String root : ROOTS) reflectSurface(Class.forName(root, false, loader));
			}
		});
	}

	private static void reflectSurface(Class<?> type) {
		type.getDeclaredFields();
		type.getDeclaredConstructors();
		type.getDeclaredMethods();
		RecordComponent[] components = type.getRecordComponents();
		if (components != null) {
			for (RecordComponent component : components) {
				component.getType();
				component.getGenericType();
				component.getAccessor();
			}
		}
	}

	private static final class NoJeiClassLoader extends URLClassLoader {
		NoJeiClassLoader(URL mainOutput, ClassLoader parent) {
			super(new URL[]{mainOutput}, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.startsWith("mezz.jei.")) throw new ClassNotFoundException("JEI denied: " + name);
			if (name.startsWith("com.starskyxiii.collapsible_groups.")) {
				synchronized (getClassLoadingLock(name)) {
					Class<?> loaded = findLoadedClass(name);
					if (loaded == null) {
						try {
							loaded = findClass(name);
						} catch (ClassNotFoundException ignored) {
							loaded = super.loadClass(name, false);
						}
					}
					if (resolve) resolveClass(loaded);
					return loaded;
				}
			}
			return super.loadClass(name, resolve);
		}
	}
}
