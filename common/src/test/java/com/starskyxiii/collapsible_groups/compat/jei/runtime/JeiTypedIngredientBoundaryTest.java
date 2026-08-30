package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JeiTypedIngredientBoundaryTest {
	private static final String CONTROLLER =
		"com/starskyxiii/collapsible_groups/compat/jei/runtime/JeiIngredientFilterController.class";
	private static final String INGREDIENT_MANAGER = "mezz/jei/api/runtime/IIngredientManager";
	private static final String FACTORY_DESCRIPTOR =
		"(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;Z)Ljava/util/Optional;";
	private static final Set<String> INTERNAL_TYPED_INGREDIENTS = Set.of(
		"mezz/jei/library/ingredients/TypedIngredient",
		"mezz/jei/common/ingredients/TypedIngredient"
	);

	@Test
	void groupHeadersUseThePublicIngredientManagerFactory() throws IOException {
		TypedIngredientBoundaryVisitor visitor = new TypedIngredientBoundaryVisitor();
		try (InputStream stream = getClass().getClassLoader().getResourceAsStream(CONTROLLER)) {
			assertNotNull(stream, "controller bytecode must be present");
			new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}

		assertEquals(1, visitor.publicFactoryCalls,
			"group headers must use IIngredientManager#createTypedIngredient(IIngredientType, Object, boolean)");
		assertEquals(0, visitor.internalReferences,
			"controller must not link JEI's internal TypedIngredient implementations");
	}

	private static final class TypedIngredientBoundaryVisitor extends ClassVisitor {
		private int publicFactoryCalls;
		private int internalReferences;

		private TypedIngredientBoundaryVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			countInternalReference(descriptor);
			countInternalReference(signature);
			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
			countInternalReference(descriptor);
			countInternalReference(signature);
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitTypeInsn(int opcode, String type) {
					if (INTERNAL_TYPED_INGREDIENTS.contains(type)) internalReferences++;
				}

				@Override
				public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
					if (INTERNAL_TYPED_INGREDIENTS.contains(owner)) internalReferences++;
					countInternalReference(descriptor);
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
					boolean isInterface) {
					if (INTERNAL_TYPED_INGREDIENTS.contains(owner)) internalReferences++;
					countInternalReference(descriptor);
					if (opcode == Opcodes.INVOKEINTERFACE
						&& INGREDIENT_MANAGER.equals(owner)
						&& "createTypedIngredient".equals(name)
						&& FACTORY_DESCRIPTOR.equals(descriptor)) {
						publicFactoryCalls++;
					}
				}
			};
		}

		private void countInternalReference(String value) {
			if (value == null) return;
			for (String internal : INTERNAL_TYPED_INGREDIENTS) {
				if (value.contains(internal)) internalReferences++;
			}
		}
	}
}
