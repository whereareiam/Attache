package me.whereareiam.attache.resolution.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.attache.model.LibraryRequest;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An artifact of a dependency graph resolved for several libraries at once, with the requested libraries that
 * depend on it.
 */
@Value
@Builder
public class ResolvedDependency {
	/**
	 * The artifact in the version the graph settled on.
	 */
	@NotNull @NonNull ResolvedArtifact artifact;
	/**
	 * The requested libraries whose dependencies include this artifact in any version, in the order they were
	 * requested; a requested library lists itself.
	 */
	@NotNull @NonNull @Singular List<LibraryRequest> dependents;
}
