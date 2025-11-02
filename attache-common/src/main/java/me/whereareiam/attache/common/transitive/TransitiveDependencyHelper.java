package me.whereareiam.attache.common.transitive;

import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.classloader.IsolatedClassLoader;
import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A reflection-based helper for resolving transitive dependencies. It automatically
 * downloads Libby Maven Resolver to resolve transitive dependencies.
 * <p>
 * This class loads the maven resolver library in an isolated classloader and uses
 * reflection to invoke the dependency resolution methods.
 * <p>
 * This class implements {@link AutoCloseable} to properly clean up the isolated classloader.
 */
public class TransitiveDependencyHelper implements AutoCloseable {

	/**
	 * com.alessiodp.libby.maven.resolver.TransitiveDependencyCollector class name for reflections
	 */
	private static final String TRANSITIVE_DEPENDENCY_COLLECTOR_CLASS = LibraryHelper.replaceWithDots("com{}alessiodp{}libby{}maven{}resolver{}TransitiveDependencyCollector");

	/**
	 * org.eclipse.aether.artifact.Artifact class name for reflections
	 */
	private static final String ARTIFACT_CLASS = LibraryHelper.replaceWithDots("org{}eclipse{}aether{}artifact{}Artifact");

	/**
	 * Maven resolver library coordinates
	 */
	private static final String MAVEN_RESOLVER_GROUP = "com{}alessiodp{}libby{}maven{}resolver";
	private static final String MAVEN_RESOLVER_ARTIFACT = "libby-maven-resolver";
	private static final String MAVEN_RESOLVER_VERSION = "1.0.1";
	private static final String MAVEN_RESOLVER_CHECKSUM_BASE64 = "EmsSUwjtqSeYTt8WEw7LPI/5Yz8bWSxf23XcdLEM7dk=";
	private static final String MAVEN_RESOLVER_FALLBACK_REPO = "https://repo.alessiodp.com/releases";

	/**
	 * TransitiveDependencyCollector class instance, used in {@link #findTransitiveLibraries(Library)}
	 */
	private final Object transitiveDependencyCollectorObject;

	/**
	 * Reflected method for resolving transitive dependencies
	 */
	private final Method resolveTransitiveDependenciesMethod;

	/**
	 * Reflected getter methods of Artifact class
	 */
	private final Method artifactGetGroupIdMethod;
	private final Method artifactGetArtifactIdMethod;
	private final Method artifactGetVersionMethod;
	private final Method artifactGetBaseVersionMethod;
	private final Method artifactGetClassifierMethod;

	/**
	 * BaseLibraryManager instance, used in {@link #findTransitiveLibraries(Library)}
	 */
	private final BaseLibraryManager libraryManager;

	/**
	 * Isolated class loader for the maven resolver library
	 */
	private final IsolatedClassLoader classLoader;

	/**
	 * Creates a new transitive dependency helper using the provided library manager to
	 * download the dependencies required for transitive dependency resolution in runtime.
	 *
	 * @param libraryManager the library manager used to download dependencies
	 * @param saveDirectory  the directory where all transitive dependencies would be saved
	 */
	public TransitiveDependencyHelper(@NotNull BaseLibraryManager libraryManager, @NotNull Path saveDirectory) {
		requireNonNull(libraryManager, "libraryManager");
		requireNonNull(saveDirectory, "saveDirectory");
		this.libraryManager = libraryManager;
		this.classLoader = new IsolatedClassLoader();

		try {
			loadMavenResolverLibrary();
			ReflectionObjects reflectionObjects = initializeReflectionObjects(saveDirectory);

			this.transitiveDependencyCollectorObject = reflectionObjects.collectorObject;
			this.resolveTransitiveDependenciesMethod = reflectionObjects.resolveMethod;
			this.artifactGetGroupIdMethod = reflectionObjects.getGroupIdMethod;
			this.artifactGetArtifactIdMethod = reflectionObjects.getArtifactIdMethod;
			this.artifactGetVersionMethod = reflectionObjects.getVersionMethod;
			this.artifactGetBaseVersionMethod = reflectionObjects.getBaseVersionMethod;
			this.artifactGetClassifierMethod = reflectionObjects.getClassifierMethod;
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize transitive dependency helper", e);
		}
	}

	/**
	 * Loads the maven resolver library into the isolated classloader.
	 *
	 * @throws Exception if loading fails
	 */
	private void loadMavenResolverLibrary() throws Exception {
		Library mavenResolver = Library.builder()
				.groupId(MAVEN_RESOLVER_GROUP)
				.artifactId(MAVEN_RESOLVER_ARTIFACT)
				.version(MAVEN_RESOLVER_VERSION)
				.checksum(Base64.getDecoder().decode(MAVEN_RESOLVER_CHECKSUM_BASE64))
				.fallbackRepository(Repositories.MAVEN_CENTRAL)
				.fallbackRepository(MAVEN_RESOLVER_FALLBACK_REPO)
				.build();

		classLoader.addPath(libraryManager.downloadLibrary(mavenResolver));
	}

