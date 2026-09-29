package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class JeiInputAbiTest {

	@Test
	void overlayInjectionAndNativeWrappersMatchEverySupportedAbi() throws IOException {
		Path root = Path.of(System.getProperty("collapsibleGroupsRoot"));
		for (String loader : List.of("forge", "fabric")) {
			ClassNode mixin = read(Files.readAllBytes(root.resolve(loader + "/build/classes/java/main/"
				+ "com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay.class")));
			MethodNode callback = method(mixin, "cg$wrapInputHandler");
			AnnotationNode inject = callback.visibleAnnotations.stream()
				.filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
			assertEquals(1, value(inject, "require"));
			assertEquals(1, value(inject, "allow"));
			List<?> selectors = (List<?>) value(inject, "method");
			for (Path artifact : artifacts()) {
				if (!artifact.getFileName().toString().contains("-" + loader + "-")) continue;
				try (ZipFile jar = new ZipFile(artifact.toFile())) {
					ClassNode overlay = read(jar, "mezz/jei/gui/overlay/IngredientListOverlay");
					assertEquals(1, overlay.methods.stream().filter(m -> selectors.contains(m.name + m.desc)).count(),
						artifact.toString());
					String handler = Type.getReturnType(method(overlay, "createInputHandler").desc).getInternalName();
					ClassNode button = read(jar, "mezz/jei/gui/elements/GuiIconButton");
					assertEquals("()L" + handler + ";", method(button, "createInputHandler").desc);
					String combined = handler.substring(0, handler.lastIndexOf('/')) + "/handlers/CombinedInputHandler";
					ClassNode combinedType = read(jar, combined);
					assertTrue(combinedType.interfaces.contains(handler));
					assertTrue(combinedType.methods.stream().anyMatch(m -> m.name.equals("<init>")
						&& m.desc.equals("(Ljava/lang/String;Ljava/util/List;)V")));
					ClassNode proxy = read(jar, "mezz/jei/gui/input/handlers/ProxyInputHandler");
					assertTrue(proxy.interfaces.contains(handler));
					assertTrue(proxy.methods.stream().anyMatch(m -> m.name.equals("<init>")
						&& m.desc.equals("(Ljava/util/function/Supplier;)V")));
				}
			}
		}
	}

	static List<Path> artifacts() {
		String paths = System.getProperty("jeiAbiArtifacts");
		assertNotNull(paths);
		List<Path> artifacts = Arrays.stream(paths.split(java.util.regex.Pattern.quote(File.pathSeparator)))
			.map(Path::of).toList();
		assertEquals(10, artifacts.size());
		return artifacts;
	}

	static ClassNode read(ZipFile jar, String name) throws IOException {
		var entry = jar.getEntry(name + ".class");
		assertNotNull(entry, jar.getName() + ": " + name);
		try (var in = jar.getInputStream(entry)) { return read(in.readAllBytes()); }
	}

	static ClassNode read(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return node;
	}

	static MethodNode method(ClassNode node, String name) {
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	static Object value(AnnotationNode annotation, String key) {
		int index = annotation.values.indexOf(key);
		assertTrue(index >= 0, key);
		return annotation.values.get(index + 1);
	}
}
