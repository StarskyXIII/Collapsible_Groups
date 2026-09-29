package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class JeiInputAbiTest {
	@Test
	void packagedBookmarkHooksRequireExactlyOneCompatibleBranch() throws IOException {
		for (String loader : List.of("forge", "fabric")) {
			ClassNode bookmarks = readMixin(loader, "MixinBookmarkList");
			List<MethodNode> hooks = bookmarks.methods.stream().filter(m -> m.visibleAnnotations != null
				&& m.visibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Inject;"))).toList();
			assertEquals(2, hooks.size());
			for (MethodNode hook : hooks) {
				AnnotationNode group = hook.invisibleAnnotations.stream()
					.filter(a -> a.desc.endsWith("/Group;")).findFirst().orElseThrow();
				assertEquals("cg$blockGroupHeaderBookmarks", value(group, "name"));
				assertEquals(1, value(group, "min"));
				assertEquals(1, value(group, "max"));
				AnnotationNode inject = hook.visibleAnnotations.stream()
					.filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
				assertEquals(true, value(inject, "cancellable"));
			}
		}
	}

	@Test
	void bookmarkAndElementClickHooksMatchTheSelectedNativeOwner() throws IOException {
		for (String loader : List.of("forge", "fabric")) {
			ClassNode bookmarks = readMixin(loader, "MixinBookmarkList");
			List<AnnotationNode> bookmarkHooks = bookmarks.methods.stream().filter(m -> m.visibleAnnotations != null)
				.flatMap(m -> m.visibleAnnotations.stream()).filter(a -> a.desc.endsWith("/Inject;")).toList();
			for (Path artifact : artifacts()) {
				if (!artifact.getFileName().toString().contains("-" + loader + "-")) continue;
				try (ZipFile jar = new ZipFile(artifact.toFile())) {
					ClassNode target = read(jar, "mezz/jei/gui/bookmarks/BookmarkList");
					long bookmarkMatches = bookmarkHooks.stream().mapToLong(hook -> target.methods.stream()
						.filter(m -> ((List<?>) value(hook, "method")).contains(m.name + m.desc)).count()).sum();
					assertEquals(1, bookmarkMatches, artifact.toString());
					String owner = JeiInputMixinTargets.select(true,
						name -> jar.getEntry(name.replace('.', '/') + ".class") != null, artifact.toString());
					String mixin = owner.equals(JeiInputMixinTargets.CURRENT) ? "MixinElementInputHandler" : "MixinFocusInputHandler";
					ClassNode inputMixin = readMixin(loader, mixin);
					AnnotationNode redirect = method(inputMixin, "cg$handleElementClick").visibleAnnotations.stream()
						.filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
					assertEquals(1, value(redirect, "require"));
					assertEquals(1, value(redirect, "allow"));
					List<?> selectors = (List<?>) value(redirect, "method");
					String invoke = (String) value((AnnotationNode) value(redirect, "at"), "target");
					long matches = read(jar, owner.replace('.', '/')).methods.stream()
						.filter(m -> selectors.contains(m.name) || selectors.contains(m.name + m.desc))
						.flatMap(m -> Arrays.stream(m.instructions.toArray()))
						.filter(i -> i instanceof MethodInsnNode call && invoke.equals("L" + call.owner + ";" + call.name + call.desc)).count();
					assertEquals(1, matches, artifact.toString());
				}
			}
		}
	}

	@Test
	void overlayInjectionAndNativeWrappersMatchEverySupportedAbi() throws IOException {
		for (String loader : List.of("forge", "fabric")) {
			ClassNode mixin = readMixin(loader, "MixinIngredientListOverlay");
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

	static ClassNode readMixin(String loader, String name) throws IOException {
		String path = System.getProperty("jeiAbi." + loader + ".jar");
		assertNotNull(path);
		try (ZipFile jar = new ZipFile(path)) {
			return read(jar, "com/starskyxiii/collapsible_groups/mixin/" + name);
		}
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
