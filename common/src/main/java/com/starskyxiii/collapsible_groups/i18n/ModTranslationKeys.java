package com.starskyxiii.collapsible_groups.i18n;

/** Centralised translation-key constants for all UI strings. */
public final class ModTranslationKeys {
	private ModTranslationKeys() {}

	// -----------------------------------------------------------------------
	// Screen titles
	// -----------------------------------------------------------------------
	public static final String SCREEN_TITLE      = "collapsible_groups.screen.title";
	public static final String SCREEN_NEW_GROUP  = "collapsible_groups.screen.new_group";
	public static final String SCREEN_EDIT_GROUP = "collapsible_groups.screen.edit_group";

	// -----------------------------------------------------------------------
	// Buttons
	// -----------------------------------------------------------------------
	public static final String BUTTON_MANAGE_TOOLTIP = "collapsible_groups.button.manage_tooltip";
	public static final String BUTTON_SAVE           = "collapsible_groups.button.save";
	public static final String BUTTON_CANCEL         = "collapsible_groups.button.cancel";

	// -----------------------------------------------------------------------
	// Group header tooltip (JEI overlay)
	// -----------------------------------------------------------------------
	public static final String TOOLTIP_EXPAND   = "collapsible_groups.tooltip.expand";
	public static final String TOOLTIP_COLLAPSE = "collapsible_groups.tooltip.collapse";

	// -----------------------------------------------------------------------
	// Editor fields
	// -----------------------------------------------------------------------
	public static final String EDITOR_NAME_LABEL   = "collapsible_groups.editor.name_label";
	public static final String EDITOR_SEARCH_LABEL = "collapsible_groups.editor.search_label";
	public static final String EDITOR_NAME_HINT    = "collapsible_groups.editor.name_hint";
	public static final String EDITOR_SEARCH_HINT  = "collapsible_groups.editor.search_hint";

	// -----------------------------------------------------------------------
	// Editor validation
	// -----------------------------------------------------------------------
	public static final String EDITOR_SAVE_ERROR = "collapsible_groups.editor.save_error";

	// -----------------------------------------------------------------------
	// "Already in group" tooltip lines
	// -----------------------------------------------------------------------
	public static final String EDITOR_ALREADY_IN_GROUP  = "collapsible_groups.editor.already_in_group";
	public static final String EDITOR_ALREADY_IN_GROUPS = "collapsible_groups.editor.already_in_groups";
	/** Args: count (int) */
	public static final String EDITOR_MORE_GROUPS       = "collapsible_groups.editor.more_groups";

	// -----------------------------------------------------------------------
	// Tag-match indicator
	// -----------------------------------------------------------------------
	public static final String EDITOR_TAG_MATCHED = "collapsible_groups.editor.tag_matched";

	// -----------------------------------------------------------------------
	// Editor item action hints - left panel
	// -----------------------------------------------------------------------
	public static final String EDITOR_HINT_SWITCH_TO_VARIANT = "collapsible_groups.editor.hint.switch_to_variant";
	public static final String EDITOR_HINT_DRAG_REMOVE       = "collapsible_groups.editor.hint.drag_remove";
	public static final String EDITOR_HINT_CTRL_REMOVE_ALL   = "collapsible_groups.editor.hint.ctrl_remove_all";
	public static final String EDITOR_HINT_REMOVE_THIS       = "collapsible_groups.editor.hint.remove_this";
	public static final String EDITOR_HINT_CTRL_SELECT_ALL   = "collapsible_groups.editor.hint.ctrl_select_all";
	public static final String EDITOR_HINT_ADD_THIS          = "collapsible_groups.editor.hint.add_this";
	public static final String EDITOR_HINT_DRAG_ADD          = "collapsible_groups.editor.hint.drag_add";
	public static final String EDITOR_HINT_CTRL_ADD_ALL      = "collapsible_groups.editor.hint.ctrl_add_all";

	// -----------------------------------------------------------------------
	// Editor item action hints - right panel
	// -----------------------------------------------------------------------
	public static final String EDITOR_HINT_REMOVE_ONLY_VARIANT = "collapsible_groups.editor.hint.remove_only_variant";

