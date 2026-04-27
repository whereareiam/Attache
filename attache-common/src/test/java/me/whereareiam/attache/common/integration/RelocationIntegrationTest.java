package me.whereareiam.attache.common.integration;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for library relocation.
 * These tests require network access to download libraries and jar-relocator.
 */
class RelocationIntegrationTest {
	@TempDir
	Path tempDir;

	private TestLibraryManager libraryManager;

	// Small library for testing relocation
	private static final String TEST_GROUP_ID = "org.apache.commons";
	private static final String TEST_ARTIFACT_ID = "commons-text";
	private static final String TEST_VERSION = "1.10.0";

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
	void testDownloadAndRelocateLibrary() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(Collections.singleton(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated{}commons{}text")
								.build()))
				.build();

		// This will download, relocate, and return the relocated jar
		Path relocatedFile = libraryManager.downloadLibrary(library);

		assertNotNull(relocatedFile);
		assertTrue(Files.exists(relocatedFile));
		assertTrue(relocatedFile.toString().contains("relocated"));
	}

	@Test
	void testRelocatedPackageStructure() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(java.util.Collections.singleton(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated{}commons{}text")
								.build()))
				.build();

		Path relocatedFile = libraryManager.downloadLibrary(library);

		// Verify the relocated jar contains the new package structure
		try (JarFile jarFile = new JarFile(relocatedFile.toFile())) {
			boolean hasRelocatedClasses = StreamSupport.stream(jarFile.stream().spliterator(), false)
					.map(JarEntry::getName)
					.anyMatch(name -> name.startsWith("me/test/relocated/commons/text/") && name.endsWith(".class"));

			assertTrue(hasRelocatedClasses, "Relocated jar should contain classes in new package");

			// Verify original package is gone
			boolean hasOriginalClasses = StreamSupport.stream(jarFile.stream().spliterator(), false)
					.map(JarEntry::getName)
					.anyMatch(name -> name.startsWith("org/apache/commons/text/") && name.endsWith(".class"));

			assertFalse(hasOriginalClasses, "Relocated jar should not contain classes in original package");
		}
	}

	@Test
	void testMultipleRelocations() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(Arrays.asList(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated{}text")
								.build(),
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}lang3")
								.relocatedPattern("me{}test{}relocated{}lang3")
								.build()))
				.build();

		Path relocatedFile = libraryManager.downloadLibrary(library);

		assertNotNull(relocatedFile);
		assertTrue(Files.exists(relocatedFile));

		// Verify both relocations are applied
		try (JarFile jarFile = new JarFile(relocatedFile.toFile())) {
			boolean hasRelocatedText = StreamSupport.stream(jarFile.stream().spliterator(), false)
					.map(JarEntry::getName)
					.anyMatch(name -> name.startsWith("me/test/relocated/text/") && name.endsWith(".class"));

			assertTrue(hasRelocatedText, "Should have relocated commons-text classes");
		}
	}

	@Test
	void testRelocationCaching() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(Collections.singleton(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated{}commons{}text")
								.build()))
				.build();

		// First relocation
		Path firstRelocated = libraryManager.downloadLibrary(library);
		long firstModified = Files.getLastModifiedTime(firstRelocated).toMillis();

		Thread.sleep(100);

		// Second call - should use cached relocated jar
		Path secondRelocated = libraryManager.downloadLibrary(library);
		long secondModified = Files.getLastModifiedTime(secondRelocated).toMillis();

		assertEquals(firstRelocated, secondRelocated);
		assertEquals(firstModified, secondModified, "Relocated jar should be cached");
	}

	@Test
	void testDifferentRelocationsCreateDifferentJars() throws Exception {
		LibraryRequest library1 = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(Collections.singleton(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated1{}commons{}text")
								.build()))
				.build();

		LibraryRequest library2 = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(Collections.singleton(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated2{}commons{}text")
								.build()))
				.build();

		Path relocated1 = libraryManager.downloadLibrary(library1);
		Path relocated2 = libraryManager.downloadLibrary(library2);

		// Different relocations should produce different files
		assertNotEquals(relocated1, relocated2);
		assertTrue(Files.exists(relocated1));
		assertTrue(Files.exists(relocated2));
	}

	@Test
	void testRelocationWithIncludes() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.relocations(Collections.singleton(
						RelocationRule.builder()
								.pattern("org{}apache{}commons{}text")
								.relocatedPattern("me{}test{}relocated{}commons{}text")
								.includes(Collections.singleton("org{}apache{}commons{}text{}**"))
								.build()))
				.build();

		Path relocatedFile = libraryManager.downloadLibrary(library);

		assertNotNull(relocatedFile);
		assertTrue(Files.exists(relocatedFile));
	}

	/**
	 * Test implementation of BaseLibraryManager for integration testing.
	 */
	private static class TestLibraryManager extends BaseLibraryManager {

		public TestLibraryManager(Path tempDir) {
			super(new TestLoggingHelper(), tempDir, "lib");
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			// No-op for testing
		}

		public void closeAllClassLoaders() {
			close();
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
