package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.widget.EditorShellLayout;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupEditorTooltipHelperTest {
	private static final String HELPER_CLASS =
		"com/starskyxiii/collapsible_groups/client/editor/GroupEditorTooltipHelper.class";
	private static final String HELPER_OWNER =
		"com/starskyxiii/collapsible_groups/client/editor/GroupEditorTooltipHelper";
	private static final String VISIBILITY_PREDICATE_DESCRIPTOR =
		"(ZLcom/starskyxiii/collapsible_groups/client/widget/EditorShellLayout$Rect;II)Z";
	private static final EditorShellLayout.Rect SEARCH = new EditorShellLayout.Rect(10, 20, 80, 12);

	@Test
	void searchSyntaxTooltipRequiresVisibleSearchFieldAndRectangleHit() {
		assertTrue(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 10, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(false, SEARCH, 10, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 9, 20));
		assertFalse(GroupEditorTooltipHelper.shouldShowSearchSyntaxTooltip(true, SEARCH, 90, 20));
	}

	@Test
	void rendererRequiresExplicitVisibilityAndCallsPredicate() throws IOException {
		List<Method> renderMethods = Arrays.stream(GroupEditorTooltipHelper.class.getDeclaredMethods())
			.filter(method -> method.getName().equals("render"))
			.toList();
		assertEquals(1, renderMethods.size(), "legacy render overloads can bypass the visibility gate");
		Class<?>[] parameterTypes = renderMethods.getFirst().getParameterTypes();
		assertEquals(9, parameterTypes.length);
		assertEquals(boolean.class, parameterTypes[8]);

		try (InputStream stream = GroupEditorTooltipHelperTest.class.getClassLoader()
			.getResourceAsStream(HELPER_CLASS)) {
			assertNotNull(stream, "compiled tooltip helper bytecode must be present");
			VisibilityPredicateCallVisitor visitor = new VisibilityPredicateCallVisitor();
			new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			assertTrue(visitor.renderFound, "visibility-aware render method must exist");
			assertTrue(visitor.predicateCallFound,
				"production render method must call shouldShowSearchSyntaxTooltip");
		}
	}

	private static final class VisibilityPredicateCallVisitor extends ClassVisitor {
		private boolean renderFound;
		private boolean predicateCallFound;

		private VisibilityPredicateCallVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
			Type[] arguments = Type.getArgumentTypes(descriptor);
			if (!name.equals("render") || arguments.length != 9
				|| arguments[8].getSort() != Type.BOOLEAN) return null;
			renderFound = true;
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
					boolean isInterface) {
					if (opcode == Opcodes.INVOKESTATIC
						&& HELPER_OWNER.equals(owner)
						&& name.equals("shouldShowSearchSyntaxTooltip")
						&& VISIBILITY_PREDICATE_DESCRIPTOR.equals(descriptor)) {
						predicateCallFound = true;
					}
				}
			};
		}
	}
}