	/**
	 * Initializes all reflection objects needed for transitive dependency resolution.
	 *
	 * @param saveDirectory the directory where transitive dependencies will be saved
	 * @return initialized reflection objects
	 * @throws Exception if initialization fails
	 */
	private ReflectionObjects initializeReflectionObjects(Path saveDirectory) throws Exception {
		Class<?> collectorClass = classLoader.loadClass(TRANSITIVE_DEPENDENCY_COLLECTOR_CLASS);
		Class<?> artifactClass = classLoader.loadClass(ARTIFACT_CLASS);

		// Initialize TransitiveDependencyCollector instance
		Constructor<?> constructor = collectorClass.getConstructor(Path.class);
		constructor.setAccessible(true);
		Object collectorObject = constructor.newInstance(saveDirectory);

		// Get the method for resolving transitive dependencies
		Method resolveMethod = collectorClass.getMethod(
				"findTransitiveDependencies",
				String.class, String.class, String.class, String.class, Stream.class
		);
		resolveMethod.setAccessible(true);

		// Get artifact getter methods
		Method getGroupIdMethod = artifactClass.getMethod("getGroupId");
		Method getArtifactIdMethod = artifactClass.getMethod("getArtifactId");
		Method getVersionMethod = artifactClass.getMethod("getVersion");
		Method getBaseVersionMethod = artifactClass.getMethod("getBaseVersion");
		Method getClassifierMethod = artifactClass.getMethod("getClassifier");

		return new ReflectionObjects(
				collectorObject,
				resolveMethod,
				getGroupIdMethod,
				getArtifactIdMethod,
				getVersionMethod,
				getBaseVersionMethod,
				getClassifierMethod
		);
	}

	/**
	 * Finds and returns a collection of transitive libraries for a given library.
	 * <p>
	 * This method fetches the transitive dependencies of the provided library using reflection-based
	 * interaction with the underlying transitive dependency collector. The method ensures to filter out
	 * any excluded transitive dependencies as specified by the provided library.
	 * </p>
	 * <p>
	 * Note: The method merges the repositories from both the library manager and the given library
	 * for dependency resolution. It also clones all relocations into transitive libraries.
	 * </p>
	 *
	 * @param library The primary library for which transitive dependencies need to be found.
	 * @return A collection of {@link Library} objects representing the transitive libraries
	 * excluding the ones marked as excluded in the provided library.
	 * @throws RuntimeException If there's any exception during the reflection-based operations.
	 */
	@NotNull
	public Collection<Library> findTransitiveLibraries(@NotNull Library library) {
		requireNonNull(library, "library");

		validateRepositories(library);

		Set<ExcludedDependency> excludedDependencies = new HashSet<>(library.getExcludedTransitiveDependencies());
		Collection<?> resolvedArtifacts = resolveArtifacts(library);

		List<Library> transitiveLibraries = new ArrayList<>();
		for (Object resolved : resolvedArtifacts) {
			Library transitiveLibrary = processResolvedArtifact(resolved, library, excludedDependencies);
			if (transitiveLibrary != null)
				transitiveLibraries.add(transitiveLibrary);
		}

		return Collections.unmodifiableCollection(transitiveLibraries);
	}

	/**
	 * Validates that at least one repository is configured for dependency resolution.
	 *
	 * @param library the library to validate repositories for
	 * @throws IllegalArgumentException if no repositories are configured
	 */
	private void validateRepositories(@NotNull Library library) {
		Collection<String> globalRepositories = libraryManager.getRepositories();
		Collection<String> libraryRepositories = library.getRepositories();
		Collection<String> libraryFallbackRepositories = library.getFallbackRepositories();

		if (globalRepositories.isEmpty() && libraryRepositories.isEmpty() && libraryFallbackRepositories.isEmpty())
			throw new IllegalArgumentException("No repositories have been added before resolving transitive dependencies");
	}

	/**
	 * Resolves artifacts using the maven resolver.
	 *
	 * @param library the library to resolve transitive dependencies for
	 * @return collection of resolved artifacts
	 * @throws RuntimeException if resolution fails
	 */
	private Collection<?> resolveArtifacts(@NotNull Library library) {
		// Normalize the library to replace {} with . in coordinates
		Library normalizedLibrary = LibraryHelper.normalize(library);
		Stream<String> repositories = libraryManager.resolveRepositories(normalizedLibrary).stream();

		try {
			return (Collection<?>) resolveTransitiveDependenciesMethod.invoke(
					transitiveDependencyCollectorObject,
					normalizedLibrary.getGroupId(),
					normalizedLibrary.getArtifactId(),
					normalizedLibrary.getVersion(),
					normalizedLibrary.getClassifier(),
					repositories
			);
		} catch (Exception e) {
			throw new RuntimeException("Failed to resolve transitive dependencies for " + library, e);
		}
	}

