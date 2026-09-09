package com.starskyxiii.collapsible_groups.internal.version.data;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Minecraft1201NbtAccess {
	public static final int MAX_EXPECTED_LENGTH = 65_536;
	public static final int MAX_NESTING = 64;
	public static final int MAX_PATH_STEPS = 64;
	public static final int MAX_SNAPSHOT_PATHS = 4_096;
	public static final int MAX_SNAPSHOT_VISITS = 4_096;
	public static final int MAX_SNAPSHOT_CHARACTERS = 1_048_576;
	private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
	private static final Pattern NATIVE_SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

	private Minecraft1201NbtAccess() {}

	@FunctionalInterface
	public interface Matcher {
		boolean matches(ItemStack stack);
	}

	public record PathValue(String path, String valueSnbt) {}

	public record Snapshot(String rootSnbt, List<PathValue> paths, boolean truncated) {
		public Snapshot {
			paths = List.copyOf(paths);
		}
	}

	public static Optional<String> canonicalRoot(String input) {
		return parseValue(input)
			.filter(CompoundTag.class::isInstance)
			.flatMap(Minecraft1201NbtAccess::boundedSnbt);
	}

	public static Optional<String> canonicalValue(String input) {
		return parseValue(input).flatMap(Minecraft1201NbtAccess::boundedSnbt);
	}

	public static boolean validPath(String path) {
		return parsePath(path).isPresent();
	}

	public static Optional<Matcher> compileRoot(String expectedSnbt) {
		return parseValue(expectedSnbt)
			.filter(CompoundTag.class::isInstance)
			.filter(value -> boundedSnbt(value).isPresent())
			.map(Tag::copy)
			.map(expected -> stack -> {
				CompoundTag actual = stack.getTag();
				return actual != null && actual.equals(expected);
			});
	}

	public static Optional<Matcher> compilePath(String path, String expectedSnbt) {
		Optional<List<PathStep>> parsedPath = parsePath(path);
		Optional<Tag> parsedExpected = parseValue(expectedSnbt)
			.filter(value -> boundedSnbt(value).isPresent())
			.map(Tag::copy);
		if (parsedPath.isEmpty() || parsedExpected.isEmpty()) return Optional.empty();
		List<PathStep> steps = parsedPath.get();
		Tag expected = parsedExpected.get();
		return Optional.of(stack -> {
			Tag actual = navigate(stack.getTag(), steps);
			return actual != null && actual.equals(expected);
		});
	}

	public static Optional<Snapshot> snapshot(ItemStack stack) {
		CompoundTag original = stack.getTag();
		if (original == null) return Optional.empty();
		CompoundTag root = original.copy();
		List<PathValue> paths = new ArrayList<>();
		Traversal traversal = new Traversal(paths);
		for (String key : sortedKeys(root)) {
			Tag value = root.get(key);
			if (value == null || !traversal.add(keyStep("", key), value, 1)) break;
		}
		String rootSnbt = boundedSnbt(root).orElse("");
		if (rootSnbt.isEmpty()) traversal.truncated = true;
		return Optional.of(new Snapshot(rootSnbt, paths, traversal.truncated));
	}

	private static Optional<Tag> parseValue(String input) {
		if (input == null || input.isBlank() || input.length() > MAX_EXPECTED_LENGTH || exceedsNesting(input)) {
			return Optional.empty();
		}
		try {
			StringReader reader = new StringReader(input);
			Tag value = new TagParser(reader).readValue();
			reader.skipWhitespace();
			return reader.canRead() ? Optional.empty() : Optional.of(value);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static boolean exceedsNesting(String input) {
		int depth = 0;
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (quote != 0) {
				if (escaped) escaped = false;
				else if (c == '\\') escaped = true;
				else if (c == quote) quote = 0;
				continue;
			}
			if (c == '\'' || c == '"') quote = c;
			else if (c == '{' || c == '[') {
				if (++depth > MAX_NESTING) return true;
			} else if (c == '}' || c == ']') {
				depth--;
			}
		}
		return false;
	}

	private static Optional<List<PathStep>> parsePath(String path) {
		if (path == null || path.isEmpty() || path.length() > MAX_EXPECTED_LENGTH) return Optional.empty();
		try {
			PathReader reader = new PathReader(path);
			List<PathStep> steps = new ArrayList<>();
			if (reader.peek('[')) steps.add(reader.readQuotedKey());
			else steps.add(reader.readBareKey());
			while (!reader.done()) {
				if (reader.take('.')) steps.add(reader.readBareKey());
				else if (reader.peek('[')) steps.add(reader.readBracketStep());
				else return Optional.empty();
				if (steps.size() > MAX_PATH_STEPS) return Optional.empty();
			}
			return steps.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(steps));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static @Nullable Tag navigate(@Nullable Tag root, List<PathStep> steps) {
		Tag current = root;
		for (PathStep step : steps) {
			if (current == null) return null;
			if (step instanceof KeyStep key) {
				if (!(current instanceof CompoundTag compound)) return null;
				current = compound.get(key.key);
			} else {
				int index = ((IndexStep) step).index;
				if (!(current instanceof CollectionTag<?> collection) || index >= collection.size()) return null;
				current = collection.get(index);
			}
		}
		return current;
	}

	private static List<String> sortedKeys(CompoundTag compound) {
		return compound.getAllKeys().stream().sorted(Comparator.naturalOrder()).toList();
	}

	private static Optional<String> boundedSnbt(Tag value) {
		return boundedSnbt(value, MAX_EXPECTED_LENGTH);
	}

	private static Optional<String> boundedSnbt(Tag value, int maxCharacters) {
		if (maxCharacters < 1) return Optional.empty();
		StringBuilder output = new StringBuilder();
		try {
			appendSnbt(value, output, 0, maxCharacters);
			String encoded = output.toString();
			return parseValue(encoded).filter(value::equals).map(ignored -> encoded);
		} catch (SnbtLimitException e) {
			return Optional.empty();
		}
	}

	private static void appendSnbt(Tag value, StringBuilder output, int depth, int maxCharacters) {
		if (output.length() > maxCharacters
			|| ((value instanceof CompoundTag || value instanceof CollectionTag<?>) && depth >= MAX_NESTING)) {
			throw new SnbtLimitException();
		}
		if (value instanceof CompoundTag compound) {
			append(output, "{", maxCharacters);
			boolean first = true;
			for (String key : sortedKeys(compound)) {
				Tag child = compound.get(key);
				if (child == null) continue;
				if (!first) append(output, ",", maxCharacters);
				first = false;
				append(output, NATIVE_SIMPLE_KEY.matcher(key).matches() ? key : StringTag.quoteAndEscape(key), maxCharacters);
				append(output, ":", maxCharacters);
				appendSnbt(child, output, depth + 1, maxCharacters);
			}
			append(output, "}", maxCharacters);
			return;
		}
		if (value instanceof ByteArrayTag array) {
			append(output, "[B;", maxCharacters);
			byte[] values = array.getAsByteArray();
			for (int i = 0; i < values.length; i++) {
				if (i > 0) append(output, ",", maxCharacters);
				append(output, Byte.toString(values[i]) + "B", maxCharacters);
			}
			append(output, "]", maxCharacters);
			return;
		}
		if (value instanceof IntArrayTag array) {
			append(output, "[I;", maxCharacters);
			int[] values = array.getAsIntArray();
			for (int i = 0; i < values.length; i++) {
				if (i > 0) append(output, ",", maxCharacters);
				append(output, Integer.toString(values[i]), maxCharacters);
			}
			append(output, "]", maxCharacters);
			return;
		}
		if (value instanceof LongArrayTag array) {
			append(output, "[L;", maxCharacters);
			long[] values = array.getAsLongArray();
			for (int i = 0; i < values.length; i++) {
				if (i > 0) append(output, ",", maxCharacters);
				append(output, Long.toString(values[i]) + "L", maxCharacters);
			}
			append(output, "]", maxCharacters);
			return;
		}
		if (value instanceof CollectionTag<?> collection) {
			append(output, "[", maxCharacters);
			for (int i = 0; i < collection.size(); i++) {
				if (i > 0) append(output, ",", maxCharacters);
				appendSnbt(collection.get(i), output, depth + 1, maxCharacters);
			}
			append(output, "]", maxCharacters);
			return;
		}
		append(output, value.toString(), maxCharacters);
	}

	private static void append(StringBuilder output, String value, int maxCharacters) {
		if (output.length() + value.length() > maxCharacters) throw new SnbtLimitException();
		output.append(value);
	}

	private static String keyStep(String parent, String key) {
		if (SAFE_KEY.matcher(key).matches()) return parent.isEmpty() ? key : parent + "." + key;
		return parent + "[" + new JsonPrimitive(key) + "]";
	}

	private static final class Traversal {
		private final List<PathValue> paths;
		private boolean truncated;
		private int characters;
		private int visits;

		private Traversal(List<PathValue> paths) {
			this.paths = paths;
		}

		private boolean add(String path, Tag value, int depth) {
			if (visits++ >= MAX_SNAPSHOT_VISITS || paths.size() >= MAX_SNAPSHOT_PATHS) {
				truncated = true;
				return false;
			}
			if (path.length() > MAX_EXPECTED_LENGTH) {
				truncated = true;
				return false;
			}
			int remaining = MAX_SNAPSHOT_CHARACTERS - characters - path.length();
			if (remaining < 1) {
				truncated = true;
				return false;
			}
			Optional<String> encoded = boundedSnbt(value, Math.min(MAX_EXPECTED_LENGTH, remaining));
			if (encoded.isPresent()) {
				String valueSnbt = encoded.get();
				paths.add(new PathValue(path, valueSnbt));
				characters += path.length() + valueSnbt.length();
			} else {
				truncated = true;
			}
			if (value instanceof CompoundTag compound) {
				if (depth >= MAX_NESTING) {
					boolean hasChildren = !compound.isEmpty();
					if (hasChildren) truncated = true;
					return !hasChildren;
				}
				for (String key : sortedKeys(compound)) {
					Tag child = compound.get(key);
					if (child != null && !add(keyStep(path, key), child, depth + 1)) return false;
				}
			} else if (value instanceof CollectionTag<?> collection) {
				if (depth >= MAX_NESTING) {
					boolean hasChildren = !collection.isEmpty();
					if (hasChildren) truncated = true;
					return !hasChildren;
				}
				for (int i = 0; i < collection.size(); i++) {
					if (!add(path + "[" + i + "]", collection.get(i), depth + 1)) return false;
				}
			}
			return true;
		}
	}

	private sealed interface PathStep permits KeyStep, IndexStep {}
	private record KeyStep(String key) implements PathStep {}
	private record IndexStep(int index) implements PathStep {}

	private static final class PathReader {
		private final String value;
		private int cursor;

		private PathReader(String value) {
			this.value = value;
		}

		private boolean done() { return cursor == value.length(); }
		private boolean peek(char c) { return !done() && value.charAt(cursor) == c; }
		private boolean take(char c) {
			if (!peek(c)) return false;
			cursor++;
			return true;
		}

		private KeyStep readBareKey() {
			int start = cursor;
			while (!done() && value.charAt(cursor) != '.' && value.charAt(cursor) != '[') cursor++;
			String key = value.substring(start, cursor);
			if (!SAFE_KEY.matcher(key).matches()) throw new IllegalArgumentException();
			return new KeyStep(key);
		}

		private PathStep readBracketStep() {
			if (!take('[')) throw new IllegalArgumentException();
			if (peek('"')) return readQuotedKeyBody();
			int start = cursor;
			while (!done() && isAsciiDigit(value.charAt(cursor))) cursor++;
			if (start == cursor || !take(']')) throw new IllegalArgumentException();
			try {
				return new IndexStep(Integer.parseInt(value.substring(start, cursor - 1)));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException();
			}
		}

		private KeyStep readQuotedKey() {
			if (!take('[')) throw new IllegalArgumentException();
			return readQuotedKeyBody();
		}

		private KeyStep readQuotedKeyBody() {
			if (!take('"')) throw new IllegalArgumentException();
			StringBuilder decoded = new StringBuilder();
			while (!done()) {
				char c = value.charAt(cursor++);
				if (c == '"') {
					if (!take(']')) throw new IllegalArgumentException();
					return new KeyStep(decoded.toString());
				}
				if (c < 0x20) throw new IllegalArgumentException();
				if (c != '\\') {
					decoded.append(c);
					continue;
				}
				if (done()) throw new IllegalArgumentException();
				char escape = value.charAt(cursor++);
				switch (escape) {
					case '"', '\\', '/' -> decoded.append(escape);
					case 'b' -> decoded.append('\b');
					case 'f' -> decoded.append('\f');
					case 'n' -> decoded.append('\n');
					case 'r' -> decoded.append('\r');
					case 't' -> decoded.append('\t');
					case 'u' -> decoded.append(readUnicodeEscape());
					default -> throw new IllegalArgumentException();
				}
			}
			throw new IllegalArgumentException();
		}

		private char readUnicodeEscape() {
			if (cursor + 4 > value.length()) throw new IllegalArgumentException();
			int decoded = 0;
			for (int i = 0; i < 4; i++) {
				int digit = asciiHex(value.charAt(cursor++));
				if (digit < 0) throw new IllegalArgumentException();
				decoded = decoded * 16 + digit;
			}
			return (char) decoded;
		}

		private static boolean isAsciiDigit(char value) {
			return value >= '0' && value <= '9';
		}

		private static int asciiHex(char value) {
			if (value >= '0' && value <= '9') return value - '0';
			if (value >= 'a' && value <= 'f') return value - 'a' + 10;
			if (value >= 'A' && value <= 'F') return value - 'A' + 10;
			return -1;
		}
	}

	private static final class SnbtLimitException extends RuntimeException {}
}
