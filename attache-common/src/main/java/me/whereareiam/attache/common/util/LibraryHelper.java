package me.whereareiam.attache.common.util;

import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for Library business logic.
 * Handles path calculations, transformations, and processing that shouldn't be in the model.
 */
public final class LibraryHelper {
	/**
	 * Normalizes a library by applying transformations like replacing "{}" with "."
	 * and ensuring repositories end with "/".
	 * <p>
	 * This MUST be called before downloading or processing a library.
	 *
	 * @param library the library to normalize
	 * @return a new library with normalized values
	 */
	@NotNull
	public static LibraryRequest normalize(@NotNull LibraryRequest library) {
		requireNonNull(library, "library");

		return library.toBuilder()
				.clearUrls()
				.urls(library.getUrls()) // Keep URLs as-is
				.groupId(replaceWithDots(library.getGroupId()))
				.artifactId(replaceWithDots(library.getArtifactId()))
				.clearExcludedTransitiveDependencies()
				.excludedTransitiveDependencies(library.getExcludedTransitiveDependencies().stream()
						.map(excluded -> new ExcludedDependency(replaceWithDots(excluded.getGroupId()),
								replaceWithDots(excluded.getArtifactId()))).toList())
				.clearRepositories()
				.repositories(normalizeRepositories(library.getRepositories()))
				.clearFallbackRepositories()
				.fallbackRepositories(normalizeRepositories(library.getFallbackRepositories()))
				.clearRelocations()
				.relocations(normalizeRelocations(library.getRelocations()))
				.build();
	}

	/**
	 * Normalizes repository URLs by ensuring they end with "/".
	 *
	 * @param repositories the repositories to normalize
	 * @return normalized repositories
	 */
	@NotNull
	private static Collection<String> normalizeRepositories(@NotNull Collection<String> repositories) {
		return repositories.stream()
				.map(url -> url.endsWith("/") ? url : url + '/')
				.collect(Collectors.toList());
	}

	/**
	 * Normalizes relocations by applying transformations.
	 *
	 * @param relocations the relocations to normalize
	 * @return normalized relocations
	 */
	@NotNull
	private static Collection<RelocationRule> normalizeRelocations(@NotNull Collection<RelocationRule> relocations) {
		return relocations.stream()
				.map(LibraryHelper::normalizeRelocation)
				.collect(Collectors.toList());
	}

	/**
	 * Normalizes single relocation.
	 *
	 * @param relocation the relocation to normalize
	 * @return normalized relocation
	 */
	@NotNull
	private static RelocationRule normalizeRelocation(@NotNull RelocationRule relocation) {
		return relocation.toBuilder()
				.pattern(replaceWithDots(relocation.getPattern()))
				.relocatedPattern(replaceWithDots(relocation.getRelocatedPattern()))
				.clearIncludes()
				.includes(normalizePatterns(relocation.getIncludes()))
				.clearExcludes()
				.excludes(normalizePatterns(relocation.getExcludes()))
				.build();
	}

	/**
	 * Replaces "{}" with "." in the provided string.
	 * <p>
	 * This is used for normalization of library coordinates and relocation patterns
	 * since "{}" is used as a convenience syntax to avoid escaping dots in annotations.
	 *
	 * @param str the string to normalize
	 * @return normalized string
	 */
	@NotNull
	public static String replaceWithDots(@NotNull String str) {
		return str.replace("{}", ".");
	}

	/**
	 * Gets the relative partial Maven path to this library.
	 * <p>
	 * Example: {@code com/example/my-lib/1.0.0/}
	 *
	 * @param library the library
	 * @return relative partial Maven path for this library
	 */
	@NotNull
	public static String getPartialPath(@NotNull LibraryRequest library) {
		requireNonNull(library, "library");

		String groupId = library.getGroupId();
		String artifactId = library.getArtifactId();
		String version = library.getVersion();

		return groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/';
	}

	/**
	 * Gets the relative Maven path to this library's artifact.
	 * <p>
	 * Example: {@code com/example/my-lib/1.0.0/my-lib-1.0.0.jar}
	 *
	 * @param library the library
	 * @return relative Maven path for this library
	 */
	@NotNull
	public static String getPath(@NotNull LibraryRequest library) {
		requireNonNull(library, "library");

		String partialPath = getPartialPath(library);
		String artifactId = library.getArtifactId();
		String version = library.getVersion();
		String classifier = library.getClassifier();

		String path = partialPath + artifactId + '-' + version;
		if (classifier != null && !classifier.isEmpty())
			path += '-' + classifier;

		return path + ".jar";
	}

	/**
	 * Gets the relative path to this library's relocated jar.
	 *
	 * @param library the library
	 * @return path to relocated artifact or null if it has no relocations
	 */
	@Nullable
	public static String getRelocatedPath(@NotNull LibraryRequest library) {
		requireNonNull(library, "library");

		if (!library.hasRelocations())
			return null;

		String path = getPath(library);
		return path + "-relocated-" + Math.abs(library.getRelocations().hashCode()) + ".jar";
	}

	@NotNull
	private static Collection<String> normalizePatterns(@Nullable Collection<String> patterns) {
		if (patterns == null) return List.of();

		return patterns.stream()
				.map(LibraryHelper::replaceWithDots)
				.collect(Collectors.toSet());
	}
}
