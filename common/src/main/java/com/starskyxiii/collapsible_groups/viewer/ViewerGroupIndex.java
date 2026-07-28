package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Viewer-neutral ownership/cache seam used by viewer integrations.
 *
 * <p>The implementation is the single source of truth for the following lifecycle contract.
 * “Rebuild” is asynchronous, “re-resolve” walks the already-built candidate edges only, and
 * “retain” means that the exact cache generation remains available.
 *
 * <table>
 *   <caption>Event-by-cache-layer lifecycle contract</caption>
 *   <tr><th>Event</th><th>Candidate index</th><th>Enabled-dependent resolved caches</th>
 *       <th>Enabled-independent full-match caches</th><th>Preview caches</th></tr>
 *   <tr><td>FULL</td><td>rebuild asynchronously</td><td>repopulate with rebuild</td>
 *       <td>clear; refill lazily or with rebuild</td><td>clear</td></tr>
 *   <tr><td>ENABLED</td><td>retain</td><td>re-resolve immediately from candidates</td>
 *       <td>retain</td><td>retain</td></tr>
 *   <tr><td>STRUCTURE</td><td>retain</td><td>retain</td><td>retain</td><td>retain</td></tr>
 *   <tr><td>KUBEJS_REPLACE</td><td>rebuild asynchronously</td><td>repopulate with rebuild</td>
 *       <td>clear</td><td>clear</td></tr>
 * </table>
 *
 * <p>Editor entry must use {@link #ready()} and {@link #whenReady()} rather than forcing a
 * synchronous candidate rebuild. Implementations may derive editor ownership from the ready
 * candidate index; otherwise the editor displays a loading placeholder until the asynchronous
 * rebuild completes.
 */
public interface ViewerGroupIndex {
	/** Returns the current enabled-independent candidate generation, if one is ready. */
	Optional<GroupCandidateIndex> candidates();

	/** True only when a candidate generation and its enabled-dependent resolved caches are ready. */
	boolean ready();

	/** Completes when the current asynchronous rebuild, if any, has published its generation. */
	CompletableFuture<Void> whenReady();

	/**
	 * Returns one coherent, enabled-independent full-match preview from the published generation.
	 * An empty optional means no generation has been published (or the viewer must still populate
	 * that generation's preview cache); present snapshots retain explicit empty kind buckets.
	 */
	Optional<ViewerGroupPreviewSnapshot> fullMatchSnapshot(GroupDefinition group);

	/** Resolves one current enabled owner by walking only this identity's candidate list. */
	Optional<String> resolveOwner(ViewerIngredientIdentity identity, List<GroupDefinition> groups);

	/** Resolves all current enabled owners from the ready candidate generation. */
	Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups);

	/** Applies the cache action prescribed by the lifecycle table. */
	void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups);
}
