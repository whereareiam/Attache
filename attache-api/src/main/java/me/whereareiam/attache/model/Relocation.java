package me.whereareiam.attache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Relocations are used to describe a search and replace pattern for renaming
 * packages in a library jar for the purpose of preventing namespace conflicts
 * with other java applications that bundle their own version of the same library.
 */
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class Relocation {
	/**
	 * Search pattern
	 */
	@NotNull
	@NonNull
	String pattern;

	/**
	 * Replacement pattern
	 */
	@NotNull
	@NonNull
	String relocatedPattern;

	/**
	 * Classes and resources to include
	 */
	@NotNull
	@NonNull
	@lombok.Singular("include")
	Collection<String> includes;

	/**
	 * Classes and resources to exclude
	 */
	@NotNull
	@NonNull
	@lombok.Singular("exclude")
	Collection<String> excludes;
}