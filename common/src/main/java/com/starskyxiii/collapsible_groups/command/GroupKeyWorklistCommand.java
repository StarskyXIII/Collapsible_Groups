package com.starskyxiii.collapsible_groups.command;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.group.GroupEvaluation;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class GroupKeyWorklistCommand {
    private GroupKeyWorklistCommand() {}

    public static int export(String locale, boolean missingOnly, Consumer<Component> feedback) {
        try {
            TargetLocaleEntries.validateLocale(locale);
            var adapter = ViewerLifecycleCoordinator.global().activeAdapter().orElse(null);
            if (adapter == null || !adapter.groupIndex().ready()) {
                feedback.accept(Component.translatable("collapsible_groups.command.worklist_pending"));
                return 0;
            }
            var index = adapter.groupIndex();
            var candidates = index.candidates().orElseThrow(() -> new IllegalStateException("Group evaluation is pending"));
            var snapshot = GroupRepository.readSnapshot();
            Map<String, GroupEvaluation> evaluations = new LinkedHashMap<>();
            for (var group : snapshot.groups()) evaluations.put(group.id(), index.evaluation(group));
            var manager = Minecraft.getInstance().getResourceManager();
            var packs = manager.listPacks().toList();
            var language = TargetLocaleEntries.read(manager, GroupTranslationHelper.getOverlayLangDir(), locale);
            var plan = GroupTranslationWorklist.plan(snapshot.groups(), snapshot.resources(), evaluations, language.entries(), missingOnly);
            var current = GroupRepository.readSnapshot();
            if (current.groups() != snapshot.groups() || current.resources() != snapshot.resources()
                || !index.ready() || index.candidates().orElse(null) != candidates
                || !packs.equals(manager.listPacks().toList()) || !language.unchanged()) {
                throw new IllegalStateException("Groups or language resources changed; retry the worklist command");
            }
            var output = GroupTranslationWorklist.write(plan,
                GroupTranslationHelper.getOverlayLangDir().getParent().resolve("translation-work"),
                locale, SharedConstants.getCurrentVersion().getName(), adapter.id(), language.sources());
            feedback.accept(Component.translatable(plan.complete() ? "collapsible_groups.command.worklist_success"
                : "collapsible_groups.command.worklist_incomplete", plan.entries().size(), output.toString()));
            return plan.complete() ? 1 : 0;
        } catch (Exception failure) {
            Constants.LOG.warn("Cannot export group translation worklist", failure);
            feedback.accept(Component.translatable("collapsible_groups.command.worklist_error", failure.getMessage()));
            return 0;
        }
    }
}