	// -----------------------------------------------------------------------
	// Editor fluid / generic action hints (NeoForge)
	// -----------------------------------------------------------------------
	public static final String EDITOR_HINT_CLICK_REMOVE_FROM_GROUP = "collapsible_groups.editor.hint.click_remove_from_group";
	public static final String EDITOR_HINT_CLICK_ADD_TO_GROUP      = "collapsible_groups.editor.hint.click_add_to_group";
	public static final String EDITOR_HINT_DRAG_REMOVE_FLUIDS      = "collapsible_groups.editor.hint.drag_remove_fluids";
	public static final String EDITOR_HINT_DRAG_ADD_FLUIDS         = "collapsible_groups.editor.hint.drag_add_fluids";
	public static final String EDITOR_HINT_DRAG_REMOVE_ENTRIES     = "collapsible_groups.editor.hint.drag_remove_entries";
	public static final String EDITOR_HINT_DRAG_ADD_ENTRIES        = "collapsible_groups.editor.hint.drag_add_entries";

	// -----------------------------------------------------------------------
	// Count labels - args: count (int passed as %s)
	// -----------------------------------------------------------------------
	public static final String COUNT_ITEMS   = "collapsible_groups.count.items";
	public static final String COUNT_FLUIDS  = "collapsible_groups.count.fluids";
	public static final String COUNT_ENTRIES = "collapsible_groups.count.entries";

	// -----------------------------------------------------------------------
	// Config screen - general
	// -----------------------------------------------------------------------
	/** Args: mod name (%s) */
	public static final String CONFIG_MOD_NOT_INSTALLED = "collapsible_groups.config.mod_not_installed";

	public static final String CONFIG_SCREEN_TITLE = "collapsible_groups.config.screen_title";

	// Section headers
	public static final String CONFIG_SECTION_DEFAULT_GROUPS  = "collapsible_groups.config.section.default_groups";
	public static final String CONFIG_SECTION_MOD_INTEGRATION = "collapsible_groups.config.section.mod_integration";
	public static final String CONFIG_SECTION_UI              = "collapsible_groups.config.section.ui";
	public static final String CONFIG_SECTION_DEBUG           = "collapsible_groups.config.section.debug";

	// Option names
	public static final String CONFIG_OPT_DEFAULT_GROUPS_ENABLED  = "collapsible_groups.config.opt.default_groups_enabled";
	public static final String CONFIG_OPT_LOAD_GENERIC            = "collapsible_groups.config.opt.load_generic";
	public static final String CONFIG_OPT_LOAD_VANILLA            = "collapsible_groups.config.opt.load_vanilla";
	public static final String CONFIG_OPT_LOAD_MOD_INTEGRATION    = "collapsible_groups.config.opt.load_mod_integration";
	public static final String CONFIG_OPT_LOAD_CHIPPED            = "collapsible_groups.config.opt.load_chipped";
	public static final String CONFIG_OPT_LOAD_RECHISELED         = "collapsible_groups.config.opt.load_rechiseled";
	public static final String CONFIG_OPT_LOAD_RS2                = "collapsible_groups.config.opt.load_rs2";
	public static final String CONFIG_OPT_LOAD_MACAWS_SERIES      = "collapsible_groups.config.opt.load_macaws_series";
	public static final String CONFIG_OPT_SHOW_MANAGER_BUTTON     = "collapsible_groups.config.opt.show_manager_button";
	public static final String CONFIG_OPT_TIMING_LOGS             = "collapsible_groups.config.opt.timing_logs";
	public static final String CONFIG_OPT_VERIFY_STARTUP_INDEX    = "collapsible_groups.config.opt.verify_startup_index";
	public static final String CONFIG_OPT_VERIFY_EDITOR_INDEX     = "collapsible_groups.config.opt.verify_editor_index";

	// Toggle values
	public static final String CONFIG_VAL_ON  = "collapsible_groups.config.val.on";
	public static final String CONFIG_VAL_OFF = "collapsible_groups.config.val.off";

	// -----------------------------------------------------------------------
	// Group Manager screen - buttons and labels
	// -----------------------------------------------------------------------
	public static final String MANAGER_BTN_BACK           = "collapsible_groups.manager.btn_back";
	public static final String MANAGER_BTN_FILTER_BUILTIN = "collapsible_groups.manager.btn_filter_builtin";
	public static final String MANAGER_BTN_FILTER_KUBEJS  = "collapsible_groups.manager.btn_filter_kubejs";
	public static final String MANAGER_BTN_NEW_GROUP      = "collapsible_groups.manager.btn_new_group";
	/** Args: total count (%s) */
	public static final String MANAGER_COUNT_ALL          = "collapsible_groups.manager.count_all";
	/** Args: filtered count (%s), total count (%s) */
	public static final String MANAGER_COUNT_FILTERED     = "collapsible_groups.manager.count_filtered";
	public static final String MANAGER_FOOTER_HINT        = "collapsible_groups.manager.footer_hint";
	public static final String MANAGER_BTN_ENABLED        = "collapsible_groups.manager.btn_enabled";
	public static final String MANAGER_BTN_DISABLED       = "collapsible_groups.manager.btn_disabled";
	public static final String MANAGER_BTN_EDIT           = "collapsible_groups.manager.btn_edit";
	public static final String MANAGER_BTN_DELETE         = "collapsible_groups.manager.btn_delete";
	public static final String MANAGER_BADGE_BUILTIN      = "collapsible_groups.manager.badge_builtin";
	public static final String MANAGER_BADGE_KUBEJS       = "collapsible_groups.manager.badge_kubejs";
	/** Args: group name (%s) */
	public static final String MANAGER_PREFIX_BUILTIN     = "collapsible_groups.manager.prefix_builtin";
	/** Args: group name (%s) */
	public static final String MANAGER_PREFIX_KUBEJS      = "collapsible_groups.manager.prefix_kubejs";

