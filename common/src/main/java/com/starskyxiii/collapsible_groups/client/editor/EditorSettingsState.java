package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent settings-mode edit data owned by the loader-specific
 * {@code GroupEditorState} and read/written by the common
 * {@link EditorSettingsPanel}. Mirrors {@link EditorRulesState}: the panel keeps
 * transient UI state (modals, scroll, priority-edit buffer) itself and only
 * touches durable draft data through this interface.
 */
public interface EditorSettingsState {
	AppearanceDraft appearanceDraft();

	void setAppearanceDraft(AppearanceDraft draft);

	int editPriority();

	void setEditPriority(int priority);

	boolean editEnabled();

	void setEditEnabled(boolean enabled);

	@Nullable
	String editId();

	String pendingRawId();
}
