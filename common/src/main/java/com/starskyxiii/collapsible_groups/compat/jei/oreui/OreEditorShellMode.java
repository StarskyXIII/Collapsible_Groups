package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;

public enum OreEditorShellMode {
	CONTENTS(ModTranslationKeys.EDITOR_TAB_CONTENTS, true, false, false),
	RULES(ModTranslationKeys.EDITOR_TAB_RULES, false, true, false),
	LOOK(ModTranslationKeys.ORE_EDITOR_MODE_LOOK, false, false, true);

	private final String labelKey;
	private final boolean contentControlsEnabled;
	private final boolean rulesPanelEnabled;
	private final boolean appearancePanelEnabled;

	OreEditorShellMode(
		String labelKey,
		boolean contentControlsEnabled,
		boolean rulesPanelEnabled,
		boolean appearancePanelEnabled
	) {
		this.labelKey = labelKey;
		this.contentControlsEnabled = contentControlsEnabled;
		this.rulesPanelEnabled = rulesPanelEnabled;
		this.appearancePanelEnabled = appearancePanelEnabled;
	}

	public String labelKey() {
		return labelKey;
	}

	public boolean contentControlsEnabled() {
		return contentControlsEnabled;
	}

	public boolean rulesPanelEnabled() {
		return rulesPanelEnabled;
	}

	public boolean appearancePanelEnabled() {
		return appearancePanelEnabled;
	}

	public boolean rightPreviewEnabled() {
		return true;
	}
}
