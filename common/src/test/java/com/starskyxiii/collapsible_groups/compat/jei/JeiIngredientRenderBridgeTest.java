package com.starskyxiii.collapsible_groups.compat.jei;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiIngredientRenderBridgeTest {
	private static final String GUI_GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
	private static final String INGREDIENT_RENDERER = "mezz/jei/api/ingredients/IIngredientRenderer";
	private static final String TWO_ARG_RENDER =
		"(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;)V";
	private static final String FOUR_ARG_RENDER =
		"(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)V";
	private static final String GROUP_ICON_ABSOLUTE_RENDER =
		"(Lnet/minecraft/client/gui/GuiGraphics;" +
			"Lcom/starskyxiii/collapsible_groups/compat/jei/element/GroupIcon;II)V";
	private static final String GROUP_ICON_RENDERER =
		"com/starskyxiii/collapsible_groups/compat/jei/element/GroupIconRenderer.class";

	private static final List<String> CUSTOM_RENDER_CLASSES = List.of(
		"com/starskyxiii/collapsible_groups/compat/jei/editor/EditorFluidIngredientHelper.class",
		"com/starskyxiii/collapsible_groups/compat/jei/editor/EditorGenericIngredientHelper.class",
		"com/starskyxiii/collapsible_groups/compat/jei/preview/PreviewIngredientRenderer.class",
		GROUP_ICON_RENDERER,
		"com/starskyxiii/collapsible_groups/compat/jei/JeiViewerAdapter$JeiPresentation.class"
	);

	@Test
	void bridgeFlushesBeforeCallingAbsolutePositionRenderer() throws IOException {
		BridgeCallVisitor visitor = new BridgeCallVisitor();
		readClass(
			"com/starskyxiii/collapsible_groups/compat/jei/JeiIngredientRenderBridge.class",
			visitor
		);

		assertTrue(visitor.renderMethodFound, "bridge render method must be present");
		assertTrue(visitor.flushInstruction >= 0, "bridge must flush pending GuiGraphics draws");
		assertTrue(visitor.fourArgRenderInstruction >= 0, "bridge must call JEI's absolute-position renderer");
		assertTrue(visitor.flushInstruction < visitor.fourArgRenderInstruction,
			"GuiGraphics.flush must happen before JEI renderer delegation");
		assertEquals(0, visitor.twoArgRenderCalls, "bridge must not call JEI's relative 2-arg renderer");
	}

	@Test
	void customUiDoesNotBypassBridgeWithRelativeRendererCalls() throws IOException {
		for (String className : CUSTOM_RENDER_CLASSES) {
			RelativeRenderCallVisitor visitor = new RelativeRenderCallVisitor();
			readClass(className, visitor);
			assertEquals(0, visitor.calls, () ->
				className + " must delegate JEI rendering through JeiIngredientRenderBridge");
		}
	}

	@Test
	void groupHeaderDoesNotClipThirdPartyRenderersToItsLogicalSlot() throws IOException {
		GroupHeaderClipVisitor visitor = new GroupHeaderClipVisitor();
		readClass(GROUP_ICON_RENDERER, visitor);

		assertTrue(visitor.absoluteRenderMethodFound,
			"GroupIconRenderer must keep its absolute-position render entry");
		assertEquals(0, visitor.scissorCalls,
			"GroupIconRenderer must let third-party renderers overflow their logical slot");
		assertEquals(0, visitor.flushCalls,
			"GroupIconRenderer must not add flush boundaries just to contain a logical slot");
	}

	private static void readClass(String className, ClassVisitor visitor) throws IOException {
		try (InputStream stream = JeiIngredientRenderBridgeTest.class.getClassLoader()
			.getResourceAsStream(className)) {
			assertNotNull(stream, "class bytecode must be present: " + className);
			new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
	}

	private static final class BridgeCallVisitor extends ClassVisitor {
		private boolean renderMethodFound;
		private int instruction;
		private int flushInstruction = -1;
		private int fourArgRenderInstruction = -1;
		private int twoArgRenderCalls;

		private BridgeCallVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
			if (!"render".equals(name)) return null;
			renderMethodFound = true;
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
					boolean isInterface) {
					int current = instruction++;
					if (GUI_GRAPHICS.equals(owner) && "flush".equals(name) && "()V".equals(descriptor)) {
						flushInstruction = current;
					}
					if (INGREDIENT_RENDERER.equals(owner) && "render".equals(name)) {
						if (FOUR_ARG_RENDER.equals(descriptor)) fourArgRenderInstruction = current;
						if (TWO_ARG_RENDER.equals(descriptor)) twoArgRenderCalls++;
					}
				}
			};
		}
	}

	private static final class RelativeRenderCallVisitor extends ClassVisitor {
		private int calls;

		private RelativeRenderCallVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
					boolean isInterface) {
					if (INGREDIENT_RENDERER.equals(owner)
						&& "render".equals(name)
						&& TWO_ARG_RENDER.equals(descriptor)) {
						calls++;
					}
				}
			};
		}
	}

	private static final class GroupHeaderClipVisitor extends ClassVisitor {
		private boolean absoluteRenderMethodFound;
		private int scissorCalls;
		private int flushCalls;

		private GroupHeaderClipVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
			if (!"render".equals(name) || !GROUP_ICON_ABSOLUTE_RENDER.equals(descriptor)) return null;
			absoluteRenderMethodFound = true;
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
					boolean isInterface) {
					if (!GUI_GRAPHICS.equals(owner)) return;
					if ("enableScissor".equals(name) || "disableScissor".equals(name)) scissorCalls++;
					if ("flush".equals(name)) flushCalls++;
				}
			};
		}
	}
}
