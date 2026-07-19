package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Selects and owns the single active recipe-viewer adapter for the process lifetime. */
public final class ViewerLifecycleCoordinator {
	public static final String JEI = "jei";
	public static final String EMI = "emi";
	public static final String TMRV_MOD_ID = "toomanyrecipeviewers";
	private static final Set<String> SUPPORTED_VIEWERS = Set.of(JEI);

	private final ViewerAdapterRegistry registry = new ViewerAdapterRegistry();
	private final Set<String> supportedViewerIds;
	private final Selection selection;
	private final Consumer<String> warnings;
	private ScriptedGroupBootstrap scriptedGroupBootstrap;

	public ViewerLifecycleCoordinator(Environment environment, Set<String> supportedViewerIds,
		Consumer<String> warnings) {
		this.warnings = Objects.requireNonNull(warnings, "warnings");
		this.supportedViewerIds = Set.copyOf(Objects.requireNonNull(supportedViewerIds, "supportedViewerIds"));
		this.selection = select(environment, this.supportedViewerIds);
		selection.warning().ifPresent(warnings);
	}

	public static ViewerLifecycleCoordinator global() { return GlobalHolder.INSTANCE; }

	public static boolean isJeiSelected() { return global().isSelected(JEI); }

	public boolean isSelected(String viewerId) {
		return selection.viewerId().filter(viewerId::equals).isPresent();
	}

	public Selection selection() { return selection; }

	/** Registers only the selected adapter; inactive viewer callbacks are deliberate no-ops. */
	public synchronized ViewerRegistration register(ViewerAdapter<?, ?> adapter) {
		if (!isSelected(adapter.id()) || !supportedViewerIds.contains(adapter.id())) return () -> {};
		return registry.register(adapter);
	}

	public synchronized void setScriptedGroupBootstrap(ScriptedGroupBootstrap bootstrap) {
		this.scriptedGroupBootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
	}

	/** Called when the selected adapter has published its initial ingredient universe. */
	public synchronized boolean activeUniverseReady(String viewerId, ViewerBootstrapContext<?> context) {
		if (!isSelected(viewerId) || !supportedViewerIds.contains(viewerId)) return false;
		if (scriptedGroupBootstrap == null || ScriptedGroupStore.isApplied()) return false;
		scriptedGroupBootstrap.apply(context);
		ScriptedGroupStore.markApplied();
		return true;
	}

	/**
	 * Selection is automatic and has no user-facing preference: the EMI ecosystem wins when a
	 * supported EMI adapter exists, otherwise real JEI. TMRV reimplements the JEI plugin API on
	 * top of EMI without JEI's gui internals, so its presence disqualifies the JEI path entirely.
	 */
	public static Selection select(Environment environment, Set<String> supportedViewerIds) {
		Objects.requireNonNull(environment, "environment");
		Set<String> supported = Set.copyOf(Objects.requireNonNull(supportedViewerIds, "supportedViewerIds"));
		boolean realJei = environment.jeiPresent() && !environment.tmrvPresent();
		boolean effectiveEmi = environment.emiPresent() || environment.tmrvPresent();
		boolean supportedJei = realJei && supported.contains(JEI);
		boolean supportedEmi = effectiveEmi && supported.contains(EMI);
		String selected = supportedEmi ? EMI : supportedJei ? JEI : null;
		String warning = null;
		if (selected == null && (realJei || effectiveEmi)) {
			warning = "Detected recipe viewer mods do not have a supported adapter in this version; "
				+ "no recipe viewer integration will be active.";
		}
		return new Selection(Optional.ofNullable(selected), Optional.ofNullable(warning));
	}

	public record Environment(boolean jeiPresent, boolean emiPresent, boolean tmrvPresent) {}
	public record Selection(Optional<String> viewerId, Optional<String> warning) {}

	@FunctionalInterface
	public interface ScriptedGroupBootstrap {
		void apply(ViewerBootstrapContext<?> context);
	}

	private static final class GlobalHolder {
		private static final ViewerLifecycleCoordinator INSTANCE = new ViewerLifecycleCoordinator(
			new Environment(Services.PLATFORM.isModLoaded(JEI), Services.PLATFORM.isModLoaded(EMI),
				Services.PLATFORM.isModLoaded(TMRV_MOD_ID)),
			SUPPORTED_VIEWERS,
			message -> Constants.LOG.warn("[CollapsibleGroups] {}", message)
		);
	}
}
