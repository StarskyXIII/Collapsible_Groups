package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.platform.Services;

import java.util.concurrent.TimeUnit;

/**
 * Lightweight performance logging helper gated by config-backed debug options.
 */
public final class PerformanceTrace {
	private static final String PERF_TRACE_OVERRIDE =
		System.getProperty("collapsible_groups.perf_trace");

	private PerformanceTrace() {}

	public static long begin() {
		return isEnabled() ? System.nanoTime() : 0L;
	}

	public static boolean isEnabled() {
		if (PERF_TRACE_OVERRIDE != null) {
			return Boolean.parseBoolean(PERF_TRACE_OVERRIDE);
		}
		return Services.CONFIG.debugTimingEnabled();
	}

	public static long elapsedMillis(long startNanos) {
		if (!isEnabled() || startNanos == 0L) {
			return 0L;
		}
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
	}

	public static void logIfSlow(String scope, long startNanos, long thresholdMs, String detail) {
		if (!isEnabled() || startNanos == 0L) {
			return;
		}
		long elapsedMs = elapsedMillis(startNanos);
		if (elapsedMs >= thresholdMs) {
			Constants.LOG.info("[Perf] {} took {} ms {}", scope, elapsedMs, detail);
		}
	}

	public static void log(String scope, String detail) {
		if (!isEnabled()) {
			return;
		}
		Constants.LOG.info("[Perf] {} {}", scope, detail);
	}
}
