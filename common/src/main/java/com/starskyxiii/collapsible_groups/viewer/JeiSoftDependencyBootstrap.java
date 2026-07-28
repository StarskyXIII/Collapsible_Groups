package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.Constants;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Predicate;

/** Loads JEI-specific optional-mod bridges only after JEI wins viewer selection. */
public final class JeiSoftDependencyBootstrap {
	public record Registration(String modId, String loaderClassName) {}

	private JeiSoftDependencyBootstrap() {}

	public static void registerSelected(
		boolean jeiSelected,
		Predicate<String> modLoaded,
		List<Registration> registrations
	) {
		if (!jeiSelected) return;
		for (Registration registration : registrations) {
			if (!modLoaded.test(registration.modId())) continue;
			invoke(registration);
		}
	}

	private static void invoke(Registration registration) {
		try {
			Class.forName(registration.loaderClassName()).getMethod("register").invoke(null);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof LinkageError linkageError) {
				Constants.LOG.warn("[CollapsibleGroups] Could not link JEI soft dependency loader {}: {}",
					registration.loaderClassName(), linkageError.toString());
			} else {
				Constants.LOG.warn("[CollapsibleGroups] JEI soft dependency loader {} failed: {}",
					registration.loaderClassName(), cause == null ? exception.toString() : cause.toString());
			}
		} catch (ReflectiveOperationException | LinkageError exception) {
			Constants.LOG.warn("[CollapsibleGroups] Could not load JEI soft dependency bridge {}: {}",
				registration.loaderClassName(), exception.toString());
		}
	}
}
