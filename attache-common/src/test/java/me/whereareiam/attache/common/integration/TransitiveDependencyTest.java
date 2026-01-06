package me.whereareiam.attache.common.integration;

import lombok.Getter;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for transitive dependency resolution.
 * These tests require network access to download libraries and the maven resolver.
 */
class TransitiveDependencyTest {
	@TempDir
	Path tempDir;

	private TestLibraryManager libraryManager;

	// Test library with known transitive dependencies
	// Apache Commons Text depends on Commons Lang3
	private static final String TEST_GROUP_ID = "org.apache.commons";
	private static final String TEST_ARTIFACT_ID = "commons-text";
	private static final String TEST_VERSION = "1.10.0";

	// Known transitive dependency
	private static final String TRANSITIVE_GROUP_ID = "org.apache.commons";
	private static final String TRANSITIVE_ARTIFACT_ID = "commons-lang3";

	@BeforeEach
	void setUp() {
		libraryManager = new TestLibraryManager(tempDir);
		libraryManager.addMavenCentral();
	}

	@AfterEach
	void tearDown() throws Exception {
		// Close all isolated classloaders to release file locks on Windows
		if (libraryManager != null) libraryManager.closeAllClassLoaders();
	}

	@Test
	void testBasicTransitiveResolution() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.resolveTransitiveDependencies(true)
				.build();

		// Load the library with transitive dependencies
		libraryManager.loadLibrary(library);

		// Verify the main library was loaded
		Path mainLibPath = tempDir.resolve("lib").resolve("org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar");
		assertTrue(Files.exists(mainLibPath), "Main library should be downloaded");
		assertTrue(libraryManager.hasAddedToClasspath(mainLibPath), "Main library should be loaded to classpath");

		// Verify transitive dependency was loaded
		// Commons Lang3 version might vary, so we check the directory exists
		Path transitiveDirPath = tempDir.resolve("lib").resolve("org/apache/commons/commons-lang3");
		assertTrue(Files.exists(transitiveDirPath), "Transitive dependency directory should exist");

