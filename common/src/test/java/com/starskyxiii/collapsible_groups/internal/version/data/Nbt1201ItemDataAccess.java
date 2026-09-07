package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Nbt1201ItemDataAccess implements ItemDataAccess<Nbt1201ItemDataAccess.NbtStack, Nbt1201ItemDataAccess.NbtNode> {
	static final ItemDataFormat FORMAT = new ItemDataFormat(
		"collapsible_groups:exact_stack", 1, "minecraft:item_nbt", "1.20.1");
	private static final Object REGISTRY = new Object();
	private final Codec codec = new Codec();

	record NbtStack(String itemId, NbtCompound tag) {}

	sealed interface NbtNode permits NbtByte, NbtShort, NbtInt, NbtLong, NbtFloat, NbtDouble,
		NbtString, NbtList, NbtByteArray, NbtIntArray, NbtLongArray, NbtCompound {
		byte type();
	}

	record NbtByte(byte value) implements NbtNode { @Override public byte type() { return 1; } }
	record NbtShort(short value) implements NbtNode { @Override public byte type() { return 2; } }
	record NbtInt(int value) implements NbtNode { @Override public byte type() { return 3; } }
	record NbtLong(long value) implements NbtNode { @Override public byte type() { return 4; } }
	record NbtFloat(float value) implements NbtNode { @Override public byte type() { return 5; } }
	record NbtDouble(double value) implements NbtNode { @Override public byte type() { return 6; } }
	record NbtByteArray(List<Byte> values) implements NbtNode {
		NbtByteArray { values = List.copyOf(values); }
		@Override public byte type() { return 7; }
	}
	record NbtString(String value) implements NbtNode { @Override public byte type() { return 8; } }
	record NbtList(byte elementType, List<NbtNode> values) implements NbtNode {
		NbtList {
			values = List.copyOf(values);
			if (values.stream().anyMatch(value -> value.type() != elementType)) {
				throw new IllegalArgumentException("NBT list elements must share their declared type");
			}
		}
		@Override public byte type() { return 9; }
	}
	record NbtCompound(Map<String, NbtNode> values) implements NbtNode {
		NbtCompound { values = Map.copyOf(values); }
		@Override public byte type() { return 10; }
	}
	record NbtIntArray(List<Integer> values) implements NbtNode {
		NbtIntArray { values = List.copyOf(values); }
		@Override public byte type() { return 11; }
	}
	record NbtLongArray(List<Long> values) implements NbtNode {
		NbtLongArray { values = List.copyOf(values); }
		@Override public byte type() { return 12; }
	}

	@Override public ExactStackCodec<NbtStack> exactStacks() { return codec; }

	@Override
	public boolean matchesDataValue(NbtStack stack, String dataTypeId, String encodedValue) {
		NbtNode actual = stack.tag().values().get(dataTypeId);
		return actual != null && encodeNode(actual).equals(encodedValue);
	}

	@Override
	public boolean matchesDataPath(NbtStack stack, String dataTypeId, String path, String expectedValue) {
		NbtNode current = stack.tag().values().get(dataTypeId);
		if (current == null) return false;
		for (String segment : path.split("\\.")) {
			int bracket = segment.indexOf('[');
			String field = bracket < 0 ? segment : segment.substring(0, bracket);
			if (!field.isEmpty()) {
				if (!(current instanceof NbtCompound compound)) return false;
				current = compound.values().get(field);
				if (current == null) return false;
			}
			if (bracket >= 0) {
				if (!(current instanceof NbtList list) || !segment.endsWith("]")) return false;
				int index;
				try {
					index = Integer.parseInt(segment.substring(bracket + 1, segment.length() - 1));
				} catch (NumberFormatException e) {
					return false;
				}
				if (index < 0 || index >= list.values().size()) return false;
				current = list.values().get(index);
			}
		}
		return encodeNode(current).equals(expectedValue);
	}

	@Override
	public List<DataReference<NbtNode>> enumerateData(NbtStack stack) {
		return stack.tag().values().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new DataReference<>(entry.getKey(), entry.getValue(), encodeNode(entry.getValue()), false))
			.toList();
	}

	@Override
	public List<DataPath<NbtNode>> enumeratePaths(NbtNode root) {
		List<DataPath<NbtNode>> paths = new ArrayList<>();
		if (root instanceof NbtCompound compound) {
			compound.values().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> collectPaths(entry.getKey(), entry.getValue(), paths));
		}
		return List.copyOf(paths);
	}

	private static void collectPaths(String path, NbtNode node, List<DataPath<NbtNode>> paths) {
		paths.add(new DataPath<>(path, node));
		if (node instanceof NbtCompound compound) {
			compound.values().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> collectPaths(path + "." + entry.getKey(), entry.getValue(), paths));
		} else if (node instanceof NbtList list) {
			for (int i = 0; i < list.values().size(); i++) {
				NbtNode element = list.values().get(i);
				String elementPath = path + "[" + i + "]";
				paths.add(new DataPath<>(elementPath, element));
				if (element instanceof NbtCompound compound) {
					compound.values().entrySet().stream().sorted(Map.Entry.comparingByKey())
						.forEach(entry -> collectPaths(elementPath + "." + entry.getKey(), entry.getValue(), paths));
				}
			}
		}
	}

	static String encodeNode(NbtNode node) {
		return switch (node) {
			case NbtByte value -> value.value() + "b";
			case NbtShort value -> value.value() + "s";
			case NbtInt value -> Integer.toString(value.value());
			case NbtLong value -> value.value() + "L";
			case NbtFloat value -> value.value() + "f";
			case NbtDouble value -> value.value() + "d";
			case NbtString value -> '"' + value.value() + '"';
			case NbtByteArray value -> "[B;" + join(value.values(), "b") + "]";
			case NbtIntArray value -> "[I;" + join(value.values(), "") + "]";
			case NbtLongArray value -> "[L;" + join(value.values(), "L") + "]";
			case NbtList value -> "[" + value.values().stream().map(Nbt1201ItemDataAccess::encodeNode)
				.reduce((left, right) -> left + "," + right).orElse("") + "]";
			case NbtCompound value -> "{" + value.values().entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + ":" + encodeNode(entry.getValue()))
				.reduce((left, right) -> left + "," + right).orElse("") + "}";
		};
	}

	private static String join(List<? extends Number> values, String suffix) {
		return values.stream().map(value -> value + suffix)
			.reduce((left, right) -> left + "," + right).orElse("");
	}

	private final class Codec implements ExactStackCodec<NbtStack> {
		@Override public ItemDataFormat format() { return FORMAT; }
		@Override public NbtStack normalizedCopy(NbtStack stack) { return stack; }

		@Override
		public Optional<String> encodeLegacy(NbtStack stack) {
			try {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				try (DataOutputStream output = new DataOutputStream(bytes)) {
					writeString(output, stack.itemId());
					writeNode(output, stack.tag());
				}
				return Optional.of(Base64.getEncoder().encodeToString(bytes.toByteArray()));
			} catch (IOException e) {
				return Optional.empty();
			}
		}

		@Override
		public Optional<String> encodeEnvelope(NbtStack stack) {
			return encodeLegacy(stack).map(encoded -> VersionedDataEnvelope.wrap(format(), new JsonPrimitive(encoded)));
		}

		@Override public Object registryIdentity() { return REGISTRY; }
		@Override public DecodeSnapshot<NbtStack> beginDecode() { return new Snapshot(); }
		@Override public boolean equivalent(NbtStack left, NbtStack right) { return left.equals(right); }
		@Override public String itemId(NbtStack stack) { return stack.itemId(); }
		@Override
		public VersionedDataEnvelope.Support support(String encoded) {
			return VersionedDataEnvelope.isEnvelope(encoded)
				? VersionedDataEnvelope.inspect(encoded, FORMAT).support()
				: VersionedDataEnvelope.Support.LEGACY;
		}
	}

	private final class Snapshot implements ExactStackCodec.DecodeSnapshot<NbtStack> {
		@Override public boolean liveRegistry() { return true; }
		@Override public Object registryIdentity() { return REGISTRY; }

		@Override
		public Optional<NbtStack> decode(String encoded) {
			String payload = encoded;
			if (VersionedDataEnvelope.isEnvelope(encoded)) {
				VersionedDataEnvelope.Inspection inspection = VersionedDataEnvelope.inspect(encoded, FORMAT);
				if (inspection.support() != VersionedDataEnvelope.Support.CURRENT) return Optional.empty();
				payload = inspection.data().orElseThrow().getAsString();
			}
			try {
				byte[] bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.US_ASCII));
				try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
					String itemId = readString(input);
					NbtNode node = readNode(input);
					if (!(node instanceof NbtCompound compound) || input.available() != 0) return Optional.empty();
					return Optional.of(new NbtStack(itemId, compound));
				}
			} catch (RuntimeException | IOException e) {
				return Optional.empty();
			}
		}
	}

	private static void writeNode(DataOutputStream output, NbtNode node) throws IOException {
		output.writeByte(node.type());
		switch (node) {
			case NbtByte value -> output.writeByte(value.value());
			case NbtShort value -> output.writeShort(value.value());
			case NbtInt value -> output.writeInt(value.value());
			case NbtLong value -> output.writeLong(value.value());
			case NbtFloat value -> output.writeFloat(value.value());
			case NbtDouble value -> output.writeDouble(value.value());
			case NbtString value -> writeString(output, value.value());
			case NbtByteArray value -> {
				output.writeInt(value.values().size());
				for (byte element : value.values()) output.writeByte(element);
			}
			case NbtIntArray value -> {
				output.writeInt(value.values().size());
				for (int element : value.values()) output.writeInt(element);
			}
			case NbtLongArray value -> {
				output.writeInt(value.values().size());
				for (long element : value.values()) output.writeLong(element);
			}
			case NbtList value -> {
				output.writeByte(value.elementType());
				output.writeInt(value.values().size());
				for (NbtNode element : value.values()) writeNodePayload(output, element);
			}
			case NbtCompound value -> {
				List<Map.Entry<String, NbtNode>> entries = value.values().entrySet().stream()
					.sorted(Map.Entry.comparingByKey()).toList();
				output.writeInt(entries.size());
				for (Map.Entry<String, NbtNode> entry : entries) {
					writeString(output, entry.getKey());
					writeNode(output, entry.getValue());
				}
			}
		}
	}

	private static void writeNodePayload(DataOutputStream output, NbtNode node) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream temporary = new DataOutputStream(bytes)) {
			writeNode(temporary, node);
		}
		byte[] encoded = bytes.toByteArray();
		output.write(encoded, 1, encoded.length - 1);
	}

	private static NbtNode readNode(DataInputStream input) throws IOException {
		byte type = input.readByte();
		return readNodePayload(input, type);
	}

	private static NbtNode readNodePayload(DataInputStream input, byte type) throws IOException {
		return switch (type) {
			case 1 -> new NbtByte(input.readByte());
			case 2 -> new NbtShort(input.readShort());
			case 3 -> new NbtInt(input.readInt());
			case 4 -> new NbtLong(input.readLong());
			case 5 -> new NbtFloat(input.readFloat());
			case 6 -> new NbtDouble(input.readDouble());
			case 7 -> {
				int size = readSize(input);
				List<Byte> values = new ArrayList<>(size);
				for (int i = 0; i < size; i++) values.add(input.readByte());
				yield new NbtByteArray(values);
			}
			case 8 -> new NbtString(readString(input));
			case 9 -> {
				byte elementType = input.readByte();
				int size = readSize(input);
				List<NbtNode> values = new ArrayList<>(size);
				for (int i = 0; i < size; i++) values.add(readNodePayload(input, elementType));
				yield new NbtList(elementType, values);
			}
			case 10 -> {
				int size = readSize(input);
				Map<String, NbtNode> values = new LinkedHashMap<>();
				for (int i = 0; i < size; i++) values.put(readString(input), readNode(input));
				yield new NbtCompound(values);
			}
			case 11 -> {
				int size = readSize(input);
				List<Integer> values = new ArrayList<>(size);
				for (int i = 0; i < size; i++) values.add(input.readInt());
				yield new NbtIntArray(values);
			}
			case 12 -> {
				int size = readSize(input);
				List<Long> values = new ArrayList<>(size);
				for (int i = 0; i < size; i++) values.add(input.readLong());
				yield new NbtLongArray(values);
			}
			default -> throw new IOException("Unsupported NBT tag type " + type);
		};
	}

	private static int readSize(DataInputStream input) throws IOException {
		int size = input.readInt();
		if (size < 0 || size > 10_000) throw new IOException("Invalid NBT collection size");
		return size;
	}

	private static void writeString(DataOutputStream output, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	private static String readString(DataInputStream input) throws IOException {
		int size = readSize(input);
		byte[] bytes = input.readNBytes(size);
		if (bytes.length != size) throw new IOException("Truncated NBT string");
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
