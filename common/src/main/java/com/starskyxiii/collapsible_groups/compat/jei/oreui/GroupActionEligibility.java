package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.Objects;

public record GroupActionEligibility(
	GroupSource source,
	boolean canRequestSwitch,
	EnabledPersistenceKind enabledPersistenceKind,
	boolean canEdit,
	boolean canDelete,
	boolean canShiftDelete,
	boolean canCopyAsCustom,
	boolean canBatchSelect,
	boolean canBatchRequestEnable,
	boolean canBatchRequestDisable,
	boolean canBatchDelete
) {
	public GroupActionEligibility {
		source = Objects.requireNonNull(source, "source");
		enabledPersistenceKind = Objects.requireNonNull(enabledPersistenceKind, "enabledPersistenceKind");
	}

	public static GroupActionEligibility forSource(GroupSource source) {
		GroupSource resolved = Objects.requireNonNull(source, "source");
		return switch (resolved) {
			case USER -> new GroupActionEligibility(
				resolved,
				true,
				EnabledPersistenceKind.GROUP_JSON,
				true,
				true,
				true,
				false,
				true,
				true,
				true,
				true
			);
			case BUILTIN, KUBEJS -> new GroupActionEligibility(
				resolved,
				true,
				EnabledPersistenceKind.ENABLED_OVERRIDE_STORE,
				false,
				false,
				false,
				true,
				true,
				true,
				true,
				false
			);
		};
	}

	public boolean canRequest(GroupAction action) {
		return switch (Objects.requireNonNull(action, "action")) {
			case SWITCH_ENABLED -> canRequestSwitch;
			case EDIT -> canEdit;
			case DELETE -> canDelete;
			case SHIFT_DELETE -> canShiftDelete;
			case COPY_AS_CUSTOM -> canCopyAsCustom;
			case BATCH_SELECT -> canBatchSelect;
			case BATCH_ENABLE -> canBatchRequestEnable;
			case BATCH_DISABLE -> canBatchRequestDisable;
			case BATCH_DELETE -> canBatchDelete;
		};
	}

	public boolean switchRequiresEnabledOverrideStore() {
		return canRequestSwitch && enabledPersistenceKind.requiresOverrideStore();
	}
}
