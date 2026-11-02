package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiOverlayInputAbiTest {
    @ParameterizedTest
    @ValueSource(strings = {"fabric", "forge", "neoforge"})
    void packagedRequiredInputHookResolvesExactlyOnceInJei(String loader) throws Exception {
        ClassNode mixin = read(System.getProperty("jeiAbi." + loader + ".mod"),
            "com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay");
        AnnotationNode hook = mixin.methods.stream()
            .filter(method -> method.name.equals("cg$wrapInputHandler"))
            .flatMap(method -> method.visibleAnnotations.stream())
            .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;"))
            .findFirst().orElseThrow();
        assertEquals(1, value(hook, "require"));
        assertEquals(1, value(hook, "allow"));
        List<?> selectors = (List<?>) value(hook, "method");
        ClassNode overlay = read(System.getProperty("jeiAbi." + loader + ".jei"),
            "mezz/jei/gui/overlay/IngredientListOverlay");
        var methods = overlay.methods.stream()
            .filter(method -> selectors.contains(method.name + method.desc)).toList();
        assertEquals(1, methods.size(),
            "The required input hook must resolve against the actual " + loader + " JEI artifact");
        var at = (AnnotationNode) value(hook, "at");
        assertEquals("INVOKE", value(at, "value"));
        assertEquals(0, value(hook, "index"));
        String target = (String) value(at, "target");
        MethodNode method = methods.get(0);
        int matches = 0;
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode call
                && target.equals("L" + call.owner + ";" + call.name + call.desc)) {
                assertReceiver(overlay, method, i);
                matches++;
            }
        }
        assertEquals(1, matches, "The controls wrapper must target exactly one call");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric", "forge", "neoforge"})
    void packagedSourceChangeHookKeepsAValidReceiverInReleasedJei(String loader) throws Exception {
        ClassNode mixin = read(System.getProperty("jeiAbi." + loader + ".mod"),
            "com/starskyxiii/collapsible_groups/mixin/MixinIngredientFilter");
        AnnotationNode hook = mixin.methods.stream()
            .filter(method -> method.name.equals("cg$onSourceChanged"))
            .flatMap(method -> method.visibleAnnotations.stream())
            .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
            .findFirst().orElseThrow();
        List<?> selectors = (List<?>) value(hook, "method");
        String at = (String) value((AnnotationNode) ((List<?>) value(hook, "at")).get(0), "value");
        ClassNode filter = read(System.getProperty("jeiAbi." + loader + ".jei"),
            "mezz/jei/gui/ingredients/IngredientFilter");
        int matches = 0;
        for (MethodNode method : filter.methods) {
            if (!selectors.contains(method.name) && !selectors.contains(method.name + method.desc)) continue;
            int site = -1;
            for (int i = 0; i < method.instructions.size(); i++) {
                int opcode = method.instructions.get(i).getOpcode();
                if (at.equals("HEAD") && opcode >= 0) { site = i; break; }
                if (at.equals("TAIL") && opcode == Opcodes.RETURN) site = i;
            }
            assertTrue(site >= 0, method.name + " @" + at);
            assertReceiver(filter, method, site);
            matches++;
        }
        assertTrue(matches > 0, "Source change callbacks must exist in the released artifact");
    }

    private static void assertReceiver(ClassNode owner, MethodNode method, int instruction) throws Exception {
        // Preserve exact reference types without loading Minecraft or JEI classes into the test JVM.
        var interpreter = new BasicInterpreter(Opcodes.ASM9) {
            @Override public BasicValue newValue(Type type) {
                if (type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) {
                    return new BasicValue(type);
                }
                return super.newValue(type);
            }
        };
        var frames = new Analyzer<BasicValue>(interpreter).analyze(owner.name, method);
        assertNotNull(frames[instruction], "Injection must target reachable bytecode");
        assertEquals(new BasicValue(Type.getObjectType(owner.name)), frames[instruction].getLocal(0),
            owner.name + "." + method.name + ": instance callback requires this in local slot 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric", "forge", "neoforge"})
    void packagedRenderHooksCoverEveryNormalExit(String loader) throws Exception {
        ClassNode mixin = read(System.getProperty("jeiAbi." + loader + ".mod"),
            "com/starskyxiii/collapsible_groups/mixin/MixinIngredientListOverlay");
        ClassNode overlay = read(System.getProperty("jeiAbi." + loader + ".jei"),
            "mezz/jei/gui/overlay/IngredientListOverlay");
        for (String name : List.of("cg$drawForegroundPhase", "cg$drawTooltips")) {
            AnnotationNode hook = mixin.methods.stream().filter(method -> method.name.equals(name))
                .flatMap(method -> method.visibleAnnotations.stream())
                .filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            List<?> selectors = (List<?>) value(hook, "method");
            String at = (String) value((AnnotationNode) ((List<?>) value(hook, "at")).get(0), "value");
            MethodNode target = overlay.methods.stream()
                .filter(method -> selectors.contains(method.name) || selectors.contains(method.name + method.desc))
                .findFirst().orElseThrow();
            int exits = 0;
            for (int i = 0; i < target.instructions.size(); i++) {
                if (target.instructions.get(i).getOpcode() == Opcodes.RETURN) {
                    assertReceiver(overlay, target, i);
                    exits++;
                }
            }
            assertTrue(exits > 0);
            int injectedExits = at.equals("RETURN") ? exits : at.equals("TAIL") ? 1 : 0;
            assertEquals(exits, injectedExits, target.name + ": each exit must draw the group button or tooltip");
        }
    }

    private static ClassNode read(String path, String name) throws IOException {
        assertNotNull(path);
        try (ZipFile jar = new ZipFile(path)) {
            var entry = jar.getEntry(name + ".class");
            assertNotNull(entry, path + ": " + name);
            try (var stream = jar.getInputStream(entry)) {
                ClassNode node = new ClassNode();
                new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            }
        }
    }

    private static Object value(AnnotationNode annotation, String key) {
        int index = annotation.values.indexOf(key);
        assertTrue(index >= 0, key);
        return annotation.values.get(index + 1);
    }
}
