package com.starskyxiii.collapsible_groups.compat.emi;

import com.mojang.serialization.Lifecycle;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryResult;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EmiRegistryTagBridgeTest {
	private static final ResourceLocation TAG = ResourceLocation.parse("test:tag");
	private static final ResourceLocation EMPTY = ResourceLocation.parse("test:empty");
	interface Marker {}
	static class Value {}
	static class Child extends Value implements Marker {}

	@Test void freezesMembershipAndRetainsEmptyTagExistence() {
		Value value = new Value();
		var registry = registry(value);
		AtomicInteger lookups = new AtomicInteger();
		var bridge = new EmiRegistryTagBridge(Map.of(Value.class, adapter(Value.class, registry, lookups)));
		var tags = bridge.resolve(value);
		assertEquals(TagQueryResult.MATCH, tags.query(TAG));
		assertEquals(TagQueryResult.NO_MATCH, tags.query(EMPTY));
		assertTrue(tags.existing().contains(EMPTY));
		assertFalse(tags.existing().contains(ResourceLocation.parse("test:missing")));
		registry.bindTags(Map.of(TagKey.create(registry.key(), TAG), List.of()));
		for (int i = 0; i < 1000; i++) {
			assertSame(tags, bridge.resolve(value));
			assertEquals(TagQueryResult.MATCH, tags.query(TAG));
		}
		assertEquals(1, lookups.get());
		assertThrows(UnsupportedOperationException.class, () -> tags.members().clear());
		assertThrows(UnsupportedOperationException.class, () -> bridge.snapshot().clear());
		assertEquals(TagQueryResult.NO_MATCH, new EmiRegistryTagBridge(Map.of(Value.class,
			adapter(Value.class, registry, new AtomicInteger()))).resolve(value).query(TAG));
	}

	@Test void missingAdapterUnregisteredKeyAndAdapterFailureAreUnavailable() {
		Value value = new Value();
		assertEquals(TagQueryResult.UNAVAILABLE, new EmiRegistryTagBridge(Map.of()).resolve(value).query(TAG));
		var registry = registry(value);
		var bridge = new EmiRegistryTagBridge(Map.of(Value.class, adapter(Value.class, registry, new AtomicInteger())));
		assertEquals(TagQueryResult.UNAVAILABLE, bridge.resolve(new Value()).query(TAG));
		var broken = new EmiRegistryAdapter<Value>() {
			public Class<Value> getBaseClass() { return Value.class; }
			public Registry<Value> getRegistry() { throw new NoSuchMethodError("fixture"); }
			public EmiStack of(Value value, net.minecraft.core.component.DataComponentPatch patch, long amount) { return null; }
		};
		assertEquals(TagQueryResult.UNAVAILABLE, new EmiRegistryTagBridge(Map.of(Value.class, broken)).resolve(value).query(TAG));
	}

	@Test void superclassTakesPriorityOverDirectInterfaceAndExactClassOverSuperclass() {
		Child child = new Child();
		var registry = registry((Value) child);
		AtomicInteger parentCalls = new AtomicInteger();
		AtomicInteger interfaceCalls = new AtomicInteger();
		var parent = adapter(Value.class, registry, parentCalls);
		var marker = adapter(Marker.class, null, interfaceCalls);
		assertTrue(new EmiRegistryTagBridge(Map.of(Value.class, parent, Marker.class, marker)).resolve(child).available());
		assertEquals(1, parentCalls.get());
		assertEquals(0, interfaceCalls.get());
		var exact = adapter(Child.class, null, new AtomicInteger());
		assertFalse(new EmiRegistryTagBridge(Map.of(Value.class, parent, Child.class, exact)).resolve(child).available());
		assertEquals(1, parentCalls.get());
		assertFalse(new EmiRegistryTagBridge(Map.of(Marker.class, marker)).resolve(child).available());
		assertEquals(1, interfaceCalls.get());
	}

	@Test void sameResourceIdInDifferentRegistriesDoesNotShareMembership() {
		Value parent = new Value();
		Child child = new Child();
		var first = registry(parent);
		var second = registry(child);
		second.bindTags(Map.of(TagKey.create(second.key(), TAG), List.of()));
		var bridge = new EmiRegistryTagBridge(Map.of(Value.class,
			adapter(Value.class, first, new AtomicInteger()), Child.class,
			adapter(Child.class, second, new AtomicInteger())));
		assertEquals(TagQueryResult.MATCH, bridge.resolve(parent).query(TAG));
		assertEquals(TagQueryResult.NO_MATCH, bridge.resolve(child).query(TAG));
	}

	@Test void registryCannotSubstituteAnotherHolderWithTheSameResourceId() {
		Value registered = new Value();
		Value impostor = new Value();
		var real = registry(registered);
		@SuppressWarnings("unchecked") Registry<Value> wrong = (Registry<Value>) java.lang.reflect.Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{Registry.class}, (proxy, method, args) -> switch (method.getName()) {
				case "getResourceKey" -> real.getResourceKey(registered);
				case "getHolder" -> real.getHolder(real.getResourceKey(registered).orElseThrow());
				default -> throw new AssertionError(method.getName());
			});
		assertEquals(TagQueryResult.UNAVAILABLE, new EmiRegistryTagBridge(Map.of(Value.class,
			adapter(Value.class, wrong, new AtomicInteger()))).resolve(impostor).query(TAG));
	}

	private static <T> MappedRegistry<T> registry(T value) {
		var key = ResourceKey.<T>createRegistryKey(ResourceLocation.parse("test:registry"));
		var registry = new MappedRegistry<T>(key, Lifecycle.stable());
		var holder = registry.register(ResourceKey.create(key, ResourceLocation.parse("test:value")), value, RegistrationInfo.BUILT_IN);
		registry.freeze();
		registry.bindTags(Map.of(TagKey.create(key, TAG), List.of(holder), TagKey.create(key, EMPTY), List.of()));
		return registry;
	}

	private static <T> EmiRegistryAdapter<T> adapter(Class<T> type, Registry<T> registry, AtomicInteger calls) {
		return new EmiRegistryAdapter<>() {
			public Class<T> getBaseClass() { return type; }
			public Registry<T> getRegistry() { calls.incrementAndGet(); return registry; }
			public EmiStack of(T value, net.minecraft.core.component.DataComponentPatch patch, long amount) { throw new AssertionError("Tag lookup must not construct stacks"); }
		};
	}
}
