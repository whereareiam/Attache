package me.whereareiam.attache.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Canonical request model for Attache's library resolution pipeline.
 * Users can adapt their own models into this request via {@link me.whereareiam.attache.LibraryAdapter}.
 */
@Value
@Builder(toBuilder = true)
public class LibraryRequest {
	/**
	 * Direct download URLs for this library.
	 */
	@NotNull
	@NonNull
	@Singular
	Collection<String> urls;

	/**
	 * Repository URLs for this library.
	 */
	@NotNull
	@NonNull
	@Singular("repository")
	Collection<String> repositories;

	/**
	 * Fallback repository URLs for this library.
	 */
	@NotNull
	@NonNull
	@Singular("fallbackRepository")
	Collection<String> fallbackRepositories;

	/**
	 * Maven group ID.
	 */
	@NotNull
	@NonNull
	String groupId;

	/**
	 * Maven artifact ID.
	 */
	@NotNull
	@NonNull
	String artifactId;

	/**
	 * Artifact version.
	 */
	@NotNull
	@NonNull
	String version;

	/**
	 * Artifact classifier.
	 */
	@Nullable
	String classifier;

	/**
	 * Binary SHA-256 checksum for this library's jar file.
	 */
	byte[] checksum;

	/**
	 * Jar relocations to apply.
	 */
	@NotNull
	@NonNull
	@Singular("relocation")
	Collection<RelocationRule> relocations;

	/**
	 * Skip loading this library when it already exists on the classpath.
	 * Defaults to true and is ignored when relocations are configured.
	 */
	@Builder.Default
	boolean skipIfPresent = true;

	/**
	 * Should this library be loaded in an isolated class loader?
	 */
	@Builder.Default
	boolean isolated = false;

	/**
	 * The isolated loader id for this library.
	 */
	@Nullable
	String loader;

	/**
	 * Should transitive dependencies be resolved for this library?
	 */
	@Builder.Default
	boolean resolveTransitiveDependencies = false;

	/**
	 * Transitive dependencies that should be excluded on transitive resolution.
	 */
	@NotNull
	@NonNull
	@Singular("excludedTransitiveDependency")
	Collection<ExcludedDependency> excludedTransitiveDependencies;

	/**
	 * Gets whether this library has an artifact classifier.
	 *
	 * @return true if library has classifier, false otherwise
	 */
	public boolean hasClassifier() {
		return classifier != null && !classifier.isEmpty();
	}

	/**
	 * Gets whether this library has a checksum.
	 *
	 * @return true if library has checksum, false otherwise
	 */
	public boolean hasChecksum() {
		return checksum != null;
	}

	/**
	 * Gets whether this library has any jar relocations.
	 *
	 * @return true if library has relocations, false otherwise
	 */
	public boolean hasRelocations() {
		return !relocations.isEmpty();
	}

	/**
	 * Whether the library is a snapshot.
	 *
	 * @return whether the library is a snapshot.
	 */
	public boolean isSnapshot() {
		return version.endsWith("-SNAPSHOT");
	}

	/**
	 * Gets a concise, human-readable string representation of this library.
	 *
	 * @return string representation
	 */
	@Override
	public String toString() {
		String name = groupId + ':' + artifactId + ':' + version;
		if (hasClassifier()) name += ':' + classifier;

		return name.replace("{}", ".");
	}
}