	/**
	 * Processes a single resolved artifact entry and converts it to a Library object.
	 *
	 * @param resolved             the resolved artifact entry
	 * @param parentLibrary        the parent library
	 * @param excludedDependencies set of dependencies to exclude
	 * @return the transitive library, or null if it should be skipped
	 */
	@Nullable
	private Library processResolvedArtifact(
			@NotNull Object resolved,
			@NotNull Library parentLibrary,
			@NotNull Set<ExcludedDependency> excludedDependencies
	) {
		try {
			Entry<?, ?> resolvedEntry = (Entry<?, ?>) resolved;
			Object artifact = resolvedEntry.getKey();
			String repository = (String) resolvedEntry.getValue();

			// Extract artifact information
			String groupId = (String) artifactGetGroupIdMethod.invoke(artifact);
			String artifactId = (String) artifactGetArtifactIdMethod.invoke(artifact);
			String baseVersion = (String) artifactGetBaseVersionMethod.invoke(artifact);
			String classifier = (String) artifactGetClassifierMethod.invoke(artifact);
			String version = (String) artifactGetVersionMethod.invoke(artifact);

			// Skip the library itself
			if (parentLibrary.getGroupId().equals(groupId) && parentLibrary.getArtifactId().equals(artifactId))
				return null;

			// Skip excluded dependencies
			if (excludedDependencies.contains(new ExcludedDependency(groupId, artifactId)))
				return null;

			return buildTransitiveLibrary(groupId, artifactId, baseVersion, classifier, version, repository, parentLibrary);
		} catch (Exception e) {
			throw new RuntimeException("Failed to process resolved artifact", e);
		}
	}

	/**
	 * Builds a Library object for a transitive dependency.
	 *
	 * @param groupId       the Maven group ID
	 * @param artifactId    the Maven artifact ID
	 * @param baseVersion   the base version (without SNAPSHOT timestamp)
	 * @param classifier    the artifact classifier (can be null)
	 * @param version       the full version (with SNAPSHOT timestamp if applicable)
	 * @param repository    the repository URL where the artifact was found (can be null)
	 * @param parentLibrary the parent library to inherit properties from
	 * @return the built Library object
	 */
	@NotNull
	private Library buildTransitiveLibrary(
			@NotNull String groupId,
			@NotNull String artifactId,
			@NotNull String baseVersion,
			@Nullable String classifier,
			@NotNull String version,
			@Nullable String repository,
			@NotNull Library parentLibrary
	) {
		var libraryBuilder = Library.builder()
				.groupId(groupId)
				.artifactId(artifactId)
				.version(baseVersion)
				.isolated(parentLibrary.isIsolated())
				.loader(parentLibrary.getLoader());

		// Add classifier if present
		if (classifier != null && !classifier.isEmpty())
			libraryBuilder.classifier(classifier);

		// Clone all relocations from the parent library
		parentLibrary.getRelocations().forEach(libraryBuilder::relocation);

		// Configure repository or URL
		if (repository != null) {
			String directUrl = constructDirectDownloadUrl(repository, groupId, artifactId, baseVersion, version, classifier);
			libraryBuilder.url(directUrl);
		} else {
			parentLibrary.getRepositories().forEach(libraryBuilder::repository);
			parentLibrary.getFallbackRepositories().forEach(libraryBuilder::fallbackRepository);
		}

		return libraryBuilder.build();
	}

	/**
	 * Constructs a direct download URL for an artifact.
	 *
	 * @param repository  the repository URL
	 * @param groupId     the Maven group ID
	 * @param artifactId  the Maven artifact ID
	 * @param baseVersion the base version
	 * @param version     the full version (with SNAPSHOT timestamp if applicable)
	 * @param classifier  the artifact classifier (can be null)
	 * @return the constructed download URL
	 */
	@NotNull
	private String constructDirectDownloadUrl(
			@NotNull String repository,
			@NotNull String groupId,
			@NotNull String artifactId,
			@NotNull String baseVersion,
			@NotNull String version,
			@Nullable String classifier
	) {
		// Ensure repository ends with "/"
		if (!repository.endsWith("/")) {
			repository = repository + '/';
		}

		// Build a temporary library to get the partial path
		Library tempLibrary = Library.builder()
				.groupId(groupId)
				.artifactId(artifactId)
				.version(baseVersion)
				.build();
		String partialPath = LibraryHelper.getPartialPath(tempLibrary);

		// Construct the full path
		StringBuilder pathBuilder = new StringBuilder(partialPath)
				.append(artifactId)
				.append('-')
				.append(version);

		if (classifier != null && !classifier.isEmpty()) {
			pathBuilder.append('-').append(classifier);
		}

		pathBuilder.append(".jar");

		return repository + pathBuilder;
	}

	/**
	 * Closes the isolated classloader used for maven resolver.
	 * This should be called when the helper is no longer needed to release resources.
	 *
	 * @throws Exception if an error occurs while closing the classloader
	 */
	@Override
	public void close() throws Exception {
		if (classLoader != null) classLoader.close();
	}

	/**
	 * Internal record to hold reflection objects during initialization.
	 */
	private record ReflectionObjects(
			Object collectorObject,
			Method resolveMethod,
			Method getGroupIdMethod,
			Method getArtifactIdMethod,
			Method getVersionMethod,
			Method getBaseVersionMethod,
			Method getClassifierMethod
	) {
	}
}
