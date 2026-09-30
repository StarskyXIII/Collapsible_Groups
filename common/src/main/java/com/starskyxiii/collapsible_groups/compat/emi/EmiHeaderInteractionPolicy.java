package com.starskyxiii.collapsible_groups.compat.emi;

/** Decision-only half of EMI's synthetic-header interaction gate. */
public final class EmiHeaderInteractionPolicy {
	private EmiHeaderInteractionPolicy() {}

	public enum Action { ACTIVATE, OTHER }
	public enum Decision { TOGGLE_AND_CONSUME, REJECT }

	public static Decision decide(Action action) {
		return action == Action.ACTIVATE ? Decision.TOGGLE_AND_CONSUME : Decision.REJECT;
	}
}
