package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the restricted ComponentPath grammar validator.
 *
 * These tests exercise {@link GroupFilterValidator#PATH_PATTERN} directly and
 * do NOT require any Minecraft runtime.
 *
 * Grammar:
 *   segment = [A-Za-z_][A-Za-z0-9_-]*(\[[0-9]+\])?
 *   path    = segment(\.segment)*
 */
class ComponentPathGrammarTest {

    // -----------------------------------------------------------------------
    // Valid paths
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "id",
        "meta",
        "_underscore",
        "with-hyphen",
        "meta.type",
        "parent.child.grandchild",
        "data[0]",
        "data[10]",
        "data[0].id",
        "data[1].meta.type",
        "spell_container",
        "Uppercase",
        "camelCase",
        "a",
        "a1",
        "a[0]",
        "a[0].b",
        "a[0].b[1].c",
    })
    void validPaths(String path) {
        assertTrue(
            GroupFilterValidator.PATH_PATTERN.matcher(path).matches(),
            "Expected path to be valid: '" + path + "'"
        );
    }

    // -----------------------------------------------------------------------
    // Invalid paths — spec-required rejections
    // -----------------------------------------------------------------------

    @Test
    void rejectsEmptyString() {
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("").matches());
    }

    @Test
    void rejectsWildcardIndex() {
        // data[*].id — wildcard, not a digit
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("data[*].id").matches());
    }

    @Test
    void rejectsNegativeIndex() {
        // data[-1] — negative index
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("data[-1]").matches());
    }

    @Test
    void rejectsEmptyBrackets() {
        // data[].id — no index value
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("data[].id").matches());
    }

    @Test
    void rejectsDoubledDots() {
        // data..id — two consecutive dots produce an empty segment
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("data..id").matches());
    }

    @Test
    void rejectsTrailingDot() {
        // data. — trailing dot produces an empty segment
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("data.").matches());
    }

    @Test
    void rejectsLeadingDot() {
        // .field — leading dot produces an empty first segment
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher(".field").matches());
    }

    @Test
    void rejectsNumericStart() {
        // 0data — field names must start with letter or underscore
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("0data").matches());
    }

    @Test
    void rejectsDotOnly() {
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher(".").matches());
    }

    @Test
    void rejectsArrayOnlyPath() {
        // [0] — no field name prefix
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("[0]").matches());
    }

    @Test
    void rejectsSpacesInPath() {
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("data .id").matches());
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher(" data").matches());
    }

    @Test
    void rejectsWildcardStar() {
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("*").matches());
    }

    @Test
    void rejectsRecursiveDescent() {
        // ..id — recursive descent notation
        assertFalse(GroupFilterValidator.PATH_PATTERN.matcher("..id").matches());
    }
}