	// -----------------------------------------------------------------------
	// Editor - browser tabs
	// -----------------------------------------------------------------------
	public static final String EDITOR_TAB_ITEMS    = "collapsible_groups.editor.tab.items";
	public static final String EDITOR_TAB_FLUIDS   = "collapsible_groups.editor.tab.fluids";
	public static final String EDITOR_TAB_GENERIC  = "collapsible_groups.editor.tab.generic";
	public static final String EDITOR_TAB_CONTENTS = "collapsible_groups.editor.tab.contents";
	public static final String EDITOR_TAB_RULES    = "collapsible_groups.editor.tab.rules";

	// -----------------------------------------------------------------------
	// Editor - chips and panel headers
	// -----------------------------------------------------------------------
	public static final String EDITOR_CHIP_HIDE_USED = "collapsible_groups.editor.chip.hide_used";
	/** Args: count (%s) */
	public static final String EDITOR_SUMMARY_ITEMS   = "collapsible_groups.editor.summary.items";
	/** Args: count (%s) */
	public static final String EDITOR_SUMMARY_FLUIDS  = "collapsible_groups.editor.summary.fluids";
	/** Args: count (%s) */
	public static final String EDITOR_SUMMARY_GENERIC = "collapsible_groups.editor.summary.generic";
	/** Args: count (%s) */
	public static final String EDITOR_PANEL_ITEMS_HEADER    = "collapsible_groups.editor.panel.items_header";
	/** Args: count (%s) */
	public static final String EDITOR_PANEL_FLUIDS_HEADER   = "collapsible_groups.editor.panel.fluids_header";
	/** Args: count (%s) */
	public static final String EDITOR_PANEL_GENERIC_HEADER  = "collapsible_groups.editor.panel.generic_header";
	/** Args: summary string (%s) */
	public static final String EDITOR_PANEL_CONTENTS_HEADER = "collapsible_groups.editor.panel.contents_header";
	public static final String EDITOR_PANEL_RULES_HEADER    = "collapsible_groups.editor.panel.rules_header";
	/** Args: count (%s) */
	public static final String EDITOR_PANEL_COUNT_ENTRIES   = "collapsible_groups.editor.panel.count_entries";

	// -----------------------------------------------------------------------
	// Editor - Rules panel section titles
	// -----------------------------------------------------------------------
	public static final String EDITOR_RULES_STATUS          = "collapsible_groups.editor.rules.status";
	public static final String EDITOR_RULES_INTERNAL_ID     = "collapsible_groups.editor.rules.internal_id";
	public static final String EDITOR_RULES_UNSUPPORTED     = "collapsible_groups.editor.rules.unsupported_nodes";
	public static final String EDITOR_RULES_REASON          = "collapsible_groups.editor.rules.reason";
	public static final String EDITOR_RULES_SUMMARY         = "collapsible_groups.editor.rules.summary";
	public static final String EDITOR_RULES_CLAUSES         = "collapsible_groups.editor.rules.clauses";
	public static final String EDITOR_RULES_PREVIEW_NOTE    = "collapsible_groups.editor.rules.preview_note";
	public static final String EDITOR_RULES_NO_FILTER       = "collapsible_groups.editor.rules.no_filter";

	// -----------------------------------------------------------------------
	// Editor - filter status and save-blocked reasons
	// -----------------------------------------------------------------------
	public static final String EDITOR_FILTER_EDITABLE    = "collapsible_groups.editor.filter.editable";
	public static final String EDITOR_FILTER_READONLY    = "collapsible_groups.editor.filter.readonly";
	public static final String EDITOR_SAVE_BLOCKED_NO_NAME   = "collapsible_groups.editor.save_blocked.no_name";
	/** Args: reason (%s) */
	public static final String EDITOR_SAVE_BLOCKED_READONLY  = "collapsible_groups.editor.save_blocked.readonly";
	public static final String EDITOR_SAVE_BLOCKED_NO_FILTER = "collapsible_groups.editor.save_blocked.no_filter";