		// Verify at least one transitive dependency was added to classpath
		assertTrue(
				libraryManager.getAddedToClasspathCount() > 1,
				"Should have loaded main library plus transitive dependencies"
		);
	}

	@Test
	void testTransitiveResolutionWithExclusion() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.resolveTransitiveDependencies(true)
				.excludedTransitiveDependency(new ExcludedDependency(TRANSITIVE_GROUP_ID, TRANSITIVE_ARTIFACT_ID))
				.build();

		// Load the library with transitive dependencies but exclude commons-lang3
		libraryManager.loadLibrary(library);

		// Verify the main library was loaded
		Path mainLibPath = tempDir.resolve("lib").resolve("org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar");
		assertTrue(Files.exists(mainLibPath), "Main library should be downloaded");
		assertTrue(libraryManager.hasAddedToClasspath(mainLibPath), "Main library should be loaded to classpath");

		// Should only have the main library loaded (excluded transitive)
		assertEquals(1, libraryManager.getAddedToClasspathCount(), "Should only load main library, not the excluded transitive dependency");
	}

	@Test
	void testTransitiveResolutionDisabled() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.resolveTransitiveDependencies(false) // Explicitly disabled
				.build();

		// Load the library without transitive dependencies
		libraryManager.loadLibrary(library);

		// Verify only the main library was loaded
		Path mainLibPath = tempDir.resolve("lib").resolve("org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar");
		assertTrue(Files.exists(mainLibPath), "Main library should be downloaded");
		assertTrue(libraryManager.hasAddedToClasspath(mainLibPath), "Main library should be loaded to classpath");

		// Should only have one library loaded
		assertEquals(1, libraryManager.getAddedToClasspathCount(), "Should only load main library when transitive resolution is disabled");
	}

	@Test
	void testTransitiveResolutionWithRelocations() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.resolveTransitiveDependencies(true)
				.relocation(RelocationRule.builder()
						.pattern("org{}apache{}commons{}text")
						.relocatedPattern("me{}test{}relocated{}commons{}text")
						.build())
				.build();

		// Load the library with relocations - transitive deps should inherit them
		libraryManager.loadLibrary(library);

		// Main library should be relocated - check that relocated jar was created
		// The exact path includes a hash, so we check that at least one relocated jar exists
		Path libDir = tempDir.resolve("lib").resolve("org/apache/commons/commons-text/1.10.0");
		assertTrue(Files.exists(libDir), "Library directory should exist");

		// At least one library should be loaded (main library plus potentially transitive)
		assertTrue(libraryManager.getAddedToClasspathCount() >= 1, "Should have loaded at least the main library");

		// Verify at least one .jar file with "relocated" in the name exists
		boolean relocatedJarExists = Files.list(libDir)
				.anyMatch(path -> path.getFileName().toString().contains("relocated") &&
						path.getFileName().toString().endsWith(".jar"));
		assertTrue(relocatedJarExists, "Relocated main library should exist");
	}

	@Test
	void testTransitiveResolutionWithIsolatedClassLoader() {
		String loaderId = "transitive-test-loader";

		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.resolveTransitiveDependencies(true)
				.isolated(true)
				.loader(loaderId)
				.build();

		// Load the library with transitive dependencies into isolated classloader
		libraryManager.loadLibrary(library);

		// Should have loaded to isolated classloader, not regular classpath
		assertEquals(0, libraryManager.getAddedToClasspathCount(), "Should not add to regular classpath when using isolated classloader");

		// Verify isolated classloader exists and has the library
		assertNotNull(libraryManager.getIsolatedClassLoaderById(loaderId), "Isolated classloader should exist");
	}

	@Test
	void testNoRepositoriesThrowsException() throws Exception {
		// Create a library manager without any repositories
		TestLibraryManager emptyRepoManager = new TestLibraryManager(tempDir);

		try {
			LibraryRequest library = LibraryRequest.builder()
					.groupId(TEST_GROUP_ID)
					.artifactId(TEST_ARTIFACT_ID)
					.version(TEST_VERSION)
					.resolveTransitiveDependencies(true)
					.build();

			// Should throw IllegalArgumentException because no repositories are configured
			assertThrows(RuntimeException.class, () -> emptyRepoManager.loadLibrary(library));
		} finally {
			emptyRepoManager.closeAllClassLoaders();
		}
	}

	@Test
	void testTransitiveResolutionWithBraceSyntax() {
		// Test that {} syntax in library coordinates is properly normalized
		// before being passed to the maven resolver
		LibraryRequest library = LibraryRequest.builder()
				.groupId("org{}apache{}commons")
				.artifactId("commons-text")
				.version(TEST_VERSION)
				.resolveTransitiveDependencies(true)
				.build();

		// Load the library - should normalize {} to . before resolution
		libraryManager.loadLibrary(library);

		// Verify the main library was loaded
		Path mainLibPath = tempDir.resolve("lib").resolve("org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar");
		assertTrue(Files.exists(mainLibPath), "Main library should be downloaded");
		assertTrue(libraryManager.hasAddedToClasspath(mainLibPath), "Main library should be loaded to classpath");

		// Should have loaded main library plus transitive dependencies
		assertTrue(
				libraryManager.getAddedToClasspathCount() > 1,
				"Should have loaded main library plus transitive dependencies"
		);
	}

	/**
	 * Test implementation of BaseLibraryManager for integration testing.
	 */
	private static class TestLibraryManager extends BaseLibraryManager {
		@Getter
		private int addedToClasspathCount = 0;
		private final java.util.Set<Path> addedPaths = new java.util.HashSet<>();

		public TestLibraryManager(Path tempDir) {
			super(new TestLoggingHelper(), tempDir, "lib");
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			addedToClasspathCount++;
			addedPaths.add(file);
			System.out.println("Added to classpath: " + file);
		}

		public boolean hasAddedToClasspath(Path path) {
			return addedPaths.contains(path);
		}

		public void closeAllClassLoaders() throws Exception {
			// Close relocator's classloader first
			if (relocator != null)
				relocator.close();

			// Close transitive helper if it exists
			if (transitiveDependencyHelper != null)
				transitiveDependencyHelper.close();

			globalIsolatedClassLoader.close();
			for (me.whereareiam.attache.common.classloader.IsolatedClassLoader cl : isolatedLibraries.values())
				cl.close();
		}
	}

	/**
	 * Simple log adapter for testing that outputs to console.
	 */
	private static class TestLoggingHelper implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
			System.out.println("[" + level + "] " + message);
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
			System.out.println("[" + level + "] " + message);
			throwable.printStackTrace(System.out);
		}
	}
}

