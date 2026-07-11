package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupFilterRuleDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boundary matrix for {@link EditorRulesPanel#resolveSlot}: band edges
 * f=0.24/0.25/0.74/0.75/0.99/1.0 (gap folds into the lower band), the exact two-point no-op
 * suppression set {oldIndex, oldIndex+1}, the collapsed/empty-compound lower-band branch, and
 * the root row's missing top band. Pure-data function — no MC types are touched at class init.
 */
class EditorRulesPanelDropSlotTest {

	// Tree layout (model indices in root):
	//   root ALL                    depth 0
	//   ├─ any ANY        idx 0     depth 1   (one child → expanded/hasVisibleChildren)
	//   │   └─ x ID                 depth 2
	//   ├─ emptyCmp ANY   idx 1     depth 1   (no children)
	//   ├─ a ID           idx 2     depth 1
	//   ├─ b TAG          idx 3     depth 1
	//   ├─ c NAMESPACE    idx 4     depth 1
	//   ├─ other ANY      idx 5     depth 1
	//   │   └─ d ID                 depth 2   (default drag node — its parent never collides
	//   │                                      with root/any, so no-op suppression stays off)
	private GroupFilterRuleDraft draft;
	private GroupFilterRuleDraft.Node root;
	private GroupFilterRuleDraft.Node any;
	private GroupFilterRuleDraft.Node emptyCmp;
	private GroupFilterRuleDraft.Node a;
	private GroupFilterRuleDraft.Node b;
	private GroupFilterRuleDraft.Node c;
	private GroupFilterRuleDraft.Node other;
	private GroupFilterRuleDraft.Node d;

	@BeforeEach
	void setUp() {
		draft = GroupFilterRuleDraft.empty();
		root = draft.setRoot(GroupFilterRuleDraft.NodeKind.ALL);
		any = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		GroupFilterRuleDraft.Node x = draft.insertRelativeTo(any, GroupFilterRuleDraft.NodeKind.ID);
		assertNotNull(x);
		emptyCmp = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		a = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ID);
		b = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.TAG);
		c = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.NAMESPACE);
		other = draft.insertRelativeTo(root, GroupFilterRuleDraft.NodeKind.ANY);
		d = draft.insertRelativeTo(other, GroupFilterRuleDraft.NodeKind.ID);
		assertEquals(java.util.List.of(any, emptyCmp, a, b, c, other), root.children());
	}

	/** Hover the expanded compound `any` (idx 0, depth 1) dragging the unrelated node d. */
	private EditorRulesPanel.DropSlot onAny(double f) {
		return EditorRulesPanel.resolveSlot(f, any, root, 0, 1, true, other, 0);
	}

	// ── Band boundaries on a non-root expanded compound ───────────────────

	@Test
	void compoundTopBandBelowQuarterInsertsAboveAsSibling() {
		EditorRulesPanel.DropSlot slot = onAny(0.24);
		assertEquals(EditorRulesPanel.SlotKind.BETWEEN, slot.kind());
		assertSame(root, slot.parent());
		assertEquals(0, slot.index());
		assertEquals(1, slot.depth());
	}

	@Test
	void compoundMiddleBandStartsExactlyAtQuarter() {
		EditorRulesPanel.DropSlot slot = onAny(0.25);
		assertEquals(EditorRulesPanel.SlotKind.INTO, slot.kind());
		assertSame(any, slot.parent());
	}

	@Test
	void compoundMiddleBandEndsJustBeforeThreeQuarters() {
		EditorRulesPanel.DropSlot slot = onAny(0.74);
		assertEquals(EditorRulesPanel.SlotKind.INTO, slot.kind());
		assertSame(any, slot.parent());
	}

	@Test
	void expandedCompoundLowerBandBecomesFirstChild() {
		for (double f : new double[] {0.75, 0.99}) {
			EditorRulesPanel.DropSlot slot = onAny(f);
			assertEquals(EditorRulesPanel.SlotKind.BETWEEN, slot.kind(), "f=" + f);
			assertSame(any, slot.parent(), "f=" + f);
			assertEquals(0, slot.index(), "f=" + f);
			assertEquals(2, slot.depth(), "first child indents one level deeper, f=" + f);
		}
	}

	@Test
	void gapBandFoldsIntoLowerBandInsteadOfDroppingTheSlot() {
		// f >= 1.0 (pointer in the ROW_GAP strip) must yield the same slot as the
		// row's lower band, never null-by-omission.
		EditorRulesPanel.DropSlot slot = onAny(1.05);
		assertNotNull(slot);
		assertEquals(EditorRulesPanel.SlotKind.BETWEEN, slot.kind());
		assertSame(any, slot.parent());
		assertEquals(0, slot.index());
	}

	// ── Collapsed / empty compound lower band ─────────────────────────────

	@Test
	void emptyCompoundLowerBandInsertsBelowAsSibling() {
		// emptyCmp: idx 1 in root, no visible children → BETWEEN(node.parent, idx+1).
		for (double f : new double[] {0.75, 1.0}) {
			EditorRulesPanel.DropSlot slot =
				EditorRulesPanel.resolveSlot(f, emptyCmp, root, 1, 1, false, other, 0);
			assertEquals(EditorRulesPanel.SlotKind.BETWEEN, slot.kind(), "f=" + f);
			assertSame(root, slot.parent(), "f=" + f);
			assertEquals(2, slot.index(), "f=" + f);
			assertEquals(1, slot.depth(), "sibling stays at the row's own depth, f=" + f);
		}
	}

	@Test
	void collapsedCompoundLowerBandInsertsBelowAsSibling() {
		// A collapsed `any` reports hasVisibleChildren=false — same branch as the empty compound:
		// the visual gap under the header borders the *next sibling*, not the hidden child layer.
		EditorRulesPanel.DropSlot slot =
			EditorRulesPanel.resolveSlot(0.9, any, root, 0, 1, false, other, 0);
		assertEquals(EditorRulesPanel.SlotKind.BETWEEN, slot.kind());
		assertSame(root, slot.parent());
		assertEquals(1, slot.index());
	}

	// ── Root row: no top band ──────────────────────────────────────────────

	@Test
	void rootHasNoTopBandItsUpperHalfIsInto() {
		for (double f : new double[] {0.05, 0.24, 0.5}) {
			EditorRulesPanel.DropSlot slot =
				EditorRulesPanel.resolveSlot(f, root, null, -1, 0, true, other, 0);
			assertEquals(EditorRulesPanel.SlotKind.INTO, slot.kind(), "f=" + f);
			assertSame(root, slot.parent(), "f=" + f);
		}
	}

	@Test
	void rootLowerBandStillOffersFirstChildSlot() {
		EditorRulesPanel.DropSlot slot =
			EditorRulesPanel.resolveSlot(0.8, root, null, -1, 0, true, other, 0);
		assertEquals(EditorRulesPanel.SlotKind.BETWEEN, slot.kind());
		assertSame(root, slot.parent());
		assertEquals(0, slot.index());
		assertEquals(1, slot.depth());
	}

	// ── Leaf rows: half split, gap folds down ─────────────────────────────

	@Test
	void leafUpperHalfInsertsAboveLowerHalfBelow() {
		// b sits at idx 3; drag d so nothing suppresses.
		EditorRulesPanel.DropSlot upper =
			EditorRulesPanel.resolveSlot(0.49, b, root, 3, 1, false, other, 0);
		assertEquals(EditorRulesPanel.SlotKind.BETWEEN, upper.kind());
		assertSame(root, upper.parent());
		assertEquals(3, upper.index());

		for (double f : new double[] {0.5, 1.0}) {
			EditorRulesPanel.DropSlot lower =
				EditorRulesPanel.resolveSlot(f, b, root, 3, 1, false, other, 0);
			assertEquals(EditorRulesPanel.SlotKind.BETWEEN, lower.kind(), "f=" + f);
			assertSame(root, lower.parent(), "f=" + f);
			assertEquals(4, lower.index(), "f=" + f);
		}
	}

	// ── No-op suppression: exactly {oldIndex, oldIndex+1} ─────────────────

	@Test
	void noOpSuppressionCoversExactlyTheTwoGapsAroundTheDraggedNode() {
		// Drag b itself (parent root, oldIndex 3).
		// Hovering b's own upper band → BETWEEN(root, 3) == oldIndex → suppressed.
		assertNull(EditorRulesPanel.resolveSlot(0.2, b, root, 3, 1, false, root, 3));
		// Hovering b's own lower band → BETWEEN(root, 4) == oldIndex+1 → suppressed.
		assertNull(EditorRulesPanel.resolveSlot(0.8, b, root, 3, 1, false, root, 3));

		// One step outside the set on either side is NOT suppressed:
		// a's upper band → BETWEEN(root, 2) == oldIndex-1 → live slot.
		EditorRulesPanel.DropSlot above =
			EditorRulesPanel.resolveSlot(0.2, a, root, 2, 1, false, root, 3);
		assertNotNull(above);
		assertEquals(2, above.index());
		// c's lower band → BETWEEN(root, 5) == oldIndex+2 → live slot.
		EditorRulesPanel.DropSlot below =
			EditorRulesPanel.resolveSlot(0.8, c, root, 4, 1, false, root, 3);
		assertNotNull(below);
		assertEquals(5, below.index());
	}

	@Test
	void noOpSuppressionOnlyAppliesToMatchingParent() {
		// Same indices but a different parent must not suppress: drag d (parent other, oldIndex 0)
		// while hovering any's first-child gap BETWEEN(any, 0) — index 0 matches oldIndex but the
		// parents differ.
		EditorRulesPanel.DropSlot slot = onAny(0.8);
		assertNotNull(slot);
		assertSame(any, slot.parent());
		assertEquals(0, slot.index());
	}

	@Test
	void intoSlotsAreNeverNoOpSuppressed() {
		// Dragging b over any's middle band yields INTO(any) even though a BETWEEN at the same
		// coordinates could be near b's own gaps — suppression is a BETWEEN-only rule.
		EditorRulesPanel.DropSlot slot =
			EditorRulesPanel.resolveSlot(0.5, any, root, 0, 1, true, root, 3);
		assertNotNull(slot);
		assertEquals(EditorRulesPanel.SlotKind.INTO, slot.kind());
		assertSame(any, slot.parent());
	}
}
