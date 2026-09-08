package com.starskyxiii.collapsible_groups.compat.kubejs;

public record KubeJsMaterializationCapture(String source, long generation, long captureToken) {
	public KubeJsMaterializationCapture {
		if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
		if (generation <= 0) throw new IllegalArgumentException("generation must be positive");
		if (captureToken <= 0) throw new IllegalArgumentException("captureToken must be positive");
	}
}
