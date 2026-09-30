package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JeiInputLinkageTest {
	private static final String PREFIX = "com/starskyxiii/collapsible_groups/compat/jei/";
	private static final List<String> MOVED_TYPES = List.of(
		"mezz/jei/gui/input/UserInput", "mezz/jei/common/input/UserInput",
		"mezz/jei/gui/input/IUserInputHandler", "mezz/jei/common/input/IUserInputHandler",
		"mezz/jei/gui/input/handlers/CombinedInputHandler", "mezz/jei/common/input/handlers/CombinedInputHandler",
		"mezz/jei/gui/input/handlers/ProxyInputHandler"
	);

	@Test
	void sharedInputIntegrationHasNoConcreteInputAbiLinkage() throws IOException {
		for (String name : List.of("runtime/JeiInputHandlerAdapter", "runtime/JeiInputHandlerAdapter$Bindings",
			"runtime/JeiIngredientListOverlayController", "element/GroupHeaderElement", "element/JeiClickableElement",
			"element/GroupChildElement", "element/AbstractFluidChildElement", "element/GenericChildElement")) {
			try (var stream = getClass().getClassLoader().getResourceAsStream(PREFIX + name + ".class")) {
				assertNotNull(stream, name);
				new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
					@Override
					public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
						check(descriptor);
						check(signature);
						return null;
					}

					@Override
					public MethodVisitor visitMethod(int access, String method, String descriptor, String signature,
						String[] exceptions) {
						check(descriptor);
						check(signature);
						return new MethodVisitor(Opcodes.ASM9) {
							@Override
							public void visitTypeInsn(int opcode, String type) { check(type); }

							@Override
							public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
								check(owner);
								check(descriptor);
							}

							@Override
							public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
								check(owner);
								check(descriptor);
							}

							@Override
							public void visitLdcInsn(Object value) { checkConstant(value); }

							@Override
							public void visitInvokeDynamicInsn(String method, String descriptor, Handle bootstrap, Object... arguments) {
								check(descriptor);
								checkConstant(bootstrap);
								for (Object argument : arguments) checkConstant(argument);
							}
						};
					}
				}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			}
		}
	}

	private static void checkConstant(Object value) {
		if (value instanceof Type type) check(type.getDescriptor());
		if (value instanceof Handle handle) {
			check(handle.getOwner());
			check(handle.getDesc());
		}
	}

	private static void check(String value) {
		if (value == null) return;
		for (String type : MOVED_TYPES) assertFalse(value.contains(type), value);
	}
}
