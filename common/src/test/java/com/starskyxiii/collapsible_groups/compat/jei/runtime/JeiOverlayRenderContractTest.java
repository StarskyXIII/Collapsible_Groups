package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiOverlayRenderContractTest {
	private static final String GUI_EVENT_HANDLER = "mezz/jei/gui/events/GuiEventHandler.class";
	private static final String OVERLAY_OWNER = "mezz/jei/gui/overlay/IngredientListOverlay";

	@Test
	void productionRenderPathCallsSplitOverlayPhases() throws IOException {
		assertProductionCall(
			"drawOverlayBackgrounds",
			"drawBackground",
			"(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
		);
		assertProductionCall(
			"drawOverlayForegrounds",
			"drawForeground",
			"(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
		);
	}

	private static void assertProductionCall(String callerName, String targetName, String targetDescriptor)
		throws IOException {
		try (InputStream stream = JeiOverlayRenderContractTest.class.getClassLoader()
			.getResourceAsStream(GUI_EVENT_HANDLER)) {
			assertNotNull(stream, "JEI GuiEventHandler bytecode must be present");
			ProductionCallVisitor visitor =
				new ProductionCallVisitor(callerName, targetName, targetDescriptor);
			new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			assertTrue(visitor.callerFound, () -> "JEI caller missing: " + callerName);
			assertTrue(visitor.targetCallFound, () ->
				callerName + " must call " + OVERLAY_OWNER + "." + targetName + targetDescriptor);
		}
	}

	private static final class ProductionCallVisitor extends ClassVisitor {
		private final String callerName;
		private final String targetName;
		private final String targetDescriptor;
		private boolean callerFound;
		private boolean targetCallFound;

		private ProductionCallVisitor(String callerName, String targetName, String targetDescriptor) {
			super(Opcodes.ASM9);
			this.callerName = callerName;
			this.targetName = targetName;
			this.targetDescriptor = targetDescriptor;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
			if (!callerName.equals(name)) return null;
			callerFound = true;
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
					boolean isInterface) {
					if (opcode == Opcodes.INVOKEVIRTUAL
						&& OVERLAY_OWNER.equals(owner)
						&& targetName.equals(name)
						&& targetDescriptor.equals(descriptor)) {
						targetCallFound = true;
					}
				}
			};
		}
	}
}
