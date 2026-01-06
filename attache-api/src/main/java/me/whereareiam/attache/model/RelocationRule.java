package me.whereareiam.attache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Relocations describe a search and replace pattern for renaming packages
 * in a library jar to prevent namespace conflicts.
 */
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class RelocationRule {
	/**
	 * Search pattern.
	 */
	@NotNull
	@NonNull
	String pattern;

	/**
	 * Replacement pattern.
	 */
	@NotNull
	@NonNull
	String relocatedPattern;

	/**
	 * Classes and resources to include.
	 */
	@NotNull
	@NonNull
	@Singular("include")
	Collection<String> includes;

	/**
	 * Classes and resources to exclude.
	 */
	@NotNull
	@NonNull
	@Singular("exclude")
	Collection<String> excludes;
}
