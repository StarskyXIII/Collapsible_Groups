package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EmiEditorTooltipTest {
	private static final String ID = "minecraft:plains";
	private static final String TYPE = "emi_ores:biome";
	private static final Component NAME = Component.literal("Plains");
	private final EmiEditorRuntimeAccess runtime = new EmiEditorRuntimeAccess(null, null);

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static Stream<List<Component>> tooltipSources() {
		return Stream.of(null, List.of(), List.of(
			Component.literal("Provider name").withStyle(ChatFormatting.GOLD),
			Component.literal("Provider detail").withStyle(ChatFormatting.ITALIC)));
	}

	@ParameterizedTest
	@MethodSource("tooltipSources")
	void genericTooltipPreservesProviderTextOrFallsBackToName(List<Component> source) {
		var stack = new TooltipStack(source);
		var entry = new EditorGenericIngredientView(TYPE, stack, null, NAME, ID, ID, Set.of(), null);

		assertTooltip(source, stack, runtime.genericTooltip(entry), true);
	}

	@ParameterizedTest
	@MethodSource("tooltipSources")
	void fluidTooltipPreservesProviderTextOrFallsBackToName(List<Component> source) {
		var stack = new TooltipStack(source);
		var entry = new EditorFluidIngredientView(stack, NAME, ID, null, ItemStack.EMPTY);

		assertTooltip(source, stack, runtime.fluidTooltip(entry), false);
	}

	private static void assertTooltip(List<Component> source, TooltipStack stack,
		List<Component> result, boolean generic) {
		List<Component> expected = new ArrayList<>(source == null || source.isEmpty() ? List.of(NAME) : source);
		expected.add(Component.literal(ID).withStyle(ChatFormatting.DARK_GRAY));
		if (generic) expected.add(Component.literal(TYPE).withStyle(ChatFormatting.GRAY));
		assertEquals(expected, result);
		assertEquals(1, stack.textReads);
		assertEquals(source == null || source.isEmpty() ? 1 : 0, stack.nameReads);
		assertThrows(UnsupportedOperationException.class, () -> result.add(Component.literal("extra")));
		if (source != null) {
			assertEquals(source, result.subList(0, source.size()));
			assertNotSame(source, result);
		}
		assertFalse(stack.getTooltip().isEmpty());
	}

	private static final class TooltipStack extends EmiStack {
		private final List<Component> text;
		private int textReads;
		private int nameReads;

		private TooltipStack(List<Component> text) {
			this.text = text;
		}

		@Override public List<Component> getTooltipText() { textReads++; return text; }
		@Override public Component getName() { nameReads++; return NAME; }
		@Override public List<ClientTooltipComponent> getTooltip() {
			return List.of(ClientTooltipComponent.create(NAME.getVisualOrderText()));
		}
		@Override public EmiStack copy() { return new TooltipStack(text); }
		@Override public boolean isEmpty() { return false; }
		@Override public DataComponentPatch getComponentChanges() { return DataComponentPatch.EMPTY; }
		@Override public Object getKey() { return ID; }
		@Override public ResourceLocation getId() { return ResourceLocation.parse(ID); }
		@Override public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {}
	}
}
