package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure-Gson path navigation helper for {@link GroupFilter.ComponentPath}.
 *
 * <p>Navigates a restricted path grammar through a JsonElement tree.
 * This class is intentionally isolated from Minecraft classes so that
 * it can be exercised by plain JUnit tests without game bootstrap.
 *
 * <h2>Path grammar</h2>
 * <pre>
 *   segment = [A-Za-z_][A-Za-z0-9_-]*(\[[0-9]+\])?
 *   path    = segment(\.segment)*
 * </pre>
 *
 * <h2>Boundary rules</h2>
 * <ul>
 *   <li>Returns {@code null} when navigation fails: missing field, wrong node
 *       type, or out-of-range array index.
 *   <li>Returns {@link com.google.gson.JsonNull} when the selected element is
 *       JSON null (this is distinct from navigation failure).
 *   <li>The path must already satisfy the grammar; behaviour for invalid paths
 *       is unspecified at runtime (validation happens earlier in
 *       {@link GroupFilterValidator}).
 * </ul>
 */
public final class ComponentPathNavigator {
	private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");

    private ComponentPathNavigator() {}

	public record PathNode(String path, JsonElement value) {}

    /**
     * Navigates {@code path} through {@code root} and returns the selected element,
     * or {@code null} if navigation fails at any step.
     */
    @Nullable
    public static JsonElement navigatePath(JsonElement root, String path) {
        JsonElement current = root;
        for (String segment : path.split("\\.", -1)) {
            if (current == null) return null;
            int bracketStart = segment.indexOf('[');
            if (bracketStart >= 0) {
                // Segment form: fieldName[index]
                String fieldName = segment.substring(0, bracketStart);
                int index;
                try {
                    index = Integer.parseInt(segment.substring(bracketStart + 1, segment.length() - 1));
                } catch (NumberFormatException e) {
                    return null;
                }
                // Navigate to the named field first
                if (!(current instanceof JsonObject obj)) return null;
                current = obj.get(fieldName);
                if (current == null) return null;
                // Then navigate the array index
                if (!(current instanceof JsonArray arr)) return null;
                if (index < 0 || index >= arr.size()) return null;
                current = arr.get(index);
            } else {
                // Plain field segment
                if (!(current instanceof JsonObject obj)) return null;
                current = obj.get(segment);
                // current may now be null (missing field) or JsonNull (explicit null value)
            }
        }
        return current; // null = navigation failed; JsonNull.INSTANCE = selected null value
    }

	/**
	 * Enumerates every non-root node expressible by this class's restricted path grammar.
	 * Container nodes are included because they are valid comparison targets too.
	 */
	public static List<PathNode> enumerateReachable(JsonElement root) {
		List<PathNode> nodes = new ArrayList<>();
		if (root instanceof JsonObject object) {
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (FIELD_NAME.matcher(entry.getKey()).matches()) {
					visit(entry.getValue(), entry.getKey(), nodes);
				}
			}
		}
		return List.copyOf(nodes);
	}

	private static void visit(JsonElement value, String path, List<PathNode> nodes) {
		nodes.add(new PathNode(path, value.deepCopy()));
		if (value instanceof JsonObject object) {
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (FIELD_NAME.matcher(entry.getKey()).matches()) {
					visit(entry.getValue(), path + "." + entry.getKey(), nodes);
				}
			}
			return;
		}
		if (value instanceof JsonArray array && canAppendArrayIndex(path)) {
			for (int index = 0; index < array.size(); index++) {
				visit(array.get(index), path + "[" + index + "]", nodes);
			}
		}
	}

	private static boolean canAppendArrayIndex(String path) {
		int lastDot = path.lastIndexOf('.');
		String lastSegment = path.substring(lastDot + 1);
		return FIELD_NAME.matcher(lastSegment).matches();
	}
}
