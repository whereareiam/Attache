package me.whereareiam.attache.resolution;

import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.model.ResolvedArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Resolves a library's runtime dependencies without exposing a Maven implementation to callers.
 */
public interface DependencyResolver extends AutoCloseable {
	/**
	 * Collects the dependency graph and resolves compile/runtime artifacts, excluding the root
	 * library itself. An excluded artifact is left out of the graph together with its own
	 * dependencies, as a Maven exclusion is; those stay only where another path reaches them.
	 * Repository order is preserved.
	 *
	 * @param library normalized root request
	 * @param repositories ordered repository URLs
	 * @return resolved transitive artifacts with their existing local files
	 */
	@NotNull Collection<ResolvedArtifact> resolve(@NotNull LibraryRequest library, @NotNull Collection<String> repositories);

	/**
	 * Releases transports and other resolver-owned resources. Further resolution is rejected.
	 */
	@Override
	void close();
}
