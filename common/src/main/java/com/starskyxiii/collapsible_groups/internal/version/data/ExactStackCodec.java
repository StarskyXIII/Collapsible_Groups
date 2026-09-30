package com.starskyxiii.collapsible_groups.internal.version.data;

import java.util.Optional;

public interface ExactStackCodec<S> {
	ItemDataFormat format();

	S normalizedCopy(S stack);

	Optional<String> encodeLegacy(S stack);

	Optional<ItemDataPayload> encodePayload(S stack);

	Object registryIdentity();

	DecodeSnapshot<S> beginDecode();

	boolean equivalent(S left, S right);

	String itemId(S stack);

	interface DecodeSnapshot<S> {
		boolean liveRegistry();

		Object registryIdentity();

		Optional<S> decode(String encoded);
	}
}
