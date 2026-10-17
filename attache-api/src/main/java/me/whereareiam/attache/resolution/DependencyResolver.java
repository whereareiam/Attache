package me.whereareiam.attache.resolution;

import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.model.ResolvedArtifact;
import me.whereareiam.attache.resolution.model.ResolvedDependency;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
	 * Collects one dependency graph for several libraries and resolves its compile/runtime artifacts, so every
	 * artifact is resolved in one version, as Maven resolves a project's dependencies: the version nearest to a
	 * requested library wins, and a requested library wins over any other version of itself. Each library's
	 * exclusions apply to its own dependencies, and a library that does not resolve transitive dependencies
	 * contributes none. Requested libraries are resolved as well.
	 *
	 * <p>
	 * The default implementation resolves each library on its own, without settling versions across
	 * libraries, and leaves the requested libraries out.
	 *
	 * @param libraries normalized requests, no two of the same artifact
	 * @param repositories ordered repository URLs
	 * @return the resolved artifacts with the requested libraries that depend on each, in graph order
	 */
	default @NotNull List<ResolvedDependency> resolve(@NotNull List<LibraryRequest> libraries, @NotNull Collection<String> repositories) {
		List<ResolvedDependency> dependencies = new ArrayList<>();
		for (LibraryRequest library : libraries) {
			if (!library.isResolveTransitiveDependencies()) continue;

			for (ResolvedArtifact artifact : resolve(library, repositories))
				dependencies.add(ResolvedDependency.builder().artifact(artifact).dependent(library).build());
		}

		return dependencies;
	}

	/**
	 * Releases transports and other resolver-owned resources. Further resolution is rejected.
	 */
	@Override
	void close();
}
