package com.starskyxiii.collapsible_groups.config;

/**
 * Parses user-facing ARGB color config values.
 *
 * <p>Accepted formats: {@code #AARRGGBB}, {@code 0xAARRGGBB}, {@code AARRGGBB},
 * and the same three forms with RGB only. RGB values inherit the fallback
 * alpha so users can adjust hue without accidentally making a solid overlay.
 */
public final class ColorConfigParser {
	private ColorConfigParser() {}

	public static int parseArgb(String value, int fallback) {
		String hex = normalizedHex(value);
		if (hex == null) return fallback;

		long parsed = Long.parseUnsignedLong(hex, 16);
		if (hex.length() == 6) {
			return (fallback & 0xFF000000) | (int) parsed;
		}
		return (int) parsed;
	}

	public static int parseRgb(String value, int fallback) {
		return parseArgb(value, fallback) & 0x00FFFFFF;
	}

	public static boolean isValidArgb(String value) {
		return normalizedHex(value) != null;
	}

	private static String normalizedHex(String value) {
		if (value == null) return null;

		String hex = value.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		} else if (hex.startsWith("0x") || hex.startsWith("0X")) {
			hex = hex.substring(2);
		}

		int length = hex.length();
		if ((length != 6 && length != 8) || !isHex(hex)) {
			return null;
		}
		return hex;
	}

	private static boolean isHex(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean digit = c >= '0' && c <= '9';
			boolean lower = c >= 'a' && c <= 'f';
			boolean upper = c >= 'A' && c <= 'F';
			if (!digit && !lower && !upper) return false;
		}
		return true;
	}
}
