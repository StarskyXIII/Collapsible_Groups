package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.List;
import java.util.Optional;

interface EditorGroupAccess {
	List<GroupDefinition> allGroups();
	Optional<GroupDefinition> findGroup(String id);
	void saveQuietly(GroupDefinition definition);

    default boolean saveChecked(GroupDefinition definition) {
        saveQuietly(definition);
        return true;
    }
	String sanitizeGeneratedIdBase(String name);
	String generateUniqueId(String name);
	String generateUniqueIdIncludingKubeJs(String name);
	void notifyViewer();
	boolean setEnabledQuietlyWithoutEvent(String id, boolean enabled);
}
