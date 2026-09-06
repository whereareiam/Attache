package me.whereareiam.attache.launcher;

import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.attache.resolution.model.ResolvedArtifact;
import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.resolution.DependencyResolver;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Properties;

/**
 * Boots Attache's optional resolver artifact and translates its typed results into library loads.
 * Maven implementation details and repository/cache policies belong in attache-resolver.
 */
final class IsolatedDependencyResolver implements DependencyResolver {
	private final LibraryManager libraryManager;
	private final ResolverClassLoader classLoader = new ResolverClassLoader();
	private final DependencyResolver resolver;

	/**
	 * Downloads the matching, checksum-pinned resolver and loads it in an isolated class loader.
	 *
	 * @param libraryManager owner of download and relocation caches
	 * @param saveDirectory local Maven repository used by the resolver
	 */
	IsolatedDependencyResolver(@NotNull LibraryManager libraryManager, @NotNull Path saveDirectory) {
		this.libraryManager = libraryManager;
		try {
			resolver = createResolver(saveDirectory);
		} catch (Exception | LinkageError failure) {
			classLoader.closeAfterFailure(failure);
			throw new IllegalStateException("Could not initialize Attache's dependency resolver", failure);
		}
	}

	private @NotNull DependencyResolver createResolver(@NotNull Path saveDirectory) throws Exception {
		String apiPackage = DependencyResolver.class.getPackageName();
		String apiRoot = apiPackage.substring(0, apiPackage.lastIndexOf('.'));
		LibraryRequest request = resolverRequest(apiRoot);

		classLoader.addPath(libraryManager.downloadLibrary(request));
		Class<? extends DependencyResolver> resolverType = classLoader.loadClass(apiRoot + ".resolver.MavenDependencyResolver")
				.asSubclass(DependencyResolver.class);

		return resolverType.getConstructor(Path.class).newInstance(saveDirectory);
	}

	private @NotNull LibraryRequest resolverRequest(@NotNull String apiRoot) throws IOException {
		Properties metadata = resolverMetadata();
		LibraryRequest request = LibraryHelper.normalize(LibraryRequest.builder()
				.groupId("me{}whereareiam")
				.artifactId("attache-resolver")
				.version(metadata.getProperty("version"))
				.checksum(HexFormat.of().parseHex(metadata.getProperty("sha256")))
				.fallbackRepository("https://registry.whereareiam.me/maven/packages/")
				.build());

		var builder = request.toBuilder().url(Repositories.mavenLocal() + LibraryHelper.getPath(request));
		String originalRoot = LibraryHelper.replaceWithDots("me{}whereareiam{}attache");
		if (!originalRoot.equals(apiRoot))
			builder.relocation(RelocationRule.builder()
					.pattern(originalRoot)
					.relocatedPattern(apiRoot)
					.build());

		return builder.build();
	}

	private @NotNull Properties resolverMetadata() throws IOException {
		Properties metadata = new Properties();
		try (var input = IsolatedDependencyResolver.class.getResourceAsStream("resolver.properties")) {
			if (input == null) throw new IllegalStateException("Missing packaged resolver metadata");

			metadata.load(input);
		}

		return metadata;
	}

	@Override
	public @NotNull Collection<ResolvedArtifact> resolve(
			@NotNull LibraryRequest library,
			@NotNull Collection<String> repositories
	) {
		return resolver.resolve(library, repositories);
	}

	/**
	 * Releases the resolver's transports before closing its isolated class loader.
	 *
	 * @throws UncheckedIOException when the class loader cannot be closed
	 */
	@Override
	public void close() {
		try (classLoader) {
			resolver.close();
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}
}
