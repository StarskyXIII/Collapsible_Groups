package com.starskyxiii.collapsible_groups.compat.emi;

/** Reload-epoch gate: registration dirties, and the first loaded INDEX access claims bootstrap. */
public final class EmiBootstrapGate {
	private long epoch;
	private long claimedEpoch = -1;
	private long completedEpoch = -1;

	public synchronized long markDirty() {
		return ++epoch;
	}

	public synchronized boolean tryClaim(boolean loaded, boolean indexAccess) {
		if (!loaded || !indexAccess || claimedEpoch == epoch || completedEpoch == epoch) return false;
		claimedEpoch = epoch;
		return true;
	}

	public synchronized void complete() {
		completedEpoch = epoch;
	}

	public synchronized void releaseFailedClaim() {
		if (completedEpoch != epoch) claimedEpoch = -1;
	}

	public synchronized boolean ready() {
		return completedEpoch == epoch;
	}

	public synchronized long epoch() {
		return epoch;
	}
}
