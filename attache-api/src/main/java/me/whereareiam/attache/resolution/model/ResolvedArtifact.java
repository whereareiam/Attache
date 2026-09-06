package me.whereareiam.attache.resolution.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A resolved Maven artifact and its materialized local jar, independent of the resolver backend.
 */
@Value
@Builder
public class ResolvedArtifact {
	/**
	 * Maven group identifier.
	 */
	@NotNull @NonNull String groupId;
	/**
	 * Maven artifact identifier.
	 */
	@NotNull @NonNull String artifactId;
	/**
	 * Resolved version, including a timestamp for snapshots when applicable.
	 */
	@NotNull @NonNull String version;
	/**
	 * Declared base version, retaining the SNAPSHOT suffix when applicable.
	 */
	@NotNull @NonNull String baseVersion;
	/**
	 * Optional Maven classifier.
	 */
	@Nullable String classifier;
	/**
	 * Existing local jar produced by resolution.
	 */
	@NotNull @NonNull Path file;
}