	// -----------------------------------------------------------------------
	// Editor - unsupported filter node reasons
	// -----------------------------------------------------------------------
	public static final String EDITOR_UNSUPPORTED_EDITABLE    = "collapsible_groups.editor.unsupported.editable";
	public static final String EDITOR_UNSUPPORTED_UNAVAILABLE = "collapsible_groups.editor.unsupported.unavailable";
	public static final String EDITOR_UNSUPPORTED_NONE        = "collapsible_groups.editor.unsupported.none";

	// Unsupported node kind labels and reason texts
	public static final String EDITOR_UNSUPPORTED_NODE_ALL_LABEL       = "collapsible_groups.editor.unsupported_node.all.label";
	public static final String EDITOR_UNSUPPORTED_NODE_ALL_REASON      = "collapsible_groups.editor.unsupported_node.all.reason";
	public static final String EDITOR_UNSUPPORTED_NODE_NOT_LABEL       = "collapsible_groups.editor.unsupported_node.not.label";
	public static final String EDITOR_UNSUPPORTED_NODE_NOT_REASON      = "collapsible_groups.editor.unsupported_node.not.reason";
	public static final String EDITOR_UNSUPPORTED_NODE_BLOCK_TAG_LABEL  = "collapsible_groups.editor.unsupported_node.block_tag.label";
	public static final String EDITOR_UNSUPPORTED_NODE_BLOCK_TAG_REASON = "collapsible_groups.editor.unsupported_node.block_tag.reason";
	public static final String EDITOR_UNSUPPORTED_NODE_NAMESPACE_LABEL  = "collapsible_groups.editor.unsupported_node.namespace.label";
	public static final String EDITOR_UNSUPPORTED_NODE_NAMESPACE_REASON = "collapsible_groups.editor.unsupported_node.namespace.reason";
	public static final String EDITOR_UNSUPPORTED_NODE_NESTED_LABEL    = "collapsible_groups.editor.unsupported_node.nested.label";
	public static final String EDITOR_UNSUPPORTED_NODE_NESTED_REASON   = "collapsible_groups.editor.unsupported_node.nested.reason";
	public static final String EDITOR_UNSUPPORTED_NODE_HAS_COMPONENT_LABEL  = "collapsible_groups.editor.unsupported_node.has_component.label";
	public static final String EDITOR_UNSUPPORTED_NODE_HAS_COMPONENT_REASON = "collapsible_groups.editor.unsupported_node.has_component.reason";
	public static final String EDITOR_UNSUPPORTED_NODE_COMPONENT_PATH_LABEL  = "collapsible_groups.editor.unsupported_node.component_path.label";
	public static final String EDITOR_UNSUPPORTED_NODE_COMPONENT_PATH_REASON = "collapsible_groups.editor.unsupported_node.component_path.reason";

	// -----------------------------------------------------------------------
	// Editor - preview ownership note
	// -----------------------------------------------------------------------
	public static final String EDITOR_PREVIEW_NOTE = "collapsible_groups.editor.preview_note";

	// -----------------------------------------------------------------------
	// Command feedback - /cg group_key dump
	// -----------------------------------------------------------------------
	public static final String COMMAND_DUMP_SUCCESS = "collapsible_groups.command.dump_success";
	public static final String COMMAND_DUMP_EMPTY   = "collapsible_groups.command.dump_empty";
	public static final String COMMAND_DUMP_ERROR   = "collapsible_groups.command.dump_error";
	/** Args: removedCount (%s) */
	public static final String COMMAND_DUMP_CLEANED = "collapsible_groups.command.dump_cleaned";

	// -----------------------------------------------------------------------
	// Editor - pending internal ID labels (NeoForge)
	// -----------------------------------------------------------------------
	public static final String EDITOR_PENDING_ID_GENERATING     = "collapsible_groups.editor.pending_id.generating";
	/** Args: id (%s) */
	public static final String EDITOR_PENDING_ID_EXISTING       = "collapsible_groups.editor.pending_id.existing";
	/** Args: id (%s) */
	public static final String EDITOR_PENDING_ID_ON_SAVE        = "collapsible_groups.editor.pending_id.on_save";
	/** Args: id (%s) */
	public static final String EDITOR_PENDING_ID_ON_SAVE_GEN    = "collapsible_groups.editor.pending_id.on_save_generated";
}
