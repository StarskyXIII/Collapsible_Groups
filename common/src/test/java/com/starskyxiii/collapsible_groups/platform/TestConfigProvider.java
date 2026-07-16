package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;

public final class TestConfigProvider implements IConfigProvider {
	@Override
	public boolean loadDefaultGroups() {
		return false;
	}

	@Override
	public boolean loadGenericGroups() {
		return false;
	}

	@Override
	public boolean loadVanillaGroups() {
		return false;
	}

	@Override
	public boolean showManagerButton() {
		return false;
	}

	@Override
	public boolean showGroupBackgrounds() {
		return false;
	}

	@Override
	public boolean searchUngroupSmallGroups() {
		return false;
	}

	@Override
	public int searchUngroupThreshold() {
		return 0;
	}

	@Override
	public int collapsedGroupBackgroundColor() {
		return 0;
	}

	@Override
	public int expandedGroupBackgroundColor() {
		return 0;
	}

	@Override
	public int groupNameColor() {
		return 0;
	}

	@Override
	public int expandedGroupBorderColor() {
		return 0;
	}

	@Override
	public boolean debugTimingEnabled() {
		return false;
	}

	@Override
	public boolean debugStartupIndexVerificationEnabled() {
		return false;
	}

	@Override
	public boolean debugEditorIndexVerificationEnabled() {
		return false;
	}
}
