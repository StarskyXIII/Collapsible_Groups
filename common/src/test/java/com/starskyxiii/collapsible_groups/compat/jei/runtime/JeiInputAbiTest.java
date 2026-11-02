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
	void forgeSearchInterfaceBridgesReachThePackagedJeiOverrides() throws IOException {
		ClassNode bridge = readMixin("forge", "MixinGuiTextFieldFilter");
		Path artifact = artifacts().stream().filter(p -> p.getFileName().toString().contains("-forge-")).findFirst().orElseThrow();
		try (ZipFile jar = new ZipFile(artifact.toFile())) {
			ClassNode contract = read(jar, "mezz/jei/gui/overlay/ISearchField");
			ClassNode implementation = read(jar, "mezz/jei/gui/input/GuiTextFieldFilter");
			for (MethodNode required : contract.methods) {
				if (implementation.methods.stream().anyMatch(m -> m.name.equals(required.name) && m.desc.equals(required.desc))) continue;
				MethodNode proxy = method(bridge, "cg$" + required.name);
				assertEquals(required.desc, proxy.desc);
				assertTrue(proxy.invisibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Intrinsic;")), "Existing JEI implementations must be preserved");
				List<MethodInsnNode> calls = Arrays.stream(proxy.instructions.toArray())
					.filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
				assertEquals(1, calls.size());
				MethodInsnNode call = calls.get(0);
				assertEquals("net/minecraft/client/gui/components/EditBox", call.owner);
				assertNotEquals(required.name, call.name, "Forge bridge must call the remapped override, not itself");
				assertTrue(implementation.methods.stream().anyMatch(m -> m.name.equals(call.name) && m.desc.equals(call.desc)), "Bridge must preserve JEI's search colors and focus handling");
			}
		}
	}

	@Test
	void packagedHooksMatchLatestJeiAndInputTargetsExist() throws IOException {
		for (String loader : List.of("forge", "fabric")) {
			ClassNode mixin = readMixin(loader, "MixinIngredientListOverlay");
			AnnotationNode input = method(mixin, "cg$wrapInputHandler").visibleAnnotations.stream()
				.filter(a -> a.desc.endsWith("/ModifyArg;")).findFirst().orElseThrow();
			assertEquals(1, value(input, "require"));
			assertEquals(1, value(input, "allow"));
			ClassNode bookmarks = readMixin(loader, "MixinBookmarkList");
			List<AnnotationNode> bookmarkHooks = bookmarks.methods.stream().filter(m -> m.visibleAnnotations != null)
				.flatMap(m -> m.visibleAnnotations.stream()).filter(a -> a.desc.endsWith("/Inject;")).toList();
			assertEquals(1, bookmarkHooks.size());
			for (Path artifact : artifacts()) {
				if (!artifact.getFileName().toString().contains("-" + loader + "-")) continue;
				try (ZipFile jar = new ZipFile(artifact.toFile())) {
					ClassNode overlay = read(jar, "mezz/jei/gui/overlay/IngredientListOverlay");
					assertEquals(1, overlay.methods.stream().filter(m -> ((List<?>) value(input, "method")).contains(m.name + m.desc)).count());
					Object atValue = value(input, "at");
					AnnotationNode at = (AnnotationNode) (atValue instanceof List<?> values ? values.get(0) : atValue);
					assertEquals("INVOKE", value(at, "value"));
					assertEquals(0, value(input, "index"));
					assertEquals(1, Arrays.stream(method(overlay, "createInputArea").instructions.toArray())
						.filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
						.filter(call -> value(at, "target").equals("L" + call.owner + ";" + call.name + call.desc)).count());
					assertEquals("()Z", method(overlay, "isSearchDisplayed").desc);
					AnnotationNode mouseOver = method(mixin, "cg$includeGroupsButton").visibleAnnotations.stream()
						.filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
					assertEquals(1, overlay.methods.stream().filter(m -> ((List<?>) value(mouseOver, "method")).contains(m.name + m.desc)).count(), "Packaged hitbox injection must resolve on " + loader);
					MethodNode hitbox = method(read(jar, "mezz/jei/common/input/IMouseOverable"), "containsMouse");
					assertEquals(hitbox.desc, method(read(jar, "mezz/jei/gui/input/handlers/SearchInputLayer"), hitbox.name).desc, "Packaged search input must implement the JEI hitbox interface");
					ClassNode target = read(jar, "mezz/jei/gui/bookmarks/BookmarkList");
					assertEquals(1, target.methods.stream().filter(m -> ((List<?>) value(bookmarkHooks.get(0), "method")).contains(m.name + m.desc)).count());
					ClassNode button = read(jar, "mezz/jei/gui/elements/IconButton");
					assertEquals("()Lmezz/jei/common/input/IInputTarget;", method(button, "createInputHandler").desc);
					assertEquals("(Lmezz/jei/common/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Ljava/util/Optional;",
						method(read(jar, "mezz/jei/gui/overlay/elements/IElement"), "prepareAction").desc);
					ClassNode group = read(jar, "mezz/jei/common/input/handlers/InputGroup");
					assertTrue(group.interfaces.contains("mezz/jei/common/input/IInputTarget"));
					assertTrue(group.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(
						"(Ljava/lang/String;[Lmezz/jei/common/input/handlers/InputGroup$Child;)V")));
				}
			}
		}
	}

	static List<Path> artifacts() {
		String paths = System.getProperty("jeiAbiArtifacts");
		assertNotNull(paths);
		List<Path> artifacts = Arrays.stream(paths.split(java.util.regex.Pattern.quote(File.pathSeparator)))
			.map(Path::of).toList();
		assertEquals(2, artifacts.size());
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
